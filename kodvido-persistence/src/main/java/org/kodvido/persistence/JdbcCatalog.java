package org.kodvido.persistence;

import org.kodvido.domain.*;
import javax.sql.DataSource;
import java.nio.file.Path;
import java.sql.*;
import java.time.*;
import java.util.*;

/** Plain JDBC adapter. No Spring dependency and no filesystem write capability. */
public final class JdbcCatalog implements Catalog {
    private final DataSource source;
    public JdbcCatalog(DataSource source) { this.source = source; }
    @Override public List<CatalogProject> list() {
        try (var connection = source.getConnection(); var statement = connection.prepareStatement("SELECT * FROM project ORDER BY name, id"); var rows = statement.executeQuery()) {
            List<CatalogProject> projects = new ArrayList<>();
            while (rows.next()) projects.add(new CatalogProject(rows.getObject("id", UUID.class), rows.getString("name"),
                Path.of(rows.getString("primary_path")), rows.getObject("created_at", OffsetDateTime.class).toInstant(),
                rows.getObject("updated_at", OffsetDateTime.class).toInstant()));
            return List.copyOf(projects);
        } catch (SQLException ex) { throw new CatalogException(ex.getSQLState(), ex.getErrorCode()); }
    }
    @Override public void addAll(List<CatalogProject> projects) {
        try (var connection = source.getConnection()) {
            connection.setAutoCommit(false);
            try (var statement = connection.prepareStatement("INSERT INTO project(id,name,primary_path,created_at,updated_at) VALUES(?,?,?,?,?)")) {
                for (var project : projects) {
                    statement.setObject(1, project.id()); statement.setString(2, project.name());
                    statement.setString(3, project.path().toString());
                    statement.setObject(4, project.createdAt().atOffset(ZoneOffset.UTC));
                    statement.setObject(5, project.updatedAt().atOffset(ZoneOffset.UTC)); statement.addBatch();
                }
                statement.executeBatch(); connection.commit();
            } catch (SQLException ex) { connection.rollback(); throw ex; }
        } catch (SQLException ex) { throw new CatalogException(ex.getSQLState(), ex.getErrorCode()); }
    }
    @Override public void relocate(CatalogProject project) {
        try (var connection = source.getConnection()) {
            connection.setAutoCommit(false);
            try (var statement = connection.prepareStatement("UPDATE project SET primary_path=?,updated_at=? WHERE id=?")) {
                statement.setString(1, project.path().toString()); statement.setObject(2, project.updatedAt().atOffset(ZoneOffset.UTC));
                statement.setObject(3, project.id());
                if (statement.executeUpdate() != 1) throw new CatalogException();
                try(var invalidate=connection.prepareStatement("DELETE FROM inventory_snapshot WHERE project_id=?")) { invalidate.setObject(1,project.id());invalidate.executeUpdate(); }
                JdbcInteractionStore.append(connection,project.id(),Interaction.Kind.RELOCATED,project.updatedAt());
                connection.commit();
            } catch (SQLException | RuntimeException ex) { connection.rollback(); throw ex; }
        } catch (SQLException ex) { throw new CatalogException(ex.getSQLState(), ex.getErrorCode()); }
    }
    @Override public void remove(UUID id) {
        try (var connection = source.getConnection(); var statement = connection.prepareStatement("DELETE FROM project WHERE id=?")) {
            statement.setObject(1, id); statement.executeUpdate();
        } catch (SQLException ex) { throw new CatalogException(ex.getSQLState(), ex.getErrorCode()); }
    }
}
