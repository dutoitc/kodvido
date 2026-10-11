package org.kodvido.detection;

import org.kodvido.domain.ProjectInspection;
import org.kodvido.domain.ProjectInspection.*;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.*;
import java.util.*;

/** Local, bounded metadata inspection. Never runs Git, scripts or document instructions. */
public final class ProjectInspector {
    public static final List<String> DOCUMENTS = List.of("PROJECT.md", "TODO.md", "AGENTS.md", "README.md");
    public static final Set<String> EXCLUDED = Set.of(".git", "target", "build", ".venv", "venv",
        "node_modules", "dist", ".idea", "__pycache__", "vendor", ".gradle", ".mypy_cache", ".pytest_cache");
    private static final int DOCUMENT_BYTES = 262_144;
    private final Clock clock;
    private final int maxDepth, maxEntries;
    private final Duration timeout;

    public ProjectInspector(Clock clock) { this(clock, 32, 100_000, Duration.ofSeconds(30)); }
    public ProjectInspector(Clock clock, int maxDepth, int maxEntries, Duration timeout) {
        this.clock = clock; this.maxDepth = maxDepth; this.maxEntries = maxEntries; this.timeout = timeout;
    }

    public ProjectInspection inspect(Path input) {
        Instant now = clock.instant();
        var notes = new TreeSet<String>();
        var documents = new ArrayList<Document>();
        long[] counts = new long[3];
        Instant[] latest = {null};
        Status status;
        try {
            Path root = SafePaths.directory(input);
            for (String name : DOCUMENTS) documents.add(new Document(name, documentStatus(root.resolve(name))));
            long started = System.nanoTime();
            Files.walkFileTree(root, EnumSet.noneOf(FileVisitOption.class), maxDepth, new SimpleFileVisitor<Path>() {
                int entries;
                private FileVisitResult next() {
                    if (++entries > maxEntries || System.nanoTime() - started >= timeout.toNanos() || Thread.currentThread().isInterrupted()) {
                        notes.add("LIMIT_REACHED"); return FileVisitResult.TERMINATE;
                    }
                    return FileVisitResult.CONTINUE;
                }
                @Override public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                    if (next() == FileVisitResult.TERMINATE) return FileVisitResult.TERMINATE;
                    if (!dir.equals(root) && EXCLUDED.contains(dir.getFileName().toString().toLowerCase(Locale.ROOT))) return FileVisitResult.SKIP_SUBTREE;
                    try { SafePaths.directory(dir); }
                    catch (IOException | SecurityException ex) { notes.add("UNREADABLE_OR_LINKED_ENTRY"); return FileVisitResult.SKIP_SUBTREE; }
                    return FileVisitResult.CONTINUE;
                }
                @Override public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                    if (next() == FileVisitResult.TERMINATE) return FileVisitResult.TERMINATE;
                    if (attrs.isDirectory()) { notes.add("DEPTH_REACHED"); return FileVisitResult.CONTINUE; }
                    if (!attrs.isRegularFile() || attrs.isSymbolicLink() || attrs.isOther()) {
                        notes.add("UNREADABLE_OR_LINKED_ENTRY"); return FileVisitResult.CONTINUE;
                    }
                    counts[0]++;
                    Instant modified = attrs.lastModifiedTime().toInstant();
                    if (modified.isAfter(now)) { notes.add("FUTURE_TIMESTAMP"); return FileVisitResult.CONTINUE; }
                    if (latest[0] == null || modified.isAfter(latest[0])) latest[0] = modified;
                    if (!modified.isBefore(now.minus(Duration.ofDays(7)))) counts[1]++;
                    if (!modified.isBefore(now.minus(Duration.ofDays(30)))) counts[2]++;
                    return FileVisitResult.CONTINUE;
                }
                @Override public FileVisitResult visitFileFailed(Path file, IOException ex) {
                    notes.add("UNREADABLE_ENTRY"); return next();
                }
                @Override public FileVisitResult postVisitDirectory(Path dir, IOException ex) {
                    if (ex != null) notes.add("UNREADABLE_ENTRY");
                    return FileVisitResult.CONTINUE;
                }
            });
            status = notes.isEmpty() ? Status.SUPPORTED : Status.PARTIAL;
        } catch (NoSuchFileException ex) { status = Status.MISSING; }
        catch (NotDirectoryException ex) { status = Status.UNSUPPORTED; }
        catch (IOException | SecurityException ex) { status = Status.FAILED; }
        return new ProjectInspection(now, "filesystem-activity-1", status, counts[0], counts[1], counts[2],
            latest[0], documents, new ArrayList<>(notes));
    }

    private Status documentStatus(Path file) {
        try {
            var attrs = Files.readAttributes(file, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
            if (!attrs.isRegularFile() || attrs.isSymbolicLink() || attrs.isOther()) return Status.UNSUPPORTED;
            return attrs.size() > DOCUMENT_BYTES ? Status.UNSUPPORTED : Status.SUPPORTED;
        } catch (NoSuchFileException ex) { return Status.MISSING; }
        catch (IOException | SecurityException ex) { return Status.FAILED; }
    }

    /** Exact allowlist: no arbitrary path, relative traversal, HTML or Markdown execution. */
    public DocumentText readDocument(Path input, String name) {
        if (!DOCUMENTS.contains(name)) throw new IllegalArgumentException("Document non pris en charge.");
        try {
            Path file = SafePaths.directory(input).resolve(name);
            Status status = documentStatus(file);
            if (status != Status.SUPPORTED) return new DocumentText(name, status, "");
            try (var in = Files.newInputStream(file, LinkOption.NOFOLLOW_LINKS)) {
                byte[] bytes = in.readNBytes(DOCUMENT_BYTES + 1);
                if (bytes.length > DOCUMENT_BYTES) return new DocumentText(name, Status.UNSUPPORTED, "");
                String text = StandardCharsets.UTF_8.newDecoder().decode(ByteBuffer.wrap(bytes)).toString();
                return new DocumentText(name, Status.SUPPORTED, text);
            }
        } catch (NoSuchFileException ex) { return new DocumentText(name, Status.MISSING, ""); }
        catch (IOException | SecurityException ex) { return new DocumentText(name, Status.FAILED, ""); }
    }
}
