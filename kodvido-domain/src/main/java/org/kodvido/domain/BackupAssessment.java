// SPDX-License-Identifier: GPL-3.0-only
package org.kodvido.domain;
import java.time.*;
import java.util.*;
/** Dated human declarations, never independently verified backup guarantees. */
public record BackupAssessment(UUID projectId, long revision, List<Item> items, Instant updatedAt) {
    public BackupAssessment { items = List.copyOf(items); }
    public enum Dimension { GIT_REMOTE, UNTRACKED_FILES, APPLICATION_DATA, RESTORE_TEST }
    public enum State { UNKNOWN, DECLARED_OK, DECLARED_FAILED }
    public record Item(Dimension dimension, State state, LocalDate checkedOn, String evidence) {}
}
