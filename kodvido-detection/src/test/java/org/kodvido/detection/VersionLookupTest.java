package org.kodvido.detection;
import org.junit.jupiter.api.Test;
import org.kodvido.domain.Inventory.Dependency;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
class VersionLookupTest {
 Dependency d(String group,String artifact,String version) {return new Dependency(group,artifact,version,"pom.xml","","NOT_CHECKED");}
 @Test void publicMetadataResultsAreNotMisrepresentedAsSecurityOrSupportVerdicts() {
  var urls=new ArrayList<String>();var lookup=new VersionLookup(uri->{urls.add(uri.toString());return (uri.getHost().contains("npm")?"{\"version\":\"2.0\"}":"<metadata><versioning><release>2.0</release></versioning></metadata>").getBytes();});
  var result=lookup.check(List.of(d("npm","@angular/core","^1.0"),d("npm","bootstrap","2.0"),d("org.example","a","2.0"),d("${private}","a","1")));
  assertThat(result).extracting(Dependency::result).containsExactly("COMPARE_RELEASE","MATCHES_RELEASE","MATCHES_RELEASE","UNRESOLVED");
  assertThat(urls).hasSize(3);assertThat(urls.getFirst()).contains("@angular%2Fcore");
  assertThat(new VersionLookup(uri->{throw new java.io.IOException();}).check(List.of(d("org.example","a","1"))).getFirst().result()).isEqualTo("UNAVAILABLE");
  assertThat(new VersionLookup(uri->"<metadata/>".getBytes()).check(List.of(d("org.example","a","1"))).getFirst().result()).isEqualTo("UNKNOWN");
  assertThat(new VersionLookup(uri->{throw new InterruptedException();}).check(List.of(d("org.example","a","1"))).getFirst().result()).isEqualTo("CANCELLED");Thread.interrupted();
  assertThat(lookup.check(Collections.nCopies(51,d("org.example","a","1"))).getLast().result()).isEqualTo("LIMIT_REACHED");
 }
 @Test void realHttpClientBoundsBodiesAndRejectsHttpErrors() throws Exception {
  var server=com.sun.net.httpserver.HttpServer.create(new java.net.InetSocketAddress("127.0.0.1",0),0);
  server.createContext("/ok",e->{byte[] b="metadata".getBytes();e.sendResponseHeaders(200,b.length);try(var out=e.getResponseBody()){out.write(b);}});
  server.createContext("/large",e->{byte[] b=new byte[262145];e.sendResponseHeaders(200,b.length);try(var out=e.getResponseBody()){out.write(b);}});
  server.start();String base="http://127.0.0.1:"+server.getAddress().getPort();
  try {
   assertThat(new String(VersionLookup.fetch(java.net.URI.create(base+"/ok")))).isEqualTo("metadata");
   assertThatThrownBy(()->VersionLookup.fetch(java.net.URI.create(base+"/missing"))).isInstanceOf(java.io.IOException.class);
   assertThatThrownBy(()->VersionLookup.fetch(java.net.URI.create(base+"/large"))).isInstanceOf(java.io.IOException.class);
  } finally {server.stop(0);}
  var failed=new VersionLookup.LimitedBody();failed.onError(new java.io.IOException());assertThat(failed.getBody().toCompletableFuture()).isCompletedExceptionally();
 }
}
