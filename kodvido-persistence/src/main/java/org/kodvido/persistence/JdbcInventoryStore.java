// SPDX-License-Identifier: GPL-3.0-only
package org.kodvido.persistence;
import org.kodvido.domain.*;
import org.kodvido.domain.Inventory.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import javax.sql.DataSource;
import java.sql.*;
import java.util.*;
public final class JdbcInventoryStore implements Store {
    private final DataSource source;
    private final ObjectMapper json=new ObjectMapper();
    public JdbcInventoryStore(DataSource source) { this.source=source; }
    public List<String> roots() {
        try(var c=source.getConnection();var s=c.prepareStatement("SELECT path FROM inventory_root ORDER BY position");var rows=s.executeQuery()) {
            var result=new ArrayList<String>();while(rows.next()) result.add(rows.getString(1));return List.copyOf(result);
        } catch(SQLException e) { throw new CatalogException(e.getSQLState(),e.getErrorCode()); }
    }
    public void roots(List<String> roots) {
        try(var c=source.getConnection()) {
            c.setAutoCommit(false);
            try(var clear=c.prepareStatement("DELETE FROM inventory_root");var insert=c.prepareStatement("INSERT INTO inventory_root VALUES(?,?)")) {
                clear.executeUpdate();int i=0;for(String root:roots) {insert.setInt(1,i++);insert.setString(2,root);insert.addBatch();}insert.executeBatch();c.commit();
            } catch(SQLException e) { c.rollback();throw e; }
        } catch(SQLException e) { throw new CatalogException(e.getSQLState(),e.getErrorCode()); }
    }
    public Optional<Snapshot> snapshot(CatalogProject p) {
        try(var c=source.getConnection();var s=c.prepareStatement("SELECT payload FROM inventory_snapshot WHERE project_id=? AND location=?")) {
            s.setObject(1,p.id());s.setString(2,p.path().toString());try(var rows=s.executeQuery()) {
                return rows.next()?Optional.of(json.readValue(rows.getString(1),Snapshot.class)):Optional.empty();
            }
        } catch(java.io.IOException e) { throw new CatalogException(); }
        catch(SQLException e) { throw new CatalogException(e.getSQLState(),e.getErrorCode()); }
    }
    public void snapshot(CatalogProject p,Snapshot snapshot) {
        try(var c=source.getConnection()) {
            c.setAutoCommit(false);
            try(var lock=c.prepareStatement("SELECT primary_path,updated_at FROM project WHERE id=? FOR UPDATE");
                var s=c.prepareStatement("MERGE INTO inventory_snapshot KEY(project_id) VALUES(?,?,?)")) {
                lock.setObject(1,p.id());try(var rows=lock.executeQuery()) {
                    if(rows.next() && rows.getString(1).equals(p.path().toString()) && rows.getObject(2,java.time.OffsetDateTime.class).toInstant().equals(p.updatedAt())) {
                        s.setObject(1,p.id());s.setString(2,p.path().toString());s.setString(3,json.writeValueAsString(snapshot));s.executeUpdate();
                    }
                }c.commit();
            } catch(Exception e) {c.rollback();throw e;}
        } catch(java.io.IOException e) { throw new CatalogException(); }
        catch(SQLException e) { throw new CatalogException(e.getSQLState(),e.getErrorCode()); }
    }
    public Notes notes(UUID id) {
        try(var c=source.getConnection()) {return notes(c,id);}
        catch(SQLException e) {throw new CatalogException(e.getSQLState(),e.getErrorCode());}
    }
    static Notes notes(Connection c,UUID id) {
        try(var s=c.prepareStatement("SELECT * FROM inventory_note WHERE project_id=?")) {
            s.setObject(1,id);try(var rows=s.executeQuery()) {
                return rows.next()?new Notes(Lifecycle.valueOf(rows.getString("lifecycle")),rows.getString("note"),rows.getString("tasks"),rows.getLong("revision")):Notes.empty();
            }
        } catch(SQLException e) {throw new CatalogException(e.getSQLState(),e.getErrorCode());}
    }
    public void notes(UUID id,Notes notes) {
        try(var c=source.getConnection()) {
            c.setAutoCommit(false);
            try(var lock=c.prepareStatement("SELECT id FROM project WHERE id=? FOR UPDATE");
                var check=c.prepareStatement("SELECT revision FROM inventory_note WHERE project_id=?");
                var s=c.prepareStatement("MERGE INTO inventory_note KEY(project_id) VALUES(?,?,?,?,?)")) {
                lock.setObject(1,id);try(var rows=lock.executeQuery()) {if(!rows.next()) throw new IllegalArgumentException("Projet absent.");}
                check.setObject(1,id);try(var rows=check.executeQuery()) {
                    long revision=rows.next()?rows.getLong(1):0;
                    if(revision!=notes.revision()) throw new IllegalArgumentException("Notes modifiées ailleurs : rechargez la fiche avant de réessayer.");
                }
                s.setObject(1,id);s.setString(2,notes.lifecycle().name());s.setString(3,notes.note());s.setString(4,notes.tasks());s.setLong(5,notes.revision()+1);s.executeUpdate();c.commit();
            } catch(SQLException | RuntimeException e) {c.rollback();throw e;}
        } catch(SQLException e) {throw new CatalogException(e.getSQLState(),e.getErrorCode());}
    }
}
