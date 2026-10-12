// SPDX-License-Identifier: GPL-3.0-only
package org.kodvido.detection;

import org.kodvido.domain.StackObservation;
import org.kodvido.domain.StackObservation.Evidence;
import org.kodvido.domain.ProjectInspection.Status;
import org.w3c.dom.*;
import org.xml.sax.*;
import org.xml.sax.helpers.DefaultHandler;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.*;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.*;
import java.util.*;
import java.util.regex.Pattern;

/** Root declarations and shallow script filenames only; no processes, imports or network. */
public final class StackInspector {
    private static final int MAX_BYTES=262144;
    private static final Set<String> JAVA_KEYS=Set.of("java.version","maven.compiler.release","maven.compiler.source","maven.compiler.target");
    private static final Pattern PYTHON=Pattern.compile("requires-python\\s*=\\s*([\"'])([^\"'\\\\]{1,256})\\1\\s*(?:#.*)?");
    private final Clock clock;
    private final int maxEntries;
    private final Duration timeout;
    public StackInspector(Clock clock) { this(clock,200,Duration.ofSeconds(5)); }
    public StackInspector(Clock clock,int maxEntries,Duration timeout) { this.clock=clock;this.maxEntries=maxEntries;this.timeout=timeout; }

    public StackObservation inspect(Path input) {
        var evidence=new ArrayList<Evidence>(); var scripts=new TreeSet<String>(); var notes=new TreeSet<String>();
        Instant now=clock.instant(); long start=System.nanoTime(); Status status;
        try {
            Path root=SafePaths.directory(input);
            inspectFile(root,"pom.xml",evidence,notes);
            inspectFile(root,"pyproject.toml",evidence,notes);
            inspectFile(root,".python-version",evidence,notes);
            int[] entries={0};
            scripts(root,root,scripts,notes,entries,start);
            if(Files.exists(root.resolve("scripts"),LinkOption.NOFOLLOW_LINKS))
                scripts(root,SafePaths.directory(root.resolve("scripts")),scripts,notes,entries,start);
            status=notes.isEmpty() ? Status.UNKNOWN : Status.PARTIAL;
        } catch(NoSuchFileException ex) { status=Status.MISSING; }
        catch(NotDirectoryException ex) { status=Status.UNSUPPORTED; }
        catch(IOException | SecurityException | DirectoryIteratorException ex) { status=Status.FAILED; notes.add("DIRECTORY_UNREADABLE_OR_LINKED"); }
        // Even a clean bounded inspection cannot establish the effective runtime or all scripts.
        if(status==Status.UNKNOWN && (!evidence.isEmpty() || !scripts.isEmpty())) status=Status.PARTIAL;
        return new StackObservation(now,"stack-declarations-1",status,evidence,new ArrayList<>(scripts),new ArrayList<>(notes));
    }
    private void inspectFile(Path root,String name,List<Evidence> evidence,Set<String> notes) {
        try {
            Path file=root.resolve(name);
            var a=Files.readAttributes(file,BasicFileAttributes.class,LinkOption.NOFOLLOW_LINKS);
            if(!a.isRegularFile() || a.isSymbolicLink() || a.isOther() || a.size()>MAX_BYTES) {
                notes.add(name+":UNSUPPORTED_TYPE_OR_SIZE");return;
            }
            byte[] bytes;
            try(var in=Files.newInputStream(file,LinkOption.NOFOLLOW_LINKS)) { bytes=in.readNBytes(MAX_BYTES+1); }
            if(bytes.length>MAX_BYTES) { notes.add(name+":SIZE_LIMIT");return; }
            var found=new ArrayList<Evidence>();
            switch(name) {
                case "pom.xml" -> maven(bytes,found);
                case "pyproject.toml" -> python(StandardCharsets.UTF_8.newDecoder().decode(ByteBuffer.wrap(bytes)).toString(),found);
                default -> {
                    String version=StandardCharsets.UTF_8.newDecoder().decode(ByteBuffer.wrap(bytes)).toString().strip();
                    if(!version.matches("[0-9]+(?:\\.[0-9]+){0,2}(?:[a-zA-Z0-9.-]*)") || version.length()>64) throw new IOException();
                    found.add(new Evidence("Python",name,version));
                }
            }
            evidence.addAll(found); notes.add(name+":DECLARATIONS_ONLY");
            if(found.isEmpty()) notes.add(name+":VERSION_UNKNOWN");
        } catch(NoSuchFileException ex) { /* Optional manifest absent; not a failed inspection. */ }
        catch(Exception ex) { notes.add(name+":UNREADABLE_OR_UNSUPPORTED_SYNTAX"); }
    }
    private static void maven(byte[] bytes,List<Evidence> found) throws Exception {
        var factory=DocumentBuilderFactory.newInstance();factory.setNamespaceAware(true);
        factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING,true);
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl",true);
        factory.setFeature("http://xml.org/sax/features/external-general-entities",false);
        factory.setFeature("http://xml.org/sax/features/external-parameter-entities",false);
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD,"");factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA,"");
        factory.setAttribute("http://www.oracle.com/xml/jaxp/properties/maxElementDepth",64);
        factory.setXIncludeAware(false);factory.setExpandEntityReferences(false);
        var builder=factory.newDocumentBuilder();
        builder.setErrorHandler(new DefaultHandler() {
            @Override public void error(SAXParseException ex) throws SAXException { throw ex; }
            @Override public void fatalError(SAXParseException ex) throws SAXException { throw ex; }
        });
        Element project=builder.parse(new ByteArrayInputStream(bytes)).getDocumentElement();
        if(!"project".equals(project.getLocalName()) || !(project.getNamespaceURI()==null || "http://maven.apache.org/POM/4.0.0".equals(project.getNamespaceURI()))) throw new IOException();
        for(var props:children(project,"properties")) for(String key:new TreeSet<>(JAVA_KEYS))
            for(var value:children(props,key)) addJava(found,"pom.xml / properties / "+key,value);
        for(var build:children(project,"build")) for(var plugins:children(build,"plugins")) for(var plugin:children(plugins,"plugin")) {
            var artifact=children(plugin,"artifactId");var group=children(plugin,"groupId");
            if(artifact.size()!=1 || !"maven-compiler-plugin".equals(artifact.getFirst().getTextContent().strip())) continue;
            if(!group.isEmpty() && !"org.apache.maven.plugins".equals(group.getFirst().getTextContent().strip())) continue;
            for(var config:children(plugin,"configuration")) for(String key:List.of("release","source","target"))
                for(var value:children(config,key)) addJava(found,"pom.xml / maven-compiler-plugin / "+key,value);
        }
    }
    private static List<Element> children(Element parent,String name) {
        var result=new ArrayList<Element>();
        for(Node n=parent.getFirstChild();n!=null;n=n.getNextSibling())
            if(n instanceof Element e && name.equals(e.getLocalName()) && Objects.equals(parent.getNamespaceURI(),e.getNamespaceURI())) result.add(e);
        return result;
    }
    private static void addJava(List<Evidence> found,String source,Element value) throws IOException {
        String text=value.getTextContent().strip();
        if(text.isEmpty() || text.length()>256 || value.getElementsByTagName("*").getLength()!=0) throw new IOException();
        found.add(new Evidence("Java",source,text));
    }
    /** Deliberate lexical subset, not a TOML parser or evaluation of packaging configuration. */
    private static void python(String text,List<Evidence> found) throws IOException {
        if(text.contains("\"\"\"") || text.contains(String.valueOf((char)39).repeat(3))) throw new IOException();
        boolean project=false,seen=false;
        for(String raw:text.lines().toList()) {
            String line=raw.strip();
            if(line.startsWith("[")) {
                project=line.matches("\\[project]\\s*(?:#.*)?");
                if(project && seen) throw new IOException();
                seen |= project;
            } else if(project && line.startsWith("requires-python")) {
                var match=PYTHON.matcher(line);
                if(!match.matches() || !found.isEmpty()) throw new IOException();
                found.add(new Evidence("Python","pyproject.toml / project.requires-python",match.group(2)));
            }
        }
    }
    private void scripts(Path root,Path directory,Set<String> scripts,Set<String> notes,int[] entries,long start) throws IOException {
        try(var stream=Files.newDirectoryStream(directory)) {
            for(Path path:stream) {
                if(++entries[0]>maxEntries || System.nanoTime()-start>=timeout.toNanos() || Thread.currentThread().isInterrupted()) { notes.add("SCRIPT_LIMIT_REACHED");return; }
                String name=path.getFileName().toString().toLowerCase(Locale.ROOT);
                if(!(name.matches(".*\\.(sh|bat|cmd|ps1|py)") || name.equals("mvnw"))) continue;
                try {
                    var a=Files.readAttributes(path,BasicFileAttributes.class,LinkOption.NOFOLLOW_LINKS);
                    if(a.isRegularFile() && !a.isSymbolicLink() && !a.isOther()) scripts.add(root.relativize(path).toString().replace('\\','/'));
                    else notes.add("SCRIPT_LINK_OR_SPECIAL_FILE_SKIPPED");
                } catch(IOException | SecurityException ex) { notes.add("SCRIPT_UNREADABLE"); }
            }
        }
    }
}
