# overlay-position-persistence

New capability for `slice-2-movable-and-yours` (#16). Defines fraction-based position storage,
write-once-at-rest timing, and read-before-`addView` startup ordering. Replaces the pixel-based
keys shipped ahead of schedule in slice 1 (see the `pet-overlay-rendering`/domain MODIFIED note in
the proposal) — decision 3.

## Requirements

### Requirement: Position is persisted as screen fractions, never absolute pixels
The repository SHALL store `xFraction` and `yFraction`, each a `Float` in `0.0..1.0`, in DataStore
Preferences via `floatPreferencesKey`. No absolute pixel coordinate SHALL be persisted anywhere.

#### Scenario: Persisted keys are fraction-typed
- **GIVEN** the DataStore Preferences schema for position
- **WHEN** its keys are inspected
- **THEN** both keys are `floatPreferencesKey`s constrained to `0.0..1.0`, and no
  `intPreferencesKey` for position exists

#### Scenario: Round-trip conversion is lossless within tolerance
- **GIVEN** a fraction converted to pixels for a given screen size and back to a fraction
- **WHEN** the result is compared to the original
- **THEN** it matches within floating-point tolerance

### Requirement: Absence of a stored value emits null, never a fabricated zero
When no value has ever been written, the repository SHALL emit `null` for the position. It MUST
NOT emit `0f`, `(0f, 0f)`, or any other fabricated coordinate as a stand-in for "no value."

#### Scenario: First launch with nothing stored emits null
- **GIVEN** a DataStore instance with no position keys ever written
- **WHEN** the position `Flow` is collected
- **THEN** it emits `null`, and no test observes `0f` for either fraction

#### Scenario: Null falls back to the computed resting corner, not to zero
- **GIVEN** the position `Flow` emits `null`
- **WHEN** the pet's initial placement is computed
- **THEN** it uses the resting-corner computation against live screen bounds, never coordinate
  `(0, 0)` and never a hardcoded default fraction

### Requirement: The write happens once per gesture, at rest
A write to the position store SHALL occur exactly once per completed drag gesture, after the snap
animation finishes and the final resting position is known. `ACTION_MOVE` events and intermediate
animation frames MUST NOT trigger a write.

#### Scenario: A completed drag writes exactly one entry
- **GIVEN** a drag that moves, releases, and completes its snap animation
- **WHEN** the write activity is counted for that gesture
- **THEN** exactly one `DataStore.edit` call occurs, after the animation completes

### Requirement: A new drag cancels a pending write from the previous gesture
If a new drag starts before the previous gesture's write has completed, the pending write SHALL be
cancelled rather than allowed to persist a stale value.

#### Scenario: Starting a new drag before the prior write lands cancels it
- **GIVEN** a snap animation just completed and its write is in flight
- **WHEN** a new drag begins before that write finishes
- **THEN** the in-flight write is cancelled and only the eventual new gesture's resting position is
  persisted

### Requirement: The persisted position is read before the overlay window is added
The service SHALL await the first position emission (success, stored value, or timeout fallback)
before calling `addView`. The overlay MUST NOT appear at a default position and then jump to the
stored one.

#### Scenario: Cold start with a stored value shows no jump
- **GIVEN** a previously stored position
- **WHEN** the service creates the overlay window on a cold start
- **THEN** the window is added directly at the stored position, with no intermediate default frame

#### Scenario: A slow read falls back to the default after a timeout, without blocking indefinitely
- **GIVEN** the position read does not complete within an injected timeout
- **WHEN** the timeout elapses
- **THEN** the service proceeds with the computed resting corner rather than waiting further

### Requirement: Restart restores the last resting position
Killing and restarting the service SHALL restore the pet to the position last written before the
kill.

#### Scenario: Kill and restart restores position
- **GIVEN** a resting position was written before the service was killed
- **WHEN** the service restarts
- **THEN** the pet appears at that same position with no jump

### Requirement: Rotation and differing screen dimensions preserve a sensible relative position
Because the stored value is a fraction, rotating the device or restoring on a device with
different screen dimensions SHALL keep the pet on screen at an equivalent relative position rather
than off-screen or at a nonsensical pixel location.

#### Scenario: Rotating mid-session keeps the pet on screen
- **GIVEN** a stored fractional position
- **WHEN** the device rotates
- **THEN** the pet's on-screen position is recomputed from the same fraction against the new
  bounds and stays fully on screen

#### Scenario: Restoring on a different screen size stays on screen
- **GIVEN** a fraction stored on one device
- **WHEN** it is read on a device with different screen dimensions
- **THEN** the resulting pixel position is within the new screen's bounds
