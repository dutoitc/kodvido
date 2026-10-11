package org.kodvido.detection;
import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
/** Reject symlinks, junctions/reparse points and symlink ancestors. No realpath traversal. */
public final class SafePaths {
    private SafePaths() {}
    public static Path directory(Path input) throws IOException {
        Path absolute = input.toAbsolutePath();
        checkComponents(absolute);
        Path normalized = absolute.normalize();
        if (!Files.isDirectory(normalized, LinkOption.NOFOLLOW_LINKS)) throw new NotDirectoryException("Not a directory");
        return normalized.toRealPath(LinkOption.NOFOLLOW_LINKS);
    }
    private static void checkComponents(Path path) throws IOException {
        Path cursor = path.getRoot();
        for (Path part : path) {
            cursor = cursor.resolve(part);
            var attrs = Files.readAttributes(cursor, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
            if (attrs.isSymbolicLink() || attrs.isOther()) throw new IOException("Linked paths are not allowed");
        }
    }
}
