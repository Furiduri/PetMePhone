# Proposal: A pet you can type to

Issue #18 — `feat(overlay): focusable text input and IME handling in the quick menu`.

## Intent

The quick menu can be read but not written to: its add-task control ships disabled because no text
field exists, and the card window is non-focusable so no keyboard can ever reach it. Three device
spike rounds on the Redmi Note 14 Pro (HyperOS 3, API 36) settled the two open questions, so the
field can now be built on measurement instead of reasoning.

## Scope

### In scope

- **The pet follows the card while the field holds focus.** This reverses an earlier decision in
  this same change that left the pet untouched. What changed is not the appetite but the evidence:
  that decision was taken when moving the pet required a keyboard height, and three spike rounds
  had established no dependable signal for one. It no longer does — `ADJUST_RESIZE` moves the card
  above the keyboard, so the card's own position is a measured value the pet can be hung on.

- **The card becomes a reusable container that hosts one content at a time**, and the content swaps
  in place rather than opening a new surface. This change introduces the container seam and its
  first two contents: the metrics dashboard (today's card, unchanged in appearance) and the task
  input. See "The container model" below.
- A text field in the input content, focusable on tap only. The card still opens on the dashboard
  content; no auto-focus.
- Card window becomes focusable and sets `SOFT_INPUT_ADJUST_RESIZE`.
- Focus lifecycle with a never-left-focusable-after-dismissal guarantee.
- Back gesture dismisses the keyboard first, then unwinds the container, then the card — closing
  #17's tracked deviation.
- Deliberate revision/removal of `NoBackGestureCodeTest` and `QuickMenuBackGestureDoesNotDismissTest`.

### Out of scope

- Submission. Submit produces no task; no task-domain use case (`CreateOneOffTask` or otherwise) is called. #100 owns that.
- Drafts. Text typed but not submitted is discarded on dismissal — no persistence, no confirmation dialog.
- Orientation-aware window positions, and any keyboard-height detection for the pet.
- Landscape handling: the IME goes fullscreen with its own extracted field.
- Manual keyboard-height measurement, `getWindowVisibleDisplayFrame`, and `WindowInsets.ime` — all measured unreliable or undelivered on this window class.

## Capabilities

### New capabilities

- `quick-menu-text-input`: the field, its focus lifecycle, keyboard visibility strategy, and back-gesture ordering.

### Modified capabilities

- `overlay-quick-menu`: reverses "no text field renders in the card", "the card window is never focusable", and "back-gesture dismissal is out of scope". The "pet window's flags are never mutated" clause is retained unchanged.

## Approach

**Focus and `ADJUST_RESIZE` are one indivisible change.** `dumpsys window windows` showed
`imeLayeringTarget`, `imeInputTarget` and `imeControlTarget` all on a single window: `softInputMode`
is a property of the IME target only, so a non-focusable card ignores any value set on it. Shipping
either half alone is a no-op.

**The container model.** The card is a host, not a screen. It shows exactly one content at a time
and swaps it in place — the same relationship the full-screen app has with its own destinations.
The dashboard content is today's three metric rows and the launch button; the input content is the
text field and its actions. Tapping the add-task control swaps dashboard for input; leaving the
input swaps back.

Two reasons this is the model rather than a nicety:

- **It removes a dependency on a signal measured to be unreliable.** A rule of the form "hide the
  metrics while the keyboard is up" requires knowing when the keyboard is up. Three spike rounds
  established that no such signal is dependable on this window class: `WindowInsets.ime` is never
  delivered, and `getWindowVisibleDisplayFrame` reported the resize on some runs and not others on
  the same device minutes apart. Content selection driven by user action needs no keyboard
  knowledge at all.
- **#100 is a multi-step form inside this same card.** Built as a keyboard-driven visibility rule,
  that seam has to be torn out and rebuilt. Built as a container now, with two contents, #100 adds
  further contents without touching it.

This is the interaction model the maintainer proposed on 2026-08-12 and deferred; #18 is the change
that first requires a second content, so it is where the seam belongs.

**Strategy chosen on evidence.** `ADJUST_PAN` performed no better than the no-strategy control —
the field stayed partially covered and was rejected on device. `ADJUST_RESIZE` on a focusable card
produced a fully visible field across multiple runs and was approved on device. The `adjust=pan`
previously seen in `dumpsys` was the system default; the production card sets no `softInputMode`
today.

| File | Impact | Change |
|------|--------|--------|
| `feature/overlay/.../service/QuickMenuWindowParams.kt` | Modified | Drop `FLAG_NOT_FOCUSABLE`; set `softInputMode = SOFT_INPUT_ADJUST_RESIZE`. Keep `FLAG_NOT_TOUCH_MODAL` and `FLAG_WATCH_OUTSIDE_TOUCH`. |
| `feature/overlay/.../service/OverlayWindowParams.kt` | Unchanged | The pet window's flags are never mutated — an #18 acceptance criterion. Only its position moves, through the existing update path. |
| `feature/overlay/.../quickmenu/QuickMenuWindowController.kt` | Modified | Focus lifecycle, back-gesture ordering. |
| `core/domain/.../overlay/QuickMenuState.kt` | Modified | Additive reducer cases for focused/keyboard-visible and a back event. |
| `feature/overlay/.../quickmenu/ui/QuickMenuCard.kt` | Modified | Split into the container plus a dashboard content; the currently-disabled add-task control becomes the swap trigger. |
| `feature/overlay/.../quickmenu/ui/` (new file) | Added | The input content: text field and its actions. |
| `.../NoBackGestureCodeTest.kt`, `.../QuickMenuBackGestureDoesNotDismissTest.kt` | Revised/removed | A focusable, back-handling card contradicts them. |

**Focus lifecycle.** Focusability is a property of the params at `addView` time, not a runtime flag
mutation. `QuickMenuWindowController.closeWindow()` already removes the view rather than hiding it
and nulls the field, so no focusable window can survive dismissal; `PetOverlayService` holds no
state and `onDestroy()` closes an open card. A hard kill drops the window with the process.

**Automatable vs manual-only.** Automatable: the reducer as pure logic; Compose semantics on the
field (content description, 48dp target, IME action) via clickable-node iteration, never named tags;
an instrumented assertion that the window is created with the expected focus flags and
`softInputMode`, and that `addView`/`removeView` land at the right lifecycle points; a revised
structural test. Manual-only, per device: keyboard appearance and field visibility, back-gesture
ordering, behaviour at every screen edge, and whether a playing video pauses (measured: it does not,
across all rounds).

## Tracked deviation

**#18's "verified manually on at least two different OEM skins" cannot be honestly satisfied.**
No Samsung-class device is available; #82 remains open recording exactly this gap. Carry it as a
named tracked deviation pointing at #82 — not done, not dropped, and not a blocker on this change.
This mirrors how slice 3-B handled #17's back-gesture criterion.

## Risks

| Risk | Likelihood | Mitigation |
|------|------------|------------|
| `ADJUST_RESIZE` behaves differently on another OEM skin | Med | Tracked deviation against #82; overlay text entry is unreleased |
| Window focus disturbs the app underneath on untested devices | Med | Measured zero video-pause cost on all rounds; focus is taken only on field tap, never on card open |
| Removing the structural back-gesture gate lets back handling drift back in unnoticed | Low | Replace it with a revised structural test, not with nothing |
| Submit wiring leaks in from #100 | Low | Non-goal stated explicitly; no task-domain import permitted in this change |

## Rollback

Revert the `QuickMenuWindowParams` flag and `softInputMode` change and the card's field; the window
returns to non-focusable and the pre-existing tests are restored from history. No persisted data,
schema, or pet-window behaviour is touched, so rollback is a pure code revert.

## Dependencies

- Spike findings: `spike-findings/xiaomi-redmi-note-14-pro-hyperos3-api36.md` (sixteen runs, rounds 1–3) — authoritative.
- #100 consumes this field later; #98 makes minimum version required, which is why #100 supersedes #27.

## Success criteria

- [ ] The card opens on the dashboard content with no keyboard; tapping the field raises the keyboard.
- [ ] The add-task control swaps the card's content in place; no second window or surface is opened.
- [ ] Leaving the input content restores the dashboard content in the same card.
- [ ] The card reopens on whichever content it was last dismissed from — pet tap, outside tap or back.
      Only the active content is remembered; the field's text is not, and nothing is persisted.
- [ ] With the keyboard up, the field is fully visible.
- [ ] No content decision reads keyboard visibility or window insets.
- [ ] Back unwinds exactly one step per press, in three levels: keyboard, then input content back to
      dashboard, then the card. No press ever skips a level.
- [ ] Dismissal leaves no focusable window attached.
- [ ] The pet window's flags and `softInputMode` are never mutated; only its position moves.
- [ ] While the field holds focus the card asks for a placement no frame can clamp, and the pet is placed against the card's own laid-out top — no keyboard height is computed anywhere.
- [ ] When the card's frame returns to its largest observed size the card goes back to its opening placement and the pet goes home.
- [ ] A card position that cannot be read moves nothing.
- [ ] The pet's persisted position is never written by a follow move.
- [ ] Submitting creates no task and calls no task-domain use case.
- [ ] The two contradicted tests are deliberately revised or removed, with the reason recorded.
- [ ] The #82 two-OEM gap is recorded as a named tracked deviation.
