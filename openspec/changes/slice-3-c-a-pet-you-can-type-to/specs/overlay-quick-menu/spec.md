# Delta for overlay-quick-menu

## MODIFIED Requirements

### Requirement: The card opens in its own independent `WindowManager` window
Tapping the pet SHALL open the quick-menu card in a second, independent `WindowManager` window.
The pet's own window and its `LayoutParams` MUST NOT be mutated to show or hide the card — no
focusability toggling, no `updateViewLayout` on the pet window for this purpose. A text field now
renders inside the card, as part of the task-input content (see `quick-menu-text-input`).

(Previously: asserted that no text field ever renders in the card. That assertion no longer holds
now that the container hosts a task-input content.)

#### Scenario: Card window is separate from the pet window (machine-verifiable)
- GIVEN the pet overlay is running
- WHEN the pet is tapped
- THEN a second `WindowManager` window is added, and the pet window's `LayoutParams` are unchanged

#### Scenario: A text field renders in the card's task-input content (machine-verifiable)
- GIVEN the quick-menu card is open and the task-input content is shown
- WHEN its Compose semantics tree is inspected
- THEN an editable text field exists in the task-input content

### Requirement: The card window is focusable, and the pet window's flags are never mutated
The card window SHALL be created focusable, with `softInputMode = SOFT_INPUT_ADJUST_RESIZE`, at
`addView` time — focusability and `softInputMode` are properties of the `LayoutParams` object at
construction, never a runtime flag mutation on a live window. The pet window's `LayoutParams` MUST
NOT be mutated at any point in the card's lifecycle. On dismissal the card window SHALL be removed,
not hidden, so no focusable window survives the card's lifecycle. Rationale: three device spike
rounds measured `ADJUST_RESIZE` on a focusable window as the only strategy producing a fully
visible field, with zero observed video-pause cost across every round — see
`spike-findings/xiaomi-redmi-note-14-pro-hyperos3-api36.md`.

(Previously: required the card window to be created non-focusable, with no `softInputMode` set,
because window focus's cost on the app underneath was unmeasured. That measurement is now
complete.)

#### Scenario: Card window is created focusable with ADJUST_RESIZE (machine-verifiable)
- GIVEN the card's `WindowManager.LayoutParams`
- WHEN inspected at creation, before `addView`
- THEN the window is focusable and `softInputMode` is `SOFT_INPUT_ADJUST_RESIZE`

#### Scenario: Focusability is never set by a runtime flag mutation (machine-verifiable)
- GIVEN the card's window setup code
- WHEN inspected
- THEN no code path calls `updateViewLayout` or mutates the card's live `LayoutParams` to toggle
  focusability after `addView`; the value is fixed at construction

#### Scenario: The pet window's flags are never mutated by the card's lifecycle (machine-verifiable)
- GIVEN the pet window's `LayoutParams` before the card opens
- WHEN the card is opened and then dismissed
- THEN the pet window's `LayoutParams` are bit-for-bit unchanged throughout

#### Scenario: Dismissal leaves no focusable window attached (machine-verifiable)
- GIVEN the card is open and focusable
- WHEN the card is dismissed
- THEN the card window is removed, not hidden; no focusable window belonging to the card remains
  attached to the `WindowManager`

### Requirement: The card integrates through the existing `onTap` seam only
The card SHALL open by implementing `OverlayTapListener` and being invoked from
`PetOverlayService.onPetTapped(anchor)`. The card MUST NOT attach any touch listener of its own
directly to the pet view.

#### Scenario: onPetTapped delegates to the card (machine-verifiable)
- GIVEN `PetOverlayService.onPetTapped(anchor)` is invoked
- WHEN the quick menu is wired
- THEN it opens the card at the given anchor, and no new touch listener is attached to the pet

### Requirement: `FLAG_WATCH_OUTSIDE_TOUCH` lives on the card window
The card window SHALL declare `FLAG_WATCH_OUTSIDE_TOUCH`; the pet window SHALL NOT.

#### Scenario: Flag ownership (machine-verifiable)
- GIVEN the card window's `LayoutParams`
- WHEN inspected
- THEN `FLAG_WATCH_OUTSIDE_TOUCH` is present on the card window and absent from the pet window

### Requirement: Outside-tap dismissal, with the pet-tap/drag fallback as a primary path
The card SHALL dismiss on `MotionEvent.ACTION_OUTSIDE`. That event is best-effort — it gives no
usable coordinates and does not fire when another window consumes the touch first — so the card
SHALL ALSO dismiss when the pet is tapped again or dragged. The back gesture is now also a
dismissal path (see below); together the three paths MUST guarantee there is no reachable state in
which the card cannot be dismissed.

(Previously: justified the fallback partly by the back gesture being out of scope. The back gesture
is now in scope and is a third dismissal path, not a substitute for the other two.)

#### Scenario: ACTION_OUTSIDE dismisses the card (machine-verifiable)
- GIVEN the card is open
- WHEN a touch outside the card window is delivered as `ACTION_OUTSIDE`
- THEN the card window is removed

#### Scenario: Fallback dismissal via pet tap (machine-verifiable)
- GIVEN the card is open and `ACTION_OUTSIDE` does not fire
- WHEN the pet is tapped again
- THEN the card window is removed

#### Scenario: Fallback dismissal via pet drag (machine-verifiable)
- GIVEN the card is open
- WHEN the pet is dragged
- THEN the card window is removed

#### Scenario: No reachable state leaves the card undismissable (machine-verifiable)
- GIVEN the card is open and another window has consumed the outside touch, so `ACTION_OUTSIDE`
  never fires
- WHEN the pet is tapped or dragged
- THEN the card window is removed; there is no sequence of events after which no dismissal path is
  available

### Requirement: Back-gesture dismissal unwinds the card's container, keyboard first
The card window, now focusable, SHALL wire `OnBackPressedDispatcher` attachment (or equivalent key
interception) so back presses reach the card. Back-gesture handling SHALL follow the three-level
ordering defined in `quick-menu-text-input`: keyboard first, then the container's content back to
the dashboard, then the card itself. This closes issue #17's back-gesture acceptance criterion.

(Previously: declared back-gesture dismissal out of scope, because a non-focusable window receives
no key events. The card is now focusable, so this deviation no longer applies.)

#### Scenario: Back-dispatcher attachment exists on the focusable card (machine-verifiable)
- GIVEN the card's window setup code
- WHEN inspected
- THEN an `OnBackPressedDispatcher` attachment (or equivalent key interception) exists for the card

#### Scenario: Pressing back while the card is open dismisses it, level by level (machine-verifiable)
- GIVEN the card is open on the dashboard content
- WHEN the device back gesture/key is triggered
- THEN the card is dismissed directly, since no keyboard or input content is active to unwind first

#### Scenario: #17's criterion is met (machine-verifiable)
- GIVEN this change's proposal or spec documentation
- WHEN searched for the back-gesture criterion
- THEN an explicit statement exists that issue #17's back-gesture criterion is now met, and #82's
  two-OEM verification gap is recorded separately as a tracked deviation (see below)

### Requirement: A button launches the full-screen app
The card SHALL display a button that, when activated, launches the full-screen application.

#### Scenario: Launch button opens the app (machine-verifiable)
- GIVEN the card is open
- WHEN the launch button is activated
- THEN the app's launcher `Activity` is started

### Requirement: Accessibility minimums are acceptance criteria
Every interactive element in the card SHALL carry a content description or an appropriate
semantic role. Every interactive element SHALL have a touch target of at least 48dp. The card
SHALL NOT contain an undescribed full-bounds touchable scrim.

#### Scenario: Every interactive element is described (machine-verifiable)
- GIVEN the card's Compose semantics tree
- WHEN every interactive node is inspected
- THEN each carries a content description or semantic role, and no full-bounds scrim lacks one

#### Scenario: Touch targets meet the minimum (machine-verifiable)
- GIVEN the card's interactive elements
- WHEN their layout bounds are measured
- THEN each is at least 48dp in both dimensions

#### Scenario: Manual TalkBack pass (maintainer-device-only)
- GIVEN the card is open on a physical device with TalkBack enabled
- WHEN a maintainer swipes through the card and the app underneath
- THEN every element is announced correctly and the app underneath remains reachable

### Requirement: Two-OEM manual verification is a tracked deviation
Issue #18's "verified manually on at least two different OEM skins" acceptance criterion cannot be
honestly satisfied in this change: no Samsung-class device is available to the maintainer. This is
recorded as a named tracked deviation pointing at issue #82, which remains open recording exactly
this gap. It is not marked satisfied, not dropped, and not treated as a blocker on this change —
the same treatment slice 3-B gave issue #17's back-gesture criterion before it was closed.

#### Scenario: The two-OEM deviation is recorded (machine-verifiable)
- GIVEN this change's proposal or spec documentation
- WHEN searched for the two-OEM verification deviation
- THEN an explicit statement exists naming issue #82 as the open tracker for this gap, and stating
  that the criterion is not met by this change
