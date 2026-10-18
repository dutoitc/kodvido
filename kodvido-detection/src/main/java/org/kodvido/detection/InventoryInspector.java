// SPDX-License-Identifier: GPL-3.0-only
package org.kodvido.detection;
import org.kodvido.domain.Inventory.*;
import com.fasterxml.jackson.databind.*;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.*;
import java.util.*;
import java.io.*;
/** Bounded manifest/source inventory. No project commands or build lifecycle execution. */
public final class InventoryInspector implements Inspector {
    private static final Set<String> EXCLUDED=Set.of(".git","target","build","dist","node_modules","vendor",".venv","venv",".idea","__pycache__");
    private static final Set<String> SOURCE=Set.of("java","py","ts","tsx","js","jsx","html","css","scss","sql","kt","xml","yml","yaml");
    private final ObjectMapper json=new ObjectMapper();
    private final InventoryGit git;
    private final VersionLookup versions;
    public InventoryInspector() {this(new InventoryGit(),new VersionLookup());}
    InventoryInspector(InventoryGit git,VersionLookup versions) {this.git=git;this.versions=versions;}
    public Snapshot inspect(Path input,boolean checkVersions) {
        var technologies=new LinkedHashSet<Technology>();var dependencies=new ArrayList<Dependency>();var diagnostics=new TreeSet<String>();
        long[] lines={0};String status="PARTIAL";String committed=null;InventoryGit.Evidence remote=new InventoryGit.Evidence("Non vérifié",List.of());
        try {
            Path root=SafePaths.directory(input);long started=System.nanoTime();int[] entries={0},manifests={0};
            Files.walkFileTree(root,EnumSet.noneOf(FileVisitOption.class),24,new SimpleFileVisitor<Path>() {
                boolean stop() {return ++entries[0]>50000 || System.nanoTime()-started>Duration.ofSeconds(15).toNanos() || Thread.currentThread().isInterrupted();}
                FileVisitResult limited() {diagnostics.add("SCAN_LIMIT_REACHED");return FileVisitResult.TERMINATE;}
                @Override public FileVisitResult preVisitDirectory(Path dir,BasicFileAttributes a) throws IOException {
                    if(stop()) return limited();
                    if(!dir.equals(root) && (EXCLUDED.contains(dir.getFileName().toString().toLowerCase(Locale.ROOT)) || Files.exists(dir.resolve(".git"),LinkOption.NOFOLLOW_LINKS))) return FileVisitResult.SKIP_SUBTREE;
                    try {SafePaths.directory(dir);} catch(IOException e) {diagnostics.add("LINK_OR_DIRECTORY_SKIPPED");return FileVisitResult.SKIP_SUBTREE;}
                    return FileVisitResult.CONTINUE;
                }
                @Override public FileVisitResult visitFile(Path file,BasicFileAttributes a) {
                    if(stop()) return limited();
                    if(!a.isRegularFile() || a.isSymbolicLink() || a.isOther()) {diagnostics.add("LINK_OR_DEPTH_LIMIT");return FileVisitResult.CONTINUE;}
                    String name=file.getFileName().toString();String relative=root.relativize(file).toString().replace('\\','/');
                    try {
                        if(Set.of("pom.xml","package.json","pyproject.toml",".python-version").contains(name)) {
                            if(++manifests[0]>200) {diagnostics.add("MANIFEST_LIMIT_REACHED");return FileVisitResult.CONTINUE;}
                            if(name.equals("package.json")) npm(read(file),relative,technologies,dependencies);
                            else {
                                var stack=new StackInspector(Clock.systemUTC()).inspect(file.getParent());
                                for(var e:stack.evidence()) technologies.add(new Technology(e.technology(),e.declaration(),relative));
                                diagnostics.addAll(stack.diagnostics());
                                if(name.equals("pom.xml")) maven(read(file),relative,dependencies);
                            }
                        }
                        String extension=name.contains(".")?name.substring(name.lastIndexOf('.')+1):"";
                        if(SOURCE.contains(extension)) {
                            if(a.size()>262144) diagnostics.add("LARGE_SOURCE_EXCLUDED");
                            else {String text=new String(read(file),java.nio.charset.StandardCharsets.UTF_8);if(text.indexOf(0)<0) lines[0]+=text.lines().count();}
                        }
                    } catch(Exception e) {diagnostics.add("UNREADABLE_OR_UNSUPPORTED_FILE");}
                    return FileVisitResult.CONTINUE;
                }
                @Override public FileVisitResult visitFileFailed(Path file,IOException e) {diagnostics.add("UNREADABLE_ENTRY");return FileVisitResult.CONTINUE;}
            });
            remote=git.inspect(root);committed=Optional.ofNullable(new GitInspector(Clock.systemUTC()).inspect(root).committedAt()).map(Instant::toString).orElse(null);
            if(checkVersions) {var checked=versions.check(dependencies);dependencies.clear();dependencies.addAll(checked);}
        } catch(NoSuchFileException e) {status="MISSING";diagnostics.add("DIRECTORY_MISSING");}
        catch(Exception e) {status="FAILED";diagnostics.add("DIRECTORY_UNREADABLE");}
        return new Snapshot(Instant.now().toString(),status,lines[0],committed,remote.workingTree(),new ArrayList<>(technologies),dependencies,remote.remotes(),new ArrayList<>(diagnostics));
    }
    static byte[] read(Path file) throws IOException {
        try(var in=Files.newInputStream(file,LinkOption.NOFOLLOW_LINKS)) {byte[] data=in.readNBytes(262145);if(data.length>262144) throw new IOException();return data;}
    }
    private void npm(byte[] bytes,String source,Set<Technology> tech,List<Dependency> deps) throws IOException {
        JsonNode root=json.readTree(bytes);
        for(String section:List.of("dependencies","devDependencies")) {
            JsonNode values=root.path(section);
            for(String name:List.of("@angular/core","bootstrap")) {
                var value=values.path(name);
                if(value.isTextual() && value.asText().length()<256) {String version=value.asText();tech.add(new Technology(name.equals("bootstrap")?"Bootstrap":"Angular",version,source));deps.add(new Dependency("npm",name,version,source,"","NOT_CHECKED"));}
            }
        }
    }
    static org.w3c.dom.Element xml(byte[] data) throws Exception {
        var f=javax.xml.parsers.DocumentBuilderFactory.newInstance();
        f.setFeature("http://apache.org/xml/features/disallow-doctype-decl",true);f.setAttribute(javax.xml.XMLConstants.ACCESS_EXTERNAL_DTD,"");f.setAttribute(javax.xml.XMLConstants.ACCESS_EXTERNAL_SCHEMA,"");
        f.setAttribute("http://www.oracle.com/xml/jaxp/properties/maxElementDepth",64);f.setExpandEntityReferences(false);f.setXIncludeAware(false);
        var b=f.newDocumentBuilder();b.setErrorHandler(new org.xml.sax.helpers.DefaultHandler());return b.parse(new ByteArrayInputStream(data)).getDocumentElement();
    }
    private static String child(org.w3c.dom.Element parent,String name) {
        for(var n=parent.getFirstChild();n!=null;n=n.getNextSibling()) if(n instanceof org.w3c.dom.Element e && e.getTagName().equals(name)) return e.getTextContent().strip();return "";
    }
    private static void maven(byte[] bytes,String source,List<Dependency> deps) throws Exception {
        var root=xml(bytes);if(!root.getTagName().equals("project")) throw new IOException();
        var nodes=root.getElementsByTagName("dependency");
        for(int i=0;i<Math.min(nodes.getLength(),500) && deps.size()<1000;i++) {
            var d=(org.w3c.dom.Element)nodes.item(i);
            // Explicit declarations, including dependencyManagement. No effective model claim.
            deps.add(new Dependency(child(d,"groupId"),child(d,"artifactId"),child(d,"version"),source,"","NOT_CHECKED"));
        }
    }
}
