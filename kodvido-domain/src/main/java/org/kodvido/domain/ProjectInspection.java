package org.kodvido.domain;

import java.time.Instant;
import java.util.List;

/** Transient measured evidence. Document contents are deliberately not part of this snapshot. */
public record ProjectInspection(Instant observedAt, String detectorVersion, Status status,
        long files, long modified7Days, long modified30Days, Instant latestModification,
        List<Document> documents, List<String> diagnostics) {
    public ProjectInspection {
        documents = List.copyOf(documents);
        diagnostics = List.copyOf(diagnostics);
    }
    public enum Status { SUPPORTED, PARTIAL, MISSING, UNSUPPORTED, FAILED, UNKNOWN }
    public record Document(String name, Status status) {}
    public record DocumentText(String name, Status status, String text) {}
}
