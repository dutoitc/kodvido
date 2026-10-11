# M2.1 verification — 2026-10-11

## Scope and local results

PR #1 is merged. This increment implements the first M2 vertical slice, not all
M2 requirements: project detail page, explicit read-only file activity inspection,
and live links to PROJECT.md, TODO.md, AGENTS.md and README.md.

Java 21.0.12.1 / Maven 3.9.9 on Linux: `clean verify` passed, followed by `verify`
after the final source changes. **37 discovered, 36 passed, one Windows-only
junction test skipped; no failures or errors.** All configured JaCoCo gates pass.
Tests use temporary fixtures; no personal project data is published.

| Module | Lines | Branches |
|---|---:|---:|
| app | 96.97% (32/33) | 75.00% (6/8) |
| detection | 97.93% (142/145) | 82.76% (96/116) |
| domain | 85.71% (12/14) | N/A (no branches) |
| engine | 100% (53/53) | 100% (36/36) |
| persistence | 100% (31/31) | 100% (6/6) |

Reporting remains empty. These are local per-module measurements, not an aggregate
percentage. Domain's branch gate remains vacuous.

## New exercised scenarios

- Inclusive UTC 7/30-day windows, older and future timestamps, empty directories,
  missing roots, non-directory roots, generated/vendor exclusions, unchanged
  source bytes and modification times.
- Traversal depth/entry/time limits, cancellation, links and unreadable directories;
  partial/failed evidence is distinguished from missing evidence.
- Exact document allowlist, live rereading, oversized documents, non-UTF-8 bytes,
  directories and symlink documents, missing documents and failed reads.
- Immutable snapshot collections; catalogue relocation used on the next document
  read; removed/unknown IDs rejected without touching source files.
- Real Spring/H2/Flyway MockMvc project page, inspection form, CSRF rejection,
  document HTML escaping and live changes. All M1 regression tests also run.
- `git diff --check` passed.

## CI

[CI run 38151716716](https://github.com/dutoitc/kodvido/actions/runs/38151716716)
passes on commit `e0fb75f44bc34b67f456f6f306e7b46ad9b27695` in
[PR #2](https://github.com/dutoitc/kodvido/pull/2):

| OS | Discovered | Passed | Skipped | Result |
|---|---:|---:|---:|---|
| Ubuntu | 37 | 36 | 1 Windows-only junction test | SUCCESS |
| Windows | 37 | 35 | 2 POSIX-only permission tests | SUCCESS |

The symlink tests ran on both systems. All configured JaCoCo gates passed on both
systems, and test/coverage reports plus runnable JARs were uploaded. Percentages
above are local measurements; CI confirms the gates, not identical percentages.
The pull request records verification of the final documentation commit.

## Limits and next slices

Snapshots are transient; no persistent observation or interaction history yet.
File mtime is low-confidence evidence, never a developer-activity claim. Git status,
last interaction and all backup confidence remain UNKNOWN. `.gitignore` is not
interpreted; exclusions are explicit and visible. Only the four root-level document
names are supported; docs subdirectories and Markdown rendering are deferred.
Documents are bounded UTF-8 text and never persisted in H2.

Traversal timeout is cooperative, not a guarantee against blocking OS calls.
Stable-tree/no-hostile-race assumptions from ADR-008 apply. No discovered commands,
Git hooks, document instructions or scripts are executed. No new dependencies or
schema changes are introduced.

Next M2 slices: Git evidence; phases and separate development/operations restart
cards; backup evidence; recommendations/sanitized local issue drafts; portable
JSON catalogue backup/import. M3 exports remain unstarted.

## Changed components

`ProjectInspection` (domain), `ProjectInspector` (detection),
`ProjectDetailsService` (engine), app composition/controller/system stage,
project/document templates and catalogue links, tests in domain/detection/engine/app,
README, roadmap, ADR-010 and verification records. Persistence is unchanged.
