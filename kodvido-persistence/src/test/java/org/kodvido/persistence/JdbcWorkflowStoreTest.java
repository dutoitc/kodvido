package org.kodvido.persistence;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.kodvido.domain.*;
import org.h2.jdbcx.JdbcDataSource;
import org.flywaydb.core.Flyway;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;

class JdbcWorkflowStoreTest {
    @TempDir Path temp;
    JdbcDataSource source; JdbcCatalog catalog; JdbcWorkflowStore store;
    final UUID id = UUID.randomUUID(); final Instant now = Instant.parse("2026-01-01T00:00:00Z");
    @BeforeEach void setup() {
        source = new JdbcDataSource(); source.setURL("jdbc:h2:file:"+temp.resolve("catalog"));
        // Start at V1 and migrate an existing catalogue with a real project.
        Flyway.configure().dataSource(source).target("1").load().migrate(); catalog = new JdbcCatalog(source);
        catalog.addAll(List.of(new CatalogProject(id,"fixture",temp,now,now)));
        Flyway.configure().dataSource(source).load().migrate(); store = new JdbcWorkflowStore(source);
    }
    WorkflowNotes notes(long revision) {
        return new WorkflowNotes(id,revision,List.of(new WorkflowNotes.Phase(LocalDate.of(2026,1,1),"milestone",WorkflowNotes.State.DONE)),"build","run",now);
    }
    @Test void migrationReopenRelocationAndRemovalPreserveCorrectData() throws Exception {
        Files.writeString(temp.resolve("PROJECT.md"),"source remains owned by project");
        assertThat(store.find(id)).isEmpty(); store.save(notes(1));
        store = new JdbcWorkflowStore(source); assertThat(store.find(id)).contains(notes(1));
        catalog.relocate(new CatalogProject(id,"fixture",temp.resolve("moved"),now,now));
        assertThat(store.find(id)).contains(notes(1));
        store.save(new WorkflowNotes(id,2,List.of(),"","",now)); assertThat(store.find(id).orElseThrow().phases()).isEmpty();
        store.save(notes(3));
        catalog.remove(id); assertThat(store.find(id)).isEmpty();
        try(var c=source.getConnection();var s=c.createStatement();var rows=s.executeQuery("SELECT COUNT(*) FROM workflow_phase")) { rows.next(); assertThat(rows.getInt(1)).isZero(); }
        assertThat(Files.readString(temp.resolve("PROJECT.md"))).isEqualTo("source remains owned by project");
    }
    @Test void staleAndInvalidSavesRollbackTheWholeWorksheet() {
        store.save(notes(1));
        assertThatThrownBy(() -> store.save(notes(1))).isInstanceOf(WorkflowConflictException.class);
        var invalid = new WorkflowNotes(id,2,Collections.nCopies(6,notes(1).phases().getFirst()),"changed","changed",now);
        assertThatThrownBy(() -> store.save(invalid)).isInstanceOf(CatalogException.class);
        assertThat(store.find(id)).contains(notes(1));
        catalog.remove(id); assertThatThrownBy(() -> store.save(notes(2))).isInstanceOf(CatalogException.class);
    }
    @Test void concurrentInitialSavesAllowOnlyOneWinner() throws Exception {
        try(var pool=Executors.newFixedThreadPool(2)) {
            var start = new CountDownLatch(1);
            Callable<Boolean> save = () -> { start.await(); try { store.save(notes(1)); return true; } catch(WorkflowConflictException ex) { return false; } };
            var first=pool.submit(save);var second=pool.submit(save);start.countDown();
            assertThat(List.of(first.get(10,TimeUnit.SECONDS),second.get(10,TimeUnit.SECONDS))).containsExactlyInAnyOrder(true,false);
        }
        assertThat(store.find(id)).contains(notes(1));
    }
    @Test void databaseFailuresAreSanitized() throws Exception {
        try(var c=source.getConnection();var s=c.createStatement()) { s.execute("DROP TABLE workflow_phase"); }
        assertThatThrownBy(() -> store.find(id)).isInstanceOf(CatalogException.class).hasMessageNotContaining(temp.toString());
        assertThatThrownBy(() -> store.save(notes(1))).isInstanceOf(CatalogException.class);
    }
}
