package org.kodvido.domain;
import java.nio.file.Path;
import java.time.Instant;
import java.util.UUID;
public record CatalogProject(UUID id, String name, Path path, Instant createdAt, Instant updatedAt) {}
