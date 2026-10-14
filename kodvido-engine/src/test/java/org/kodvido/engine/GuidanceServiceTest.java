package org.kodvido.engine;
import org.junit.jupiter.api.Test;
import org.kodvido.domain.*;
import org.kodvido.domain.ProjectGuidance.*;
import org.kodvido.domain.ProjectInspection.Status;
import java.time.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
class GuidanceServiceTest {
    final GuidanceService service=new GuidanceService();final UUID id=UUID.randomUUID();
    final String secret="/home/private/client/token-XY<script>payload</script>";
    WorkflowNotes notes(String development,String operations){return new WorkflowNotes(id,1,List.of(),development,operations,Instant.EPOCH);}
    BackupAssessment backup(BackupAssessment.State state){return new BackupAssessment(id,1,Arrays.stream(BackupAssessment.Dimension.values())
        .map(d->new BackupAssessment.Item(d,state,state==BackupAssessment.State.UNKNOWN?null:LocalDate.of(2026,1,1),secret)).toList(),Instant.EPOCH);}
    ProjectInspection files(Status s){return new ProjectInspection(Instant.EPOCH,secret,s,0,0,0,null,List.of(new ProjectInspection.Document(secret,Status.FAILED)),List.of(secret));}
    GitObservation git(Status s){return new GitObservation(s,secret,Instant.EPOCH,Instant.EPOCH,secret,secret);}
    StackObservation stack(Status s){return new StackObservation(Instant.EPOCH,secret,s,List.of(new StackObservation.Evidence(secret,secret,secret)),List.of(secret),List.of(secret));}
    @Test void unobservedIsNotMissingOrFailedAndNeedsExplicitInspection(){
        var result=service.evaluate(notes(" ",""),backup(BackupAssessment.State.UNKNOWN),null,null,null);
        assertThat(result.advice()).containsExactly(
            new Advice(Topic.FILES,Reason.NOT_OBSERVED),new Advice(Topic.GIT,Reason.NOT_OBSERVED),new Advice(Topic.STACK,Reason.NOT_OBSERVED),
            new Advice(Topic.DEVELOPMENT,Reason.NOT_DOCUMENTED),new Advice(Topic.OPERATIONS,Reason.NOT_DOCUMENTED),
            new Advice(Topic.BACKUP_GIT_REMOTE,Reason.BACKUP_UNKNOWN),new Advice(Topic.BACKUP_UNTRACKED_FILES,Reason.BACKUP_UNKNOWN),
            new Advice(Topic.BACKUP_APPLICATION_DATA,Reason.BACKUP_UNKNOWN),new Advice(Topic.BACKUP_RESTORE_TEST,Reason.BACKUP_UNKNOWN));
        assertThat(result.gaps()).isEmpty();
    }
    @Test void successfulDeclarationsDoNotInventBackupVerificationOrScore(){
        var result=service.evaluate(notes(secret,secret),backup(BackupAssessment.State.DECLARED_OK),files(Status.SUPPORTED),git(Status.SUPPORTED),stack(Status.SUPPORTED));
        assertThat(result.advice()).isEmpty();assertThat(result.gaps()).isEmpty();
        var failed=service.evaluate(notes(secret,secret),backup(BackupAssessment.State.DECLARED_FAILED),files(Status.SUPPORTED),git(Status.SUPPORTED),stack(Status.SUPPORTED));
        assertThat(failed.advice()).hasSize(4).allMatch(a->a.reason()==Reason.BACKUP_FAILED);
    }
    @Test void detectorFailuresAreNotAbsenceAndMissingSourcesAreNotEnhancementRequests(){
        var absent=service.evaluate(notes(secret,secret),backup(BackupAssessment.State.DECLARED_OK),files(Status.MISSING),git(Status.MISSING),stack(Status.MISSING));
        assertThat(absent.advice()).hasSize(3).allMatch(a->a.reason()==Reason.MISSING);assertThat(absent.gaps()).isEmpty();
        for(var status:List.of(Status.PARTIAL,Status.FAILED,Status.UNSUPPORTED,Status.UNKNOWN)){
            var result=service.evaluate(notes(secret,secret),backup(BackupAssessment.State.DECLARED_OK),files(status),git(status),stack(status));
            assertThat(result.advice()).hasSize(3).allMatch(a->a.reason().name().equals(status.name()));
            assertThat(result.gaps()).containsExactly(new Gap(Detector.FILES,status),new Gap(Detector.GIT,status),new Gap(Detector.STACK,status));
        }
    }
    @Test void draftsExcludeEveryFreeTextFieldAndAcceptOnlyAllowlistedGapStates(){
        var result=service.evaluate(notes(secret,secret),backup(BackupAssessment.State.UNKNOWN),files(Status.PARTIAL),git(Status.FAILED),stack(Status.UNSUPPORTED));
        for(var gap:result.gaps()){
            var text=service.draft(gap);
            assertThat(text).contains("Detector: "+gap.detector(),"Reported status: "+gap.status(),"minimal synthetic fixture","Nothing is submitted automatically.")
                .doesNotContain(secret,id.toString(),"<script>","1970-01-01");
            assertThat(service.draft(new Gap(gap.detector(),gap.status()))).isEqualTo(text);
        }
        for(var detector:Detector.values()){
            assertThat(service.draft(new Gap(detector,Status.UNKNOWN))).contains("Reported status: UNKNOWN");
            for(var status:List.of(Status.SUPPORTED,Status.MISSING))assertThatThrownBy(()->service.draft(new Gap(detector,status))).isInstanceOf(IllegalArgumentException.class);
        }
    }
}
