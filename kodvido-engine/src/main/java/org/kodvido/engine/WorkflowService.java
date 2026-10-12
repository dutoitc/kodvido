// SPDX-License-Identifier: GPL-3.0-only
package org.kodvido.engine;

import org.kodvido.domain.*;
import java.time.Clock;
import java.util.*;

public final class WorkflowService {
    private final Catalog catalog;
    private final WorkflowStore store;
    private final Clock clock;
    public WorkflowService(Catalog catalog, WorkflowStore store, Clock clock) {
        this.catalog = catalog; this.store = store; this.clock = clock;
    }
    private void requireProject(UUID id) {
        if (catalog.list().stream().noneMatch(p -> p.id().equals(id)))
            throw new IllegalArgumentException("Projet absent du catalogue.");
    }
    public WorkflowNotes load(UUID id) {
        requireProject(id);
        return store.find(id).orElseGet(() -> new WorkflowNotes(id, 0, List.of(), "", "", null));
    }
    public void save(UUID id, long revision, List<WorkflowNotes.Phase> phases, String development, String operations) {
        requireProject(id);
        if (revision < 0 || revision == Long.MAX_VALUE) throw new IllegalArgumentException("Révision invalide.");
        if (phases == null || phases.size() > 5) throw new IllegalArgumentException("Cinq phases au maximum.");
        for (var phase : phases) {
            if (phase == null || phase.date() == null || phase.state() == null)
                throw new IllegalArgumentException("Date et état obligatoires pour chaque phase.");
            if (text(phase.description(), 500).isBlank()) throw new IllegalArgumentException("Description de phase obligatoire.");
        }
        var cleaned = phases.stream().map(p -> new WorkflowNotes.Phase(p.date(), p.description().strip(), p.state())).toList();
        store.save(new WorkflowNotes(id, revision + 1, cleaned, text(development, 4000), text(operations, 4000), clock.instant()));
    }
    private String text(String value, int max) {
        if (value == null || value.length() > max || value.indexOf('\0') >= 0)
            throw new IllegalArgumentException("Texte absent, trop long ou invalide.");
        return value.strip();
    }
}
