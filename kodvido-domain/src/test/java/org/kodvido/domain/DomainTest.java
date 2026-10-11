package org.kodvido.domain;
import org.junit.jupiter.api.Test;
import java.nio.file.Path;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
class DomainTest {
    @Test void catalogFailureDoesNotExposeInternalExceptions() {
        var failure = new CatalogException();
        assertThat(failure.getCause()).isNull();
        assertThat(failure.getMessage()).isEqualTo("Catalogue indisponible ou chemin déjà enregistré.");
    }
    @Test void evidenceAndReportsAreImmutableSnapshots() {
        Set<String> evidence = new HashSet<>(Set.of("pom.xml"));
        var project = new DetectedProject(Path.of("project"), evidence); evidence.clear();
        assertThat(project.evidence()).containsExactly("pom.xml");
        var projects = new ArrayList<>(List.of(project)); var diagnostics = new ArrayList<>(List.of("partial"));
        var report = new ScanReport(projects, diagnostics); projects.clear(); diagnostics.clear();
        assertThat(report.candidates()).containsExactly(project); assertThat(report.diagnostics()).containsExactly("partial");
        assertThatThrownBy(() -> report.candidates().clear()).isInstanceOf(UnsupportedOperationException.class);
    }
    @Test void inspectionCannotChangeWhenDetectorCollectionsAreReused() {
        var documents = new ArrayList<>(List.of(new ProjectInspection.Document("PROJECT.md", ProjectInspection.Status.MISSING)));
        var notes = new ArrayList<>(List.of("LIMIT_REACHED"));
        var snapshot = new ProjectInspection(java.time.Instant.EPOCH, "test", ProjectInspection.Status.PARTIAL,
            0, 0, 0, null, documents, notes);
        documents.clear(); notes.clear();
        assertThat(snapshot.documents()).hasSize(1);
        assertThat(snapshot.diagnostics()).containsExactly("LIMIT_REACHED");
        assertThatThrownBy(() -> snapshot.documents().clear()).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> snapshot.diagnostics().clear()).isInstanceOf(UnsupportedOperationException.class);
    }
}
