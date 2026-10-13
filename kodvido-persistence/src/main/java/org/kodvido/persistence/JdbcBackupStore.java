// SPDX-License-Identifier: GPL-3.0-only
package org.kodvido.persistence;
import org.kodvido.domain.*;
import javax.sql.DataSource;
import java.sql.*;
import java.time.*;
import java.util.*;

public final class JdbcBackupStore implements BackupStore {
    private final DataSource source;
    public JdbcBackupStore(DataSource source) { this.source=source; }
    @Override public List<CatalogBackup.Entry> snapshot() {
        try(var c=source.getConnection()) {
            c.setTransactionIsolation(Connection.TRANSACTION_SERIALIZABLE); c.setAutoCommit(false);
            try(var s=c.prepareStatement("SELECT * FROM project ORDER BY name,id"); var rows=s.executeQuery()) {
                var result=new ArrayList<CatalogBackup.Entry>();
                while(rows.next()) {
                    if(result.size()==1000) throw new IllegalArgumentException("Maximum 1000 projets.");
                    UUID id=rows.getObject("id",UUID.class);
                    result.add(new CatalogBackup.Entry(id,rows.getString("name"),rows.getString("primary_path"),
                        rows.getObject("created_at",OffsetDateTime.class).toInstant(),rows.getObject("updated_at",OffsetDateTime.class).toInstant(),
                        JdbcWorkflowStore.find(c,id).orElse(null),JdbcBackupAssessmentStore.find(c,id).orElse(null)));
                }
                c.commit(); return List.copyOf(result);
            } catch(SQLException | RuntimeException ex) { c.rollback(); throw ex; }
        } catch(SQLException ex) { throw new CatalogException(ex.getSQLState(),ex.getErrorCode()); }
    }
    @Override public void restore(List<CatalogBackup.Entry> entries) {
        try(var c=source.getConnection()) {
            c.setAutoCommit(false);
            try {
                for(var e:entries) {
                    try(var s=c.prepareStatement("INSERT INTO project(id,name,primary_path,created_at,updated_at) VALUES(?,?,?,?,?)")) {
                        s.setObject(1,e.id());s.setString(2,e.name());s.setString(3,e.path());
                        s.setObject(4,e.createdAt().atOffset(ZoneOffset.UTC));s.setObject(5,e.updatedAt().atOffset(ZoneOffset.UTC));s.executeUpdate();
                    }
                    if(e.notes()!=null) insertNotes(c,e.notes());
                    if(e.assessment()!=null) JdbcBackupAssessmentStore.insert(c,e.assessment());
                }
                c.commit();
            } catch(SQLException | RuntimeException ex) { c.rollback(); throw ex; }
        } catch(SQLException ex) { throw new CatalogException(ex.getSQLState(),ex.getErrorCode()); }
    }
    private static void insertNotes(Connection c,WorkflowNotes n) throws SQLException {
        try(var s=c.prepareStatement("INSERT INTO workflow_notes(project_id,revision,development,operations,updated_at) VALUES(?,?,?,?,?)")) {
            s.setObject(1,n.projectId());s.setLong(2,n.revision());s.setString(3,n.development());s.setString(4,n.operations());
            s.setObject(5,n.updatedAt().atOffset(ZoneOffset.UTC));s.executeUpdate();
        }
        try(var s=c.prepareStatement("INSERT INTO workflow_phase(project_id,phase_order,phase_date,description,state) VALUES(?,?,?,?,?)")) {
            for(int i=0;i<n.phases().size();i++) {
                var p=n.phases().get(i);s.setObject(1,n.projectId());s.setInt(2,i);s.setObject(3,p.date());s.setString(4,p.description());s.setString(5,p.state().name());s.addBatch();
            }
            s.executeBatch();
        }
    }
}
