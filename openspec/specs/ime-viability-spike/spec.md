# ime-viability-spike Specification

## Purpose

A runnable, maintainer-executed deliverable that measures whether in-overlay text entry is
viable, and records its own findings. It decides #18's IME implementation; nothing in this change
is designed against its outcome.

## Requirements

### Requirement: The spike ships as an installable, runnable app
The spike SHALL be delivered as an installable Android app (or app module) the maintainer builds
and runs on their own device — not as written instructions and a findings template for the
maintainer to fill in by hand.

#### Scenario: Spike installs and runs (maintainer-device-only)
- GIVEN the spike's build output
- WHEN it is installed on the maintainer's device
- THEN it launches a focusable `TYPE_APPLICATION_OVERLAY` window containing a text field over
  another app

### Requirement: The spike records its own findings
The spike app SHALL record its own measurements for each run — not rely on the maintainer
transcribing observations afterward — and persist or display them for the maintainer to commit to
the findings record.

#### Scenario: Findings are recorded by the app (maintainer-device-only)
- GIVEN a completed spike run
- WHEN the maintainer reviews the run's output
- THEN the measured results below are present without requiring manual transcription from memory

### Requirement: The spike measures the defined question set
The spike SHALL measure, per device tested: whether the soft keyboard appears when the field is
tapped; whether the keyboard covers the field; whether IME insets arrive without `imePadding()`;
whether the app underneath loses focus, tested against a playing video; and whether the window
ever remains focusable after the spike's card is dismissed.

#### Scenario: All five questions are measured (maintainer-device-only)
- GIVEN a spike run on a given device
- WHEN its recorded findings are reviewed
- THEN all five questions have a recorded answer for that device — none are left unanswered

### Requirement: The spike measures the cost of window focus separately from IME behavior
The spike SHALL measure, per device tested, whether taking window focus alone — without raising the
soft keyboard — pauses a playing video in the app underneath, and whether focus correctly returns
to the app underneath after the spike's window is dismissed. This measurement is distinct from the
IME question set: it decides whether and how `overlay-quick-menu`'s back-gesture dismissal can ever
be delivered, since a card cannot receive a back key press without first becoming focusable.

#### Scenario: Focus-only cost is measured independent of the keyboard (maintainer-device-only)
- GIVEN a device running the spike, with a playing video in the app underneath
- WHEN the spike's window takes focus without raising the soft keyboard
- THEN the recorded findings state whether the video paused as a result of focus alone

#### Scenario: Focus return after dismissal is measured (maintainer-device-only)
- GIVEN the spike's window has held focus and is then dismissed
- WHEN focus is checked on the app underneath afterward
- THEN the recorded findings state whether focus returned correctly, with no window left
  incorrectly focusable

### Requirement: Findings are committed per device and per OEM skin
The findings record SHALL be committed to the change, with one entry per device/OEM skin tested,
including the video-pause result for each.

#### Scenario: Findings record exists in the diff (machine-verifiable)
- GIVEN the change's diff
- WHEN the spike's findings file is inspected
- THEN it contains at least one recorded entry with a device/OEM identifier and a video-pause
  result

### Requirement: The spike's outcome gates no other work in this change
No production code in this change SHALL depend on the spike's outcome. #27 remains out of scope
regardless of the spike's result.

#### Scenario: No IME implementation exists outside the spike (machine-verifiable)
- GIVEN the change's diff outside the spike app
- WHEN searched for IME-handling production code (`WindowInsetsAnimationCallback`,
  `setOnApplyWindowInsetsListener` wired to the card, or a real text field in the card)
- THEN none exists
</content>
