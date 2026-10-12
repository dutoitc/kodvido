package org.kodvido.engine;

import org.junit.jupiter.api.Test;
import org.kodvido.domain.*;
import java.nio.file.Path;
import java.time.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

class WorkflowServiceTest {
    final UUID id = UUID.randomUUID();
    final Instant now = Instant.parse("2026-01-01T00:00:00Z");
    final List<CatalogProject> projects = new ArrayList<>(List.of(new CatalogProject(id, "fixture", Path.of("fixture"), now, now)));
    final Map<UUID, WorkflowNotes> saved = new HashMap<>();
    final Catalog catalog = new Catalog() {
        public List<CatalogProject> list() { return List.copyOf(projects); }
        public void addAll(List<CatalogProject> p) { projects.addAll(p); }
        public void relocate(CatalogProject p) { throw new UnsupportedOperationException(); }
        public void remove(UUID id) { projects.clear(); }
    };
    final WorkflowStore store = new WorkflowStore() {
        public Optional<WorkflowNotes> find(UUID id) { return Optional.ofNullable(saved.get(id)); }
        public void save(WorkflowNotes n) { saved.put(n.projectId(), n); }
    };
    final WorkflowService service = new WorkflowService(catalog, store, Clock.fixed(now, ZoneOffset.UTC));
    WorkflowNotes.Phase phase(String text) { return new WorkflowNotes.Phase(LocalDate.of(2026,1,1), text, WorkflowNotes.State.DONE); }
    @Test void savesDeclaredNotesWithTimestampAndWithoutProjectFiles() {
        assertThat(service.load(id).revision()).isZero(); assertThat(service.load(id).updatedAt()).isNull();
        service.save(id, 0, List.of(phase(" milestone ")), " mvn verify ", " java -jar app.jar ");
        var notes = service.load(id);
        assertThat(notes.revision()).isEqualTo(1); assertThat(notes.updatedAt()).isEqualTo(now);
        assertThat(notes.development()).isEqualTo("mvn verify"); assertThat(notes.operations()).isEqualTo("java -jar app.jar");
        assertThat(notes.phases().getFirst().description()).isEqualTo("milestone");
        service.save(id, 1, List.of(), "", ""); assertThat(service.load(id).phases()).isEmpty();
        projects.clear(); assertThatThrownBy(() -> service.load(id)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.save(id, 0, List.of(), "", "")).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void invalidWorksheetNeverReachesStorage() {
        for (long revision : new long[]{-1, Long.MAX_VALUE}) assertThatThrownBy(() -> service.save(id, revision, List.of(), "", "")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.save(id, 0, null, "", "")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.save(id, 0, Collections.nCopies(6, phase("phase")), "", "")).isInstanceOf(IllegalArgumentException.class);
        var invalid = Arrays.asList(null, new WorkflowNotes.Phase(null,"phase",WorkflowNotes.State.DONE),
            new WorkflowNotes.Phase(LocalDate.now(),"phase",null), phase(null), phase(" "), phase("x".repeat(501)));
        for (var p : invalid) assertThatThrownBy(() -> service.save(id, 0, Collections.singletonList(p), "", "")).isInstanceOf(IllegalArgumentException.class);
        for (String text : Arrays.asList(null,"x".repeat(4001),"bad\0text")) {
            assertThatThrownBy(() -> service.save(id,0,List.of(),text,"")).isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> service.save(id,0,List.of(),"",text)).isInstanceOf(IllegalArgumentException.class);
        }
        assertThat(saved).isEmpty();
        service.save(id,0,Collections.nCopies(5, phase("x".repeat(500))),"x".repeat(4000),"y".repeat(4000));
        assertThat(service.load(id).phases()).hasSize(5);
    }
}
