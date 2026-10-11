# AGENTS.md — Kodvido agent instructions

## Mission & boundaries

Implement Kodvido incrementally following `docs/REQUIREMENTS.md` and `docs/ROADMAP.md`. Name is **Kodvido** exclusively; do not resurrect former working names. Communicate in French unless asked otherwise. Keep production code/docs in English for accessibility; UI may support French first.

## Development

- Java 21, Maven multimodule, Spring Boot in `kodvido-app` only; core modules must NOT depend on Spring or presentation.
- Split detection (read-only), engine (use cases and decisions), domain (immutable types/ports), persistence (H2/Flyway), reporting (standalone HTML/PDF), and app (REST + minimal server-side UI).
- Never scan recursively through symlinks/junctions by default; handle Windows/Linux roots and inaccessible directories gracefully.
- Explicitly distinguish MISSING, UNKNOWN, PARTIAL, UNSUPPORTED, FAILED and verified statuses where appropriate. Unknown backup is not safe backup.
- Do not overwrite `PROJECT.md`, `TODO.md`, `AGENTS.md` or source project files without explicit opt-in.
- Do not assume files with modification timestamps prove developer activity; exclude build/output/vendor directories and count files/day windows.
- Never execute discovered scripts by scan. Future actions need allowlist + preview + user confirmation; never auto-deploy.
- Never send files, path names, contents or secrets to remote providers by default. Issue templates for detector gaps must be sanitized before sharing.
- Respect report snapshot timestamp and clearly distinguish measured from declared facts.
- No Angular in initial milestones. Minimal server-rendered UI or static HTML with Spring Boot is sufficient.

## Workflow

1. Read docs; note contradictions as an ADR before changing scope.
2. Run `mvn -B verify`; fix scaffold compilation first.
3. Implement a **vertical slice** per milestone with tests, then summarize real results.
4. Each change must include meaningful tests, error/edge cases, and documentation updates.
5. Avoid giant unreviewable changes and speculative integrations. Prefer PRs per milestone.
6. On every completion report: implemented features, tests actually run, skipped tests, remaining blockers, files changed.

## Quality gates

- JUnit 5, AssertJ, integration tests with temp dirs/temporary Git repos as relevant.
- JaCoCo gate targets: engine/domain >=90% branches; detection >=85% lines; persistence/reporting >=80% lines once implemented. Avoid meaningless tests gaming coverage.
- GitHub Actions matrix: Ubuntu + Windows, Java 21, `mvn -B verify`.
- Sanitize input paths in logging and snapshot exports; no secret/token persistence.
- Destructive operations forbidden in V1. Removing a catalog item must never delete its source directory.
