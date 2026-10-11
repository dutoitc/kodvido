# Roadmap — implement by verified vertical slices

## M0 — Bootstrap
- [x] Compile all modules and run tests on Linux and Windows.
- [x] Compile the supplied starter on Linux; no compilation correction needed. Commands/results documented.
- [x] CI workflows, JaCoCo gates, .gitignore, documentation and safety defaults configured.

## M1 — Scan + catalogue (first Work mission)
- [x] User declares scan roots; scanner discovers Maven and Python candidates without mutating them.
- [x] Exclude generated dirs, avoid symlink loops, protect against exceptions; nested multi-module Maven grouped.
- [x] Candidate review/import endpoint + minimal UI; persist chosen projects to file-backed H2 with Flyway.
- [x] List projects, inspect path, relocate without losing stable ID, remove **catalog only**.
- [x] Tests for nested Maven, Python, invalid paths, permissions where portable, Windows/Linux separators, and non-destructive removal.
- [x] `mvn -B verify` green on both OS GitHub CI.

## M2 — Usable cockpit
- [ ] Git/file activity detectors and evidence confidence + exclusions.
- [ ] Read `PROJECT.md`/`TODO.md`/`AGENTS.md` as links, phases, operational restart, backup evidence.
- [ ] Detector gaps with sanitized local enhancement request, recommendation cards.
- [ ] Document project workflow and recent phases without turning into a task tracker.

## M3 — Offline exports
- [ ] Snapshot model, self-contained dashboard/project HTML and readable single-page A4 PDF.
- [ ] Automated checks for standalone HTML, injection sanitization and PDF exactly 1 page.
- [ ] CI artifacts and deterministic snapshot tests.

## Future (no implementation yet)
GitLab/Bitbucket/Jenkins/OpenShift integrations, actual script execution behind confirmation, CLI/MCP, auto-version remediation, database/architecture annexes, change of profiles, richer recommendation policy.

M1 automated acceptance passes on both operating systems; PR #1 was merged.
M2.1 implements a project detail page, bounded file-mtime observations and live
read-only links to four project-owned documents. The broader M2 items above remain
unchecked until their full acceptance criteria pass. M3 remains unstarted.
See `M2-VERIFICATION.md` for this increment.
See `VERIFICATION.md` for the exact executed tests and current limitations.
