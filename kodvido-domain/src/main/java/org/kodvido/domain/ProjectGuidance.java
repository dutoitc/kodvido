// SPDX-License-Identifier: GPL-3.0-only
package org.kodvido.domain;
import java.util.List;
/** Rule outcomes only: no paths, source text, or user-authored content. */
public record ProjectGuidance(List<Advice> advice,List<Gap> gaps) {
    public ProjectGuidance { advice=List.copyOf(advice);gaps=List.copyOf(gaps); }
    public enum Topic { FILES, GIT, STACK, DEVELOPMENT, OPERATIONS, BACKUP_GIT_REMOTE, BACKUP_UNTRACKED_FILES, BACKUP_APPLICATION_DATA, BACKUP_RESTORE_TEST }
    public enum Reason { NOT_OBSERVED, PARTIAL, MISSING, UNSUPPORTED, FAILED, UNKNOWN, NOT_DOCUMENTED, BACKUP_UNKNOWN, BACKUP_FAILED }
    public enum Detector { FILES, GIT, STACK }
    public record Advice(Topic topic,Reason reason) {}
    public record Gap(Detector detector,ProjectInspection.Status status) {}
}
