# pet-overlay-rendering

Delta spec for the change `slice-1-pet-on-screen` (issue #36, PR 2). Defines IDLE-row drawing,
the manual animation clock, screen-off suspension, and the visibly-broken failure placeholder.
This is a new capability — no existing spec covers it.
Scope boundary: only row 0 (IDLE) is drawn by this change. `DRAGGING`, `HUNGRY`, `HAPPY`,
`SLEEPING`, `TYPING` and state resolution (#37) are OUT and belong to slice 2. Reactive animation
(squash, drag physics, particles, #38) is OUT.

## ADDED Requirements

### Requirement: Only the IDLE row is drawn in this change
The renderer SHALL draw frames only from row 0 (IDLE) of a decoded sheet. It SHALL NOT attempt to
draw any other row, even though the decoded sheet type exposes all six rows.

#### Scenario: Renderer draws exclusively from row 0
- **GIVEN** a decoded sheet with all six rows present
- **WHEN** the pet is composed on screen
- **THEN** every drawn frame is sourced from row 0's cells; no other row is ever drawn

### Requirement: Frame drawing performs zero bitmap allocations per frame
Each drawn frame SHALL be produced by drawing a source rect from the single resident bitmap to a
destination rect, using the existing bitmap in place. Drawing a frame MUST NOT allocate a new
`Bitmap`, a per-frame `BitmapPainter`, or a sub-bitmap.

#### Scenario: Advancing many frames allocates no new bitmaps
- **GIVEN** the IDLE row is animating
- **WHEN** several hundred thousand frame advances are simulated
- **THEN** no new `Bitmap` instance is created for any of them; native/bitmap-attributable heap
  growth stays flat within noise

### Requirement: The animation clock is a manual interval loop, not a per-vsync callback
Frame advancement SHALL be driven by a manual interval loop inside a `LaunchedEffect`, with the
frame interval an injected value (never a hardcoded literal). The clock SHALL NOT be driven by
`withInfiniteAnimationFrameNanos` or any other per-vsync Compose frame callback.

#### Scenario: Frame interval is configurable, not literal
- **GIVEN** the animation clock's construction
- **WHEN** its frame-interval source is inspected
- **THEN** the value is received as an injected parameter, not a literal constant in the clock's
  own code

### Requirement: The animation clock is suspended while the screen is off
A `StateFlow<Boolean>` screen-on signal owned in `:feature:overlay` SHALL be collected by the
animation composition. While the signal reports the screen off, frame advancement SHALL be
suspended — not merely slowed — and SHALL resume from where it left off (or from a defined
resume point) once the screen reports on again.

#### Scenario: Screen-off suspends frame advancement
- **GIVEN** the pet is animating and the screen-on signal transitions to off
- **WHEN** time passes while the signal remains off
- **THEN** no further frame advancement occurs during that interval

#### Scenario: Screen-on resumes frame advancement
- **GIVEN** frame advancement was suspended while the screen was off
- **WHEN** the screen-on signal transitions back to on
- **THEN** frame advancement resumes

#### Scenario: If suspension proves infeasible, the documented fallback is frame-0 suspension
- **GIVEN** the screen-off clock spike finds that true suspension cannot be achieved
- **WHEN** the screen is off
- **THEN** the animation is held suspended at frame 0 rather than continuing to advance, and this
  fallback is the explicitly shipped behavior, not a silent workaround

### Requirement: A sheet that fails to decode renders a visibly-broken placeholder, never blank
When the sprite sheet decode result (see `pet-sprite-sheet`) is a failure, the renderer SHALL
draw a distinct, visibly-broken placeholder shape drawn in code. The renderer MUST NOT render
nothing, MUST NOT render a blank/transparent area, and MUST NOT silently substitute a different
built-in character asset. No user-facing error message is shown in this change.

#### Scenario: Decode failure renders the broken-shape placeholder
- **GIVEN** a sheet decode result that is the explicit failure case
- **WHEN** the pet is composed on screen
- **THEN** a visibly-broken shape drawn in code is rendered in the pet's place; the pet area is
  never blank and no other built-in sheet is substituted

#### Scenario: The broken placeholder is not itself a decodable asset
- **GIVEN** the broken-placeholder rendering path
- **WHEN** its implementation is inspected
- **THEN** it is drawn programmatically (not decoded from an image asset), so it cannot fail the
  same way a corrupt sheet can

### Requirement: The magenta placeholder is fully replaced
`PetOverlayService`'s previous magenta placeholder composable SHALL be replaced by the IDLE
renderer described in this spec; no code path SHALL still draw the magenta placeholder after this
change.

#### Scenario: Overlay shows the pet, not the magenta placeholder
- **GIVEN** the overlay permission granted and the service running
- **WHEN** the overlay is visible over another app
- **THEN** the animated IDLE pet is drawn; the magenta placeholder is never drawn

## Out of Scope

The following are explicitly not covered by this spec and are deferred to slice 2 or later:
- Rendering rows 1–5 (`DRAGGING`, `HUNGRY`, `HAPPY`, `SLEEPING`, `TYPING`) and state resolution (#37)
- Quality tiers and any `inSampleSize`-driven re-render on tier change
- User-facing import error messages
- Reactive animation (squash, drag physics, particles, #38)
