package org.kodvido.engine;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.kodvido.domain.*;
import org.kodvido.detection.ProjectDirectoryScanner;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
class CatalogServiceTest {
    @TempDir Path temp;
    MemoryCatalog catalog = new MemoryCatalog();
    MutableClock clock = new MutableClock();
    CatalogService service = new CatalogService(new ProjectDirectoryScanner(), catalog, clock,new ObservationCache());
    static class MemoryCatalog implements Catalog {
        Map<UUID,CatalogProject> data = new LinkedHashMap<>();
        public List<CatalogProject> list() { return List.copyOf(data.values()); }
        public void addAll(List<CatalogProject> items) { items.forEach(p -> data.put(p.id(), p)); }
        public void relocate(CatalogProject p) { data.put(p.id(), p); }
        public void remove(UUID id) { data.remove(id); }
    }
    static class MutableClock extends Clock {
        Instant now = Instant.parse("2026-01-01T00:00:00Z");
        public ZoneId getZone() { return ZoneOffset.UTC; }
        public Clock withZone(ZoneId zone) { return this; }
        public Instant instant() { return now; }
    }
    CatalogService.Preview preview() throws Exception {
        Files.writeString(temp.resolve("pom.xml"), "<project/>"); return service.preview(List.of(temp.toString()));
    }
    @Test void previewRequiresConfirmationAndRemovalNeverChangesSource() throws Exception {
        var p = preview(); assertThat(service.list()).isEmpty();
        service.confirm(p.id(), List.of(0,0)); var stored = service.list().getFirst();
        assertThat(stored.createdAt()).isEqualTo(clock.now);
        service.remove(stored.id()); assertThat(service.list()).isEmpty();
        assertThat(Files.readString(temp.resolve("pom.xml"))).isEqualTo("<project/>");
        assertThatThrownBy(() -> service.confirm(p.id(), List.of(0))).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void relocationPreservesIdNameAndCreationTime() throws Exception {
        var p = preview(); service.confirm(p.id(), List.of(0)); var original = service.list().getFirst();
        Path destination = temp.resolve("new"); Files.createDirectory(destination);
        clock.now = clock.now.plusSeconds(20); service.relocate(original.id(), destination.toString());
        var moved = service.list().getFirst(); assertThat(moved.id()).isEqualTo(original.id());
        assertThat(moved.name()).isEqualTo(original.name()); assertThat(moved.createdAt()).isEqualTo(original.createdAt());
        assertThat(moved.updatedAt()).isEqualTo(clock.now); assertThat(moved.path()).isEqualTo(destination.toRealPath(LinkOption.NOFOLLOW_LINKS));
        assertThatThrownBy(() -> service.relocate(UUID.randomUUID(), destination.toString())).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.relocate(original.id(), temp.resolve("missing").toString())).isInstanceOf(java.io.IOException.class);
    }
    @Test void cannotImportUnknownStaleExpiredOrInvalidSelections() throws Exception {
        assertThatThrownBy(() -> service.confirm(UUID.randomUUID(), List.of(0))).isInstanceOf(IllegalArgumentException.class);
        var old = preview(); var p = preview();
        assertThatThrownBy(() -> service.confirm(old.id(), List.of(0))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.confirm(p.id(), List.of())).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.confirm(p.id(), List.of(-1))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.confirm(p.id(), List.of(1))).isInstanceOf(IllegalArgumentException.class);
        clock.now = p.expiresAt();
        assertThatThrownBy(() -> service.confirm(p.id(), List.of(0))).isInstanceOf(IllegalArgumentException.class);
        assertThat(service.list()).isEmpty();
    }
    @Test void checksDisappearedMarkerAndDoesNotDuplicateExistingProjects() throws Exception {
        var p = preview(); Files.delete(temp.resolve("pom.xml"));
        assertThatThrownBy(() -> service.confirm(p.id(), List.of(0))).isInstanceOf(IllegalArgumentException.class);
        var next = preview(); service.confirm(next.id(), List.of(0));
        var again = preview(); service.confirm(again.id(), List.of(0)); assertThat(service.list()).hasSize(1);
    }
    @Test void pathParsingRejectsInvalidOrRelativeRootsAndExpandsHome() {
        assertThatThrownBy(() -> service.preview(List.of())).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.preview(Collections.nCopies(21, temp.toString()))).isInstanceOf(IllegalArgumentException.class);
        for (String bad : List.of("", "relative", "bad\u0000path"))
            assertThatThrownBy(() -> CatalogService.parsePath(bad)).isInstanceOf(IllegalArgumentException.class);
        assertThat(CatalogService.parsePath("~")).isEqualTo(Path.of(System.getProperty("user.home")));
        assertThat(CatalogService.parsePath("~/projects")).isEqualTo(Path.of(System.getProperty("user.home"), "projects"));
        assertThat(CatalogService.parsePath("~\\projects").toString()).contains(System.getProperty("user.home"));
        if (System.getProperty("os.name").startsWith("Windows")) {
            assertThat(CatalogService.parsePath("C:\\projects")).isEqualTo(Path.of("C:\\projects"));
        } else {
            assertThatThrownBy(() -> CatalogService.parsePath("C:\\projects")).isInstanceOf(IllegalArgumentException.class);
        }
        assertThat(CatalogService.parsePath(" " + temp + " ")).isEqualTo(temp);
    }
    @Test void separateMavenModuleCanBeExplicitlyImported() throws Exception {
        Files.writeString(temp.resolve("pom.xml"), "<project><modules><module>child</module></modules></project>");
        Files.createDirectory(temp.resolve("child")); Files.writeString(temp.resolve("child/pom.xml"), "<project/>");
        var p = service.preview(List.of(temp.toString())); service.confirm(p.id(), List.of(1));
        Path expected = temp.resolve("child").toRealPath(LinkOption.NOFOLLOW_LINKS);
        assertThat(service.list()).hasSize(1).allSatisfy(x -> assertThat(x.path()).isEqualTo(expected));
    }
    @Test void equivalentRootsUseOneCanonicalCatalogueLocation() throws Exception {
        Files.writeString(temp.resolve("pom.xml"), "<project/>");
        var p = service.preview(List.of(temp.toString(), temp.resolve(".").toString()));
        assertThat(p.report().candidates()).hasSize(1);
        service.confirm(p.id(), List.of(0));
        assertThat(service.list().getFirst().path()).isEqualTo(temp.toRealPath(LinkOption.NOFOLLOW_LINKS));
        var again = service.preview(List.of(temp.resolve(".").toString()));
        service.confirm(again.id(), List.of(0));
        assertThat(service.list()).hasSize(1);
    }
    @Test void legacyDiscoveryDelegatesReadOnly() throws Exception {
        preview(); assertThat(new DiscoveryService(new ProjectDirectoryScanner()).preview(temp)).hasSize(1);
    }
}
