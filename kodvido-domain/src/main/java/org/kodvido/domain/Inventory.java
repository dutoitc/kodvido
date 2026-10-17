// SPDX-License-Identifier: GPL-3.0-only
package org.kodvido.domain;
import java.util.*;
/** Persisted local evidence. Dates use ISO-8601; remote errors never contain raw process output. */
public final class Inventory {
    private Inventory() {}
    public enum Lifecycle { ACTIVE, PAUSED, COMPLETED, ARCHIVED }
    public enum RemoteState { ACCESSIBLE, NOT_FOUND_OR_PRIVATE, ACCESS_DENIED, TIMEOUT, UNREACHABLE, ERROR, NO_REMOTE, NO_GIT, UNSUPPORTED, NOT_CHECKED }
    public record Technology(String name,String version,String source) {}
    public record Dependency(String group,String artifact,String version,String source,String available,String result,String checkedAt) {
        public Dependency(String group,String artifact,String version,String source,String available,String result) {this(group,artifact,version,source,available,result,null);}
    }
    public record Remote(String name,String url,RemoteState state,String checkedAt) {}
    public record Snapshot(String observedAt,String status,long lines,String committedAt,String workingTree,
            List<Technology> technologies,List<Dependency> dependencies,List<Remote> remotes,List<String> diagnostics) {
        public Snapshot { technologies=List.copyOf(technologies);dependencies=List.copyOf(dependencies);remotes=List.copyOf(remotes);diagnostics=List.copyOf(diagnostics); }
    }
    public record Notes(Lifecycle lifecycle,String note,String tasks,long revision) {
        public static Notes empty() { return new Notes(Lifecycle.ACTIVE,"","",0); }
    }
    public interface Store {
        List<String> roots();
        void roots(List<String> roots);
        Optional<Snapshot> snapshot(CatalogProject project);
        void snapshot(CatalogProject project,Snapshot snapshot);
        Notes notes(UUID id);
        void notes(UUID id,Notes notes);
    }
    @FunctionalInterface public interface Inspector { Snapshot inspect(java.nio.file.Path path,boolean dependencies); }
}
