package org.kodvido.engine;
import org.junit.jupiter.api.Test;
import org.kodvido.domain.*;
import org.kodvido.domain.BackupAssessment.*;
import java.nio.file.Path;
import java.time.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
class BackupAssessmentServiceTest {
    final UUID id=UUID.randomUUID(); final Instant now=Instant.parse("2026-01-01T00:00:00Z");
    final List<CatalogProject> projects=new ArrayList<>(List.of(new CatalogProject(id,"fixture",Path.of("fixture"),now,now)));
    final Map<UUID,BackupAssessment> saved=new HashMap<>();
    final Catalog catalog=new Catalog() {
        public List<CatalogProject> list(){return projects;}
        public void addAll(List<CatalogProject> p){projects.addAll(p);}
        public void relocate(CatalogProject p){throw new UnsupportedOperationException();}
        public void remove(UUID id){projects.clear();}
    };
    final BackupAssessmentService service=new BackupAssessmentService(catalog,new BackupAssessmentStore(){
        public Optional<BackupAssessment> find(UUID id){return Optional.ofNullable(saved.get(id));}
        public void save(BackupAssessment a){saved.put(a.projectId(),a);}
    },Clock.fixed(now,ZoneOffset.UTC));
    List<Item> items(Item first){var list=new ArrayList<>(service.load(id).items());list.set(0,first);return list;}
    Item good(){return new Item(Dimension.GIT_REMOTE,State.DECLARED_OK,LocalDate.of(2026,1,1)," compared refs ");}
    @Test void defaultsAndExplicitDeclarationsNeverImplyVerification(){
        var empty=service.load(id);assertThat(empty.revision()).isZero();assertThat(empty.updatedAt()).isNull();
        assertThat(empty.items()).hasSize(4).allMatch(i->i.state()==State.UNKNOWN);
        var input=items(good());service.save(id,0,input);input.clear();
        var a=service.load(id);assertThat(a.items()).hasSize(4);assertThat(a.items().getFirst().evidence()).isEqualTo("compared refs");
        assertThat(a.updatedAt()).isEqualTo(now);assertThat(a.revision()).isEqualTo(1);
        assertThatThrownBy(()->a.items().clear()).isInstanceOf(UnsupportedOperationException.class);
        service.save(id,1,items(new Item(Dimension.GIT_REMOTE,State.DECLARED_FAILED,LocalDate.of(2025,1,1),"failed")));
        assertThat(service.load(id).items().getFirst().state()).isEqualTo(State.DECLARED_FAILED);
        projects.clear();assertThatThrownBy(()->service.load(id)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(()->service.save(id,0,List.of())).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void rejectsMalformedIncompleteAndFutureEvidenceBeforePersistence(){
        for(long rev:new long[]{-1,Long.MAX_VALUE-1,Long.MAX_VALUE})assertThatThrownBy(()->service.save(id,rev,items(good()))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(()->service.save(id,0,null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(()->service.save(id,0,List.of())).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(()->service.save(id,0,Collections.nCopies(4,good()))).isInstanceOf(IllegalArgumentException.class);
        var invalid=Arrays.asList(null,new Item(null,State.UNKNOWN,null,""),new Item(Dimension.GIT_REMOTE,null,null,""),
            new Item(Dimension.GIT_REMOTE,State.UNKNOWN,null,null),new Item(Dimension.GIT_REMOTE,State.UNKNOWN,null,"x".repeat(1001)),
            new Item(Dimension.GIT_REMOTE,State.UNKNOWN,null,"bad\0"),new Item(Dimension.GIT_REMOTE,State.UNKNOWN,LocalDate.of(2026,1,1),""),
            new Item(Dimension.GIT_REMOTE,State.DECLARED_OK,null,"proof"),new Item(Dimension.GIT_REMOTE,State.DECLARED_OK,LocalDate.of(2026,1,2),"proof"),
            new Item(Dimension.GIT_REMOTE,State.DECLARED_FAILED,LocalDate.of(2026,1,1)," "));
        for(var i:invalid)assertThatThrownBy(()->service.save(id,0,items(i))).isInstanceOf(IllegalArgumentException.class);
        assertThat(saved).isEmpty();
        service.save(id,0,items(new Item(Dimension.GIT_REMOTE,State.UNKNOWN,null,"x".repeat(1000))));
        assertThat(service.load(id).items().getFirst().evidence()).hasSize(1000);
    }
    @Test void versionTwoRoundtripAndVersionOneCompatibilityValidateImportedClaims(){
        service.save(id,0,items(good()));var assessment=service.load(id);
        final List<CatalogBackup.Entry> restored=new ArrayList<>();
        var entry=new CatalogBackup.Entry(id,"fixture",Path.of("fixture").toAbsolutePath().toString(),now,now,null,assessment);
        var backup=new BackupService(new BackupStore(){public List<CatalogBackup.Entry> snapshot(){return List.of(entry);}public void restore(List<CatalogBackup.Entry> es){restored.addAll(es);}},Clock.fixed(now,ZoneOffset.UTC));
        var exported=backup.export();assertThat(exported.version()).isEqualTo(2);
        var preview=backup.preview(exported);backup.confirm(preview.id(),List.of(entry.path()));assertThat(restored.getFirst().assessment()).isEqualTo(assessment);
        assertThatThrownBy(()->backup.preview(new CatalogBackup(1,now,List.of(entry)))).isInstanceOf(IllegalArgumentException.class);
        backup.preview(new CatalogBackup(1,now,List.of(new CatalogBackup.Entry(id,"legacy",entry.path(),now,now,null))));
        var bad=List.of(new BackupAssessment(UUID.randomUUID(),1,assessment.items(),now),
            new BackupAssessment(id,0,assessment.items(),now),new BackupAssessment(id,Long.MAX_VALUE,assessment.items(),now),
            new BackupAssessment(id,1,assessment.items(),null),new BackupAssessment(id,1,List.of(),now));
        for(var a:bad)assertThatThrownBy(()->backup.preview(new CatalogBackup(2,now,List.of(new CatalogBackup.Entry(id,"fixture",entry.path(),now,now,null,a))))).isInstanceOfAny(IllegalArgumentException.class,NullPointerException.class);
    }
}
