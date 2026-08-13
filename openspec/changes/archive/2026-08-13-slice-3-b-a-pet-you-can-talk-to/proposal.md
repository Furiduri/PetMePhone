# Proposal: Slice 3 (part B) — A pet you can talk to, the overlay half

Build order slice 3, issues #17 and #18's spike. Part A (#29, #23, #26, #33) is archived.

## Intent

Tapping the pet logs a line and nothing else. This change turns that seam into a real surface: a
quick menu card in its own window, showing what the pet actually knows, plus a runnable spike that
answers whether in-overlay text entry is viable at all — before anyone builds on that assumption.

## Scope

### In Scope
- #17 in full: a second, independent `WindowManager` card window opened from the existing
  `onTap`/`OverlayAnchor` seam, with no change to the drag/touch layer.
- Pure positioning math in `:core:domain` — anchor + bounds + insets in, offset out — unit tested
  at four corner anchors and mid-edges, including clamping.
- `androidx.window` through the version catalog for API 26–29 insets; today's `WindowMetrics` path
  is API 30+ only.
- **New** Hunger `Flow` plumbing (`TaskRepository` counts + `AppClock` today + `BalanceConfig`).
  Part A shipped pure functions with no production consumer; there is no existing flow to reuse.
- A per-metric loading / available / unavailable state. Happiness and Energy have no producer and
  render as loading, never zero.
- Dismissal: `ACTION_OUTSIDE` plus the required pet-tap/drag fallback; back via explicit
  `OnBackPressedDispatcher` wiring or `KEYCODE_BACK`, not `BackHandler` alone.
- Accessibility as acceptance criteria: descriptions/roles, 48dp targets, no undescribed scrim.
- A button launching the full-screen app.
- #18's **spike only**, as a runnable deliverable the maintainer executes on their own hardware,
  with a defined measurement list and a committed findings record.

### Out of Scope
- #18's IME implementation. The spike decides it; nothing is designed against its outcome.
- **#27 is not started.** If the spike says unviable, task creation moves to slice 7 and #27 is
  re-scoped.
- Quick task entry, today's checklist (#28), any fake `TaskRepository`.
- The #38/#70 resolver widening; a `HUNGRY` sprite; creation animations.

## Capabilities

### New Capabilities
- `overlay-quick-menu`: second-window lifecycle, dismissal rules and fallback, back wiring,
  accessibility minimums, app-launch action.
- `quick-menu-positioning`: pure offset math, most-space side selection, inset subtraction,
  clamping, API 26–29 compat.
- `overlay-metric-display`: per-metric loading/available/unavailable; absence never renders as zero.
- `ime-viability-spike`: what must be measured, on what hardware, and how findings are recorded.

### Modified Capabilities
- `hunger-metric`: adds an observable Hunger exposed to consumers.
- `build-foundation`: `androidx.window` added via the version catalog and `ProjectConfig`.

## Approach

Extract a `QuickMenuWindowController`, mirroring `PetTouchController` / `OverlayWindowParams`, so a
negative spike outcome deletes one class rather than unpicking a 365-line service. All decidable
logic (positioning, metric state) is pure and JVM-tested; the window/Compose layer stays thin.

## Affected Areas

| Area | Impact | Description |
|---|---|---|
| `core/domain/.../overlay/` | New | Positioning math, metric state type |
| `core/domain/.../balance/` | Modified | Observable Hunger composition |
| `feature/overlay/.../QuickMenuWindowController.kt` | New | Card window lifecycle |
| `feature/overlay/.../ui/` | New | Card composables, semantics |
| `PetOverlayService.kt` | Modified | `onPetTapped` stub replaced by delegation |
| `PetOverlayStateHolder.kt` | Modified | Metric state exposed |
| `gradle/libs.versions.toml`, `build-logic` | Modified | `androidx.window` |
| `docs/` or change folder | New | Spike instructions and findings record |

## Risks

| Risk | Likelihood | Mitigation |
|---|---|---|
| Spike concludes unviable, stranding work | Medium | Only the deletable controller and pure math are built; #27 untouched |
| Card ships correct on API 30+ only | High | `androidx.window` compat scoped explicitly, tested below 30 |
| `ACTION_OUTSIDE` does not fire, card undismissable | High | Pet-tap/drag fallback is an acceptance criterion |
| Back "just works" assumption | High | Dispatcher wiring named as its own work unit |
| A metric collapses to `0` in Compose | Medium | Sealed state with no zero-valued default anywhere |
| Device-only criteria marked green by CI | Medium | Split honestly: emulator-closable vs maintainer-only |

## Rollback Plan

Chained PRs revert newest-first: spike doc, card UI, controller, Hunger flow, positioning math,
`androidx.window`. `onPetTapped` returns to its stub and the drag layer is untouched, so a full
revert restores today's behaviour exactly.

## Dependencies

- Part A (archived) for `BalanceConfig`, `TaskRepository`, `AppClock`.
- Slice 2's `onTap` seam (merged).
- The maintainer's physical device for the spike and the TalkBack pass.

## Success Criteria

- [ ] Tapping the pet opens a card in its own window; the pet window's flags are never mutated.
- [ ] The card is fully visible at all four corner anchors, clear of system bars and cutouts, on an
      API 26–29 device and an API 30+ device.
- [ ] Hunger renders a real value; Happiness and Energy render loading — no `0` appears.
- [ ] Outside tap dismisses; when `ACTION_OUTSIDE` does not fire, tapping or dragging the pet does.
- [ ] Back dismisses the card through explicit dispatcher wiring.
- [ ] Every interactive element has a description/role and a 48dp target; manual TalkBack pass noted.
- [ ] Positioning tests pass in `:core:domain` with no device.
- [ ] The spike is runnable by the maintainer and its findings are committed, including the
      video-pause result and each OEM skin tested.
- [ ] No `TaskRepository` fake, no IME implementation, no #27 work exists in the diff.

## Proposal question round

Interactive mode, but this executor cannot prompt directly. Assumptions to confirm or correct:

1. The card ships with **no text field at all** in this change — the spike is separate and
   throwaway. Is a disabled/placeholder field acceptable, or must the card show nothing?
2. The spike is delivered as **instructions plus a findings template** the maintainer runs, not as
   automated tests. Should it also ship a minimal throwaway focusable-window sample app?
3. Happiness and Energy render as **loading** (indeterminate) rather than an explicit "not yet
   available" label. Which reads better to you?
4. `androidx.window` is added for API 26–29 correctness. Would you rather raise `minSdk` to 30 and
   drop the compat path entirely?
