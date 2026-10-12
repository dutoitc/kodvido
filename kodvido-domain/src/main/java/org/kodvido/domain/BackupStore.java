// SPDX-License-Identifier: GPL-3.0-only
package org.kodvido.domain;
import java.util.List;
public interface BackupStore {
    /** One consistent database snapshot. */
    List<CatalogBackup.Entry> snapshot();
    /** Atomic insert only: conflicts must roll back every entry and note. */
    void restore(List<CatalogBackup.Entry> entries);
}
