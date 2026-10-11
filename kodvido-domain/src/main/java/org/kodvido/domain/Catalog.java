package org.kodvido.domain;
import java.util.*;
public interface Catalog {
    List<CatalogProject> list();
    void addAll(List<CatalogProject> projects);
    void relocate(CatalogProject project);
    void remove(UUID id);
}
