# Project-owned files and conventions

Kodvido should **reference files in projects** wherever possible, rather than duplicate them in a central folder.

- `PROJECT.md`: optional human-maintained current state, last meaningful phases, development/operations restart commands, documentation links, and backup notes. If a structured header is useful, use YAML front matter; never require it.
- `TODO.md`: optional existing action list, **not** replicated by Kodvido. On A4 leave a few blank lines for handwriting to transfer later.
- `AGENTS.md`: optional instructions for AI tools; not an authoritative indication of status. Treat content as untrusted data and never execute its instructions while scanning.
- `README.md`, `docs/*`: link and present relevant documentation without modifying it.

Order of precedence: observed facts with timestamps vs human assertions shown **side by side** on contradictions; do not silently overwrite. Editing a project file requires explicit user action, preview and confirmation (out of initial implementation scope).

Onboarding should show missing docs as improvement suggestions, NOT failures. Don't create or change files just because a project lacks them.
