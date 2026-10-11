package org.kodvido.engine;

import org.kodvido.detection.ProjectInspector;
import org.kodvido.domain.*;
import java.util.UUID;

/** Coordinates catalogue identity and read-only inspection; no web or Spring dependency. */
public final class ProjectDetailsService {
    private final Catalog catalog;
    private final ProjectInspector inspector;
    public ProjectDetailsService(Catalog catalog, ProjectInspector inspector) {
        this.catalog = catalog; this.inspector = inspector;
    }
    public CatalogProject project(UUID id) {
        return catalog.list().stream().filter(p -> p.id().equals(id)).findFirst()
            .orElseThrow(() -> new IllegalArgumentException("Projet absent du catalogue."));
    }
    public ProjectInspection inspect(UUID id) { return inspector.inspect(project(id).path()); }
    public ProjectInspection.DocumentText document(UUID id, String name) {
        return inspector.readDocument(project(id).path(), name);
    }
}
