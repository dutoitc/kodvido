// SPDX-License-Identifier: GPL-3.0-only
package org.kodvido.engine;
import org.kodvido.domain.*;
import java.nio.file.Path;
import java.time.*;
import java.util.*;

/** Bounded, explicit metadata restoration. Never touches the project filesystem. */
public final class BackupService {
    public static final int MAX_PROJECTS = 1000;
    private final BackupStore store;
    private final Clock clock;
    private Preview pending;
    public record Preview(UUID id, Instant expiresAt, CatalogBackup backup) {}
    public BackupService(BackupStore store, Clock clock) { this.store=store; this.clock=clock; }
    public CatalogBackup export() {
        var backup = new CatalogBackup(clock.instant(), store.snapshot());
        validate(backup); return backup;
    }
    public synchronized Preview preview(CatalogBackup backup) {
        validate(backup);
        pending = new Preview(UUID.randomUUID(), clock.instant().plusSeconds(1800), backup);
        return pending;
    }
    public synchronized void confirm(UUID token, List<String> paths) {
        require(pending != null, "Aperçu absent.");
        require(pending.id().equals(token), "Aperçu remplacé.");
        require(clock.instant().isBefore(pending.expiresAt()), "Aperçu expiré.");
        var entries = pending.backup().projects();
        require(paths.size() == entries.size(), "Un chemin par projet est requis.");
        var restored = new ArrayList<CatalogBackup.Entry>();
        var unique = new HashSet<Path>();
        for (int i=0;i<entries.size();i++) {
            text(paths.get(i), 4000);
            Path path = Path.of(paths.get(i));
            require(path.isAbsolute(), "Chemin absolu natif requis ; adaptez les chemins étrangers.");
            path = path.normalize();
            require(unique.add(path), "Chemins dupliqués.");
            var e=entries.get(i);
            restored.add(new CatalogBackup.Entry(e.id(),e.name(),path.toString(),e.createdAt(),e.updatedAt(),e.notes(),e.assessment()));
        }
        store.restore(List.copyOf(restored));
        pending=null;
    }
    private void validate(CatalogBackup backup) {
        Objects.requireNonNull(backup.exportedAt());
        require(backup.projects().size() <= MAX_PROJECTS, "Maximum 1000 projets.");
        var ids=new HashSet<UUID>();
        for (var e:backup.projects()) {
            Objects.requireNonNull(e.id()); Objects.requireNonNull(e.createdAt()); Objects.requireNonNull(e.updatedAt());
            require(ids.add(e.id()), "Identifiants dupliqués.");
            text(e.name(),250); text(e.path(),4000);
            if(e.assessment()!=null) {
                var a=e.assessment();
                require(e.id().equals(a.projectId()),"Preuves liées à un autre projet.");
                require(a.revision()>0 && a.revision()<Long.MAX_VALUE,"Révision invalide.");
                Objects.requireNonNull(a.updatedAt());
                BackupAssessmentService.validateItems(a.items(),LocalDate.now(clock));
            }
            if(e.notes()!=null) {
                var n=e.notes();
                require(e.id().equals(n.projectId()), "Notes liées à un autre projet.");
                require(n.revision()>0, "Révision invalide.");
                require(n.revision()<Long.MAX_VALUE, "Révision invalide.");
                Objects.requireNonNull(n.updatedAt());
                require(n.phases().size()<=5, "Cinq phases au maximum.");
                optionalText(n.development(),4000); optionalText(n.operations(),4000);
                for(var p:n.phases()) {
                    Objects.requireNonNull(p.date()); Objects.requireNonNull(p.state()); text(p.description(),500);
                }
            }
        }
    }
    private static void text(String value,int max) { optionalText(value,max); require(!value.isBlank(), "Texte obligatoire."); }
    private static void optionalText(String value,int max) {
        Objects.requireNonNull(value); require(value.length()<=max,"Texte trop long."); require(value.indexOf('\0')<0,"Texte invalide.");
    }
    private static void require(boolean valid,String message) { if(!valid) throw new IllegalArgumentException(message); }
}
