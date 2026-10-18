package org.kodvido.detection;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.kodvido.domain.Inventory.*;
import java.nio.file.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
class InventoryInspectorTest {
 @TempDir Path temp;
 @BeforeEach void canonical() throws Exception {temp=temp.toRealPath();}
 InventoryInspector inspector=new InventoryInspector(new InventoryGit(),new VersionLookup(uri->"<metadata><versioning><release>2.0</release></versioning></metadata>".getBytes()));
 @Test void inventoriesModulesFrontendAndPhysicalLinesWithoutChangingFiles() throws Exception {
  Files.writeString(temp.resolve("pom.xml"),"<project><properties><java.version>11</java.version></properties><dependencies><dependency><groupId>org.example</groupId><artifactId>demo</artifactId><version>1.0</version></dependency></dependencies></project>");
  var module=Files.createDirectory(temp.resolve("module"));Files.writeString(module.resolve("pom.xml"),"<project><properties><java.version>17</java.version></properties></project>");
  Files.writeString(temp.resolve("package.json"),"{\"dependencies\":{\"@angular/core\":\"^20.0.0\",\"bootstrap\":\"5.3.0\"}}");
  Files.writeString(temp.resolve("app.java"),"// comment\nclass App {}\n");Files.writeString(temp.resolve("pyproject.toml"),"[project]\nrequires-python = \">=3.11\"\n");
  var ignored=Files.createDirectory(temp.resolve("node_modules"));Files.writeString(ignored.resolve("package.json"),"{\"dependencies\":{\"bootstrap\":\"OLD\"}}");
  var nested=Files.createDirectory(temp.resolve("nested"));Files.createDirectory(nested.resolve(".git"));Files.writeString(nested.resolve("pom.xml"),"<project><properties><java.version>8</java.version></properties></project>");
  var before=Files.readAllBytes(temp.resolve("pom.xml"));var modified=Files.getLastModifiedTime(temp.resolve("pom.xml"));
  var result=inspector.inspect(temp,true);
  assertThat(result.technologies()).extracting(Technology::version).contains("11","17","^20.0.0","5.3.0",">=3.11").doesNotContain("OLD","8");
  assertThat(result.dependencies()).anyMatch(d->d.available().equals("2.0") && d.result().equals("COMPARE_RELEASE"));
  assertThat(result.lines()).isGreaterThanOrEqualTo(4);assertThat(result.remotes().getFirst().state()).isEqualTo(RemoteState.NO_GIT);
  assertThat(Files.readAllBytes(temp.resolve("pom.xml"))).isEqualTo(before);assertThat(Files.getLastModifiedTime(temp.resolve("pom.xml"))).isEqualTo(modified);
 }
 @Test void malformedLargeLinkedAndMissingFilesAreExplicit() throws Exception {
  Files.writeString(temp.resolve("pom.xml"),"<!DOCTYPE x [<!ENTITY e SYSTEM 'file:///never'>]><project>&e;</project>");
  Files.writeString(temp.resolve("package.json"),"bad");Files.writeString(temp.resolve("large.java"),"x".repeat(262145));
  var result=inspector.inspect(temp,false);assertThat(result.diagnostics()).contains("UNREADABLE_OR_UNSUPPORTED_FILE","LARGE_SOURCE_EXCLUDED");
  assertThat(inspector.inspect(temp.resolve("missing"),false).status()).isEqualTo("MISSING");
  assertThat(inspector.inspect(temp.resolve("pom.xml"),false).status()).isEqualTo("FAILED");
  Files.writeString(temp.resolve("pom.xml"),"<other/>");assertThat(inspector.inspect(temp,false).dependencies()).isEmpty();
  Files.writeString(temp.resolve("package.json"),"x".repeat(262145));assertThat(inspector.inspect(temp,false).technologies()).isEmpty();
 }
 @Test void discoveryGroupsOneRepositoryAndKeepsNestedRepositories() throws Exception {
  Files.createDirectory(temp.resolve(".git"));Files.writeString(temp.resolve("pom.xml"),"<project/>");
  var child=Files.createDirectory(temp.resolve("frontend"));Files.writeString(child.resolve("package.json"),"{}");
  var nested=Files.createDirectory(temp.resolve("other"));Files.createDirectory(nested.resolve(".git"));
  var candidates=new ProjectDirectoryScanner().discover(temp);
  assertThat(candidates).hasSize(3);assertThat(candidates.stream().filter(c->c.root().equals(child.toAbsolutePath())).findFirst().orElseThrow().parentRoot()).isEqualTo(temp.toRealPath());
  assertThat(candidates.stream().filter(c->c.root().equals(nested.toAbsolutePath())).findFirst().orElseThrow().parentRoot()).isNull();
 }
}
