package org.kodvido.engine;
import org.junit.jupiter.api.*;
import org.kodvido.domain.*;
import java.nio.file.Path;
import java.time.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

class BackupServiceTest {
    final Instant now=Instant.parse("2026-01-01T00:00:00Z");
    final UUID id=UUID.randomUUID();
    Instant current=now;
    List<CatalogBackup.Entry> saved=List.of();
    BackupStore store=new BackupStore() {
        public List<CatalogBackup.Entry> snapshot() { return saved; }
        public void restore(List<CatalogBackup.Entry> entries) { saved=entries; }
    };
    BackupService service=new BackupService(store,new Clock() {
        public ZoneId getZone() { return ZoneOffset.UTC; }
        public Clock withZone(ZoneId zone) { return this; }
        public Instant instant() { return current; }
    });
    WorkflowNotes notes() { return new WorkflowNotes(id,7,List.of(new WorkflowNotes.Phase(LocalDate.of(2025,12,1),"done",WorkflowNotes.State.DONE)),"build","run",now); }
    CatalogBackup.Entry entry(WorkflowNotes n) { return new CatalogBackup.Entry(id,"fixture","C:\\old\\project",now,now,n); }
    CatalogBackup backup(CatalogBackup.Entry... entries) { return new CatalogBackup(1,now,List.of(entries)); }
    String nativePath() { return Path.of("fixture").toAbsolutePath().toString(); }
    void invalid(CatalogBackup b) { assertThatThrownBy(()->service.preview(b)).isInstanceOfAny(IllegalArgumentException.class,NullPointerException.class); }
    @Test void previewRequiresConfirmationPreservesNotesAndAllowsMissingNativePaths() {
        assertThat(service.export().projects()).isEmpty();
        var p=service.preview(backup(entry(notes()))); assertThat(saved).isEmpty();
        service.confirm(p.id(),List.of(nativePath()));
        assertThat(saved.getFirst().notes()).isEqualTo(notes());
        assertThat(saved.getFirst().path()).isEqualTo(nativePath());
        assertThat(service.export().projects()).isEqualTo(saved);
        assertThatThrownBy(()->service.confirm(p.id(),List.of(nativePath()))).isInstanceOf(IllegalArgumentException.class);
        var empty=service.preview(backup()); service.confirm(empty.id(),List.of()); assertThat(saved).isEmpty();
        assertThatThrownBy(()->p.backup().projects().clear()).isInstanceOf(UnsupportedOperationException.class);
    }
    @Test void tokensExpireAreReplacedAndPathsAreValidated() {
        var old=service.preview(backup(entry(null))); var p=service.preview(backup(entry(null)));
        assertThatThrownBy(()->service.confirm(old.id(),List.of(nativePath()))).hasMessageContaining("remplacé");
        assertThatThrownBy(()->service.confirm(p.id(),List.of())).hasMessageContaining("chemin");
        assertThatThrownBy(()->service.confirm(p.id(),List.of("relative"))).hasMessageContaining("absolu");
        assertThatThrownBy(()->service.confirm(p.id(),List.of("bad\0"))).hasMessageContaining("invalide");
        current=now.plusSeconds(1800); assertThatThrownBy(()->service.confirm(p.id(),List.of(nativePath()))).hasMessageContaining("expiré");
        var second=new CatalogBackup.Entry(UUID.randomUUID(),"second","/other",now,now,null);
        var two=service.preview(backup(entry(null),second));
        assertThatThrownBy(()->service.confirm(two.id(),List.of(nativePath(),nativePath()))).hasMessageContaining("dupliqués");
        assertThat(saved).isEmpty();
    }
    @Test void rejectsInvalidEnvelopeIdentityAndText() {
        invalid(new CatalogBackup(3,now,List.of())); invalid(new CatalogBackup(1,null,List.of()));
        invalid(new CatalogBackup(1,now,Collections.nCopies(1001,entry(null)))); invalid(backup(entry(null),entry(null)));
        invalid(backup(new CatalogBackup.Entry(null,"fixture","/x",now,now,null)));
        invalid(backup(new CatalogBackup.Entry(id,"fixture","/x",null,now,null)));
        invalid(backup(new CatalogBackup.Entry(id,"fixture","/x",now,null,null)));
        for(String name:Arrays.asList(null," ","x".repeat(251),"a\0")) invalid(backup(new CatalogBackup.Entry(id,name,"/x",now,now,null)));
        for(String path:Arrays.asList(null,"","x".repeat(4001))) invalid(backup(new CatalogBackup.Entry(id,"fixture",path,now,now,null)));
    }
    @Test void rejectsMalformedWorkflowWithoutChangingSavedData() {
        var n=notes();
        invalid(backup(entry(new WorkflowNotes(UUID.randomUUID(),1,n.phases(),"","",now))));
        for(long revision:new long[]{0,-1,Long.MAX_VALUE}) invalid(backup(entry(new WorkflowNotes(id,revision,n.phases(),"","",now))));
        invalid(backup(entry(new WorkflowNotes(id,1,n.phases(),"","",null))));
        invalid(backup(entry(new WorkflowNotes(id,1,Collections.nCopies(6,n.phases().getFirst()),"","",now))));
        for(String bad:Arrays.asList(null,"x".repeat(4001),"\0")) {
            invalid(backup(entry(new WorkflowNotes(id,1,List.of(),bad,"",now))));
            invalid(backup(entry(new WorkflowNotes(id,1,List.of(),"",bad,now))));
        }
        for(var phase:List.of(new WorkflowNotes.Phase(null,"x",WorkflowNotes.State.DONE),new WorkflowNotes.Phase(LocalDate.now(),"x",null),
            new WorkflowNotes.Phase(LocalDate.now(),"",WorkflowNotes.State.DONE)))
            invalid(backup(entry(new WorkflowNotes(id,1,List.of(phase),"","",now))));
        service.preview(backup(entry(new WorkflowNotes(id,1,List.of(),"","",now))));
        assertThat(saved).isEmpty();
    }
}
