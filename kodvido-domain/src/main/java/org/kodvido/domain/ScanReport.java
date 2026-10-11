package org.kodvido.domain;
import java.util.List;
public record ScanReport(List<DetectedProject> candidates, List<String> diagnostics) {
    public ScanReport { candidates = List.copyOf(candidates); diagnostics = List.copyOf(diagnostics); }
}
