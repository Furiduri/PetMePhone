# Design: Slice 2 — A pet you can move, and make yours (#37, #15, #16, #39, #12)

## Technical approach

Same split as slice 1, applied to four new concerns. `:core:domain` owns every decision that is
arithmetic or policy — nearest edge, fraction↔pixel conversion, the slop decision, priority
resolution, dwell time, the render cap, rejection reasons — and is JVM-tested with no Robolectric.
`:feature:overlay` owns `MotionEvent`, `Choreographer`, `WindowManager`, `BitmapFactory`, the Photo
Picker and every composable. `:core:data` owns DataStore keys and the in-memory drag flag, matching
where `OverlayPositionRepositoryImpl` already lives. `PetOverlayService` gains no state: it gains
wiring, and each new piece of plumbing is rebuildable from zero information, as its kdoc requires.

Satisfies `pet-state-resolution`, `overlay-drag`, `overlay-position-persistence`,
`character-import`, `overlay-onboarding-ui`, and the `pet-overlay-rendering` / `pet-sprite-sheet`
deltas.

## Architecture decisions

| # | Decision | Choice | Rejected | Rationale |
|---|---|---|---|---|
| 1 | Snapshot fields | `PetSnapshot(isDragging: Boolean)` — one field, the only one with a source | six fields with four defaulting to `false` | A `false` with no producer is a fabricated present-tense claim. A field arrives with its provider, in the slice that can feed it. Adding one later is a data-class parameter, not a redesign |
| 2 | Dwell operator | Custom `Flow` operator inside `PetStateResolver.states()`: `distinctUntilChanged()` then delay-the-remainder-and-recheck-latest | `debounce` / `sample` | `debounce` drops the *first* change until quiet — the pet would not react to a drag until it stopped. This emits immediately when the dwell window is already clear, and coalesces only a flap |
| 3 | Two position types | `OverlayPosition(x: Int, y: Int)` stays the pixel runtime type; new `OverlayPositionFraction(x: Float, y: Float)` is the only persisted type | one type carrying both; a single `Float` type converted at the window | `LayoutParams` needs pixels and DataStore must never see them. Two types make the boundary a compile error rather than a review comment |
| 4 | Fraction denominator | Fraction of the **travel range** (`bounds - renderSize`), not of raw screen width | fraction of raw screen size | `1.0` then means "flush against the far edge" on every device. With a raw denominator, a fraction stored on a tall device places part of the pet off-screen on a short one — the spec's cross-device scenario fails |
| 5 | Touch host | `View.OnTouchListener` on the `ComposeOverlayHost` instance, attached by the service | Compose `pointerInput` / `detectDragGestures` | The window moves under the finger, so Compose-local coordinates feed back into themselves. `getRawX/Y` is screen-absolute and immune. The per-frame throttle also needs `Choreographer` at view level |
| 6 | Nearest-edge tie-break | Exact centre snaps **right** | left; nearest-previous-edge | The unplaced resting corner is bottom-right, so right is the pet's home side. Arbitrary either way, so it is named and tested rather than left to `<` vs `<=` |
| 7 | Write scope | `@OverlayApplicationScope` (the existing `@Singleton` scope), not `serviceScope` | service scope | A window torn down one tick after the snap must still persist where the user left the pet. Cancellation stays explicit (`job.cancel()` on a new drag), never a lifecycle side effect |
| 8 | Character metadata store | DataStore: `stringSetPreferencesKey("characters")` + `stringPreferencesKey("active_character")` | a Room table | The filesystem is the real store; the id list is capped and has no relations. A migration-bearing schema for a set of strings costs more than it carries |
| 9 | Import staging | Copy to `cacheDir/import/<uuid>.png`, validate, then move to `filesDir/characters/<uuid>/idle.png` on confirm | copy straight into the library folder | Still app-private before any validation, as the spec demands, but a rejected or abandoned import leaves nothing in the library and the OS reclaims the cache |
| 10 | Path contradiction | Imported characters live at `filesDir/characters/<uuid>/idle.png` (folder) | `filesDir/characters/<uuid>.png` (flat, as one `character-import` requirement words it) | The flat wording predates the one-folder-per-character contract; the folder form is what `pet-sprite-sheet` and the loader require, and it is the only form that can hold a second animation |
| 11 | Render cap | `OverlayRenderSize.MAX_RENDER_SIZE_PX = 220` in `:core:domain/overlay/`; `OverlayWindowParams` derives width, height and clamping from it; `PLACEHOLDER_SIZE_PX` deleted | keep the placeholder constant | A guarantee held by a constant named "placeholder" is an accident. Same number, now a named contract with a test |
| 12 | `FLAG_LAYOUT_NO_LIMITS` | Not set; decided inside #15 by procedure (below) | pin either answer now | Only observed clipping can decide it |

### `FLAG_LAYOUT_NO_LIMITS` decision procedure (executed in #15, outcome appended here)

Drag the pet hard into each of the four edges on (a) the emulator with 3-button navigation and
(b) a real HyperOS device with gesture navigation. Set the flag only if either shows the pet
visibly clipped or `updateViewLayout` refuses the requested coordinate. Because the controller
already clamps `x` into `[0, width − renderSize]` and `y` into the usable bounds before every
update, no out-of-bounds coordinate is ever requested, so the expected outcome is "not needed".
If it *is* set, the same PR must add explicit `y` clamping against nav-bar insets, because the flag
disables the system's own clamp. Record the observation and the outcome in this section.

**Outcome (recorded during apply, work unit 2, #15).** The manual emulator/HyperOS-device leg of
this procedure was **not physically executed** in the apply environment — there is no attached
emulator or device in this sandbox. This is recorded honestly rather than assumed; the manual pass
remains an open item for a human running the app on `emulator-5554` or a real device, per the
tasks artifact's own evidence rule.

`FLAG_LAYOUT_NO_LIMITS` is **not set**, consistent with the design's structural expectation:
`PetTouchController.snap()` clamps `x` into `[0, screenWidthPx − renderSizePx]` via
`nearestEdge`'s two edge targets (never a value outside that range), and clamps `y` into
`[0, screenHeightPx − navigationBarInsetBottomPx() − renderSizePx]` before every
`updateViewLayout` call during the snap. `OverlayWindowParams.clampToBounds` (rotation path)
independently re-clamps both axes against the current bounds. No code path in this PR ever
requests an out-of-bounds coordinate, so no visible clipping is structurally possible from this
controller's own writes — the remaining unknown is only whether the OEM's window manager
(HyperOS specifically) refuses or misplaces an in-bounds `updateViewLayout` call for its own
reasons, which only the manual device pass can observe. Because the flag is not set, the explicit
`y`-clamp obligation this section's own procedure would trigger does not apply; the `y` clamp
described above exists anyway, as an ordinary bounds guard, and is exercised by
`PetTouchControllerTest`'s vertical-preservation and snap-direction cases.

**Outcome confirmed on hardware (device pass, maintainer).** The open leg above has now been run on
a Redmi Note 14 Pro 5G — Android 16 / API 36, HyperOS 3.0, gesture navigation. Dragging the pet
hard into all four edges showed **no visible clipping and no refused coordinate**. The remaining
unknown this section named — whether HyperOS's window manager mishandles an in-bounds
`updateViewLayout` — is answered: it does not. `FLAG_LAYOUT_NO_LIMITS` **stays unset**, and the
conditional `y`-clamp obligation remains inapplicable.

Supporting measurement from `adb shell dumpsys window windows`: the overlay's parent frame is
`[0,130][1220,2660]`, so the system itself already excludes the status bar (130px) and the gesture
bar (52px) from the window's usable area. A stored x-fraction of `1.0` resolves to `x = 1000`,
exactly `1220 − 220`, flush against that usable right edge. Nothing is clipped because nothing is
ever asked to draw beneath the bars.

Not executed: leg (a), the emulator with 3-button navigation. Closed on the real-device leg alone
by maintainer decision — gesture navigation on an OEM skin is the stricter environment for this
particular question, since it is the one with a non-stock window manager.

## Data flow

```
[touch]  ComposeOverlayHost ──OnTouchListener──► PetTouchController        [:feature:overlay]
             DOWN: record rawX/Y, params.x/y
             MOVE: exceedsSlop(dx,dy,scaledTouchSlop)?  ──► DragStateRepository.isDragging = true
                   pendingX/Y = …; if (!frameScheduled) Choreographer.postFrameCallback
             FRAME: updateViewLayout(pending)   ── at most one per rendered frame
             UP  : slop never exceeded ──► onTap(OverlayAnchor)   [no move, no snap]
                   else ──► nearestEdge(x, bounds, renderSize)         [:core:domain]
                            Animatable + spring() → params.x only; y frozen at release
                            settle ──► isDragging = false ──► PositionWriter.writeAtRest(fraction)

[state]  DragStateRepository.isDragging ──► PetSnapshot ──► PetStateResolver  [:core:domain]
             providers sorted desc: Dragging(100) → Idle(0)
             distinctUntilChanged + minimum dwell ──► Flow<PetState>

[assets] ActiveCharacterRepository.active: Flow<CharacterId>       [:core:data]
             mapLatest ──► CharacterSheetLoader(CharacterAssetSource)  [:feature:overlay]
                             BuiltIn(name)   → assets/pet/<name>/
                             Imported(uuid)  → filesDir/characters/<uuid>/
             ──► StateFlow<CharacterSheets>  (Loading | Ready(map, idle) | Broken(failure))

[draw]   PetOverlay(holder): sheets.forState(state) ?: sheets.idle ?: BrokenPlaceholder
                             + identity affordance, drawn after the sprite

[start]  onStartCommand ──► withTimeoutOrNull(config.firstReadTimeoutMillis) { position.first() }
             ──► toPixels(bounds) ?: restingCorner() ──► addView   ──► collect drop(1)
```

## Interfaces

```kotlin
// :core:domain/pet/state/
enum class PetState { IDLE, DRAGGING, TYPING, HAPPY, SLEEPING, HUNGRY }  // names map to filenames
data class PetSnapshot(val isDragging: Boolean)                          // decision 1
interface PetStateProvider { val priority: Int; fun evaluate(snapshot: PetSnapshot): PetState? }

class PetStateResolver(providers: Set<PetStateProvider>, private val config: PetStateConfig) {
    init { require(providers.distinctBy { it.priority }.size == providers.size) { … } }  // fail fast
    fun resolve(snapshot: PetSnapshot): PetState        // first non-null, desc priority, else IDLE
    fun states(snapshots: Flow<PetSnapshot>): Flow<PetState>   // distinct + config.minimumDwell
}

// :core:domain/overlay/
enum class ScreenEdge { LEFT, RIGHT }
fun nearestEdge(xPx: Int, screenWidthPx: Int, renderSizePx: Int): ScreenEdge      // centre → RIGHT
fun exceedsSlop(dxPx: Float, dyPx: Float, slopPx: Int): Boolean
object OverlayRenderSize { const val MAX_RENDER_SIZE_PX = 220 }

data class OverlayPositionFraction(val x: Float, val y: Float) {   // 0f..1f of the travel range
    fun toPixels(widthPx: Int, heightPx: Int, renderSizePx: Int): OverlayPosition
    companion object {
        fun ofPixels(p: OverlayPosition, widthPx: Int, heightPx: Int, renderSizePx: Int): OverlayPositionFraction
        fun validOrNull(x: Float?, y: Float?): OverlayPositionFraction?   // NaN / out-of-range → null
    }
}

interface OverlayPositionRepository {
    val position: Flow<OverlayPositionFraction?>          // null = never placed, never 0f
    suspend fun save(position: OverlayPositionFraction)
}
interface DragStateRepository { val isDragging: StateFlow<Boolean>; fun set(dragging: Boolean) }

// :core:domain/character/
sealed interface CharacterId { data class BuiltIn(val name: String); data class Imported(val uuid: String) }
sealed interface CharacterImportRejection {          // every case carries its measured values
    data object NotPng; data class TooLarge(val actualBytes: Long, val maxBytes: Long)
    data class Oversized(val widthPx: Int, val heightPx: Int, val maxPx: Int)
    data class NotDivisible(val widthPx: Int, val heightPx: Int)
    data object Undecodable; data object EmptySheet; data class CapReached(val cap: Int)
}

// :feature:overlay/character/
fun interface CharacterAssetSource { fun open(animationFileName: String): InputStream? }  // null = absent
sealed interface CharacterSheets {
    data object Loading
    data class Ready(val byState: Map<PetState, SpriteSheetResult.Loaded>, val idle: SpriteSheetResult.Loaded)
    data class Broken(val failure: SpriteSheetFailure)
}
```

Injected config, never literals: `PetStateConfig(minimumDwellMillis)`,
`OverlayPositionConfig(firstReadTimeoutMillis)`, `CharacterLibraryConfig(maxImportedCharacters,
maxImportBytes)` — all provided from `OverlayModule` alongside the existing
`MAX_SPRITE_DIMENSION_PX` and `IDLE_FRAME_INTERVAL_MILLIS`.

## The fraction migration

Slice 1's `intPreferencesKey("overlay_position_x"/"_y")` are **read by nothing after this change and
written by nothing before it** — no drag existed, so no device in the world holds a value under
those keys. The treatment is therefore explicit non-migration: the new
`floatPreferencesKey("overlay_position_x_fraction"/"_y_fraction")` keys are a fresh namespace, the
int keys are never read, and the first successful `save()` calls `remove()` on both inside the same
`edit` block so no orphan survives a real usage.

`0f` is unreachable on the read path by construction: `validOrNull` returns `null` unless *both*
values are present, finite, and inside `0f..1f`; the caller's only fallback is `restingCorner()`
against live bounds. A legitimately stored `0f` (pet dragged flush left) is distinguishable from
absence because the key exists — which is exactly why the check is key-presence, never
`?: 0f`.

## Startup ordering and write-at-rest

`addOverlayWindow` is preceded by a single suspending read:

```kotlin
val stored = withTimeoutOrNull(positionConfig.firstReadTimeoutMillis) { positionRepository.position.first() }
addOverlayWindow(stored?.toPixels(w, h, MAX_RENDER_SIZE_PX) ?: restingCorner())
positionCollectionJob = serviceScope.launch { positionRepository.position.drop(1).collect { … } }
```

A timeout and a stored `null` collapse to the same branch — the computed corner — so the ambiguity
is harmless and no fabricated coordinate exists on either path. `drop(1)` prevents the awaited
emission from being re-applied as a second `updateViewLayout`.

`PositionWriter` (`@Singleton`, `@OverlayApplicationScope`) holds one nullable `Job`. Drag start
calls `cancelPending()`; snap settle calls `writeAtRest(fraction)`, which cancels any prior job and
launches a new one. One `DataStore.edit` per completed gesture, and a gesture that is superseded
mid-write never lands.

## `PetOverlayStateHolder` rework

The holder stops decoding at construction and becomes a projection of the active character:

```kotlin
val sheets: StateFlow<CharacterSheets> = activeCharacterRepository.active
    .mapLatest { id -> withContext(io) { sheetLoader.load(id) } }   // mapLatest: a fast switch cancels the stale decode
    .stateIn(scope, SharingStarted.WhileSubscribed(5_000), CharacterSheets.Loading)
```

`CharacterSheetLoader` picks the source from the id (assets vs. `filesDir`), decodes each known
animation filename through the **existing** `SpriteSheetDecoder`, treats a missing optional file as
an ordinary absence (simply not in the map), and returns `Broken` only when `idle.png` itself is
absent or fails — which is also the "file deleted outside the app" case the spec requires to render
visibly broken.

**What the user sees during a switch.** `PetOverlay` remembers the last `Ready` value and keeps
drawing it while the new one is `Loading`; only `Ready` or `Broken` replaces it. Without this the
pet blanks or flashes the red cross for the duration of a decode. `frameIndex` is reset by keying
the animation `remember` on the `Ready` identity, because frame counts differ between characters
and a stale index would index past the new sheet's row.

## Import pipeline

Photo Picker (`PickVisualMedia`, no storage permission) → copy to `cacheDir/import/<uuid>.png` →
three tiers, stopping at the first failure:

| Tier | Check | Reuses | Allocates pixels |
|---|---|---|---|
| 1 | 8-byte PNG signature; `maxImportBytes` ceiling | — | No |
| 2 | `BitmapDecoding.decodeBounds` + `SpriteGrid.of` | existing, unchanged | No |
| 3 | `decodeFull` + `TransparentCellScanner` | existing, unchanged | Yes |

To avoid a second copy of tiers 2–3, `SpriteSheetDecoder` gains `fun validateBounds(bytes):
SpriteGridResult` and its existing `decode` calls it — one implementation, two entry points. Tier 3
runs behind a visible loading state. On success the preview animates the detected row using the
same `SpriteLayout` arithmetic the renderer uses and names the state the file maps to (IDLE, under
one-file-per-animation); the move into `filesDir` happens only on confirm. A rejection produces a
`CharacterImportRejection` whose string resource formats the measured values, and a unit test greps
the string resources for a generic "invalid image" to prove none exists.

The identity affordance is drawn by `PetOverlay` *after* `drawImage`, in the same `DrawScope`, so no
imported pixel can paint over it.

## File changes

| Path | Action | Purpose |
|---|---|---|
| `core/domain/.../pet/state/{PetState,PetSnapshot,PetStateProvider,PetStateResolver,PetStateConfig}.kt` | Create | #37 |
| `core/domain/.../pet/state/{DraggingStateProvider,IdleStateProvider}.kt` | Create | The only two providers with a real input |
| `core/domain/.../overlay/OverlayDrag.kt` | Create | `ScreenEdge`, `nearestEdge`, `exceedsSlop` |
| `core/domain/.../overlay/OverlayRenderSize.kt` | Create | The named cap (decision 11) |
| `core/domain/.../overlay/OverlayPositionFraction.kt` | Create | Persisted type + conversions |
| `core/domain/.../overlay/{OverlayPosition,OverlayPositionRepository}.kt` | Modify | `save()`; fraction-typed flow |
| `core/domain/.../overlay/DragStateRepository.kt` | Create | In-memory `StateFlow` contract |
| `core/domain/.../character/{CharacterId,Character,CharacterImportRejection,CharacterRepository,ActiveCharacterRepository,CharacterLibraryConfig}.kt` | Create | #39 domain |
| `core/data/.../overlay/OverlayPositionRepositoryImpl.kt` | Modify | Float keys, range validation, legacy `remove` |
| `core/data/.../overlay/DragStateRepositoryImpl.kt` | Create | `@Singleton MutableStateFlow` |
| `core/data/.../character/{CharacterRepositoryImpl,ActiveCharacterRepositoryImpl}.kt` | Create | DataStore id set + active pointer |
| `core/data/di/BindingsModule.kt` | Modify | New `@Binds` |
| `feature/overlay/.../input/{PetTouchController,OverlayAnchor,OverlayTapListener}.kt` | Create | #15 |
| `feature/overlay/.../position/PositionWriter.kt` | Create | Write-at-rest, cancellable |
| `feature/overlay/.../service/OverlayWindowParams.kt` | Modify | Derive size from the cap; delete `PLACEHOLDER_SIZE_PX` |
| `feature/overlay/.../service/PetOverlayService.kt` | Modify | Await-first-read, attach controller, cancel on destroy |
| `feature/overlay/.../ui/{PetOverlayStateHolder,PetOverlay}.kt` | Modify | Reactive sheets; state-driven animation; identity affordance |
| `feature/overlay/.../character/{CharacterAssetSource,CharacterSheetLoader,CharacterSheets,CharacterImporter}.kt` | Create | #39 pipeline |
| `feature/overlay/.../character/ui/{ImportScreen,PreviewScreen,LibraryScreen}.kt` | Create | #39 UI |
| `feature/overlay/.../onboarding/{OverlayOnboardingScreen,OverlayOnboardingViewModel,ReEntryCard}.kt` | Create | #12 |
| `feature/overlay/.../sprite/SpriteSheetDecoder.kt` | Modify | Expose `validateBounds` |
| `feature/overlay/.../di/OverlayModule.kt` | Modify | Three new config objects; provider `@IntoSet` bindings |

Screens outside the overlay window use ordinary `hiltViewModel()`; the no-`ViewModel` rule is
scoped to `ComposeOverlayHost`'s tree, which has no `ViewModelStoreOwner`.

## Testing strategy

| Layer | What | How |
|---|---|---|
| Unit `:core:domain` | `nearestEdge` table incl. the exact-centre tie; fraction round-trip within tolerance; `exceedsSlop`; resolver order-independence, IDLE fallback, duplicate-priority throw at construction; dwell coalescing | JUnit4, pure Kotlin; dwell on `runTest` virtual time |
| Unit `:core:data` | float keys only, no int position key exists; missing → `null`; NaN/out-of-range → `null`; never `0f`; one `edit` per gesture | `runTest` + temp-file DataStore |
| Unit `:feature:overlay` (Robolectric) | Sub-slop touch leaves params untouched and fires `onTap` once; past-slop follows the finger; `updateViewLayout` count ≤ frames via a fake `Choreographer` seam; destroy mid-drag cancels callback and animation; params derive from the cap and keep `FLAG_NOT_FOCUSABLE`; import tiers over byte fixtures (oversized never full-decodes); loader treats a missing optional file as absence and a missing `idle.png` as `Broken`; switching keeps the previous frame until `Ready` | Robolectric + `MotionEvent.obtain`, fake sources, injected seams |
| Unit | No generic "invalid image" string exists | Resource-scan test |
| Instrumented | Onboarding and preview screens | `createComposeRule` — see the gap below |
| Manual, recorded in the PR | Real-device drag on HyperOS (OEM `ACTION_MOVE` delivery); kill/restart with no jump; rotation; live re-render after a switch with the service running; TalkBack pass over onboarding | Device + OS version stated in the PR body |

**Outcome (recorded during apply, work unit 3, #16).** The manual acceptance pass (kill/restart with
no jump; rotation preserves relative position) was **not physically executed** in the apply
environment — there is no attached emulator or device in this sandbox (`emulator`/`adb` both absent
from `PATH`, same constraint as work unit 2). This is recorded honestly rather than assumed. The
fraction-persistence, write-at-rest, and await-first-read logic is fully covered by automated
evidence instead (`OverlayPositionFractionTest`, `OverlayPositionRepositoryImplTest`,
`PositionWriterTest`, `PetOverlayServiceStartupTest`, `PetTouchControllerTest`'s new write-at-rest
case — all green), but the end-to-end observation this task requires (a real service kill/restart
and a real device rotation, both visually confirming no jump) remains an open item for a human
running the app on `emulator-5554` or a real device.

**The API 37 gap.** Slice 1's instrumented tests fail on the API 37 image with
`NoSuchMethodException: android.hardware.input.InputManager.getInstance` — a toolchain defect, not a
code defect. No slice-2 acceptance criterion may depend on a passing instrumented test. Drag gets
its automated evidence from Robolectric, which builds events with `MotionEvent.obtain` and never
touches `InputManager`; import gets its evidence from JVM and Robolectric tests over byte fixtures.
Each affected PR additionally attempts the instrumented suite once against an API 34 image and
records the result here, so the gap is measured rather than assumed permanent. The real-hardware
manual pass is the primary evidence for touch delivery either way, as the proposal's risk table
already states.

## Threat matrix

N/A — no routing, shell, subprocess, VCS/PR automation, executable-file classification, or
process-integration boundary. The two adjacent hazards are handled in-design: untrusted image bytes
by the header-first tiering plus the injected size ceiling, and untrusted pixels on screen by the
named render cap, the non-focusable window, and the always-drawn identity affordance. Per
`character-import`, no copy claims deception is prevented.

## Migration / rollout

No schema migration. The position keys are a new namespace with an explicit orphan cleanup
(above). Imported files under `filesDir/characters/` survive a revert unread, and the active
pointer falls back to the built-in. Revert order is newest-first; #16 must be reverted before #15,
because #15 alone leaves #16 with no writer.

## PR boundaries

| PR | Content | Depends on | Est. changed lines |
|---|---|---|---|
| 1 | #37 — state types, resolver, dwell, DRAGGING + IDLE providers, `DragStateRepository` | tracker branch | ~260 |
| 2 | #15 — touch controller, throttle, horizontal snap, `onTap`, cap constant, `OverlayWindowParams` | 1 | ~380 |
| 3 | #16 — fraction type and keys, `PositionWriter`, await-first-read | 2 | ~240 |
| 4 | #39a — importer, three tiers, rejections, preview | 3 | ~520 |
| 5 | #39b — library, cap, delete | 4 | ~340 |
| 6 | #39c — active-character switching + the reactive state-holder rework | 5 | ~420 |
| 7 | #12 — onboarding screen, copy, re-entry card | 6 (ordering only) | ~300 |

Every slice is under the 800-line budget on its own; the chain is not. PR 1 targets the tracker
branch, each later PR targets its predecessor (`feature-branch-chain`).

## Open questions

- [ ] `FLAG_LAYOUT_NO_LIMITS` — resolved by the procedure above during PR 2; outcome appended here
      before PR 3 starts.
- [ ] Whether an API 34 emulator image clears the `InputManager.getInstance` failure. Measured once
      per affected PR; recorded, not assumed.
- [ ] Play UGC classification for image import — flagged by the proposal, confirmed in the Play
      Console before any build containing PR 4 ships. Not a code decision.

---

## Decisions 13–15 (maintainer, mid-slice)

Recorded here and written back to the affected issues, per `docs/build-order.md`'s rule that a
mid-slice decision contradicting an issue goes back to that issue.

### 13. A sprite sheet may have more than one row, and the grid is declared, not inferred

The one-row contract from #68 made the format self-describing: `cellSizePx = heightPx`,
`columns = widthPx / heightPx`, no metadata anywhere. That property does not survive contact with
real artwork.

Two concrete failures on the maintainer's reference sheet, a 6x6 grid of 36 frames:

- **It passes validation and renders wrongly.** A square sheet satisfies `widthPx % heightPx == 0`,
  so `SpriteGrid.of` derives `columns = 1` and a cell the size of the whole image. Nothing is
  rejected; a single frame containing the entire grid is drawn. A silent wrong render is worse than
  a rejection.
- **The same frames cannot be expressed in one row at all.** 36 frames of a 250px cell is a 9000px
  strip, far past the 2048 cap. The one-row rule caps any animation at roughly 8 frames.

A multi-row grid cannot be inferred: 1500x1500 is equally consistent with 6x6, 5x5 and 10x10 cells.
One number has to come from outside the image. It is therefore **declared**:

- **Imported characters** declare it in the import preview step, which already exists. The screen
  proposes a detected grid and the user confirms or corrects it.
- **Bundled characters** declare it in a small per-character manifest in their folder.

Filename conventions (`idle_6x6.png`) were rejected: they put load-bearing data in a string no
validation owns, and they are user-hostile in an import flow aimed at people who draw rather than
name files carefully.

### 14. The import step captures the character's name

`Character.displayName` existed with nothing to fill it for imported characters, so the library
showed every one of them as "Imported character". Importing your own drawing and having the app
refuse to call it anything is the wrong outcome for what `docs/build-order.md` calls the project's
highest-motivation feature. The name is captured on the same preview screen as the grid: one step,
one confirmation.

### 15. Single tap cycles designs; a sustained press opens the quick menu

Single tap now cycles between alternative designs for the pet's **current state**, when more than
one exists. The quick menu moves to a sustained press.

The hold duration is injected configuration, initial value 3000 ms at the maintainer's direction.
It is recorded here that 3000 ms is far above the platform norm (Android's own long press is
roughly 400–500 ms) and risks reading as an unresponsive control, which is why the value is
injected rather than fixed: it is expected to be tuned against a real thumb. A sustained press also
needs a visible progress cue, or the wait is blind.

Constraint on the timer: it is cancelled the moment the gesture exceeds touch slop, so
press-and-drag remains a drag and never opens the menu.

**This contradicts #17 and #27**, which both specify that tapping the pet opens the quick menu.
Written back to both.

## Bundled test characters (`default`, `default2`)

`src/main/assets/pet/` carries two real, artist-exported characters, used as fixtures by
`BundledCharacterSheetsTest`. Every other sheet test uses generated pixels, which are only ever as
correct as the generator; these are the actual files, so a format change that passes on synthetic
data and fails on real artwork is caught.

| Character | Sheet | Pixels | Declared grid | Frames |
|---|---|---|---|---|
| `default` | `idle.png` | 2046x341 | 6 x 1 | 6 |
| `default2` | `idle.png` | 2046x682 | 6 x 2 | 12 |

`default2` is the two-row case that motivated #69 and is the intended fixture for the
active-character switching work: a second real character, not a variant of the first.

### Two limits this pair exposed

**A character-level manifest cannot describe per-file grids.** `manifest.properties` declares one
`columns`/`rows` pair for the whole folder, but `default2` also holds `idle-2.png` at 9 x 1. One
declaration cannot cover two different grids. The manifest has to become per-file, which is the
same shape the animation-binding work needs anyway.

**The 2048 bound makes long single-row strips unusable.** `idle-2.png` is 3069 px wide — 9 frames
of a 341 px cell — and is rejected as `Oversized` before any decode. The same 9 frames as a 3 x 3
grid are 1023 x 1023 and fit comfortably. This is the concrete argument for multi-row support:
rows are how a long animation stays inside the memory bound, not a convenience.

## Decision 16 — sprite bindings are data, and tap browses rather than time rotating

Full specification in #70. Recorded here because it changes what #37's resolver feeds.

The file-to-action link stops being a filename convention and becomes an editable binding: one
sheet, the action it plays for, and its own declared grid. An action with several bindings has
several designs.

**#37's priority resolution is kept.** It still selects the dominant state, and that is what the
pet shows unprompted. Tap adds a manual browse across everything currently applicable — the current
state's other variants, and the animations of other simultaneously valid states.

**Nothing rotates on a timer.** The display advances on its own in exactly two cases: a new state
becomes dominant, or the displayed state stops applying. Either one discards a manual selection and
returns to the dominant state. #37's minimum dwell continues to govern those automatic transitions
and has no bearing on tap.

The maintainer chose this over interleaving simultaneous states automatically. The reason is
legibility: a 220 px overlay is read in peripheral vision while the user is doing something else,
and a pet that alternates between hunger and exhaustion on its own stops communicating either. The
information is still reachable — the user asks for it with a tap instead of being shown it in
rotation.

Bindings live in the per-file manifest for bundled characters and in DataStore for imported ones.
DataStore is explicitly interim: this is relational data, and it migrates to Room in slice 3 as
part of #23 rather than as a later discovery.
