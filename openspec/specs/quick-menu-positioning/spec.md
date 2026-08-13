# quick-menu-positioning Specification

## Purpose

Pure positioning math in `:core:domain` for the quick-menu card: pet anchor, screen bounds, and
insets in, card offset out. No device or Android dependency; unit-tested exhaustively.

## Requirements

### Requirement: Positioning is a pure function with no Android dependency
The card offset calculation SHALL be a pure function in `:core:domain`, taking the pet's
`OverlayAnchor`, screen bounds, the card's measured size, and inset values as input, and returning
an offset. It MUST NOT reference any `androidx.*` or `android.*` type.

#### Scenario: Same inputs always produce the same offset (machine-verifiable)
- GIVEN a fixed anchor, bounds, card size, and insets
- WHEN the offset is calculated repeatedly
- THEN it returns the same result every time with no I/O performed

### Requirement: The card opens toward the side with the most available space
Given the pet's anchor position within the screen bounds, the calculation SHALL choose the
horizontal and vertical direction with the most available space and offset the card accordingly.

#### Scenario: Pet at top-left corner opens down-right (machine-verifiable)
- GIVEN the pet anchored at the top-left corner of the screen
- WHEN the offset is calculated
- THEN the card is placed below and to the right of the pet

#### Scenario: Pet at top-right corner opens down-left (machine-verifiable)
- GIVEN the pet anchored at the top-right corner of the screen
- WHEN the offset is calculated
- THEN the card is placed below and to the left of the pet

#### Scenario: Pet at bottom-left corner opens up-right (machine-verifiable)
- GIVEN the pet anchored at the bottom-left corner of the screen
- WHEN the offset is calculated
- THEN the card is placed above and to the right of the pet

#### Scenario: Pet at bottom-right corner opens up-left (machine-verifiable)
- GIVEN the pet anchored at the bottom-right corner of the screen
- WHEN the offset is calculated
- THEN the card is placed above and to the left of the pet

#### Scenario: Pet at a mid-edge position picks the larger perpendicular space (machine-verifiable)
- GIVEN the pet anchored at the vertical mid-point of the left edge
- WHEN the offset is calculated
- THEN the card is placed to the right of the pet, vertically centered toward the side with more
  room

### Requirement: The offset is clamped to available space and never leaves the screen
When the card's measured width or height exceeds the available space in the chosen direction, the
offset SHALL be clamped so the card remains fully within the screen bounds.

#### Scenario: Card wider than available space is clamped (machine-verifiable)
- GIVEN a card wider than the remaining horizontal space in the chosen direction
- WHEN the offset is calculated
- THEN the resulting card bounds do not exceed the screen's horizontal bounds

#### Scenario: Card taller than available space is clamped (machine-verifiable)
- GIVEN a card taller than the remaining vertical space in the chosen direction
- WHEN the offset is calculated
- THEN the resulting card bounds do not exceed the screen's vertical bounds

### Requirement: System-bar and display-cutout insets are subtracted from available space
The calculation SHALL subtract system-bar insets and display-cutout insets from the usable screen
bounds before computing available space in any direction.

#### Scenario: Card near the status bar clears it (machine-verifiable)
- GIVEN the pet anchored near the top edge and a non-zero top system-bar inset
- WHEN the offset is calculated
- THEN the resulting card bounds start below the inset, never under the status bar

#### Scenario: Card near a display cutout clears it (machine-verifiable)
- GIVEN the pet anchored near a display cutout and a non-zero cutout inset on that edge
- WHEN the offset is calculated
- THEN the resulting card bounds do not overlap the cutout inset region

### Requirement: Inset resolution uses the `WindowMetrics` API 30+ path exclusively
Because `minSdk` is 30, inset resolution for positioning SHALL use the `WindowMetrics`
`getInsetsIgnoringVisibility(systemBars() or displayCutout())` path only. No `androidx.window`
compatibility path SHALL exist for any API level below 30.

#### Scenario: No androidx.window dependency for inset compat (machine-verifiable)
- GIVEN the version catalog and module dependency graphs
- WHEN inspected
- THEN no `androidx.window` compatibility artifact is declared for pre-30 inset support
</content>
