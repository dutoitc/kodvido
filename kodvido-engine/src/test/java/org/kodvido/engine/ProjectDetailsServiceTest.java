package org.kodvido.engine;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.kodvido.detection.ProjectInspector;
import org.kodvido.domain.*;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

class ProjectDetailsServiceTest {
    @TempDir Path temp;
    @Test void inspectionUsesCurrentCatalogPathAndRejectsRemovedIdentity() throws Exception {
        var projects = new ArrayList<CatalogProject>();
        Catalog catalog = new Catalog() {
            public List<CatalogProject> list() { return List.copyOf(projects); }
            public void addAll(List<CatalogProject> values) { projects.addAll(values); }
            public void relocate(CatalogProject project) { projects.clear(); projects.add(project); }
            public void remove(UUID id) { projects.clear(); }
        };
        UUID id = UUID.randomUUID(); Instant now = Instant.now();
        catalog.addAll(List.of(new CatalogProject(id, "example", temp, now, now)));
        var events=new ArrayList<Interaction>();
        var store=new InteractionStore() {
            public List<Interaction> recent(UUID projectId) { return List.copyOf(events); }
            public void record(UUID projectId,Interaction.Kind kind,Instant time) { events.add(new Interaction(events.size()+1,projectId,kind,time)); }
        };
        var service = new ProjectDetailsService(catalog, new ProjectInspector(Clock.systemUTC()),store,Clock.fixed(now,ZoneOffset.UTC),new ObservationCache());
        assertThat(service.interactions(id)).isEmpty();
        assertThat(service.inspect(id).files()).isZero();
        assertThat(service.interactions(id)).containsExactly(new Interaction(1,id,Interaction.Kind.INSPECTION_REQUESTED,now));
        assertThat(service.stack(id).status()).isEqualTo(ProjectInspection.Status.UNKNOWN);
        assertThat(service.git(id).status()).isEqualTo(ProjectInspection.Status.MISSING);
        Path moved = Files.createDirectory(temp.resolve("moved")); Files.writeString(moved.resolve("README.md"), "live");
        catalog.relocate(new CatalogProject(id, "example", moved, now, now));
        assertThat(service.document(id, "README.md").text()).isEqualTo("live");
        Files.writeString(moved.resolve(".python-version"),"3.12.1");
        assertThat(service.stack(id).evidence().getFirst().declaration()).isEqualTo("3.12.1");
        assertThatThrownBy(() -> service.project(UUID.randomUUID())).isInstanceOf(IllegalArgumentException.class);
        assertThat(events).hasSize(1); // document/Git/stack reads do not create separate events.
        catalog.relocate(new CatalogProject(id,"example",temp.resolve("missing"),now,now));
        assertThat(service.inspect(id).status()).isEqualTo(ProjectInspection.Status.MISSING);
        assertThat(events).hasSize(2); // request recorded even when the directory is missing.
        catalog.remove(id);
        assertThatThrownBy(() -> service.inspect(id)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.stack(id)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.interactions(id)).isInstanceOf(IllegalArgumentException.class);
        assertThat(events).hasSize(2);
        assertThat(Files.readString(moved.resolve("README.md"))).isEqualTo("live");
    }
}
