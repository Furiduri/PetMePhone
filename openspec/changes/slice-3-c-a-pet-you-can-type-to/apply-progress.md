# Apply progress: slice-3-c-a-pet-you-can-type-to

## Phase 1: Pure domain — content and back-outcome (PR 1) — DONE

All tasks 1.1–1.6 complete. TDD RED→GREEN followed throughout.

- Created `core/domain/src/main/kotlin/com/gcatcode/petmephone/core/domain/overlay/QuickMenuContent.kt`
  - `sealed interface QuickMenuContent { Dashboard, TaskInput }`
  - `sealed interface BackOutcome { ShowDashboard, CloseCard }`
  - `fun resolveBack(content: QuickMenuContent): BackOutcome` — total, two-case
  - No `android.*` import.
- Modified `core/domain/src/main/kotlin/com/gcatcode/petmephone/core/domain/overlay/QuickMenuState.kt`
  - Added `QuickMenuEvent.BackPressed` as an additive case (not a reuse of `OutsideTouch`, per
    design decision 9 — `SAME_GESTURE_WINDOW_MS` suppression rationale).
  - Wired `BackPressed` through `reduce` for both `Closed` and `Open` branches, preserving the
    "every event from `Open` yields `Closed`" property.
- Created `core/domain/src/test/kotlin/com/gcatcode/petmephone/core/domain/overlay/QuickMenuContentTest.kt`
  - `ResolveBackTest`: `TaskInput -> ShowDashboard`, `Dashboard -> CloseCard`.
- Modified `core/domain/src/test/kotlin/com/gcatcode/petmephone/core/domain/overlay/QuickMenuStateTest.kt`
  - Added `QuickMenuEvent.BackPressed` to `ALL_EVENTS` and `coverageOf` (exhaustive `when` guard
    already present in the file caught the addition at compile time, as designed).
  - Bumped the `ALL_EVENTS contains every QuickMenuEvent subtype` expected count from 5 to 6.
  - Added `reduce(Closed, BackPressed) is a no-op - closed stays closed`.
  - The existing `every event from Open yields Closed` test now iterates `BackPressed` too, with no
    special-casing.

RED confirmed first: `./gradlew :core:domain:test --rerun-tasks` failed compilation with
`Unresolved reference` errors for `resolveBack`, `QuickMenuContent`, `BackOutcome`, `BackPressed` —
the right reason. Implemented GREEN, re-ran, `BUILD SUCCESSFUL`.

Not touched (correctly out of scope for Phase 1): `feature/overlay/**`, `QuickMenuState.Open`
signature (still `Open(anchor)` only, per design decision 4).

Next: Phase 2 (window focusable + IME target, PR 2, depends on PR 1).
