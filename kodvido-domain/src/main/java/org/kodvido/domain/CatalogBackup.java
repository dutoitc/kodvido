// SPDX-License-Identifier: GPL-3.0-only
package org.kodvido.domain;
import java.time.Instant;
import java.util.*;
/** Local metadata transfer, deliberately not a sanitized shareable report. */
public record CatalogBackup(Instant exportedAt, List<Entry> projects) {
    public CatalogBackup { projects = List.copyOf(projects); }
    public record Entry(UUID id, String name, String path, Instant createdAt, Instant updatedAt, WorkflowNotes notes, BackupAssessment assessment, Inventory.Notes inventoryNotes) {
        public Entry(UUID id,String name,String path,Instant createdAt,Instant updatedAt,WorkflowNotes notes,BackupAssessment assessment) {
            this(id,name,path,createdAt,updatedAt,notes,assessment,null);
        }
        public Entry(UUID id,String name,String path,Instant createdAt,Instant updatedAt,WorkflowNotes notes) {
            this(id,name,path,createdAt,updatedAt,notes,null);
        }
    }
}
