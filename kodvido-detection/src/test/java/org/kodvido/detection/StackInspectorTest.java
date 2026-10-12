package org.kodvido.detection;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.kodvido.domain.ProjectInspection.Status;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

class StackInspectorTest {
    @TempDir Path temp;
    final Instant now=Instant.parse("2026-01-01T00:00:00Z");
    final Clock clock=Clock.fixed(now,ZoneOffset.UTC);
    final StackInspector inspector=new StackInspector(clock);
    @Test void mavenDeclarationsKeepConflictingValuesAndExpressionsWithoutResolvingParents() throws Exception {
        Files.writeString(temp.resolve("pom.xml"),"""
            <project xmlns="http://maven.apache.org/POM/4.0.0"><parent><relativePath>../outside.xml</relativePath></parent>
            <properties><java.version>21</java.version><maven.compiler.release>${target.java}</maven.compiler.release></properties>
            <build><plugins><plugin><artifactId>maven-compiler-plugin</artifactId><configuration><release>17</release><source>11</source><target>11</target></configuration></plugin>
            <plugin><groupId>other</groupId><artifactId>maven-compiler-plugin</artifactId><configuration><release>99</release></configuration></plugin>
            <plugin><artifactId>other</artifactId></plugin></plugins></build>
            <profiles><profile><properties><java.version>99</java.version></properties></profile></profiles></project>
            """);
        var result=inspector.inspect(temp);
        assertThat(result.status()).isEqualTo(Status.PARTIAL);assertThat(result.observedAt()).isEqualTo(now);
        assertThat(result.evidence()).extracting(e->e.declaration()).containsExactly("21","${target.java}","17","11","11");
        assertThat(result.evidence()).allMatch(e->e.source().startsWith("pom.xml /"));
        assertThatThrownBy(()->result.evidence().clear()).isInstanceOf(UnsupportedOperationException.class);
    }
    @Test void pythonVersionAndSimpleProjectConstraintAreSeparateEvidence() throws Exception {
        Files.writeString(temp.resolve("pyproject.toml"),"[tool.other]\nrequires-python = 'ignored'\n[project] # package\nrequires-python = \">=3.10,<4\" # constraint\n[tool.poetry]\npython = 'ignored'\n");
        Files.writeString(temp.resolve(".python-version"),"3.12.1\n");
        var result=inspector.inspect(temp);
        assertThat(result.evidence()).extracting(e->e.declaration()).containsExactly(">=3.10,<4","3.12.1");
        assertThat(result.evidence()).allMatch(e->e.technology().equals("Python"));
    }
    @Test void scriptsAreShallowNamesOnlyAndNeverExecuted() throws Exception {
        Path scripts=Files.createDirectory(temp.resolve("scripts"));Files.createDirectory(scripts.resolve("nested"));
        Files.writeString(temp.resolve("mvnw"),"exit 99");Files.writeString(temp.resolve("run.py"),"raise Exception('must not run')");
        Files.writeString(scripts.resolve("build.CMD"),"exit 99");Files.writeString(scripts.resolve("nested/hidden.sh"),"exit 99");
        Files.writeString(temp.resolve("setup.py"),"raise Exception('never import')");
        var result=inspector.inspect(temp);
        assertThat(result.scripts()).containsExactly("mvnw","run.py","scripts/build.CMD","setup.py");
        assertThat(result.status()).isEqualTo(Status.PARTIAL);
        assertThat(Files.readString(temp.resolve("run.py"))).isEqualTo("raise Exception('must not run')");
        assertThatThrownBy(()->result.scripts().clear()).isInstanceOf(UnsupportedOperationException.class);
    }
    @Test void absentMissingNonDirectoryAndEmptyManifestsAreDistinguished() throws Exception {
        assertThat(inspector.inspect(temp).status()).isEqualTo(Status.UNKNOWN);
        assertThat(inspector.inspect(temp.resolve("absent")).status()).isEqualTo(Status.MISSING);
        Path file=Files.writeString(temp.resolve("file"),"text");assertThat(inspector.inspect(file).status()).isEqualTo(Status.UNSUPPORTED);
        Files.writeString(temp.resolve("pom.xml"),"<project/>");Files.writeString(temp.resolve("pyproject.toml"),"[tool.poetry]\npython = '^3.12'\n");
        var result=inspector.inspect(temp);assertThat(result.evidence()).isEmpty();
        assertThat(result.diagnostics()).contains("pom.xml:VERSION_UNKNOWN","pyproject.toml:VERSION_UNKNOWN");
    }
    @Test void malformedExternalEntitiesForeignNamespacesAndDeepXmlFailClosed() throws Exception {
        Path secret=Files.writeString(temp.resolve("secret.txt"),"not to be read");
        for(String xml:List.of("<project>","<wrong/>","<project xmlns='urn:foreign'/>",
            "<!DOCTYPE project [<!ENTITY leak SYSTEM '"+secret.toUri()+"'>]><project><properties><java.version>&leak;</java.version></properties></project>",
            "<project>"+"<nested>".repeat(80)+"</nested>".repeat(80)+"</project>",
            "<project><properties><java.version><nested>21</nested></java.version></properties></project>",
            "<project><properties><java.version> </java.version></properties></project>")) {
            Files.writeString(temp.resolve("pom.xml"),xml);var result=inspector.inspect(temp);
            assertThat(result.evidence()).isEmpty();assertThat(result.diagnostics()).contains("pom.xml:UNREADABLE_OR_UNSUPPORTED_SYNTAX");
            assertThat(result.toString()).doesNotContain("not to be read",secret.toString());
        }
    }
    @Test void unsupportedPythonSyntaxAndInvalidUtf8DoNotProduceVersionClaims() throws Exception {
        for(String toml:List.of("[project]\nrequires-python = [3, 12]","[project]\nrequires-python='3'\nrequires-python='4'", 
            "[project]\nrequires-python='3'\n[project]","[project]\nrequires-python = \"\"\"3.12\"\"\"")) {
            Files.writeString(temp.resolve("pyproject.toml"),toml);assertThat(inspector.inspect(temp).evidence()).isEmpty();
        }
        Files.write(temp.resolve("pyproject.toml"),new byte[]{(byte)0xc3,0x28});
        Files.writeString(temp.resolve(".python-version"),"3.12\n3.13");
        var result=inspector.inspect(temp);assertThat(result.evidence()).isEmpty();assertThat(result.diagnostics()).hasSize(2);
    }
    @Test void oversizedSpecialFilesAndScriptEnumerationLimitsAreExplicit() throws Exception {
        Files.writeString(temp.resolve("pom.xml"),"x".repeat(262145));Files.createDirectory(temp.resolve(".python-version"));
        Files.createDirectory(temp.resolve("fake.sh"));
        var result=inspector.inspect(temp);assertThat(result.diagnostics()).contains("pom.xml:UNSUPPORTED_TYPE_OR_SIZE",".python-version:UNSUPPORTED_TYPE_OR_SIZE","SCRIPT_LINK_OR_SPECIAL_FILE_SKIPPED");
        assertThat(new StackInspector(clock,0,Duration.ofSeconds(5)).inspect(temp).diagnostics()).contains("SCRIPT_LIMIT_REACHED");
        assertThat(new StackInspector(clock,200,Duration.ZERO).inspect(temp).diagnostics()).contains("SCRIPT_LIMIT_REACHED");
        Thread.currentThread().interrupt();
        try { assertThat(inspector.inspect(temp).diagnostics()).contains("SCRIPT_LIMIT_REACHED"); } finally { Thread.interrupted(); }
    }
    @Test void symbolicLinksAndLinkedAncestorsAreNotFollowed() throws Exception {
        Path outside=Files.createDirectory(temp.resolve("outside"));Path root=Files.createDirectory(temp.resolve("project"));
        Files.writeString(outside.resolve("secret.py"),"must not run");
        try { Files.createSymbolicLink(root.resolve("pom.xml"),outside.resolve("secret.py")); }
        catch(UnsupportedOperationException | java.io.IOException | SecurityException ex) { Assumptions.abort("Symbolic links unavailable"); }
        Files.createSymbolicLink(root.resolve("linked.py"),outside.resolve("secret.py"));
        var result=inspector.inspect(root);assertThat(result.evidence()).isEmpty();assertThat(result.scripts()).isEmpty();
        assertThat(result.diagnostics()).contains("pom.xml:UNSUPPORTED_TYPE_OR_SIZE","SCRIPT_LINK_OR_SPECIAL_FILE_SKIPPED");
        Files.createSymbolicLink(root.resolve("scripts"),outside);assertThat(inspector.inspect(root).status()).isEqualTo(Status.FAILED);
        Files.createSymbolicLink(temp.resolve("linked-root"),root);assertThat(inspector.inspect(temp.resolve("linked-root")).status()).isEqualTo(Status.FAILED);
    }
}
