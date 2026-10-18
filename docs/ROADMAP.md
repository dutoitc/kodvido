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
- [x] Detector gaps with sanitized local enhancement request, recommendation cards (M2.8).
- [x] Document project workflow and recent phases without turning into a task tracker (M2.3).

- [x] Portable JSON catalogue backup/import, with review and manual path mapping (M2.4).

## M3 — Offline exports
- [ ] Snapshot model, self-contained dashboard/project HTML and readable single-page A4 PDF.
- [ ] Automated checks for standalone HTML, injection sanitization and PDF exactly 1 page.
- [ ] CI artifacts and deterministic snapshot tests.

## Future (no implementation yet)
GitLab/Bitbucket/Jenkins/OpenShift integrations, actual script execution behind confirmation, CLI/MCP, auto-version remediation, database/architecture annexes, change of profiles, richer recommendation policy.

M1 automated acceptance passes on both operating systems; PR #1 was merged.
M2.1 implements a project detail page, bounded file-mtime observations and live
read-only links to four project-owned documents. The other broader M2 items remain unchecked until their full acceptance criteria pass. M3 remains unstarted.
See `M2-VERIFICATION.md` for this increment.
See `VERIFICATION.md` for the exact executed tests and current limitations.

M2.2 adds local HEAD commit evidence (packed and detached HEAD included) and adopts
GPL-3.0-only as explicitly selected by the owner. M3 remains unstarted. See `M2.2-VERIFICATION.md`.

M2.3 implements up to five manual phases and separate development/operations restart
worksheets, persisted with Flyway V2 and optimistic revision checks. R12/R13 are
implemented by manual catalogue notes; project-owned documentation remains untouched.
Interaction history, richer stack/script evidence, backup evidence, recommendations,
local issue drafts remain before full M2 acceptance. JSON metadata backup/import
is implemented in M2.4. See `M2.3-VERIFICATION.md`. M3 remains unstarted.

M2.4 implements R20 JSON metadata backup/import, including M2.3 notes. It does not
establish project backup confidence (R11) or implement M3 reports. See
`M2.4-VERIFICATION.md` for executed checks and limits.

M2.5 adds root Java/Maven and Python version declarations plus shallow script-name
inventory (R10, partial coverage). Parent/profile/module resolution and broader
Python packaging remain unsupported; no commands are executed. See
`M2.5-VERIFICATION.md`. M3 remains unstarted.

M2.6 adds the local interaction part of R08: persisted inspection requests,
successful note saves and relocations, with a 100-event window per project. It is
shown separately from Git/file activity; read-only page views are not events.
See `M2.6-VERIFICATION.md`. R11 backup confidence, recommendations/local issue drafts,
richer stack evidence and M3 remain pending.

M2.7 implements R11 as four independent, dated manual evidence declarations with
UNKNOWN defaults, Flyway V4 persistence, optimistic concurrency and JSON metadata transfer. It does not verify backups automatically. See
`M2.7-VERIFICATION.md`. Recommendations/local sanitized drafts and final M2
acceptance remain; M3 has not started.

M2.8 adds deterministic suggestion cards (R14) and local allowlisted Markdown issue
drafts for incomplete/unavailable detectors (R18). No remote submission or execution.
See `M2.8-VERIFICATION.md`. Current JSON transfer is deliberately unversioned (ADR-017).
Final M2 acceptance/documentation review remains; richer stack resolution remains
explicitly unsupported. Do not start M3 before M2 validation.

M2.9 adds port 8930 and the filterable/paginated catalogue with bounded, timestamped
last-inspection summaries. PR #9 was merged into the old feature branch rather than
main; this increment carries that reviewed history forward into main as well.
See `M2.9-VERIFICATION.md` and `M2-ACCEPTANCE.md`. M3 remains unstarted pending final
M2 acceptance, including browser usability review.

## M2.10 — Inventory-first correction (owner clarification, 2026-10-17)

- [x] Default compact dashboard, column sorts/filters, optional detail view.
- [x] H2 persisted roots/observations/lifecycle/notes/tasks; asynchronous startup,
  periodic and manual refresh. Newly discovered projects require confirmation.
- [x] Repository-level grouping, non-Git projects, bounded module/Node declarations.
- [x] Automatic read-only remote Git checks and local working-tree evidence.
- [x] Explicit public-registry comparison of declared Maven/Angular/Bootstrap versions.
- [ ] Owner validates the revised dashboard with a real project portfolio.
- [ ] Richer effective Maven resolution/support lifecycle policy (not claimed here).

M3 remains paused until this revised product target is validated. See ADR-020/021
and M2.10-VERIFICATION.md for executed checks and actual limitations.
