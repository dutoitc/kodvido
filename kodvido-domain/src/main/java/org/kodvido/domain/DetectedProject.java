package org.kodvido.domain;
import java.nio.file.Path;
import java.time.Instant;
import java.util.*;
/** Evidence paths are local-only; never included in remote telemetry. */
public record DetectedProject(Path root, Set<String> evidence, Path parentRoot,
        DetectionOutcome outcome, List<String> diagnostics, Instant observedAt, String detectorVersion) {
    public DetectedProject { evidence = Set.copyOf(evidence); diagnostics = List.copyOf(diagnostics); }
    public DetectedProject(Path root, Set<String> evidence) {
        this(root, evidence, null, DetectionOutcome.SUPPORTED, List.of(), Instant.now(), "filesystem-1");
    }
}
