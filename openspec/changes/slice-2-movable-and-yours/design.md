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
