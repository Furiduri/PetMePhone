# Design: Slice 1 — A pet on screen (#36, IDLE row only)

## Technical Approach

Split by Android dependency, per the proposal. `:core:domain` owns pure grid arithmetic, the fixed
row table, per-row frame counts and the typed failure enum — JVM-testable, no Robolectric.
`:feature:overlay` owns everything that touches `BitmapFactory`, pixels, the screen signal and the
draw scope. `PetOverlayService` gains no state: it swaps `OverlayPlaceholder()` for `PetOverlay()`
and passes the `@Inject`ed state holder it already has a graph for. Satisfies
`specs/pet-sprite-sheet/spec.md` (PR 1) and `specs/pet-overlay-rendering/spec.md` (PR 2).

## The sheet contract, in one paragraph

*One PNG. Six rows, top to bottom: IDLE, DRAGGING, HUNGRY, HAPPY, SLEEPING, TYPING. Cells are
square: cell size = image height ÷ 6, and the image width must be a whole number of those cells —
that number is your column count, so 6 columns means up to 6 frames per state. A state with fewer
frames leaves the rest of its row fully transparent. Nothing else is read: no sidecar file, no
metadata inside the PNG.*

Square cells are the load-bearing derivation. With one image and no metadata, height fixes the cell
height (six known rows) but nothing fixes the column count — squareness closes it without asking the
user for a number they cannot store anywhere.

| Invalid because | Detected at |
|---|---|
| wider or taller than 2048×2048 | header read, before allocation |
| `height % 6 != 0` | header read |
| `width % (height / 6) != 0` (non-square / partial column) | header read |
| `BitmapFactory.decode*` returns null (corrupt) | decode |
| every cell of row 0 fully transparent (no IDLE frames) | post-decode scan |

How a user gets it wrong: exporting at a size that no longer divides (a resize step), or leaving an
opaque near-blank cell where they meant a transparent one — the latter silently inflates the frame
count and is the recorded, unenforced convention risk (#36). The authoring preview tool that would
catch it stays deferred.

## Architecture decisions

| Decision | Choice | Rejected | Rationale |
|---|---|---|---|
| Column count derivation | Square cells; `columns = width / (height / 6)` | user-supplied column count; widest-row heuristic | The only rule that is total, checkable at header stage, and explainable in one sentence |
| Sprites over Lottie; no embedded metadata | Inherited from #36, not re-decided | — | UGC authoring barrier; `tEXt`/`iTXt` chunks are stripped silently by consumer tooling |
| Decoder location | `:feature:overlay/sprite/` | `:core:data` | `:core:data` owns persistence; the decoder has one consumer today. It relocates on evidence when import (#39) becomes the second |
| Failure representation | `sealed interface SpriteSheetResult { Loaded, Failed(SpriteSheetFailure) }` | nullable `Bitmap?`; `Result<Bitmap>` | A nullable makes "absent" and "blank" the same value at the call site. A sealed type forces the renderer to name the broken branch — "absence never renders as zero" enforced by the compiler, not by review |
| Broken placeholder | Shape drawn in `DrawScope` | an asset | An asset used to report decode failure can itself fail to decode |
| Frame state | `MutableIntState` read **inside** the draw lambda | `MutableState<Int>` read in composition | Int specialisation avoids boxing; reading in draw skips recomposition and layout entirely — only the draw phase re-runs |
| Clock | manual `delay()` loop in `LaunchedEffect` | `withInfiniteAnimationFrameNanos` | Fires at 60–120 Hz for a 12–24 fps target and offers no screen-off hook (#36) |
| Screen signal | `callbackFlow` over a runtime-registered `BroadcastReceiver`, `stateIn(WhileSubscribed)` | manifest receiver | `ACTION_SCREEN_ON/OFF` are never delivered to manifest receivers — the rule already recorded in `slice-1-foundation` |

## Data flow

```
assets/pet/idle_default.png
   │  header read (inJustDecodeBounds=true, no pixels)
   ▼
SpriteSheetDecoder ──► SpriteGrid.of(w,h) ──► Valid | Oversized | NotDivisible   [:core:domain]
   │  (only if valid) decode ARGB_8888, inPreferredConfig, no HARDWARE
   ▼
transparent-cell scan ──► SpriteLayout(grid, frameCounts[6])                     [:core:domain]
   ▼
SpriteSheetResult.Loaded(ImageBitmap, layout) | .Failed(reason)
   ▼
PetOverlayStateHolder (@Inject, @Singleton)  ◄── PetAnimationConfig(frameIntervalMillis)
   ▼                                          ◄── ScreenStateMonitor: StateFlow<Boolean>
PetOverlay() ── LaunchedEffect clock ──► frameIndex: MutableIntState
   └── Modifier.drawBehind { drawImage(srcOffset, srcSize, dstOffset, dstSize) }
```

## Zero allocation per frame

What would allocate, and how each is avoided:

- `Bitmap.createBitmap` per cell → never; one resident bitmap, source rects only.
- `BitmapPainter` / `Image()` composable per frame → never; `DrawScope.drawImage` with explicit
  `srcOffset`/`srcSize`/`dstOffset`/`dstSize`. These are `IntOffset`/`IntSize` value classes over
  `Long`, so the per-frame arguments do not reach the heap.
- Boxing of the frame index → `mutableIntStateOf`, not `mutableStateOf`.
- Lambda re-creation → the `drawBehind` lambda and the `ImageBitmap` are `remember`ed; nothing in
  the draw path captures a value that changes.
- Recomposition allocation → the frame index is read only inside draw, so composition never runs
  again after the first frame. This composition lives for days; a per-frame allocation there is not
  a leak but sustained churn on a process the system is already inclined to kill.

Cell rects come from `SpriteLayout.cellLeft(row, frame)` — integer arithmetic, no objects.

## The clock and screen-off suspension

```kotlin
LaunchedEffect(layout, config) {
    screenOn.collectLatest { on ->
        if (!on) return@collectLatest          // true suspension: the loop is cancelled
        while (isActive) {
            delay(config.frameIntervalMillis)  // injected, never a literal
            frameIndex.intValue = (frameIndex.intValue + 1) % layout.idleFrameCount
        }
    }
}
```

`collectLatest` cancels the loop on screen-off, so no timer survives; `frameIndex` lives outside the
effect, so resume continues from where it stopped. If PR 0 finds true suspension unachievable, the
recorded fallback applies: hold at frame 0 while the screen is off, shipped explicitly.

`ScreenStateMonitor` seeds from `PowerManager.isInteractive` so the first emission is real state,
never an assumed `true`.

## PR 0 — the spike, precisely

Measures, on an emulator with the overlay running and the screen off for ≥10 minutes:

1. Does a `delay()`-driven loop inside an overlay composition keep firing at the requested interval,
   get batched by Doze, or stop? Logged timestamps per tick.
2. Do `ACTION_SCREEN_ON`/`ACTION_SCREEN_OFF` actually arrive at a runtime receiver registered from
   the foreground service, and with what latency?
3. Does the composition keep producing draw frames with the screen off (`Choreographer` health),
   which would mean drawing work is being paid for with nobody looking?

Results that change PR 2's design: if (2) fails, the suspension trigger moves off broadcast onto a
`DisplayManager.DisplayListener` and the `StateFlow` source changes; if (1) shows the loop already
stops under Doze, the explicit suspension stays anyway but the acceptance test must not assert on
tick timing while the screen is off; if (3) shows draw frames continue, `drawBehind` must be gated
on the same signal rather than only the clock. Findings are written to #36 and appended here.

## Programmer art

Generated by `ProgrammerArtGenerator`, a `@Ignore`d JUnit test in
`feature/overlay/src/test/.../sprite/` that writes a 6-column × 6-row PNG with `java.awt` (test
source set only — nothing ships). Run manually, output committed to
`feature/overlay/src/main/assets/pet/idle_default.png`. Row 0 gets 4 drawn frames and 2 transparent
cells, so the built-in asset exercises the trailing-transparent clamp on every launch. Replacing it
with real art is dropping a conforming PNG at that exact path — no code change, which is the point
of treating the layout as a public contract. Library assets merge into `:app`, so no module script
changes and the `android { namespace }`-only rule holds.

## File changes

| Path | Action | Purpose |
|---|---|---|
| `core/domain/.../pet/sprite/PetSpriteRow.kt` | Create | Fixed six-entry enum, row indices |
| `core/domain/.../pet/sprite/SpriteGrid.kt` | Create | `of(width,height)` → grid or failure; cell arithmetic |
| `core/domain/.../pet/sprite/SpriteLayout.kt` | Create | Grid + per-row frame counts + cell rect arithmetic |
| `core/domain/.../pet/sprite/SpriteSheetFailure.kt` | Create | `Oversized`, `NotDivisible`, `Undecodable`, `EmptyIdleRow` |
| `feature/overlay/.../sprite/SpriteSheetDecoder.kt` | Create | Header-first validation, ARGB_8888 decode |
| `feature/overlay/.../sprite/TransparentCellScanner.kt` | Create | Trailing-transparent clamp per row |
| `feature/overlay/.../sprite/SpriteSheetResult.kt` | Create | Sealed `Loaded`/`Failed` |
| `feature/overlay/.../ui/PetOverlay.kt` | Create | IDLE draw, clock, broken placeholder |
| `feature/overlay/.../system/ScreenStateMonitor.kt` | Create | `StateFlow<Boolean>` screen-on |
| `feature/overlay/.../ui/PetOverlayStateHolder.kt` | Create | `@Inject` holder: sheet result + config + signal |
| `feature/overlay/.../di/OverlayModule.kt` | Modify | Provide `PetAnimationConfig`, decoder, monitor |
| `feature/overlay/.../service/PetOverlayService.kt` | Modify | `OverlayPlaceholder()` → `PetOverlay(holder)`; delete the magenta composable |
| `feature/overlay/src/main/assets/pet/idle_default.png` | Create | Built-in IDLE sheet |
| `feature/overlay/src/test/.../sprite/ProgrammerArtGenerator.kt` | Create | `@Ignore`d asset generator |

## Interfaces

```kotlin
// :core:domain — Android-free
enum class PetSpriteRow { IDLE, DRAGGING, HUNGRY, HAPPY, SLEEPING, TYPING }

sealed interface SpriteGridResult {
    data class Valid(val grid: SpriteGrid) : SpriteGridResult
    data class Invalid(val failure: SpriteSheetFailure) : SpriteGridResult
}

data class SpriteGrid(val cellSizePx: Int, val columns: Int) {
    companion object { fun of(widthPx: Int, heightPx: Int, maxDimensionPx: Int): SpriteGridResult }
}

data class SpriteLayout(val grid: SpriteGrid, val frameCounts: List<Int> /* size 6 */) {
    fun cellLeftPx(row: PetSpriteRow, frame: Int): Int
    fun cellTopPx(row: PetSpriteRow): Int
}
```

`maxDimensionPx` is injected (2048 today), so the safety bound is a value, not a literal.

## Testing strategy

| Layer | What | How |
|---|---|---|
| Unit (`:core:domain`) | grid derivation, divisibility, oversize, cell arithmetic, frame-count clamping, six-row invariant | JUnit4, pure Kotlin, no Robolectric |
| Unit (`:feature:overlay`) | header-first ordering (oversized fixture never reaches full decode), corrupt bytes → `Failed`, config is ARGB_8888, never HARDWARE | Robolectric + byte fixtures; assert `inJustDecodeBounds` path via a decoder seam |
| Unit | clock advances on injected interval; screen-off cancels advancement; screen-on resumes from the same index | `runTest` virtual time + `MutableStateFlow` fake |
| Instrumented | pet visible over another app; decode failure shows the broken shape, never blank; magenta gone | `createComposeRule` + emulator overlay run |

Deferred with the proposal: the several-hundred-thousand-frame heap-growth test.

## Threat matrix

N/A — no routing, shell, subprocess, VCS/PR automation, executable-file classification, or
process-integration boundary. The decoder parses untrusted image bytes, which is covered by the
header-first size bound above rather than by that matrix.

## PR boundaries

| PR | Content | Must be true before the next starts |
|---|---|---|
| 0 | Screen-off clock spike | The three questions above answered with logged evidence; findings in #36 and appended to this file; any design change recorded here before PR 2 is coded |
| 1 | Domain grid/layout/failure + decoder + scanner + fixtures | `:core:domain` still Android-free (`dependencies` shows no AGP); oversized fixture proves no full decode; corrupt PNG returns `Failed`, never null; `./gradlew test` green |
| 2 | Renderer, clock, screen signal, wiring, placeholder, asset | Pet animates over another app on the emulator; magenta path deleted; interval injected; decode failure draws the shape |

PR 1 targets the slice tracker branch, PR 2 targets PR 1 (`feature-branch-chain`). Each stays under
the 800-line budget on its own; combined they do not.

## Migration / rollout

No migration, no persisted data, no schema. Both PRs are additive and independently revertible;
reverting PR 2 restores the magenta placeholder with the window intact.

## Open questions

- [ ] PR 0's three measurements are unanswered by construction — that is the PR's purpose, not a gap.
- [ ] Whether `inPreferredConfig = ARGB_8888` is honoured on every API 26 emulator image, or needs a
      post-decode `config` assertion with a copy fallback — settle during PR 1.
