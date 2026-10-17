package org.kodvido.engine;
import org.junit.jupiter.api.Test;
import org.kodvido.domain.*;
import org.kodvido.engine.CatalogOverviewService.*;
import java.nio.file.Path;
import java.time.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
class CatalogOverviewServiceTest {
    final CatalogServiceTest.MemoryCatalog catalog=new CatalogServiceTest.MemoryCatalog();
    final ObservationCache cache=new ObservationCache();final Instant now=Instant.parse("2026-01-01T00:00:00Z");
    final Map<UUID,WorkflowNotes> notes=new HashMap<>();final Map<UUID,BackupAssessment> backups=new HashMap<>();final Map<UUID,List<Interaction>> events=new HashMap<>();
    final CatalogOverviewService service=new CatalogOverviewService(catalog,new WorkflowStore(){
        public Optional<WorkflowNotes> find(UUID id){return Optional.ofNullable(notes.get(id));}public void save(WorkflowNotes n){notes.put(n.projectId(),n);}
    },new BackupAssessmentStore(){public Optional<BackupAssessment> find(UUID id){return Optional.ofNullable(backups.get(id));}public void save(BackupAssessment a){backups.put(a.projectId(),a);}},
        new InteractionStore(){public List<Interaction> recent(UUID id){return events.getOrDefault(id,List.of());}public void record(UUID id,Interaction.Kind k,Instant t){throw new AssertionError("read-only");}},cache);
    CatalogProject project(String name){var p=new CatalogProject(UUID.randomUUID(),name,Path.of("missing",name),now,now);catalog.addAll(List.of(p));return p;}
    void observe(CatalogProject p,String technology,String declaration,Instant time){
        cache.remember(p,new ProjectInspection(time,"fixture",ProjectInspection.Status.PARTIAL,8,2,3,time,List.of(),List.of()),
            new GitObservation(ProjectInspection.Status.SUPPORTED,"hash",time,time,"fixture",""),
            new StackObservation(time,"fixture",ProjectInspection.Status.PARTIAL,List.of(new StackObservation.Evidence(technology,"pom.xml",declaration)),List.of("build.sh"),List.of()));
    }
    Page list(String q,Technology t,Filter f,Sort s,int page){return service.list(q,t,f,s,page);}
    @Test void filtersAndSortingUseOnlyStoredMetadataAndExplicitObservations(){
        var java=project("Alpha");var python=project("beta");var unseen=project("gamma");observe(java,"Java","21",now);observe(python,"Python",">=3.12",now.plusSeconds(1));
        notes.put(java.id(),new WorkflowNotes(java.id(),1,List.of(new WorkflowNotes.Phase(LocalDate.of(2026,1,1),"phase",WorkflowNotes.State.DONE)),"build","run",now));
        notes.put(python.id(),new WorkflowNotes(python.id(),1,List.of()," ","",now));
        backups.put(java.id(),new BackupAssessment(java.id(),1,List.of(new BackupAssessment.Item(BackupAssessment.Dimension.RESTORE_TEST,BackupAssessment.State.DECLARED_FAILED,LocalDate.of(2026,1,1),"failed")),now));
        backups.put(python.id(),new BackupAssessment(python.id(),1,List.of(new BackupAssessment.Item(BackupAssessment.Dimension.GIT_REMOTE,BackupAssessment.State.UNKNOWN,null,"")),now));
        events.put(java.id(),List.of(new Interaction(1,java.id(),Interaction.Kind.WORKFLOW_SAVED,now)));
        events.put(python.id(),List.of(new Interaction(2,python.id(),Interaction.Kind.RELOCATED,now.plusSeconds(1))));
        var all=list("",Technology.ALL,Filter.ALL,Sort.NAME,0);assertThat(all.total()).isEqualTo(3);assertThat(all.rows()).extracting(v->v.project().name()).containsExactly("Alpha","beta","gamma");
        assertThat(all.rows().getFirst().phases()).isEqualTo(1);assertThat(all.rows().getFirst().development()).isTrue();assertThat(all.rows().getFirst().operations()).isTrue();
        assertThat(all.rows().getLast().unknownBackups()).isEqualTo(4);assertThat(all.rows().getLast().interactionAt()).isNull();
        assertThat(list(" ALPHA ",Technology.ALL,Filter.ALL,Sort.NAME,0).filtered()).isEqualTo(1);
        assertThat(list("missing",Technology.ALL,Filter.ALL,Sort.NAME,0).filtered()).isEqualTo(3);
        assertThat(list("21",Technology.JAVA,Filter.OBSERVED,Sort.NAME,0).rows()).extracting(Row::project).containsExactly(java);
        assertThat(list("",Technology.PYTHON,Filter.ALL,Sort.NAME,0).rows()).extracting(Row::project).containsExactly(python);
        assertThat(list("",Technology.ALL,Filter.UNOBSERVED,Sort.NAME,0).rows()).extracting(Row::project).containsExactly(unseen);
        assertThat(list("",Technology.ALL,Filter.BACKUP_FAILED,Sort.NAME,0).rows()).extracting(Row::project).containsExactly(java);
        assertThat(list("",Technology.ALL,Filter.BACKUP_UNKNOWN,Sort.NAME,0).rows()).extracting(Row::project).containsExactly(python,unseen);
        for(var sort:List.of(Sort.OBSERVED,Sort.INTERACTION))assertThat(list("",Technology.ALL,Filter.ALL,sort,0).rows()).extracting(Row::project).containsExactly(python,java,unseen);
        assertThat(list("absent",Technology.ALL,Filter.ALL,Sort.NAME,0).rows()).isEmpty();
    }
    @Test void paginationClampsAndInvalidQueriesFailWithoutFilesystemAccess(){
        assertThat(list("",Technology.ALL,Filter.ALL,Sort.NAME,99).page()).isZero();
        for(int i=0;i<26;i++)project(String.format(Locale.ROOT,"fixture%02d",i));
        var first=list("",Technology.ALL,Filter.ALL,Sort.NAME,0);var last=list("",Technology.ALL,Filter.ALL,Sort.NAME,Integer.MAX_VALUE);
        assertThat(first.rows()).hasSize(25);assertThat(last.rows()).hasSize(1);assertThat(last.page()).isEqualTo(1);assertThat(last.pages()).isEqualTo(2);
        assertThatThrownBy(()->first.rows().clear()).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(()->list("x".repeat(201),Technology.ALL,Filter.ALL,Sort.NAME,0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(()->list("",Technology.ALL,Filter.ALL,Sort.NAME,-1)).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void cacheIsBoundedInvalidatedByIdentityChangesAndLostOnRestart(){
        var first=project("first");observe(first,"Java","x".repeat(200),now);
        assertThat(cache.find(first).orElseThrow().evidence().getFirst().declaration()).hasSize(161).endsWith("…");
        observe(first,"Java","21",now);assertThat(cache.find(first).orElseThrow().files()).isEqualTo(8);
        assertThat(new ObservationCache().find(first)).isEmpty();
        var moved=new CatalogProject(first.id(),first.name(),Path.of("elsewhere"),now,now);assertThat(cache.find(moved)).isEmpty();assertThat(cache.find(first)).isEmpty();
        observe(first,"Java","21",now);cache.forget(first.id());assertThat(cache.find(first)).isEmpty();
        observe(first,"Java","21",now);
        for(int i=0;i<1000;i++)observe(project("fixture"+i),"Python","3.12",now);
        assertThat(cache.find(first)).isEmpty();
    }
}
