# Kodvido

A local Java 21 application for discovering and cataloguing software projects.
The first M1 slice is implemented and verified on Linux. Windows CI acceptance
is still pending; see [verification](docs/VERIFICATION.md). No M2/M3 features are
implemented. No open-source licence is selected yet; see LICENSE-CHOICE.md.

## Build and run

Install Java **21** and Maven **3.9+**, then run from the repository root:

```sh
mvn -B --no-transfer-progress clean verify
java -jar kodvido-app/target/kodvido-app-0.1.0-SNAPSHOT.jar
```

Open **http://127.0.0.1:8080**. The same commands work on Windows and Linux;
Windows execution still needs confirmation by the supplied CI matrix.
The app is local-only and must not be exposed through a reverse proxy or to a LAN.

The executable JAR in the delivery archive only requires Java 21, not Maven.
The H2 database is created outside the repository, under `~/.kodvido/data`.
Set `KODVIDO_DATA_DIR` to an **absolute directory** before starting to use a
separate home/work catalogue. Stop the app before copying its database files.
No telemetry, remote calls, Git operations, or discovered scripts are run.

## Use

1. Enter one absolute scan root per line, for example `~/projects` on Linux or
   `D:\projects` on Windows. Up to 20 roots can be scanned together.
2. Review candidates and diagnostics. Maven and Python are detected using
   `pom.xml`, `pyproject.toml`, `requirements.txt`, and `setup.py`.
3. Select projects and confirm import. Maven children declared in their parent's
   POM are unchecked by default; select a child explicitly to manage it separately.
4. Use **Relocaliser** after moving a project yourself. Validate the destination;
   Kodvido changes only the catalogue mapping and preserves the project UUID.
5. Use **Retirer du catalogue** to remove an entry. Source files are never deleted.

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
