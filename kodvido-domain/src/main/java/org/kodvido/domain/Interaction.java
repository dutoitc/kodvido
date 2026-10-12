// SPDX-License-Identifier: GPL-3.0-only
package org.kodvido.domain;
import java.time.Instant;
import java.util.UUID;
/** A local Kodvido action, not evidence of source-code or developer activity. */
public record Interaction(long sequence, UUID projectId, Kind kind, Instant occurredAt) {
    public enum Kind { INSPECTION_REQUESTED, WORKFLOW_SAVED, RELOCATED }
}
