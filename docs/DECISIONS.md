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
