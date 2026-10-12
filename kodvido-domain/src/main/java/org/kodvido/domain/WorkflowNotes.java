// SPDX-License-Identifier: GPL-3.0-only
package org.kodvido.domain;

import java.time.*;
import java.util.*;

/** User-declared local notes; not measured facts or project-file copies. */
public record WorkflowNotes(UUID projectId, long revision, List<Phase> phases,
        String development, String operations, Instant updatedAt) {
    public WorkflowNotes { phases = List.copyOf(phases); }
    public record Phase(LocalDate date, String description, State state) {}
    public enum State { PLANNED, IN_PROGRESS, DONE }
}
