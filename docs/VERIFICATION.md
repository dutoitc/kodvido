# M1 verification — 2026-10-11

## Actual result

Linux, Eclipse Temurin 21.0.12.1, Apache Maven 3.9.9.
`mvn -B --no-transfer-progress clean verify`: **BUILD SUCCESS**.
Environment-only Maven proxy/trust-store settings were required locally; none
are included in source or delivery configuration.

28 tests discovered; 27 executed successfully, 1 skipped, 0 failures, 0 errors.

The sole skipped test is `windowsJunctionIsNotTraversed`, intentionally enabled
only on Windows. The symlink and POSIX permission-denial tests ran on Linux.
The native junction test also passes in the Windows CI job.

| Module | Lines | Branches |
|---|---:|---:|
| kodvido-app | 95.45% (21/22) | 75.00% (6/8) |
| kodvido-detection | 97.67% (84/86) | 83.87% (52/62) |
| kodvido-domain | 85.71% (6/7) | N/A (no branches) |
| kodvido-engine | 100.00% (46/46) | 100.00% (36/36) |
| kodvido-persistence | 100.00% (31/31) | 100.00% (6/6) |

Reporting contains no implementation and has no coverage. All configured JaCoCo
gates passed. Domain has no measured branches; its branch gate is vacuous.
Coverage above is per-module, not an invented repository-wide percentage.

## Executed scenarios

- Original bootstrap: unchanged build and both original tests passed.
- Maven/Python markers, excluded build/vendor trees, overlapping roots, declared
  nested modules, independent nested projects, module separators and profiles.
- Malformed, oversized, wrong-root and XXE POMs; missing/file roots, symlink
  ancestors, loops, marker symlinks, native permissions, bounds and cancellation.
- Preview without writes; explicit import; stale/expired/invalid selection;
  duplicate import; missing marker; module override; native path parsing.
- Stable ID/name/creation time on relocation; missing relocation target; removal
  without filesystem deletion.
- File-backed H2 reopen, Flyway initial migration and idempotency, duplicate batch
  rollback, conflicting relocation and sanitized database failures.
- Spring web onboarding, confirmation, relocation and removal through MockMvc;
  real H2/Flyway, CSRF enforcement, Host rejection and HTML escaping.
- Separate packaged-JAR smoke check over real HTTP: start, cookie/CSRF form scan
  and import, stop, restart, verify the catalogue survived in file-backed H2 and
  the source POM remained unchanged. **PASS**.
- `git diff --check`: passed.

## Delivery and acceptance limits

The owner authorized public visibility for [dutoitc/kodvido](https://github.com/dutoitc/kodvido).
Implementation is in [PR #1](https://github.com/dutoitc/kodvido/pull/1), merged on 2026-10-11.
[CI run 38145468894](https://github.com/dutoitc/kodvido/actions/runs/38145468894)
verified commit `60c257296184c80a375d8abfc587302b9e8e3068` with Java 21:

| Job | Result | Discovered | Passed | Skipped |
|---|---|---:|---:|---:|
| Ubuntu | SUCCESS | 28 | 27 | 1 (Windows junction only) |
| Windows | SUCCESS | 28 | 27 | 1 (POSIX permissions only) |

All JaCoCo gates passed on both platforms; JAR and verification artifacts were
uploaded successfully. The coverage percentages above are measured locally;
remote logs confirm the same configured gates passed, not identical percentages.
The Windows junction and symbolic-link tests ran successfully. This report records M1 only. Automated M1 acceptance is complete and PR #1 is
merged. Subsequent work is recorded in `M2-VERIFICATION.md`.

Roots and previews are transient. M1 persists project UUID/name/path/timestamps;
Git metadata, notes, persisted evidence history, portable catalogue export and
reports are deferred. Profile/property/external Maven module resolution is partial
by design and explicitly reported. Relocation is a user-confirmed mapping change,
not automatic proof that the new directory is the same project.

The scan budget is cooperative, not a hard timeout for blocked OS calls. No
protection against hostile simultaneous filesystem mutation is claimed.
The repository is public by explicit owner authorization; its licence is not yet selected.

## Main changed files

- Parent/module POMs, `.github/workflows/ci.yml`, `.gitignore`, `.gitattributes`.
- Domain records and `Catalog` port; `SafePaths` and `ProjectDirectoryScanner`.
- `CatalogService`; `JdbcCatalog`; migration owned by persistence.
- App composition/security/controller, Thymeleaf templates, CSS and configuration.
- Unit/integration tests in domain/detection/engine/persistence/app.
- README, roadmap, ADRs, M0 and M1 verification reports, draft PR description.

## CI portability correction

The first remote Ubuntu job passed. Windows reached the engine tests and exposed
two test expectations comparing an 8.3 temporary-directory alias to the canonical
path intentionally returned by Kodvido. Expectations now use real no-follow paths;
a regression verifies equivalent root spellings yield one catalogue entry.
Scanner fixtures are canonicalized so path-based assertions inspect candidates
on Windows as well. The native Windows junction test passed in the failing run.
The corrected matrix passed on both operating systems (run linked above).
