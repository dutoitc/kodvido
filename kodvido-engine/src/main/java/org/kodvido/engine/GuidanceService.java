// SPDX-License-Identifier: GPL-3.0-only
package org.kodvido.engine;
import org.kodvido.domain.*;
import org.kodvido.domain.ProjectGuidance.*;
import org.kodvido.domain.ProjectInspection.Status;
import java.util.*;

/** Deterministic suggestions; never scans, executes, persists or sends anything. */
public final class GuidanceService {
    public ProjectGuidance evaluate(WorkflowNotes notes,BackupAssessment backup,
            ProjectInspection files,GitObservation git,StackObservation stack) {
        var advice=new ArrayList<Advice>();var gaps=new ArrayList<Gap>();
        detector(Detector.FILES,files==null?null:files.status(),advice,gaps);
        detector(Detector.GIT,git==null?null:git.status(),advice,gaps);
        detector(Detector.STACK,stack==null?null:stack.status(),advice,gaps);
        if(notes.development().isBlank()) advice.add(new Advice(Topic.DEVELOPMENT,Reason.NOT_DOCUMENTED));
        if(notes.operations().isBlank()) advice.add(new Advice(Topic.OPERATIONS,Reason.NOT_DOCUMENTED));
        for(var i:backup.items()) {
            var topic=Topic.valueOf("BACKUP_"+i.dimension().name());
            switch(i.state()) {
                case UNKNOWN -> advice.add(new Advice(topic,Reason.BACKUP_UNKNOWN));
                case DECLARED_FAILED -> advice.add(new Advice(topic,Reason.BACKUP_FAILED));
                case DECLARED_OK -> { /* No warning from this rule is not a verified success. */ }
            }
        }
        return new ProjectGuidance(advice,gaps);
    }
    private static void detector(Detector detector,Status status,List<Advice> advice,List<Gap> gaps) {
        var topic=Topic.valueOf(detector.name());
        if(status==null) { advice.add(new Advice(topic,Reason.NOT_OBSERVED));return; }
        if(status==Status.SUPPORTED) return;
        advice.add(new Advice(topic,Reason.valueOf(status.name())));
        if(isGap(status)) gaps.add(new Gap(detector,status));
    }
    private static boolean isGap(Status status) {
        return switch(status) { case PARTIAL,UNSUPPORTED,FAILED,UNKNOWN -> true; default -> false; };
    }
    /** Allowlisted enums only. Never accepts project identity, diagnostics or free text. */
    public String draft(Gap gap) {
        Objects.requireNonNull(gap.detector());Objects.requireNonNull(gap.status());
        if(!isGap(gap.status())) throw new IllegalArgumentException("Cet état ne décrit pas une limite de détection.");
        return """
            # Kodvido detector gap

            Detector: %s
            Reported status: %s
            Template: M2.8

            ## Request
            Investigate this detector limitation or clarify its diagnostics.
            This status alone does not establish a bug or the absence of a technology.

            ## Expected behavior
            Bounded read-only detection with explicit evidence and limitations.
            Never execute project scripts or contact remotes automatically.

            ## Reproduction to prepare manually
            - Build a minimal synthetic fixture that contains no real project data.
            - Describe expected and actual behavior using that fixture only.
            - Add the Kodvido release and operating system manually if relevant.

            ## Privacy review before sharing
            This generated draft includes only an allowlisted detector and status.
            No project name, ID, path, URL, timestamp, document, note, version
            declaration, script name or raw diagnostic has been copied.
            Review any additions for secrets and personal/professional data.
            Nothing is submitted automatically.
            """.formatted(gap.detector().name(),gap.status().name());
    }
}
