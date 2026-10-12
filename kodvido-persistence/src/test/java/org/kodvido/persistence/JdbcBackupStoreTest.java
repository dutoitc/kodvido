package org.kodvido.persistence;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.kodvido.domain.*;
import org.h2.jdbcx.JdbcDataSource;
import org.flywaydb.core.Flyway;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

class JdbcBackupStoreTest {
    @TempDir Path temp;
    JdbcDataSource source; JdbcBackupStore store;
    final Instant now=Instant.parse("2026-01-01T00:00:00Z");
    @BeforeEach void setup() {
        source=new JdbcDataSource();source.setURL("jdbc:h2:file:"+temp.resolve("catalog"));
        Flyway.configure().dataSource(source).load().migrate();store=new JdbcBackupStore(source);
    }
    CatalogBackup.Entry entry(String name,boolean notes) {
        UUID id=UUID.randomUUID();
        return new CatalogBackup.Entry(id,name,temp.resolve(name).toString(),now,now,notes ? new WorkflowNotes(id,7,
            List.of(new WorkflowNotes.Phase(LocalDate.of(2025,12,1),"phase",WorkflowNotes.State.DONE)),"build","run",now):null);
    }
    @Test void fileDatabaseRoundTripPreservesIdentityDatesNotesAndSourceFiles() throws Exception {
        Files.writeString(temp.resolve("PROJECT.md"),"unchanged");
        var entries=List.of(entry("a",true),entry("b",false));
        assertThat(store.snapshot()).isEmpty(); store.restore(entries);
        assertThat(new JdbcBackupStore(source).snapshot()).isEqualTo(entries);
        var catalog=new JdbcCatalog(source);entries.forEach(e->catalog.remove(e.id()));
        assertThat(store.snapshot()).isEmpty();store.restore(entries);
        assertThat(store.snapshot()).isEqualTo(entries);
        assertThat(Files.readString(temp.resolve("PROJECT.md"))).isEqualTo("unchanged");
        assertThat(Files.exists(temp.resolve("a"))).isFalse();
    }
    @Test void conflictsAndInvalidNotesRollbackEveryProjectAndWorksheet() {
        var existing=entry("existing",true);store.restore(List.of(existing));
        var fresh=entry("fresh",true);
        assertThatThrownBy(()->store.restore(List.of(fresh,existing))).isInstanceOf(CatalogException.class);
        var samePath=new CatalogBackup.Entry(UUID.randomUUID(),"other",existing.path(),now,now,null);
        assertThatThrownBy(()->store.restore(List.of(fresh,samePath))).isInstanceOf(CatalogException.class);
        var n=fresh.notes();var bad=new CatalogBackup.Entry(fresh.id(),fresh.name(),fresh.path(),now,now,
            new WorkflowNotes(n.projectId(),n.revision(),Collections.nCopies(6,n.phases().getFirst()),"bad","bad",now));
        assertThatThrownBy(()->store.restore(List.of(bad))).isInstanceOf(CatalogException.class);
        assertThat(store.snapshot()).containsExactly(existing);
    }
    @Test void databaseFailuresRevealOnlySafeDiagnosticCodes() throws Exception {
        try(var c=source.getConnection();var s=c.createStatement()) {s.execute("DROP TABLE project CASCADE");}
        assertThatThrownBy(()->store.snapshot()).isInstanceOf(CatalogException.class).hasMessageNotContaining(temp.toString());
        assertThatThrownBy(()->store.restore(List.of(entry("a",false)))).isInstanceOf(CatalogException.class).hasMessageNotContaining(temp.toString());
    }
}
