package org.kodvido.domain;
import java.nio.file.Path;
import java.util.Set;
public record DetectedProject(Path root, Set<String> evidence) {
    public DetectedProject { evidence = Set.copyOf(evidence); }
}
