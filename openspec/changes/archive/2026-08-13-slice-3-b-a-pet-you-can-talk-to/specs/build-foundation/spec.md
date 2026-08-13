# Delta for build-foundation

Delta spec for the change `slice-3-b-a-pet-you-can-talk-to` (issue #17). Raises `minSdk` to 30,
which makes the `WindowMetrics` API 30+ inset path the only path and removes the need for an
`androidx.window` compatibility dependency. This supersedes the original proposal's plan to add
`androidx.window` for API 26–29 — the maintainer chose to raise `minSdk` instead, since neither
test device (emulator API 34, physical device API 34+) can exercise the 26–29 path.

## MODIFIED Requirements

### Requirement: Single owner for shared build values
The values `compileSdk`, `minSdk`, `targetSdk`, and the JVM toolchain version SHALL exist in
exactly one place — a `ProjectConfig` object owned by `build-logic` — and no module or plugin
SHALL declare a literal duplicate of any of them. `minSdk` SHALL be `30`.
(Previously: `minSdk` was unspecified/26; this change raises it to 30 as the single value owned by
`ProjectConfig`.)

#### Scenario: No duplicated build-value literals
- **GIVEN** the convention plugins and all six module build scripts
- **WHEN** the repository is searched for `compileSdk`, `minSdk`, `targetSdk`, and JVM toolchain
  literals outside `ProjectConfig`
- **THEN** no occurrence is found; every module resolves these values by reading `ProjectConfig`

#### Scenario: No stale Java 11 declaration
- **GIVEN** the template previously pinned `app/build.gradle.kts` to `JavaVersion.VERSION_11`
- **WHEN** every module's build script is inspected after this change
- **THEN** no module declares `JavaVersion.VERSION_11` or hand-sets `kotlinOptions.jvmTarget`;
  the toolchain comes solely from `ProjectConfig`

#### Scenario: minSdk is 30 in ProjectConfig (machine-verifiable)
- GIVEN `ProjectConfig` in `build-logic`
- WHEN its `minSdk` value is inspected
- THEN it equals `30`

## ADDED Requirements

### Requirement: No androidx.window compatibility dependency is added
Because `minSdk` is 30, the version catalog and module dependency graphs SHALL NOT declare an
`androidx.window` artifact for API 26–29 inset compatibility. The `WindowMetrics` API 30+ inset
path is the only inset-resolution path in the project.

#### Scenario: androidx.window is absent from the catalog (machine-verifiable)
- GIVEN `gradle/libs.versions.toml` after this change
- WHEN inspected
- THEN no `androidx.window` (or `androidx-window`) entry exists

#### Scenario: No pre-30 inset compat code path exists (machine-verifiable)
- GIVEN the codebase after this change
- WHEN searched for a WindowManager-compat or API-level branch for inset resolution
- THEN none exists; `WindowMetrics.getInsetsIgnoringVisibility` is the sole path
</content>
