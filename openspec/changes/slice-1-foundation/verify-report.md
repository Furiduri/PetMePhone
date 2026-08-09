```yaml
schema: gentle-ai.verify-result/v1
evidence_revision: sha256:a8468f8351d097c753591038d2af382d8b7a06420b57a600c4582ebd0cda8af3
verdict: fail
blockers: 2
critical_findings: 2
requirements: 18/20
scenarios: 23/25
test_command: "./gradlew test"
test_exit_code: 0
test_output_hash: sha256:aba820b0590e003f871f1d63075731836ba206ac32ca94edca8f83f4d2f6beeb
build_command: "./gradlew build --configuration-cache"
build_exit_code: 0
build_output_hash: sha256:80e04fd1a08815619bbb202f96ac48f1fdae4b49aeca7d1f85f9f428cb3b439a
```

# Verification Report — slice-1-foundation, PR 3 (issue #6)

HEAD at verification: `174c5c0`, branch `feat/slice-1-hilt-graph-and-workmanager-factory`.
Device: `emulator-5554` (`Pixel_10(AVD)`, API 37). PR 3 diff vs PR 2's branch: 681 insertions /
33 deletions across 30 files, 8 commits.

PR 1 and PR 2 are verified and closed; their records are preserved verbatim below. This run
re-checks them only through the regression list.

**Verdict: FAIL — 2 CRITICAL, 6 WARNING, 5 SUGGESTION.** Both CRITICALs are narrow and cheap to
close. The Hilt graph itself is correct, builds, and executes on a real device.

## Runtime evidence — all executed at `174c5c0`

| Command | Result |
|---|---|
| `./gradlew :app:assembleDebug` | BUILD SUCCESSFUL, exit 0. |
| `rm -rf .gradle/configuration-cache` then `./gradlew build --configuration-cache` | BUILD SUCCESSFUL in 43s. 485 actionable tasks, 9 executed. "**Configuration cache entry stored.**" Cold — cache directory deleted first. |
| `./gradlew build --configuration-cache` (2nd, immediate) | BUILD SUCCESSFUL in 8s. 481 tasks, 476 up-to-date. "**Configuration cache entry reused.**" |
| `./gradlew test` | BUILD SUCCESSFUL, exit 0. 152 actionable tasks, all up-to-date. Every unit-test task `NO-SOURCE`/`UP-TO-DATE`. Zero tests executed, zero failures. |
| `./gradlew :app:connectedDebugAndroidTest` | BUILD SUCCESSFUL. "Starting 2 tests on Pixel_10(AVD) - 17" / "**Finished 2 tests on Pixel_10(AVD) - 17**". Both passed. |
| Merged manifest inspection | `WorkManagerInitializer` **absent**. `EmojiCompatInitializer` (l.45), `ProcessLifecycleInitializer` (l.48), `ProfileInstallerInitializer` (l.51) **all still present** — the nested `<meta-data tools:node="remove">` on a `tools:node="merge"` provider removed only the WorkManager entry, exactly as design.md requires. |
| `adb install -r app-debug.apk` + `am start -n com.gcatcode.petmephone/.MainActivity` | `Success` / `Starting: Intent { … }`. |
| `adb logcat -d -b crash` (buffer cleared before install) | **0 lines. Empty crash buffer.** |
| `adb shell pidof com.gcatcode.petmephone` (after 5s) | `24647` — process alive. |
| `./gradlew :app:dependencies --configuration debugRuntimeClasspath` | `androidx.work:work-runtime-ktx:2.11.2`; `androidx.work:work-runtime:2.3.4 -> 2.11.2`. |

## Regression checks

| # | Check | Result | Evidence |
|---|---|---|---|
| 1 | `androidx.work` >= 2.11.2 on `:app` debug runtime | **PASS** | `2.3.4 -> 2.11.2` in the dependency report; `work = "2.11.2"` catalog pin and its FLAG_IMMUTABLE comment intact, now owned by the new `AndroidHiltWorkConventionPlugin`. App launches with an empty crash buffer. |
| 2 | No BOM-covered Compose artifact has `version.ref`; `ksp` prefix == `kotlin` | **PASS** | All seven `androidx.compose.*` entries declare `group`/`name` only. `kotlin = "2.2.10"`, `ksp = "2.2.10-2.0.2"`. |
| 3 | `android.disallowKotlinSourceSets=false` present | **PASS** | `gradle.properties`, comment and AGP doc link intact. |
| 4 | No Android module applies `org.jetbrains.kotlin.android` | **PASS** | Repo-wide search outside `openspec/`: zero hits. |
| 5 | `:core:domain` resolves zero `androidx.*`/`android.*` | **PASS, with a scope note** | `./gradlew :core:domain:dependencies` — zero matches. But the module did **not** gain only `javax.inject`: it gained `api(libs.kotlinx.coroutines.core)` (`kotlinx-coroutines-core:1.11.0`, pure JVM), and it gained **no** `javax.inject` dependency at all. See W1. |
| 6 | No build-value literals outside `build-logic` | **PASS** | Search for `compileSdk`, `minSdk`, `targetSdk`, `VERSION_11`, `kotlinOptions`, `jvmTarget` across `app`, `core`, `feature` `*.kts`: zero hits. |
| 7 | Module scripts carry at most an `android {}` block containing only `namespace` | **FAIL** | `app/build.gradle.kts` now carries `android { namespace = …; defaultConfig { testInstrumentationRunner = "com.gcatcode.petmephone.CustomTestRunner" } }`. See C1. |

Six of seven hold. Check 7 is a genuine regression introduced by this PR.

## Spec compliance — dependency-injection (9 requirements / 11 scenarios): 8/9, 10/11

| Requirement | Status | Evidence |
|---|---|---|
| Application is the single Hilt root and WorkManager configuration provider (2 scn) | **PARTIAL — 1/2** | scn "Application class shape" **PASS**: `PetMePhoneApplication` carries `@HiltAndroidApp`, implements `Configuration.Provider`, has `@Inject lateinit var workerFactory: HiltWorkerFactory`, and overrides `override val workManagerConfiguration: Configuration` — a Kotlin property, not a Java getter. scn "Single WorkManager instance at cold start" **UNTESTED** — see C2. |
| Default WorkManager initializer is removed from the merged manifest | **PASS** | Merged-manifest inspection above; the three sibling `androidx.startup` initializers survive. |
| MainActivity is a Hilt entry point | **PASS** | `@AndroidEntryPoint class MainActivity : ComponentActivity()`. |
| Domain layer stays free of Dagger/Hilt imports | **PASS (literal tension, W1)** | Searching `core/domain/` for `dagger` matches only a doc comment. `PetProfileRepository` imports `kotlinx.coroutines.flow.Flow` and no `javax.inject` type, which the requirement's "MUST import only `javax.inject` types" does not literally allow. |
| Hilt bindings live in `:core:data`, not `:core:domain` | **PASS** | `BindingsModule` (`@Module @InstallIn(SingletonComponent::class)`, `@Binds` only) and `DataModule` (`@Provides` only, for `AppDatabase` and `DataStore<Preferences>`), both under `core/data/src/main/kotlin/.../di/`. The `@Binds`/`@Provides` split matches the requirement exactly. |
| No service-scoped Hilt bindings exist | **PASS** | Repo-wide search for `ServiceScoped`/`ServiceComponent` outside `openspec/`: zero hits. |
| KSP is applied to every module declaring a Hilt annotation | **PASS** | `:core:data` via `.hilt` + `.room`; `:app` via `.hilt`, plus `kspAndroidTest(hilt-android-compiler)` and `kspAndroidTest(androidx-hilt-compiler)` for the `androidTest`-only `@HiltWorker`. `:app:kspDebugAndroidTestKotlin` ran and the worker executed. |
| A placeholder HiltWorker proves the factory wiring end-to-end (2 scn) | **PASS (2/2, negative scenario weak — W2)** | Both scenarios executed on `emulator-5554`. |
| Configuration cache tolerates Hilt and KSP | **PASS** | Cold `build --configuration-cache` twice with reuse; no Hilt/KSP-attributable warning. |

## Spec compliance — build-foundation (11 requirements / 14 scenarios): 10/11, 13/14

Ten requirements are unchanged from PR 2's verified 11/11. One regressed:

| Requirement | Status | Evidence |
|---|---|---|
| Module scripts are plugin application plus dependencies only | **FAIL (was PASS)** | See C1. |
| All other ten | PASS | Re-checked: six modules unchanged in `settings.gradle.kts`; `:core:domain:dependencies` Android-free; namespaces present, `resourcePrefix` still plugin-derived; zero build-value literals; `includeBuild` first; catalog rules hold; `./gradlew test` green with zero tests; `assembleDebugAndroidTest` compiles (`:app` ran on device); config cache reused cold; APK installs and launches with an empty crash buffer. |

## Task completeness — PR 3 (3.1–3.17 + CF-1..CF-4)

**19 of 21 claims are true against the tree. Two are not.**

| Task | Claim | Tree state | Verdict |
|---|---|---|---|
| 3.1 | `hilt-work` + separate `androidx.hilt:hilt-compiler` confirmed; split into `.hilt.work` | `androidx-hilt-work`/`androidx-hilt-compiler` at `hiltExt = "1.4.0"`, distinct from `hilt-android-compiler` at `hilt = "2.60.1"`; `AndroidHiltWorkConventionPlugin` exists and is registered | TRUE |
| 3.2 | `PetMePhoneApplication` with the Kotlin property override | file present, exact shape | TRUE |
| 3.3 | `android:name` registered | manifest `android:name=".PetMePhoneApplication"` | TRUE |
| 3.4 | `tools:node="remove"` on nested meta-data, `merge` on provider | manifest matches literally | TRUE |
| 3.5 | `@AndroidEntryPoint MainActivity`, switched to `ComponentActivity` | matches; `implementation(libs.androidx.activity)` = `activity-ktx:1.13.0` | TRUE |
| 3.6 | Domain interface, `javax.inject` only, no `dagger.*` | `PetProfileRepository` present; no `dagger.*`; **no `javax.inject` either** — the parenthetical admits this honestly | TRUE (as amended) |
| 3.7 | `@Binds`/`@Provides` modules in `:core:data` | `BindingsModule`, `DataModule`, `AppDatabase`, `PlaceholderEntity`/`Dao` all present | TRUE |
| 3.8 | KSP on every Hilt-annotated module | verified above | TRUE |
| 3.9 | No `@ServiceScoped`/`ServiceComponent` | search: zero | TRUE |
| 3.10 | Decisions recorded in design.md's table | design.md rows "Compose in the overlay" and "Screen receiver" present | TRUE |
| 3.11 | Placeholder `@HiltWorker` in `app/src/androidTest` | `PlaceholderHiltWorker` + `PlaceholderWorkerWithoutHiltAnnotation`, both `androidTest`-only; nothing in `main` | TRUE |
| 3.12 | Positive instrumented test ran and passed | re-run here: 2 tests finished, exit 0 | TRUE |
| 3.13 | Negative instrumented test ran and passed | re-run here; assertion is weaker than the task text implies — W2 | TRUE (weak) |
| 3.14 | Merged manifest has no `WorkManagerInitializer` | independently re-inspected | TRUE |
| 3.15 | Cold start `WorkManager.getInstance` throws no `IllegalStateException` | **FALSE** — no executed check covers this; the stated reasoning is self-contradictory. See C2. | **FALSE** |
| 3.16 | `assembleDebug --configuration-cache` clean of Hilt/KSP warnings | reproduced | TRUE |
| 3.17 | `connectedDebugAndroidTest` passes | reproduced: 2/2 | TRUE |
| CF-1 | `hilt-work` split into `.hilt.work`, applied only to `:app` | `AndroidHiltConventionPlugin` now carries only `hilt-android` + `hilt-android-compiler`; `hilt-work`/`androidx-hilt-compiler`/`work-runtime-ktx` moved; `:app` is the sole consumer of the id | TRUE |
| CF-2 | `isIncludeAndroidResources = true` + `failOnNoDiscoveredTests = false` in `.library` | both present with the causal comment | TRUE (see W3) |
| CF-3 | `androidx-lifecycle-runtime-ktx` and `androidx-activity-compose` pruned; `activity-ktx` added | both aliases and their version keys absent from the catalog; `androidx-activity = activity-ktx` present | TRUE |
| CF-4 | design.md test table corrected | table rows now name `androidx-junit` and `ui-test-manifest`, matching `core/designsystem` and `feature/*` scripts as they actually are | TRUE — the edit follows verified reality, not the reverse |

Two further claims outside the checklist are **not** true against the tree:

- The `:app` script's `defaultConfig` block is nowhere recorded as a deviation, although it breaks a
  spec requirement PR 1 and PR 2 both had to fight for (C1).
- `apply-progress.md`'s "Verified" bullet asserting the instrumented tests exercise
  `PetMePhoneApplication.workManagerConfiguration` is false (C2).

## Adjudications requested

### 1. `MainActivity`: `Activity` to `ComponentActivity` — the constraint is real; the requirement that forced it is the questionable part

**The claim is true, not convenient.** Dagger Hilt's `@AndroidEntryPoint` processor rejects
activities that are not `androidx.activity.ComponentActivity` subclasses; the generated
`Hilt_MainActivity` base class relies on `ComponentActivity`'s `getDefaultViewModelProviderFactory`
hook for `ActivityComponent`/`ViewModelComponent` integration, which `android.app.Activity` does not
have. The apply phase reports the exact processor message and the build failed until the switch —
and this run reproduces the working end state (`:app:assembleDebug`, exit 0, `@AndroidEntryPoint`
present). It is a compile-time constraint, not a design preference.

**But the honest reading is that the requirement, not the code, is the weak link.** `MainActivity`
injects nothing — its `onCreate` calls only `super`. `@AndroidEntryPoint` on it buys zero behaviour
today; its entire cost is a new `activity-ktx` artifact, a new catalog entry, and the reversal of a
deliberate PR 1 decision. The DI spec nevertheless states flatly that `MainActivity` SHALL be
annotated `@AndroidEntryPoint`, and the spec is binding, so the implementation is **correct as
specified**.

**Verdict: the change is correct and the deviation is well recorded.** The cheap, honest closure is
a one-line note on the spec requirement saying it is forward-looking (the activity will inject once
the feature slices land), so a future reader does not mistake a currently-inert annotation for dead
code and remove it. Recorded as SUGGESTION 1, not a finding against the code.

### 2. `failOnNoDiscoveredTests = false` — a legitimate fix applied at the wrong blast radius

**The causal chain is real.** `unitTests.isIncludeAndroidResources = true` makes AGP put the
merged-resources jar on the unit-test runtime classpath; Gradle's bundled JUnit Platform launcher
treats that populated root as a test-source root and then fails the task when it discovers no tests.
`:feature:overlay` and `:feature:tasks` have zero tests by this slice's explicit design invariant,
so the failure is spurious. Disabling the check is Gradle's own documented escape hatch, and the
inline comment records the whole chain — good practice, since the error message alone never names
the cause.

**It does nonetheless mask real failures later, and the masking is permanent as written.** The
property is set on `tasks.withType<Test>().configureEach` in `AndroidLibraryConventionPlugin`, so it
applies to **every** `Test` task in **every** Android library module, **forever** — including
`:core:data` once it has Robolectric tests. "Zero tests discovered" then stops being a designed
state and becomes the classic silent failure: a bad include pattern, a broken runner, a runner /
JUnit-platform mismatch, or a Robolectric SDK resolution failure that drops the whole class from
discovery all report BUILD SUCCESSFUL. The zero-test invariant this suppression protects is
explicitly temporary — design.md scopes it to this slice.

**Verdict: legitimate now, a liability the moment the first test lands.** Closure: scope it rather
than remove it — for example, set it `false` only when the module has no `src/test` sources, or drop
it in the same PR that writes the first unit test. Recorded as W3, not a blocker: nothing today is
hidden, because there is nothing to hide.

### 3. The instrumented test pair — the positive test is strong; the negative test is real but under-asserted

**Positive (`PlaceholderHiltWorkerTest`): genuinely proves the wiring.** It injects
`HiltWorkerFactory` through `HiltAndroidRule`, hands it to `Configuration.Builder`, enqueues
`PlaceholderHiltWorker`, and asserts both `SUCCEEDED` and the output-data flag. The worker's
constructor takes `PetProfileRepository` as a third, non-assisted parameter, so it cannot be
instantiated at all unless the Hilt graph supplied that dependency. Reaching `doWork()` is itself
the proof; the output flag confirms the body actually ran. Not vacuous.

**Negative (`PlaceholderWorkerWithoutHiltAnnotationTest`): not vacuous, but weaker than its own
docstring.** It does test the right thing — `PlaceholderWorkerWithoutHiltAnnotation` is
line-for-line `PlaceholderHiltWorker` minus `@HiltWorker`, which is exactly the spec scenario, and it
compiles, proving the "fails at execution, not compile time" half. But the assertion is
`assertNotEquals` against `SUCCEEDED`, which passes for **any** non-success state, including
`ENQUEUED` and `BLOCKED`. If `WorkManagerTestInitHelper` / `SynchronousExecutor` ever stopped driving
the work — a setup regression, not a factory regression — the work would sit at `ENQUEUED` and the
test would still pass while proving nothing. The same green also appears if the class were renamed,
made abstract, or otherwise broken for reasons unrelated to `@HiltWorker`.

**Verdict: keep it, tighten it.** Assert `WorkInfo.State.FAILED` specifically, and add a positive
control in the same test — enqueue the annotated worker through the same helper and assert
`SUCCEEDED` — so a broken harness fails the test instead of silently satisfying it. Recorded as W2.

### 4. The Hilt scaffolding — at or just past the minimum, with one artifact that outlives the slice

`AppDatabase` plus `PlaceholderEntity` and `PlaceholderDao` are **not** gratuitous: the DI spec
itself mandates a `@Provides` for the Room database instance, and Room refuses to compile a
`@Database` with zero entities. The scaffolding is therefore the smallest shape that satisfies the
requirement as written, and `PlaceholderEntity`'s KDoc says so explicitly.

`PetProfileRepository` (`isOnboarded: Flow<Boolean>` and `setOnboarded`) is the one place where the
scaffolding starts to name domain concepts, and `PetProfileRepositoryImpl` gives it a real
DataStore-backed implementation. This is mild and arguably necessary — `@Binds` needs a real
interface/implementation pair — but "onboarding state" is a product decision no spec in this change
makes.

**The item that genuinely escapes the slice is the exported Room schema
`core/data/schemas/com.gcatcode.petmephone.core.data.local.AppDatabase/1.json`.** An exported schema
is a migration baseline, not a scratch file: once version 1 is committed, every future schema change
is measured against a `placeholder` table that will never ship. Nothing breaks while the app is
unreleased, and deleting the entity plus re-exporting a real version 1 later is trivial — but only
if someone remembers. Recorded as W4 with that exact closure.

**Verdict: acceptable as the minimum needed to prove wiring, with one carried liability.** No
finding against `AppDatabase`; W4 against the committed schema baseline.

### 5. The four carried-forward items — all four are genuinely done

1. **`.hilt.work` split.** Verified in the plugin sources, not just the record:
   `AndroidHiltConventionPlugin` now adds only `hilt-android` and `hilt-android-compiler`;
   `AndroidHiltWorkConventionPlugin` adds `work-runtime-ktx`, `hilt-work`, `androidx-hilt-compiler`;
   the id `com.petmephone.android.hilt.work` is registered and appears in exactly one module script,
   `app/build.gradle.kts`. Regression check 1 confirms `work` still resolves to `2.11.2`, so the move
   preserved the pin. **DONE.**
2. **`isIncludeAndroidResources`.** Present in `AndroidLibraryConventionPlugin`'s `testOptions`, with
   the `failOnNoDiscoveredTests` fix and its causal comment. `./gradlew test` is green graph-wide.
   **DONE** — see W3 on the fix's blast radius.
3. **Catalog pruning.** `androidx-lifecycle-runtime-ktx`, `androidx-activity-compose`, and both
   version keys are absent from `gradle/libs.versions.toml`. `androidx-activity = activity-ktx` was
   added with a comment stating why it is not `activity-compose`. `AndroidComposeConventionPlugin`
   still adds no Activity artifact, so design.md's rule holds. **DONE.**
4. **design.md test-table correction.** The table now lists `androidx-junit` and `ui-test-manifest`
   for `:core:designsystem` and `:feature:*`. Checked **against the module scripts, not against the
   record**: `core/designsystem/build.gradle.kts` and `feature/overlay/build.gradle.kts` declare
   exactly `androidx.junit`, `bundles.compose.test`, and a `debugImplementation` of
   `ui-test-manifest`. The edit describes verified reality. **DONE.**

## apply-progress.md honesty

Mostly honest and current, and better structured than PR 1's. It proactively surfaced the
`ComponentActivity` deviation, the scaffolding-not-domain-modelling caveat, the new
`kotlinx-coroutines-core` dependency, and the `failOnNoDiscoveredTests` chain — all four of which
this run confirmed and none of which it had to discover unaided. Its commit list matches `git log`.

Two defects:

- **One false verification claim.** The bullet asserting that
  `PetMePhoneApplication.workManagerConfiguration` is exercised at cold start, because
  `Configuration.Provider` is queried by the default initializer path removed in 3.4 and again by
  the instrumented tests' own `WorkManagerTestInitHelper` calls, is wrong on both halves — see C2.
  This is the change's own memory of a spec scenario, so a false entry here is worse than an
  omission.
- **One unrecorded deviation.** `app/build.gradle.kts` gaining a `defaultConfig` block appears
  nowhere in "Deviations / notes for verify", even though it violates a build-foundation requirement
  that PR 1 needed a spec amendment to satisfy (C1).

## Findings

### CRITICAL

**C1 — `app/build.gradle.kts` violates build-foundation "Module scripts are plugin application plus
dependencies only", regressing that spec from 11/11 to 10/11.**

The module script's `android {}` block now contains a `defaultConfig` block setting
`testInstrumentationRunner = "com.gcatcode.petmephone.CustomTestRunner"` alongside `namespace`.

The spec's carve-out — amended into existence during PR 1 precisely so this rule could survive —
is explicit and bounded: an `android {}` block containing nothing but `namespace`, and *"anything
beyond `namespace` inside that block is a violation."* This is beyond `namespace`.

It is also a **two-owner conflict**, which is the requirement's whole point:
`AndroidApplicationConventionPlugin`'s `defaultConfig` already sets
`testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"`, and the module script
silently overrides it. A reader of the convention plugin now reads a false statement about `:app`.

Closure is one line and carries no risk: `:app` is the plugin's only consumer, so set the
`CustomTestRunner` value in `AndroidApplicationConventionPlugin` and delete the `defaultConfig`
block from the module script. The `CustomTestRunner` class itself is correct and should stay.

**C2 — the dependency-injection scenario "Single WorkManager instance at cold start" has no covering
runtime evidence, and both tasks.md 3.15 and apply-progress.md claim it does.**

The scenario requires that `WorkManager.getInstance(context)` on the cold-started app throw no
`IllegalStateException`, "proving exactly one WorkManager initialisation occurred, using the custom
`Configuration`". Nothing executed proves that:

- **The instrumented tests cannot cover it.** `CustomTestRunner` substitutes `HiltTestApplication`
  for `PetMePhoneApplication` in every instrumented test, so the production `Configuration.Provider`
  is never consulted. Both tests then build their **own** `Configuration.Builder()` and call
  `WorkManagerTestInitHelper.initializeTestWorkManager`. They prove `HiltWorkerFactory` works; they
  say nothing about `PetMePhoneApplication`.
- **The install-and-launch check cannot cover it either.** `WorkManagerInitializer` was removed
  (correctly, task 3.4), so WorkManager is not initialised at startup, and no app code calls
  `WorkManager.getInstance`. Confirmed on device during this run: after launching at pid `24647`,
  the only `WM-*` logcat lines in the buffer come from pid `4861`, a different process. The app
  never touched WorkManager.
- **The recorded justification is self-contradictory.** Task 3.15 says the provider is queried by
  the default initializer path "removed in 3.4" — a path that, by 3.4's own success, does not run.

The implementation is very likely correct: with the initializer removed, `getInstance` performs
on-demand initialisation and reads `Configuration.Provider` off the `Application`, which
`PetMePhoneApplication` implements. **The defect is evidentiary, not (apparently) behavioural** — but
it is exactly the class of defect this PR was asked to catch: a checked task whose evidence does not
exist, supported by reasoning that refutes itself. This slice already paid once for trusting a green
build over a runtime check (PR 1's `ForceStopRunnable` crash).

Closure, roughly ten lines: add one instrumented test that does **not** go through
`WorkManagerTestInitHelper` — call `WorkManager.getInstance` against the real
`PetMePhoneApplication` and assert no throw, plus that the resolved configuration's worker factory
is a `HiltWorkerFactory`. Then correct 3.15's text and the apply-progress bullet.

### WARNING

1. **`:core:domain` gained more than `javax.inject`, and less.** It gained
   `api(libs.kotlinx.coroutines.core)` — pure JVM, so the Android-free requirement is untouched, and
   design.md's "api only for Flow-returning domain interfaces" rule explicitly authorises it. But the
   DI requirement says repository interfaces "MUST import only `javax.inject` types", and
   `PetProfileRepository` imports `kotlinx.coroutines.flow.Flow` and **no** `javax.inject` type at
   all — the module declares no `javax.inject` dependency, so the `@Qualifier`/`@Scope` the
   requirement contemplates are not even available there today. The intent (no `dagger.*` in the
   domain) is fully met. Amend the requirement to allow pure-JVM types required by interface
   signatures, so it stops contradicting design.md.
2. **The negative worker test asserts too little.** `assertNotEquals` against `SUCCEEDED` passes for
   `ENQUEUED`. Assert `FAILED` and add a same-test positive control. See Adjudication 3.
3. **`failOnNoDiscoveredTests = false` is unscoped and permanent.** It will hide a genuine
   "no tests ran" failure the moment `:core:data` has Robolectric tests. See Adjudication 2.
4. **The exported Room schema commits a placeholder table as the migration baseline.** Delete the
   placeholder entity and re-export before the first release, or note in `AppDatabase` that
   version 1 is disposable until release. See Adjudication 4.
5. **design.md now contradicts its own code in places CF-4 did not touch.** The module table still
   lists `:app` as `android.application`, `android.compose`, `android.hilt` with no `.hilt.work`; the
   plugin table still describes `.hilt` as carrying `hilt-work` and `androidx.hilt:hilt-compiler`,
   which CF-1 deliberately moved out; and both the heading "The six convention plugins" and the
   `build-logic` layout comment naming six registrations are now wrong — there are seven. This is
   PR 1's W2 and PR 2's W2 recurring a third time: design.md is binding, so a stale binding document
   eventually gets used to "correct" working code.
6. **`apply-progress.md` contains one false verification claim (C2) and omits one deviation (C1).**
   Both are one-paragraph fixes and should land with the CRITICAL closures.

### SUGGESTION

1. Note on the DI spec's `MainActivity` requirement that `@AndroidEntryPoint` is forward-looking, so
   nobody deletes a currently-inert annotation. See Adjudication 1.
2. `PlaceholderDao.getAll()` is never called by any code or test. Either drop the DAO (the
   `@Database` needs the entity, not the DAO) or exercise it once, so the Room half of the graph has
   at least the same evidentiary standing as the Hilt half.
3. `AndroidHiltConventionPlugin`'s KDoc names the new plugin `com.petmephone.android.work`; the
   registered id is `com.petmephone.android.hilt.work`. One-word fix in a comment a future reader
   will grep.
4. `.idea/markdown.xml` was committed in this PR. PR 1's SUGGESTION 4 (gitignore `.idea/` state
   files) is now three PRs old, and the working tree still shows `.idea/deploymentTargetSelector.xml`
   dirty.
5. Still open from earlier PRs and unaffected by this one: `android.disallowKotlinSourceSets=false`
   has no recorded exit condition (PR 1 W3); issue #1's self-contradictory acceptance criterion
   (PR 1 W4); and of the three unconsumed catalog entries flagged in PR 2, `datastore` is now
   consumed by `:core:data`, leaving `lottie-compose` and `dmfs-lib-recur` unverified by any build.

## Verdict

**FAIL for PR 3 — 2 CRITICAL, 6 WARNING, 5 SUGGESTION.**

What works, proven by execution rather than inspection: the Hilt object graph configures, compiles,
and runs. `PetMePhoneApplication` has exactly the shape the spec dictates. The merged manifest drops
`WorkManagerInitializer` while keeping all three sibling `androidx.startup` initializers — the
precise, easy-to-get-wrong outcome design.md called for. `@Binds` and `@Provides` are split correctly
and live in `:core:data`. Zero service-scoped bindings. Cold configuration-cache reuse survives Hilt
and KSP. Both instrumented tests pass on `emulator-5554`, and the positive one is real proof: the
worker cannot be constructed without the injected repository. The APK installs, launches, and holds
a stable PID with an empty crash buffer. All four carried-forward items are genuinely discharged,
including CF-4, which was checked against the module scripts rather than against the record it was
written from. `androidx.work` still resolves 2.3.4 to 2.11.2.

What blocks archive: `app/build.gradle.kts` reintroduces module-script configuration that
`build-foundation` forbids in terms PR 1 had to amend the spec to make satisfiable, and it silently
overrides the convention plugin that is supposed to be the value's single owner (C1). And task 3.15
claims runtime evidence for the cold-start WorkManager scenario that no executed command produced,
justified by reasoning that contradicts task 3.4's own success (C2). Neither is expensive: one line
moved into a convention plugin, one short instrumented test, and two documentation corrections.

`dependency-injection` is 8/9 requirements and 10/11 scenarios; `build-foundation` regressed to
10/11 and 13/14. Change-level coverage is **18/20 requirements and 23/25 scenarios**.

**The change is NOT archivable.** Unlike PR 1 and PR 2, the reason is no longer scheduled scope —
all three PRs have landed — but two defects in this PR's own surface. Re-run `sdd-apply` for C1 and
C2, then re-verify; the remaining six warnings are documentation and hardening and can be triaged
into the follow-up slices.

---

> **Preserved record — PR 2 (issue #3), verbatim.** Verified at HEAD `58a7d07`. Its envelope figures
> (11/20 requirements, 14/25 scenarios, verdict `fail` for scheduled scope) were correct at that
> revision and are superseded by the PR 3 envelope at the top of this file. Status of its open
> findings as of PR 3: W1 (`isIncludeAndroidResources` missing) **resolved** by CF-2; W2 (design.md
> test table) **resolved** by CF-4; W3 (apply-progress PR 1 heading) **still open**; SUGGESTION 1
> partly resolved (`datastore` is now consumed); SUGGESTION 2 (catalog pruning) **resolved** by
> CF-3; SUGGESTIONs 3 and 4 **still open**.
# Verification Report — slice-1-foundation, PR 2 (issue #3)

HEAD at verification: `58a7d07`. Device: `emulator-5554` (API 37, x86_64).
PR 2 diff vs PR 1's branch: 170 insertions / 21 deletions across 10 files.

PR 1 is verified and closed (record preserved below). PR 3 (#6) has not started; its
`dependency-injection` requirements are **out of scope and explicitly not failed here**.

## Runtime evidence — all executed at `58a7d07`

| Command | Result |
|---|---|
| `rm -rf .gradle/configuration-cache` then `./gradlew build --configuration-cache` | BUILD SUCCESSFUL in 14s. 435 actionable tasks. "**Configuration cache entry stored.**" Cold — the cache directory was deleted first, so run 1 could not reuse a warm entry. |
| `./gradlew build --configuration-cache` (2nd, immediate) | BUILD SUCCESSFUL in 3s. 431 actionable tasks, 426 up-to-date. "**Configuration cache entry reused.**" |
| `./gradlew test` | BUILD SUCCESSFUL, exit 0. 103 actionable tasks. Every unit-test task `NO-SOURCE` (`:app:testDebugUnitTest NO-SOURCE`, `:feature:tasks:test UP-TO-DATE`, …). **Zero tests executed, zero failures.** |
| `:feature:overlay`, `:feature:tasks`, `:core:designsystem`, `:app` `assembleDebugAndroidTest` + `:app:assembleDebug` | BUILD SUCCESSFUL. All four instrumentation source sets compile and package; debug APK produced. |
| `./gradlew :app:dependencies --configuration debugRuntimeClasspath` | `androidx.work:work-runtime-ktx:2.11.2`; `androidx.work:work-runtime:2.3.4 -> 2.11.2`. **PR 1's WorkManager fix has not drifted.** |
| `adb install -r` + `am start -n com.gcatcode.petmephone/.MainActivity` | `Success`; `Starting: Intent { … }`. |
| `adb shell pidof com.gcatcode.petmephone` (after 6s) | `11239` — process alive and stable. |
| `adb logcat -d -b crash` (buffer cleared before install) | **0 lines. Empty crash buffer.** |

**`connectedDebugAndroidTest` was deliberately NOT run, and this was checked rather than assumed.**
`git ls-files | rg "src/(test|androidTest)"` returns nothing — no test source file is tracked in the
repository. The `test`/`androidTest` source sets are configured and resolvable but contain zero
files, exactly as the zero-tests requirement demands. `connectedDebugAndroidTest` would report
success over an empty suite, which is not evidence. Reported as vacuous, not as passed.

## Regression checks — the catalog is where these live

| # | Check | Result | Evidence |
|---|---|---|---|
| 1 | `androidx.work` >= 2.11.2 | **PASS** | `work = "2.11.2"` in catalog with the four-line FLAG_IMMUTABLE comment intact; runtime resolution shows `2.3.4 -> 2.11.2`; app launches with an empty crash buffer |
| 2 | No BOM-covered Compose artifact carries `version.ref` | **PASS** | All seven `androidx.compose.*` entries (`ui`, `ui-graphics`, `ui-tooling`, `ui-tooling-preview`, `ui-test-manifest`, `ui-test-junit4`, `material3`) declare `group`/`name` only. BOM resolves them — `ui-test-manifest -> 1.10.4` in the dependency report |
| 3 | `ksp` Kotlin prefix == `kotlin` | **PASS** | `kotlin = "2.2.10"`, `ksp = "2.2.10-2.0.2"`; coupling comment present on the adjacent line |
| 4 | `android.disallowKotlinSourceSets=false` present | **PASS** | `gradle.properties` line 30, comment and AGP doc link intact |
| 5 | No Android module applies `org.jetbrains.kotlin.android` | **PASS** | Repo-wide search outside `openspec/`/`docs/`: **zero hits** |
| 6 | No sdk/toolchain literals outside `build-logic` | **PASS** | Search for `compileSdk`, `minSdk`, `targetSdk`, `VERSION_11`, `kotlinOptions`, `jvmTarget` across `app`, `core`, `feature`, `gradle.properties`, `settings.gradle.kts`, `build.gradle.kts`: **zero hits** |

All six regression checks hold. Nothing PR 1 established was undone.

## Task completeness — PR 2 (2.1–2.15)

**15 of 15 checked tasks verified as genuinely done.** Each `[x]` was checked against the tree, not
against its own description. No task claims work that was not performed — the failure mode that
task 1.1 exhibited in PR 1 does not recur here.

| Task | Claim | Tree state | Verdict |
|---|---|---|---|
| 2.1 | KSP `2.2.10-2.0.2` resolved, discharged in PR 1 | catalog line 11, with coupling comment | TRUE |
| 2.2 | `[versions]` added: datastore, lottieCompose, dmfsLibRecur, kotlinxCoroutinesTest, turbine, robolectric, mockk | all seven present, lines 21–27 | TRUE |
| 2.3 | `[libraries]` added; BOM-covered artifacts carry no `version.ref` | eight new entries present; regression check 2 confirms | TRUE |
| 2.4 | `kotlin-compose` uses `version.ref = "kotlin"` | catalog line 91 — `version.ref = "kotlin"`, not a literal | TRUE |
| 2.5 | `[bundles]` `compose-ui` + `compose-test`; BOM outside any bundle | both bundles present; `androidx-compose-bom` appears in no bundle and is applied via `dependencies.platform(...)` | TRUE |
| 2.6 | `:core:domain` test: coroutines-test, Turbine, JUnit4 inherited, no MockK | `testImplementation(libs.kotlinx.coroutines.test)` + `libs.turbine`; `JvmLibraryConventionPlugin` adds `junit`; no MockK | TRUE |
| 2.7 | `:core:data` test: JUnit4, Robolectric, coroutines-test, Turbine, MockK | all five declared | TRUE |
| 2.8 | `:core:designsystem` androidTest: compose.ui.test bundle + androidx-junit + ui-test-manifest | all three declared | TRUE |
| 2.9 | `:feature:overlay` / `:feature:tasks` test + androidTest | both scripts identical and complete | TRUE |
| 2.10 | `:app` androidTest smoke: androidx-junit + espresso-core | both declared | TRUE |
| 2.11 | No `JavaVersion.VERSION_11` outside `build-logic` | regression check 6 — zero hits | TRUE |
| 2.12 | `./gradlew test` green, zero tests | exit 0, all `NO-SOURCE` | TRUE |
| 2.13 | Four `assembleDebugAndroidTest` targets compile | BUILD SUCCESSFUL | TRUE |
| 2.14 | Catalog inspection: no double-pinned artifact; ksp prefix matches | regression checks 2 and 3 | TRUE |
| 2.15 | `build --configuration-cache` x2 with reuse, cache deleted first | stored then reused, cold | TRUE |

## Spec compliance — build-foundation (11 requirements / 14 scenarios): 11/11, 14/14

| Requirement | Status | Evidence |
|---|---|---|
| Six-module graph with fixed identities | PASS | unchanged from PR 1; `settings.gradle.kts` untouched by PR 2 |
| Domain layer is Android-free (2 scn) | PASS | PR 2 added only `testImplementation` coroutines-test + Turbine to `:core:domain`, both pure-JVM; `./gradlew test` compiles the module clean |
| Explicit module identity | PASS | all five Android scripts retain `namespace`; `resourcePrefix` still plugin-derived |
| Single owner for shared build values (2 scn) | PASS | regression check 6 — zero literals outside `build-logic` |
| Module scripts are plugin application plus dependencies only | PASS | all six scripts re-read at `58a7d07`: `plugins {}` + `android { namespace = … }` (nothing else) + `dependencies {}`. The amended one-property exception is respected in every case |
| Included build resolves before the root build's plugin blocks | PASS | build configures; no "was not found" |
| **No Compose artifact covered by the BOM carries an explicit version** | **PASS (was PARTIAL)** | regression check 2; catalog now complete |
| **KSP version is coupled to the Kotlin version** | **PASS (was PARTIAL)** | regression check 3; `kotlin-compose` now confirmed at `version.ref = "kotlin"` (task 2.4) |
| **Test source sets exist with zero tests (2 scn)** | **PASS (was OUT OF SCOPE)** | scn 1: `./gradlew test` exit 0, every task `NO-SOURCE`. scn 2: all four `assembleDebugAndroidTest` targets compile |
| Configuration cache reuse survives the whole slice | PASS | cold x2 above — the requirement's "MUST hold after each of #1, #2, #3" clause is satisfied for #3 |
| The application module remains installable throughout | PASS | install + launch + surviving PID + empty crash buffer on `emulator-5554` |

**The `build-foundation` delta spec is now fully satisfied.** Every scenario is backed by an
executed command at `58a7d07`, not by source inspection alone.

## Spec compliance — dependency-injection (9 requirements / 11 scenarios): 0/9, 0/11

Entirely PR 3 (#6) scope. **Not claimed and not failed here.** PR 2 touched nothing in that spec's
surface: no `@HiltAndroidApp`, no manifest override, no bindings, no worker. Reporting these as
failures would be a category error; reporting them as passes would be a fabrication.

## Design coherence — PR 2 surface

| Design element | Status |
|---|---|
| Catalog structure `[versions]` to `[libraries]` to `[bundles]` to `[plugins]` | MATCHES — sections appear in exactly that order |
| BOM-covered artifacts unpinned; BOM applied as `platform(...)`, never in a bundle | MATCHES |
| `ksp = "<kotlin>-<patch>"` with adjacent coupling comment | MATCHES |
| `kotlin-compose` uses `version.ref = "kotlin"` | MATCHES |
| Room's KSP processor tracks the Room version, not Kotlin | MATCHES — `androidx-room-compiler` uses `version.ref = "room"` |
| JUnit4 project-wide because `ui-test-junit4` needs `@Rule` | MATCHES |
| `.compose` = "Compose BOM + `compose-ui` bundle" | MATCHES — this PR is what made the design text true; see Adjudication 4 |
| Test table: `:core:domain` test = JUnit4, coroutines-test, Turbine, no MockK | MATCHES |
| Test table: `:core:data` test = + Robolectric, MockK | MATCHES declaratively; see W1 for the runtime gap |
| Test table: `:core:designsystem` androidTest = `compose.ui.test` | **DEVIATION (justified)** — `ui-test-manifest` and `androidx-junit` also added. See Adjudication 1 |
| Test table: `:feature:*` = JUnit4, MockK, `compose.ui.test` | **DEVIATION (justified)** — same `ui-test-manifest` addition |
| Test table: `:app` androidTest = smoke | MATCHES (the placeholder `@HiltWorker` half is PR 3) |

## Adjudications requested

### 1. `ui-test-manifest` as `debugImplementation` — correct call; amend the design table, not the code

**The technical claim is correct.** `createComposeRule()` launches a host activity and needs
`androidx.activity.ComponentActivity` declared in the manifest merged into the androidTest APK.
`androidx.compose.ui:ui-test-manifest` exists for exactly that purpose and supplies only that
manifest entry; AndroidX documents `debugImplementation` as its intended configuration. Without it
the rule throws at runtime — a failure that no amount of compile-time green would surface, and the
current zero-test state would hide indefinitely.

**Verified empirically, not accepted on assertion.** `ui-test-manifest` resolves to `1.10.4` on
`:app`'s `debugRuntimeClasspath` (BOM-supplied, unpinned), and the same dependency report against
`releaseRuntimeClasspath` returns **0 matches** — it does not reach the release APK. The cost is
confined to debug builds, which is the whole point of the `debugImplementation` configuration.

**Verdict: adding it is right; the design table is what should change.** `design.md`'s test
infrastructure table names only "`compose.ui.test`", which is under-specified — it omits both
`ui-test-manifest` and `androidx-junit`, and the latter is equally load-bearing. This is the same
class of finding as PR 1's W2 (design.md's `.hilt` row omitting `work-runtime-ktx`): the code is
correct and the binding document trails it. Recorded as W2 below. Amending code to match an
incomplete table would break `createComposeRule()` to satisfy prose.

### 2. `robolectric = 4.16.1` / `datastore = 1.2.1` — "latest stable" is the right reading

**Verified against live Maven metadata, not taken on trust:**

```
robolectric:            <latest>4.17-beta-2</latest>   <release>4.17-beta-2</release>
  version list tail:    4.15.1, 4.16-beta-1, 4.16, 4.16.1, 4.17-beta-1, 4.17-beta-2
datastore-preferences:  <latest>1.3.0-alpha10</latest> <release>1.3.0-alpha10</release>
  stable version tail:  1.1.6, 1.1.7, 1.2.0, 1.2.1
```

The apply phase's description of the metadata is exact. Maven's `<release>` tag means "the most
recent non-SNAPSHOT version published", which is a *publication* fact, not a *stability* claim — it
happily points at betas and alphas, and here it points at both. Reading it as "the version to
depend on" is a common and costly mistake.

`4.16.1` is the newest entry with no alpha/beta/rc qualifier; `1.2.1` likewise. Pinning a build
foundation — the layer every future module inherits — to a beta test runner and an alpha
persistence library would import their churn into every module at once, for no benefit, in a slice
whose entire purpose is to make shared values stable and single-sourced. **Justified. No change.**

### 3. Unconsumed catalog entries — compliant with task 2.2, and now de-risked

`datastore`, `lottie-compose`, and `dmfs-lib-recur` are declared and consumed by nothing. Task 2.2
names all three by name ("DataStore, Lottie, `dmfs:lib-recur`"), so the apply phase implemented the
task as written; this is not scope creep and not a task-claim defect.

**But the real risk is not prematurity — it is that no build verifies them.** Gradle resolves only
what a configuration requests, so an unconsumed catalog entry with a typo'd coordinate or a
nonexistent version stays green through every build in this report and fails for the first
developer who consumes it, in a PR that did not introduce the error. I therefore resolved all three
directly rather than rely on the build:

```
androidx/datastore/datastore-preferences/1.2.1  -> HTTP 200
com.airbnb.android/lottie-compose/6.7.1         -> HTTP 200
org.dmfs/lib-recur/0.17.1                       -> HTTP 200
```

All three exist. **Justified, and empirically de-risked.** Recorded as SUGGESTION 1 so a future
reader knows these three coordinates carry no build-enforced guarantee until consumed.

### 4. `AndroidComposeConventionPlugin` refactor — confirmed behaviour-preserving

The complete diff is seven lines:

```diff
-            dependencies.add("implementation", libs.findLibrary("androidx-compose-ui").get())
-            dependencies.add("implementation", libs.findLibrary("androidx-compose-ui-graphics").get())
-            dependencies.add("implementation", libs.findLibrary("androidx-compose-ui-tooling-preview").get())
-            dependencies.add("implementation", libs.findLibrary("androidx-compose-material3").get())
+            libs.findBundle("compose-ui").get().get().forEach {
+                dependencies.add("implementation", it)
+            }
```

**Same artifacts:** the `compose-ui` bundle lists `androidx-compose-ui`,
`androidx-compose-ui-graphics`, `androidx-compose-ui-tooling-preview`, `androidx-compose-material3`
— the identical four aliases, in the identical order.
**Same configuration:** every element is still added to `implementation` via the same
`dependencies.add` call.
**Same surroundings:** the BOM `platform(...)` line above and the `debugImplementation` `ui-tooling`
line below are byte-identical and untouched, so the BOM still governs versions and `ui-tooling`
still stays out of the bundle and out of release.

**Confirmed behaviour-preserving.** This also discharges a carried item: PR 1's verify report
SUGGESTION 6 asked for exactly this move once `[bundles]` landed. Done.

## apply-progress.md honesty

**The PR 2 record is honest and current** — unlike PR 1's, which went stale and needed correction.
Every claim it makes was independently reproduced here: the config-cache timings, the four
`assembleDebugAndroidTest` targets, the `NO-SOURCE` test result, the `work` resolution, and the
on-device launch with an empty crash buffer. Its "Deviations / notes for verify" section proactively
surfaced all three items adjudicated above rather than leaving them for verify to discover, which is
the correct posture and is why this verification could be targeted.

One structural defect: PR 1's `## Commits (in order)` and `## Verified` sections (from line 98) sit
**below** PR 2's section with no `## PR 1` heading of their own, while PR 2's equivalents are nested
as `###`. A reader arriving at line 98 has no cue that the content reverted to PR 1. The information
is accurate; the layout invites misattribution. Recorded as W3.

## Findings

### CRITICAL

**None.** Fifteen of fifteen PR 2 task claims are true against the tree, all six regressions hold,
every `build-foundation` scenario is backed by an executed command, and the app launches on a real
device with an empty crash buffer.

### WARNING

1. **`:core:data` declares Robolectric, but the AGP option Robolectric needs is set nowhere.**
   Searching `build-logic/` for `testOptions`, `unitTests`, or `includeAndroidResources` returns
   **zero hits**, and no module script sets them either. Robolectric's documented requirement for
   reading the merged manifest and resources is
   `testOptions { unitTests.isIncludeAndroidResources = true }`. Nothing fails today because zero
   tests exist — but that is precisely the problem: the spec requirement is "test source sets and
   test libraries wired **and resolvable**", and resolvability was proven while *usability* was not.
   The first Robolectric test written will likely fail on infrastructure this slice declared
   complete. Not CRITICAL, because `./gradlew test` genuinely passes and the zero-tests scenario is
   genuinely met. Closure: add the option to `AndroidLibraryConventionPlugin` (one owner, consistent
   with the slice's whole thesis), or record an explicit decision to defer it to the PR that writes
   the first test.
2. **`design.md`'s test-infrastructure table is under-specified against working code.** It names
   only "`compose.ui.test`" for `:core:designsystem` and `:feature:*`, while the implementation
   correctly also needs `ui-test-manifest` and `androidx-junit`. See Adjudication 1. This is the
   second instance of the same pattern (PR 1's W2 on the `.hilt` row); design.md is binding, so a
   binding document that trails the code will eventually be used to "correct" working code. Amend
   both rows.
3. **`apply-progress.md`'s PR 1 sections are unheaded and sit below PR 2's**, so PR 1's commit list
   and verification results read as if they belong to PR 2. Content is accurate; add a
   `## PR 1 (issues #1 + #2)` heading before line 98.

### SUGGESTION

1. `datastore`, `lottie-compose`, and `dmfs-lib-recur` are unconsumed, so no Gradle task resolves
   them and no build can catch a bad coordinate. All three verified present upstream during this
   run (HTTP 200); note in the catalog that they are unverified-by-build until first consumed.
2. **PR 1's SUGGESTION 6 is only half discharged.** It asked PR 2 to prune four unused template
   catalog entries. `androidx-junit` and `androidx-espresso-core` are now genuinely consumed (tasks
   2.8–2.10), but `androidx-lifecycle-runtime-ktx` and `androidx-activity-compose` remain declared
   and consumed by nothing. `activity-compose` is the sharper one: `design.md` explicitly states the
   `.compose` plugin must **not** add an Activity-specific artifact, so leaving the entry in the
   catalog is a standing invitation to do the thing the design forbids. Prune both, or comment why
   they stay.
3. The Compose BOM covers `foundation`, which the spec names explicitly as an example, but no
   `foundation` entry exists in the catalog and the `compose-ui` bundle cannot supply it. Vacuously
   compliant today. When a feature needs it, it must be added without a `version.ref` — worth a
   comment above the bundle so the rule survives the next author.
4. PR 1's still-open warnings are unchanged by this PR and remain open before archive: the
   `android.disallowKotlinSourceSets=false` exit condition (PR 1 W3), `design.md`'s `.hilt` row
   omitting `work-runtime-ktx` (PR 1 W2), and issue #1's self-contradictory acceptance criterion
   (PR 1 W4). PR 1's W1 (apply-progress under-recording) was resolved by `3582813`.

## Verdict

**PASS WITH WARNINGS for PR 2.** All fifteen task claims are true against the tree — the task-1.1
class of defect does not recur. The `build-foundation` delta spec is now complete at 11/11
requirements and 14/14 scenarios, with every scenario backed by an executed command rather than
source inspection. All six regression checks hold, including the WorkManager pin that crashed the
app in PR 1: it still resolves `2.3.4 -> 2.11.2`, and the APK installs, launches, and holds a stable
PID with an empty crash buffer on `emulator-5554`. Configuration-cache reuse holds cold with the
cache directory deleted first. All four requested adjudications resolve in the implementation's
favour: `ui-test-manifest` is required and correctly scoped to debug, "latest stable" is the right
reading of Maven metadata, the unconsumed entries match task 2.2's wording and were verified to
exist, and the Compose plugin refactor is confirmed behaviour-preserving artifact-for-artifact.

Three warnings remain. Two are documentation. The first is not: `:core:data` declares Robolectric on
infrastructure that lacks the AGP option Robolectric requires, so "the test infrastructure works" is
proven for resolution but not for use. It does not block PR 2 — the spec asks for resolvable, green,
and zero tests, and all three hold — but it should be closed before anyone writes a Robolectric test.

**The change is not archivable yet**, for a scheduled reason rather than a defect: PR 3 (#6) has not
started, so change-level coverage is 11/20 requirements and 14/25 scenarios and the
`dependency-injection` spec is unclaimed by any evidence in this run.

---

> **Preserved record — PR 1 (issues #1 + #2), verbatim.** Verified at HEAD `42ddf8a`. Its
> envelope figures (8/20 requirements, 10/25 scenarios) were correct at that revision and are
> superseded by the PR 2 envelope at the top of this file. Status of its open findings as of
> PR 2: W1 (apply-progress under-recording) **resolved** by `3582813`; W2, W3 and W4 **still
> open**; SUGGESTION 3 **resolved** (PR 2 marked tasks 2.2/2.3 `[x]`); SUGGESTION 6's bundle
> move **resolved** by PR 2, its catalog pruning only **half discharged** (PR 2 SUGGESTION 2).

# Verification Report (RE-RUN) — slice-1-foundation, PR 1 (issues #1 + #2)

HEAD at verification: `42ddf8a`. Device: `emulator-5554` (sdk_gphone16k_x86_64, API 37, x86_64).

Totals span BOTH delta specs (`build-foundation` 11 req / 14 scn, `dependency-injection` 9 req /
11 scn). PR 1 is the first of three PRs; the uncompleted majority is scheduled scope, not defect.

## Record of the previous run and how it was resolved

The first verification returned `fail` with 1 CRITICAL and 6 WARNINGs.

| Previous finding | Correction | Re-verified state |
|---|---|---|
| CRITICAL — `build-foundation` "Module scripts are plugin application plus dependencies only" forbade any `android {}` block while "Explicit module identity" mandated a per-module `namespace`, which AGP can only express inside that block. Jointly unsatisfiable. | `d1b9717` amended the spec with an explicit, bounded carve-out. | **RESOLVED.** See Adjudication 1 below. |
| WARNING — task 1.1 claimed `git rm MainActivity.kt`; the file exists as a blank `Activity`. | `d1b9717` amended the task text. | **RESOLVED.** |
| WARNING — tasks.md 2.1 stale (work already discharged in PR 1). | `d1b9717` marked 2.1 `[x]` with the rationale. | **RESOLVED.** |
| WARNING — on-device install/launch unverified (no device). | Device now available; run executed here. Doing so exposed a real runtime crash, fixed in `42ddf8a`. | **RESOLVED, and it paid for itself.** |
| WARNING — jvmToolchain design deviation recorded only in a source comment; MainActivity deviation absent from `apply-progress.md`. | Not addressed. | **STILL OPEN** (W1). |
| SUGGESTIONs 1–5 | Not addressed. | Still open, non-blocking. |

## Correction 1 — the spec amendment (`d1b9717`)

Amended text in `specs/build-foundation/spec.md`:

> **Exception, and the only one:** an `android {}` block containing nothing but `namespace`. […]
> Anything beyond `namespace` inside that block is a violation.

The scenario THEN clause was amended in step: *"…at most an `android { namespace = "..." }` block
carrying nothing else; no `kotlin {}` block, no compiler-option block, and no other AGP
configuration appears in the module script itself."*

**Coherent.** The carve-out is bounded (one property), falsifiable (anything else is a violation),
and states its own cause. It closes the contradiction without weakening the requirement's intent —
no *shared* build configuration inline.

**Implementation respects the narrowed rule — all six scripts checked, not one:**

| Module | `android {}` contents | Verdict |
|---|---|---|
| `:app` | `namespace = "com.gcatcode.petmephone"` only | PASS |
| `:core:domain` | no `android {}` block at all (JVM module) | PASS |
| `:core:data` | `namespace = "…core.data"` only | PASS |
| `:core:designsystem` | `namespace = "…core.designsystem"` only | PASS |
| `:feature:overlay` | `namespace = "…feature.overlay"` only | PASS |
| `:feature:tasks` | `namespace = "…feature.tasks"` only | PASS |

No `kotlin {}` block, no compiler-option block, no other AGP configuration in any of the six.
Repo-wide grep for `compileSdk|minSdk|targetSdk|VERSION_11|kotlinOptions|jvmTarget|jvmToolchain`
across all `*.kts`/`*.properties` outside `build-logic/` and `**/build/`: **zero hits.**

## Correction 2 — task 1.1 text (`d1b9717`)

Amended text now reads: *"**Amended during verify:** `MainActivity.kt` is NOT removed. It is the
manifest `LAUNCHER` target, and deleting it makes the 'APK launches without crashing' success
criterion unsatisfiable. It is reduced to a blank `android.app.Activity` instead…"*

**Matches reality.** `app/src/main/java/com/gcatcode/petmephone/MainActivity.kt` is a 14-line
`class MainActivity : Activity()` whose `onCreate` calls only `super`, with a comment naming it a
placeholder. `ui/theme/*` and both generated test stubs remain deleted. The task text is now a true
statement about the tree.

## Correction 3 — task 2.1 (`d1b9717`)

Marked `[x]` with the discharge rationale and the resolved versions (KSP `2.2.10-2.0.2`, Hilt
`2.60.1`, Room `2.8.4`, androidx.hilt `1.4.0`). Accurate — those catalog entries exist and the
`.hilt`/`.room` plugins `.get()` them. Tasks 2.2/2.3 remain `[ ]` although partly done; that is a
minor and now deliberate bookkeeping choice, downgraded to SUGGESTION.

## Correction 4 — the WorkManager runtime defect (`42ddf8a`)

**The resolution really is 2.11.2.** `./gradlew :app:dependencies --configuration
debugRuntimeClasspath`:

```
+--- androidx.work:work-runtime-ktx:2.11.2
|    +--- androidx.work:work-runtime:2.11.2
|    +--- androidx.work:work-runtime:2.3.4 -> 2.11.2 (*)
```

The `2.3.4 -> 2.11.2` line is `hilt-work`'s floor being lifted, exactly as the commit message
claims.

**The fix is durable, not incidental.** Three independent reasons: the version is a named catalog
pin (`work = "2.11.2"`) with a four-line comment stating the FLAG_IMMUTABLE causality, not a
transitive accident; the dependency is declared with `implementation` in
`AndroidHiltConventionPlugin`, so it applies to every present and future `.hilt` consumer rather
than to one hand-edited script; and it is a direct first-order declaration, so it wins conflict
resolution rather than depending on another node happening to request a higher version. The
residual gap is narrow and worth naming: a module that hosts a worker **without** applying `.hilt`
would inherit nothing. No such module exists today.

## Runtime evidence — all re-executed at `42ddf8a`

| Command | Result |
|---|---|
| `rm -rf .gradle/configuration-cache` then `./gradlew build --configuration-cache` | BUILD SUCCESSFUL in 2m 12s. 435 tasks: 263 executed, 54 cached, 118 up-to-date. "**Configuration cache entry stored.**" |
| `./gradlew build --configuration-cache` (2nd, immediate) | BUILD SUCCESSFUL in 5s. "**Configuration cache entry reused.**" Cold check — the cache directory was deleted first, so run 1 could not have reused a warm entry. |
| `./gradlew :app:assembleDebug` | BUILD SUCCESSFUL. `app-debug.apk`, 12,510,674 bytes. |
| `adb uninstall` + `adb install -r` | `Success` (clean install, prior install removed first). |
| `adb shell am start -n com.gcatcode.petmephone/.MainActivity` | `Starting: Intent { cmp=com.gcatcode.petmephone/.MainActivity }`; logcat: `ActivityTaskManager: Displayed com.gcatcode.petmephone/.MainActivity for user 0: +7s59ms`. |
| `adb shell pidof com.gcatcode.petmephone` at +8s and +23s | `9904` both times — **same PID, no restart, process survived.** |
| `adb logcat -b crash -d` (buffer cleared before install) | **0 lines. Empty crash buffer.** |
| `adb logcat -d \| grep FATAL\|AndroidRuntime` | no match. |
| `./gradlew :app:dependencies --configuration debugRuntimeClasspath` | work-runtime resolves to 2.11.2 (above). |

`build-foundation` Requirement "The application module remains installable throughout" and its
scenario "Debug APK installs and launches" are now **verified at runtime**, not inferred from a
green build. The predecessor's refusal to accept a green build as launch evidence was correct:
the build was green through the entire crash.

**Instrumented tests: none exist in PR 1 scope.** `find app core feature -path "*src/androidTest*"
-o -path "*src/test*"` returns nothing — no module declares a test or androidTest source set. Test
source sets are PR 2 tasks 2.6–2.10; `@HiltWorker` instrumented tests are PR 3 tasks 3.11–3.13.
`connectedDebugAndroidTest` would therefore be vacuous here and was not run. Reported as absent,
not as passed.

## Task completeness — PR 1 (1.1–1.36)

**36 of 36 checked tasks now verified as genuinely done.** Task 1.1's text was the only mismatch
and is corrected. Spot re-checks against the tree at `42ddf8a`: 1.2 (settings declares exactly the
six includes), 1.3 (`core/domain/build.gradle.kts` is `jvm.library` + empty `dependencies`, no
`android {}`), 1.6 (all five properties present in `gradle.properties`), 1.15 (`pluginManagement {
includeBuild("build-logic") }` is the first block of root `settings.gradle.kts`), 1.24 (all six ids
registered; all six resolve — the build configures with no "was not found"), 1.31 (grep above, zero
hits), 1.32 (six-script table above), 1.35 (cold x2 above), 1.36 (APK above).

Task 1.12's parenthetical "(APK built successfully; on-device launch not verified — no
device/emulator in this environment)" is now stale — the launch **is** verified. Cosmetic; noted
under SUGGESTION.

PR 2 (2.2–2.15) and PR 3 (3.1–3.17) remain unchecked. That is the accepted chain, not a defect,
but it does mean the **change** cannot be archived yet.

## Spec compliance — build-foundation (11 requirements)

| Requirement | Status | Evidence |
|---|---|---|
| Six-module graph with fixed identities | PASS | `settings.gradle.kts` declares exactly six includes; `./gradlew projects` lists exactly those, plus the container projects `:core`/`:feature` inherent to nested paths |
| Domain layer is Android-free (2 scn) | PASS | dependency report clean; `compileKotlin` succeeds; no `android {}` block in `core/domain/build.gradle.kts` |
| Explicit module identity | PASS | explicit `namespace` in all five Android scripts; `resourcePrefix` derived from `project.path` in `AndroidLibraryConventionPlugin` — the scenario explicitly allows "or the convention plugin it applies" |
| Single owner for shared build values (2 scn) | PASS | repo-wide grep returns zero hits outside `build-logic` |
| Module scripts are plugin application plus dependencies only | **PASS (was the CRITICAL)** | amended requirement + six-script table above |
| Included build resolves before the root build's plugin blocks | PASS | `includeBuild("build-logic")` is line 2 of root settings; all six `com.petmephone.*` ids resolve |
| Configuration cache reuse survives the whole slice | PASS | cold x2 above |
| The application module remains installable throughout | **PASS (was unverifiable)** | install + launch + surviving PID + empty crash buffer above |
| No Compose artifact covered by the BOM carries an explicit version | PARTIAL — PR 2 (#3) | true for the entries that exist; catalog not yet complete |
| KSP version is coupled to the Kotlin version | PARTIAL — PR 2 (#3) | `kotlin = "2.2.10"`, `ksp = "2.2.10-2.0.2"`, coupling comment present; `kotlin-compose` entry is task 2.4 |
| Test source sets exist with zero tests (2 scn) | OUT OF SCOPE — PR 2 (#3) | no module declares test sources; confirmed by `find` |

**8 of 11 build-foundation requirements pass; the 3 remaining belong to PR 2 (#3).** No
build-foundation requirement in PR 1 scope is now unsatisfied.

## Spec compliance — dependency-injection (9 requirements)

Entirely PR 3 (#6) scope. **Explicitly not failed here and not claimed here.** Two are structurally
prepared by PR 1 — "KSP is applied to every module that declares an annotation Hilt processes"
(`AndroidHiltConventionPlugin` applies `com.google.devtools.ksp` to `:core:data`,
`:feature:overlay`, `:feature:tasks`, `:app`) and "Configuration cache tolerates Hilt and KSP"
(demonstrated above) — but both are vacuously true until #6 introduces the first annotation and
must be re-verified then.

## Design coherence

| Design element | Status |
|---|---|
| Module graph and per-module plugin table | MATCHES |
| build-logic layout, ProjectConfig, CatalogExt runtime accessor | MATCHES |
| `.compose` uses `CommonExtension` property access, no star projection, no `androidComponents`, no Activity artifact | MATCHES |
| `.library` deliberately does not set `namespace` | MATCHES |
| `pluginManagement` includeBuild first | MATCHES |
| `.room` extracted despite one consumer | MATCHES (recorded, revisitable) |
| `.hilt` = ksp + hilt plugin + `hilt-android` + `hilt-android-compiler` + `hilt-work` + `androidx.hilt:hilt-compiler` | MATCHES, **plus one undocumented addition**: `androidx-work-runtime-ktx`. Justified and commented in code, but design.md's plugin table (line 62) was not updated. See W2. |
| "jvmToolchain is set once per plugin family" | **DEVIATION, still unrecorded outside a code comment.** Only `JvmLibraryConventionPlugin` calls `jvmToolchain(...)`; the two Android plugins use `compileOptions { … JavaVersion.toVersion(ProjectConfig.jvmToolchain) }`, forced by AGP 9.3.1 built-in Kotlin. Correct and single-sourced. See W1. |
| `LibraryExtension` sets sdk levels incl. `targetSdk` | DEVIATION (justified, recorded in apply-progress finding 3) |

## apply-progress.md honesty — still incomplete

`apply-progress.md` was **not** touched by `d1b9717` or `42ddf8a` (`git show --stat` confirms:
d1b9717 touched spec.md, tasks.md, verify-report.md; 42ddf8a touched the Hilt plugin and the
catalog). It therefore:

- **does** honestly record the three AGP 9.3.1 environment findings, the early version pins, and
  the namespace tension — including the sentence "This reading was necessary to proceed; it should
  be confirmed or corrected at verify/review time", which is exactly the right posture and is what
  surfaced the CRITICAL;
- **still does not** record the MainActivity retention deviation;
- **still does not** record the jvmToolchain/compileOptions deviation;
- **still does not** record the WorkManager crash and its fix — the single most consequential event
  of this PR;
- **still states** "On-device install/launch NOT verified — no device or emulator in this
  environment", which is now false.

The file is honest about what it covers and silent about the rest. That silence is the one
remaining substantive gap, and it is documentation, not code.

## Adjudications requested

### 1. `.hilt` adds `hilt-work` + `work-runtime-ktx` to every consumer — acceptable for now, not a blocker

`.hilt` is applied to `:core:data`, `:feature:overlay`, `:feature:tasks`, and `:app`, though only
`:app` will host workers.

**Not a defect that should block PR 1**, for three reasons. The runtime graph is identical either
way: `:app` depends on all four modules, so `work-runtime` lands in exactly one APK regardless of
which module declares it — there is no size, method-count, or duplicate-initializer cost. The
declarations are `implementation`, not `api`, so they do not leak onto any consumer's compile
classpath transitively. And design.md line 62 already specifies `hilt-work` as part of `.hilt`, so
the current shape is the design's shape; only `work-runtime-ktx` is new, and it exists solely to
pin the version `hilt-work` would otherwise floor at 2.3.4.

**It is nonetheless imprecise**, and the imprecision has a real edge: a developer reading
`:feature:overlay` sees WorkManager on the classpath and may reasonably conclude workers belong
there, which contradicts the design's single-Hilt-root / single-WorkManager-configuration model.
The proportionate response is a follow-up in PR 3 (#6), where the worker story actually lands:
split `.hilt` into a base variant and a `.hilt.work` variant applied only to `:app`, or keep one
plugin and add a comment naming `:app` as the only intended worker host. Recorded as SUGGESTION 1,
not a blocker.

### 2. `android.disallowKotlinSourceSets=false` — still no recorded exit condition

Re-confirmed load-bearing at `42ddf8a`: AGP 9.3.1 built-in Kotlin → classic
`org.jetbrains.kotlin.android` unusable → KSP cannot register generated source dirs via
`kotlin.sourceSets` → Hilt and Room do not build. The four-line comment in `gradle.properties`
states the mechanism and links `developer.android.com/r/tools/built-in-kotlin`.

The gap the previous run identified is **unchanged**: the comment says why the flag is there, never
what would let it leave. AGP marks the property experimental and eventually-deprecated, so the flag
will be removed by someone else's release, not by this project's choice. Without a written exit
condition, the first AGP upgrade that drops it produces an unexplained build failure in a file
nobody owns.

This is a WARNING rather than a CRITICAL because the failure mode is a loud build break at upgrade
time, not silent wrong behaviour. Concrete closure: append one sentence to the existing comment —
"Exit condition: re-test on every AGP major; remove when AGP's built-in Kotlin registers KSP
generated sources natively, or when the property no longer exists" — and open a tracking issue
pinned to the AGP version. A comment alone will not survive three upgrades.

## Findings

### CRITICAL

**None.** The previous CRITICAL is resolved, and re-verification of the corrected tree (including
runtime execution on a real device) surfaced no new one.

### WARNING

1. **`apply-progress.md` still under-records deviations, and now contains a false statement.** The
   MainActivity retention, the jvmToolchain/compileOptions deviation, and the entire WorkManager
   crash-and-fix are absent, and its "no device or emulator in this environment" line is now
   untrue. Update before archive; this is the change's own memory of what happened.
2. **design.md's `.hilt` plugin table does not list `androidx-work-runtime-ktx`.** The code adds it
   with a good comment; the design the code is verified against does not mention it. One-line
   amendment.
3. **`android.disallowKotlinSourceSets=false` has no recorded exit condition.** See Adjudication 2.
4. **Issue #1's acceptance criterion "No template source files remain" is internally contradictory**
   with its own "launches to a blank screen" criterion. Unchanged from the previous run. Worth a
   comment on the issue so the next reader does not re-open it as a defect.

### SUGGESTION

1. Split `.hilt` so WorkManager reaches only `:app` (Adjudication 1) — do it in PR 3 (#6).
2. Task 1.12's parenthetical "on-device launch not verified" is stale; the launch is now verified.
3. tasks.md 2.2/2.3 are partly discharged by PR 1 but still `[ ]`. Deliberate is fine; a one-line
   note would stop PR 2 from re-litigating it.
4. `.idea/gradle.xml` was committed in `3e355a7`; `.idea/deploymentTargetSelector.xml` is dirty in
   the working tree right now. Gitignore `.idea/` state files.
5. `app/src/main/java/...` remains while every new module uses `src/main/kotlin/`. A rename in PR 3
   (when MainActivity is rewritten anyway) would settle it.
6. Carried from the previous run and still open: the dropped `optimization { enable = false }`
   release block should be noted in #4; unused template catalog entries (androidx-junit,
   androidx-espresso-core, androidx-lifecycle-runtime-ktx, androidx-activity-compose) should be
   pruned in PR 2; `AndroidComposeConventionPlugin` should move to the `compose-ui` bundle when
   `[bundles]` lands.

## Verdict

**PASS WITH WARNINGS for PR 1.** Every `build-foundation` requirement in PR 1 scope now passes,
including the two that previously did not: the module-script shape (the spec contradiction is
amended and all six scripts respect the narrowed rule) and installability (the APK installs,
launches, and the process survives with an empty crash buffer on `emulator-5554`). All 36 PR 1
tasks are done and their text now matches the tree. The WorkManager fix resolves to 2.11.2 and is
durable by construction. Cold configuration-cache reuse holds with the cache directory deleted
first. Four warnings remain, all documentation rather than code, and none blocks merging PR 1.

**The change is not archivable yet**, for a scheduled reason rather than a defect: requirement
coverage is 8/20 and scenario coverage 10/25 because PR 2 (#3) and PR 3 (#6) have not started.
Archive after those land and are verified; the `dependency-injection` spec has not been claimed by
any evidence in this run.
