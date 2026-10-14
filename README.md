# Kodvido

A local Java 21 application for discovering and cataloguing software projects.
The first M1 slice is implemented and verified by GitHub CI on Ubuntu and Windows;
see [verification](docs/VERIFICATION.md). The first M2 slice adds a read-only project
cockpit; M2 is still in progress and M3 has not started. Licensed under **GPL-3.0-only**; see [LICENSE](LICENSE) and [license decision](LICENSE-CHOICE.md).

## Build and run

Install Java **21** and Maven **3.9+**, then run from the repository root:

```sh
mvn -B --no-transfer-progress clean verify
java -jar kodvido-app/target/kodvido-app-0.1.0-SNAPSHOT.jar
```

Open **http://127.0.0.1:8080**. These commands are verified on Windows and Linux.
The app is local-only and must not be exposed through a reverse proxy or to a LAN.

The executable JAR in the delivery archive only requires Java 21, not Maven.
The H2 database is created outside the repository, under `~/.kodvido/data`.
Set `KODVIDO_DATA_DIR` to an **absolute directory** before starting to use a
separate home/work catalogue. Stop the app before copying its database files.
No telemetry, remote calls or discovered scripts are run. Explicit inspection
can run one fixed, read-only command using the Git installed in the launch environment.

## Use

1. Enter one absolute scan root per line, for example `~/projects` on Linux or
   `D:\projects` on Windows. Up to 20 roots can be scanned together.
2. Review candidates and diagnostics. Maven and Python are detected using
   `pom.xml`, `pyproject.toml`, `requirements.txt`, and `setup.py`.
3. Select projects and confirm import. Maven children declared in their parent's
   POM are unchecked by default; select a child explicitly to manage it separately.
4. Use **Relocaliser** after moving a project yourself. Validate the destination;
   Kodvido changes only the catalogue mapping and preserves the project UUID.
5. Open a project name, then **Observer les fichiers** to inspect file activity
   and find project-owned documents. Open a document to read its live, escaped
   UTF-8 text (256 KiB maximum); no content is copied into H2.
6. Use **Retirer du catalogue** to remove an entry. Source files are never deleted.

Scans never import automatically. Review expires after 30 minutes or a newer scan.
Existing paths are not imported twice. Multi-project import is transactional.
Root lists and detection previews are transient in M1; project identity/path/name
and timestamps persist. Manual phases and restart notes also persist since M2.3;
Git/file observations and their history remain transient.

## Safety and limitations

- Scans skip generated/vendor directories, links and junctions. Maven XML rejects
  DTDs and external entities; POM reads are capped at 1 MiB. No Maven execution.
- Maximum depth 32, 100,000 visited entries and a 30-second cooperative budget.
  Partial traversal, invalid roots and unreadable entries produce diagnostics.
  Blocking filesystem calls cannot be forcibly interrupted; avoid network mounts.
- Only already discovered descendant Maven modules are grouped. Conditional,
  unresolved and external modules are flagged for manual review, never read by
  following their declarations. Independent nested projects remain independent.
- Native paths only: a Windows drive path is not translated on Linux.
- Scanned trees should remain stable. Concurrent hostile filesystem replacement
  is outside the M1 threat model; no-follow checks cannot remove all OS-level races.
- CSRF, Host validation and HTML escaping protect local web forms.
- There is no file editing, deployment, backup confidence calculation or report
  generation. Unknown backup must never be interpreted as safe backup.

## Architecture and tests

`domain` (immutable data and catalogue port), `detection` (read-only filesystem),
`engine` (use cases), `persistence` (JDBC/H2/Flyway), `reporting` (reserved, empty),
`app` (Spring Boot composition and Thymeleaf). Spring remains in `app` only.

`mvn verify` executes JUnit unit and integration tests with temporary fixtures,
including real H2/Flyway and a Spring web context. Reports are in each module's
`target/site/jacoco/index.html`; Surefire XML is in `target/surefire-reports`.
JaCoCo enforces engine/domain ≥90% branches, detection ≥85% lines and persistence
≥80% lines. Reporting is empty; domain and engine branch gates apply to measured branches.

See [requirements](docs/REQUIREMENTS.md), [decisions](docs/DECISIONS.md),
[roadmap](docs/ROADMAP.md) and [verification](docs/VERIFICATION.md).

## M2.1 project cockpit

File activity uses filesystem modification times, with inclusive UTC 7/30-day
windows and a low-confidence warning. Future timestamps produce partial evidence
and do not count as recent activity. An incomplete scan never proves inactivity.
Observations are transient and explicitly requested via a CSRF-protected form;
opening the project page alone does not recursively scan it.

Exclusions and bounded traversal are shown in the UI. `.gitignore` is not parsed.
The four supported documents are `PROJECT.md`, `TODO.md`, `AGENTS.md`, `README.md`;
arbitrary document paths, binary/non-UTF-8 contents, symlinks and oversized files
are not supported. Missing documents are optional, not project failures.

Interaction history and manual backup evidence are implemented in M2.6/M2.7;
recommendations remain subsequent M2 work. Portable JSON catalogue backup is implemented in M2.4.
See [M2 verification](docs/M2-VERIFICATION.md).

## License

Copyright (C) 2026 Kodvido contributors. Kodvido is free software: you can
redistribute it and/or modify it under the GNU General Public License as published
by the Free Software Foundation, version 3 only. Kodvido is distributed without
any warranty, including merchantability or fitness for a particular purpose.
See [LICENSE](LICENSE) for the complete terms. Dependencies retain their own
licenses. Commercial redistribution is permitted under GPLv3; no royalty or
upstream contribution is required.

## M2.2 local Git evidence

The inspection form also reads the latest commit at the project's own `.git/HEAD`,
including packed repositories and detached HEAD. It reports the commit hash and
committer date, not authors, messages or remote URLs. The timestamp is Git-declared
metadata, not independently verified activity; future timestamps are PARTIAL.
This does not measure uncommitted changes, all branches or remote synchronisation.

Git must be installed in the application's trusted launch environment. Kodvido
uses one fixed read-only `git log` command without a shell, no pager/signature
verification/notes, no global/system Git configuration, and no inherited `GIT_*`
settings. No network operation or project script runs. Repository metadata is
checked for links/junctions before Git starts. Includes, alternates, external
worktrees/submodules, shallow/partial clones and extensions are conservatively
UNSUPPORTED. It never searches parent directories for a repository.

Metadata traversal is bounded at 32 levels / 100,000 entries / 5 seconds; the Git
process has a separate 5-second timeout and 4 KiB output cap. The stable-tree
assumption still applies. Missing `.git` is MISSING; unreadable/corrupt/unborn HEAD
is FAILED (not evidence of no repository). Observations remain transient.

## M2.3 phases and restart worksheets

Open a project and choose **Modifier les phases et fiches de reprise**. Record up
to five important phases (date, description, planned/in-progress/done), plus separate
**development** (build/test) and **operations** (run/diagnose/deploy) instructions.
Descriptions are limited to 500 characters and each restart note to 4,000. Clear a
phase's date and description to remove that row on save. Notes are inert text;
Kodvido does not execute any entered command.

These are user-declared catalogue notes, with a save timestamp and revision,
separate from measured Git/file evidence and the project's own PROJECT.md. No
project file is read into these notes or changed by saving. Do not enter secrets.
Relocation preserves the worksheet; catalogue removal cascades only its metadata.

Flyway V2 migrates existing V1 catalogues without replacing their projects. A save
is transactional. A stale browser tab receives HTTP 409 and its unsaved text is
shown for recovery; reload the current worksheet before submitting a new revision.
There is no phase archive or full task tracker. See [M2.3 verification](docs/M2.3-VERIFICATION.md).

### M2.4 — Local JSON catalogue backup

Open **Sauvegarder ou restaurer le catalogue JSON** from the home page. Download a
JSON snapshot containing catalogue IDs, names, paths, timestamps, phases
development/operations notes and manual backup declarations. Treat this file as private: it contains local
paths and manually entered text, is not encrypted, and must not be committed or
shared publicly. Project files/documents and transient observations are excluded.

To restore, upload the JSON, review the entries/notes, adapt absolute destination
paths for Windows or Linux, then confirm. Missing directories are allowed; no
filesystem access is performed. Identifiers, timestamps and note revisions are
preserved. Any existing ID or identical stored path aborts the entire batch,
without overwriting existing data. Restoring into an empty catalogue is the
simplest complete recovery. A successful restore consumes the preview; previews
expire after 30 minutes or when replaced. Limits: 8 MiB JSON, 1000 projects.
Larger snapshots fail explicitly rather than silently dropping entries.

This backs up catalogue metadata only, not project source, untracked files or
application databases. It does not mark a project's backup as verified. M3 offline
HTML/PDF reports remain unimplemented.

### M2.5 — Stack declarations and potential scripts

Use **Observer les fichiers** on a project page to see Java declarations from root
Maven properties/compiler plugin, Python's simple `[project].requires-python` and
`.python-version`, with source and observation time. Expressions and conflicting
values are displayed as written, not resolved. These are partial declarations,
not the installed or effective runtime. Parent POMs, profiles, child module POMs,
pluginManagement, Poetry and dynamic Python configuration are not evaluated.

Potential script names (`.sh`, `.bat`, `.cmd`, `.ps1`, `.py`, `mvnw`) are listed only
at root and directly inside `scripts/`. Their content, executable permission and
safety are not inspected. No script or language/build runtime is invoked. Bounds:
256 KiB per manifest, XML depth 64, 200 directory entries and cooperative 5-second
script enumeration. Links and special files are excluded. Empty evidence means
UNKNOWN, not absence of a technology. Findings remain transient, outside backups.

### M2.6 — Local interactions

The project page now shows the last recorded Kodvido interaction and up to 100
recent events: inspection requests, saved workflow notes and relocations. This
history persists across restarts and relocation. Notes/path changes and their
success events commit atomically; stale or failed mutations create no event.
An inspection event records the request, including when inspection later finds
missing files; it does not claim a successful inspection or developer activity.

Read-only page/document views, scans before import and metadata imports/restores
are not tracked. Older activity is not fabricated: an empty history is UNKNOWN.
Ordering follows insertion sequence, so a corrected system clock does not reorder
previous events. Retention/removal affects only catalogue metadata. No paths,
commands, source content or identities are copied into these events.

This bounded operational history is excluded from JSON backups; restoring
metadata preserves notes but starts a fresh interaction history. It is not an audit
log or evidence of project/data backup. Manual backup declarations are available in M2.7; automated verification remains unsupported.

### M2.7 — Dated backup evidence

Choose **Renseigner les preuves** on a project page. Record four separate states:
remote Git sync, copies of untracked/ignored files, application data backup, and
restore test. UNKNOWN is the default. DECLARED_OK / DECLARED_FAILED require a check
date (not in the future, UTC) and evidence/problem description, up to 1000 characters.
For UNKNOWN, leave the date empty. Never enter credentials or secrets.

All results are human declarations, not independently verified by Kodvido. A Git
remote is not a backup of all files/data; a copy is not proof of restoration. Old
dates remain displayed without automatic renewal. No global safe score is shown.
Nothing executes, contacts a remote, or modifies a project when this form is saved.
Stale tabs receive HTTP 409 with escaped text for recovery. Relocation keeps the
worksheet; removal deletes only catalogue metadata. Saves do not add a history event.

The current JSON format includes these declarations. During development there is
no format version or backward-compatibility contract; earlier exports must be recreated. Restoring metadata preserves the
original check dates/revisions, and never verifies or restores project data.
See [M2.7 verification](docs/M2.7-VERIFICATION.md).
