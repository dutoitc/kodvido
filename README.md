# Kodvido

A local Java 21 / Spring Boot dashboard for your software projects. Start with a
sortable inventory, then open a project for technologies, notes, tasks and docs.
Windows and Linux; local H2 storage; GPL-3.0-only. M2 remains under validation and
M3 offline HTML/PDF exports have not started.

## Build and run

Install Java **21** and Maven **3.9+**:

```sh
mvn -B --no-transfer-progress clean verify
java -jar kodvido-app/target/kodvido-app-0.1.0-SNAPSHOT.jar
```

Open **http://127.0.0.1:8930**. The executable JAR requires Java 21, not Maven.
Bind only to loopback; do not expose the application to a LAN or reverse proxy.
H2 lives outside the repository under `~/.kodvido/data`. Set `KODVIDO_DATA_DIR` to
an absolute directory for separate work/home catalogues. Stop the app before
copying its database files. There is no telemetry or automatic project mutation.

## Use the inventory

Open http://127.0.0.1:8930 for the sortable inventory. Configure multiple absolute
root paths under **Dossiers racines**, then review/confirm detected projects.
One repository forms one row; module technologies appear together. Observations
are persisted in local H2, refreshed at startup and every 30 minutes. Global and
project rescan buttons queue work without blocking the page; reload the view when
it finishes. Only one job runs at a time. Set `kodvido.inventory.automatic=false`
to disable startup/periodic refresh; manual buttons remain available.

Click a project for technologies, remote checks, short note, text todo-list,
lifecycle and read-only Markdown links. Short notes/tasks are part of private JSON
catalogue backups; configured roots and observations are retained in H2 but omitted
from portable JSON. Reconfigure roots after a JSON-only restore and rescan.
Older phases/restart/backup worksheets remain secondary detail links.

Remote Git checks contact configured HTTP(S)/SSH repositories using existing Git
credentials without interactive prompts. No fetch/push, build, clean or source
write occurs. Not-found/private, denied, timeout and unreachable remain separate.
SSH hosts must already be trusted in the user's known_hosts; Kodvido adds no keys.
URLs with embedded HTTP credentials/query strings and complex linked Git layouts
are deliberately unsupported. Never put tokens in remote URL paths.

The explicit dependency analysis button compares declared Maven coordinates and
Angular/Bootstrap constraints with Maven Central/npm releases. Coordinates are sent
to those registries; project files are not. This is not Maven effective-model,
transitive dependency, Sonar, vulnerability or Java EOL analysis. Different release
numbers require interpretation, especially version ranges/prereleases. Scans do
not execute Maven or npm. See ADR-021 for bounds and the read-only rationale.

## Limits and recovery

Discovery supports at most 20 native roots, 32 levels / 100,000 entries / a
30-second cooperative budget. Inventory uses 24 levels / 50,000 entries / 15
seconds, 200 manifests of 256 KiB each, and up to 1000 declared Maven dependencies.
Generated/vendor directories, symlinks and junctions are skipped; nested Git
repositories are separate. No arbitrary script, Maven model extension or plugin
is executed. Scanned trees should remain stable; blocking filesystem operations
and hostile concurrent replacement remain OS-level limitations.

Version queries are limited to 50 requests / 30 seconds plus the final in-flight
request, with 5-second request and 256 KiB body limits. Git commands have a
12-second process deadline and bounded output; up to 10 remotes are checked per
project. Slow/unavailable repositories can delay a full inventory. The dashboard
remains responsive and displays the previous dated observations during work.

Line counts are physical lines (including comments) in supported source extensions,
not logical code statements. Files above 256 KiB are excluded. Missing declarations
do not prove absence of a language. `.gitignore` is not interpreted by the inventory
walker. Java support/EOL and semantic dependency upgrade decisions remain future work.

Use **Relocaliser** after moving a project yourself; identity and notes survive,
observations are invalidated. **Retirer du catalogue** deletes only catalogue
metadata. Markdown links read the current file, bounded and HTML-escaped; missing
files are optional. Notes/tasks are inert text and never execute commands.

Private JSON backup/import includes project identities, paths, lifecycle, notes,
tasks and earlier phase/restart/backup declarations. It excludes source files,
root configuration, observations and interaction history. Review imports and adapt
paths before confirming. Existing IDs/paths cause an atomic rollback. Limits:
8 MiB / 1000 projects, one current unversioned development format. Never publish
these private JSON files. This is not a source/data backup or proof of restoration.

## Architecture and verification

`domain` (immutable types/ports), `detection` (read-only filesystem/network adapters),
`engine` (Spring-free use cases), `persistence` (JDBC/H2/Flyway), `reporting` (reserved),
`app` (Spring Boot composition, background worker and Thymeleaf).

`mvn verify` runs JUnit unit and integration suites with synthetic temporary projects,
H2/Flyway, real local Git and HTTP fixtures and the Spring web layer. JaCoCo reports
are in each module's `target/site/jacoco`; test XML is in `target/surefire-reports`.
Gates: engine/domain ≥90% branches, detection ≥85% lines, persistence ≥80% lines.
The existing GitHub Actions matrix checks Java 21 on Ubuntu and Windows.

See [current verification](docs/M2.10-VERIFICATION.md),
[requirements](docs/REQUIREMENTS.md), [decisions](docs/DECISIONS.md),
[roadmap](docs/ROADMAP.md) and earlier milestone verification records in `docs/`.

## License

Copyright (C) 2026 Kodvido contributors. Licensed under **GPL-3.0-only**, without
warranty. Commercial redistribution is permitted under GPLv3; no royalty or
upstream contribution is required. Dependencies retain their own licenses.
See [LICENSE](LICENSE) and [license decision](LICENSE-CHOICE.md).
