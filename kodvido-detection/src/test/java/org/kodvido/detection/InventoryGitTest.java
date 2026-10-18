package org.kodvido.detection;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.kodvido.domain.Inventory.*;
import java.nio.file.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
class InventoryGitTest {
 @TempDir Path temp;
 void metadata() throws Exception {Files.createDirectory(temp.resolve(".git"));Files.writeString(temp.resolve(".git/config"),"[core]\n repositoryformatversion = 0\n");}
 @Test void statusesAndRemoteAddressesAreConservative() {
  var cases=Map.of("404",RemoteState.NOT_FOUND_OR_PRIVATE,"permission denied",RemoteState.ACCESS_DENIED,"Authentication failed",RemoteState.ACCESS_DENIED,"timed out",RemoteState.TIMEOUT,"could not resolve host",RemoteState.UNREACHABLE,"unknown",RemoteState.ERROR);
  cases.forEach((text,state)->assertThat(InventoryGit.classify(new InventoryGit.Result(1,text))).isEqualTo(state));
  assertThat(InventoryGit.classify(new InventoryGit.Result(0,""))).isEqualTo(RemoteState.ACCESSIBLE);
  assertThat(InventoryGit.classify(new InventoryGit.Result(124,""))).isEqualTo(RemoteState.TIMEOUT);
  for(String url:List.of("https://example.org/a.git","http://example.org/a","ssh://git@example.org/a","git@example.org:a.git")) assertThat(InventoryGit.allowed(url)).isTrue();
  for(String url:List.of("https://token@example.org/a","https://example.org/a?token=secret","https://example.org/#secret","ext::sh","file:///tmp/a","ssh://user:pass@example.org/a","/tmp/repo","bad url","x".repeat(2049))) assertThat(InventoryGit.allowed(url)).isFalse();
 }
 @Test void readsConfiguredRemotesWithoutExecutingProjectConfigurationOrPersistingErrors() throws Exception {
  metadata();var commands=new ArrayList<List<String>>();
  var git=new InventoryGit((args,network)->{commands.add(args);if(args.contains("status")) return new InventoryGit.Result(0," M file");if(args.contains("config")) return new InventoryGit.Result(0,"remote.origin.url https://example.org/repo.git\nremote.secret.url https://token@example.org/repo\n");return new InventoryGit.Result(1,"404 secret details");});
  var result=git.inspect(temp);assertThat(result.workingTree()).isEqualTo("Modifications locales");
  assertThat(result.remotes()).extracting(Remote::state).containsExactly(RemoteState.NOT_FOUND_OR_PRIVATE,RemoteState.UNSUPPORTED);
  assertThat(result.toString()).doesNotContain("token@","secret details");assertThat(commands).anyMatch(a->a.contains("--no-includes"));
  assertThat(new InventoryGit((a,n)->new InventoryGit.Result(0,"")).inspect(temp).workingTree()).isEqualTo("Propre");
  assertThat(new InventoryGit((a,n)->new InventoryGit.Result(1,"")).inspect(temp).workingTree()).isEqualTo("Non vérifié");
  assertThat(new InventoryGit((a,n)->{throw new java.io.IOException();}).inspect(temp).remotes().getFirst().state()).isEqualTo(RemoteState.ERROR);
  assertThat(new InventoryGit((a,n)->{throw new InterruptedException();}).inspect(temp).remotes().getFirst().state()).isEqualTo(RemoteState.NOT_CHECKED);Thread.interrupted();
  Files.writeString(temp.resolve(".git/config"),"[include]\n path = somewhere");assertThat(git.inspect(temp).remotes().getFirst().state()).isEqualTo(RemoteState.UNSUPPORTED);
 }
 @Test void realGitStatusDoesNotChangeIndexOrWorktree() throws Exception {
  var init=new ProcessBuilder("git","init",temp.toString()).redirectErrorStream(true).start();assertThat(init.waitFor()).isZero();
  Files.writeString(temp.resolve("hello.txt"),"untouched");
  var git=new InventoryGit();var result=git.inspect(temp);
  assertThat(result.workingTree()).isEqualTo("Modifications locales");assertThat(result.remotes().getFirst().state()).isEqualTo(RemoteState.NO_REMOTE);
  assertThat(Files.exists(temp.resolve(".git/index"))).isFalse();
  var add=new ProcessBuilder("git","-C",temp.toString(),"add","hello.txt").start();assertThat(add.waitFor()).isZero();
  var index=temp.resolve(".git/index");byte[] before=Files.readAllBytes(index);var date=Files.getLastModifiedTime(index);
  git.inspect(temp);assertThat(Files.readAllBytes(index)).isEqualTo(before);assertThat(Files.getLastModifiedTime(index)).isEqualTo(date);
  assertThat(Files.readString(temp.resolve("hello.txt"))).isEqualTo("untouched");
 }
 @Test void realRemoteAdvertisementReports404WithoutFetchingObjects() throws Exception {
  var init=new ProcessBuilder("git","init",temp.toString()).redirectErrorStream(true).start();assertThat(init.waitFor()).isZero();
  var server=com.sun.net.httpserver.HttpServer.create(new java.net.InetSocketAddress("127.0.0.1",0),0);
  server.createContext("/",e->{e.sendResponseHeaders(404,-1);e.close();});server.start();
  try {
   String url="http://127.0.0.1:"+server.getAddress().getPort()+"/missing.git";
   var config=new ProcessBuilder("git","-C",temp.toString(),"remote","add","origin",url).start();assertThat(config.waitFor()).isZero();
   byte[] before=Files.readAllBytes(temp.resolve(".git/config"));
   var result=new InventoryGit().inspect(temp);assertThat(result.remotes().getFirst().state()).isEqualTo(RemoteState.NOT_FOUND_OR_PRIVATE);
   assertThat(Files.readAllBytes(temp.resolve(".git/config"))).isEqualTo(before);assertThat(Files.exists(temp.resolve(".git/FETCH_HEAD"))).isFalse();
  } finally {server.stop(0);}
 }
}
