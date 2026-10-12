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
        var service = new ProjectDetailsService(catalog, new ProjectInspector(Clock.systemUTC()));
        assertThat(service.inspect(id).files()).isZero();
        assertThat(service.git(id).status()).isEqualTo(ProjectInspection.Status.MISSING);
        Path moved = Files.createDirectory(temp.resolve("moved")); Files.writeString(moved.resolve("README.md"), "live");
        catalog.relocate(new CatalogProject(id, "example", moved, now, now));
        assertThat(service.document(id, "README.md").text()).isEqualTo("live");
        assertThatThrownBy(() -> service.project(UUID.randomUUID())).isInstanceOf(IllegalArgumentException.class);
        catalog.remove(id);
        assertThatThrownBy(() -> service.inspect(id)).isInstanceOf(IllegalArgumentException.class);
        assertThat(Files.readString(moved.resolve("README.md"))).isEqualTo("live");
    }
}
