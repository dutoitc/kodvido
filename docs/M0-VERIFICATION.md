# M0 verification

The original supplied bootstrap (local commit `9fc2e38`) was tested unchanged with
Temurin Java 21.0.12.1 and Maven 3.9.9 on Linux on 2026-10-11.

Command: `mvn -B --no-transfer-progress verify`.
Result: BUILD SUCCESS, all six modules compiled/packaged, 2 tests passed.
No scaffold compilation correction was needed.

The execution environment initially had Java 17 and no Maven. Java 21 and Maven
were downloaded to a temporary toolchain directory; Maven used the environment's
proxy and trusted CA. Those environment-specific settings are not part of Kodvido.

CI now verifies Java 21 on Ubuntu and Windows, enforces the configured coverage
thresholds, and retains test/coverage reports. Both jobs passed on 2026-10-11
for commit `60c257296184c80a375d8abfc587302b9e8e3068`; see VERIFICATION.md.
