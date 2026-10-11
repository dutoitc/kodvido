# Implement M1 local discovery and catalogue

Kodvido's bootstrap had no end-to-end workflow for reviewing discovered projects
or maintaining a local catalogue. This change adds read-only Maven/Python discovery,
explicit candidate confirmation, transactional H2/Flyway storage and a minimal
local web interface. Declared Maven modules are grouped by default and remain
individually selectable. Relocation preserves identity; removal changes only the
catalogue. Core modules remain independent from Spring and presentation.

Local requests are protected by CSRF, a Host allowlist and escaped templates.
Scans avoid links/junctions, exclude generated directories and report partial
results. POM parsing disables external entities and never executes Maven/scripts.

Validation: Java 21/Linux `mvn clean verify` passes with 27 executed tests and
one platform-specific test skipped on each of Ubuntu and Windows. Packaged-JAR HTTP smoke/restart test passes.
JaCoCo thresholds pass; exact figures are in docs/VERIFICATION.md.

**Ready for review:** Ubuntu and Windows GitHub Actions passed, including the
native Windows junction test. Windows 8.3-path test assertions were corrected and
equivalent-root deduplication is covered. Public visibility was authorized by the
owner. No M2/M3 implementation or licence choice is included.
