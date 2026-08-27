# Design: A balance tuning panel compiled only into the debug variant (#92)

## Technical approach

Five pieces, all but one inside `app/src/debug/`.

1. **One debug package**, `com.gcatcode.petmephone.debug.tuning`, holding everything: the
   `@AndroidEntryPoint` launcher activity, the Compose panel, the pure row-state function, the
   rejection wording, and the input parser.
2. **Row state is a pure function** over `(field, stored)` returning everything a row renders. It
   calls `:core:domain`'s existing `resolve` — it never re-implements it.
3. **The panel reads the store directly** through `ConfigOverrideStore.override(field)` and writes
   through `set`/`reset`. The two `*ConfigSource` StateFlows are injected only to render an
   independent "in use" readout.
4. **Restart control** is `stopService` then `startService` on the same `Intent` shape
   `MainActivity.startOverlayService()` uses.
5. **CI** gains `assembleRelease` and a two-sided artifact check.

Nothing in `app/src/main`, `:core:*` or `:feature:overlay` main sources changes.

## Architecture decisions

| # | Decision | Choice | Rejected | Rationale |
|---|---|---|---|---|
| 1 | **Grep token** (the carried open question) | Two tokens. **T1** = the package path `com/gcatcode/petmephone/debug/tuning`, the exact form a DEX type descriptor stores. **T2** = a marker literal `PETMEPHONE_DEBUG_TUNING_PANEL`, declared once as a `const val` in the panel package and reused as the debug launcher's `android:label` string value | The activity's FQCN; a class simple name; a Compose function name | A class rename is an ordinary refactor; a **package** rename is not, and IDE refactors update it in exactly one place the test pins. T1 catches compiled code, T2 catches the string pool, `resources.arsc` and the binary manifest — the leak paths a dex-only grep misses |
| 1a | **Keeping the check honest** | The CI step is **two-sided**: both tokens must appear **≥1 time in the debug APK** (positive control) and **0 times in the release APK**. Either half failing fails the job | Asserting absence from release only | This is the answer to "a grep for a name nobody uses passes trivially". If a rename, a minifier, or a deleted marker makes the token dead, the positive control goes to zero and CI goes red *before* the negative half can pass vacuously. Absence is only evidence when presence is proven on the same run |
| 1b | Tokens are pinned in code | `TuningPanelMarkerTest` (debug unit test) asserts `TuningPanelActivity::class.java.name.startsWith("com.gcatcode.petmephone.debug.tuning.")` and that the marker `const val` equals the exact literal, and reads `.github/workflows/ci.yml` to assert both literals appear there | A comment saying "keep in sync" | The CI file and the source are two owners of one value. The test makes them one |
| 2 | **Where row state is computed** | `TuningRowState.kt`, pure Kotlin in the debug package: `fun <T : Comparable<T>> tuningRowOf(field: ConfigField<T>, stored: StoredOverride<T>): TuningRow`. No Android import, no Compose import, no coroutines | A `ViewModel` method; a Compose `remember` block | Every interesting case is a JVM test with nothing on the path. Same discipline `resolve` already follows |
| 3 | **How current value is obtained** | `store.override(field)` → `resolve(field, stored)`. **The same function `BalanceConfigSourceImpl` and `PetAnimationConfigSource` call**, so there is one resolution path, not two | Deriving each row's value from the source StateFlows via per-field accessors | Reading the value off `BalanceConfig` needs a `(BalanceConfig) -> T` accessor per field — a second registry restating `ConfigField`, which the proposal forbids. Folding `resolve` over the same stored flow is equal by construction, because that is literally what the sources do |
| 3a | The sources are still injected | A collapsed **"in use"** readout renders `balanceConfigSource.config` and `petAnimationConfigSource.config` as their `toString()` | Not injecting them at all | It is the cheapest possible independent witness that the running app agrees with the rows. If they ever disagree, the panel shows it rather than hiding it behind a shared derivation |
| 4 | **Rejection wording** | `TuningRejectionCopy.kt`, pure: `fun rejectionMessage(rejection: ConfigWriteResult.OutOfRange<*>): String`, plus `parseTypedValue(field, text): ParsedInput` (`Valid<T>` / `Unparseable`) | Wording inline in the composable; wording in `:core:domain` | `ConfigWriteResult` is typed data by decision 4 of #91 and must stay wording-free. A pure function is a JVM test; a composable is a Compose test. The parser is here too because "not a number" is a rejection the store never sees |
| 5 | **Restart control** | `context.stopService(Intent(context, PetOverlayService::class.java))` then `startService` with an identical `Intent`. No method added to `PetOverlayService` | `PetOverlayService.restart()`; `stopSelf` plus a broadcast | `onCreate`/`onStartCommand` reconstruct the window from persisted and live state with no `Intent` extras (the service returns `START_STICKY` on exactly that basis) and `onDestroy` tears down fully. Adding a restart method to shared code is the leak this issue forbids |
| 6 | **Source set layout** | `app/src/debug/java/...` for code, `app/src/debug/res/values/strings.xml` for the marker label, `app/src/test/java/...` for the JVM tests | `app/src/test` | Verified against the build, not assumed — see the correction section at the end. `src/test` does see debug main sources, and `testReleaseUnitTest` does not exist in this project, so `src/testDebug` would solve a problem that does not occur. Note `:app` uses `java/` as its source directory name, not `kotlin/`, matching `app/src/main/java` and `app/src/test/java` |

## Data flow

```
ConfigOverrideStore.override(field) ──► StoredOverride<T>
        │
        └─► resolve(field, stored)              [:core:domain — the ONLY resolution path]
                 │
                 └─► tuningRowOf(field, stored) ──► TuningRow
                          shippedDefault, currentValue, overridden,
                          staleness ∈ {Fresh, Stale(v), NotVersioned},
                          application ∈ {LIVE, NEXT_SERVICE_START}
                                   │
                                   └─► TuningPanelScreen  (Compose, debug only)

[write]  text ─► parseTypedValue(field, text)
                     Unparseable → rejectionMessage, typed text KEPT on screen
                     Valid(v)    → store.set(field, v)
                                      Accepted   → row re-emits from the store
                                      OutOfRange → rejectionMessage(min,max,offending),
                                                   typed text KEPT on screen

[reset]  per-field → store.reset(field)                 no confirmation
         all       → confirm dialog → store.reset(f) for each f in ALL

[restart] stopService(Intent(ctx, PetOverlayService)) ; startService(same shape)

[witness] BalanceConfigSource.config / PetAnimationConfigSource.config  → "in use" readout
```

## Interfaces

```kotlin
package com.gcatcode.petmephone.debug.tuning   // app/src/debug/java — the grep token T1

/** The literal CI greps for (token T2). Pinned by TuningPanelMarkerTest. */
const val TUNING_PANEL_MARKER = "PETMEPHONE_DEBUG_TUNING_PANEL"

sealed interface Staleness {
    data object Fresh : Staleness
    /** [writtenUnderVersion] may be null: an entry predating the version stamp. */
    data class Stale(val writtenUnderVersion: Int?) : Staleness
    /** group.currentVersion == null — the check structurally never ran. Never "Fresh". */
    data object NotVersioned : Staleness
}

enum class ValueApplication { LIVE, NEXT_SERVICE_START }

data class TuningRow(
    val key: String,
    val groupId: String,
    val shippedDefault: String,
    val currentValue: String,
    val overridden: Boolean,
    val rangeLabel: String,
    val staleness: Staleness,
    val application: ValueApplication,
)

/** Pure. No Android, no Compose, no coroutines. Calls resolve; never re-derives it. */
fun <T : Comparable<T>> tuningRowOf(field: ConfigField<T>, stored: StoredOverride<T>): TuningRow

sealed interface ParsedInput<out T> {
    data class Valid<T>(val value: T) : ParsedInput<T>
    data object Unparseable : ParsedInput<Nothing>
}
fun <T : Comparable<T>> parseTypedValue(field: ConfigField<T>, text: String): ParsedInput<T>
fun rejectionMessage(rejection: ConfigWriteResult.OutOfRange<*>): String
fun unparseableMessage(field: ConfigField<*>): String
```

`ValueApplication` is `LIVE` for every field in both registries today, decided by the group id, not
hardcoded per row — the follow-up slice that routes the snapshot configs changes one `when` branch.

## File changes

| Path | Action | Purpose |
|---|---|---|
| `app/src/debug/java/.../debug/tuning/TuningRowState.kt` | Create | Pure row state, `Staleness`, `ValueApplication`, marker const (decisions 1, 2) |
| `app/src/debug/java/.../debug/tuning/TuningRejectionCopy.kt` | Create | Parser and wording (decision 4) |
| `app/src/debug/java/.../debug/tuning/TuningPanelViewModel.kt` | Create | `@HiltViewModel`; combines `ALL` overrides into rows; `set`/`reset`/reset-all/restart |
| `app/src/debug/java/.../debug/tuning/TuningPanelScreen.kt` | Create | Compose panel, `PetMePhoneTheme`, `safeDrawing` insets, confirm dialog, "in use" readout |
| `app/src/debug/java/.../debug/tuning/TuningPanelActivity.kt` | Create | `@AndroidEntryPoint ComponentActivity` (decision 6) |
| `app/src/debug/res/values/strings.xml` | Create | Marker label string (token T2) |
| `app/src/debug/AndroidManifest.xml` | Modify | Second `MAIN`/`LAUNCHER` `<activity>` beside the existing `exported` override |
| `app/src/test/java/.../debug/tuning/*Test.kt` | Create | Row-state matrix, copy, parser, marker pinning |
| `.github/workflows/ci.yml` | Modify | `assembleRelease` + two-sided artifact inspection |

## The CI step, concretely

Placement: `assembleRelease` is appended to the **existing** Gradle invocation (one daemon session),
and the inspection becomes a **new step immediately after it**, before the upload steps. It has no
`if: always()` — it must gate, not report.

```bash
# Step: Verify the tuning panel is absent from the release artifact
set -euo pipefail
TOKENS=('com/gcatcode/petmephone/debug/tuning' 'PETMEPHONE_DEBUG_TUNING_PANEL')

inspect() {                       # $1 = apk glob, $2 = expected 'present'|'absent'
  local apk; apk=$(ls $1)
  [ "$(ls $1 | wc -l)" -eq 1 ] || { echo "::error::expected exactly one APK at $1"; exit 1; }
  local dir; dir=$(mktemp -d); unzip -q -o "$apk" -d "$dir"
  for t in "${TOKENS[@]}"; do
    n=$(LC_ALL=C grep -r -a -l -F "$t" "$dir" | wc -l)
    if [ "$2" = absent  ] && [ "$n" -ne 0 ]; then echo "::error::'$t' leaked into $apk"; exit 1; fi
    if [ "$2" = present ] && [ "$n" -eq 0 ]; then echo "::error::'$t' is dead — the release check guards nothing"; exit 1; fi
  done
}
inspect 'app/build/outputs/apk/debug/*.apk'   present
inspect 'app/build/outputs/apk/release/*.apk' absent
```

Three deliberate details. **`set -euo pipefail`**, because `grep` exiting 1 on no-match is the
normal case and an unguarded pipeline would swallow a real failure. **A glob, not a filename**: with
no signing config AGP emits `app-release-unsigned.apk`, and it emits `app-release.apk` the day a
signing config appears — the "exactly one APK" assertion turns a future AGP output-path change into
a red build rather than a silently-skipped check. **Whole-APK extraction, not `unzip -p classes*.dex`**,
because `resources.arsc`, the binary manifest and `res/` are leak paths a dex-only grep cannot see.

## Testing strategy

| Layer | What | How |
|---|---|---|
| Pure JVM (`test`) | `tuningRowOf` over the matrix: absent → not overridden, current = shipped default; present in range → overridden, current = stored; present out of range → `SHIPPED_DEFAULT_RANGE_NARROWED`, current = default, still overridden; present under an older balance version → `Stale(v)`; every `PetAnimationConfig.ALL` field → `NotVersioned`, **never `Fresh`** | JUnit4, no Android |
| Pure JVM (`test`) | Registry coverage: `tuningRowOf` produces a row for all 5 + 3 fields with unique keys; `NotVersioned` occurs iff `group.currentVersion == null` | JUnit4 over `ALL` |
| Pure JVM (`test`) | `parseTypedValue` per field type incl. blank, `"abc"`, `"1e9"`, `"0.6"` into an `IntField`; `rejectionMessage` names min, max and the offending value; wording contains no display copy borrowed from `:core:domain` | JUnit4 |
| Pure JVM (`test`) | `TuningPanelMarkerTest` — package prefix, marker literal, and both tokens present in `ci.yml` (decision 1b) | JUnit4 + `File` read |
| Source scan (`test`) | **No file under `app/src/main`, `core/`, or `feature/` contains the string `debug.tuning` or `TUNING_PANEL_MARKER`** — the in-repo mirror of the CI check, failing in seconds instead of after a release build | `File.walkTopDown()`, `ConfigStoreNoBulkWriteCodeTest` shape |
| Source scan (`test`) | `BuildConfig.DEBUG` appears nowhere under `app/src/debug/` | Same shape |
| Compose UI | **None.** | See below |
| Manual, device | A `hungryThresholdRatio` change is visible on the running overlay without leaving the panel; the restart control produces one window, not two | Device pass, per the standing "launching is not verifying" rule |

**No Compose UI test.** Every claim worth pinning — the three staleness states, the rejection
wording, the kept-value-on-rejection rule, liveness — is decided in pure functions the panel only
renders. A Compose test here would assert that `Text(row.currentValue)` displays
`row.currentValue`. The two claims a UI test *could* add (the confirm dialog gates reset-all; a
rejected write leaves the text field populated) are single-screen behaviour on a maintainer
instrument, and are covered by the device pass. The structural scan is warranted and the UI test is
not, because the leak is the risk this change actually carries.

## Threat matrix

| Boundary | Applicable | Expected behaviour | RED test |
|---|---|---|---|
| Executable-artifact classification (does the release APK contain debug code?) | **Applicable** | Both tokens absent from the release APK **and** present in the debug APK; either violation fails the job | The two-sided CI step; the in-repo source scan |
| Shell command in CI | **Applicable** | `set -euo pipefail`; a missing or ambiguous APK path fails rather than skipping; no interpolation of untrusted input into the shell | The "exactly one APK" assertion |
| Untrusted value crossing the store's write boundary | **Applicable** — the panel is the first real driver of `set` | Unparseable text never reaches `set`; out-of-range is rejected typed, never clamped, and the typed text stays on screen | Parser and `rejectionMessage` tests |
| Routing, subprocess, VCS/PR automation, process integration | N/A | No such boundary in this change | — |

## Migration / rollout

No migration. Delete `app/src/debug/java/.../debug/tuning/`, `app/src/debug/res/`, the manifest
`<activity>`, and the two CI edits. Override entries a session persisted stay in `petmephone_prefs`
and keep resolving through #91's store — removing the panel removes a writer, not a schema.

## Revised changed-line estimate (code only)

| Component | Estimate |
|---|---|
| `TuningRowState.kt` (KDoc-heavy, per house style) | 70–95 |
| `TuningRejectionCopy.kt` (parser + wording) | 45–65 |
| `TuningPanelViewModel.kt` | 80–110 |
| `TuningPanelScreen.kt` (rows, dialog, restart, "in use" readout, insets) | 110–150 |
| `TuningPanelActivity.kt` + debug `strings.xml` | 25–40 |
| Debug manifest addition | 12–20 |
| Pure JVM tests (matrix, registry, parser, copy, marker) | 130–180 |
| Source-scan tests (2) | 50–75 |
| CI: `assembleRelease` + inspection step | 40–60 |
| **Total** | **~562–795** |

**800-line budget risk: Medium.** Higher than exploration's 320–530, because the marker pinning
(decision 1b), the in-repo source scan, and the two-sided CI shell are new since then. The top of
the range sits just inside 800. **No split is planned**, but the natural seam if one is needed is
pure logic and its tests (~245–340) ahead of the Compose surface — the pure half ships green with
no consumer, exactly as #91's Slice A did.

## Open questions

- [ ] R8 is not enabled on release today, so token T1 survives as a plain package path. If
      minification is ever turned on, T1 could vanish from the release APK for an innocent reason —
      but the debug positive control keeps passing, so the check would still be honest, just weaker.
      The marker string T2 is the durable half.
- [ ] A stale row's affordance is display only, per the proposal's fourth question left to design.
      `Stale(writtenUnderVersion)` carries the number, so a one-tap "reset stale rows" is additive
      later with no shape change.

## Correction — unit tests live in `src/test`, not `src/testDebug`

This design originally placed the panel's unit tests in `app/src/testDebug`, reasoning that `src/test`
compiles into every variant and therefore cannot reference debug-only main sources. That reasoning was
checked against the build rather than accepted, and it is wrong here on two counts.

**`src/test` can see the debug main source set.** A class was placed in `app/src/debug/java` and a test
in `app/src/test/java` referencing it; `:app:testDebugUnitTest` compiled and passed. When AGP builds
the debug variant it merges `main` + `debug` before compiling, and those classes are on the unit-test
compile classpath.

**There is no release unit-test variant at all.** `./gradlew :app:tasks --all` lists exactly one:
`testDebugUnitTest`. `testReleaseUnitTest` does not exist in this project, so there is no variant in
which a `src/test` file referencing debug code could break.

So `src/testDebug` would have been the first and only directory of its kind in the repository, added to
solve a problem that does not occur. The tests go in `app/src/test/java`, beside every other test the
module already has.

The rest of the design is unaffected: the panel's production code still lives only in `app/src/debug`,
and the source set is still the gate. Only the test location changes.

**One consequence worth carrying to `sdd-tasks`:** because `src/test` can reference debug-only code, a
test file is not a barrier against the panel leaking. The release-artifact check remains the only
mechanical guard, which raises rather than lowers its importance.
