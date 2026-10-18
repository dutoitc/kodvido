package org.kodvido.detection;

import org.kodvido.domain.*;
import java.io.*;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.*;
import java.util.*;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.Element;
import org.xml.sax.helpers.DefaultHandler;

/** Bounded, read-only discovery. Neither scripts nor Maven models are executed. */
public final class ProjectDirectoryScanner {
    private static final Set<String> IGNORED = Set.of(".git", "target", "build", ".venv", "venv", "node_modules", "dist", ".idea", "__pycache__", "vendor");
    private static final Set<String> INDICATORS = Set.of("pom.xml", "pyproject.toml", "requirements.txt", "setup.py", "package.json");
    private final int maxDepth, maxEntries;
    private final Duration timeout;
    public ProjectDirectoryScanner() { this(32, 100_000, Duration.ofSeconds(30)); }
    public ProjectDirectoryScanner(int maxDepth, int maxEntries, Duration timeout) {
        this.maxDepth = maxDepth; this.maxEntries = maxEntries; this.timeout = timeout;
    }
    public List<DetectedProject> discover(Path root) throws IOException {
        SafePaths.directory(root);
        return scan(List.of(root)).candidates();
    }
    public ScanReport scan(List<Path> roots) {
        Map<Path, Set<String>> found = new TreeMap<>();
        List<String> diagnostics = new ArrayList<>();
        long started = System.nanoTime();
        int[] entries = {0};
        for (int i = 0; i < roots.size(); i++) {
            final String label = "Racine " + (i + 1);
            try {
                Path root = SafePaths.directory(roots.get(i));
                Files.walkFileTree(root, EnumSet.noneOf(FileVisitOption.class), maxDepth, new SimpleFileVisitor<Path>() {
                    private boolean stopped() {
                        return ++entries[0] > maxEntries || System.nanoTime() - started >= timeout.toNanos() || Thread.currentThread().isInterrupted();
                    }
                    private FileVisitResult limit() {
                        diagnostics.add(label + " : scan partiel (limite ou annulation)."); return FileVisitResult.TERMINATE;
                    }
                    @Override public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                        if (stopped()) return limit();
                        if (!dir.equals(root) && IGNORED.contains(dir.getFileName().toString().toLowerCase(Locale.ROOT))) return FileVisitResult.SKIP_SUBTREE;
                        try { SafePaths.directory(dir); }
                        catch (IOException ex) { diagnostics.add(label + " : lien ou dossier inaccessible ignoré."); return FileVisitResult.SKIP_SUBTREE; }
                        if (Files.isDirectory(dir.resolve(".git"),LinkOption.NOFOLLOW_LINKS))
                            found.computeIfAbsent(dir, ignored -> new TreeSet<>()).add(".git");
                        return FileVisitResult.CONTINUE;
                    }
                    @Override public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                        if (stopped()) return limit();
                        if (attrs.isDirectory()) diagnostics.add(label + " : profondeur maximale atteinte.");
                        String name = file.getFileName().toString();
                        if (INDICATORS.contains(name) && attrs.isRegularFile())
                            found.computeIfAbsent(file.getParent(), ignored -> new TreeSet<>()).add(name);
                        return FileVisitResult.CONTINUE;
                    }
                    @Override public FileVisitResult visitFileFailed(Path file, IOException exc) {
                        diagnostics.add(label + " : entrée inaccessible ignorée."); return FileVisitResult.CONTINUE;
                    }
                    @Override public FileVisitResult postVisitDirectory(Path dir, IOException exc) {
                        if (exc != null) diagnostics.add(label + " : lecture du dossier incomplète.");
                        return FileVisitResult.CONTINUE;
                    }
                });
            } catch (NoSuchFileException ex) { diagnostics.add(label + " : absente (MISSING)."); }
            catch (NotDirectoryException ex) { diagnostics.add(label + " : chemin non répertoire (UNSUPPORTED)."); }
            catch (IOException | SecurityException ex) { diagnostics.add(label + " : inaccessible ou liée (FAILED)."); }
        }
        Map<Path, Path> parents = new HashMap<>();
        Map<Path, List<String>> notes = new HashMap<>();
        for (var entry : found.entrySet()) {
            Path dir = entry.getKey();
            if (!entry.getValue().contains("pom.xml")) continue;
            if (System.nanoTime() - started >= timeout.toNanos() || Thread.currentThread().isInterrupted()) {
                notes.computeIfAbsent(dir, k -> new ArrayList<>()).add("Analyse Maven interrompue ou délai dépassé.");
                continue;
            }
            try {
                SafePaths.directory(dir);
                Path pom = dir.resolve("pom.xml");
                // NOFOLLOW protects a replaced marker; bounded bytes protect memory use.
                try (var in = Files.newInputStream(pom, LinkOption.NOFOLLOW_LINKS)) {
                    byte[] xml = in.readNBytes(1_048_577);
                    if (xml.length > 1_048_576) throw new IOException("POM too large");
                    var factory = DocumentBuilderFactory.newInstance();
                    factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
                    factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
                    factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
                    factory.setXIncludeAware(false); factory.setExpandEntityReferences(false);
                    var builder = factory.newDocumentBuilder(); builder.setErrorHandler(new DefaultHandler());
                    var document = builder.parse(new ByteArrayInputStream(xml));
                    if (!document.getDocumentElement().getTagName().equals("project")) throw new IOException("Not a Maven project");
                    var modules = document.getElementsByTagName("module");
                    for (int j = 0; j < modules.getLength(); j++) {
                        Element module = (Element) modules.item(j);
                        if (!module.getParentNode().getNodeName().equals("modules") || module.getParentNode().getParentNode() != document.getDocumentElement()) {
                            notes.computeIfAbsent(dir, k -> new ArrayList<>()).add("Module conditionnel ou non standard ; validation manuelle nécessaire.");
                            continue;
                        }
                        String value = module.getTextContent().trim().replace('\\', '/');
                        Path child = dir.resolve(value).normalize();
                        // Only already-discovered descendants can be grouped. Never open an external module.
                        if (!child.equals(dir) && child.startsWith(dir) && found.containsKey(child) && found.get(child).contains("pom.xml")) {
                            parents.merge(child, dir, (old, next) -> old.getNameCount() > next.getNameCount() ? old : next);
                        } else notes.computeIfAbsent(dir, k -> new ArrayList<>()).add("Module non résolu, externe ou absent ; aucun accès supplémentaire.");
                    }
                }
            } catch (Exception ex) { notes.computeIfAbsent(dir, k -> new ArrayList<>()).add("POM illisible, trop volumineux ou XML non pris en charge."); }
        }
        // A Git repository is the dashboard unit; preserve nested repositories as separate projects.
        for (var entry : found.entrySet()) {
            if (entry.getValue().contains(".git")) { parents.remove(entry.getKey());continue; }
            Path ancestor=entry.getKey().getParent();
            while(ancestor!=null) {
                if(found.containsKey(ancestor) && found.get(ancestor).contains(".git")) {parents.put(entry.getKey(),ancestor);break;}
                ancestor=ancestor.getParent();
            }
        }
        Instant observedAt = Instant.now();
        var candidates = found.entrySet().stream().map(e -> new DetectedProject(e.getKey(), e.getValue(), parents.get(e.getKey()),
            notes.containsKey(e.getKey()) ? DetectionOutcome.PARTIAL : DetectionOutcome.SUPPORTED,
            notes.getOrDefault(e.getKey(), List.of()), observedAt, "filesystem-1")).toList();
        return new ScanReport(candidates, diagnostics.stream().distinct().toList());
    }
}
