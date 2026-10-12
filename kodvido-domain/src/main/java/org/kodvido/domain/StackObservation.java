// SPDX-License-Identifier: GPL-3.0-only
package org.kodvido.domain;
import java.time.Instant;
import java.util.List;
/** Transient declarations, never a claim about the installed runtime or executable safety. */
public record StackObservation(Instant observedAt, String detectorVersion, ProjectInspection.Status status,
        List<Evidence> evidence, List<String> scripts, List<String> diagnostics) {
    public StackObservation { evidence=List.copyOf(evidence); scripts=List.copyOf(scripts); diagnostics=List.copyOf(diagnostics); }
    public record Evidence(String technology, String source, String declaration) {}
}
