// SPDX-License-Identifier: GPL-3.0-only
package org.kodvido.domain;
import java.time.Instant;
import java.util.*;
/** Bounded last-inspection summary, not current filesystem truth. */
public record CatalogObservation(Instant observedAt,ProjectInspection.Status filesStatus,long files,long modified7Days,
        ProjectInspection.Status gitStatus,Instant committedAt,ProjectInspection.Status stackStatus,
        Set<String> technologies,List<StackObservation.Evidence> evidence,int scriptCount) {
    public CatalogObservation { technologies=Set.copyOf(technologies);evidence=List.copyOf(evidence); }
}
