# Roadmap — implement by verified vertical slices

## M0 — Bootstrap
- [ ] Compile all modules and run initial tests on Linux and Windows.
- [ ] Fix errors in supplied starter if any, document actual commands and results.
- [ ] CI workflows, JaCoCo baseline, .gitignore, documentation and safety defaults.

## M1 — Scan + catalogue (first Work mission)
- [ ] User declares scan roots; scanner discovers Maven and Python candidates without mutating them.
- [ ] Exclude generated dirs, avoid symlink loops, protect against exceptions; nested multi-module Maven grouped.
- [ ] Candidate review/import endpoint + minimal UI; persist chosen projects to file-backed H2 with Flyway.
- [ ] List projects, inspect path, relocate without losing stable ID, remove **catalog only**.
- [ ] Tests for nested Maven, Python, invalid paths, permissions where portable, Windows/Linux separators, and non-destructive removal.
- [ ] `mvn -B verify` green on both OS GitHub CI.

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
