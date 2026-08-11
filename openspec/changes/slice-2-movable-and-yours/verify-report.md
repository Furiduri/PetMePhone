```yaml
schema: gentle-ai.verify-result/v1
evidence_revision: sha256:83cdb0a7224af1becd96e60f416db1f655b8ad9dcfac9435638a5d29003c90ba
verdict: fail
blockers: 0
critical_findings: 0
requirements: 47/48
scenarios: 61/62
test_command: "./gradlew assembleDebug testDebugUnitTest assembleDebugAndroidTest lintDebug --rerun-tasks"
test_exit_code: 0
test_output_hash: sha256:550cbb69abd48bfc9c2015f0bd0745fbb3765181c731982a7f8d7c69b72afad1
build_command: "./gradlew assembleDebug testDebugUnitTest assembleDebugAndroidTest lintDebug --rerun-tasks"
build_exit_code: 0
build_output_hash: sha256:550cbb69abd48bfc9c2015f0bd0745fbb3765181c731982a7f8d7c69b72afad1
```

# Verification Report - slice-2-movable-and-yours (WHOLE CHANGE, PRs 1-7 + 5.1 + 6a/6b)

Supersedes the previous whole-change report. Scope: git diff master...HEAD at
731bd39e9b186e2a9d951f186c771b934fb34c79, branch feat/slice-2-movable-and-yours, PR #72,
135 files, +11820/-205. All 130 tasks are now checked. Mode: openspec + engram.

## Verdict: FAIL (formal completeness only) - ZERO merge-blocking findings

Read the two halves separately.

- **Defect status: clean.** No CRITICAL finding. Every declared command exits 0. 184 unit tests and
  5 instrumented tests pass on freshly regenerated output with zero UP-TO-DATE actionable tasks.
- **Formal status: one scenario short.** 47/48 requirements and 61/62 scenarios are complete. The
  five device/manual tasks the last pass waited on (31, 47, 66, 101, 102) are genuinely closed and
  between them closed four of the five previously-incomplete scenarios. **One scenario did not close
  and was never going to close through a device pass**: `character-import` -> "Identity affordance
  renders with every character". `gentle-ai sdd-verify-validate` therefore still refuses a passing
  verdict, and `fail` remains the only admissible value. This is bookkeeping plus one real gap, not
  a defect verdict on the implementation.

## Runtime evidence (freshly regenerated)

| Command | Exit | Result |
|---|---|---|
| `./gradlew assembleDebug testDebugUnitTest assembleDebugAndroidTest lintDebug --rerun-tasks` | 0 | BUILD SUCCESSFUL in 3m43s, **468 actionable tasks: 468 executed** - zero UP-TO-DATE actionable tasks, so the false-green trap from earlier in this change cannot apply |
| `ANDROID_SERIAL=emulator-5554 ./gradlew :feature:overlay:connectedDebugAndroidTest` | 0 | **5 tests on Pixel_8(AVD) - 14, 0 skipped, 0 failed** |

Real per-suite counts read from the regenerated `TEST-*.xml`:

| Module | classes | tests | failures | errors | skipped |
|---|---|---|---|---|---|
| `:app` | 2 | 8 | 0 | 0 | 0 |
| `:core:data` | 5 | 22 | 0 | 0 | 0 |
| `:core:domain` | 11 | 55 | 0 | 0 | 0 |
| `:feature:overlay` | 25 | 99 | 0 | 0 | 1 |
| **Total unit** | **43** | **184** | **0** | **0** | **1** |
| `:feature:overlay` instrumented | - | 5 | 0 | 0 | 0 |

The single skip is the pre-existing, deliberately disabled `ProgrammerArtGenerator`. No suite
compiles-but-runs-zero. `lintDebug` produced a SARIF report with no issues at any level; all 17
`:feature:overlay` string resources carry the `feature_overlay_` prefix (17/17), so the `ResourceName`
fix is complete and orphaned no reference - `NoGenericRejectionStringTest` still passes 2/2 against
the renamed file.

**Device safety.** `adb devices` was run first and listed the maintainer's physical phone
(`adb-O7SSINS4LFK7OJW4-T7xiQB`, twice over TLS) alongside `emulator-5554`. The instrumented
invocation was scoped with `ANDROID_SERIAL=emulator-5554` and Gradle reported a single target,
`Pixel_8(AVD) - 14`. No physical device was targeted, installed to, launched on, or sent input.

## 1. Is every checked box earned?

I walked all 130 tasks and all ten success criteria and checked each named artefact. **No task is
ticked that should not be.** The five newly closed tasks specifically:

| Task | Claim | Evidence checked | Verdict |
|---|---|---|---|
| 31 | instrumented suite on API 34, 3 tests 0 failed, `InputManager` gap clears | re-executed here: the suite now holds 5 tests including the original three (`CharacterSwitchLiveRenderTest`, `PetOverlayFailurePlaceholderTest`, `PetOverlayRendersTest`), 0 failed on Pixel_8 AVD API 34 | EARNED |
| 101 | `OverlayOnboardingRendersTest` was written, suite went 3 -> 5 tests, 0 failed | file exists at `feature/overlay/src/androidTest/.../onboarding/OverlayOnboardingRendersTest.kt` with exactly two `@Test` methods (four claims from real resources; primary action clickable and delegating to `OverlaySettingsLauncher` exactly once). 3 + 2 = 5, matching the run I executed | EARNED |
| 47 | kill/restart identical frames; rotation failed first, produced #71, then `frame=[2492,344][2712,564]` | the arithmetic side is real and green (`PetOverlayRotationPositionTest`, the four `OverlayPositionFraction*` suites, `PetOverlayServiceStartupTest`); the device measurements are maintainer observation and the record honestly names its own first failure | EARNED |
| 66 | full import flow end to end on a Redmi Note 14 Pro 5G, character "Emocion" reads back active | maintainer observation; the task explicitly records that **no instrumented test exists for `ImportScreen`/`PreviewScreen`** and calls itself a manual pass, not an automated one | EARNED |
| 102 | TalkBack pass, "works fine for the early phases" | maintainer observation, explicitly scoped below a full audit - see section 2 | EARNED |

The ten success criteria each name a real, executed artefact: `PetTouchControllerTest` (9 tests),
`PetOverlayServiceStartupTest` (2), `NoGenericRejectionStringTest` (2), `PetStateResolverTest` (5),
`OverlayOnboardingScreenTest` (3), `OverlayOnboardingViewModelTest` (6), plus
`CharacterSwitchLiveRenderTest` and `OverlayOnboardingRendersTest` in the green instrumented run.
Every named file exists and every named command was run by me at this revision.

One bookkeeping defect, non-blocking:

- **D1 - task 102 is tagged `[ONBOARD-8]`, which does not exist.** `specs/overlay-onboarding-ui/spec.md`
  has exactly 7 requirements, and the TalkBack scenario belongs to `[ONBOARD-7]` ("The screen meets
  baseline accessibility requirements"). The tag is off by one; the traceability itself is sound.

## 2. Does the device-pass wording overstate what was done?

No. All three manual records are, if anything, understated:

- **Task 102** states the verdict was that it "works fine for the early phases", says every element
  was reachable and announced - which is exactly what `[ONBOARD-7]`'s scenario asks for - and then
  explicitly disclaims announcement ordering, verbosity and content-description wording, and says it
  should be revisited before anyone relies on a screen reader daily. That claims strictly less than a
  full accessibility audit.
- **Task 66** volunteers that no instrumented test for those two screens exists and calls itself a
  recorded manual pass rather than an automated one.
- **Task 47** records that the rotation leg *failed first* and names the issue it produced (#71).
  A record that names its own failure is not an overstatement.
- **Task 27** (closed in an earlier pass) still honestly states that leg (a), the 3-button-navigation
  emulator, was never executed.

## 3. Requirement and scenario counts, recounted honestly

| Capability | Requirements | Scenarios |
|---|---|---|
| `character-import` | 15 | 17 |
| `overlay-drag` | 9 | 11 |
| `overlay-onboarding-ui` | 7 | 9 |
| `overlay-position-persistence` | 7 | 11 |
| `pet-overlay-rendering` | 1 | 3 |
| `pet-sprite-sheet` | 2 | 2 |
| `pet-state-resolution` | 7 | 9 |
| **Total** | **48** | **62** |

Four of the previous pass's five incomplete scenarios now close:

| Scenario | Now closed by |
|---|---|
| `character-import` - Picking a photo prompts no storage permission dialog | Task 66's end-to-end device import through `PickVisualMedia`; no storage permission is declared or requested anywhere in the flow |
| `overlay-position-persistence` - Kill and restart restores position | Task 47 leg 1, four force-stop cycles, identical frames |
| `overlay-position-persistence` - Rotating mid-session keeps the pet on screen | Task 47 leg 2 after the #71 fix, plus `PetOverlayRotationPositionTest` |
| `overlay-onboarding-ui` - A manual TalkBack pass is completed and recorded | Task 102 |

The fifth does **not** close, and I will not inflate it:

| Requirement | Scenario | Why still incomplete |
|---|---|---|
| `character-import` - A persistent identity affordance is always visible | Identity affordance renders with every character | Two independent gaps, both re-verified at this revision. (a) **Nothing asserts it renders.** No unit or instrumented test in the repo references the badge; a search of every `src/test` and `src/androidTest` source for identity/affordance/badge returns only unrelated hits. (b) **It is literally not always visible.** `PetOverlay.kt:66` routes `CharacterSheets.Broken` to `BrokenPlaceholder()`, whose `drawBehind` calls only `drawBrokenShape()`; `drawIdentityAffordance()` is reachable solely from the `ReadyPet` draw path at `:233`. A character whose `idle.png` is missing or corrupt therefore renders with no identity affordance, contradicting "regardless of active character". No device pass could have closed this one. |

Reported as **47/48 requirements, 61/62 scenarios**. This is why the validator still refuses a
passing verdict. I made no adjustment to force admission.

Note on `[DRAG-7]`: still counted COMPLETE. Its scenario is "a drag ending near the nav bar still
rests above it", proven by `PetTouchControllerTest`; task 27's `FLAG_LAYOUT_NO_LIMITS` procedure is a
window-flag decision, not a spec scenario. Counting it against a requirement would be inflation in
the other direction.

## 4. Load-bearing invariants, re-confirmed at this revision

1. **One render path, `CharacterId` consulted exactly once - CONFIRMED.** The only id-type branch is
   `CharacterSheetLoader.assetSourceFor` (`:76` BuiltIn, `:83` Imported). Downstream, `load(source)`
   is identical for both, and the holder, renderer and switching call sites pass `CharacterId`
   through untyped.
2. **No decode at construction - CONFIRMED.** `PetOverlayStateHolder` performs no decode in `init`;
   sheets are `activeCharacterRepository.active.mapLatest { withContext(Dispatchers.IO) { sheetLoader.load(id) } }`
   collected via `stateIn(..., initialValue = CharacterSheets.Loading)`.
3. **Absence never renders as zero - CONFIRMED.** `Loading` is a distinct seed member, never a zeroed
   `Ready`; a missing optional animation is simply absent from `byState`; `OverlayPositionRepositoryImpl`
   emits `null`, never `0f`; `CharacterName.validOrNull` never returns an empty string; `LibraryScreen`
   renders `character_unnamed` rather than a fabricated name; `Broken` draws a code-only cross, never
   an empty window.
4. **`[IMPORT-15]` ordering - CONFIRMED for the Ready path.** `drawIdentityAffordance()` is called at
   `PetOverlay.kt:233`, inside the same `DrawScope`, after the `drawImage` at `:217`. Badge geometry
   and colour are compile-time constants (`IDENTITY_BADGE_RADIUS_PX`, `IDENTITY_BADGE_MARGIN_PX`), so
   no decoded pixel participates and imported content cannot paint over or spoof it. The Broken-state
   gap is section 3's finding; note the security intent survives there, because a Broken character
   draws no imported pixels at all.
5. **`[ONBOARD-2]` delegation - CONFIRMED.** `OverlayOnboardingScreen.kt` constructs no `Intent` and
   imports no `Settings`; it calls `settingsLauncher.launchOverlaySettings()` at `:52`.
6. **`[ONBOARD-4]` non-nagging - CONFIRMED.** `recordRefusal` fires only when `awaitingSettingsResult`
   is true, and only feeds `OverlayOnboardingPolicy.shouldAutoShowOnboarding`, which gates
   auto-display alone. Nothing on the refusal path disables, degrades or re-prompts.
7. **`SpriteSheetDecoder` decode contract - CONFIRMED.** `CharacterSheetLoader` decodes through the
   injected `SpriteSheetDecoder` (`:50`, `:61`) and adds no decoding of its own. The decoder file was
   touched exactly twice in the whole change, both by explicitly authorised tasks: `0efe072`
   (task 53, `validateBounds` extraction) and `168d8e8` (task 115, declared grid). Neither is a
   PR-6-era modification.

## 5. Carried non-blocking findings, all re-checked at this revision

- **W2 - still accurate.** `CharacterSheetLoaderTest.kt:103-118` still builds `builtInShaped` and
  `importedShaped` from the same `files` map, so the assertions compare a fixture to itself and
  cannot fail. Still the only vacuous test I found in the change. Mitigated by the sibling
  `load(CharacterId)` case at `:121` and by the instrumented `PetOverlayRendersTest`, which decodes
  the real bundled asset and passed here.
- **BuiltIn loader-branch unit coverage - still accurate.** The `CharacterId.BuiltIn` assets branch of
  `assetSourceFor` has no JVM unit test; it is covered at runtime only by the instrumented suite,
  which passed.
- **`ActiveCharacterRepositoryImpl` kdoc claim - still accurate.** The kdoc (`:16-17`) still claims a
  "stale (deleted-target)" pointer resolves to the fallback, while `decode` (`:47-49`) never checks
  existence. The deleted-target case is actually handled by `CharacterRepositoryImpl.remove`; an
  externally deleted folder resolves to a `Broken` render, which is correct `[IMPORT-14]` behaviour.
  Documentation defect only.
- **Empty-name `builtin:` pointer - still accurate.** A stored `builtin:` with an empty name still
  decodes to `CharacterId.BuiltIn("")` (`:49`, `removePrefix`) and yields a permanent `Broken` rather
  than the injected fallback. Cannot crash; an edge defect, not a blocker.
- **N1 - still accurate.** `PetOverlayFrameClockSwitchTest` still bounds a virtual-clock wait with
  wall-clock time; it passed here but remains a plausible CI flake source.
- **W7 - still accurate.** `ReEntryCard.kt:40` and `:48` still pass `label = "Open"` and
  `label = "Dismiss"` as hardcoded English literals rather than `stringResource`, so they are
  untranslatable and sit outside the file the `[ONBOARD-6]` copy audit reads.
- **W8 - still accurate.** The onboarding screen still offers no explicit decline affordance; refusal
  is recorded only after a Settings round-trip. Errs toward not suppressing the screen, never toward
  nagging.
- **S1 - still accurate.** `OverlayOnboardingViewModel.kt:59` reads `System.currentTimeMillis()`
  directly, against the slice's injected-config convention.
- **S2 - still accurate.** `LEGACY_X_KEY`/`LEGACY_Y_KEY` still exist as deletion targets only: never
  read, removed in the same `edit` block as the first successful save. `[POS-1]` is met in spirit,
  not in letter.
- **S3 - still accurate.** The tagged-pointer encoding is still duplicated between
  `ActiveCharacterRepositoryImpl.encode` and `LibraryScreen.libraryKey`.
- **`ReadyPet` internal plus `onFrameAdvance` test hook - unchanged, still acceptable.** It buys
  coverage Robolectric cannot provide, at no production cost.

## 6. Artifact integrity

- 130 tasks, numbered 1-130 with no gap and no duplicate, plus 92a. **130 checked, 0 unchecked.**
- No task is marked done whose Done condition is unmet at this revision. The two historical false
  cases (tasks 78 and 92) remain true here: `ActiveCharacterRepositoryImplTest` passes 4/4 under the
  forced full run.
- Task 103's dependency line listed tasks that were open when it was first checked; all are now
  closed, so the inconsistency the previous pass noted has resolved itself.
- D1 (task 102's `[ONBOARD-8]` tag) is the one remaining artefact defect.
- All artifacts are in English.
- Working tree clean apart from untracked `.kotlin/`.

## Recommendation

Nothing in the code blocks this merge. 184 unit tests and 5 instrumented tests green on freshly
regenerated output with 468/468 actionable tasks executed, lint clean, both historical blockers still
fixed, and every load-bearing invariant re-confirmed by inspection rather than inherited from the
prior report.

The `fail` verdict is formal. To reach an admissible `pass_with_warnings` at 48/48 and 62/62 the
change needs one thing, and it is code rather than another device session: either draw the identity
affordance in `BrokenPlaceholder` as well as `ReadyPet` and add a test asserting it renders, or amend
the `character-import` requirement so it scopes itself to a successfully rendered character. That is
small and bounded. Archiving as-is would archive a change whose `[IMPORT-15]` "always visible" claim
is untrue for a broken character and is asserted by no test at all.
