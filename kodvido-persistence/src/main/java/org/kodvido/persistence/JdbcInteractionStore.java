// SPDX-License-Identifier: GPL-3.0-only
package org.kodvido.persistence;
import org.kodvido.domain.*;
import javax.sql.DataSource;
import java.sql.*;
import java.time.*;
import java.util.*;

public final class JdbcInteractionStore implements InteractionStore {
    private final DataSource source;
    public JdbcInteractionStore(DataSource source) { this.source=source; }
    @Override public List<Interaction> recent(UUID projectId) {
        try(var c=source.getConnection();var s=c.prepareStatement("SELECT * FROM project_interaction WHERE project_id=? ORDER BY sequence DESC FETCH FIRST 100 ROWS ONLY")) {
            s.setObject(1,projectId);
            try(var rows=s.executeQuery()) {
                var result=new ArrayList<Interaction>();
                while(rows.next()) result.add(new Interaction(rows.getLong("sequence"),projectId,
                    Interaction.Kind.valueOf(rows.getString("kind")),rows.getObject("occurred_at",OffsetDateTime.class).toInstant()));
                return List.copyOf(result);
            }
        } catch(SQLException ex) { throw new CatalogException(ex.getSQLState(),ex.getErrorCode()); }
    }
    @Override public void record(UUID projectId,Interaction.Kind kind,Instant occurredAt) {
        try(var c=source.getConnection()) {
            c.setAutoCommit(false);
            try { append(c,projectId,kind,occurredAt);c.commit(); }
            catch(SQLException | RuntimeException ex) { c.rollback();throw ex; }
        } catch(SQLException ex) { throw new CatalogException(ex.getSQLState(),ex.getErrorCode()); }
    }
    /** Shared by adapters to commit a successful mutation and its event atomically. */
    static void append(Connection c,UUID projectId,Interaction.Kind kind,Instant occurredAt) throws SQLException {
        // Serialize sequence allocation and retention for a project, including concurrent requests.
        try(var s=c.prepareStatement("SELECT id FROM project WHERE id=? FOR UPDATE")) {
            s.setObject(1,projectId);try(var rows=s.executeQuery()) { if(!rows.next()) throw new CatalogException(); }
        }
        try(var s=c.prepareStatement("INSERT INTO project_interaction(project_id,kind,occurred_at) VALUES(?,?,?)")) {
            s.setObject(1,projectId);s.setString(2,kind.name());s.setObject(3,occurredAt.atOffset(ZoneOffset.UTC));s.executeUpdate();
        }
        try(var s=c.prepareStatement("DELETE FROM project_interaction WHERE project_id=? AND sequence IN (SELECT sequence FROM project_interaction WHERE project_id=? ORDER BY sequence DESC OFFSET 100 ROWS)")) {
            s.setObject(1,projectId);s.setObject(2,projectId);s.executeUpdate();
        }
    }
}
