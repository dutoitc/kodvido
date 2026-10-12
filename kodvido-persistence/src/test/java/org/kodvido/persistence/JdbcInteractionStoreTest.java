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

class JdbcInteractionStoreTest {
    @TempDir Path temp;
    JdbcDataSource source;JdbcCatalog catalog;JdbcInteractionStore history;JdbcWorkflowStore workflow;
    final Instant now=Instant.parse("2026-01-01T00:00:00Z");final UUID id=UUID.randomUUID();
    CatalogProject original;
    @BeforeEach void setup() {
        source=new JdbcDataSource();source.setURL("jdbc:h2:file:"+temp.resolve("catalog"));
        Flyway.configure().dataSource(source).target("2").load().migrate();
        catalog=new JdbcCatalog(source);original=new CatalogProject(id,"fixture",temp.resolve("source"),now,now);catalog.addAll(List.of(original));
        Flyway.configure().dataSource(source).load().migrate();
        history=new JdbcInteractionStore(source);workflow=new JdbcWorkflowStore(source);
    }
    WorkflowNotes notes(long revision) { return new WorkflowNotes(id,revision,List.of(),"build","run",now.plusSeconds(revision)); }
    @Test void migrationCreatesNoInventedEventsAndHistorySurvivesReopenAndRelocation() throws Exception {
        Files.createDirectory(original.path());Files.writeString(original.path().resolve("PROJECT.md"),"untouched");
        assertThat(history.recent(id)).isEmpty();assertThat(catalog.list()).containsExactly(original);
        history.record(id,Interaction.Kind.INSPECTION_REQUESTED,now);
        workflow.save(notes(1));
        catalog.relocate(new CatalogProject(id,"fixture",temp.resolve("moved"),now,now.plusSeconds(2)));
        var events=new JdbcInteractionStore(source).recent(id);
        assertThat(events).extracting(Interaction::kind).containsExactly(Interaction.Kind.RELOCATED,Interaction.Kind.WORKFLOW_SAVED,Interaction.Kind.INSPECTION_REQUESTED);
        assertThat(events).extracting(Interaction::occurredAt).containsExactly(now.plusSeconds(2),now.plusSeconds(1),now);
        assertThatThrownBy(events::clear).isInstanceOf(UnsupportedOperationException.class);
        catalog.remove(id);assertThat(history.recent(id)).isEmpty();
        assertThat(Files.readString(original.path().resolve("PROJECT.md"))).isEqualTo("untouched");
    }
    @Test void staleOrInvalidSavesAndFailedRelocationsHaveNoEvent() {
        workflow.save(notes(1));
        assertThatThrownBy(()->workflow.save(notes(1))).isInstanceOf(WorkflowConflictException.class);
        var invalid=new WorkflowNotes(id,2,Collections.nCopies(6,new WorkflowNotes.Phase(LocalDate.of(2026,1,1),"phase",WorkflowNotes.State.DONE)),"bad","bad",now);
        assertThatThrownBy(()->workflow.save(invalid)).isInstanceOf(CatalogException.class);
        var other=new CatalogProject(UUID.randomUUID(),"other",temp.resolve("other"),now,now);catalog.addAll(List.of(other));
        assertThatThrownBy(()->catalog.relocate(new CatalogProject(id,"fixture",other.path(),now,now))).isInstanceOf(CatalogException.class);
        assertThat(history.recent(id)).hasSize(1);assertThat(workflow.find(id)).contains(notes(1));
        assertThatThrownBy(()->history.record(UUID.randomUUID(),Interaction.Kind.INSPECTION_REQUESTED,now)).isInstanceOf(CatalogException.class);
    }
    @Test void historyFailureRollsBackBusinessMutationsAndErrorsAreSanitized() throws Exception {
        workflow.save(notes(1));
        try(var c=source.getConnection();var s=c.createStatement()) { s.execute("DROP TABLE project_interaction"); }
        assertThatThrownBy(()->workflow.save(notes(2))).isInstanceOf(CatalogException.class).hasMessageNotContaining(temp.toString());
        assertThat(workflow.find(id)).contains(notes(1));
        assertThatThrownBy(()->catalog.relocate(new CatalogProject(id,"fixture",temp.resolve("moved"),now,now))).isInstanceOf(CatalogException.class);
        assertThat(catalog.list()).containsExactly(original);
        assertThatThrownBy(()->history.record(id,Interaction.Kind.INSPECTION_REQUESTED,now)).isInstanceOf(CatalogException.class);
        assertThatThrownBy(()->history.recent(id)).isInstanceOf(CatalogException.class);
    }
    @Test void retentionIsPerProjectAndConcurrentRequestsKeepExactlyLatestHundred() throws Exception {
        var other=new CatalogProject(UUID.randomUUID(),"other",temp.resolve("other"),now,now);catalog.addAll(List.of(other));
        try(var keepAlive=source.getConnection();var pool=Executors.newFixedThreadPool(2)) {
            history.record(other.id(),Interaction.Kind.INSPECTION_REQUESTED,now);
            for(int i=0;i<100;i++) history.record(id,Interaction.Kind.INSPECTION_REQUESTED,now.plusSeconds(i));
            var before=history.recent(id);assertThat(before).hasSize(100);
            var start=new CountDownLatch(1);
            Callable<Void> record=()->{start.await();for(int i=0;i<10;i++)history.record(id,Interaction.Kind.INSPECTION_REQUESTED,now.minusSeconds(i));return null;};
            var a=pool.submit(record);var b=pool.submit(record);start.countDown();a.get(20,TimeUnit.SECONDS);b.get(20,TimeUnit.SECONDS);
            var events=history.recent(id);assertThat(events).hasSize(100);
            assertThat(events).extracting(Interaction::sequence).isSortedAccordingTo(Comparator.reverseOrder()).doesNotHaveDuplicates();
            assertThat(events.getFirst().occurredAt()).isBeforeOrEqualTo(now); // order is recording sequence, not wall-clock sorting.
            assertThat(events.getLast().sequence()).isGreaterThan(before.getLast().sequence());
            assertThat(history.recent(other.id())).hasSize(1);
            try(var s=keepAlive.createStatement();var rows=s.executeQuery("SELECT COUNT(*) FROM project_interaction")) { rows.next();assertThat(rows.getInt(1)).isEqualTo(101); }
        }
    }
    @Test void metadataRestorePreservesNotesButDoesNotFabricateInteractionHistory() {
        workflow.save(notes(1));history.record(id,Interaction.Kind.INSPECTION_REQUESTED,now);
        var backup=new JdbcBackupStore(source);var snapshot=backup.snapshot();catalog.remove(id);backup.restore(snapshot);
        assertThat(workflow.find(id)).contains(notes(1));assertThat(history.recent(id)).isEmpty();
    }
}
