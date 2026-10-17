package org.kodvido.engine;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.kodvido.domain.*;
import org.kodvido.domain.Inventory.*;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
class InventoryServiceTest {
 @TempDir Path temp;
 final CatalogServiceTest.MemoryCatalog catalog=new CatalogServiceTest.MemoryCatalog();
 static class Memory implements Store {
  List<String> roots=List.of();Map<UUID,Snapshot> snapshots=new HashMap<>();Map<UUID,Notes> notes=new HashMap<>();
  public List<String> roots(){return roots;}public void roots(List<String> values){roots=values;}
  public Optional<Snapshot> snapshot(CatalogProject p){return Optional.ofNullable(snapshots.get(p.id()));}
  public void snapshot(CatalogProject p,Snapshot s){snapshots.put(p.id(),s);}
  public Notes notes(UUID id){return notes.getOrDefault(id,Notes.empty());}public void notes(UUID id,Notes n){notes.put(id,n);}
 }
 Memory store=new Memory();int calls;
 InventoryService service=new InventoryService(catalog,store,(path,versions)->{calls++;return snapshot(path.toString());});
 Snapshot snapshot(String name){return new Snapshot("2026-10-17T10:00:00Z","PARTIAL",42,"2026-01-01T00:00:00Z","Propre",List.of(new Technology("Java","17",name),new Technology("Java","11",name),new Technology("Angular","20",name)),List.of(),List.of(new Remote("origin","https://example.org/a",RemoteState.ACCESSIBLE,"2026-10-17T10:00:00Z")),List.of());}
 CatalogProject project(String name){var p=new CatalogProject(UUID.randomUUID(),name,temp.resolve(name),Instant.EPOCH,Instant.EPOCH);catalog.addAll(List.of(p));return p;}
 @Test void filtersAndEverySortUsePersistedObservationsWithoutRescanning(){
  var a=project("Alpha");var b=project("beta");service.refresh(a.id(),false);service.notes(a.id(),Lifecycle.PAUSED," next "," task ",0);
  assertThat(service.row(a.id()).versions("Java")).isEqualTo("11\n17");assertThat(service.row(b.id()).remoteState()).isEqualTo("NOT_CHECKED");
  for(var sort:InventoryService.Sort.values()) {assertThat(service.rows("","","","",sort,false)).hasSize(2);assertThat(service.rows("","","","",sort,true)).hasSize(2);}
  assertThat(service.rows("next","PAUSED","Java","ACCESSIBLE",InventoryService.Sort.NAME,false)).extracting(r->r.project().id()).containsExactly(a.id());
  assertThat(service.rows("missing","","","",InventoryService.Sort.NAME,false)).isEmpty();
  assertThat(service.rows("","ACTIVE","Java","",InventoryService.Sort.NAME,false)).isEmpty();
  assertThat(service.rows("","","Python","",InventoryService.Sort.NAME,false)).isEmpty();
  assertThat(service.rows("","","","ERROR",InventoryService.Sort.NAME,false)).isEmpty();assertThat(calls).isEqualTo(1);
  assertThat(service.row(a.id()).unresolvedVersions()).isZero();assertThat(service.row(a.id()).comparisons()).isZero();assertThat(service.row(a.id()).versionsChecked()).isFalse();
  assertThat(service.row(b.id()).unresolvedVersions()).isZero();assertThat(service.row(b.id()).comparisons()).isZero();assertThat(service.row(b.id()).versionsChecked()).isFalse();
  service.refresh(a.id(),false);service.refresh(a.id(),true);
 }
 @Test void rootsArePersistedAndDiscoveryRequiresConfirmation() throws Exception {
  Files.writeString(temp.resolve("package.json"),"{}");service.roots(List.of(temp.toString(),temp.toString()));assertThat(service.roots()).hasSize(1);
  service.refreshAll();assertThat(service.discovery().candidates()).hasSize(1);assertThat(catalog.list()).isEmpty();
  var p=new CatalogProject(UUID.randomUUID(),"root",temp.toRealPath(),Instant.EPOCH,Instant.EPOCH);catalog.addAll(List.of(p));service.refreshAll();assertThat(service.discovery().candidates()).isEmpty();assertThat(calls).isEqualTo(1);
  Thread.currentThread().interrupt();try{service.refreshAll();assertThat(calls).isEqualTo(1);}finally{Thread.interrupted();}
 }
 @Test void invalidNotesPathsAndUnknownProjectsAreRejected(){
  var p=project("a");assertThatThrownBy(()->service.roots(Collections.nCopies(21,temp.toString()))).isInstanceOf(IllegalArgumentException.class);
  assertThatThrownBy(()->service.roots(List.of("relative"))).isInstanceOf(IllegalArgumentException.class);
  assertThatThrownBy(()->service.rows("x".repeat(201),"","","",InventoryService.Sort.NAME,false)).isInstanceOf(IllegalArgumentException.class);
  assertThatThrownBy(()->service.row(UUID.randomUUID())).isInstanceOf(IllegalArgumentException.class);
  assertThatThrownBy(()->service.notes(p.id(),null,"","",0)).isInstanceOf(IllegalArgumentException.class);
  for(String note:Arrays.asList(null,"x".repeat(1001),"bad\0")) assertThatThrownBy(()->service.notes(p.id(),Lifecycle.ACTIVE,note,"",0)).isInstanceOf(IllegalArgumentException.class);
  for(String tasks:Arrays.asList(null,"x".repeat(10001),"bad\0")) assertThatThrownBy(()->service.notes(p.id(),Lifecycle.ACTIVE,"",tasks,0)).isInstanceOf(IllegalArgumentException.class);
  assertThatThrownBy(()->service.notes(p.id(),Lifecycle.ACTIVE,"","",Long.MAX_VALUE)).isInstanceOf(IllegalArgumentException.class);
  assertThatThrownBy(()->service.notes(p.id(),Lifecycle.ACTIVE,"","",-1)).isInstanceOf(IllegalArgumentException.class);
 }
 @Test void scheduledRefreshRetainsOnlyComparisonsForUnchangedDeclarations() {
  var p=project("deps");var checked=new Dependency("org.example","a","1","pom.xml","2","COMPARE_RELEASE","2026-10-16T00:00:00Z");
  var old=snapshot("pom.xml");store.snapshot(p,new Snapshot(old.observedAt(),old.status(),old.lines(),null,"Propre",old.technologies(),List.of(checked),old.remotes(),List.of()));
  var service=new InventoryService(catalog,store,(path,versions)->new Snapshot("2026-10-17T00:00:00Z","PARTIAL",1,null,"Propre",List.of(),List.of(new Dependency("org.example","a","1","pom.xml","","NOT_CHECKED"),new Dependency("org.example","b","1","pom.xml","","NOT_CHECKED")),List.of(),List.of()));
  service.refresh(p.id(),false);assertThat(service.row(p.id()).snapshot().dependencies().getFirst()).isEqualTo(checked);
  assertThat(service.row(p.id()).unresolvedVersions()).isEqualTo(1);assertThat(service.row(p.id()).comparisons()).isEqualTo(1);assertThat(service.row(p.id()).versionsChecked()).isTrue();
  service.refresh(p.id(),true);assertThat(service.row(p.id()).snapshot().dependencies().getFirst().checkedAt()).isNull();
 }
}
