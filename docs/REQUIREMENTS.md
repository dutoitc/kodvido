# Product requirements — priority and acceptance criteria

Definitions: **M1** minimum end-to-end intake, **M2** core cockpit, **M3** reports, **later** future capabilities. Every detected fact carries provenance (`source`), `observedAt`, detector version and structured diagnostic.

| ID | Requirement | Priority | Acceptance criterion |
|---|---|---|---|
| R01 | Configure multiple scan roots | M1 | Accept Windows and POSIX paths; missing root shown as error, no crash |
| R02 | Recursively discover Maven and Python projects | M1 | `pom.xml`, `pyproject.toml`, `requirements.txt`, `setup.py` recognized; ignore `.git`, `target`, `build`, `.venv`, `node_modules`, `dist` |
| R03 | Offer review/confirmation before catalog import | M1 | Scan alone never adds or changes a project |
| R04 | Handle nested Maven modules and duplicate candidates | M1 | Do not treat Maven children as unrelated project by default; user may override |
| R05 | Catalog with stable ID, path and optional Git metadata | M1 | Project can be relocated retaining ID, notes and observations |
| R06 | Remove from catalog without deleting files | M1 | Deleting a catalog entry does not change disk content |
| R07 | Read project-owned docs | M2 | Link `PROJECT.md`, `TODO.md`, `AGENTS.md` when present; do not copy or overwrite |
| R08 | Activity estimation | M2 | Distinguish last Hub interaction, last Git commit and disk file-change window/count, filter generated files; confidence shown |
| R09 | Detector status/limitations | M2 | `SUPPORTED/PARTIAL/UNSUPPORTED/FAILED` and actionable gap description; error is not absence |
| R10 | Version/stack/scripts discovery | M2 | Report Java and Python evidence, discovered scripts, no auto-execution |
| R11 | Backup confidence | M2 | Distinguish remote Git sync, copies of untracked files, data backup, restore test; UNKNOWN by default |
| R12 | Recent phases | M2 | 3–5 manually recorded phases, with date, description, completion status (no full task tracker) |
| R13 | Development & operational restart | M2 | Separate cards for how to build/test and how to run/diagnose/deploy |
| R14 | Recommendations/actions | M2 | Clearly marked suggestions, no automatic scripts or updates |
| R15 | Global self-contained dashboard HTML | M3 | Open from `file://` in offline browser, no server calls/assets; explicit snapshot time |
| R16 | Per-project HTML | M3 | Standalone offline view; links optional; avoid leaking home/work paths by default |
| R17 | Single-page A4 PDF | M3 | Readable, visual workflow, status+backup, recent phases and small notes area, automatically check pages=1 |
| R18 | Developer evolution request | M2 | Detector gaps appear with locally generated, sanitized issue draft; never sent automatically |
| R19 | Platform parity & CI | M1 | GitHub Actions green on Ubuntu and Windows, Java 21 |
| R20 | Restore-friendly local storage | M2 | H2 data file outside repo, Flyway migrations, portable JSON metadata backup/import |

## Important semantics

- An H2 cached detection of a file is not the project documentation source of truth.
- A Git remote does not prove a push or backup, and a backup does not prove restore viability.
- Filesystem mtime is an imperfect proxy for activity; avoid treating bulk-generated changes as active development.
- Manage home/work data in separate installations/profiles. Never publish private path/credentials in sample config or GitHub issues.
- Onboarding is by folder roots; scanner **cannot** read arbitrary files outside user-selected roots during normal discovery.
