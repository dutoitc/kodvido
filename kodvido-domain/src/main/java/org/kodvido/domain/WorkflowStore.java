// SPDX-License-Identifier: GPL-3.0-only
package org.kodvido.domain;
import java.util.*;
public interface WorkflowStore {
    Optional<WorkflowNotes> find(UUID projectId);
    /** Atomic compare-and-save; stored revision must equal notes.revision() - 1. */
    void save(WorkflowNotes notes);
}
