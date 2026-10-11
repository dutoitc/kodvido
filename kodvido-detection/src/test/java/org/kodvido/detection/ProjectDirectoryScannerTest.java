package org.kodvido.detection;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import static org.junit.jupiter.api.Assertions.*;
class ProjectDirectoryScannerTest {
 @TempDir Path temp;
 @Test void recognizesMavenAndPythonAndIgnoresGeneratedFolders() throws Exception {
   Files.createDirectories(temp.resolve("one")); Files.writeString(temp.resolve("one/pom.xml"), "<project/>");
   Files.createDirectories(temp.resolve("two")); Files.writeString(temp.resolve("two/pyproject.toml"), "[project]");
   Files.createDirectories(temp.resolve("one/target")); Files.writeString(temp.resolve("one/target/requirements.txt"), "x");
   var result = new ProjectDirectoryScanner().discover(temp);
   assertEquals(2, result.size());
   assertTrue(result.stream().anyMatch(x -> x.evidence().contains("pom.xml")));
   assertTrue(result.stream().anyMatch(x -> x.evidence().contains("pyproject.toml")));
 }
 @Test void nonexistentRootFailsExplicitly() { assertThrows(NoSuchFileException.class, () -> new ProjectDirectoryScanner().discover(temp.resolve("absent"))); }
}
