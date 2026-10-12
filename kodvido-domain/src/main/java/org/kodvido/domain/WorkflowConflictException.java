// SPDX-License-Identifier: GPL-3.0-only
package org.kodvido.domain;
public final class WorkflowConflictException extends RuntimeException {
    public WorkflowConflictException() { super("Cette fiche a changé. Rechargez-la avant de ressaisir vos modifications."); }
}
