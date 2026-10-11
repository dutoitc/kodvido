package org.kodvido.domain;
/** Deliberately contains no SQL, credentials or filesystem paths. */
public final class CatalogException extends RuntimeException {
    public CatalogException() { super("Catalogue indisponible ou chemin déjà enregistré."); }
}
