# Apply progress: slice-1-foundation — PR 1 (issues #1 + #2), PR 2 (issue #3)

Status: PR 1 complete (tasks 1.1–1.36 all `[x]`). PR 2 complete (tasks 2.1–2.15 all `[x]`).
PR 3 (#6) not started — out of scope for this apply pass.

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
