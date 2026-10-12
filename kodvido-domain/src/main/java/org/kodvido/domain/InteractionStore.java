// SPDX-License-Identifier: GPL-3.0-only
package org.kodvido.domain;
import java.time.Instant;
import java.util.*;
public interface InteractionStore {
    /** Latest recorded first, at most 100 events per project; immutable result. */
    List<Interaction> recent(UUID projectId);
    void record(UUID projectId, Interaction.Kind kind, Instant occurredAt);
}
