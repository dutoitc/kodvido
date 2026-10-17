// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 Kodvido contributors
package org.kodvido.detection;

import org.kodvido.domain.GitObservation;
import org.kodvido.domain.ProjectInspection.Status;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.*;
import java.util.*;
import java.util.concurrent.TimeUnit;

/** Fixed read-only Git command, never a discovered executable or shell command. */
public final class GitInspector {
    private final Clock clock;
    private final Reader reader;
    @FunctionalInterface interface Reader { Result read(Path gitDir, Path root) throws IOException, InterruptedException; }
    record Result(int exit, String output) {}
    public GitInspector(Clock clock) { this(clock, GitInspector::readCommit); }
    GitInspector(Clock clock, Reader reader) { this.clock = clock; this.reader = reader; }

    public GitObservation inspect(Path input) {
        Instant observed = clock.instant();
        try {
            Path root = SafePaths.directory(input);
            Path metadata = root.resolve(".git");
            BasicFileAttributes attrs;
            try { attrs = Files.readAttributes(metadata, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS); }
            catch (NoSuchFileException ex) { return observation(Status.MISSING, observed, "NO_LOCAL_GIT_DIRECTORY"); }
            if (!attrs.isDirectory() || attrs.isSymbolicLink() || attrs.isOther())
                return observation(Status.UNSUPPORTED, observed, "EXTERNAL_OR_LINKED_GIT_DIRECTORY");
            if (!safeMetadata(metadata)) return observation(Status.UNSUPPORTED, observed, "UNSAFE_OR_COMPLEX_GIT_METADATA");
            Result result = reader.read(metadata, root);
            if (result.exit() != 0) return observation(Status.FAILED, observed, "NO_READABLE_HEAD_COMMIT");
            String[] lines = result.output().strip().split("\\R");
            if (lines.length != 2 || !lines[0].matches("[0-9a-f]{40}|[0-9a-f]{64}"))
                return observation(Status.FAILED, observed, "INVALID_GIT_RESPONSE");
            Instant date = Instant.ofEpochSecond(Long.parseLong(lines[1]));
            return new GitObservation(date.isAfter(observed) ? Status.PARTIAL : Status.SUPPORTED,
                lines[0], date, observed, "git-head-1", date.isAfter(observed) ? "FUTURE_COMMIT_TIMESTAMP" : "LOCAL_HEAD_ONLY");
        } catch (NoSuchFileException ex) { return observation(Status.MISSING, observed, "PROJECT_DIRECTORY_MISSING"); }
        catch (InterruptedException ex) { Thread.currentThread().interrupt(); return observation(Status.PARTIAL, observed, "CANCELLED"); }
        catch (IOException | SecurityException ex) { return observation(Status.FAILED, observed, "GIT_UNAVAILABLE_OR_UNREADABLE"); }
        catch (IllegalArgumentException | DateTimeException ex) { return observation(Status.FAILED, observed, "INVALID_GIT_RESPONSE"); }
    }

    private GitObservation observation(Status status, Instant time, String diagnostic) {
        return new GitObservation(status, null, null, time, "git-head-1", diagnostic);
    }

    /** Preflight all metadata before Git can follow object/ref paths. Stable-tree assumption applies. */
    boolean safeMetadata(Path metadata) throws IOException {
        boolean[] safe = {true}; int[] entries = {0}; long started = System.nanoTime();
        Files.walkFileTree(metadata, EnumSet.noneOf(FileVisitOption.class), 32, new SimpleFileVisitor<Path>() {
            private FileVisitResult check(Path path, BasicFileAttributes attrs) throws IOException {
                if (++entries[0] > 100_000 || System.nanoTime() - started > Duration.ofSeconds(5).toNanos()
                        || Thread.currentThread().isInterrupted() || attrs.isSymbolicLink() || attrs.isOther()) {
                    safe[0] = false; return FileVisitResult.TERMINATE;
                }
                if (attrs.isDirectory()) SafePaths.directory(path);
                return FileVisitResult.CONTINUE;
            }
            @Override public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException { return check(dir, attrs); }
            @Override public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                if (check(file, attrs) == FileVisitResult.TERMINATE) return FileVisitResult.TERMINATE;
                String name = file.getFileName().toString().toLowerCase(Locale.ROOT);
                if (!attrs.isRegularFile() || Set.of("alternates", "http-alternates", "commondir", "gitdir", "shallow").contains(name)
                        || name.endsWith(".promisor")) { safe[0] = false; return FileVisitResult.TERMINATE; }
                return FileVisitResult.CONTINUE;
            }
        });
        if (!safe[0]) return false;
        // Reject includes, extensions and partial clones before Git loads local configuration.
        // Conservative token rejection also covers case/whitespace/continued-line spelling.
        Path config = metadata.resolve("config");
        try (var in = Files.newInputStream(config, LinkOption.NOFOLLOW_LINKS)) {
            byte[] bytes = in.readNBytes(65_537);
            if (bytes.length > 65_536) return false;
            String value = new String(bytes, StandardCharsets.UTF_8).replace("\\\r\n", "").replace("\\\n", "").toLowerCase(Locale.ROOT);
            return !value.contains("include") && !value.contains("extensions") && !value.contains("promisor");
        }
    }

    private static Result readCommit(Path gitDir, Path root) throws IOException, InterruptedException {
        var command = List.of("git", "--no-pager", "--no-replace-objects", "--no-optional-locks",
            "--git-dir=" + gitDir, "--work-tree=" + root,
            "-c", "core.fsmonitor=false", "-c", "gc.auto=0", "-c", "maintenance.auto=false",
            "-c", "log.showSignature=false", "log", "-1", "--no-show-signature", "--no-notes",
            "--no-decorate", "--no-patch", "--no-ext-diff", "--no-textconv", "--format=%H%n%ct", "HEAD", "--");
        var builder = new ProcessBuilder(command).redirectError(ProcessBuilder.Redirect.DISCARD);
        // Do not run from the scanned directory (notably Windows executable lookup).
        var env = builder.environment();
        env.keySet().removeIf(key -> key.toUpperCase(Locale.ROOT).startsWith("GIT_"));
        String emptyFile = System.getProperty("os.name").startsWith("Windows") ? "NUL" : "/dev/null";
        env.put("GIT_CONFIG_NOSYSTEM", "1"); env.put("GIT_CONFIG_GLOBAL", emptyFile);
        env.put("GIT_TERMINAL_PROMPT", "0"); env.put("GIT_NO_LAZY_FETCH", "1");
        Process process = builder.start();
        try {
            process.getOutputStream().close();
            if (!process.waitFor(5, TimeUnit.SECONDS)) throw new IOException("Git deadline exceeded");
            byte[] output = process.getInputStream().readNBytes(4097);
            if (output.length > 4096) throw new IOException("Git output limit exceeded");
            return new Result(process.exitValue(), new String(output, StandardCharsets.UTF_8));
        } finally { process.destroyForcibly(); }
    }
}
