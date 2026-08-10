# Design: Slice 1 — A pet on screen (#36, IDLE row only)

## Technical Approach

Split by Android dependency, per the proposal. `:core:domain` owns pure grid arithmetic, the fixed
row table, per-row frame counts and the typed failure enum — JVM-testable, no Robolectric.
`:feature:overlay` owns everything that touches `BitmapFactory`, pixels, the screen signal and the
draw scope. `PetOverlayService` gains no state: it swaps `OverlayPlaceholder()` for `PetOverlay()`
and passes the `@Inject`ed state holder it already has a graph for. Satisfies
`specs/pet-sprite-sheet/spec.md` (PR 1) and `specs/pet-overlay-rendering/spec.md` (PR 2).

## The sheet contract, in one paragraph

**Corrected by `feat/sheet-per-animation`** — the original six-row grid below was replaced before
any real character shipped more than IDLE, because a fixed row count makes adding a state a
breaking change to artwork users already drew. The corrected, simpler contract:

*One image = one animation = one row of square frames. Cell side = image height. Frame count =
image width ÷ image height — a remainder is never truncated, it invalidates the sheet. Assets live
one folder per character, fixed filenames inside: `pet/<character>/idle.png`. `idle.png` is
required; every other animation filename is optional, and its absence is an ordinary, valid state,
never an error. Nothing else is read: no sidecar file, no metadata inside the PNG, no row count.*

| Invalid because | Detected at |
|---|---|
| wider or taller than 2048px (either axis) | header read, before allocation |
| `height <= 0` | header read |
| `width % height != 0` | header read |
| `BitmapFactory.decode*` returns null (corrupt) | decode |
| every cell fully transparent (no usable frames) | post-decode scan |

<details>
<summary>Original six-row-grid contract (superseded, kept for history)</summary>

*One PNG. Six rows, top to bottom: IDLE, DRAGGING, HUNGRY, HAPPY, SLEEPING, TYPING. Cells are
square: cell size = image height ÷ 6, and the image width must be a whole number of those cells —
that number is your column count, so 6 columns means up to 6 frames per state. A state with fewer
frames leaves the rest of its row fully transparent.*

This was rejected because exactly six states were fixed forever (a new state = breaking artwork
change), the whole 36-cell sheet had to be resident to draw six cells (~16.7MB for a 2046×2046
sheet), and a character missing one state made the whole sheet suspect instead of modelling an
ordinary absence.
</details>

How a user gets it wrong: exporting at a size that no longer divides (a resize step), or leaving an
opaque near-blank cell where they meant a transparent one — the latter silently inflates the frame
count and is the recorded, unenforced convention risk (#36). The authoring preview tool that would
catch it stays deferred.

## Architecture decisions

| Decision | Choice | Rejected | Rationale |
|---|---|---|---|
| Sheet scope | One image = one animation = one row | six-row fixed grid (original, superseded) | Fixed row count made adding a state a breaking artwork change, forced full-sheet residency to draw one row, and made a missing state look like sheet corruption instead of an ordinary absence |
| Column count derivation | Square cells; `columns = width / height` | user-supplied column count; widest-row heuristic | The only rule that is total, checkable at header stage, and explainable in one sentence |
| Asset layout | Folder per character, fixed filenames inside (`pet/<character>/idle.png`) | filename carrying both character and animation as a single token | A folder position survives being moved/renamed better than a name encoding two pieces of data |
| Sprites over Lottie; no embedded metadata | Inherited from #36, not re-decided | — | UGC authoring barrier; `tEXt`/`iTXt` chunks are stripped silently by consumer tooling |
| Decoder location | `:feature:overlay/sprite/` | `:core:data` | `:core:data` owns persistence; the decoder has one consumer today. It relocates on evidence when import (#39) becomes the second |
| Failure representation | `sealed interface SpriteSheetResult { Loaded, Failed(SpriteSheetFailure) }` | nullable `Bitmap?`; `Result<Bitmap>` | A nullable makes "absent" and "blank" the same value at the call site. A sealed type forces the renderer to name the broken branch — "absence never renders as zero" enforced by the compiler, not by review |
| Broken placeholder | Shape drawn in `DrawScope` | an asset | An asset used to report decode failure can itself fail to decode |
| Frame state | `MutableIntState` read **inside** the draw lambda | `MutableState<Int>` read in composition | Int specialisation avoids boxing; reading in draw skips recomposition and layout entirely — only the draw phase re-runs |
| Clock | manual `delay()` loop in `LaunchedEffect` | `withInfiniteAnimationFrameNanos` | Fires at 60–120 Hz for a 12–24 fps target and offers no screen-off hook (#36) |
| Screen signal | `callbackFlow` over a runtime-registered `BroadcastReceiver`, `stateIn(WhileSubscribed)` | manifest receiver | `ACTION_SCREEN_ON/OFF` are never delivered to manifest receivers — the rule already recorded in `slice-1-foundation` |

## Data flow

```
assets/pet/default/idle.png
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

PR 0 finding (3) confirmed `Choreographer` draw callbacks continue while the screen is off, so
`PetOverlay`'s `drawBehind` lambda itself checks the same `screenOn` value the clock collects
(read, not re-collected, inside the lambda) before issuing `drawImage`, rather than relying on the
clock alone to stop draw-attributable work.

## PR 0 — the spike, precisely (measured)

Measured on `emulator-5554` with the overlay running and the screen off for ~2m24s. **Deviation
from the original plan**: the window was ≥10 minutes in the plan; actual execution used ~2m24s due
to session time constraints. This is recorded honestly rather than silently — see the caveat on
finding (1) below.

Harness: a disposable 1s `delay()` tick loop, a runtime `ACTION_SCREEN_ON`/`OFF`
`BroadcastReceiver`, and a `Choreographer.FrameCallback` draw-frame logger, all temporarily added to
`PetOverlayService`/`OverlayPlaceholder` and removed before this PR closed (task 9).

1. **Does the `delay()` loop keep firing, get Doze-batched, or stop?** It kept firing at ~1s
   intervals for the entire off window (149 ticks logged across the run, continuous through the
   screen-off period). No Doze-driven stop or batching was observed — **but** a ~2.4 minute emulator
   window is too short to reliably trigger Doze's standard idle thresholds, so this is not proof
   Doze never intervenes over longer real-world durations. The actionable conclusion is unchanged
   either way: ship explicit suspension, don't rely on Doze to do it for you.
2. **Do `ACTION_SCREEN_ON`/`ACTION_SCREEN_OFF` arrive at a runtime receiver from the foreground
   service, and with what latency?** Yes, reliably: `SCREEN_OFF` arrived ~100ms after the trigger,
   `SCREEN_ON` arrived effectively immediately on wake.
3. **Does the composition keep producing draw frames with the screen off?** Yes — `Choreographer`
   draw callbacks fired continuously through the entire off window with no gap, confirming draw work
   is paid for even while nobody is looking.

### Design impact (baseline vs. actual)
- (2) matches the baseline assumption. `ScreenStateMonitor` stays exactly as designed: a runtime
  `BroadcastReceiver`, `stateIn(WhileSubscribed)`, seeded from `PowerManager.isInteractive`. **No
  change.**
- (1) the loop does not self-stop within the measured window. Explicit suspension
  (`collectLatest` cancel-on-screen-off) stays exactly as designed. The PR 2 acceptance test must
  not assert on exact tick timing while the screen is off, since Doze behavior beyond the measured
  window remains unconfirmed.
- (3) confirmed draw frames continue unless gated. `PetOverlay`'s `drawBehind` is gated on the same
  `screenOn` signal the clock uses, not the clock alone — implemented in PR 2.

Findings posted to issue #36: https://github.com/Furiduri/PetMePhone/issues/36#issuecomment-5242884355

## Programmer art

Generated by `ProgrammerArtGenerator`, a `@Ignore`d JUnit test in
`feature/overlay/src/test/.../sprite/` that writes a single-row, 6-frame PNG with `java.awt` (test
source set only — nothing ships). Run manually, output committed to
`feature/overlay/src/main/assets/pet/default/idle.png`. The migrated shipped asset is 2046×341 (341px
cells, 6 frames), 4 drawn frames plus transparent trailing cells so the built-in asset exercises the
trailing-transparent clamp on every launch. Replacing it with real art is dropping a conforming PNG
at that exact path — no code change, which is the point of treating the layout as a public
contract. Library assets merge into `:app`, so no module script changes and the
`android { namespace }`-only rule holds.

## File changes

| Path | Action | Purpose |
|---|---|---|
| `core/domain/.../pet/sprite/SpriteGrid.kt` | Create | `of(width,height)` → grid or failure; cell arithmetic |
| `core/domain/.../pet/sprite/SpriteLayout.kt` | Create | Grid + one frame count + cell rect arithmetic |
| `core/domain/.../pet/sprite/SpriteSheetFailure.kt` | Create | `Oversized`, `NotDivisible`, `Undecodable`, `EmptySheet` |
| `feature/overlay/.../sprite/SpriteSheetDecoder.kt` | Create | Header-first validation, ARGB_8888 decode |
| `feature/overlay/.../sprite/TransparentCellScanner.kt` | Create | Trailing-transparent clamp on the single row |
| `feature/overlay/.../sprite/SpriteSheetResult.kt` | Create | Sealed `Loaded`/`Failed` |
| `feature/overlay/.../ui/PetOverlay.kt` | Create | IDLE draw, clock, broken placeholder |
| `feature/overlay/.../system/ScreenStateMonitor.kt` | Create | `StateFlow<Boolean>` screen-on |
| `feature/overlay/.../ui/PetOverlayStateHolder.kt` | Create | `@Inject` holder: sheet result + config + signal |
| `feature/overlay/.../di/OverlayModule.kt` | Modify | Provide `PetAnimationConfig`, decoder, monitor |
| `feature/overlay/.../service/PetOverlayService.kt` | Modify | `OverlayPlaceholder()` → `PetOverlay(holder)`; delete the magenta composable |
| `feature/overlay/src/main/assets/pet/default/idle.png` | Create | Built-in IDLE sheet, one folder per character |
| `feature/overlay/src/test/.../sprite/ProgrammerArtGenerator.kt` | Create | `@Ignore`d asset generator |

**`PetSpriteRow.kt` was removed** (`feat/sheet-per-animation`): an animation's identity now comes
from its file, not from a row index, so a fixed six-entry enum has no reason to exist.

## Interfaces

```kotlin
// :core:domain — Android-free
sealed interface SpriteGridResult {
    data class Valid(val grid: SpriteGrid) : SpriteGridResult
    data class Invalid(val failure: SpriteSheetFailure) : SpriteGridResult
}

data class SpriteGrid(val cellSizePx: Int, val columns: Int) {
    companion object { fun of(widthPx: Int, heightPx: Int, maxDimensionPx: Int): SpriteGridResult }
}

data class SpriteLayout(val grid: SpriteGrid, val frameCount: Int) {
    fun cellLeftPx(frame: Int): Int
}
```

`maxDimensionPx` is injected (2048 today), so the safety bound is a value, not a literal.

## Testing strategy

| Layer | What | How |
|---|---|---|
| Unit (`:core:domain`) | grid derivation, divisibility, oversize, cell arithmetic, frame-count clamping | JUnit4, pure Kotlin, no Robolectric |
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
