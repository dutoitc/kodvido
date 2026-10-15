// SPDX-License-Identifier: GPL-3.0-only
package org.kodvido.engine;
import org.kodvido.domain.*;
import java.util.*;
/** Process-local, at most 1000 bounded summaries. Never part of JSON backups. */
public final class ObservationCache {
    private record Entry(CatalogProject project,CatalogObservation observation) {}
    private final LinkedHashMap<UUID,Entry> entries=new LinkedHashMap<>();
    public synchronized void remember(CatalogProject p,ProjectInspection files,GitObservation git,StackObservation stack) {
        var technologies=new HashSet<String>();
        for(var e:stack.evidence()) technologies.add(e.technology());
        var evidence=stack.evidence().stream().limit(4).map(e->new StackObservation.Evidence(
            bounded(e.technology()),bounded(e.source()),bounded(e.declaration()))).toList();
        var summary=new CatalogObservation(files.observedAt(),files.status(),files.files(),files.modified7Days(),
            git.status(),git.committedAt(),stack.status(),technologies,evidence,stack.scripts().size());
        entries.remove(p.id());entries.put(p.id(),new Entry(p,summary));
        if(entries.size()>1000) entries.remove(entries.keySet().iterator().next());
    }
    public synchronized Optional<CatalogObservation> find(CatalogProject p) {
        var e=entries.get(p.id());
        if(e==null) return Optional.empty();
        if(!e.project().equals(p)) { entries.remove(p.id());return Optional.empty(); }
        return Optional.of(e.observation());
    }
    public synchronized void forget(UUID id) { entries.remove(id); }
    private static String bounded(String value) { return value.length()<=160?value:value.substring(0,160)+"…"; }
}
