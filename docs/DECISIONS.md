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
