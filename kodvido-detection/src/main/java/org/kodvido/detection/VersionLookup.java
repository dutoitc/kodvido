// SPDX-License-Identifier: GPL-3.0-only
package org.kodvido.detection;
import org.kodvido.domain.Inventory.Dependency;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.*;
import java.net.http.*;
import java.time.*;
import java.util.*;
/** Optional public-registry lookup. Coordinates only, no source/configuration upload. */
public final class VersionLookup {
    @FunctionalInterface interface Fetcher { byte[] get(URI uri) throws Exception; }
    private final Fetcher fetcher;
    public VersionLookup() {this(VersionLookup::fetch);}
    VersionLookup(Fetcher fetcher) {this.fetcher=fetcher;}
    public List<Dependency> check(List<Dependency> dependencies) {
        var result=new ArrayList<Dependency>();long start=System.nanoTime();int queries=0;
        for(var d:dependencies) {
            String available="",state="UNRESOLVED";
            if(queries>=50 || System.nanoTime()-start>Duration.ofSeconds(30).toNanos()) state="LIMIT_REACHED";
            else try {
                URI uri;
                if(d.group().equals("npm") && Set.of("@angular/core","bootstrap").contains(d.artifact())) uri=URI.create("https://registry.npmjs.org/"+d.artifact().replace("/","%2F")+"/latest");
                else if(d.group().matches("[a-zA-Z0-9_.-]+") && d.artifact().matches("[a-zA-Z0-9_.-]+")) uri=URI.create("https://repo.maven.apache.org/maven2/"+d.group().replace('.','/')+"/"+d.artifact()+"/maven-metadata.xml");
                else {result.add(new Dependency(d.group(),d.artifact(),d.version(),d.source(),"",state));continue;}
                queries++;byte[] data=fetcher.get(uri);
                if(d.group().equals("npm")) available=new ObjectMapper().readTree(data).path("version").asText();
                else {var releases=InventoryInspector.xml(data).getElementsByTagName("release");if(releases.getLength()>0) available=releases.item(0).getTextContent().strip();}
                state=available.isBlank()?"UNKNOWN":(d.version().equals(available)?"MATCHES_RELEASE":"COMPARE_RELEASE");
            } catch(InterruptedException e) {Thread.currentThread().interrupt();state="CANCELLED";}
            catch(Exception e) {state="UNAVAILABLE";}
            result.add(new Dependency(d.group(),d.artifact(),d.version(),d.source(),available,state,Instant.now().toString()));
        }
        return List.copyOf(result);
    }
    static byte[] fetch(URI uri) throws Exception {
        try(var client=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).followRedirects(HttpClient.Redirect.NEVER).build()) {
            var response=client.send(HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(5)).GET().build(),info->new LimitedBody());
            if(response.statusCode()!=200) throw new java.io.IOException();return response.body();
        }
    }
    /** Bound the body while it arrives, so the request deadline covers body consumption too. */
    static final class LimitedBody implements HttpResponse.BodySubscriber<byte[]> {
        private final java.util.concurrent.CompletableFuture<byte[]> result=new java.util.concurrent.CompletableFuture<>();
        private final java.io.ByteArrayOutputStream bytes=new java.io.ByteArrayOutputStream();
        private java.util.concurrent.Flow.Subscription subscription;
        public java.util.concurrent.CompletionStage<byte[]> getBody() {return result;}
        public void onSubscribe(java.util.concurrent.Flow.Subscription s) {subscription=s;s.request(1);}
        public void onNext(List<java.nio.ByteBuffer> items) {
            for(var buffer:items) {
                if(bytes.size()+buffer.remaining()>262144) {subscription.cancel();result.completeExceptionally(new java.io.IOException("Metadata size limit"));return;}
                byte[] chunk=new byte[buffer.remaining()];buffer.get(chunk);bytes.writeBytes(chunk);
            }subscription.request(1);
        }
        public void onError(Throwable error) {result.completeExceptionally(error);}
        public void onComplete() {result.complete(bytes.toByteArray());}
    }
}
