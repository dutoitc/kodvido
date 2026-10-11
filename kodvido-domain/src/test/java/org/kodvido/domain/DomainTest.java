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
}
