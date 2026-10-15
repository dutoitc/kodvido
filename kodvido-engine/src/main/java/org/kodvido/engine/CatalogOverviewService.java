// SPDX-License-Identifier: GPL-3.0-only
package org.kodvido.engine;
import org.kodvido.domain.*;
import java.time.Instant;
import java.util.*;

/** Read-only catalogue projection and filtering: never invokes a filesystem detector. */
public final class CatalogOverviewService {
    public enum Technology { ALL, JAVA, PYTHON }
    public enum Filter { ALL, UNOBSERVED, OBSERVED, BACKUP_UNKNOWN, BACKUP_FAILED }
    public enum Sort { NAME, OBSERVED, INTERACTION }
    public record Row(CatalogProject project,CatalogObservation observation,int phases,boolean development,boolean operations,
            long unknownBackups,long failedBackups,Instant interactionAt) {}
    public record Page(List<Row> rows,int total,int filtered,int page,int pages) { public Page { rows=List.copyOf(rows); } }
    private final Catalog catalog;private final WorkflowStore workflow;private final BackupAssessmentStore backup;
    private final InteractionStore interactions;private final ObservationCache cache;
    public CatalogOverviewService(Catalog catalog,WorkflowStore workflow,BackupAssessmentStore backup,InteractionStore interactions,ObservationCache cache) {
        this.catalog=catalog;this.workflow=workflow;this.backup=backup;this.interactions=interactions;this.cache=cache;
    }
    public Page list(String query,Technology technology,Filter filter,Sort sort,int page) {
        if(query.length()>200 || page<0) throw new IllegalArgumentException("Filtre ou page invalide.");
        var projects=catalog.list();var rows=new ArrayList<Row>();var q=query.strip().toLowerCase(Locale.ROOT);
        for(var p:projects) {
            var o=cache.find(p).orElse(null);var n=workflow.find(p.id());var b=backup.find(p.id());var history=interactions.recent(p.id());
            var row=new Row(p,o,n.map(v->v.phases().size()).orElse(0),n.map(v->!v.development().isBlank()).orElse(false),
                n.map(v->!v.operations().isBlank()).orElse(false),
                b.map(v->v.items().stream().filter(i->i.state()==BackupAssessment.State.UNKNOWN).count()).orElse(4L),
                b.map(v->v.items().stream().filter(i->i.state()==BackupAssessment.State.DECLARED_FAILED).count()).orElse(0L),
                history.isEmpty()?null:history.getFirst().occurredAt());
            boolean tech=switch(technology) {case ALL->true;case JAVA->o!=null && o.technologies().contains("Java");case PYTHON->o!=null && o.technologies().contains("Python");};
            boolean state=switch(filter) {case ALL->true;case UNOBSERVED->o==null;case OBSERVED->o!=null;case BACKUP_UNKNOWN->row.unknownBackups()>0;case BACKUP_FAILED->row.failedBackups()>0;};
            String text=p.name()+" "+p.path()+" "+(o==null?"":o.evidence().toString());
            if(tech && state && text.toLowerCase(Locale.ROOT).contains(q)) rows.add(row);
        }
        Comparator<Row> byName=Comparator.comparing(v->v.project().name(),String.CASE_INSENSITIVE_ORDER);
        Comparator<Row> order=switch(sort) {
            case NAME->byName;
            case OBSERVED->Comparator.comparing(v->v.observation()==null?null:v.observation().observedAt(),Comparator.nullsLast(Comparator.<Instant>reverseOrder()));
            case INTERACTION->Comparator.comparing(Row::interactionAt,Comparator.nullsLast(Comparator.reverseOrder()));
        };
        rows.sort(order.thenComparing(byName).thenComparing(v->v.project().id()));
        int pages=Math.max(1,(rows.size()+24)/25), current=Math.min(page,pages-1), from=current*25;
        return new Page(rows.subList(from,Math.min(from+25,rows.size())),projects.size(),rows.size(),current,pages);
    }
}
