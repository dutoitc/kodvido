// SPDX-License-Identifier: GPL-3.0-only
package org.kodvido.persistence;
import org.kodvido.domain.*;
import org.kodvido.domain.BackupAssessment.*;
import javax.sql.DataSource;
import java.sql.*;
import java.time.*;
import java.util.*;

public final class JdbcBackupAssessmentStore implements BackupAssessmentStore {
    private final DataSource source;
    public JdbcBackupAssessmentStore(DataSource source) { this.source=source; }
    @Override public Optional<BackupAssessment> find(UUID id) {
        try(var c=source.getConnection()) { return find(c,id); }
        catch(SQLException ex) { throw new CatalogException(ex.getSQLState(),ex.getErrorCode()); }
    }
    static Optional<BackupAssessment> find(Connection c,UUID id) throws SQLException {
        try(var s=c.prepareStatement("SELECT a.revision,a.updated_at,e.* FROM backup_assessment a JOIN backup_evidence e ON e.project_id=a.project_id WHERE a.project_id=?")) {
            s.setObject(1,id);
            try(var rows=s.executeQuery()) {
                if(!rows.next()) return Optional.empty();
                long revision=rows.getLong("revision");
                Instant updated=rows.getObject("updated_at",OffsetDateTime.class).toInstant();
                var items=new ArrayList<Item>();
                do { items.add(new Item(Dimension.valueOf(rows.getString("dimension")),State.valueOf(rows.getString("state")),
                    rows.getObject("checked_on",LocalDate.class),rows.getString("evidence"))); } while(rows.next());
                items.sort(Comparator.comparing(Item::dimension));
                return Optional.of(new BackupAssessment(id,revision,items,updated));
            }
        }
    }
    @Override public void save(BackupAssessment a) {
        try(var c=source.getConnection()) {
            c.setAutoCommit(false);
            try {
                try(var s=c.prepareStatement("SELECT id FROM project WHERE id=? FOR UPDATE")) {
                    s.setObject(1,a.projectId()); try(var rows=s.executeQuery()) { if(!rows.next()) throw new CatalogException(); }
                }
                long revision=0;
                try(var s=c.prepareStatement("SELECT revision FROM backup_assessment WHERE project_id=?")) {
                    s.setObject(1,a.projectId());try(var rows=s.executeQuery()) { if(rows.next()) revision=rows.getLong(1); }
                }
                if(revision!=a.revision()-1) throw new BackupAssessmentConflictException();
                try(var s=c.prepareStatement("DELETE FROM backup_assessment WHERE project_id=?")) { s.setObject(1,a.projectId());s.executeUpdate(); }
                insert(c,a); c.commit();
            } catch(SQLException | RuntimeException ex) { c.rollback();throw ex; }
        } catch(SQLException ex) { throw new CatalogException(ex.getSQLState(),ex.getErrorCode()); }
    }
    static void insert(Connection c,BackupAssessment a) throws SQLException {
        try(var s=c.prepareStatement("INSERT INTO backup_assessment(project_id,revision,updated_at) VALUES(?,?,?)")) {
            s.setObject(1,a.projectId());s.setLong(2,a.revision());s.setObject(3,a.updatedAt().atOffset(ZoneOffset.UTC));s.executeUpdate();
        }
        try(var s=c.prepareStatement("INSERT INTO backup_evidence(project_id,dimension,state,checked_on,evidence) VALUES(?,?,?,?,?)")) {
            for(var i:a.items()) {
                s.setObject(1,a.projectId());s.setString(2,i.dimension().name());s.setString(3,i.state().name());
                s.setObject(4,i.checkedOn());s.setString(5,i.evidence());s.addBatch();
            }
            s.executeBatch();
        }
    }
}
