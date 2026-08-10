```yaml
schema: gentle-ai.verify-result/v1
evidence_revision: sha256:78fb1b26e7d53880fc2503586e2c76df5214af28fe66f0e53366c8fe78e1ee36
verdict: fail
blockers: 0
critical_findings: 0
requirements: 8/20
scenarios: 10/25
test_command: "adb shell pidof com.gcatcode.petmephone"
test_exit_code: 0
test_output_hash: sha256:63b3597c67b7b7ad4831f62cc59e5f76bad3de2a3f3ab750941dce1d008b10b7
build_command: "./gradlew build --configuration-cache"
build_exit_code: 0
build_output_hash: sha256:98b48dba952ae1e0a460e54e9052661aefe5e1a6df38c91ec96f257ab160de1c
```

> **Envelope verdict note.** The envelope reads `fail` because change-level coverage is 8/20
> requirements and 10/25 scenarios — PR 2 (#3) and PR 3 (#6) have not started, and the validator
> refuses a passing verdict on incomplete evidence. That is scheduled scope, not a defect. **Within
> PR 1's own scope the verdict is PASS WITH WARNINGS: zero CRITICAL findings, zero blockers.**

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
