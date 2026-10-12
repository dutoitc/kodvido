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
and timestamps persist. Git/notes/observation history are future M2 work.

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
≥80% lines. Domain currently contains no measured branches; reporting is empty.

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

Interaction history, phases, restart cards, backup evidence,
recommendations and portable JSON catalogue backup remain subsequent M2 work.
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
