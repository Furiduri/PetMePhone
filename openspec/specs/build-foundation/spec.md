# build-foundation

Delta spec for the change `slice-1-foundation` (issues #1, #2, #3). Defines the module graph,
convention-plugin ownership of shared build values, version-catalog alignment rules, and
test-source-set layout that the rest of the build must obey after this change lands.

## ADDED Requirements

### Requirement: Six-module graph with fixed identities
The project SHALL consist of exactly six Gradle modules — `:app`, `:core:domain`, `:core:data`,
`:core:designsystem`, `:feature:overlay`, `:feature:tasks` — no more, no fewer, with no other
module declared in `settings.gradle.kts`.

#### Scenario: Listing project modules
- **GIVEN** a clean checkout of the repository
- **WHEN** `./gradlew projects` runs
- **THEN** the output lists exactly the six modules above, correctly nested, and no others

### Requirement: Domain layer is Android-free, enforced by the build
`:core:domain` SHALL apply `org.jetbrains.kotlin.jvm` and MUST NOT resolve any `androidx.*` or
`android.*` artifact in any dependency configuration. This isolation MUST be a property of the
module's applied plugin and its declared dependencies, not of reviewer discipline or a naming
convention.

#### Scenario: Domain dependency report is Android-free
- **GIVEN** the six-module graph exists
- **WHEN** `./gradlew :core:domain:dependencies` runs
- **THEN** zero `androidx.*` or `android.*` artifacts appear in any resolved configuration

#### Scenario: Domain compiles without the Android SDK
- **GIVEN** `:core:domain` applies `org.jetbrains.kotlin.jvm` and declares no `android {}` block
- **WHEN** `./gradlew :core:domain:compileKotlin` runs
- **THEN** the module compiles successfully with no Android SDK on its compile classpath

### Requirement: Explicit module identity
Every Android module SHALL declare an explicit `namespace`, and every Android library module
SHALL additionally declare `android.resourcePrefix`, so that no two modules can collide on a
generated resource identifier.

#### Scenario: Namespace and resource prefix present
- **GIVEN** any of `:app`, `:core:data`, `:core:designsystem`, `:feature:overlay`,
  `:feature:tasks`
- **WHEN** its `build.gradle.kts` (or the convention plugin it applies) is inspected
- **THEN** an explicit `namespace` is present, and library modules additionally declare
  `android.resourcePrefix`

### Requirement: Single owner for shared build values
The values `compileSdk`, `minSdk`, `targetSdk`, and the JVM toolchain version SHALL exist in
exactly one place — a `ProjectConfig` object owned by `build-logic` — and no module or plugin
SHALL declare a literal duplicate of any of them.

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

### Requirement: Module scripts are plugin application plus dependencies only
Every module `build.gradle.kts` SHALL be reducible to a `plugins {}` block plus that module's own
`dependencies {}` block, with no AGP or Kotlin block-level configuration left inline.

**Exception, and the only one:** an `android {}` block containing nothing but `namespace`.
AGP exposes no way to set a module's namespace outside that block, and the "Explicit module
identity" requirement above mandates a per-module namespace. Forbidding the block outright would
make the two requirements jointly unsatisfiable. Anything beyond `namespace` inside that block
is a violation.

#### Scenario: Module script shape after convention-plugin extraction
- **GIVEN** the six convention plugins exist in `build-logic`
- **WHEN** any of the six module `build.gradle.kts` files is read
- **THEN** its content is limited to a `plugins {}` block, a `dependencies {}` block, and at most
  an `android { namespace = "..." }` block carrying nothing else; no `kotlin {}` block, no
  compiler-option block, and no other AGP configuration appears in the module script itself

### Requirement: Included build resolves before the root build's plugin blocks
`build-logic` SHALL be wired as a Gradle included build via `pluginManagement { includeBuild(...) }`
declared as the first block of the root `settings.gradle.kts`, so that the six convention plugin
ids resolve for every consuming module.

#### Scenario: Plugin ids resolve for consuming modules
- **GIVEN** `pluginManagement { includeBuild("build-logic") }` is the first block in root
  `settings.gradle.kts`
- **WHEN** any module applies one of the six `com.petmephone.*` plugin ids
- **THEN** the build configures successfully with no `Plugin [id: '...'] was not found` error

### Requirement: No Compose artifact covered by the BOM carries an explicit version
Every Compose artifact covered by the Compose BOM (including but not limited to `ui`,
`ui-graphics`, `foundation`, `material3`, `ui-tooling-preview`) SHALL be declared in
`gradle/libs.versions.toml` without a `version.ref`, and the BOM itself SHALL be applied
separately as a platform dependency, never inside a version-pinned bundle.

#### Scenario: Catalog has no double-pinned Compose artifact
- **GIVEN** `gradle/libs.versions.toml` after this change
- **WHEN** the Compose-related entries are inspected
- **THEN** no BOM-covered artifact carries a `version.ref`, and the BOM entry is applied via
  `implementation(platform(...))` rather than a `[bundles]` entry

### Requirement: KSP version is coupled to the Kotlin version
The `ksp` version in the catalog SHALL match the `kotlin` version exactly
(`<kotlin-version>-<ksp-patch>`), with an adjacent comment stating the coupling, and the
`kotlin-compose` plugin entry SHALL use `version.ref = "kotlin"` rather than a literal.

#### Scenario: KSP and Kotlin versions stay atomic
- **GIVEN** `gradle/libs.versions.toml` after this change
- **WHEN** the `kotlin`, `ksp`, and `kotlin-compose` entries are inspected
- **THEN** the `ksp` version's Kotlin-version prefix equals the `kotlin` version exactly, and
  `kotlin-compose` references `version.ref = "kotlin"`

### Requirement: Test source sets exist with zero tests
Each module SHALL have the test source sets and test libraries assigned to it wired and
resolvable, with zero tests written in this change, and the whole graph SHALL run through the
test task without failure.

#### Scenario: Zero-test build is a pass
- **GIVEN** test source sets wired per module (`:core:domain` and `:core:data` get `test`;
  `:feature:overlay` and `:feature:tasks` get `test` and `androidTest`; `:core:designsystem` and
  `:app` get `androidTest`)
- **WHEN** `./gradlew test` runs across the graph
- **THEN** every test task completes successfully with zero tests executed

#### Scenario: Instrumentation source sets compile
- **GIVEN** `androidTest` source sets exist on `:core:designsystem`, `:feature:overlay`,
  `:feature:tasks`, and `:app`
- **WHEN** `./gradlew :feature:overlay:assembleDebugAndroidTest` (and the equivalent task for the
  other modules with `androidTest`) runs
- **THEN** the instrumentation source set compiles successfully

### Requirement: Configuration cache reuse survives the whole slice
`./gradlew build --configuration-cache`, run twice in a row, SHALL succeed both times, and the
second run SHALL report configuration-cache reuse rather than recalculating the task graph. This
MUST hold after each of #1, #2, #3, and #6 lands, not only at the end of the slice.

#### Scenario: Second consecutive build reuses the configuration cache
- **GIVEN** the module graph, convention plugins, and completed version catalog exist
- **WHEN** `./gradlew build --configuration-cache` runs twice in immediate succession
- **THEN** the first run succeeds and the second run succeeds while reporting cache reuse, not
  cache recalculation

### Requirement: The application module remains installable throughout
`:app:assembleDebug` SHALL produce an APK that installs and launches without crashing at every
step of this change, even while intermediate steps leave the app visually blank.

#### Scenario: Debug APK installs and launches
- **GIVEN** the state of the repository after any of #1, #2, #3, or #6
- **WHEN** `./gradlew :app:assembleDebug` runs and the resulting APK is installed on a device or
  emulator
- **THEN** the APK installs and the app launches without crashing

## ADDED Requirements (slice-3)

### Requirement: room-testing is a version-catalog entry, not an ad hoc literal
The `room-testing` artifact SHALL be declared in `gradle/libs.versions.toml`, coupled to the same
Room version already used elsewhere, and consumed by `:core:data`'s test source set through the
catalog reference — never as a hand-written literal coordinate in a module's `build.gradle.kts`.

#### Scenario: room-testing resolves through the catalog
- GIVEN `gradle/libs.versions.toml` after this change
- WHEN it is inspected
- THEN a `room-testing` entry exists referencing the same `version.ref` as the other Room
  artifacts

#### Scenario: No literal room-testing coordinate in a module script
- GIVEN `:core:data`'s `build.gradle.kts`
- WHEN its `dependencies {}` block is inspected
- THEN `room-testing` is referenced via `libs.room.testing` (or equivalent catalog alias), with no
  hardcoded group:artifact:version string
