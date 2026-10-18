// SPDX-License-Identifier: GPL-3.0-only
package org.kodvido.engine;
import org.kodvido.domain.*;
import org.kodvido.domain.Inventory.*;
import org.kodvido.detection.ProjectDirectoryScanner;
import java.util.*;
/** Local inventory use cases, independent from scheduling and web presentation. */
public final class InventoryService {
    public enum Sort { NAME, LIFECYCLE, LOCATION, JAVA, ANGULAR, BOOTSTRAP, COMMIT, LINES, REMOTE, NOTE, OBSERVED, VERSIONS }
    public record Row(CatalogProject project,Notes notes,Snapshot snapshot) {
        public String versions(String technology) {return snapshot==null?"":snapshot.technologies().stream().filter(t->t.name().equalsIgnoreCase(technology)).map(Technology::version).distinct().sorted(Comparator.comparing(InventoryService::versionKey)).collect(java.util.stream.Collectors.joining("\n"));}
        public long comparisons() {return snapshot==null?0:snapshot.dependencies().stream().filter(d->d.result().equals("COMPARE_RELEASE")).count();}
        public long unresolvedVersions() {return snapshot==null?0:snapshot.dependencies().stream().filter(d->!Set.of("COMPARE_RELEASE","MATCHES_RELEASE").contains(d.result())).count();}
        public boolean versionsChecked() {return snapshot!=null && snapshot.dependencies().stream().anyMatch(d->d.checkedAt()!=null);}
        public String remoteState() {return snapshot==null?"NOT_CHECKED":snapshot.remotes().stream().map(r->r.state().name()).distinct().collect(java.util.stream.Collectors.joining(" / "));}
    }
    private final Catalog catalog;private final Store store;private final Inspector inspector;
    private volatile ScanReport discovery=new ScanReport(List.of(),List.of());
    public InventoryService(Catalog catalog,Store store,Inspector inspector) {this.catalog=catalog;this.store=store;this.inspector=inspector;}
    public List<String> roots() {return store.roots();}
    public void roots(List<String> values) {
        if(values.size()>20) throw new IllegalArgumentException("Maximum 20 racines.");
        store.roots(values.stream().map(CatalogService::parsePath).map(p->p.normalize().toString()).distinct().toList());
    }
    public ScanReport discovery() {return discovery;}
    public void refreshAll() {
        var roots=store.roots().stream().map(java.nio.file.Path::of).toList();
        var found=new ProjectDirectoryScanner().scan(roots);
        var paths=catalog.list().stream().map(CatalogProject::path).collect(java.util.stream.Collectors.toSet());
        discovery=new ScanReport(found.candidates().stream().filter(p->p.parentRoot()==null && !paths.contains(p.root())).toList(),found.diagnostics());
        for(var project:catalog.list()) {if(Thread.currentThread().isInterrupted()) return;refresh(project,false);}
    }
    public void refresh(UUID id,boolean versions) {
        refresh(project(id),versions);
    }
    private void refresh(CatalogProject p,boolean versions) {
        var fresh=inspector.inspect(p.path(),versions);var previous=store.snapshot(p);
        if(!versions && previous.isPresent()) {
            var old=previous.get().dependencies();
            var retained=fresh.dependencies().stream().map(d->old.stream().filter(o->o.group().equals(d.group()) && o.artifact().equals(d.artifact()) && o.version().equals(d.version()) && o.source().equals(d.source())).findFirst().orElse(d)).toList();
            fresh=new Snapshot(fresh.observedAt(),fresh.status(),fresh.lines(),fresh.committedAt(),fresh.workingTree(),fresh.technologies(),retained,fresh.remotes(),fresh.diagnostics());
        }
        store.snapshot(p,fresh);
    }
    public Row row(UUID id) {var p=project(id);return new Row(p,store.notes(id),store.snapshot(p).orElse(null));}
    public void notes(UUID id,Lifecycle state,String note,String tasks,long revision) {
        project(id);
        if(state==null || note==null || tasks==null || note.length()>1000 || tasks.length()>10000 || note.indexOf(0)>=0 || tasks.indexOf(0)>=0 || revision<0 || revision==Long.MAX_VALUE) throw new IllegalArgumentException("Notes invalides (1000 caractères, tâches 10000 maximum).");
        store.notes(id,new Notes(state,note.strip(),tasks.strip(),revision));
    }
    private static String versionKey(String value) {
        return java.util.regex.Pattern.compile("[0-9]+").matcher(value).replaceAll(m->String.format(Locale.ROOT,"%020d",new java.math.BigInteger(m.group())));
    }
    private CatalogProject project(UUID id) {return catalog.list().stream().filter(p->p.id().equals(id)).findFirst().orElseThrow(()->new IllegalArgumentException("Projet absent."));}
    public List<Row> rows(String query,String lifecycle,String technology,String remote,Sort sort,boolean descending) {
        if(query.length()>200) throw new IllegalArgumentException("Recherche trop longue.");
        String q=query.toLowerCase(Locale.ROOT);
        var rows=catalog.list().stream().map(p->new Row(p,store.notes(p.id()),store.snapshot(p).orElse(null)))
            .filter(r->lifecycle.isEmpty() || r.notes().lifecycle().name().equals(lifecycle))
            .filter(r->technology.isEmpty() || !r.versions(technology).isEmpty())
            .filter(r->remote.isEmpty() || r.remoteState().contains(remote))
            .filter(r->(r.project().name()+" "+r.project().path()+" "+r.notes().note()+" "+(r.snapshot()==null?"":r.snapshot().technologies())).toLowerCase(Locale.ROOT).contains(q)).toList();
        Comparator<Row> comparator=switch(sort) {
            case NAME -> Comparator.comparing(r->r.project().name().toLowerCase(Locale.ROOT));
            case LIFECYCLE -> Comparator.comparing(r->r.notes().lifecycle());
            case LOCATION -> Comparator.comparing(r->r.project().path().toString());
            case JAVA -> Comparator.comparing(r->versionKey(r.versions("Java")));
            case ANGULAR -> Comparator.comparing(r->versionKey(r.versions("Angular")));
            case BOOTSTRAP -> Comparator.comparing(r->versionKey(r.versions("Bootstrap")));
            case COMMIT -> Comparator.comparing(r->r.snapshot()==null?"":Objects.toString(r.snapshot().committedAt(),""));
            case OBSERVED -> Comparator.comparing(r->r.snapshot()==null?"":r.snapshot().observedAt());
            case LINES -> Comparator.comparingLong(r->r.snapshot()==null?-1:r.snapshot().lines());
            case REMOTE -> Comparator.comparing(Row::remoteState);
            case VERSIONS -> Comparator.comparingLong(Row::comparisons);
            case NOTE -> Comparator.comparing(r->r.notes().note());
        };
        if(descending) comparator=comparator.reversed();
        return rows.stream().sorted(comparator.thenComparing(r->r.project().id())).toList();
    }
}
