package org.kodvido.detection;

import org.kodvido.domain.DetectedProject;
import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;

/** Read-only candidate scan. Prototype: Maven multimodule deduplication is M1 TODO. */
public final class ProjectDirectoryScanner {
    private static final Set<String> IGNORED = Set.of(".git", "target", "build", ".venv", "venv", "node_modules", "dist", ".idea", "__pycache__");
    private static final Set<String> INDICATORS = Set.of("pom.xml", "pyproject.toml", "requirements.txt", "setup.py");

    public List<DetectedProject> discover(Path root) throws IOException {
        if (!Files.isDirectory(root)) throw new NoSuchFileException(root.toString());
        Map<Path, Set<String>> found = new LinkedHashMap<>();
        Files.walkFileTree(root, EnumSet.noneOf(FileVisitOption.class), 32, new SimpleFileVisitor<>() {
            @Override public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                if (!dir.equals(root) && IGNORED.contains(dir.getFileName().toString().toLowerCase(Locale.ROOT))) return FileVisitResult.SKIP_SUBTREE;
                return FileVisitResult.CONTINUE;
            }
            @Override public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                String name = file.getFileName().toString().toLowerCase(Locale.ROOT);
                if (INDICATORS.contains(name) && attrs.isRegularFile())
                    found.computeIfAbsent(file.getParent(), ignored -> new TreeSet<>()).add(name);
                return FileVisitResult.CONTINUE;
            }
            @Override public FileVisitResult visitFileFailed(Path file, IOException exc) { return FileVisitResult.CONTINUE; }
        });
        return found.entrySet().stream().map(e -> new DetectedProject(e.getKey(), e.getValue())).toList();
    }
}
