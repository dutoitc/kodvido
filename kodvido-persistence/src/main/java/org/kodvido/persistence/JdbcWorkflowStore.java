// SPDX-License-Identifier: GPL-3.0-only
package org.kodvido.persistence;

import org.kodvido.domain.*;
import javax.sql.DataSource;
import java.sql.*;
import java.time.*;
import java.util.*;

public final class JdbcWorkflowStore implements WorkflowStore {
    private final DataSource source;
    public JdbcWorkflowStore(DataSource source) { this.source = source; }
    @Override public Optional<WorkflowNotes> find(UUID id) {
        String sql = "SELECT n.*, p.phase_order, p.phase_date, p.description, p.state FROM workflow_notes n "
            + "LEFT JOIN workflow_phase p ON p.project_id=n.project_id WHERE n.project_id=? ORDER BY p.phase_order";
        try (var c = source.getConnection(); var s = c.prepareStatement(sql)) {
            s.setObject(1, id);
            try (var rows = s.executeQuery()) {
                if (!rows.next()) return Optional.empty();
                long revision = rows.getLong("revision"); String dev = rows.getString("development"), ops = rows.getString("operations");
                Instant updated = rows.getObject("updated_at", OffsetDateTime.class).toInstant();
                var phases = new ArrayList<WorkflowNotes.Phase>();
                do {
                    if (rows.getObject("phase_order") != null) phases.add(new WorkflowNotes.Phase(rows.getObject("phase_date", LocalDate.class),
                        rows.getString("description"), WorkflowNotes.State.valueOf(rows.getString("state"))));
                } while (rows.next());
                return Optional.of(new WorkflowNotes(id, revision, phases, dev, ops, updated));
            }
        } catch (SQLException ex) { throw new CatalogException(ex.getSQLState(), ex.getErrorCode()); }
    }
    @Override public void save(WorkflowNotes notes) {
        try (var c = source.getConnection()) {
            c.setAutoCommit(false);
            try {
                // Lock the stable parent row even for the first save (no notes row yet).
                try (var s = c.prepareStatement("SELECT id FROM project WHERE id=? FOR UPDATE")) {
                    s.setObject(1, notes.projectId());
                    try (var rows = s.executeQuery()) { if (!rows.next()) throw new CatalogException(); }
                }
                long current = 0;
                try (var s = c.prepareStatement("SELECT revision FROM workflow_notes WHERE project_id=?")) {
                    s.setObject(1, notes.projectId());
                    try (var rows = s.executeQuery()) { if (rows.next()) current = rows.getLong(1); }
                }
                if (current != notes.revision() - 1) throw new WorkflowConflictException();
                String sql = current == 0 ? "INSERT INTO workflow_notes(development,operations,updated_at,revision,project_id) VALUES(?,?,?,?,?)"
                    : "UPDATE workflow_notes SET development=?,operations=?,updated_at=?,revision=? WHERE project_id=?";
                try (var s = c.prepareStatement(sql)) {
                    s.setString(1, notes.development()); s.setString(2, notes.operations()); s.setObject(3, notes.updatedAt().atOffset(ZoneOffset.UTC));
                    s.setLong(4, notes.revision()); s.setObject(5, notes.projectId()); s.executeUpdate();
                }
                try (var s = c.prepareStatement("DELETE FROM workflow_phase WHERE project_id=?")) { s.setObject(1, notes.projectId()); s.executeUpdate(); }
                try (var s = c.prepareStatement("INSERT INTO workflow_phase(project_id,phase_order,phase_date,description,state) VALUES(?,?,?,?,?)")) {
                    for (int i = 0; i < notes.phases().size(); i++) {
                        var phase = notes.phases().get(i);
                        s.setObject(1, notes.projectId()); s.setInt(2, i); s.setObject(3, phase.date());
                        s.setString(4, phase.description()); s.setString(5, phase.state().name()); s.addBatch();
                    }
                    s.executeBatch();
                }
                c.commit();
            } catch (SQLException | RuntimeException ex) { c.rollback(); throw ex; }
        } catch (SQLException ex) { throw new CatalogException(ex.getSQLState(), ex.getErrorCode()); }
    }
}
