```yaml
schema: gentle-ai.verify-result/v1
evidence_revision: sha256:a65735e9983f50908ac214148ad1d7eceff7c76ee07e6a2fdc4bb1203cb6da48
verdict: fail
blockers: 1
critical_findings: 1
requirements: 6/20
scenarios: 8/25
test_command: "./gradlew :core:domain:dependencies"
test_exit_code: 0
test_output_hash: sha256:2c195488847a4fbe4d5455d6eedb0b53e09df09aa5224c1eeafac1f0ba792428
build_command: "./gradlew build --configuration-cache"
build_exit_code: 0
build_output_hash: sha256:14d33ffe6f0e0edb7df9cdb1dc31ac14d9f6f6e92125e368c610e9fb969c4d72
```

# Verification Report — slice-1-foundation, PR 1 (issues #1 + #2)

Requirement/scenario totals span BOTH delta specs (`build-foundation` 11 req / 14 scn,
`dependency-injection` 9 req / 11 scn). PR 1 is only the first of three PRs in this change, so the
uncompleted majority is scheduled scope, not defect.

## Task completeness — PR 1 (1.1–1.36)

35 of 36 checked tasks verified as genuinely done. One is checked but was not performed as written.

| Task | Claim | Actual |
|---|---|---|
| 1.1 | `git rm` MainActivity.kt, ui/theme/*, test stubs | **PARTIAL.** `ui/theme/{Color,Theme,Type}.kt`, `ExampleUnitTest.kt`, `ExampleInstrumentedTest.kt` are deleted in `bf643d1`. `MainActivity.kt` was **not** removed — it was rewritten to a blank `android.app.Activity` and still exists at `app/src/main/java/com/gcatcode/petmephone/MainActivity.kt`. Compose removal from `app/build.gradle.kts` is done. |
| 1.2–1.12 | skeleton + verifications | Confirmed against `bf643d1`; post-#1 scripts genuinely carry duplicated compileSdk/minSdk/targetSdk/VERSION_11 literals. |
| 1.13–1.31 | build-logic extraction | Confirmed file-by-file; all six plugin ids registered and all six modules consume them. |
| 1.32 | module scripts reduced to plugins + dependencies | Five of six also carry `android { namespace = ... }` — see Adjudication 1. |
| 1.33–1.36 | verifications | Re-executed here, all green. |

Retaining a blank `MainActivity` is the *correct* engineering call: the manifest declares it as the
LAUNCHER activity, and both issue #1 and spec `build-foundation` Requirement "The application module
remains installable throughout" demand an APK that "launches to a blank screen without crashing".
Deleting it would have made that requirement unsatisfiable. Issue #1's own acceptance criterion "No
template source files remain" contradicts its own "launches to a blank screen" criterion. The defect
is therefore in task 1.1's wording and in the fact that `apply-progress.md` does not record this
deviation at all — the one deviation it silently made while documenting three others carefully.

## Runtime evidence (all re-executed, not inherited)

| Command | Result |
|---|---|
| `./gradlew projects` | BUILD SUCCESSFUL. Exactly the six modules. Gradle also renders the intermediate container projects `:core` and `:feature`; these are not declared in settings.gradle.kts and hold no build script — inherent to nested paths, and the spec's own word "correctly nested" anticipates them. |
| `./gradlew :core:domain:dependencies` | BUILD SUCCESSFUL. Zero `androidx.*` / `android.*` in any configuration. testRuntimeClasspath = kotlin-stdlib:2.2.10 + junit:4.13.2 + hamcrest-core:1.3 only. |
| `./gradlew :core:domain:compileKotlin` | BUILD SUCCESSFUL, NO-SOURCE (module is empty). |
| `./gradlew build --configuration-cache` x2, **cold** (configuration-cache dir deleted first) | Run 1 SUCCESSFUL, "Configuration cache entry stored." Run 2 SUCCESSFUL in 2s, "**Configuration cache entry reused.**" No configuration-cache problems or warnings emitted. |
| `./gradlew :app:assembleDebug` | BUILD SUCCESSFUL. `app/build/outputs/apk/debug/app-debug.apk`, 12,363,362 bytes. |
| `./gradlew :app:assembleDebug --configuration-cache` | BUILD SUCCESSFUL, no Hilt/KSP-attributable warning. |
| APK install + on-device launch | **UNVERIFIABLE HERE.** No device, no emulator. Not a pass and not a failure. |

## Spec compliance — build-foundation

| Requirement | Status | Evidence |
|---|---|---|
| Six-module graph with fixed identities | PASS | `./gradlew projects`; settings.gradle.kts declares exactly six include paths |
| Domain layer is Android-free, enforced by the build | PASS (2/2 scenarios) | dependency report clean; compileKotlin succeeds; core/domain/build.gradle.kts has no `android {}` block |
| Explicit module identity | PASS | explicit `namespace` in all five Android module scripts; `resourcePrefix` derived in AndroidLibraryConventionPlugin from `project.path` (`:core:data` → `core_data_`) — the spec scenario explicitly allows "or the convention plugin it applies" |
| Single owner for shared build values (2 scenarios) | PASS | repo-wide grep for compileSdk/minSdk/targetSdk/jvmToolchain/VERSION_11/kotlinOptions/jvmTarget returns hits **only** inside build-logic, all reading ProjectConfig |
| Module scripts are plugin application plus dependencies only | **NOT SATISFIED AS WRITTEN** | five module scripts carry `android { namespace = ... }`; see Adjudication 1 |
| Included build resolves before the root build's plugin blocks | PASS | `pluginManagement { includeBuild("build-logic") }` is the first block of root settings.gradle.kts; all six com.petmephone.* ids resolve with no "was not found" error |
| No Compose artifact covered by the BOM carries an explicit version | PARTIAL — PR 2 scope (#3) | already true for the entries that exist (ui, ui-graphics, ui-tooling, ui-tooling-preview, material3, ui-test-* carry no version.ref; BOM applied via `implementation(platform(...))` in AndroidComposeConventionPlugin). Catalog is not yet complete. |
| KSP version is coupled to the Kotlin version | PARTIAL — PR 2 scope (#3) | kotlin = "2.2.10", ksp = "2.2.10-2.0.2", coupling comment present, kotlin-compose uses version.ref = "kotlin". Substantively already satisfied. |
| Test source sets exist with zero tests | OUT OF SCOPE — PR 2 (#3) | not fully wired; no module declares test sources |
| Configuration cache reuse survives the whole slice | PASS | cold x2 above |
| The application module remains installable throughout | PARTIAL | APK produced; install + launch unverifiable without a device |

## Spec compliance — dependency-injection

Entirely PR 3 (#6) scope. Not failed here, not claimed here.
Two requirements are already structurally prepared by PR 1: "KSP is applied to every module that
declares an annotation Hilt processes" (AndroidHiltConventionPlugin applies
`com.google.devtools.ksp` to `:core:data`, `:feature:overlay`, `:feature:tasks`, `:app`) and
"Configuration cache tolerates Hilt and KSP" (demonstrated above). Both are vacuously true until
#6 introduces the first annotation, and must be re-verified then.

## Design coherence

| Design element | Status |
|---|---|
| Module graph and per-module plugin table | MATCHES exactly |
| build-logic layout, ProjectConfig, CatalogExt runtime accessor | MATCHES |
| .compose uses CommonExtension property access, no star projection, no androidComponents, no Activity artifact | MATCHES; Question B not re-spiked |
| .library deliberately does not set namespace | MATCHES |
| pluginManagement includeBuild first | MATCHES |
| .room extracted despite one consumer | MATCHES (recorded, revisitable) |
| "jvmToolchain is set once per plugin family" | **DEVIATION.** Only JvmLibraryConventionPlugin calls `jvmToolchain(...)`. The two Android plugins instead set `compileOptions { sourceCompatibility/targetCompatibility = JavaVersion.toVersion(ProjectConfig.jvmToolchain) }`, forced by AGP 9.3.1 built-in Kotlin. Correct and single-sourced, but the deviation is documented only in a code comment, not in apply-progress.md. |
| LibraryExtension sets sdk levels incl. targetSdk | **DEVIATION (justified, recorded).** LibraryExtension.defaultConfig has no targetSdk on AGP 9.3.1; library modules set minSdk only. Recorded in apply-progress.md finding 3. |
| app `buildTypes { release { optimization { enable = false } } }` present after #1 | dropped in #2, not carried into AndroidApplicationConventionPlugin. Consistent with the design's "deliberately does NOT: signing, R8 (#4)" and with AGP's default (minify off), so behaviour is unchanged — but it is an undocumented silent drop. |

---

## Adjudications requested

### 1. The namespace tension — the SPEC is wrong, not the implementation

`build-foundation` Requirement "Module scripts are plugin application plus dependencies only" says
"no `android {}`, `kotlin {}`, or compiler-option block appears in the module script itself." Under
AGP there is no API to set `namespace` outside `android {}`. That same spec file, one requirement
earlier, mandates "Every Android module SHALL declare an explicit `namespace`". Read literally, the
spec requires two things that AGP makes mutually exclusive.

Everything else points the same way: design.md lists "set `namespace` (module-specific)" under
android.library's *deliberately does NOT*; task 1.30 says namespace is "not owned by the plugin";
issue #1 requires "explicit `namespace` on every Android module". Three artifacts against one clause
of a fourth.

**The spec's scenario wording must change**, not the code. The requirement's real intent — visible in
its own title and prose ("with no AGP or Kotlin block-level configuration left inline") — is that no
*shared build configuration* remains inline. A four-line `android { namespace = "..." }` carrying
exactly one irreducibly module-specific identity is not shared configuration. Suggested amendment to
the scenario's THEN clause: *"no android block appears other than one containing namespace alone; no
kotlin block or compiler-option block appears at all."* That keeps the requirement falsifiable and
closes the contradiction. This is a spec-text correction, not a waiver: the amendment must land
before archive, because a spec that cannot be satisfied is not a spec.

### 2. android.disallowKotlinSourceSets=false — load-bearing, and adequately fenced

Load-bearing: yes, unambiguously. The chain is AGP 9.3.1 built-in Kotlin → classic
`org.jetbrains.kotlin.android` unusable → KSP cannot register generated source dirs through
`kotlin.sourceSets` → Hilt and Room, both KSP-based, do not build. Removing this line breaks
`:core:data`, `:feature:*` and `:app`. It is not a leftover.

Documentation: adequate, and better than typical. The four-line comment in gradle.properties states
the AGP mechanism, links developer.android.com/r/tools/built-in-kotlin, and names both the cause
(built-in Kotlin stays on) and the consumer (KSP, needed by Hilt/Room). It is also explained at
length in commit 3e355a7's message and in apply-progress.md. Someone tidying gradle.properties will
not delete it by accident.

One gap worth closing, not a defect: the comment does not say what to do when AGP eventually removes
the flag. The property is marked experimental/eventually-deprecated by AGP itself. Recommend adding
one sentence naming the exit condition — e.g. "re-test on each AGP major; the flag disappears when
AGP's built-in Kotlin supports KSP source registration natively" — and tracking it as a follow-up
issue rather than relying on this comment surviving three AGP upgrades.

### 3. Early version pins — legitimate necessity, do NOT move to PR 2

ksp = "2.2.10-2.0.2", hilt = "2.60.1", hiltExt = "1.4.0", room = "2.8.4" are not scope creep.
AndroidHiltConventionPlugin and AndroidRoomConventionPlugin call `libs.findLibrary("hilt-android")`,
("hilt-android-compiler"), ("androidx-hilt-work"), ("androidx-hilt-compiler"),
("androidx-room-runtime"), ("androidx-room-ktx"), ("androidx-room-compiler") — `.get()` on each.
Those catalog entries need `[versions]` refs or PR 1 does not configure, let alone compile. The ksp
version additionally backs ksp-gradlePlugin on build-logic's own classpath. This is the minimum
resolvable set for #2, and no more: no WorkManager, DataStore, Lottie, lib-recur, Turbine,
Robolectric, or MockK entry was added early.

Two things make it defensible rather than convenient: the versions were resolved against
repo1.maven.org / dl.google.com maven-metadata.xml rather than guessed, and the KSP/Kotlin coupling
comment the spec demands is already in place. PR 2's task 2.1 ("Resolve the exact KSP patch version")
is therefore already discharged and should be re-marked as such rather than re-litigated.

The residual risk is bookkeeping, and it is real: tasks.md still shows 2.1–2.5 unchecked while the
work behind 2.1 is done and part of 2.2/2.3 is done. Update tasks.md before PR 2 starts, or PR 2
will either duplicate the work or quietly skip a checkbox nobody re-reads.

### 4. Commit sequence legibility — genuinely legible, and the strongest part of this PR

The #1 → #2 narrative survives review, which was the entire justification for merging them.

- `bf643d1 feat(build): stand up six-module skeleton with duplicated scripts (#1)` — 30 files. The
  duplication is real and visible, not performative: core/data, core/designsystem, feature/overlay,
  feature/tasks each independently repeat `compileSdk { version = release(37) }`, `minSdk = 26`,
  `testInstrumentationRunner`, and `JavaVersion.VERSION_11`. Four near-identical 22-line scripts,
  side by side in one diff.
- `3e355a7 feat(build): extract build-logic convention plugins, remove duplication (#2)` — 22 files,
  +345/−96. Every one of those literals disappears from app/core/feature and reappears exactly once
  in ProjectConfig.kt. A reviewer reading commit 2's diff sees the deletion of precisely what commit
  1 introduced.
- Commit 2's message additionally records the three AGP 9.3.1 environment findings that changed the
  plan, so the deviations are legible from `git log` alone, without opening apply-progress.md.
- The two SDD bookkeeping commits (ecfb8cf, 21ac37e) are cleanly separated from both.

Verdict on the merge decision: justified in retrospect. Split apart, commit 2's diff would have been
a pile of new plugin classes with no visible motive.

---

## Findings

### CRITICAL

1. **`build-foundation` Requirement "Module scripts are plugin application plus dependencies only"
   is currently NOT satisfied, and cannot be satisfied as written.** Five of six module scripts
   carry an `android { namespace = ... }` block that the scenario's THEN clause forbids, while the
   preceding requirement, design.md and issue #1 all mandate a per-module `namespace` that AGP
   cannot express any other way. This is CRITICAL because it blocks archive, not because PR 1 is
   defective: the fix is the two-line spec amendment in Adjudication 1, applied by re-running
   `sdd-spec`. Do not change the build scripts.

### WARNING

1. **Task 1.1 is checked but was not performed as written.** MainActivity.kt still exists (rewritten
   as a blank Activity), and this deviation is absent from apply-progress.md — the only one of the
   four deviations that went unrecorded. The code is right; the task text and the progress record are
   wrong. Fix the task text to "reduce MainActivity.kt to a blank placeholder Activity (kept: it is
   the manifest LAUNCHER target)".
2. **build-foundation Requirement "Module scripts are plugin application plus dependencies only"
   cannot be satisfied as written.** Amend the scenario per Adjudication 1 before archive.
3. **Issue #1's acceptance criterion "No template source files remain" is internally contradictory**
   with its own "launches to a blank screen" criterion. Worth a comment on the issue so the next
   reader does not re-open it as a defect.
4. **Design deviation on the JVM toolchain is documented only in a source comment.** The Android
   plugins use compileOptions rather than jvmToolchain, contradicting design.md's "jvmToolchain is
   set once per plugin family". Add it to apply-progress.md alongside findings 1–4.
5. **On-device install and launch is unverified.** build-foundation Requirement "The application
   module remains installable throughout" has a scenario that requires a device or emulator. The APK
   builds; nothing here proves it launches. Carry this forward — do not let it be quietly inherited
   as passed by PR 2 and PR 3.
6. **tasks.md PR 2 checkboxes are now stale.** Task 2.1 is fully done and 2.2/2.3 are partly done,
   all still unchecked.

### SUGGESTION

1. `.idea/gradle.xml` was committed in 3e355a7. IDE state in a build-infrastructure commit is noise;
   consider gitignoring it.
2. `app/src/main/java/...` remains while every new module uses `src/main/kotlin/`. Harmless, but
   inconsistent; a rename in PR 3 (when MainActivity is rewritten anyway) would settle it.
3. The `optimization { enable = false }` release block from the template was dropped in #2 without
   comment. Behaviour is unchanged (AGP default), and R8 belongs to #4 — but note it in #4 so nobody
   assumes it was already configured.
4. gradle/libs.versions.toml still carries unused template entries (androidx-junit,
   androidx-espresso-core, androidx-lifecycle-runtime-ktx, androidx-activity-compose). PR 2 should
   prune activity-compose in particular, since design.md explicitly rejects the Activity-specific
   Compose artifact.
5. AndroidComposeConventionPlugin adds five Compose artifacts individually rather than via the
   compose-ui bundle design.md specifies. The bundle is PR 2 scope; switch to it when [bundles]
   lands.

## Verdict

**FAIL at the change level; PR 1 itself is sound and mergeable.** The envelope verdict is `fail`
for two reasons, neither of which is an implementation defect in PR 1:

1. Change-wide requirement coverage is 6/20 and scenario coverage 8/25, because PR 2 (#3) and PR 3
   (#6) have not started. That is the planned chain, not a regression.
2. One in-scope requirement is unsatisfied as written (the namespace clause above) and must be
   amended before this change can be archived.

Within its own scope, PR 1 delivers what issues #1 and #2 asked for. Every executable acceptance
criterion in scope passes on re-execution: six modules, an Android-free `:core:domain`, a single
`ProjectConfig` owner with zero literal duplicates anywhere outside `build-logic`, cold
configuration-cache reuse on the second consecutive build, and a 12 MB debug APK. The
duplication-then-removal narrative is genuinely legible across `bf643d1` and `3e355a7`, and the
three flagged apply-phase judgement calls all hold up under scrutiny. Nothing here blocks merging
PR 1; the blocker is a spec text correction that must land before archive.
