// SPDX-License-Identifier: GPL-3.0-only
package org.kodvido.app;
import org.kodvido.domain.ProjectGuidance;
import org.kodvido.domain.ProjectGuidance.*;
import org.kodvido.engine.GuidanceService;
import java.util.*;
/** Presentation wording belongs to the web adapter, not the engine. */
public final class GuidanceView {
    private GuidanceView() {}
    public record Card(String title,String reason,String suggestion) {}
    public record Draft(String detector,String status,String text) {}
    private static final Map<Topic,String> TITLES=Map.ofEntries(
        Map.entry(Topic.FILES,"Activité des fichiers"),Map.entry(Topic.GIT,"Git local"),Map.entry(Topic.STACK,"Versions et scripts"),
        Map.entry(Topic.DEVELOPMENT,"Reprise du développement"),Map.entry(Topic.OPERATIONS,"Reprise de l’exploitation"),
        Map.entry(Topic.BACKUP_GIT_REMOTE,"Synchronisation Git distante"),Map.entry(Topic.BACKUP_UNTRACKED_FILES,"Copie des fichiers non suivis / ignorés"),
        Map.entry(Topic.BACKUP_APPLICATION_DATA,"Sauvegarde des données applicatives"),Map.entry(Topic.BACKUP_RESTORE_TEST,"Test de restauration"));
    private static final Map<Reason,String> MESSAGES=Map.of(
        Reason.NOT_OBSERVED,"Lancez « Observer les fichiers » pour disposer d’un constat dans cette vue.",
        Reason.PARTIAL,"Consultez les limites et exclusions du détecteur. Complétez manuellement les points non couverts ; le constat est partiel.",
        Reason.MISSING,"Vérifiez l’emplacement et la présence de la source attendue. Un dépôt Git est facultatif ; son absence n’est pas un défaut du détecteur.",
        Reason.UNSUPPORTED,"Consultez les structures prises en charge. Si nécessaire, préparez un exemple synthétique pour proposer une évolution.",
        Reason.FAILED,"Vérifiez les droits et la lisibilité de la source, puis relancez l’observation. Un échec ne prouve pas une absence.",
        Reason.UNKNOWN,"Renseignez ou vérifiez manuellement les éléments manquants. Aucun constat exploitable ne permet de conclure.",
        Reason.NOT_DOCUMENTED,"Complétez la fiche de reprise avec les étapes utiles et les prérequis, sans identifiants ni secrets.",
        Reason.BACKUP_UNKNOWN,"Vérifiez ce périmètre puis renseignez une preuve datée, ou conservez UNKNOWN si vous ne pouvez pas conclure.",
        Reason.BACKUP_FAILED,"Examinez le problème déclaré et refaites un contrôle avant de modifier la preuve. Aucune réparation n’est lancée.");
    public static List<Card> cards(ProjectGuidance guidance) {
        return guidance.advice().stream().map(a->new Card(TITLES.get(a.topic()),a.reason().name(),MESSAGES.get(a.reason()))).toList();
    }
    public static List<Draft> drafts(ProjectGuidance guidance,GuidanceService service) {
        return guidance.gaps().stream().map(g->new Draft(g.detector().name(),g.status().name(),service.draft(g))).toList();
    }
}
