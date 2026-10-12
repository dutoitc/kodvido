package org.kodvido.detection;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.kodvido.domain.ProjectInspection.Status;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

class GitInspectorTest {
    @TempDir Path temp;
    final Clock clock = Clock.fixed(Instant.parse("2026-10-11T20:00:00Z"), ZoneOffset.UTC);
    final GitInspector inspector = new GitInspector(clock);
    final String hash = "a".repeat(40);
    void git(String... args) throws Exception {
        var cmd = new ArrayList<>(List.of("git", "-C", temp.toString())); cmd.addAll(List.of(args));
        var pb = new ProcessBuilder(cmd).redirectErrorStream(true);
        pb.environment().put("GIT_AUTHOR_DATE", "2026-01-01T12:00:00Z");
        pb.environment().put("GIT_COMMITTER_DATE", "2026-01-02T12:00:00Z");
        var process = pb.start(); var output = new String(process.getInputStream().readAllBytes());
        assertThat(process.waitFor()).as(output).isZero();
    }
    void init() throws Exception { git("init"); git("config", "user.name", "Fixture"); git("config", "user.email", "fixture@example.invalid"); }
    void commit() throws Exception { Files.writeString(temp.resolve("file"), "unchanged"); git("add", "file"); git("commit", "-m", "fixture"); }
    @Test void readsLoosePackedAndDetachedHeadWithoutChangingRepository() throws Exception {
        init(); commit();
        var first = inspector.inspect(temp);
        assertThat(first.status()).isEqualTo(Status.SUPPORTED);
        assertThat(first.committedAt()).isEqualTo(Instant.parse("2026-01-02T12:00:00Z"));
        git("gc");
        var packed = inspector.inspect(temp); assertThat(packed.commitId()).isEqualTo(first.commitId());
        git("checkout", "--detach");
        Map<String, byte[]> before = bytes();
        var detached = inspector.inspect(temp);
        assertThat(detached.status()).isEqualTo(Status.SUPPORTED); assertThat(detached.commitId()).isEqualTo(first.commitId());
        var after = bytes(); assertThat(after.keySet()).isEqualTo(before.keySet());
        before.forEach((key, value) -> assertThat(after.get(key)).as(key).isEqualTo(value));
    }
    Map<String, byte[]> bytes() throws Exception {
        var result = new TreeMap<String, byte[]>();
        try (var paths = Files.walk(temp)) { for (Path p : paths.filter(Files::isRegularFile).toList()) result.put(temp.relativize(p).toString(), Files.readAllBytes(p)); }
        return result;
    }
    @Test void missingEmptyBrokenAndExternalRepositoriesAreDistinct() throws Exception {
        assertThat(inspector.inspect(temp).status()).isEqualTo(Status.MISSING);
        assertThat(inspector.inspect(temp.resolve("missing")).diagnostic()).isEqualTo("PROJECT_DIRECTORY_MISSING");
        Files.writeString(temp.resolve(".git"), "gitdir: ../outside");
        assertThat(inspector.inspect(temp).status()).isEqualTo(Status.UNSUPPORTED);
        Files.delete(temp.resolve(".git")); init();
        assertThat(inspector.inspect(temp).status()).isEqualTo(Status.FAILED);
        commit(); Files.writeString(temp.resolve(".git/HEAD"), "broken");
        assertThat(inspector.inspect(temp).status()).isEqualTo(Status.FAILED);
        Path child = Files.createDirectory(temp.resolve("module"));
        assertThat(inspector.inspect(child).status()).isEqualTo(Status.MISSING);
    }
    @Test void rejectsIndirectConfigurationObjectStoresAndShallowClonesBeforeExecuting() throws Exception {
        init();
        Path config = temp.resolve(".git/config"); String original = Files.readString(config);
        for (String unsafe : List.of("[include]\npath = outside", "[includeIf \"gitdir:foo\"]\npath = outside", "[extensions]\nworktreeConfig = true", "[remote \"origin\"]\npromisor = true")) {
            Files.writeString(config, original + "\n" + unsafe);
            assertThat(inspector.inspect(temp).status()).isEqualTo(Status.UNSUPPORTED);
        }
        Files.writeString(config, "x".repeat(65_537)); assertThat(inspector.inspect(temp).status()).isEqualTo(Status.UNSUPPORTED);
        Files.writeString(config, original);
        for (String name : List.of("objects/info/alternates", "objects/info/http-alternates", "commondir", "shallow", "objects/pack/example.promisor")) {
            Path p = temp.resolve(".git").resolve(name); Files.createDirectories(p.getParent()); Files.writeString(p, "outside");
            assertThat(inspector.inspect(temp).status()).isEqualTo(Status.UNSUPPORTED); Files.delete(p);
        }
    }
    @Test void linkedMetadataIsNotFollowed() throws Exception {
        init(); Path target = Files.writeString(temp.resolve("private"), "private");
        try { Files.createSymbolicLink(temp.resolve(".git/objects/link"), target); }
        catch (java.io.IOException | UnsupportedOperationException ex) { Assumptions.abort("Symlinks unavailable"); }
        assertThat(inspector.inspect(temp).status()).isEqualTo(Status.UNSUPPORTED);
    }
    @Test void readerFailuresAreSanitizedAndCancellationPreservesInterrupt() throws Exception {
        init();
        assertThat(new GitInspector(clock, (g,r) -> { throw new java.io.IOException("secret path"); }).inspect(temp).diagnostic()).isEqualTo("GIT_UNAVAILABLE_OR_UNREADABLE");
        assertThat(new GitInspector(clock, (g,r) -> new GitInspector.Result(0,"bad")).inspect(temp).diagnostic()).isEqualTo("INVALID_GIT_RESPONSE");
        assertThat(new GitInspector(clock, (g,r) -> new GitInspector.Result(0,hash+"\ninvalid")).inspect(temp).status()).isEqualTo(Status.FAILED);
        assertThat(new GitInspector(clock, (g,r) -> new GitInspector.Result(0,hash+"\n"+Long.MAX_VALUE)).inspect(temp).status()).isEqualTo(Status.FAILED);
        try {
            assertThat(new GitInspector(clock, (g,r) -> { throw new InterruptedException(); }).inspect(temp).status()).isEqualTo(Status.PARTIAL);
            assertThat(Thread.currentThread().isInterrupted()).isTrue();
        } finally { Thread.interrupted(); }
    }
    @Test void futureDatesAndTraversalDepthAreReported() throws Exception {
        init();
        var result = new GitInspector(clock, (g,r) -> new GitInspector.Result(0,hash+"\n"+clock.instant().plusSeconds(1).getEpochSecond())).inspect(temp);
        assertThat(result.status()).isEqualTo(Status.PARTIAL); assertThat(result.diagnostic()).isEqualTo("FUTURE_COMMIT_TIMESTAMP");
        Files.createDirectories(temp.resolve(".git").resolve("deep/".repeat(33)));
        assertThat(inspector.inspect(temp).status()).isEqualTo(Status.UNSUPPORTED);
    }
}
