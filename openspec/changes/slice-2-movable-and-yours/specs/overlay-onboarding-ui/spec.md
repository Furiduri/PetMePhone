# overlay-onboarding-ui

New capability for `slice-2-movable-and-yours` (#12). Defines the onboarding screen for the
`SYSTEM_ALERT_WINDOW` permission: required copy, the settings hand-off, anti-nagging behaviour,
live re-query on return, the passive re-entry affordance, and accessibility. Permission detection
mechanics (#11) are OUT — this consumes them.

## Requirements

### Requirement: The screen states all four required claims
The onboarding screen SHALL state, in plain language: what will appear (a small pet drawn over
other apps); that the app cannot see, read, or interact with other apps' content and does not
capture the screen; that no data leaves the device; and that the permission is revocable at any
time from system Settings.

#### Scenario: All four claims are present in the rendered screen
- **GIVEN** the onboarding screen is rendered
- **WHEN** its text content is inspected
- **THEN** all four required claims are present

### Requirement: The primary action delegates to the permission mechanics layer
The primary action SHALL invoke the settings intent only through the permission mechanics
interface. The screen MUST NOT make a direct `Settings` call of its own.

#### Scenario: Primary action calls the mechanics layer exactly once
- **GIVEN** the user taps the primary action
- **WHEN** the call is observed
- **THEN** the mechanics layer's settings-launch function is invoked exactly once, and no direct
  `Settings` intent is constructed by the screen itself

### Requirement: The permission state is re-queried live on return, never trusted from a cached value
On returning from Settings, the screen SHALL re-query the permission mechanics layer's live check
rather than relying on any locally held boolean from before navigating away.

#### Scenario: Granting through Settings advances the flow without a manual refresh
- **GIVEN** the user grants the permission through the system Settings UI and returns
- **WHEN** the screen resumes
- **THEN** it re-queries the live permission check and advances the flow automatically, with no
  manual refresh action needed

#### Scenario: Returning without granting leaves the app usable
- **GIVEN** the user returns from Settings without granting
- **WHEN** the screen resumes
- **THEN** the app remains usable and the flow is not blocked

### Requirement: Onboarding does not auto-display again after one refusal
After the first refusal, the onboarding screen SHALL NOT be shown automatically on subsequent app
launches.

#### Scenario: A relaunch after refusal does not show onboarding automatically
- **GIVEN** the user has refused the permission once
- **WHEN** the app is relaunched
- **THEN** the onboarding screen does not appear automatically

### Requirement: A passive, dismissible re-entry point re-launches the flow on demand
After a refusal, a passive, dismissible affordance SHALL exist elsewhere in the app that
re-launches the onboarding flow when the user chooses.

#### Scenario: The re-entry affordance re-launches onboarding
- **GIVEN** the user has refused the permission and dismissed the automatic prompt
- **WHEN** they interact with the passive re-entry affordance
- **THEN** the onboarding flow launches again

### Requirement: Copy contains no dark patterns
Onboarding copy SHALL NOT contain urgency pressure, misleading claims, or any implication that the
app is unusable without the permission.

#### Scenario: Copy is audited for dark patterns
- **GIVEN** all onboarding copy
- **WHEN** it is reviewed
- **THEN** it contains no urgency language, no misleading claim, and no implication that the rest
  of the app requires this permission

### Requirement: The screen meets baseline accessibility requirements
The screen SHALL be readable in light and dark themes and at large font scales. Every interactive
element SHALL have a content description and a touch target of at least 48dp.

#### Scenario: Every interactive element has a description and minimum touch target
- **GIVEN** the rendered onboarding screen
- **WHEN** its interactive elements are inspected
- **THEN** each has a content description and a touch target of at least 48dp

#### Scenario: A manual TalkBack pass is completed and recorded
- **GIVEN** the onboarding screen implementation
- **WHEN** it is reviewed for delivery
- **THEN** a manual TalkBack pass over the whole screen is completed and noted in the pull request
