package org.kodvido.detection;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.kodvido.domain.DetectionOutcome;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.*;
class ScannerSafetyTest {
    @TempDir Path temp;
    @BeforeEach void canonicalizeFixtureRoot() throws Exception {
        // Windows temporary roots may use an 8.3 alias while discovery returns real paths.
        temp = temp.toRealPath(LinkOption.NOFOLLOW_LINKS);
    }
    Path marker(String name, String content) throws Exception {
        Path p = temp.resolve(name); Files.createDirectories(p.getParent()); Files.writeString(p, content); return p.getParent();
    }
    @Test void groupsDeclaredNestedModulesButNotUnrelatedNestedProjectsAndDeduplicatesRoots() throws Exception {
        Path root = marker("root/pom.xml", "<project><modules><module>a</module><module>missing</module><module>../outside</module></modules></project>");
        Path child = marker("root/a/pom.xml", "<project><modules><module>b</module></modules></project>");
        Path grandchild = marker("root/a/b/pom.xml", "<project/>");
        Path other = marker("root/unrelated/pom.xml", "<project/>");
        marker("outside/pom.xml", "<project/>");
        var result = new ProjectDirectoryScanner().scan(List.of(root, child));
        assertThat(result.candidates()).hasSize(4);
        assertThat(result.candidates()).filteredOn(p -> p.root().equals(child)).allSatisfy(p -> assertThat(p.parentRoot()).isEqualTo(root));
        assertThat(result.candidates()).filteredOn(p -> p.root().equals(grandchild)).allSatisfy(p -> assertThat(p.parentRoot()).isEqualTo(child));
        assertThat(result.candidates()).filteredOn(p -> p.root().equals(other)).allSatisfy(p -> assertThat(p.parentRoot()).isNull());
        assertThat(result.candidates()).filteredOn(p -> p.root().equals(root)).allSatisfy(p -> assertThat(p.outcome()).isEqualTo(DetectionOutcome.PARTIAL));
    }
    @Test void pythonMarkersAndIgnoredDirectoriesAreReadOnly() throws Exception {
        marker("python/requirements.txt", "private-value"); marker("python/setup.py", "raise Exception('never execute')");
        marker("python/pyproject.toml", "[project]");
        for (String excluded : List.of("target", "build", ".git", ".venv", "node_modules", "dist", "vendor")) marker(excluded + "/pom.xml", "<project/>");
        Path file = temp.resolve("python/requirements.txt"); var time = Files.getLastModifiedTime(file);
        var result = new ProjectDirectoryScanner().scan(List.of(temp));
        assertThat(result.candidates()).hasSize(1);
        assertThat(result.candidates().getFirst().evidence()).containsExactlyInAnyOrder("requirements.txt", "setup.py", "pyproject.toml");
        assertThat(Files.readString(file)).isEqualTo("private-value"); assertThat(Files.getLastModifiedTime(file)).isEqualTo(time);
        assertThat(result.candidates().getFirst().observedAt()).isNotNull();
    }
    @Test void malformedAndExternalEntityPomsArePartialWithoutExternalReads() throws Exception {
        marker("malformed/pom.xml", "<project>");
        marker("wrong/pom.xml", "<notproject/>");
        marker("large/pom.xml", " ".repeat(1_048_577));
        marker("xxe/pom.xml", "<!DOCTYPE project [<!ENTITY e SYSTEM 'file:///not-readable'>]><project>&e;</project>");
        var result = new ProjectDirectoryScanner().scan(List.of(temp));
        assertThat(result.candidates()).hasSize(4).allSatisfy(p -> {
            assertThat(p.outcome()).isEqualTo(DetectionOutcome.PARTIAL); assertThat(p.diagnostics()).isNotEmpty();
        });
    }
    @Test void missingRootsAndRegularFileRootsDoNotPreventOtherRoots() throws Exception {
        Path ok = marker("valid/pom.xml", "<project/>");
        var result = new ProjectDirectoryScanner().scan(List.of(temp.resolve("absent"), ok.resolve("pom.xml"), ok));
        assertThat(result.candidates()).hasSize(1); assertThat(result.diagnostics()).hasSize(2);
        assertThat(result.diagnostics().toString()).contains("MISSING", "UNSUPPORTED");
        assertThat(result.diagnostics().toString()).doesNotContain(temp.toString());
    }
    @Test void boundedTraversalAndCancellationAreExplicit() throws Exception {
        marker("a/b/pom.xml", "<project/>");
        assertThat(new ProjectDirectoryScanner(1, 100, Duration.ofSeconds(5)).scan(List.of(temp)).diagnostics()).isNotEmpty();
        assertThat(new ProjectDirectoryScanner(32, 1, Duration.ofSeconds(5)).scan(List.of(temp)).diagnostics()).isNotEmpty();
        assertThat(new ProjectDirectoryScanner(32, 100, Duration.ZERO).scan(List.of(temp)).diagnostics()).isNotEmpty();
        Thread.currentThread().interrupt();
        try { assertThat(new ProjectDirectoryScanner().scan(List.of(temp)).diagnostics()).isNotEmpty(); }
        finally { Thread.interrupted(); }
    }
    @Test void rejectsSymlinkRootAncestorLoopAndMarker() throws Exception {
        Path actual = marker("actual/project/pom.xml", "<project/>");
        Path linked = temp.resolve("linked");
        try { Files.createSymbolicLink(linked, actual.getParent()); }
        catch (Exception ex) { assumeTrue(false, "Symlink creation unavailable on this platform"); }
        assertThatThrownBy(() -> SafePaths.directory(linked.resolve("project"))).isInstanceOf(java.io.IOException.class);
        assertThatThrownBy(() -> SafePaths.directory(linked.resolve(".."))).isInstanceOf(java.io.IOException.class);
        assertThat(new ProjectDirectoryScanner().scan(List.of(linked)).diagnostics()).isNotEmpty();
        Files.createSymbolicLink(actual.resolve("loop"), temp);
        Files.createDirectories(temp.resolve("fake")); Files.createSymbolicLink(temp.resolve("fake/pom.xml"), actual.resolve("pom.xml"));
        assertThat(new ProjectDirectoryScanner().scan(List.of(temp)).candidates()).hasSize(1);
    }
    @Test void moduleSeparatorsArePortableAndProfileModulesRemainManual() throws Exception {
        marker("root/pom.xml", "<project><modules><module>a\\b</module></modules><profiles><profile><modules><module>conditional</module></modules></profile></profiles></project>");
        marker("root/a/b/pom.xml", "<project/>"); marker("root/conditional/pom.xml", "<project/>");
        var result = new ProjectDirectoryScanner().scan(List.of(temp));
        assertThat(result.candidates()).filteredOn(p -> p.root().equals(temp.resolve("root/a/b")))
            .allSatisfy(p -> assertThat(p.parentRoot()).isEqualTo(temp.resolve("root")));
        assertThat(result.candidates()).filteredOn(p -> p.root().equals(temp.resolve("root/conditional")))
            .allSatisfy(p -> assertThat(p.parentRoot()).isNull());
    }
    @Test @org.junit.jupiter.api.condition.EnabledOnOs(org.junit.jupiter.api.condition.OS.WINDOWS)
    void windowsJunctionIsNotTraversed() throws Exception {
        Path actual = marker("actual/pom.xml", "<project/>");
        Path junction = temp.resolve("junction");
        var process = new ProcessBuilder("cmd", "/c", "mklink", "/J", junction.toString(), actual.toString()).redirectErrorStream(true).start();
        process.getInputStream().readAllBytes();
        assertThat(process.waitFor()).isZero();
        try {
            assertThatThrownBy(() -> SafePaths.directory(junction)).isInstanceOf(java.io.IOException.class);
            assertThat(new ProjectDirectoryScanner().scan(List.of(temp)).candidates()).hasSize(1);
        } finally { Files.deleteIfExists(junction); }
    }
    @Test void permissionErrorsAreReportedWhereEnforced() throws Exception {
        Path denied = marker("denied/pom.xml", "<project/>");
        assumeTrue(Files.getFileStore(denied).supportsFileAttributeView("posix"));
        var old = Files.getPosixFilePermissions(denied);
        try {
            Files.setPosixFilePermissions(denied, Set.of());
            assumeFalse(Files.isReadable(denied), "Running as privileged user; permission denial cannot be simulated");
            assertThat(new ProjectDirectoryScanner().scan(List.of(temp)).diagnostics()).isNotEmpty();
        } finally { Files.setPosixFilePermissions(denied, old); }
    }
}
