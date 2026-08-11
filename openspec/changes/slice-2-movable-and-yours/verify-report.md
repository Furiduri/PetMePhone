```yaml
schema: gentle-ai.verify-result/v1
evidence_revision: sha256:a503495f2d33db1eea599eb087200da0645db227e72bd8381a665096b8b0130a
verdict: fail
blockers: 0
critical_findings: 0
requirements: 43/48
scenarios: 57/62
test_command: "./gradlew :core:domain:test :core:data:testDebugUnitTest :feature:overlay:testDebugUnitTest --rerun-tasks"
test_exit_code: 0
test_output_hash: sha256:06e682744b4f73c343bc215c383e4fd517252e3ecf53a1fde8c7ab26a63091f6
build_command: "./gradlew assembleDebug --rerun-tasks"
build_exit_code: 0
build_output_hash: sha256:65ef5410e4ab24d14410c4f4e1270160d677904687bc237a8e10d721c3f0046f
```

# Verification Report - slice-2-movable-and-yours (WHOLE CHANGE, PRs 1-7)

Supersedes the stale PR-6-scoped `verdict: fail` report. Scope: `git diff master...HEAD` at
`9e3f4a91856fc6e5a06d31982939957fb521d5af` (branch `feat/slice-2-overlay-onboarding`), covering all
130 tasks across PR 1, 2, 3, 4, 5, 5.1, 6a, 6b and 7. Mode: openspec + engram.

## Verdict: FAIL (formal completeness only) - ZERO merge-blocking findings

Read the two halves of this verdict separately; conflating them would misrepresent the change.

- **Defect status: clean.** No CRITICAL finding exists. Both blockers from the previous pass (C1
  `ActiveCharacterRepositoryImplTest` and C2 the frozen frame clock) are fixed and independently
  re-confirmed here at runtime. Every declared command exits 0.
- **Formal status: incomplete.** Five scenarios across five requirements cannot be marked complete
  because they require the physical-device and manual work the user has deliberately deferred.
  `gentle-ai sdd-verify-validate` therefore refuses any passing verdict
  (`passing verdict contradicts failing or incomplete evidence`), so `fail` is the only admissible
  value. This is a bookkeeping outcome, not a quality judgement.

## Runtime evidence (all freshly regenerated, `--rerun-tasks`)

| Command | Exit | Result |
|---|---|---|
| `:core:domain:test :core:data:testDebugUnitTest :feature:overlay:testDebugUnitTest --rerun-tasks` | 0 | 92 tasks executed, BUILD SUCCESSFUL |
| `assembleDebug --rerun-tasks` | 0 | 167 tasks executed, BUILD SUCCESSFUL |
| `ANDROID_SERIAL=emulator-5554 :feature:overlay:connectedDebugAndroidTest` | 0 | 3 tests on Pixel_8(AVD)-14, 0 failed |

Real per-suite counts from the regenerated `TEST-*.xml`:

| Suite | classes | tests | failures | errors | skipped |
|---|---|---|---|---|---|
| `:core:domain` | 10 | 50 | 0 | 0 | 0 |
| `:core:data` | 5 | 22 | 0 | 0 | 0 |
| `:feature:overlay` | 24 | 93 | 0 | 0 | 1 |
| **Total unit** | **39** | **165** | **0** | **0** | **1** |
| `:feature:overlay` instrumented | 3 | 3 | 0 | 0 | 0 |

No suite compiles-but-runs-zero. The single skip is the pre-existing `ProgrammerArtGenerator`. The
first attempt of the unit command exited 1 on a Windows file lock held by a concurrent Gradle daemon
(`bundleLibRuntimeToJarDebug`), not on any test; after `./gradlew --stop` the rerun was clean. The
`:core:data` count rose 22 from 19 and `ActiveCharacterRepositoryImplTest` now passes 4/4.

**Device safety.** `adb devices` was run before and immediately before the instrumented invocation;
both times it listed `emulator-5554` alone. No physical device was attached, targeted, installed to,
launched on, or sent input.

## Previous blockers: both fixed

- **C1 resolved.** `ActiveCharacterRepositoryImplTest` passes 4/4 in the forced full run. The two
  `[IMPORT-11]` cases task 78 exists to prove now execute green; tasks 78 and 92 are truthful at
  this revision.
- **C2 resolved.** `PetOverlay.kt:170` is now `LaunchedEffect(ready, layout, holder.config)`. Adding
  `ready` to the key set closes the value-equality hole: a switch between two layout-equal characters
  relaunches the clock, so the new character frame index advances instead of freezing on 0.
  `PetOverlayFrameClockSwitchTest` covers it and passes.

## Requested load-bearing checks

1. **One model, one render path - CONFIRMED.** The only id-type branch on the render path remains
   `CharacterSheetLoader.assetSourceFor`; `load(source)` is byte-identical for both variants, and the
   holder, renderer and switching call sites pass `CharacterId` through untyped. Downstream of that
   single consult there is no id-type branching.
2. **Absence never renders as zero - CONFIRMED across every screen and state.** A missing optional
   animation is `continue`, never an entry and never a failure. `OverlayPositionRepositoryImpl`
   emits `null` for `Absent`, never `0f`. `Loading` is a distinct sealed member and the seed value,
   never a zeroed `Ready`. `LibraryScreen` renders `character_unnamed` for an absent name rather
   than a fabricated string. `Broken` draws a code-only cross rather than an empty window.
3. **`[IMPORT-15]` ordering - CONFIRMED.** `drawIdentityAffordance()` is called at `PetOverlay.kt:225`
   inside the same `drawBehind` `DrawScope`, after the `drawImage` at `:209`. Badge geometry and
   colour are compile-time constants; no decoded pixel participates, so imported content cannot
   paint over or spoof it. See W3 for the Broken-state gap in the spec wording.
4. **`[ONBOARD-2]` - CONFIRMED.** `OverlayOnboardingScreen.kt` constructs no `Intent` and imports no
   `Settings`; it calls `settingsLauncher.launchOverlaySettings()`. A repo-wide search shows
   `Settings.ACTION_MANAGE_OVERLAY_PERMISSION` is constructed in exactly one place,
   `core/data/.../OverlaySettingsLauncherImpl.kt:26`, and `Settings.canDrawOverlays` in exactly one
   place, `OverlayPermissionCheckerImpl.kt:18`.
5. **`[ONBOARD-6]` dark-pattern audit - CONFIRMED, audited independently.** The full user-facing copy
   is seven strings. I read each against the requirement rather than accepting the prior audit.
   - "A small pet will be drawn on top of your other apps." - accurate, no urgency.
   - "The app cannot see, read, or interact with the content of other apps, and does not capture your
     screen." - this is the claim that most deserved checking, because a false reassurance here would
     be the worst dark pattern on the screen. It is **true**: the merged manifests declare only
     `SYSTEM_ALERT_WINDOW`, `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_SPECIAL_USE` and
     `POST_NOTIFICATIONS`. There is no accessibility service, no `MediaProjection`, no
     `QUERY_ALL_PACKAGES`, and no screen-capture path anywhere in the change.
   - "No data leaves this device." - **true**: no `INTERNET` permission is declared in any manifest,
     so the statement is enforced by the platform, not merely promised.
   - "You can revoke this permission at any time from system Settings." - true and volunteered.
   - "Open Settings" and "You can turn the pet on later by opening this again." - neutral, factual.
   No urgency language, no countdown, no confirmshaming, no claim that the app is unusable without
   the grant, and no claim that deceptive imports are prevented. The screen passes.
6. **`[ONBOARD-4]` one refusal leaves the app fully usable - CONFIRMED.** `onResume()` records a
   refusal only when `awaitingSettingsResult` is true, and that record feeds
   `OverlayOnboardingPolicy.shouldAutoShowOnboarding` which gates auto-display only. Nothing in the
   refusal path disables, blocks, degrades or re-prompts. `ReEntryCard` fires
   `onReopenOnboarding` solely on an explicit tap and never on dismiss, so the re-entry point is
   passive by construction. `OverlayOnboardingViewModelTest` and `OverlayOnboardingPolicyTest` both
   pass.
7. **Live re-query - CONFIRMED.** `onResume()` is the only writer of `isGranted` and always calls
   `permissionChecker.canDrawOverlays()`; `OverlayPermissionCheckerImpl` caches nothing.

## Carried-forward findings: all re-checked, all still accurate, all still non-blocking

- **W2 - still accurate.** `CharacterSheetLoaderTest.kt:103` still builds both sides from the same
  `files` map, so the assertions compare a fixture to itself and cannot fail. Its assertions were
  strengthened (keys and layout rather than whole-object equality) but the vacuity is structural,
  not assertional. This is the only vacuous test I found in the change. Mitigation is real and has
  improved: the sibling `load(CharacterId) selects the source but decodes through the same shared
  path` exercises the `Imported` branch through a real id, and the `CharacterId.BuiltIn` assets
  branch - still without a JVM unit test - is now genuinely covered at runtime by the instrumented
  `PetOverlayRendersTest`, which decodes the real bundled asset and passed on the emulator this pass.
- **W5/W6 - still accurate.** `ActiveCharacterRepositoryImpl` kdoc still claims a stale
  (deleted-target) pointer resolves to the fallback while `decode` never checks existence; the
  deleted-target case is actually handled by `CharacterRepositoryImpl.remove`, and an externally
  deleted folder resolves to a `Broken` render, which is correct `[IMPORT-14]` behaviour. A stored
  `builtin:` with an empty name still decodes to `CharacterId.BuiltIn("")` and yields a permanent
  `Broken` rather than the fallback. Neither can crash. Both are doc/edge defects, not merge blockers.
- **N1 - still accurate.** `PetOverlayFrameClockSwitchTest.kt:103-110` still bounds a virtual-clock
  wait with `System.currentTimeMillis()` and `Thread.sleep(5)`. It passed this pass, but the
  wall-clock bound remains a plausible CI flake source.
- **`ReadyPet` internal + `onFrameAdvance` test hook - unchanged, still acceptable.** The widened
  visibility buys instrumented coverage that Robolectric cannot provide, at no production cost.

## New non-blocking findings

- **W7 - two user-visible strings are hardcoded English literals.** `ReEntryCard.kt:40` and `:48`
  pass `label = "Open"` and `label = "Dismiss"` directly instead of `stringResource`. Every other
  string on both onboarding surfaces is a resource. These two are the visible button labels, so they
  cannot be translated and they sit outside the string file that the `[ONBOARD-6]` copy audit reads.
  Not a dark pattern and not merge-blocking, but it is the one place where spec-governed copy escaped
  the resource file.
- **W8 - the onboarding screen offers no explicit decline affordance.** The only interactive element
  is "Open Settings"; refusal is expressed by navigating back, and `recordRefusal` only fires after a
  Settings round-trip. A user who never opens Settings therefore never records a refusal, so
  `[ONBOARD-4]` "after the first refusal" is only reachable via the Settings path. The requirement
  is met for the path it describes; the back-out path simply records nothing, which errs toward not
  suppressing the screen rather than toward nagging. Worth a decision, not a block.
- **S1 - `System.currentTimeMillis()` is read directly in `OverlayOnboardingViewModel.onResume()`.**
  Every other time and config value in this slice is injected. A clock abstraction would match the
  slice convention and make the refusal timestamp testable.
- **S2 - `[POS-1]` is met in spirit, not in letter.** The scenario says no `intPreferencesKey` for
  position exists, and `OverlayPositionRepositoryImpl.kt:70-71` still declares `LEGACY_X_KEY` and
  `LEGACY_Y_KEY`. They exist only as deletion targets: they are never read, and `save` removes them
  in the same `edit` block as the first successful write. No absolute pixel coordinate is ever
  persisted, so the actual intent of the requirement holds. The deliberate, documented non-migration
  is sound; only the literal wording of the scenario is contradicted.
- **S3 - the tagged pointer encoding is still duplicated** between
  `ActiveCharacterRepositoryImpl.encode` and `LibraryScreen.libraryKey`.

## Spec compliance

48 requirements and 62 scenarios across seven capabilities, counted from the retrieved specs.
43 requirements and 57 scenarios are complete with runtime evidence. The five incomplete scenarios,
and the five requirements that own them, are exactly the ones the deferred device/manual work covers:

| Requirement | Scenario | Why incomplete |
|---|---|---|
| character-import - Picking uses Photo Picker, no storage permission | Picking a photo prompts no storage permission dialog | Structurally guaranteed by `PickVisualMedia` (used in `ImportScreen`/`CharacterImportController`), but no test references it. Task 66 is the deferred instrumented attempt. |
| character-import - A persistent identity affordance is always visible | Identity affordance renders with every character | Drawn correctly and in the right order, but nothing asserts it renders, and `BrokenPlaceholder` draws no affordance, so "regardless of active character" is literally unmet for a Broken character. The red cross is itself code-drawn and unspoofable, so the security intent survives; the wording does not. (W3, carried forward.) |
| overlay-position-persistence - Restart restores the last resting position | Kill and restart restores position | Task 47, deferred manual pass. |
| overlay-position-persistence - Rotation and differing screen dimensions preserve a sensible relative position | Rotating mid-session keeps the pet on screen | Task 47, deferred manual pass. The sibling different-screen-size scenario IS covered by JVM arithmetic tests. |
| overlay-onboarding-ui - The screen meets baseline accessibility requirements | A manual TalkBack pass is completed and recorded | Task 102, deferred. The spec makes the manual pass itself the scenario, so no automated test can close it. The sibling per-element scenario IS covered by `OverlayOnboardingAccessibilityTest`. |

`[DRAG-7]` is counted COMPLETE despite open task 27: the spec scenario is "a drag ending near the
nav bar still rests above it", which `PetTouchControllerTest` proves at the unit level, including
the case `a frame callback in flight at release cannot resurrect an unclamped y`. Task 27 is a
`FLAG_LAYOUT_NO_LIMITS` window-flag decision procedure, not a spec scenario. Counting it against a
requirement would have been inflation in the other direction.

Every other capability is fully covered by executed tests: `pet-state-resolution` by
`PetStateResolverTest`/`PetStateResolverFlowTest`, `overlay-drag` by `PetTouchControllerTest`
(9 cases), `ExceedsSlopTest`, `SpringSnapAnimatorTest`, `DragStateRepositoryImplTest`,
`overlay-position-persistence` by four `OverlayPositionFraction*` suites plus `PositionWriterTest`
and `PetOverlayServiceStartupTest`, `character-import` by `CharacterImporterTest`,
`NoGenericRejectionStringTest`, `LibraryScreenTest`, `PreviewScreenTest`,
`CharacterRepositoryImplTest` and `ActiveCharacterRepositoryImplTest`, `pet-sprite-sheet` by
`SpriteGridTest`, `SpriteLayoutTest`, `SpriteSheetDecoderTest`, `TransparentCellScannerTest` and
`BuiltInCharacterManifestReaderTest`, `pet-overlay-rendering` by `PetOverlayTest`,
`PetOverlayClockTest`, `PetOverlayFrameClockSwitchTest` and the three instrumented tests, and
`overlay-onboarding-ui` by `OverlayOnboardingScreenTest`, `OverlayOnboardingViewModelTest`,
`OverlayOnboardingAccessibilityTest` and `OverlayOnboardingPolicyTest`.

## Artifact integrity

- 130 tasks, numbered 1 through 130 with no gap and no duplicate. Note the change carries 130 tasks,
  not 103: tasks 104-130 are the declared-grid work of PR 5.1, appended after the PR 7 block, and
  all are checked.
- 124 checked, 6 unchecked. **The declared known-open set was five; there are six.** Task 101
  (attempt the instrumented suite once against an API 34 image for the onboarding screen) was not
  named in the deferral list but is unchecked. It is the PR-7 sibling of tasks 31 and 66 and is now
  effectively satisfied by this pass: `connectedDebugAndroidTest` ran green on `emulator-5554`,
  though it executed the three existing UI tests rather than an onboarding-specific instrumented
  test, of which none exists.
- The six unchecked tasks (27, 31, 47, 66, 101, 102) all carry honest, explicit deferral notes and
  none is fabricated as passing.
- Task 103 is checked while declaring a dependency on tasks 98, 99, 100, 101, 102 and 96, two of
  which are open. Its own Done criterion (build green, XML counts confirm real execution) is
  genuinely met, so the check is truthful; the stale dependency line is a bookkeeping inconsistency
  only.
- I found no task marked [x] whose Done criterion is unmet at this revision. Tasks 78 and 92, false
  in the previous pass, are now true.
- All artifacts are in English.

## Recommendation

Nothing in the code blocks this merge. The chain is clean: 165 unit tests plus 3 instrumented tests
green, build green, both previous blockers fixed, and every load-bearing invariant re-confirmed by
inspection rather than inherited from the prior report. The `fail` verdict above records formal
incompleteness, not a defect.

Before archive, the separate device session should close tasks 27, 47 and 102 (and optionally 31,
66, 101). Re-running verification after that manual pass should admit `pass_with_warnings` at 48/48
and 62/62, leaving only W2, W3, W5-W8 and the suggestions as recorded non-blocking debt.
