// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 Kodvido contributors
package org.kodvido.domain;

import java.time.Instant;

/** Commit identity and committer timestamp, never author identity, message or remote URL. */
public record GitObservation(ProjectInspection.Status status, String commitId, Instant committedAt,
        Instant observedAt, String detectorVersion, String diagnostic) {}
