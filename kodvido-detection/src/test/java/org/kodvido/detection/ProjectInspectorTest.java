package org.kodvido.detection;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.kodvido.domain.ProjectInspection.Status;
import java.nio.file.*;
import java.nio.file.attribute.FileTime;
import java.time.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

class ProjectInspectorTest {
    @TempDir Path temp;
    final Instant now = Instant.parse("2026-06-01T12:00:00Z");
    final Clock clock = Clock.fixed(now, ZoneOffset.UTC);
    final ProjectInspector inspector = new ProjectInspector(clock);
    Path file(String name, Instant modified) throws Exception {
        Path path = temp.resolve(name); Files.createDirectories(path.getParent());
        Files.writeString(path, "content"); Files.setLastModifiedTime(path, FileTime.from(modified)); return path;
    }
    @Test void inclusiveWindowsExcludeGeneratedDirectoriesAndDoNotChangeSource() throws Exception {
        file("current", now); file("week", now.minus(Duration.ofDays(7)));
        file("month", now.minus(Duration.ofDays(30))); file("old", now.minus(Duration.ofDays(30)).minusSeconds(1));
        file("TARGET/generated", now); file(".git/index", now); file(".venv/lib/a", now);
        var result = inspector.inspect(temp);
        assertThat(result.status()).isEqualTo(Status.SUPPORTED);
        assertThat(result.files()).isEqualTo(4); assertThat(result.modified7Days()).isEqualTo(2);
        assertThat(result.modified30Days()).isEqualTo(3); assertThat(result.latestModification()).isEqualTo(now);
        assertThat(result.observedAt()).isEqualTo(now);
        assertThat(result.documents()).allMatch(d -> d.status() == Status.MISSING);
        assertThat(Files.readString(temp.resolve("week"))).isEqualTo("content");
        assertThat(Files.getLastModifiedTime(temp.resolve("week")).toInstant()).isEqualTo(now.minus(Duration.ofDays(7)));
    }
    @Test void missingWrongTypeEmptyAndFutureAreDistinct() throws Exception {
        assertThat(inspector.inspect(temp.resolve("absent")).status()).isEqualTo(Status.MISSING);
        assertThat(inspector.inspect(file("file", now)).status()).isEqualTo(Status.UNSUPPORTED);
        var empty = inspector.inspect(Files.createDirectory(temp.resolve("empty")));
        assertThat(empty.files()).isZero(); assertThat(empty.latestModification()).isNull();
        file("future", now.plusSeconds(60));
        var result = inspector.inspect(temp);
        assertThat(result.status()).isEqualTo(Status.PARTIAL); assertThat(result.diagnostics()).contains("FUTURE_TIMESTAMP");
        assertThat(result.modified7Days()).isEqualTo(1); assertThat(result.latestModification()).isEqualTo(now);
    }
    @Test void traversalLimitsAndCancellationProducePartialEvidence() throws Exception {
        file("nested/deep/file", now);
        assertThat(new ProjectInspector(clock, 1, 100, Duration.ofSeconds(1)).inspect(temp).diagnostics()).contains("DEPTH_REACHED");
        assertThat(new ProjectInspector(clock, 32, 1, Duration.ofSeconds(1)).inspect(temp).diagnostics()).contains("LIMIT_REACHED");
        assertThat(new ProjectInspector(clock, 32, 100, Duration.ZERO).inspect(temp).status()).isEqualTo(Status.PARTIAL);
        Thread.currentThread().interrupt();
        try { assertThat(inspector.inspect(temp).diagnostics()).contains("LIMIT_REACHED"); }
        finally { Thread.interrupted(); }
    }
    @Test void documentsAreLiveBoundedUtf8AndStrictlyAllowlisted() throws Exception {
        Path doc = file("PROJECT.md", now);
        assertThat(inspector.readDocument(temp, "PROJECT.md").text()).isEqualTo("content");
        Files.writeString(doc, "new <script>alert(1)</script>");
        assertThat(inspector.readDocument(temp, "PROJECT.md").text()).startsWith("new <script>");
        assertThat(inspector.readDocument(temp, "TODO.md").status()).isEqualTo(Status.MISSING);
        assertThat(inspector.readDocument(temp.resolve("absent"), "TODO.md").status()).isEqualTo(Status.MISSING);
        assertThatThrownBy(() -> inspector.readDocument(temp, "../secret")).isInstanceOf(IllegalArgumentException.class);
        Files.write(doc, new byte[262_145]);
        assertThat(inspector.readDocument(temp, "PROJECT.md").status()).isEqualTo(Status.UNSUPPORTED);
        Files.write(doc, new byte[]{(byte)0xff});
        assertThat(inspector.readDocument(temp, "PROJECT.md").status()).isEqualTo(Status.FAILED);
        Files.createDirectory(temp.resolve("README.md"));
        assertThat(inspector.readDocument(temp, "README.md").status()).isEqualTo(Status.UNSUPPORTED);
        assertThat(inspector.readDocument(doc, "PROJECT.md").status()).isEqualTo(Status.FAILED);
    }
    @Test void linkedRootsDocumentsAndChildrenAreNotFollowed() throws Exception {
        Path source = Files.createDirectory(temp.resolve("source"));
        Path outside = Files.createDirectory(temp.resolve("outside"));
        Files.writeString(outside.resolve("secret"), "private");
        try {
            Files.createSymbolicLink(source.resolve("linked"), outside);
            Files.createSymbolicLink(source.resolve("PROJECT.md"), outside.resolve("secret"));
        } catch (UnsupportedOperationException | java.io.IOException ex) {
            Assumptions.abort("Symbolic links unavailable on this runner");
        }
        var result = inspector.inspect(source);
        assertThat(result.files()).isZero(); assertThat(result.status()).isEqualTo(Status.PARTIAL);
        assertThat(inspector.inspect(source.resolve("linked")).status()).isEqualTo(Status.FAILED);
        assertThat(inspector.readDocument(source, "PROJECT.md").status()).isEqualTo(Status.UNSUPPORTED);
    }
    @Test void deniedAccessIsNotReportedAsMissing() throws Exception {
        Assumptions.assumeTrue(Files.getFileStore(temp).supportsFileAttributeView("posix"));
        Path denied = Files.createDirectory(temp.resolve("denied"));
        Files.writeString(denied.resolve("PROJECT.md"), "private");
        var permissions = Files.getPosixFilePermissions(denied);
        try {
            Files.setPosixFilePermissions(denied, Set.of());
            Assumptions.assumeFalse(Files.isReadable(denied), "Privileged user can still read denied directory");
            assertThat(inspector.inspect(temp).status()).isEqualTo(Status.PARTIAL);
            assertThat(inspector.readDocument(denied, "PROJECT.md").status()).isEqualTo(Status.FAILED);
        } finally { Files.setPosixFilePermissions(denied, permissions); }
    }
}
