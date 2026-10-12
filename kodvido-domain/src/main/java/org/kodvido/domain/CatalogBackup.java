// SPDX-License-Identifier: GPL-3.0-only
package org.kodvido.domain;
import java.time.Instant;
import java.util.*;
/** Local metadata transfer, deliberately not a sanitized shareable report. */
public record CatalogBackup(int version, Instant exportedAt, List<Entry> projects) {
    public CatalogBackup { projects = List.copyOf(projects); }
    public record Entry(UUID id, String name, String path, Instant createdAt, Instant updatedAt, WorkflowNotes notes) {}
}
