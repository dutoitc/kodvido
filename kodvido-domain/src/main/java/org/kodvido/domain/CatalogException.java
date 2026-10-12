package org.kodvido.domain;
/** Deliberately contains no SQL, credentials or filesystem paths. */
public final class CatalogException extends RuntimeException {
    public CatalogException(String sqlState, int vendorCode) {
        super("Catalogue indisponible [" + safeState(sqlState) + "/" + vendorCode + "].");
    }
    private static String safeState(String state) {
        return state != null && state.matches("[A-Z0-9]{5}") ? state : "UNKNOWN";
    }
    public CatalogException() { super("Catalogue indisponible ou chemin déjà enregistré."); }
}
