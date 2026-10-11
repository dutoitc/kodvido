package org.kodvido.engine;
import org.kodvido.detection.ProjectDirectoryScanner;
import org.kodvido.domain.DetectedProject;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
public final class DiscoveryService {
 private final ProjectDirectoryScanner scanner;
 public DiscoveryService(ProjectDirectoryScanner scanner) { this.scanner = scanner; }
 public List<DetectedProject> preview(Path scanRoot) throws IOException { return scanner.discover(scanRoot); }
}
