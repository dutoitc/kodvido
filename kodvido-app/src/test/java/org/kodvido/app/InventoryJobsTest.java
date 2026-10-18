package org.kodvido.app;
import org.junit.jupiter.api.Test;
import org.kodvido.domain.*;
import org.kodvido.domain.Inventory.*;
import org.kodvido.engine.InventoryService;
import java.util.*;
import java.time.*;
import java.nio.file.Path;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;
class InventoryJobsTest {
 @Test void backgroundJobsDoNotOverlapAndExposeCompletionOrFailure() throws Exception {
  var p=new CatalogProject(UUID.randomUUID(),"fixture",Path.of("absent"),Instant.EPOCH,Instant.EPOCH);
  var catalog=new Catalog(){public List<CatalogProject> list(){return List.of(p);}public void addAll(List<CatalogProject> v){}public void remove(UUID id){}public void relocate(CatalogProject v){}};
  var stored=new CountDownLatch(1);var started=new CountDownLatch(1);var release=new CountDownLatch(1);
  var store=new Store(){public List<String> roots(){return List.of();}public void roots(List<String> v){}public Optional<Snapshot> snapshot(CatalogProject v){return Optional.empty();}public void snapshot(CatalogProject v,Snapshot s){stored.countDown();}public Notes notes(UUID id){return Notes.empty();}public void notes(UUID id,Notes n){}};
  var service=new InventoryService(catalog,store,(path,versions)->{started.countDown();try{release.await(3,TimeUnit.SECONDS);}catch(InterruptedException e){Thread.currentThread().interrupt();}return new Snapshot(Instant.now().toString(),"MISSING",0,null,"Non vérifié",List.of(),List.of(),List.of(),List.of());});
  var jobs=new InventoryJobs(service,true);
  try {jobs.imported();assertThat(started.await(3,TimeUnit.SECONDS)).isTrue();assertThat(jobs.submit(p.id(),false)).isFalse();assertThat(jobs.state()).contains("en cours");release.countDown();assertThat(stored.await(3,TimeUnit.SECONDS)).isTrue();}
  finally {release.countDown();jobs.stop();}
  var failing=new InventoryJobs(new InventoryService(catalog,store,(path,versions)->{throw new IllegalStateException("private exception");}),false);
  try {failing.start();failing.imported();assertThat(failing.state()).isEqualTo("En attente");assertThat(failing.submit(p.id(),true)).isTrue();
   long until=System.nanoTime()+TimeUnit.SECONDS.toNanos(3);while(!failing.state().startsWith("Échec") && System.nanoTime()<until) Thread.sleep(10);
   assertThat(failing.state()).startsWith("Échec").doesNotContain("private exception");
  }finally{failing.stop();}
 }
}
