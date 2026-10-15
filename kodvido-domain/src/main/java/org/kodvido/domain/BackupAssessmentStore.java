// SPDX-License-Identifier: GPL-3.0-only
package org.kodvido.domain;
import java.util.*;
public interface BackupAssessmentStore {
    Optional<BackupAssessment> find(UUID projectId);
    /** Atomically accept only the next revision; lock the parent for first saves too. */
    void save(BackupAssessment assessment);
}
