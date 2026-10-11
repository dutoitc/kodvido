package org.kodvido.persistence;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.kodvido.domain.*;
import org.h2.jdbcx.JdbcDataSource;
import org.flywaydb.core.Flyway;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
class JdbcCatalogTest {
    @TempDir Path temp;
    JdbcDataSource source;
    JdbcCatalog catalog;
    @BeforeEach void setup() {
        source = new JdbcDataSource(); source.setURL("jdbc:h2:file:" + temp.resolve("catalog"));
        Flyway.configure().dataSource(source).load().migrate(); catalog = new JdbcCatalog(source);
    }
    CatalogProject project(String name) {
        Instant time = Instant.parse("2026-01-01T00:00:00Z");
        return new CatalogProject(UUID.randomUUID(), name, temp.resolve(name), time, time);
    }
    @Test void survivesReopenAndRelocationPreservesMetadataAndSourceFiles() throws Exception {
        var p = project("source"); Files.createDirectory(p.path()); Files.writeString(p.path().resolve("keep.txt"), "untouched");
        catalog.addAll(List.of(p)); catalog = new JdbcCatalog(source); assertThat(catalog.list()).containsExactly(p);
        var moved = new CatalogProject(p.id(), p.name(), temp.resolve("moved"), p.createdAt(), p.updatedAt().plusSeconds(3));
        catalog.relocate(moved); assertThat(catalog.list()).containsExactly(moved);
        catalog.remove(p.id()); assertThat(catalog.list()).isEmpty();
        assertThat(Files.readString(p.path().resolve("keep.txt"))).isEqualTo("untouched");
        assertThat(Flyway.configure().dataSource(source).load().migrate().migrationsExecuted).isZero();
    }
    @Test void conflictingBatchRollsBackCompletelyAndRelocationRejectsDuplicates() {
        var first = project("same"); var duplicate = project("same");
        assertThatThrownBy(() -> catalog.addAll(List.of(first, duplicate))).isInstanceOf(CatalogException.class);
        assertThat(catalog.list()).isEmpty();
        var other = project("other"); catalog.addAll(List.of(first, other));
        assertThatThrownBy(() -> catalog.relocate(new CatalogProject(other.id(), other.name(), first.path(), other.createdAt(), other.updatedAt()))).isInstanceOf(CatalogException.class);
        assertThat(catalog.list()).hasSize(2);
        assertThatThrownBy(() -> catalog.relocate(project("absent"))).isInstanceOf(CatalogException.class);
        catalog.remove(UUID.randomUUID());
    }
    @Test void databaseErrorsAreSanitized() throws Exception {
        try (var c = source.getConnection(); var s = c.createStatement()) { s.execute("DROP TABLE project"); }
        assertThatThrownBy(catalog::list).isInstanceOf(CatalogException.class).hasMessageNotContaining(temp.toString());
        assertThatThrownBy(() -> catalog.remove(UUID.randomUUID())).isInstanceOf(CatalogException.class);
        assertThatThrownBy(() -> catalog.relocate(project("absent"))).isInstanceOf(CatalogException.class);
    }
}
