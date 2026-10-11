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
