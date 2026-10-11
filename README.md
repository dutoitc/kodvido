# Kodvido

**Local-first project cockpit** for development **and operations**. Windows + Linux, projects with or without Git.

This archive is a **bootstrap scaffold**, not a finished V1. The Java sources and initial tests have **not been compiled in the packaging environment** (Maven unavailable). Work must compile, correct, test and implement milestone M1 before claiming anything operational.

## Start

- Java 21 and Maven 3.9+.
- `mvn -B verify` at repository root.
- `mvn -pl kodvido-app -am spring-boot:run` to start on localhost:8080 (after tests are passing).
- Review `docs/VISION.md`, `docs/REQUIREMENTS.md`, `docs/ARCHITECTURE.md`, `docs/ROADMAP.md`, and `AGENTS.md` first.
- Initial demo REST endpoint: `GET /api/system` (not a completed dashboard).

## Design principles

1. Detectors only **observe** and never mutate scanned projects.
2. Projects keep their native `PROJECT.md`, `TODO.md`, `AGENTS.md` and other docs when available.
3. Stable project IDs survive relocation; deleting from catalog never deletes source files.
4. Preserve provenance, observed timestamp and confidence/limitations in observations.
5. Export standalone HTML (works by opening `file://` without server) and legible single-page A4 PDF snapshot.
6. Private data remains local; public source repository contains no personal paths or workplace metadata.

See `WORK_INITIAL_PROMPT.md` for execution instructions for ChatGPT Work.
