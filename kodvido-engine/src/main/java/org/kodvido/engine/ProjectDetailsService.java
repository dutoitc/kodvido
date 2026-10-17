package org.kodvido.engine;

import org.kodvido.detection.ProjectInspector;
import org.kodvido.domain.*;
import java.util.UUID;
import java.util.List;
import java.time.Clock;

/** Coordinates catalogue identity and read-only inspection; no web or Spring dependency. */
public final class ProjectDetailsService {
    private final Catalog catalog;
    private final ProjectInspector inspector;
    private final InteractionStore interactions;
    private final Clock clock;
    private final ObservationCache cache;
    public ProjectDetailsService(Catalog catalog, ProjectInspector inspector, InteractionStore interactions, Clock clock, ObservationCache cache) {
        this.catalog = catalog; this.inspector = inspector; this.interactions=interactions; this.clock=clock;this.cache=cache;
    }
    public record Observation(ProjectInspection files,GitObservation git,StackObservation stack) {}
    public Observation observe(UUID id) {
        var p=project(id);
        interactions.record(id,Interaction.Kind.INSPECTION_REQUESTED,clock.instant());
        var files=inspector.inspect(p.path());
        var git=new org.kodvido.detection.GitInspector(clock).inspect(p.path());
        var stack=new org.kodvido.detection.StackInspector(clock).inspect(p.path());
        cache.remember(p,files,git,stack);
        return new Observation(files,git,stack);
    }
    public CatalogProject project(UUID id) {
        return catalog.list().stream().filter(p -> p.id().equals(id)).findFirst()
            .orElseThrow(() -> new IllegalArgumentException("Projet absent du catalogue."));
    }
    public StackObservation stack(UUID id) {
        return new org.kodvido.detection.StackInspector(java.time.Clock.systemUTC()).inspect(project(id).path());
    }
    public GitObservation git(UUID id) {
        return new org.kodvido.detection.GitInspector(java.time.Clock.systemUTC()).inspect(project(id).path());
    }
    public List<Interaction> interactions(UUID id) { project(id); return interactions.recent(id); }
    public ProjectInspection inspect(UUID id) {
        var project=project(id);
        interactions.record(id,Interaction.Kind.INSPECTION_REQUESTED,clock.instant());
        return inspector.inspect(project.path());
    }
    public ProjectInspection.DocumentText document(UUID id, String name) {
        return inspector.readDocument(project(id).path(), name);
    }
}
