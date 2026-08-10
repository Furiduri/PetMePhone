# Apply progress: slice-1-foundation — PR 1 (issues #1 + #2), PR 2 (issue #3), PR 3 (issue #6)

Status: PR 1 complete (tasks 1.1–1.36 all `[x]`). PR 2 complete (tasks 2.1–2.15 all `[x]`).
PR 3 complete (tasks 3.1–3.17 all `[x]`, plus carried-forward items CF-1..CF-4 all `[x]`).
Task 3.15 (previously left open, honestly, as `[ ]`) is now also `[x]` — see "PR 3 addendum" below.

## PR 3 addendum — task 3.15 closed with a Robolectric JVM test

Task 3.15 ("Single WorkManager instance at cold start") was previously left `[ ]` on record,
because the only prior justification for marking it done refuted itself (task 3.4 removes exactly
the manifest path that justification relied on), and instrumented tests genuinely cannot reach
`PetMePhoneApplication` (`CustomTestRunner` substitutes `HiltTestApplication`). That withdrawal was
correct and is not revisited here.

### What was built

- `app/src/test/java/com/gcatcode/petmephone/PetMePhoneApplicationWorkManagerTest.kt` — a
  `RobolectricTestRunner` JVM test, `@Config(application = PetMePhoneApplication::class, sdk =
  [36])`. It instantiates the real, un-substituted `PetMePhoneApplication` (real
  `@HiltAndroidApp`-generated component, real Room/DataStore bindings via `DataModule`) and asserts
  `WorkManager.getInstance(application)` does not throw, proving the on-demand-initialisation path
  WorkManager has provided since 2.6 for `Application`s implementing `Configuration.Provider`.
  `sdk = 36` is required because Robolectric 4.16.1 does not yet ship SDK 37 shadows (the project's
  real `compileSdk`/`targetSdk`); this pins the emulated platform revision only, not what is
  asserted.
- `build-logic/convention/src/main/kotlin/com/petmephone/AndroidApplicationConventionPlugin.kt` —
  added `testOptions { unitTests.isIncludeAndroidResources = true }` and
  `tasks.withType<Test>().configureEach { failOnNoDiscoveredTests.set(false) }`, mirroring
  `AndroidLibraryConventionPlugin`'s existing rule (`:app` previously had neither, since it had no
  unit-test wiring at all before this task).
- `app/build.gradle.kts` — added `testImplementation(libs.junit)` and
  `testImplementation(libs.robolectric)`. No other line changed; the module script still carries
  only `plugins {}`/`android { namespace }`/`dependencies {}`, and `testInstrumentationRunner`
  remains solely owned by `AndroidApplicationConventionPlugin`.

### Why a Robolectric test genuinely proves the scenario (not a stand-in)

- The `Application` under test is the real production class, not `HiltTestApplication` or any
  other substitute — Robolectric's `@Config(application = ...)` is what makes this possible; no
  Hilt test infrastructure is involved.
- The Hilt graph Robolectric builds is the real, KSP-generated production graph (Room database
  creation, DataStore instance creation via `DataModule`'s `@Provides` all execute for real under
  Robolectric's shadowed `Context`).
- `WorkManager.getInstance` is called exactly as the scenario specifies, against the real
  `Configuration.Provider` implementation, with no manual `Configuration.Builder()` set up by the
  test itself (unlike the instrumented placeholder-worker tests, which do build their own).

### Verified

- `./gradlew :app:testDebugUnitTest` — ran, 1 test, 0 failures (confirmed by reading
  `app/build/test-results/testDebugUnitTest/TEST-com.gcatcode.petmephone.PetMePhoneApplicationWorkManagerTest.xml`:
  `tests="1" failures="0"`, test case time ~34s, not a NO-SOURCE/zero-test false green).
- `./gradlew test` — still green graph-wide (`BUILD SUCCESSFUL`, all modules).
- `./gradlew :app:connectedDebugAndroidTest` on `emulator-5554` (`Pixel_10(AVD)`, API 37) — still
  "Finished 2 tests on Pixel_10(AVD)", both passed, unaffected by this change.
- `./gradlew :app:assembleDebug --configuration-cache` — `BUILD SUCCESSFUL`, no Hilt/KSP-
  attributable configuration-cache warning.
- Cold launch: `adb install -r app-debug.apk`, `adb shell am force-stop
  com.gcatcode.petmephone`, `adb shell am start -W com.gcatcode.petmephone/.MainActivity` reports
  `LaunchState: COLD`, `Status: ok`; `adb logcat -d` grep for FATAL/AndroidRuntime/Exception tied to
  the package returns nothing.

### Commit

`test(app): add Robolectric test proving WorkManager cold-start init (#6)` — plus
`docs(sdd): close task 3.15 in slice-1-foundation`.

## PR 3 (issue #6) — Hilt object graph, WorkManager factory, four carried-forward items

### What was built

- `PetMePhoneApplication` (`app/src/main/java/com/gcatcode/petmephone/PetMePhoneApplication.kt`):
  `@HiltAndroidApp`, `Configuration.Provider`, injects `HiltWorkerFactory`, overrides the Kotlin
  `val workManagerConfiguration` property. Registered as `android:name` in the manifest.
- Manifest override: an explicit `<provider android:name="androidx.startup.InitializationProvider"
  ... tools:node="merge">` with a nested `<meta-data android:name="androidx.work.WorkManagerInitializer"
  ... tools:node="remove">`. Verified absent from the **merged** manifest at
  `app/build/intermediates/merged_manifest/debug/processDebugMainManifest/AndroidManifest.xml`.
- `MainActivity` switched from `android.app.Activity` (PR 1) to `androidx.activity.ComponentActivity`
  and annotated `@AndroidEntryPoint` — Hilt's KSP processor rejects `@AndroidEntryPoint` on a plain
  `Activity` ("must be a subclass of androidx.activity.ComponentActivity"). Pulled in a new,
  non-Compose `androidx-activity` (`activity-ktx:1.13.0`) catalog entry, consumed only by `:app`.
- Minimal but real Hilt graph to prove the wiring, per the dependency-injection spec:
  - `:core:domain`: `PetProfileRepository` interface (`isOnboarded: Flow<Boolean>`,
    `setOnboarded(Boolean)`). `:core:domain` gained its first non-test dependency,
    `api(libs.kotlinx.coroutines.core)`, per design.md's "api only for Flow-returning domain
    interfaces" rule.
  - `:core:data`: `PetProfileRepositoryImpl` (DataStore-backed), `AppDatabase`/`PlaceholderEntity`/
    `PlaceholderDao` (Room requires at least one entity; no real entity exists yet in this slice's
    scope, so this is a documented scaffold), `DataModule` (`@Provides` for `AppDatabase` and
    `DataStore<Preferences>`), `BindingsModule` (`@Binds` for the repository).
  - New `:core:data` dependency: `androidx.datastore:datastore-preferences`.
- Placeholder `@HiltWorker` split across two classes in `app/src/androidTest`, never shipped
  `main`, per the recorded assumption:
  - `PlaceholderHiltWorker` — `@HiltWorker`, `@AssistedInject` constructor injecting
    `PetProfileRepository`, returns `Result.success()` with an output-data flag proving the
    dependency was live (not just non-null by type).
  - `PlaceholderWorkerWithoutHiltAnnotation` — the same shape with `@HiltWorker` deliberately
    removed; compiles (plain `@AssistedInject` codegen doesn't need the Hilt annotation), but
    `HiltWorkerFactory` never registers it, so WorkManager's default reflective factory fails at
    execution time.
  - `CustomTestRunner` substitutes `HiltTestApplication` for instrumented tests; wired as
    `testInstrumentationRunner` in `app/build.gradle.kts`'s `defaultConfig`.
  - `PlaceholderHiltWorkerTest` and `PlaceholderWorkerWithoutHiltAnnotationTest` — `@HiltAndroidTest`
    + `HiltAndroidRule`, drive both workers via `WorkManagerTestInitHelper.initializeTestWorkManager`
    with a `SynchronousExecutor`, assert `WorkInfo.State` accordingly. Both new catalog entries
    consumed only by `androidTestImplementation`: `hilt-android-testing`, `androidx-work-testing`
    (plus `kspAndroidTest` for `hilt-android-compiler` and `androidx-hilt-compiler`).

### Four carried-forward items — all discharged in this PR

1. **Split `hilt-work` out of `.hilt`.** New plugin `com.petmephone.android.hilt.work`
   (`AndroidHiltWorkConventionPlugin`), registered in `build-logic/convention/build.gradle.kts`,
   applied only to `app/build.gradle.kts`. `com.petmephone.android.hilt` now only carries
   `hilt-android` + `hilt-android-compiler`; `hilt-work`/`androidx.hilt:hilt-compiler`/
   `work-runtime-ktx` moved to the new plugin. `work = "2.11.2"` stays the pinned floor (unchanged
   catalog entry, same PR 1 rationale — `hilt-work`'s own floor is `work-runtime 2.3.4`, which
   crashes `ForceStopRunnable` on API 31+).
2. **`testOptions { unitTests.isIncludeAndroidResources = true }`.** Added to
   `com.petmephone.android.library` (natural owner — every Android library module applies it,
   including `:core:data`, the sole Robolectric consumer so far). This is a genuine `android {}`
   block addition, not a module-script literal, so it does not reopen the PR 1 namespace/spec
   tension.
   **This surfaced a second latent defect**, found only by actually running the build (not by
   reading): once this flag is on, AGP adds the merged-resources jar to the unit-test classpath,
   and Gradle's bundled JUnit Platform launcher then treats that populated classpath root as
   containing discoverable tests — failing `:feature:overlay:testDebugUnitTest` and
   `:feature:tasks:testDebugUnitTest` with "did not discover any tests to execute", even though
   zero tests is this slice's explicit, designed invariant (design.md: "Zero tests but the
   infrastructure works"). Fixed in the same plugin: `tasks.withType<Test>().configureEach {
   failOnNoDiscoveredTests.set(false) }`, with the causal chain recorded inline as a comment, since
   the property's own Gradle-suggested escape hatch is exactly this and it is not otherwise
   discoverable from the error message alone.
3. **Pruned `androidx-lifecycle-runtime-ktx` and `androidx-activity-compose`.** Confirmed zero
   consumers repo-wide (`rg` for both the version keys and the library aliases) before removal.
   `activity-compose` was never reintroduced: task 3.5 (`MainActivity` → `ComponentActivity`) was
   satisfied with the plain, non-Compose `androidx.activity:activity-ktx` artifact instead — new
   catalog entry `androidx-activity`, version `1.13.0` (resolved from `dl.google.com`
   `maven-metadata.xml`, latest stable, same method as prior version resolutions), consumed only by
   `:app`. The `.compose` convention plugin's "does not add Activity-specific artifacts" rule is
   therefore still intact; `MainActivity`'s Activity-artifact dependency lives in `app/build.gradle.kts`
   directly, not in the shared plugin.
4. **`design.md`'s test-infrastructure table corrected** to list `androidx-junit` and
   `ui-test-manifest` for `:core:designsystem` and `:feature:overlay`/`:feature:tasks`, matching
   PR 2's verified reality (both were already load-bearing additions recorded in PR 2's own
   apply-progress entry, just not reflected in the table itself).

### Verified

- `./gradlew :app:assembleDebug` — succeeds (initially failed with "Activities annotated with
  @AndroidEntryPoint must be a subclass of androidx.activity.ComponentActivity" until `MainActivity`
  was switched, see deviation above).
- Merged manifest inspection (`app/build/intermediates/merged_manifest/debug/processDebugMainManifest/AndroidManifest.xml`)
  — no `WorkManagerInitializer` meta-data entry present.
- `./gradlew build --configuration-cache`, run twice after deleting `.gradle/configuration-cache`
  first: first run `BUILD SUCCESSFUL` (485 tasks), second run reused the configuration cache
  ("Configuration cache entry reused", 481 tasks, 476 up-to-date).
- `./gradlew test` — green graph-wide, `:app:test`/`:core:data:test`/`:feature:*:test` all
  `UP-TO-DATE`/`NO-SOURCE`/zero tests executed. (This required the CF-2 fix above — the raw
  `isIncludeAndroidResources = true` addition broke it first.)
- `./gradlew :app:assembleDebug --configuration-cache` (after deleting the cache) — no Hilt- or
  KSP-attributable configuration-cache warning; only the pre-existing `android.disallowKotlinSourceSets=false`
  experimental notice from PR 1.
- `./gradlew :app:connectedDebugAndroidTest` on `emulator-5554` (`Pixel_10(AVD)`, API 37) —
  "Finished 2 tests on Pixel_10(AVD)", both passed: `PlaceholderHiltWorkerTest` (worker succeeds,
  injected dependency proven live via output data) and `PlaceholderWorkerWithoutHiltAnnotationTest`
  (worker does not reach `SUCCEEDED` without `@HiltWorker`).
- On-device install + launch: `adb install -r app-debug.apk` succeeds, `am start
  com.gcatcode.petmephone/.MainActivity` launches, crash buffer (`adb logcat -d -b crash`) is
  empty, `pidof com.gcatcode.petmephone` returns a stable PID after a 4-second wait.

### Deviations / notes for verify

- **`MainActivity` changed from `android.app.Activity` to `androidx.activity.ComponentActivity`.**
  Not anticipated by task 3.5's literal text ("Create minimal `MainActivity` annotated
  `@AndroidEntryPoint`") or by PR 1's apply-progress record (which explicitly reduced it to a blank
  `android.app.Activity`). Hilt's KSP processor hard-requires `ComponentActivity` for
  `@AndroidEntryPoint` — this is not a design choice, it is a compile-time constraint discovered by
  running the build, not by reading. Required adding a plain `androidx.activity:activity-ktx`
  dependency (see carried-forward item 3 above).
- **Repository interfaces/bindings are minimal scaffolding, not real domain modeling.** `PetProfileRepository`,
  `AppDatabase`, and the two `@Module` objects exist only to give tasks 3.6/3.7 something concrete
  to bind — the spec's own scope boundary states "`PetOverlayService`, workers, and the screen
  receiver do not exist yet and are out of scope for this spec", and no other spec or design
  artifact defines the app's actual domain model yet. This scaffolding is expected to be replaced
  or extended by the feature slices that follow, not treated as final domain shape.
- **`kotlinx-coroutines-core` added as a new, non-test catalog entry** (`api` dependency of
  `:core:domain`), needed because `PetProfileRepository.isOnboarded: Flow<Boolean>` is the first
  non-test use of `Flow` in the domain layer. Previously only `kotlinx-coroutines-test` existed
  (test-only, from PR 2).
- Tasks 3.10 (document the Compose-without-Activity and screen-receiver decisions) is satisfied by
  design.md's pre-existing "Architecture decisions" table, not by new prose in this PR — no
  `PetOverlayService`/receiver code was written, matching the task's explicit scope limit.

### Commits (in order, on top of PR 2)

1. `feat(build): split hilt-work into its own convention plugin (#6)` — carried-forward item 1.
2. `feat(build): include Android resources in Robolectric unit tests, fix zero-test discovery (#6)`
   — carried-forward item 2, both the addition and its fix in one commit (the fix was required to
   land the addition safely).
3. `chore(build): prune unused activity/lifecycle catalog entries, add plain activity artifact (#6)`
   — carried-forward item 3.
4. `docs(sdd): correct design.md test-infrastructure table (#6)` — carried-forward item 4.
5. `feat(app): wire Hilt object graph, HiltWorkerFactory, and Room/DataStore bindings (#6)` — the
   Hilt graph itself: `PetMePhoneApplication`, `MainActivity`, `:core:domain`/`:core:data`
   repository + bindings.
6. `test(app): add instrumented placeholder @HiltWorker proving factory wiring (#6)` — the
   `androidTest`-only worker pair and their tests.
7. `docs(sdd): mark PR 3 tasks complete in slice-1-foundation` — tasks.md checkbox update.

(Exact commit hashes/messages may be squashed differently at commit time; see `git log`.)

## PR 2 (issue #3) — catalog completion + test infrastructure

### Commits (in order, on top of PR 1)

1. `feat(build): complete version catalog with test and feature libraries (#3)` — versions,
   libraries, bundles, `AndroidComposeConventionPlugin` refactor to `libs.bundles.compose.ui`.
2. `test(build): wire test and androidTest source sets across the module graph (#3)` — per-module
   `dependencies {}` additions for `test`/`androidTest`.
3. `docs(sdd): mark PR 2 tasks complete in slice-1-foundation` — tasks.md checkbox update.

(Exact commit hashes/messages may be squashed differently at commit time; see `git log`.)

### What was added to the catalog

`[versions]`: `datastore = "1.2.1"`, `lottieCompose = "6.7.1"`, `dmfsLibRecur = "0.17.1"`,
`kotlinxCoroutinesTest = "1.11.0"`, `turbine = "1.2.1"`, `robolectric = "4.16.1"` (latest
**stable**, not the `4.17-beta-2` "release" tag Maven metadata reports), `mockk = "1.14.11"`.
Resolved from `repo1.maven.org` / `dl.google.com` `maven-metadata.xml`, same method as PR 1's
Hilt/Room/KSP resolution — not guessed.

`[libraries]`: `androidx-datastore-preferences`, `lottie-compose`, `dmfs-lib-recur`,
`kotlinx-coroutines-test`, `turbine`, `robolectric`, `mockk`, `mockk-android`.

`[bundles]`: `compose-ui` (`ui`, `ui-graphics`, `ui-tooling-preview`, `material3` — the four
BOM-covered artifacts `AndroidComposeConventionPlugin` was already declaring individually) and
`compose-test` (`ui-test-junit4`). `AndroidComposeConventionPlugin` was refactored to iterate
`libs.findBundle("compose-ui").get().get()` instead of four separate `dependencies.add` calls;
the BOM platform dependency and `ui-tooling` (debug-only) stay outside the bundle, unchanged.

Hilt, Room, ksp, WorkManager, and Compose BOM versions were **not** re-added — they were already
present in the catalog from PR 1's forward-resolution (see PR 1's apply-progress entry), and this
apply pass explicitly did not re-litigate them per the constraint note. `foundation` was not added
to `[libraries]` — nothing in this slice consumes it, and it wasn't present before this pass either.

### Test source sets wired (per design.md's table, task 2.6–2.10)

- `:core:domain` (`jvm.library`, `test` only) — `testImplementation(libs.kotlinx.coroutines.test)`,
  `testImplementation(libs.turbine)`. `junit` is already added by `JvmLibraryConventionPlugin`
  itself (from PR 1). No MockK, per design.md ("hand-written fakes").
- `:core:data` (`test` only) — `junit`, `robolectric`, `mockk`, `kotlinx-coroutines-test`, `turbine`.
- `:core:designsystem` (`androidTest` only) — `androidx-junit`, `libs.bundles.compose.test`,
  `debugImplementation(androidx-compose-ui-test-manifest)` (required at runtime by
  `createComposeRule()`, not explicitly named in design.md's table but functionally load-bearing).
- `:feature:overlay`, `:feature:tasks` (`test` + `androidTest`) — `junit` + `mockk` on `test`;
  `androidx-junit` + `mockk-android` + `libs.bundles.compose.test` + `ui-test-manifest`
  (`debugImplementation`) on `androidTest`.
- `:app` (`androidTest` smoke only) — `androidx-junit`, `androidx-espresso-core` (both were already
  in the catalog from the original template, unused until now).

No new source directories were created — none of the six modules writes a test yet (zero-tests
requirement); Gradle resolves and configures empty `test`/`androidTest` source sets without the
directory existing on disk.

### Verified

- `./gradlew test` — green across the graph, every unit test task reports `NO-SOURCE`, `test`
  aggregate task `UP-TO-DATE`/executed with zero tests run.
- `./gradlew :feature:overlay:assembleDebugAndroidTest`, `:feature:tasks:assembleDebugAndroidTest`,
  `:core:designsystem:assembleDebugAndroidTest`, `:app:assembleDebugAndroidTest` — all four compile
  and package (`BUILD SUCCESSFUL`, APKs produced).
- Catalog inspection: `rg "version.ref"` on the BOM-covered artifact lines returns no matches, only
  the unrelated `agp`-referencing `android-gradlePlugin` line matched the loose grep; `kotlin =
  "2.2.10"` / `ksp = "2.2.10-2.0.2"` — prefix matches exactly.
- `./gradlew build --configuration-cache`, run twice after deleting `.gradle/configuration-cache`
  first: first run `BUILD SUCCESSFUL` (435 tasks, cache stored), second run `BUILD SUCCESSFUL` in
  4s (431 tasks, 426 up-to-date) reporting "Configuration cache entry reused".
- `./gradlew :app:assembleDebug` — APK produced.
- On-device install + launch on `emulator-5554` (API 37): `adb install -r` succeeds, `am start`
  launches `MainActivity`, crash buffer (`adb logcat -d -b crash`) is empty, `pidof` returns a
  stable PID after a 4-second wait. No regression from PR 1's WorkManager fix.
- Repo-wide grep for `JavaVersion.VERSION_11` outside `build-logic`: zero occurrences (only spec/
  design/proposal prose mentions the string, no code).

### Deviations / notes for verify

- `robolectric`'s Maven `<release>` tag in `maven-metadata.xml` points at `4.17-beta-2`; the pinned
  version is `4.16.1`, the latest entry in the `<versions>` list without a `-beta`/`-rc`/`-alpha`
  suffix. Same reasoning applied to `datastore` (`1.2.1` chosen over `1.3.0-alpha10`).
- `AndroidComposeConventionPlugin`'s dependency-wiring loop was refactored (not just appended to)
  to consume the new `compose-ui` bundle, per design.md's plugin table already describing it as
  "Compose BOM + `compose-ui` bundle" even before this catalog-completion task ran. This is a
  behavior-preserving refactor: the same four artifacts are still added as `implementation`
  dependencies, just sourced from the bundle instead of four literal `findLibrary` calls.
- `ui-test-manifest` (`debugImplementation`) was added to every module with a compose `androidTest`
  source set even though design.md's test-infrastructure table only names "`compose.ui.test`" —
  without it, `createComposeRule()` throws at runtime in a real test. Recorded as a deliberate,
  spec-consistent addition (not a deviation from any stated rule), flagged for verify awareness
  since it's not literally itemized in the table.
- `datastore`, `lottieCompose`, and `dmfs-lib-recur` catalog entries are added but unconsumed by
  any module in this slice — they exist for a future PR outside slice 1's scope. This mirrors task
  2.2's literal wording ("Add `[versions]` entries ... DataStore, Lottie, `dmfs:lib-recur`").

## Commits (in order)

1. `feat(build): stand up six-module skeleton with duplicated scripts (#1)` — sub-group A.
2. `feat(build): extract build-logic convention plugins, remove duplication (#2)` — sub-group B.
3. `docs(sdd): mark PR 1 tasks complete in slice-1-foundation` — tasks.md checkbox update.

## Verified

- `./gradlew projects` — exactly six modules (`:app`, `:core:domain`, `:core:data`,
  `:core:designsystem`, `:feature:overlay`, `:feature:tasks`).
- `./gradlew :core:domain:dependencies` — zero `androidx.*`/`android.*` artifacts, before and
  after the convention-plugin extraction.
- `./gradlew :core:domain:compileKotlin` — succeeds, `NO-SOURCE` (empty module), no Android SDK
  needed.
- `./gradlew build --configuration-cache` — succeeds twice in a row at both sub-group A and
  sub-group B checkpoints; second run reports "Configuration cache entry reused" each time,
  including once Hilt/KSP/Room are in the graph.
- `./gradlew :app:assembleDebug` — produces `app/build/outputs/apk/debug/app-debug.apk` at both
  checkpoints.
- **On-device install and launch — VERIFIED during the verify re-run.** An emulator
  (`emulator-5554`, `sdk_gphone16k_x86_64`, API 37) became available after the apply phase ran.
  The APK installs, `MainActivity` displays, the process survives with a stable PID, and the crash
  buffer is empty.

  **This step found a real defect that every green build had hidden.** The first launch crashed:

  ```
  java.lang.IllegalArgumentException: Targeting S+ (version 31 and above) requires that one of
  FLAG_IMMUTABLE or FLAG_MUTABLE be specified when creating a PendingIntent
      at androidx.work.impl.utils.ForceStopRunnable.getPendingIntent(ForceStopRunnable.java:196)
  ```

  `androidx.hilt:hilt-work:1.4.0` floors `androidx.work:work-runtime` at 2.3.4, which predates the
  FLAG_IMMUTABLE requirement, and nothing in the graph raised it. Fixed in commit `42ddf8a` by
  pinning `work = "2.11.2"` and declaring `work-runtime-ktx` explicitly in
  `AndroidHiltConventionPlugin`, so the version is chosen rather than inherited. Resolution now
  reports `2.3.4 -> 2.11.2`.
- Repo-wide grep for `VERSION_11` / `kotlinOptions` / stray `compileSdk`/`minSdk`/`targetSdk`/
  `jvmToolchain` literals outside `build-logic` — none found in `app/core/feature`.
- Every module `build.gradle.kts` reduced to `plugins {}` + (namespace-only) `android {}` +
  `dependencies {}` — see Key Learning below on the literal-reading tension with the spec text.

## Environment findings (recorded in the PR 2 commit message, repeated here for search)

1. AGP 9.3.1 ships built-in Kotlin compilation. Applying the classic
   `org.jetbrains.kotlin.android` plugin fails outright against AGP 9.3.1's new extension types
   (`ApplicationExtensionImpl` cannot cast to the old `BaseExtension`). Built-in Kotlin must stay
   enabled; `org.jetbrains.kotlin.android` / `.jvm` are never applied to Android modules (only
   `:core:domain`, a pure-JVM module, applies `org.jetbrains.kotlin.jvm`).
2. Built-in Kotlin, in turn, refuses to let KSP register its generated source directories via the
   classic `kotlin.sourceSets` DSL ("Using kotlin.sourceSets DSL to add Kotlin sources is not
   allowed with built-in Kotlin"). Hilt and Room both depend on KSP. Fix: AGP's own error message
   names the escape hatch — `android.disallowKotlinSourceSets=false` in `gradle.properties`
   (documented there as experimental/deprecated-eventually, but functioning).
3. `LibraryExtension.defaultConfig` has no `targetSdk` property in AGP 9.3.1 (unresolved reference
   at compile time) — only `ApplicationExtension.defaultConfig` does. Library modules therefore
   set `minSdk` only.
4. The KSP patch matching Kotlin 2.2.10 was resolved from Maven Central's own metadata (not
   guessed): `ksp = "2.2.10-2.0.2"`. Hilt (`2.60.1`), Room (`2.8.4`), and `androidx.hilt`
   (`1.4.0`) were resolved the same way (`curl` against `repo1.maven.org` / `dl.google.com`
   `maven-metadata.xml`), ahead of #3's catalog-completion task, because #2's
   `android.hilt`/`android.room` plugins need real, resolvable versions to compile and to apply to
   `:core:data` (and `.hilt` to `:feature:overlay`, `:feature:tasks`, `:app`).

## Recorded interpretation (spec/task tension — not silently resolved)

`specs/build-foundation/spec.md` Requirement "Module scripts are plugin application plus
dependencies only" reads literally as "no `android {}` ... block appears in the module script
itself." `design.md`'s own task list (1.30) says namespace is "not owned by the plugin" and must
be set per module. Since AGP has no way to set `namespace` outside an `android {}` block, and
`design.md` is binding, five of six module scripts (all but `:core:domain`, which needs no
`android {}` block at all) carry a minimal `android { namespace = "..." }` block alongside
`plugins {}` and `dependencies {}`. `resourcePrefix` is NOT a literal anywhere — the
`android.library` convention plugin derives it from `project.path` (e.g. `:core:data` →
`core_data_`), which is what design.md's plugin table implies by listing `resourcePrefix` as
plugin-configured. This reading was necessary to proceed; it should be confirmed or corrected at
verify/review time.

## Risks / follow-ups for verify

- ~~On-device APK launch not verified — no device/emulator available.~~ **Resolved.** Verified on
  `emulator-5554` during the verify re-run, which exposed and led to the WorkManager fix above.
- **`MainActivity.kt` was NOT removed, contrary to task 1.1's original text.** It is the manifest
  `LAUNCHER` target, and deleting it makes the "APK launches without crashing" criterion
  unsatisfiable. It is reduced to a blank `android.app.Activity`. This deviation was missed by
  this document originally and caught at verify; task 1.1's text has been amended.
- **The JVM target comes from `compileOptions`, not a Kotlin toolchain block**, because AGP 9.3.1's
  built-in Kotlin compilation reads it from there. Recorded here because it is a deviation from the
  KGP-based approach issue #2 assumes.
- `AndroidHiltConventionPlugin` gives `hilt-work` and `work-runtime-ktx` to every module applying
  `.hilt`, though only `:app` will host workers. Verify judged this non-blocking — `:app` depends on
  all four modules, so the artifact lands in exactly one APK either way, and the declarations are
  `implementation` rather than `api`. It is imprecise enough to mislead, so split it in PR 3 (#6).
- The `android.disallowKotlinSourceSets=false` property is marked experimental by AGP 9.3.1 and
  will presumably need re-verification against future AGP releases; this is now a real dependency
  of the build (Hilt + Room), not a cosmetic choice.
- `ksp`, `hilt`, `hiltExt` (androidx.hilt), and `room` catalog versions were added ahead of #3's
  "complete the catalog" task, scoped strictly to what #2's convention plugins need to compile.
  PR 2 (#3) should treat these as already-resolved, not re-litigate them, unless a compatibility
  issue is found.
