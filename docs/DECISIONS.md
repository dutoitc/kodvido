# Architectural decisions

## ADR-001 Local-first modular monolith — accepted
Spring Boot only in application module, Java 21, Maven multi-module, core engine independent from presentation to enable future CLI or MCP.

## ADR-002 Dual storage — accepted
File-backed local H2 for catalog/history/cache. Files owned by each project remain there. No H2 database, secrets or private metadata in Git.

## ADR-003 Read-only discovery — accepted
Root scan only observes. Confirmation for import and all future side-effects. No archival feature in early milestones.

## ADR-004 Offline export — accepted
Static standalone HTML with no live server dependencies, PDF one-page A4 per project, visible snapshot timestamp.

## ADR-005 No Angular initially — accepted
Use minimal Spring Boot HTML interface. Avoid frontend build toolchain until needed.

## ADR-006 Separate home and workplace — accepted
Keep separate profiles/catalogs and obey workplace security rules; GitHub code repo is public-capable, never home/work runtime data.

## ADR-007 M1 persistence and module grouping — accepted
Plain JDBC in the persistence adapter keeps all core modules Spring-free. Flyway
migration resources are owned by persistence and discovered by the application.
A scan produces transient evidence; only selected project identities and paths
are persisted in M1. Rich observation history, notes and Git metadata stay in M2.
Relocation updates only path and updated-at, retaining ID/name/created-at.

Only declared, already discovered descendant Maven modules are grouped. Profile,
property-based, missing or external module references are partial findings, never
an instruction to read beyond selected roots. Nested independent projects remain
independent. Child candidates remain selectable as an explicit override.

## ADR-008 Local review and security — accepted
Scans support 1–20 native absolute roots, with ~ expansion. Windows paths are
interpreted on Windows, POSIX paths on Linux; foreign paths are not guessed.
A new preview replaces the previous one; confirmation expires after 30 minutes.
All selected candidates are validated before a transactional insert. Scanning
alone never persists a project. Roots are entered per scan in M1, not saved.

Keep CSRF enabled for every mutation, HTML escaping, no CORS, loopback binding and
an explicit Host allowlist. Do not disable these to facilitate browser scripts.
Scans bound traversal at 32 levels / 100,000 entries / 30 seconds; limits are
reported. Blocking filesystem operations cannot be forcibly interrupted; network
mounts are not recommended. Concurrent hostile filesystem replacement is outside
the M1 threat model; no-follow checks reduce but cannot eliminate such OS races.

## ADR-009 Verification boundaries — accepted
The supplied bootstrap compiles unchanged under Java 21 on Linux. The owner created
the GitHub repository and explicitly approved public visibility on 2026-10-11.
The M1 implementation passes both GitHub matrix jobs after correcting Windows
8.3-path expectations in the tests. Licence selection remains a separate decision.
Reporting remains intentionally empty; no M2/M3 implementation is included.
Mockito subclass mock maker avoids runtime self-attachment in restricted JVMs;
web integration tests use the real application, JDBC repository and Flyway schema.

## ADR-010 M2.1 read-only project cockpit — accepted

Deliver the first M2 vertical slice before Git/phases/backup features. Activity
counts filesystem mtimes only, not developer activity. Use inclusive UTC 7/30-day
windows; future timestamps and incomplete traversal produce PARTIAL evidence.
Counts exclude generated/vendor directories and expose their limits in the UI.
Git, backup and last-interaction evidence stay explicitly UNKNOWN.

Inspect on an explicit CSRF-protected POST to avoid recursive work on page open.
Snapshots are transient in this increment; no history or schema migration is
introduced yet. Keep observation logic in detection, coordination in engine and
presentation in app. Document links resolve the current catalogue path on every
request, so relocation does not retain stale paths or copied contents.

Only four exact root-level Markdown filenames are allowed. Read bounded UTF-8
text with no-follow checks, and HTML-escape it without Markdown rendering. Treat
AGENTS.md and other contents as untrusted data, never instructions. The existing
stable-tree / cooperative-timeout constraints also apply to these inspections.

## ADR-011 Git evidence and GPLv3 — accepted

The owner selected GPL v3 on 2026-10-11 after discussing commercial forks and
reciprocity. Use GPL-3.0-only; retain the standard license text without added
commercial restrictions. Embed LICENSE in distributable JARs and link it in the UI.
The official repository's governance does not grant control over independent forks.

For M2.2 use the trusted installed Git executable with a fixed, read-only HEAD-log
command. No shell or project executable is used. Reject indirect metadata/config
before invocation, remove inherited Git environment settings, ignore user/system
configuration and disable signature verification, notes, pager, optional locks
and automatic maintenance. Support packed commits and detached HEAD without
reimplementing Git object parsing. Unsupported layouts fail closed. No network,
status refresh, worktree writes, author identity, messages or remote URLs.

Expose commit and committer time as local Git-declared evidence, separate from file
mtime and unknown backup/interaction history. A commit is not proof of a push.
Failure does not prove absence: an unborn or damaged HEAD is explicitly unreadable.

## ADR-012 Manual workflow notes — accepted

M2.3 stores up to five manually entered recent phases plus separate development
and operations restart notes in the local catalogue. This supplements optional
PROJECT.md; it does not parse, copy, overwrite or replace project-owned documents.
The UI labels this data as user-declared, with a save timestamp, separately from
measured evidence. Commands remain inert text; no execution control is added.

Use a separate domain port and JDBC adapter, with Flyway V2, stable project foreign
keys and cascading metadata removal. Relocation preserves notes. Save the entire
small worksheet atomically and require its revision to match, preventing lost
updates from two browser tabs. Initial revision is zero; successful saves advance
it. No phase task tracker, automatic project-file editing or M3 exports.

## ADR-013 Portable catalogue metadata backup — accepted

M2.4 adds versioned local JSON backup/import (R20), separate from M3 shareable
reports. The explicit CSRF-protected download includes stable IDs, names, paths,
creation/update timestamps and all manual workflow notes/revisions. It excludes
project files and transient detector evidence. Paths cannot be sanitized in a
restore artifact without losing its purpose: the UI identifies this download as
private local metadata, never a shareable report. It is neither encrypted nor
proof of source/data backup; no remote destination is contacted.

The persistence port reads a serializable transaction snapshot and inserts a
restoration batch plus notes in one transaction. Existing IDs or identical stored
path strings cause rollback; no merge/overwrite option is provided. JSON version
1 is strict and bounded (8 MiB, 1000 entries, depth 32, normal field limits).
Malformed input and SQL failures do not echo private content.

A transient preview expires after 30 minutes and is replaced by the next valid
preview. The user can edit each destination path before confirming. Paths must be
native absolute paths on the destination OS; foreign paths are not guessed.
Missing directories are permitted so catalogue recovery does not require restoring
source files first. Paths are normalized lexically, not resolved or traversed;
filesystem aliases, case variants and symlinks are not identified during import.
No scan, folder creation, command execution or source-file restoration occurs.
Regular inspection and relocation retain their existing no-follow controls.

## ADR-014 Root stack declarations and script filenames — accepted

M2.5 adds transient evidence for R10 on explicit inspection. Read at most 256 KiB
per root pom.xml, pyproject.toml and .python-version, with no-follow checks. Secure
JDK XML parsing rejects DTD/entities and limits nesting to 64. Only direct Maven
Java properties and build/plugins compiler configuration are collected; no parent,
profile, child-module, pluginManagement or property interpolation resolution.
Conflicting values remain separate, with their source. No effective runtime claim.

Python support is intentionally lexical: one simple quoted requires-python in a
single [project] table, and one simple version in .python-version. Multiline TOML,
escaped constraints, duplicate declarations and invalid UTF-8 fail closed. This is
not validation of the whole TOML document or interpretation of Poetry/setup.py.
Do not add a partial home-grown packaging resolver or execute Maven/Python.

Discover potential scripts by filename only at root and in the immediate scripts/
directory (200 total entries, cooperative 5-second enumeration deadline). Never
read or execute their content. Ignore links and special files. Results are PARTIAL
when evidence exists, UNKNOWN if no supported evidence exists; missing/unreadable
roots have explicit statuses. A linked scripts/ directory makes the observation
FAILED while retaining any root declarations already read. Diagnostics contain
fixed relative manifest names/codes, not exceptions or absolute paths.

This is the first bounded R10 implementation, not complete stack/dependency
inference. Observations remain transient and excluded from metadata backups.
M3 is unchanged. The stable-tree and cooperative-filesystem limits still apply.

## ADR-015 Bounded local interaction history — accepted

M2.6 completes the missing local-interaction evidence in R08 with a separate
append-only event interface and Flyway V3 table (retained window: 100 events per
project). Events contain only project ID, sequence, fixed kind and local-clock
instant. No path, command, document text, user identity or detector output is copied.

Record INSPECTION_REQUESTED after catalogue identity lookup and before inspection;
this is a request, not a success or source-activity claim. Record WORKFLOW_SAVED
and RELOCATED in the same JDBC transaction as their successful mutation. Failed
or stale saves/relocations cannot create success events. If event persistence fails,
the associated mutation rolls back. Read-only page/document GETs do not record
interactions; CSRF rejection precedes recording. Import/restore and earlier actions
are not reconstructed. Empty history is explicitly UNKNOWN.

A parent-row lock serializes insertion/retention per project. Ordering uses the
monotonic database sequence rather than wall-clock timestamps (which can regress).
Keep the newest 100 recorded events, cascade metadata removal, and preserve the
history across relocation. This is a local convenience history, not an audit log
or a developer productivity measure. Project files remain untouched.

Version 1 JSON backups keep their existing identities/notes contract; this bounded
operational history is intentionally excluded, like transient observations. Restored
catalogues therefore start with UNKNOWN interaction history. This exclusion is
visible on the backup and project pages and documented, without a format change.
Backup-confidence indicators (R11) and M3 remain separate work.

## ADR-016 Dated backup declarations and JSON v2 — accepted

R11 needs four independent dimensions: remote Git synchronization, copies of
untracked/ignored files, application data backup, and an actual restore test.
M2.7 implements a manual evidence worksheet, not a backup or verification engine.
Each dimension defaults to UNKNOWN. DECLARED_OK and DECLARED_FAILED require a
non-future UTC check date and an inert description (maximum 1000 characters).
UNKNOWN has no check date, but may carry an explanatory note. These states never
become verified through saving, inspection, relocation or metadata import. Old
dates remain visible; there is no expiry policy or aggregate safe/green score.
No remote URL is fetched and no backup/restore command is executed.

The engine validates complete, unique dimensions. Flyway V4 stores the worksheet
and its four evidence rows, keyed by the stable project UUID. Parent-row locking
and optimistic revisions prevent concurrent first-save and stale-tab overwrites.
Replacement and removal are transactional; source files are never touched. The
worksheet has a save timestamp but adds no new interaction-history event kind.

JSON export moves to version 2 to preserve these user-authored declarations, dates
and revisions in the same transaction as project identity and workflow notes.
Import accepts v1 (without assessments) and v2, rejects v1 with assessments and
validates evidence before preview. Imports do not manufacture verified results or
interaction history. Older Kodvido versions reject v2; use an updated application
when restoring it. Private paths/notes remain local and are never issue content.
Automatic confidence verification and richer backup policies remain future work.
