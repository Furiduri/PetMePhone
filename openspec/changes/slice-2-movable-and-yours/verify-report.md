```yaml
schema: gentle-ai.verify-result/v1
evidence_revision: sha256:286d5b854649c6c0fdbb72f44b52f7f90c3999e151ed2b403ad75dcdd23028b1
verdict: pass_with_warnings
blockers: 0
critical_findings: 0
requirements: 48/48
scenarios: 62/62
test_command: "./gradlew assembleDebug testDebugUnitTest assembleDebugAndroidTest lintDebug --rerun-tasks"
test_exit_code: 0
test_output_hash: sha256:3989cfa8e8696250e8b32bcb9f93ba19fba15fd4cae037e6a420636dfa6b4df2
build_command: "./gradlew assembleDebug testDebugUnitTest assembleDebugAndroidTest lintDebug --rerun-tasks"
build_exit_code: 0
build_output_hash: sha256:3989cfa8e8696250e8b32bcb9f93ba19fba15fd4cae037e6a420636dfa6b4df2
```

# Verification Report - slice-2-movable-and-yours (CLOSING, WHOLE CHANGE)

Supersedes the `fail` report written at 731bd39. Scope: `git diff master...HEAD` at
af4fb6c57d1a6b9d4307aabee2b5112b0190f563, branch `feat/slice-2-movable-and-yours`, PR #72.
Mode: openspec + engram.

## Verdict: PASS WITH WARNINGS - zero merge-blocking findings, 48/48 requirements, 62/62 scenarios

The single formal shortfall that held the previous pass at `fail` is closed. `[IMPORT-15]`
("Identity affordance renders with every character") is now satisfied by code on both render
paths and by an instrumented test that this pass proved can fail. Every declared command exits 0.

Four documentation/evidence warnings and the four carried non-blocking findings remain. None
blocks merge. Two of the warnings argue a rationale the code has since reversed, and because
archive freezes these artifacts, they are the only items worth fixing first.

## Runtime evidence (freshly regenerated this pass)

| Command | Exit | Result |
|---|---|---|
| `./gradlew assembleDebug testDebugUnitTest assembleDebugAndroidTest lintDebug --rerun-tasks` | 0 | BUILD SUCCESSFUL in 4m 7s; **468 actionable tasks, 468 executed, 0 UP-TO-DATE** |
| `./gradlew :core:domain:test --rerun-tasks` | 0 | BUILD SUCCESSFUL in 41s; 8 actionable tasks, 8 executed |
| `ANDROID_SERIAL=emulator-5554 ./gradlew :feature:overlay:connectedDebugAndroidTest` | 0 | 6/6 passed on Pixel_8(AVD) API 34, 0 skipped, 0 failed |

`adb devices` reported both `192.168.1.13:40313` (the maintainer's phone) and `emulator-5554`.
Every instrumented invocation in this pass pinned `ANDROID_SERIAL=emulator-5554`. The phone was
never targeted.

### Per-suite unit test counts, from freshly regenerated TEST-*.xml

| Module | Test classes | Tests | Failures | Errors |
|---|---|---|---|---|
| `:core:domain` | 11 | 54 | 0 | 0 |
| `:core:data` | 5 | 22 | 0 | 0 |
| `:feature:overlay` | 25 | 99 | 0 | 0 |
| `:app` | 2 | 8 | 0 | 0 |
| `:feature:design-system` | 0 | 0 | 0 | 0 |
| **Total** | **43** | **183** | **0** | **0** |

Instrumented: 6 tests, 0 failures, on API 34.

**Correction to the previous report's count.** The previous pass reported 184 unit tests. The
true current figure is 183. `:core:domain` is a plain JVM module with no `testDebugUnitTest`
task, so the workflow's declared command does not run it - its TEST-*.xml files were carried
over from an unrelated 15:47 run, stale by two commits and predating the pacing rewrite
entirely. This pass ran `:core:domain:test --rerun-tasks` separately to obtain real evidence.
The rewrite removed one assertion, so 55 became 54. See WARNING 4.

## [IMPORT-15] - the gap that held the previous pass, independently re-proved

`BrokenPlaceholder` now calls `drawIdentityAffordance()` inside its `drawBehind`, after
`drawBrokenShape()`. `ReadyPet` calls it after `drawImage` in the same `DrawScope`. Both render
paths draw the badge, so the affordance no longer depends on a successful decode.

The apply phase claimed its regression test had been confirmed to fail against pre-fix code.
This pass re-ran that experiment rather than accepting the claim. The `drawIdentityAffordance()`
call inside `BrokenPlaceholder` - and nothing else - was commented out, and the test re-run on
the emulator:

    PetOverlayFailurePlaceholderTest > theIdentityAffordanceRendersEvenWhenTheCharacterIsBroken FAILED
      java.lang.AssertionError: expected the identity badge's dark rim near (16,16) on a broken
      character; without it the affordance is conditional on a successful decode

The sibling `decodeFailureRendersBrokenShapeNeverBlank` still passed, confirming the failure was
attributable to the removed hunk and not to a broken harness. The working tree was restored to
byte-identical state afterwards (`git status --porcelain` clean). The test is **not vacuous** and
the claim in commit c9845e6 holds.

Analytically this is also sound: `drawBrokenShape` paints only `Color.Red`, the badge rim is the
only `Color.Black` geometry on the placeholder, and the assertion requires alpha>200 with r, g, b
all below 60 - a predicate no red stroke and no white background can satisfy.

## Task and success-criteria completeness

- **130 numbered tasks, 130 checked, 0 unchecked.**
- **10 proposal success criteria, 10 checked.**

Every criterion names its evidence and each named artifact exists: `PetTouchControllerTest`,
`PetOverlayServiceStartupTest`, `PetStateResolverTest`, `NoGenericRejectionStringTest`,
`CharacterSwitchLiveRenderTest`, `OverlayOnboardingScreenTest`, `OverlayOnboardingViewModelTest`,
`OverlayOnboardingRendersTest`. Device-only criteria (edge snap, restart, rotation, end-to-end
import, TalkBack) rest on the maintainer's recorded device passes in tasks 27, 47, 66, 101 and
102, which state concrete measured values rather than a judgement.

**No box is ticked that should not be.** Tasks 83 and 84 were re-read against the code this pass
because the six new commits touch exactly what they claim: task 84's stated condition (the
identity affordance draws after `drawImage`, in the same `DrawScope`) is now true on the ready
path and the broken path, and task 83's (construction no longer performs a decode) holds -
`PetOverlayStateHolder.sheets` is a `mapLatest` plus `stateIn(WhileSubscribed)` projection seeded
`CharacterSheets.Loading`, with no decode in the constructor body.

## Load-bearing invariants re-confirmed

| Invariant | State | Evidence |
|---|---|---|
| One render path; `CharacterId` consulted exactly once | HOLDS | Only `CharacterSheetLoader.assetSourceFor(id)` branches on `CharacterId`; both arms return a `CharacterAssetSource` into one shared decode |
| No decode at construction | HOLDS | `PetOverlayStateHolder.sheets` is a lazy `mapLatest`/`stateIn`, initial value `Loading` |
| Absence never renders as zero | HOLDS | `ActiveCharacterRepositoryImpl.active` falls back to the injected built-in, never a fabricated empty state; position falls back to the computed resting corner |
| `[IMPORT-15]` ordering | HOLDS | Badge drawn last in the same `DrawScope` on both paths; its shape and colour are code constants, never decoded pixels |
| `[ONBOARD-2]` delegation | HOLDS | The primary action hands off to `OverlaySettingsLauncher`; the view model records the launch rather than performing the navigation |
| `[ONBOARD-4]` non-nagging | HOLDS | `shouldAutoShow` projects `OverlayOnboardingPolicy.shouldAutoShowOnboarding`; `onResume` records at most one refusal per settings round-trip |
| `SpriteSheetDecoder` unmodified | HOLDS, scoped | Untouched by all six commits since the previous pass. It is modified across the whole change (0efe072, 168d8e8, the declared-grid work), which is in scope and already verified; the invariant as stated means unchanged since the last verification baseline, and that holds |

## Findings

### CRITICAL / merge-blocking: none

### WARNING 1 - design.md still argues the reverted per-cycle pacing model

`openspec/changes/slice-2-movable-and-yours/design.md` lines 434-436:

> **What `default2` is now.** The 3 x 3, nine-frame sheet, declaring `durationMillis=900`. Paired
> with `default`'s six frames in the same 900 ms, the two are the in-app demonstration that pacing
> is declared per cycle rather than per frame: same tempo, different smoothness.

Every clause is false against shipped code. `durationMillis` no longer exists; the manifest
declares `frameDurationMillis=250`; `default2` now runs 2250 ms; and the paragraph's entire
point - that pacing is "declared per cycle rather than per frame" - is precisely the model
commit bb86067 reverted. This is the exact failure mode the closing brief named: a document
defending a reverted decision outliving the code. Commit 34decf7 edited the section immediately
above this paragraph and left it untouched.

Archive freezes design.md. Correcting it afterwards is materially harder.

### WARNING 2 - BuiltInCharacters.kt comment argues the same reverted model

In `feature/overlay/.../character/BuiltInCharacters.kt`:

> It is the only character that declares a `durationMillis`, which made the cycle-pacing
> behaviour unobservable from inside the app.

`durationMillis` is not a field any code reads, and "cycle-pacing behaviour" is the superseded
unit. Written in 8f7db23, one commit before the reversal, and not revisited by it.

### WARNING 3 - BundledCharacterSheetsTest kdoc contradicts its own assertions

The class kdoc still says:

> `default2` in particular is the two-row case that motivated the declared-grid work: 12 frames a
> single row could not hold.

`default2` is a 3 x 3 nine-frame sheet. The kdoc says two rows and twelve frames; the test bodies
directly below assert 3 rows and 9 frames. Commit 34decf7 updated the assertions and left the
kdoc describing the sheet it replaced. Not a defect - the assertions are what execute - but a
reader trusting the kdoc is misled about the fixture's purpose.

### WARNING 4 - the declared workflow command does not exercise :core:domain

`testDebugUnitTest` does not exist on a plain JVM module, so `:core:domain`'s 54 tests - which
include `AnimationPacingTest`, `PetStateResolverTest`, `SpriteGridTest` and every
`OverlayPositionFraction*` test, that is, most of the slice's pure logic - are never run by the
command the tasks artifact declares as authoritative. Stale XML from an earlier run was counted
as fresh evidence by the previous pass. The suite passes when actually run (this pass ran it),
so nothing is broken; the gap is in the evidence command, not in the code.

### Carried non-blocking findings - status

| # | Finding | Status |
|---|---|---|
| 1 | Vacuous test at `CharacterSheetLoaderTest.kt:103` | STILL OPEN. `built-in-shaped and imported-shaped sources run the identical decode path` calls `loader.load(fakeSource(files))` twice with the same `files` map and asserts the two results match. It compares a value to itself and cannot fail while the loader is deterministic |
| 2 | Loader's `BuiltIn` branch has no JVM unit test | STILL OPEN. `CharacterSheetLoaderTest`'s `load(CharacterId)` case builds a directory under `filesDir/characters`, exercising only the `Imported` arm. `BundledCharacterSheetsTest` decodes real bundled assets but drives `BuiltInCharacterManifestReader` and `SpriteSheetDecoder` directly, bypassing `CharacterSheetLoader.assetSourceFor` entirely |
| 3 | `ActiveCharacterRepositoryImpl` kdoc claims a stale-pointer fallback | STILL OPEN. The kdoc says a "stale (deleted-target) stored pointer" resolves to the built-in fallback. `decode` performs no existence check: an `imported:<uuid>` pointer to a deleted character returns a non-null `CharacterId.Imported`, so the `?: builtInFallback` never fires. Recovery happens elsewhere (the delete path calls `setActive`), so behaviour is correct; the kdoc credits the wrong component |
| 4 | `builtin:` with an empty name yields a permanent Broken | STILL OPEN. `stored.removePrefix(BUILT_IN_PREFIX)` on the literal `builtin:` yields `CharacterId.BuiltIn("")`, which is non-null and so bypasses the fallback, then fails to resolve any asset folder forever. Only reachable through a corrupted DataStore value |

### SUGGESTION

- WARNING 4 argues for adding `:core:domain:test` (or a `check`-rooted command) to the tasks
  artifact's declared verification command, so a future pass cannot mistake stale XML for evidence.
- Findings 3 and 4 are one edit apart: validating the decoded name/uuid as non-blank would make
  the kdoc's claim true and close the empty-name trap in the same line.

## Spec compliance

All 7 capabilities, 48 requirements and 62 scenarios complete.

| Capability | Requirements | Scenarios | State |
|---|---|---|---|
| `character-import` | 15/15 | 17/17 | COMPLETE - `[IMPORT-15]` closed this pass |
| `overlay-drag` | 9/9 | 11/11 | COMPLETE |
| `overlay-onboarding-ui` | 7/7 | 9/9 | COMPLETE |
| `overlay-position-persistence` | 7/7 | 11/11 | COMPLETE |
| `pet-overlay-rendering` | 1/1 | 3/3 | COMPLETE |
| `pet-sprite-sheet` | 2/2 | 2/2 | COMPLETE |
| `pet-state-resolution` | 7/7 | 9/9 | COMPLETE |

## Archive readiness

Ready to archive on defect grounds: no CRITICAL finding, no failing command, no unearned
checkbox, and every total reached. The only reason to pause is WARNING 1, and to a lesser degree
WARNINGs 2 and 3, which are false statements that archiving would make permanent.
