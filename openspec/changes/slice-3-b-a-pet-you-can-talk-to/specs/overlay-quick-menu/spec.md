# overlay-quick-menu Specification

## Purpose

Second-window lifecycle for the tap-triggered quick menu card: opening, dismissal (via
`ACTION_OUTSIDE` and the required pet-tap/drag fallback), accessibility minimums, and the
app-launch action. No text input field exists in this capability. The card ships non-focusable in
this change; back-gesture dismissal is explicitly deferred (see below).

## Requirements

### Requirement: The card opens in its own independent `WindowManager` window
Tapping the pet SHALL open the quick-menu card in a second, independent `WindowManager` window.
The pet's own window and its `LayoutParams` MUST NOT be mutated to show or hide the card — no
`FLAG_NOT_FOCUSABLE` toggling, no `updateViewLayout` on the pet window for this purpose.

#### Scenario: Card window is separate from the pet window (machine-verifiable)
- GIVEN the pet overlay is running
- WHEN the pet is tapped
- THEN a second `WindowManager` window is added, and the pet window's `LayoutParams` are unchanged

#### Scenario: No text field renders in the card (machine-verifiable)
- GIVEN the quick-menu card is open
- WHEN its Compose semantics tree is inspected
- THEN no editable text field or IME-triggering element exists anywhere in the card

### Requirement: The card window is never focusable and the pet window's flags are never mutated
The card window SHALL be created non-focusable in this change (`FLAG_NOT_FOCUSABLE` set, no
`FLAG_ALT_FOCUSABLE_IM`, no window-focus request of any kind). The pet window's `LayoutParams`
MUST NOT be mutated at any point in the card's lifecycle. Rationale: taking window focus causes
`onWindowFocusChanged(false)` in the app underneath — video players pause, text fields lose their
cursor — which is exactly the cost issue #18's spike exists to measure before it is incurred. This
change ships the shell before that spike reports, so the shell must not incur that cost itself.

#### Scenario: Card window is created non-focusable (machine-verifiable)
- GIVEN the card's `WindowManager.LayoutParams`
- WHEN inspected at creation
- THEN `FLAG_NOT_FOCUSABLE` is set, `FLAG_ALT_FOCUSABLE_IM` is not set, and no code path requests
  window focus for the card

#### Scenario: The app underneath never loses window focus because of the card (machine-verifiable)
- GIVEN another app is in the foreground with a window focus listener
- WHEN the card is opened, interacted with, and dismissed
- THEN the app underneath never receives `onWindowFocusChanged(false)` as a result of the card

#### Scenario: The pet window's flags are never mutated by the card's lifecycle (machine-verifiable)
- GIVEN the pet window's `LayoutParams` before the card opens
- WHEN the card is opened and then dismissed
- THEN the pet window's `LayoutParams` are bit-for-bit unchanged throughout

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
SHALL ALSO dismiss when the pet is tapped again or dragged. With the back gesture out of scope for
this change (see below), this fallback is not a safety net for a rare edge case: it is one of only
two dismissal paths that exist, and MUST guarantee there is no reachable state in which the card
cannot be dismissed.

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
- THEN the card window is removed; there is no sequence of events after which neither dismissal
  path is available

### Requirement: Back-gesture dismissal is out of scope for this change
The card SHALL NOT wire `OnBackPressedDispatcher` attachment or `KEYCODE_BACK` interception in
this change, and back-gesture dismissal is explicitly NOT implemented. A non-focusable window
receives no key events, so there is no mechanism to deliver a back press to; wiring a dispatcher
with nothing able to receive it would be dead code presented as a feature. This is a tracked
deviation from issue #17's back-gesture acceptance criterion, not a silent gap: #17's criterion is
NOT met by this change. Back-gesture dismissal becomes deliverable only after the
`ime-viability-spike` reports on the cost of window focus, which decides whether and how the card
can safely become focusable.

#### Scenario: No back-dispatcher or key-interception code exists (machine-verifiable)
- GIVEN the card's window setup code
- WHEN inspected
- THEN no `OnBackPressedDispatcher` attachment and no `KEYCODE_BACK` interception exists for the
  card

#### Scenario: Pressing back while the card is open does not dismiss it (machine-verifiable)
- GIVEN the card is open
- WHEN the device back gesture/key is triggered
- THEN the card remains open; dismissal only occurs via `ACTION_OUTSIDE` or the pet-tap/drag
  fallback

#### Scenario: The deviation is recorded (machine-verifiable)
- GIVEN this change's proposal or spec documentation
- WHEN searched for the back-gesture deviation
- THEN an explicit statement exists that issue #17's back-gesture criterion is deferred, and why

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
