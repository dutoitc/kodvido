package org.kodvido.persistence;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.kodvido.domain.*;
import org.kodvido.domain.Inventory.*;
import org.h2.jdbcx.JdbcDataSource;
import org.flywaydb.core.Flyway;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
class JdbcInventoryStoreTest {
 @TempDir Path temp;JdbcDataSource source;JdbcCatalog catalog;JdbcInventoryStore store;CatalogProject p;
 @BeforeEach void setup(){source=new JdbcDataSource();source.setURL("jdbc:h2:file:"+temp.resolve("db"));Flyway.configure().dataSource(source).load().migrate();catalog=new JdbcCatalog(source);store=new JdbcInventoryStore(source);p=new CatalogProject(UUID.randomUUID(),"demo",temp.resolve("project"),Instant.EPOCH,Instant.EPOCH);catalog.addAll(List.of(p));}
 Snapshot snapshot(){return new Snapshot("2026-10-17T10:00:00Z","PARTIAL",15,null,"Propre",List.of(new Technology("Java","21","pom.xml")),List.of(),List.of(),List.of());}
 @Test void survivesReopenAndInvalidatesRelocationWithNonDestructiveRemoval() throws Exception {
  Files.createDirectory(p.path());Files.writeString(p.path().resolve("README.md"),"keep");
  assertThat(store.snapshot(p)).isEmpty();assertThat(store.notes(p.id())).isEqualTo(Notes.empty());
  store.roots(List.of(temp.toString()));store.snapshot(p,snapshot());store.notes(p.id(),new Notes(Lifecycle.PAUSED,"next","task",0));
  store=new JdbcInventoryStore(source);assertThat(store.roots()).containsExactly(temp.toString());assertThat(store.snapshot(p)).contains(snapshot());assertThat(store.notes(p.id()).revision()).isEqualTo(1);
  var moved=new CatalogProject(p.id(),p.name(),temp.resolve("moved"),p.createdAt(),Instant.now());catalog.relocate(moved);store.snapshot(p,snapshot());assertThat(store.snapshot(moved)).isEmpty();assertThat(store.snapshot(p)).isEmpty();assertThat(store.notes(p.id()).note()).isEqualTo("next");
  var returned=new CatalogProject(p.id(),p.name(),p.path(),p.createdAt(),Instant.now());catalog.relocate(returned);store.snapshot(p,snapshot());assertThat(store.snapshot(returned)).isEmpty();
  catalog.remove(p.id());store.snapshot(moved,snapshot());assertThat(store.snapshot(moved)).isEmpty();assertThat(Files.readString(p.path().resolve("README.md"))).isEqualTo("keep");
 }
 @Test void staleAndInvalidWritesRollbackAndErrorsAreSanitized() throws Exception {
  store.notes(p.id(),new Notes(Lifecycle.ACTIVE,"one","",0));
  assertThatThrownBy(()->store.notes(p.id(),new Notes(Lifecycle.ARCHIVED,"two","",0))).isInstanceOf(IllegalArgumentException.class);
  assertThat(store.notes(p.id()).note()).isEqualTo("one");
  assertThatThrownBy(()->store.notes(UUID.randomUUID(),Notes.empty())).isInstanceOf(IllegalArgumentException.class);
  store.roots(List.of("keep"));assertThatThrownBy(()->store.roots(Arrays.asList("new",null))).isInstanceOf(CatalogException.class);assertThat(store.roots()).containsExactly("keep");
  store.snapshot(p,snapshot());try(var c=source.getConnection();var s=c.createStatement()){s.execute("UPDATE inventory_snapshot SET payload='bad'");}assertThatThrownBy(()->store.snapshot(p)).isInstanceOf(CatalogException.class);
  try(var c=source.getConnection();var s=c.createStatement()){s.execute("DROP TABLE inventory_root");s.execute("DROP TABLE inventory_snapshot");s.execute("DROP TABLE inventory_note");}
  assertThatThrownBy(store::roots).isInstanceOf(CatalogException.class);assertThatThrownBy(()->store.roots(List.of())).isInstanceOf(CatalogException.class);
  assertThatThrownBy(()->store.snapshot(p)).isInstanceOf(CatalogException.class);assertThatThrownBy(()->store.snapshot(p,snapshot())).isInstanceOf(CatalogException.class);
  assertThatThrownBy(()->store.notes(p.id())).isInstanceOf(CatalogException.class);assertThatThrownBy(()->store.notes(p.id(),Notes.empty())).isInstanceOf(CatalogException.class);
 }
 @Test void portableBackupRestoresLifecycleNotesAndTasks() {
  store.notes(p.id(),new Notes(Lifecycle.ARCHIVED,"do this","task one\ntask two",0));
  var backup=new JdbcBackupStore(source);var entries=backup.snapshot();assertThat(entries.getFirst().inventoryNotes().note()).isEqualTo("do this");
  catalog.remove(p.id());backup.restore(entries);assertThat(store.notes(p.id())).isEqualTo(entries.getFirst().inventoryNotes());
 }
}
