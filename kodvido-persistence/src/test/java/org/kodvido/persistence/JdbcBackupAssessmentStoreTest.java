package org.kodvido.persistence;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.kodvido.domain.*;
import org.kodvido.domain.BackupAssessment.*;
import org.h2.jdbcx.JdbcDataSource;
import org.flywaydb.core.Flyway;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;
class JdbcBackupAssessmentStoreTest {
    @TempDir Path temp;JdbcDataSource source;JdbcCatalog catalog;JdbcBackupAssessmentStore store;
    final UUID id=UUID.randomUUID();final Instant now=Instant.parse("2026-01-01T00:00:00Z");
    @BeforeEach void setup(){
        source=new JdbcDataSource();source.setURL("jdbc:h2:file:"+temp.resolve("catalog"));
        Flyway.configure().dataSource(source).target("3").load().migrate();catalog=new JdbcCatalog(source);
        catalog.addAll(List.of(new CatalogProject(id,"fixture",temp,now,now)));
        Flyway.configure().dataSource(source).load().migrate();store=new JdbcBackupAssessmentStore(source);
    }
    BackupAssessment assessment(long revision){return new BackupAssessment(id,revision,Arrays.stream(Dimension.values())
        .map(d->d==Dimension.RESTORE_TEST?new Item(d,State.UNKNOWN,null,"not tested"):new Item(d,State.DECLARED_OK,LocalDate.of(2026,1,1),"evidence, scoped to fixture")).toList(),now);}
    @Test void migrationReopenRelocationExportRestoreAndRemoval()throws Exception{
        Files.writeString(temp.resolve("PROJECT.md"),"untouched");assertThat(store.find(id)).isEmpty();store.save(assessment(1));
        assertThat(new JdbcBackupAssessmentStore(source).find(id)).contains(assessment(1));
        catalog.relocate(new CatalogProject(id,"fixture",temp.resolve("moved"),now,now));assertThat(store.find(id)).contains(assessment(1));
        var transfer=new JdbcBackupStore(source);var snapshot=transfer.snapshot();catalog.remove(id);assertThat(store.find(id)).isEmpty();
        transfer.restore(snapshot);assertThat(store.find(id)).contains(assessment(1));store.save(assessment(2));
        assertThat(store.find(id)).contains(assessment(2));assertThat(Files.readString(temp.resolve("PROJECT.md"))).isEqualTo("untouched");
        catalog.remove(id);try(var c=source.getConnection();var s=c.createStatement();var rows=s.executeQuery("SELECT COUNT(*) FROM backup_evidence")){rows.next();assertThat(rows.getInt(1)).isZero();}
    }
    @Test void staleInvalidAndFailedRestoresRollbackEverything(){
        store.save(assessment(1));assertThatThrownBy(()->store.save(assessment(1))).isInstanceOf(BackupAssessmentConflictException.class);
        var invalid=new BackupAssessment(id,2,Collections.nCopies(4,assessment(1).items().getFirst()),now);
        assertThatThrownBy(()->store.save(invalid)).isInstanceOf(CatalogException.class);assertThat(store.find(id)).contains(assessment(1));
        catalog.remove(id);var transfer=new JdbcBackupStore(source);
        assertThatThrownBy(()->transfer.restore(List.of(new CatalogBackup.Entry(id,"fixture",temp.toString(),now,now,null,invalid)))).isInstanceOf(CatalogException.class);
        assertThat(catalog.list()).isEmpty();assertThatThrownBy(()->store.save(assessment(1))).isInstanceOf(CatalogException.class);
    }
    @Test void concurrentFirstSavesHaveOneWinner()throws Exception{
        try(var pool=Executors.newFixedThreadPool(2)){
            var start=new CountDownLatch(1);Callable<Boolean> save=()->{start.await();try{store.save(assessment(1));return true;}catch(BackupAssessmentConflictException ex){return false;}};
            var first=pool.submit(save);var second=pool.submit(save);start.countDown();
            assertThat(List.of(first.get(10,TimeUnit.SECONDS),second.get(10,TimeUnit.SECONDS))).containsExactlyInAnyOrder(true,false);
        }
    }
    @Test void sqlErrorsAreSanitized()throws Exception{
        try(var c=source.getConnection();var s=c.createStatement()){s.execute("DROP TABLE backup_evidence");}
        assertThatThrownBy(()->store.find(id)).isInstanceOf(CatalogException.class).hasMessageNotContaining(temp.toString());
        assertThatThrownBy(()->store.save(assessment(1))).isInstanceOf(CatalogException.class);
    }
}
