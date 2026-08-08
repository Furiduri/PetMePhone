```yaml
schema: gentle-ai.verify-result/v1
evidence_revision: sha256:0fbbaf73d69db116457a25590daf61c3c1d045236da2654361bcd9e29827ddca
verdict: fail
blockers: 0
critical_findings: 0
requirements: 11/20
scenarios: 14/25
test_command: "./gradlew test"
test_exit_code: 0
test_output_hash: sha256:6571b80c2b69dc58479f5de1a9c5f55735b27f546b767d5c38b17a052086845d
build_command: "./gradlew build --configuration-cache"
build_exit_code: 0
build_output_hash: sha256:0ccb74115c730862207762a44e8ee11ce4b998185af44f545a510138b7e385c5
```

> **Envelope verdict note.** The envelope reads `fail` because change-level coverage is 11/20
> requirements and 14/25 scenarios — PR 3 (#6) has not started and the entire `dependency-injection`
> spec is unclaimed by any evidence. That is scheduled scope, not a defect. **Within PR 2's own
> scope the verdict is PASS WITH WARNINGS: zero CRITICAL findings, zero blockers.** The
> `build-foundation` delta spec is now 11/11 requirements and 14/14 scenarios — complete.

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
