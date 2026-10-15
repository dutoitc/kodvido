package org.kodvido.engine;

import org.kodvido.domain.*;
import org.kodvido.detection.*;
import java.io.IOException;
import java.nio.file.*;
import java.time.*;
import java.util.*;

/** Single local review at a time; a new scan invalidates old import forms. */
public final class CatalogService {
    public record Preview(UUID id, ScanReport report, Instant expiresAt) {}
    private final ProjectDirectoryScanner scanner;
    private final Catalog catalog;
    private final Clock clock;
    private Preview pending;
    private final ObservationCache cache;
    public CatalogService(ProjectDirectoryScanner scanner, Catalog catalog, Clock clock, ObservationCache cache) {
        this.scanner = scanner; this.catalog = catalog; this.clock = clock; this.cache=cache;
    }
    public synchronized Preview preview(List<String> roots) {
        if (roots.isEmpty() || roots.size() > 20) throw new IllegalArgumentException("Indiquez entre 1 et 20 racines.");
        List<Path> paths = new ArrayList<>();
        for (String root : roots) paths.add(parsePath(root));
        pending = new Preview(UUID.randomUUID(), scanner.scan(paths), clock.instant().plus(Duration.ofMinutes(30)));
        return pending;
    }
    public synchronized void confirm(UUID previewId, List<Integer> selected) throws IOException {
        if (pending == null || !pending.id().equals(previewId) || !clock.instant().isBefore(pending.expiresAt()))
            throw new IllegalArgumentException("Prévisualisation expirée ; relancez le scan.");
        if (selected.isEmpty()) throw new IllegalArgumentException("Sélectionnez au moins un projet.");
        List<CatalogProject> projects = new ArrayList<>();
        Set<Path> existing = new HashSet<>();
        for (var project : catalog.list()) existing.add(project.path());
        for (int index : new LinkedHashSet<>(selected)) {
            if (index < 0 || index >= pending.report().candidates().size()) throw new IllegalArgumentException("Sélection invalide.");
            var candidate = pending.report().candidates().get(index);
            Path path = SafePaths.directory(candidate.root());
            if (existing.contains(path)) continue;
            boolean markerPresent = candidate.evidence().stream().anyMatch(e -> Files.isRegularFile(path.resolve(e), LinkOption.NOFOLLOW_LINKS));
            if (!markerPresent) throw new IllegalArgumentException("Projet changé depuis le scan ; relancez-le.");
            Instant now = clock.instant();
            String name = Optional.ofNullable(path.getFileName()).map(Path::toString).orElse("Project");
            projects.add(new CatalogProject(UUID.randomUUID(), name, path, now, now));
        }
        catalog.addAll(projects); // Adapter guarantees atomicity, including duplicate-path conflicts.
        pending = null;
    }
    public List<CatalogProject> list() { return catalog.list(); }
    public void relocate(UUID id, String destination) throws IOException {
        CatalogProject current = catalog.list().stream().filter(p -> p.id().equals(id)).findFirst()
            .orElseThrow(() -> new IllegalArgumentException("Projet absent du catalogue."));
        Path path = SafePaths.directory(parsePath(destination));
        catalog.relocate(new CatalogProject(id, current.name(), path, current.createdAt(), clock.instant()));
        cache.forget(id);
    }
    public void remove(UUID id) { catalog.remove(id); cache.forget(id); }
    static Path parsePath(String value) {
        String text = value.trim();
        if (text.isEmpty()) throw new IllegalArgumentException("Chemin vide.");
        if (text.equals("~")) text = System.getProperty("user.home");
        else if (text.startsWith("~/") || text.startsWith("~\\")) text = System.getProperty("user.home") + text.substring(1);
        try {
            Path path = Path.of(text);
            if (!path.isAbsolute()) throw new IllegalArgumentException("Utilisez un chemin absolu adapté au système courant.");
            return path;
        } catch (InvalidPathException ex) { throw new IllegalArgumentException("Chemin invalide."); }
    }
}
