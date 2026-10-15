# M2 acceptance — functional evidence and remaining visual review

The implemented M2 scope is covered by the following checks. This is not a claim of
exhaustive technology inference, verified backups or a completed visual acceptance.

| Requirement | Implementation / exercised checks | Limits |
|---|---|---|
| R07 project docs | Bounded live links, escaped text, missing/unsupported cases | No project-owned file editing |
| R08 activity | Git HEAD, bounded file windows, persisted interactions; separate dates | mtime/commit dates do not prove work |
| R09 detector outcomes | Explicit status, diagnostics, limitations and advice | Existing traversal/runtime restrictions |
| R10 stack/scripts | Root Java/Python declarations, shallow script-name inventory | No Maven resolution, Poetry or execution |
| R11 backup confidence | Four independent dated manual declarations, UNKNOWN default | No automated backup/restore verification |
| R12/R13 phases/restart | Manual phases, separate dev/ops notes, stale-save conflict handling | No full task tracker |
| R14 suggestions | Deterministic advice codes and UI, no automatic action | Bounded initial rule set |
| R18 enhancement requests | Allowlisted local Markdown drafts, CSRF and private-input exclusion | Generic draft, manual synthetic reproduction |
| R20 metadata recovery | Strict unversioned JSON, review/path mapping, transactional restore | Current development format only |
| Catalogue UX | Table, combined filters, sorting, pagination, bounded cached observations | Browser visual review still outstanding |

Unit/integration suites run on Java 21; exact OS-specific results and coverage are
in the increment verification documents and PR CI logs. The M2.9 packaged-JAR HTTP
smoke test additionally exercised the full scan/import/inspect/filter/export/remove/
restore chain on default port 8930 with unchanged synthetic source files.

Before final M2 acceptance, visually review the table on desktop and a narrow screen:
keyboard focus, horizontal table scrolling, expanded row actions, long names/paths,
empty results and filter controls. Automated browser setup was unavailable in the
current environment (failed Chromium download). This is a concrete outstanding check.

M3 has not started. Its first slice should be timestamped, privacy-safe standalone
HTML snapshots (catalogue/project), then the one-page A4 PDF and offline/page-count
checks. Keep those report artifacts separate from private JSON recovery files.
