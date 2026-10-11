# Vision — Kodvido

Kodvido is a **single local entry point** for a diverse portfolio of software projects, work and home, some not in Git. Users want to resume development or operations after months, know what ran where, what was updated and what remains uncertain. Project status must not require constant manual upkeep.

## Experience

- Open Kodvido; see all projects sorted by recent *evidence-backed* activity, with risks and life-cycle phase.
- Add scan roots such as `~/git` or `D:\donnees\git`; confirm detected candidates.
- Open one project and understand *location, Git, dependencies, run/build/deploy workflow, last phases, operation/runbook, backup verification status*.
- Choose a **suggested** next action (put under Git, update Java, document, build, run); automatic execution postponed.
- Print a one-page, primarily visual (approx. 60%) A4 status sheet with small handwritten notes area; generate a standalone HTML dashboard and per-project views.

## Non-goals initially

- No cloud service, multi-user auth, archive functionality, automatic code changes, production deployment, Jenkins APIs, remote Git operations, MCP server, general task tracker, or frontend SPA.
- H2 is not the single source of truth for markdown documents inside projects.

## Deployment

Local Java/Spring Boot service bound to `127.0.0.1`, no compulsory Docker, Windows/Linux. GitHub hosts public code only; personal/work data stores remain isolated and local.
