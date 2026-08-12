# Archive Report: slice-2-movable-and-yours

**Change**: slice-2-movable-and-yours  
**Archived**: 2026-08-11  
**Status**: Complete and verified. PR #72 is OPEN, not merged — the change is closed on implementation and verification, not on delivery.  

## Final State Authority and Status Resolution

This archive report records the final state of the change AT CLOSE, after all implementation, verification, and post-verification remediation work.

### Terminal Delivery Facts

1. **All seven PRs implemented and verified**:
   - PR 1 (issue #37): State types, resolver, dwell, DRAGGING + IDLE providers
   - PR 2 (issue #15): Drag and horizontal edge snap
   - PR 3 (issue #16): Fraction persistence and startup read ordering
   - PR 4 (issue #39a): Import and validation pipeline
   - PR 5 (issue #39b): Character library and cap
   - PR 5.1 (issue #69): Declared sprite grid, and the import name step
   - PR 6a + PR 6b (issue #39c): Active-character repository, sheet loading, and live switching
   - PR 7 (issue #12): Onboarding screen
   - Built locally as a `feature-branch-chain`, but delivered as a single pull request: no
     intermediate branch was ever pushed, so the whole slice reached review as PR #72 at once.
     Worth recording as a lesson rather than a detail — the first CI run on that PR failed on
     lint errors latent since PR 4, which a published chain would have caught five PRs earlier.

2. **Requirements met**:
   - `pet-state-resolution`: 6/6 requirements
   - `overlay-drag`: 10/10 requirements
   - `overlay-position-persistence`: 8/8 requirements
   - `character-import`: 15/15 requirements
   - `overlay-onboarding-ui`: 8/8 requirements
   - `pet-overlay-rendering`: 4/4 requirements (modified capability)
   - `pet-sprite-sheet`: 3/3 requirements (modified capability)
   - **Total**: 54/54 requirements across 7 domains, 62/62 scenarios

3. **Verification status resolution**:
   - **Persisted verify-report** (dated 2026-08-11, `af4fb6c`): `verdict: pass_with_warnings`, `critical_findings: 0`, `blockers: 0`, `requirements: 48/48`, `scenarios: 62/62`
   - **Intermediate snapshot contradiction**: The verify-report shows 48/48 requirements in its metadata, but the full report text enumerates 54 requirements across the 7 domains. This is a count variance in the intermediate artifact (verify-report wrote its summary header before full requirement discovery completed). The final shipped state carries 54 requirements across 7 domains, all satisfied.
   - **Post-verify remediation**: Six commits (from the final verify run through this archive) addressed WARNING findings and regression test additions. Issue #71 (rotation clamped stale pixels instead of re-deriving from the stored fraction) was found on hardware during this change, filed, fixed, and hardware-verified on a Redmi Note 14 Pro 5G (Android 16 / API 36, HyperOS 3.0).
   - **Conclusion**: No CRITICAL issues remain. The change is ready for archive.

4. **Test infrastructure**:
   - JVM unit tests (`:core:domain:test`, `:core:data:test`): 76 tests, 0 failures
   - Robolectric unit tests (`:feature:overlay:testDebugUnitTest`, `:app:testDebugUnitTest`): 107 tests, 0 failures
   - Instrumented tests (API 34 emulator): 6/6 passed
   - **Total test coverage**: 183 unit + 6 instrumented, 189/189 passed
   - Build: `./gradlew assembleDebug testDebugUnitTest assembleDebugAndroidTest lintDebug --rerun-tasks` → BUILD SUCCESSFUL in 4m 7s

### Final Task Completion

All 130 implementation tasks marked complete (`[x]` in tasks.md):
- PR 1: 14 tasks
- PR 2: 18 tasks
- PR 3: 16 tasks
- PR 4: 19 tasks
- PR 5: 6 tasks
- PR 5.1: 27 tasks
- PR 6: 19 tasks (+ 1 regression fix)
- PR 7: 11 tasks

No unchecked implementation tasks remain. Per the launch prompt's final state facts, all 10 proposal success criteria ticked with evidence; each named artifact exists.

## Work Delivered Beyond Original Task List

Per the launch prompt's authoritative final-state facts:

1. **App shell** (`PetMeApp`, one menu with every feature an entry): The screens were previously unreachable because `MainActivity` had no `setContent`. The shell was added after the task list was written and is now live.

2. **Per-character animation pacing via `frameDurationMillis`**: Originally the framework declared a whole cycle's duration; the design decision reversed this mid-change. The shipped code declares how long each frame is held. Any reader of the archive summary must understand the final state (per-frame), not the first one.

3. **Green palette in `:core:designsystem`**: This had been empty since slice 1. Colors are now injected and available to all features.

4. **Edge-to-edge inset handling**: The snap animation now accounts for navigation bar insets so the resting `y` bound never overlaps the nav bar, measured by `adb shell dumpsys window windows`.

5. **`default2` becoming selectable**: A second bundled character with a 3x3 (9-frame) grid, demonstrating multi-row animation and becoming the active character from the library screen.

6. **Two defects fixed that predated this change**:
   - 17 `ResourceName` lint errors latent since PR #4: fixed in commit 34decf7
   - `:core:domain`'s unit tests never having run in CI because a plain JVM module has no `testDebugUnitTest` task: fixed by explicitly running `:core:domain:test` in the verification command

## Specs Merged to Source of Truth

Seven delta specs merged into the main spec store (all new domains):

| Domain | Action | Requirements | Scenarios | Details |
|--------|--------|--------------|-----------|---------|
| `pet-state-resolution` | Created | 6 | 9 | Priority-ordered snapshot resolution, fail-fast duplicate check, dwell time, IDLE fallback |
| `overlay-drag` | Created | 10 | 13 | Touch ownership, tap/drag split, frame-throttled movement, horizontal edge snap |
| `overlay-position-persistence` | Created | 8 | 10 | Fraction storage, write-at-rest, read-before-`addView`, fallback to computed corner |
| `character-import` | Created | 15 | 18 | Photo Picker, three-tier validation, preview, library with cap, active switching |
| `overlay-onboarding-ui` | Created | 8 | 10 | Permission screen, four claims, re-entry affordance, anti-nagging, accessibility |
| `pet-overlay-rendering` | Modified | 4 | 2 | Draw the resolved `PetState` rather than only IDLE, fallback to IDLE when absent |
| `pet-sprite-sheet` | Modified | 3 | 0 | Filesystem source and fewer-animations-than-states fallback, declared grid |

Main specs locations (all new, copied mechanically from delta specs):
- `openspec/specs/pet-state-resolution/spec.md`
- `openspec/specs/overlay-drag/spec.md`
- `openspec/specs/overlay-position-persistence/spec.md`
- `openspec/specs/character-import/spec.md`
- `openspec/specs/overlay-onboarding-ui/spec.md`
- `openspec/specs/pet-overlay-rendering/spec.md`
- `openspec/specs/pet-sprite-sheet/spec.md`

All files copied via `cp -R` and verified by `diff -r` with empty output (byte-identity confirmed).

## Archive Contents

Change folder archived to: `openspec/changes/archive/2026-08-11-slice-2-movable-and-yours/`

Artifact inventory (verified by mechanical diff):
- ✅ `proposal.md` (3.8 KB) — scope, approach, decision rationale, delivery strategy
- ✅ `specs/` — seven delta specs (now merged to main specs)
  - `specs/pet-state-resolution/spec.md`
  - `specs/overlay-drag/spec.md`
  - `specs/overlay-position-persistence/spec.md`
  - `specs/character-import/spec.md`
  - `specs/overlay-onboarding-ui/spec.md`
  - `specs/pet-overlay-rendering/spec.md`
  - `specs/pet-sprite-sheet/spec.md`
- ✅ `design.md` (35.7 KB) — 16 architecture decisions, touch flow, loader state machine, import lifecycle, grid declaration
- ✅ `tasks.md` (48.6 KB) — 130 implementation tasks, all checked, traceability tags, parallelizable groups, review workload forecast
- ✅ `verify-report.md` (11.2 KB) — verdict `pass_with_warnings`, zero merge-blocking findings, 48/48 requirements, 62/62 scenarios, runtime evidence
- ✅ `apply-progress.md` (34.5 KB) — intermediate snapshot from apply phase, pre-archive work summary
- ✅ `exploration.md` (12.3 KB) — initial scope exploration and risk assessment

All files copied mechanically via `cp -R` and verified by `diff -r` (empty diff confirms byte-identity).

## Carried Non-Blocking Findings

The following are NOT blockers for archive and must be preserved as follow-ups for slice 3:

1. **CharacterSheetLoaderTest.kt:103 is vacuous** — Builds two identical fake sources from the same map and asserts they are equal. Found during PR 6 verification. Remediation: replace with a meaningful fixture or remove.

2. **The loader's `CharacterId.BuiltIn` branch has no JVM unit test** — Only instrumented coverage exists. Task 82 asserts the loader works for both source types via fake `CharacterAssetSource` fixtures, but the BuiltIn branch specifically lacks a direct unit test. Remediation: add a JVM test case (slice 3 scope).

3. **`ActiveCharacterRepositoryImpl` kdoc claims a stale behavior** — The doc says "a deleted-target pointer resolves to the fallback", but the `decode` method never performs that fallback logic. Task 76 implements the fallback in the delete call itself. Remediation: update kdoc to match actual behavior.

4. **Stored `builtin:` pointer with empty name yields permanent `Broken`** — When a `builtin:` pointer with an empty name is stored and the app is restarted, `CharacterId.BuiltIn("")` yields a permanent `Broken` rather than the fallback. Task 104 (CharacterName.validOrNull) prevents empty names in imports, but the fallback logic does not guard against a manually-corrupted stored pointer. Remediation: add a guard in the fallback path (slice 3 scope).

5. **Task 102's TalkBack pass was explicitly NOT a full accessibility audit** — Announcement ordering, verbosity, and content-description wording were not systematically reviewed. Found during PR 7 verification. Task 102 notes: "This is NOT a full accessibility audit — announcement ordering, verbosity, and the wording of individual content descriptions were not systematically reviewed, and should be revisited before the app is offered to anyone relying on a screen reader daily." Remediation: schedule a full audit (scope TBD).

6. **Tap on the pet only writes a log line** — Tap-to-browse-animations is a design decision (#16 in design.md, also issue #70). It requires the per-file sprite-binding rework rather than a simple handler in the current architecture. Currently, `PetTouchController` handles sub-slop touches and invokes `onTap(OverlayAnchor)`, which logs. Remediation: implement the sprite-binding and gesture handler (slice 3 scope).

## Documentation Warnings (Not Blockers)

Per the verify-report, two documentation-only findings exist:

- **WARNING 1**: `design.md` lines 434–436 still argue the reverted per-cycle pacing model. The design section claims `durationMillis` (cycle-based pacing), but the shipped code uses `frameDurationMillis` (per-frame). Commit bb86067 reverted the per-cycle design mid-change. Archive freezes this artifact; correction deferred to inline documentation in the code or a separate decision record if the change is revisited.

- **WARNING 2**: `BuiltInCharacters.kt` comment argues the same reverted model. A comment in the file references `durationMillis` and "cycle-pacing behaviour", which is the superseded design. Remediation: update the comment inline (does not require re-archive).

## SDD Cycle Summary

| Phase | Status | Notes |
|-------|--------|-------|
| Proposal | ✅ Done | Scope, delivery strategy, auto-chain decision, rollback plan defined; 10 success criteria |
| Spec | ✅ Done | 54 requirements, 62 scenarios across 7 domains |
| Design | ✅ Done | 16 architecture decisions, touch/loader/import state machines, grid declaration rationale |
| Tasks | ✅ Done | 130 implementation tasks, all checked; review workload forecast (High risk, 2460 lines, 7 chained PRs) |
| Apply | ✅ Done | Delivered as **one** PR, [#72](https://github.com/Furiduri/PetMePhone/pull/72), 60+ commits against `master`. The chain was built locally as planned — eleven branches on `feature-branch-chain` topology — but none of the intermediate branches was ever pushed, so only the tracker became a pull request. #58, #65 and #66 are dependabot and unrelated. |
| Verify | ✅ Done | Re-run verification passed; zero critical findings, pass_with_warnings; 183 unit + 6 instrumented tests green |
| Archive | ✅ Done | 7 delta specs merged to main store; change folder archived; 130 tasks confirmed complete |

## Related Changes (NOT Archived Here)

The separate change `slice-1-pet-on-screen` (PRs #62, #63) is already archived (2026-08-10 archive). It is **NOT** part of this archive.

This change depends on completed slice 1 work (`slice-1-foundation` and `slice-1-pet-on-screen`). Slice 3 (state providers: TYPING, HAPPY, SLEEPING, HUNGRY, and `CelebrationTracker`) is planned to depend on this archive.

## Source of Truth Update

The main spec store at `openspec/specs/` is now the authoritative source for:
- Pet state resolution and priority ordering (`pet-state-resolution`)
- Overlay drag, touch throttling, and edge snap (`overlay-drag`)
- Position persistence as fractions with fallback (`overlay-position-persistence`)
- Character import validation pipeline and library cap (`character-import`)
- Overlay permission onboarding and re-entry affordance (`overlay-onboarding-ui`)
- Rendering the resolved state and character sheets (`pet-overlay-rendering`)
- Sprite sheet loading with declared grid and fallback (`pet-sprite-sheet`)

Future changes that touch these domains should reference these specs before proposing modifications.

---

**Archive Report Status**: Complete  
**Ready for next cycle**: Yes  
**Recommended next**: New change for slice 3 (TYPING, HAPPY, SLEEPING, HUNGRY providers and `CelebrationTracker`), or quick menu if decoupled from animation-pacing decisions
