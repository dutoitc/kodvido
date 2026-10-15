// SPDX-License-Identifier: GPL-3.0-only
package org.kodvido.engine;
import org.kodvido.domain.*;
import org.kodvido.domain.BackupAssessment.*;
import java.time.*;
import java.util.*;

public final class BackupAssessmentService {
    private final Catalog catalog;
    private final BackupAssessmentStore store;
    private final Clock clock;
    public BackupAssessmentService(Catalog catalog, BackupAssessmentStore store, Clock clock) {
        this.catalog=catalog; this.store=store; this.clock=clock;
    }
    public BackupAssessment load(UUID id) {
        requireProject(id);
        return store.find(id).orElseGet(() -> new BackupAssessment(id,0,
            Arrays.stream(Dimension.values()).map(d -> new Item(d,State.UNKNOWN,null,"")).toList(),null));
    }
    public void save(UUID id,long revision,List<Item> items) {
        requireProject(id);
        require(revision>=0 && revision<Long.MAX_VALUE-1,"Révision invalide.");
        validateItems(items,LocalDate.now(clock));
        store.save(new BackupAssessment(id,revision+1,items.stream()
            .map(i -> new Item(i.dimension(),i.state(),i.checkedOn(),i.evidence().strip())).toList(),clock.instant()));
    }
    /** Shared by local edits and JSON import; no upgrade from declared to verified. */
    static void validateItems(List<Item> items,LocalDate today) {
        require(items!=null,"Fiche obligatoire.");
        require(items.size()==Dimension.values().length,"Quatre dimensions requises.");
        var seen=EnumSet.noneOf(Dimension.class);
        for(var i:items) {
            require(i!=null,"Dimension obligatoire.");
            require(i.dimension()!=null,"Dimension obligatoire.");
            require(seen.add(i.dimension()),"Dimension dupliquée.");
            require(i.state()!=null,"État obligatoire.");
            require(i.evidence()!=null,"Description obligatoire.");
            require(i.evidence().length()<=1000,"Description trop longue.");
            require(i.evidence().indexOf('\0')<0,"Description invalide.");
            if(i.state()==State.UNKNOWN) {
                require(i.checkedOn()==null,"UNKNOWN ne porte pas de date de vérification.");
            } else {
                require(i.checkedOn()!=null,"Date de vérification obligatoire.");
                require(!i.checkedOn().isAfter(today),"Date future interdite (UTC).");
                require(!i.evidence().isBlank(),"Décrivez la preuve ou le problème constaté.");
            }
        }
    }
    private void requireProject(UUID id) {
        require(catalog.list().stream().anyMatch(p -> p.id().equals(id)),"Projet absent du catalogue.");
    }
    private static void require(boolean ok,String message) { if(!ok) throw new IllegalArgumentException(message); }
}
