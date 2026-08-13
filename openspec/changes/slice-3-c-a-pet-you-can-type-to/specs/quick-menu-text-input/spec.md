# quick-menu-text-input Specification

## Purpose

The task-input content hosted inside the quick-menu card's container: the text field, its
focus/keyboard lifecycle, its swap relationship with the dashboard content, and the three-level
back-gesture ordering. Submission and drafts are explicitly out of scope; #100 owns them.

## Requirements

### Requirement: The card is a single-window container hosting one content at a time
The card SHALL be a container that shows exactly one content at a time and swaps it in place. This
change introduces the container seam and its first two contents: the metrics dashboard and the
task input. Swapping content MUST NOT open a new window or surface, and MUST NOT change which
`WindowManager` window is on screen.

#### Scenario: The card opens on the dashboard content when no prior content is remembered (machine-verifiable)
- GIVEN the pet is tapped and no content is remembered from a previous opening
- WHEN the card opens
- THEN the dashboard content is shown, and no keyboard is raised

#### Scenario: Content swap opens no new window (machine-verifiable)
- GIVEN the card is open showing the dashboard content
- WHEN the add-task control is activated
- THEN the task-input content replaces the dashboard content inside the same card window; no
  additional `WindowManager` window is added

#### Scenario: Leaving the input restores the dashboard in the same card (machine-verifiable)
- GIVEN the card is showing the task-input content
- WHEN the input content is left (back or an equivalent leave action)
- THEN the dashboard content is shown again in the same card window

### Requirement: Content selection reads no keyboard-visibility or inset signal
No decision about which content is shown SHALL read keyboard visibility, `WindowInsets.ime`, or any
window-inset value. Content swaps only in direct response to explicit user action (activating the
add-task control, or leaving the input). Rationale: `WindowInsets.ime` is never delivered to this
window class, and `getWindowVisibleDisplayFrame` reported the keyboard resize on some device runs
and not others minutes apart on the same device — see
`spike-findings/xiaomi-redmi-note-14-pro-hyperos3-api36.md`. A content rule built on either signal
would be unreliable by measurement, not by omission.

#### Scenario: No inset or IME signal drives content selection (machine-verifiable)
- GIVEN the container's content-selection logic
- WHEN inspected
- THEN it reads no `WindowInsets.ime` value and no `getWindowVisibleDisplayFrame`/window-inset
  value; the shown content is a pure function of explicit user actions only

#### Scenario: Keyboard visibility changes leave content selection unaffected (machine-verifiable)
- GIVEN the task-input content is shown and the field has focus
- WHEN the keyboard's visible/covered state changes without any explicit leave action
- THEN the task-input content remains shown; no content swap occurs

### Requirement: The field takes focus only on tap
The task-input content's text field SHALL request focus only when the field itself is tapped.
Opening the card, and swapping to the task-input content, MUST NOT request focus or raise the
keyboard.

#### Scenario: Opening the card raises no keyboard (machine-verifiable)
- GIVEN the card is closed
- WHEN the card opens on the dashboard content
- THEN no field has focus and no keyboard is requested

#### Scenario: Swapping to the input content raises no keyboard (machine-verifiable)
- GIVEN the card is open showing the dashboard content
- WHEN the add-task control is activated
- THEN the task-input content is shown with the field unfocused and no keyboard requested

#### Scenario: Tapping the field raises the keyboard (machine-verifiable)
- GIVEN the task-input content is shown with the field unfocused
- WHEN the field is tapped
- THEN the field requests focus and the keyboard is shown

### Requirement: Typed text is discarded, not drafted
Text entered in the field but not submitted SHALL be discarded when the card is dismissed. No
draft SHALL be persisted, and no confirmation prompt SHALL be shown on dismissal.

#### Scenario: Dismissal with unsubmitted text discards it (machine-verifiable)
- GIVEN the task-input content is shown with unsubmitted text entered in the field
- WHEN the card is dismissed
- THEN the text is discarded; no confirmation dialog is shown and nothing is persisted

#### Scenario: Reopening the card never restores prior text (machine-verifiable)
- GIVEN the card was previously dismissed while showing the task-input content with unsubmitted
  text in the field
- WHEN the card is opened again and reopens on the task-input content
- THEN the field is empty — the remembered content is restored, the text is not

### Requirement: The card reopens on the content it was last left on
The container SHALL remember which content was active when the card was dismissed, and SHALL show
that content when the card is opened again. This applies to every dismissal path: tapping the pet,
tapping outside the card, and the back gesture.

Exactly one thing is remembered — which content was active. The field's text is not, per
"Typed text is discarded, not drafted" above.

The remembered content SHALL survive the card window being destroyed and reopened, since the window
is removed rather than hidden on every dismissal. It SHALL NOT be written to persistent storage, and
it is NOT required to survive service teardown or process death; after either, opening on the
dashboard content is correct.

#### Scenario: Dismissing from the input content reopens on the input content (machine-verifiable)
- GIVEN the card is showing the task-input content
- WHEN the card is dismissed by tapping the pet, by tapping outside, or by back
- AND the card is opened again
- THEN the task-input content is shown, with the field unfocused, empty, and no keyboard raised

#### Scenario: Dismissing from the dashboard reopens on the dashboard (machine-verifiable)
- GIVEN the card is showing the dashboard content
- WHEN the card is dismissed and opened again
- THEN the dashboard content is shown

#### Scenario: The remembered content is not persisted (machine-verifiable)
- GIVEN the card was dismissed while showing the task-input content
- WHEN the hosting service is torn down and the card is opened again
- THEN the dashboard content is shown, and no persisted storage was written or read at any point

### Requirement: Submission is out of scope for this change
Submitting from the field SHALL create no task and SHALL call no task-domain use case. #100 owns
submission wiring.

#### Scenario: Submit action calls no task-domain use case (machine-verifiable)
- GIVEN the task-input content is shown with text entered
- WHEN the submit action is activated
- THEN no task-domain use case (`CreateOneOffTask` or otherwise) is invoked and no task is created

### Requirement: Back unwinds exactly one level per press
With the field focused and the keyboard visible, back SHALL dismiss the keyboard only. With the
keyboard already dismissed and the task-input content shown, back SHALL swap to the dashboard
content only. With the dashboard content shown, back SHALL dismiss the card. No single back press
SHALL skip a level.

#### Scenario: First press dismisses the keyboard only (machine-verifiable)
- GIVEN the task-input content is shown, the field is focused, and the keyboard is visible
- WHEN back is pressed once
- THEN the keyboard is dismissed; the task-input content remains shown and the card remains open

#### Scenario: Second press returns to the dashboard only (machine-verifiable)
- GIVEN the task-input content is shown, the keyboard is already dismissed
- WHEN back is pressed once
- THEN the dashboard content is shown; the card remains open

#### Scenario: Third press dismisses the card (machine-verifiable)
- GIVEN the dashboard content is shown
- WHEN back is pressed once
- THEN the card is dismissed

#### Scenario: No single press skips a level (machine-verifiable)
- GIVEN the task-input content is shown, the field is focused, and the keyboard is visible
- WHEN back is pressed once
- THEN the card is still open and the dashboard content is not yet shown — exactly one level
  unwound, not two or three

### Requirement: Field accessibility is an acceptance criterion
The field SHALL carry a content description. The field SHALL have a touch target of at least 48dp.
The field SHALL expose IME action semantics appropriate to its role. Accessibility assertions
SHALL iterate every clickable node in the content's semantics tree rather than asserting against
named test tags.

#### Scenario: The field carries a content description (machine-verifiable)
- GIVEN the task-input content's Compose semantics tree
- WHEN every clickable node is iterated
- THEN the field carries a content description

#### Scenario: The field meets the 48dp touch target minimum (machine-verifiable)
- GIVEN the field's layout bounds
- WHEN measured
- THEN both dimensions are at least 48dp

#### Scenario: The field exposes correct IME action semantics (machine-verifiable)
- GIVEN the field's semantics node
- WHEN inspected
- THEN its IME action matches its role (e.g. a "done"/"send"-class action, not the default)

### Requirement: The field has a placeholder for its empty state
The field SHALL display a plain, descriptive placeholder or hint text when empty, replacing the
function of the now-removed disabled add-task control's label. The placeholder text is a resource
string, not hardcoded.

(Open question flagged for the maintainer: the exact placeholder/hint wording is not decided by
this spec. `feature_overlay_quickmenu_add_task_button` and `..._add_task_description` are retired
by this change, since the control they described no longer exists as a disabled button; the
replacement string content is a product-copy decision, not a spec-level one.)

#### Scenario: Empty field shows a placeholder (machine-verifiable)
- GIVEN the task-input content is shown and the field is empty
- WHEN its Compose semantics tree is inspected
- THEN a non-empty placeholder or hint string, sourced from a string resource, is present
