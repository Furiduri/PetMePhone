# overlay-drag

New capability for `slice-2-movable-and-yours` (#15). Defines touch ownership, the tap/drag
split, frame-throttled window movement, horizontal edge snap, and the `onTap` integration
contract. Persistence of the resting position is OUT — see `overlay-position-persistence`.

## Requirements

### Requirement: A single raw touch listener owns the pet's touch stream
The pet's root view SHALL attach exactly one `View.OnTouchListener`, driving movement through
`WindowManager.updateViewLayout`. No second listener SHALL be attached to the same view by any
other feature (for example a future quick menu) — `onTap` is the only integration point.

#### Scenario: Only one consumer of the touch stream exists
- **GIVEN** the pet's root view
- **WHEN** its touch listeners are inspected
- **THEN** exactly one `OnTouchListener` is attached

### Requirement: Movement below touch slop does not move the pet
Movement SHALL begin only once the distance from the initial touch point exceeds
`ViewConfiguration.scaledTouchSlop` for the current device. Movement below that threshold MUST
NOT change the window position.

#### Scenario: Sub-slop movement leaves the pet in place
- **GIVEN** a touch down followed by movement below `scaledTouchSlop`
- **WHEN** the touch is released
- **THEN** the window position is unchanged from before the touch began

#### Scenario: Movement past slop starts continuous dragging
- **GIVEN** a touch down followed by movement past `scaledTouchSlop`
- **WHEN** the finger continues moving
- **THEN** the window position follows the finger continuously until release

### Requirement: A release without exceeding slop invokes onTap, never a drag
When a touch sequence never exceeds slop, `ACTION_UP` SHALL invoke the `onTap` callback and SHALL
NOT trigger a snap animation or a window position change.

#### Scenario: A tap invokes onTap exactly once
- **GIVEN** a touch down and up within `scaledTouchSlop` and no intervening drag
- **WHEN** the touch is released
- **THEN** `onTap` is invoked exactly once and no snap animation runs

### Requirement: Window movement is throttled to at most one call per display frame
During an active drag, `ACTION_MOVE` events SHALL update a pending position; a single
`Choreographer` frame callback SHALL issue at most one `updateViewLayout` call per rendered frame.

#### Scenario: Rapid ACTION_MOVE events collapse to one update per frame
- **GIVEN** a drag producing `ACTION_MOVE` events at a rate above the display refresh rate
- **WHEN** `updateViewLayout` calls are counted over the drag
- **THEN** the count never exceeds one per rendered frame

### Requirement: Release after a drag snaps to the nearest horizontal edge
On `ACTION_UP` after a drag, the pet SHALL animate with a spring to the nearest left or right
screen edge. The vertical coordinate SHALL remain exactly where the touch was released; snapping
applies to the horizontal axis only.

#### Scenario: Releasing closer to the left edge snaps left
- **GIVEN** a drag released at a horizontal position closer to the screen's left edge than its right
- **WHEN** the release triggers the snap
- **THEN** the pet animates to rest against the left edge with a spring, not a jump

#### Scenario: The vertical position is preserved through the snap
- **GIVEN** a drag released at a given vertical coordinate
- **WHEN** the horizontal snap animation completes
- **THEN** the final vertical coordinate equals the coordinate at release

### Requirement: DRAGGING is exposed as transient in-memory state, never persisted or service state
Drag-in-progress SHALL be published through a `@Singleton` in-memory `StateFlow`. It MUST NOT be
written to any persistent store and MUST NOT be held as a field of `PetOverlayService`.

#### Scenario: DRAGGING is true only while a drag is active
- **GIVEN** a drag starts and later ends
- **WHEN** the `StateFlow` is observed across that lifetime
- **THEN** it reports true only between drag start and drag end, and never touches persistence

### Requirement: The pet never settles under the navigation bar
The nearest-edge computation SHALL account for navigation bar insets so the resting position is
never obscured by it.

#### Scenario: A drag ending near the nav bar still rests above it
- **GIVEN** a drag released at a vertical position that would overlap the navigation bar inset
- **WHEN** the snap completes
- **THEN** the pet's final bounds do not overlap the navigation bar

### Requirement: In-flight movement work is cancelled if the service is destroyed mid-drag
Any pending `Choreographer` frame callback and any running snap animation coroutine SHALL be
cancelled when the hosting service is destroyed while a drag is in progress.

#### Scenario: Service destruction mid-drag leaves no dangling callback
- **GIVEN** an active drag with a pending frame callback and a running snap animation
- **WHEN** the service is destroyed
- **THEN** the frame callback and the animation coroutine are both cancelled with no crash from a
  callback holding a dead view

### Requirement: Pure drag logic is testable without Android
`nearestEdge`, fraction-pixel conversion, and the slop-based drag decision SHALL live in
`:core:domain` and be unit-testable on the JVM without any Android dependency.

#### Scenario: nearestEdge is exercised on the JVM
- **GIVEN** a table of horizontal positions and screen bounds
- **WHEN** `nearestEdge` is invoked in a JVM unit test with no Android framework present
- **THEN** each case resolves to the expected edge, including the exact-centre tie-break
