# Apply Progress: Slice 2 — A pet you can move, and make yours

## Work Unit 1 — State types, resolver, dwell, DRAGGING + IDLE providers (PR 1, #37)

**Mode**: Standard (strict TDD not active for this project).

### Completed Tasks
- [x] 1. Create `PetState.kt`
- [x] 2. Create `PetSnapshot.kt`
- [x] 3. Create `PetStateProvider.kt`
- [x] 4. Create `PetStateConfig.kt`
- [x] 5. Create `PetStateResolver.kt`: construction fail-fast and `resolve()`
- [x] 6. Extend `PetStateResolver` with `states(): Flow<PetState>` and the dwell operator
- [x] 7. Create `DraggingStateProvider.kt`
- [x] 8. Create `IdleStateProvider.kt`
- [x] 9. Create `DragStateRepository.kt` (interface only, this PR)
- [x] 10. Unit test: resolver order-independence and IDLE fallback
- [x] 11. Unit test: fail-fast duplicate priority and full registered-set distinctness
- [x] 12. Unit test (`runTest` virtual time): dwell coalesces a flapping snapshot
- [x] 13. Bind `DraggingStateProvider` and `IdleStateProvider` `@IntoSet` in `OverlayModule.kt`
- [x] 14. Full PR 1 build check

### Files Changed

| File | Action | What Was Done |
|------|--------|---------------|
| `core/domain/.../pet/state/PetState.kt` | Created | Six-entry enum, `[STATE-3]`/`[RENDER-1]` |
| `core/domain/.../pet/state/PetSnapshot.kt` | Created | `data class PetSnapshot(val isDragging: Boolean)` only — no fabricated fields, `[STATE-1]` |
| `core/domain/.../pet/state/PetStateProvider.kt` | Created | `priority: Int` + pure `evaluate(snapshot): PetState?`, `[STATE-2]` |
| `core/domain/.../pet/state/PetStateConfig.kt` | Created | Injected `minimumDwellMillis: Long`, `[STATE-6]` |
| `core/domain/.../pet/state/PetStateResolver.kt` | Created | `resolve()` (fail-fast `init` on duplicate priority, descending-priority first-match, IDLE fallback) + `states(Flow<PetSnapshot>): Flow<PetState>` using a private `withMinimumDwell` operator built from `distinctUntilChanged()` + a conflated `Channel` + `delay()` loop (never `debounce`/`sample`), `[STATE-3]` `[STATE-4]` `[STATE-6]` `[STATE-7]` |
| `core/domain/.../pet/state/DraggingStateProvider.kt` | Created | Priority 100, returns `DRAGGING` when `snapshot.isDragging`, else `null`; reads only its snapshot argument, `[STATE-5]` |
| `core/domain/.../pet/state/IdleStateProvider.kt` | Created | Priority 0, always `null` — extension-point exemplar, `[STATE-3]` |
| `core/domain/.../overlay/DragStateRepository.kt` | Created | Interface only (`isDragging: StateFlow<Boolean>`, `set(dragging)`); implementation deferred to PR 2, `[DRAG-6]` |
| `core/domain/src/test/.../pet/state/PetStateResolverTest.kt` | Created | 5 tests: scrambled-order equivalence, IDLE fallback, DRAGGING overrides IDLE regardless of order, duplicate-priority throws at construction, full registered set has distinct priorities |
| `core/domain/src/test/.../pet/state/PetStateResolverFlowTest.kt` | Created | 2 tests (Turbine + `runTest` virtual time): dwell coalesces a flapping snapshot into emissions ≥ `minimumDwellMillis` apart; "screen off" has no representation in the snapshot at all, so resolution keeps emitting — `[STATE-7]` is satisfied structurally (no screen-state field/gating exists anywhere in this class) |
| `feature/overlay/.../di/OverlayModule.kt` | Modified | Added `providePetStateConfig()` (injected `PET_STATE_MINIMUM_DWELL_MILLIS = 400L`, never a literal inside the resolver itself), `providePetStateResolver(providers, config)`, and two `@IntoSet` provider bindings (`DraggingStateProvider`, `IdleStateProvider`) |

### Deviations from Design
None — implementation matches `design.md` decisions 1, 2, and the `pet-state-resolution` spec. The dwell operator is a custom `Channel(CONFLATED)` + `delay()` loop rather than `debounce`/`sample`, exactly as decision 2 requires: it emits immediately when the dwell window is clear and only coalesces values that arrive inside the window, re-checking the latest conflated value once the window elapses.

### Issues Found
None. `PetStateConfig`'s `minimumDwellMillis` value (400ms) is an implementation choice for this slice — not specified numerically in spec/design, only that it must be injected configuration. It is provided from `OverlayModule`, not hardcoded in `PetStateResolver`.

## Work Unit Evidence

| Evidence | Value |
|---|---|
| Focused test command and exact result | `./gradlew :core:domain:test --tests "*PetStateResolverTest*" --tests "*PetStateResolverFlowTest*"` → BUILD SUCCESSFUL. `TEST-*PetStateResolverTest*.xml`: `tests="5" failures="0" errors="0"`. `TEST-*PetStateResolverFlowTest*.xml`: `tests="2" failures="0" errors="0"` |
| Full unit `core:domain:test` | `./gradlew :core:domain:test` → BUILD SUCCESSFUL (all existing + new tests, 7 new test cases confirmed via XML above) |
| DI wiring compile check | `./gradlew :feature:overlay:compileDebugKotlin` → BUILD SUCCESSFUL, including `:feature:overlay:kspDebugKotlin` (Hilt annotation processing resolves `PetStateResolver` and the `@IntoSet` provider bindings with no manual construction) |
| Runtime harness command/scenario and exact result | N/A — pure JVM/domain logic and a Dagger/Hilt compile-time graph check; no Android runtime boundary crossed in this work unit, per the tasks artifact's own harness note for Unit 1 |
| Rollback boundary | Revert `core/domain/.../pet/state/` (7 files) and `core/domain/.../overlay/DragStateRepository.kt`, plus the `OverlayModule.kt` additions (config provider, resolver provider, two `@IntoSet` bindings). No other unit's code exists yet, so nothing depends on this being present at runtime |

### Status
14/14 tasks in Work Unit 1 (PR 1) complete.

## Work Unit 2 — Drag and horizontal edge snap (PR 2, #15)

**Mode**: Standard (strict TDD not active for this project).

### Completed Tasks
- [x] 15. Create `OverlayDrag.kt`: `ScreenEdge`, `nearestEdge`, `exceedsSlop`
- [x] 16. Create `OverlayRenderSize.kt`
- [x] 17. Unit test: `nearestEdge` table including the exact-centre tie-break
- [x] 18. Unit test: `exceedsSlop` boundary cases
- [x] 19. Create `DragStateRepositoryImpl.kt`
- [x] 20. Bind `DragStateRepositoryImpl` in `core/data/di/BindingsModule.kt`
- [x] 21. Unit test: DRAGGING reports true only between drag start and drag end
- [x] 22. Modify `OverlayWindowParams.kt`: derive size from `OverlayRenderSize.MAX_RENDER_SIZE_PX`; delete `PLACEHOLDER_SIZE_PX`
- [x] 23. Create `OverlayAnchor.kt` and `OverlayTapListener.kt`
- [x] 24. Create `PetTouchController.kt`: DOWN/MOVE/UP state machine
- [x] 25. Extend `PetTouchController`: snap animation on release after a drag
- [x] 26. Extend `PetTouchController`: cancellation on service destroy mid-drag
- [x] 27. `FLAG_LAYOUT_NO_LIMITS` decision procedure (recorded, see Deviations/Issues below)
- [x] 28. Wire `PetOverlayService.kt`: attach `PetTouchController`, cancel on destroy
- [x] 29. Unit test (Robolectric): sub-slop, past-slop, throttle, cancellation
- [x] 30. Unit test (Robolectric): snap direction and vertical-preservation, params derive from the cap
- [x] 31. Attempt the instrumented suite once against an API 34 image; record the result (recorded — see Issues below)
- [x] 32. Full PR 2 build check

### Files Changed

| File | Action | What Was Done |
|------|--------|---------------|
| `core/domain/.../overlay/OverlayDrag.kt` | Created | `ScreenEdge` (`LEFT`/`RIGHT`), `nearestEdge(xPx, screenWidthPx, renderSizePx)` (centre-vs-centre comparison, exact tie → `RIGHT`), `exceedsSlop(dxPx, dyPx, slopPx)` (Euclidean distance, strictly `>`, never `>=`), `[DRAG-9]` `[DRAG-2]` |
| `core/domain/.../overlay/OverlayRenderSize.kt` | Created | `object OverlayRenderSize { const val MAX_RENDER_SIZE_PX = 220 }` — the named cap replacing the placeholder constant, `[IMPORT-10]` |
| `core/domain/src/test/.../overlay/OverlayDragTest.kt` | Created | 4 tests: left/right resolution, exact-centre tie-break, a position table across the screen width |
| `core/domain/src/test/.../overlay/ExceedsSlopTest.kt` (same file, second class) | Created | 4 tests: below slop, exactly at slop (must NOT trigger), past slop, diagonal movement past slop |
| `core/data/.../overlay/DragStateRepositoryImpl.kt` | Created | `@Singleton` `MutableStateFlow<Boolean>`-backed `DragStateRepository`; `set()` only ever touches the in-memory flow, `[DRAG-6]` |
| `core/data/di/BindingsModule.kt` | Modified | Added `@Binds bindDragStateRepository(DragStateRepositoryImpl): DragStateRepository` |
| `core/data/src/test/.../overlay/DragStateRepositoryImplTest.kt` | Created | 3 tests: initial `false`, true-only-between-start-and-end, structural proof the no-arg constructor takes no persistence dependency |
| `feature/overlay/.../service/OverlayWindowParams.kt` | Modified | `PLACEHOLDER_SIZE_PX` deleted; `SIZE_PX` now derives from `OverlayRenderSize.MAX_RENDER_SIZE_PX`; `create()`/`clampToBounds()` use `SIZE_PX`, `[IMPORT-10]` |
| `feature/overlay/.../service/PetOverlayService.kt` | Modified | Injects `DragStateRepository`, `FrameScheduler`, `SnapAnimator`; `addOverlayWindow` now constructs `PetTouchController` and attaches it as the view's sole `OnTouchListener`; `onDestroy` calls `touchController.cancel()` before tearing down `serviceScope`; added `navigationBarInsetBottomPx()`; `onPetTapped(anchor)` is the seam slice 3's quick menu will replace |
| `feature/overlay/src/test/.../service/OverlayWindowParamsTest.kt` | Modified | `PLACEHOLDER_SIZE_PX` references updated to `SIZE_PX` |
| `feature/overlay/.../input/OverlayAnchor.kt` | Created | `data class OverlayAnchor(xPx, yPx, sizePx)` — shaped for slice 3's quick menu, not built here, `[DRAG-3]` |
| `feature/overlay/.../input/OverlayTapListener.kt` | Created | `fun interface OverlayTapListener { fun onTap(anchor: OverlayAnchor) }` |
| `feature/overlay/.../input/FrameScheduler.kt` | Created | Seam interface around `Choreographer`, so the per-frame throttle is fake-able under Robolectric |
| `feature/overlay/.../input/ChoreographerFrameScheduler.kt` | Created | Real `FrameScheduler`, wraps `Choreographer.getInstance()` with a callback-identity map so `removeFrameCallback` can target the right pending callback |
| `feature/overlay/.../input/SnapAnimator.kt` | Created | `SnapAnimator` seam + real `SpringSnapAnimator` (`Animatable` + `spring()`) — extracted so the snap direction/vertical-preservation test doesn't need a real `MonotonicFrameClock` |
| `feature/overlay/.../input/PetTouchController.kt` | Created | `View.OnTouchListener` DOWN/MOVE/UP state machine: DOWN records `rawX/Y` + current `params.x/y`; MOVE calls `exceedsSlop` against `ViewConfiguration.get(context).scaledTouchSlop` (never a literal), sets `DragStateRepository.set(true)` once past slop, schedules at most one frame callback per pending update; the frame callback is the only call site for `updateViewLayout` during a drag; UP invokes `onTap` when slop was never exceeded, else calls `snap()`; `snap()` computes `nearestEdge`, clamps `y` into `[0, height − navInsetBottom − renderSize]` before animating, animates only `params.x` via the injected `SnapAnimator`, then `DragStateRepository.set(false)` and `onSettled(x, y)`; `cancel()` removes the pending frame callback and cancels the snap job, `[DRAG-1]` `[DRAG-2]` `[DRAG-3]` `[DRAG-4]` `[DRAG-5]` `[DRAG-7]` `[DRAG-8]` |
| `feature/overlay/.../di/OverlayModule.kt` | Modified | Added `OverlayInputBindingsModule` (`@Binds FrameScheduler`, `@Binds SnapAnimator`) |
| `feature/overlay/src/test/.../input/PetTouchControllerTest.kt` | Created | 7 Robolectric tests (sdk 36, matching the project's existing API-37-gap workaround): sub-slop leaves params untouched + fires `onTap` once; past-slop follows the finger only once the scheduled frame runs; rapid `ACTION_MOVE` schedules at most one frame callback; `cancel()` removes the pending callback and no-ops a post-cancel frame run; snap-left and snap-right direction; vertical coordinate at settle equals the release coordinate |

### Deviations from Design

1. **`SnapAnimator` seam not named in `design.md`'s interfaces list.** The design specifies `Animatable + spring()` directly inside `PetTouchController.snap()`. I extracted that into a `SnapAnimator` interface (real impl `SpringSnapAnimator`, still `Animatable` + `spring()`, same behavior) purely for testability: `Animatable.animateTo` requires a `MonotonicFrameClock` in the coroutine context, which is not trivially available in a plain Robolectric unit test outside a Compose test rule. The seam lets `PetTouchControllerTest` assert snap direction and vertical-preservation deterministically without a real frame clock. Production behavior (spring, never a jump) is unchanged.
2. **`FrameScheduler` seam not explicitly named in `design.md`'s interfaces list**, though the design explicitly calls for a "fake `Choreographer` seam" in its own testing-strategy table (`Unit `:feature:overlay` (Robolectric)` row) and task 29's instructions say the same. Implemented as an interface (`FrameScheduler`) with a real `ChoreographerFrameScheduler`, matching what the design already asked for structurally.
3. **`OverlayAnchor`'s shape** (`xPx: Int, yPx: Int, sizePx: Int`) is not specified numerically in `design.md`/spec beyond "the value passed to `onTap`". I chose the pet's window bounds at tap time as the minimal, obviously-extensible shape for a future quick-menu anchor point; no quick menu was built.

### Issues Found

1. **Tasks artifact's `:core:data:test`/`:feature:overlay:testDebugUnitTest` command names.** `core:data` and `feature:overlay` are Android library modules; `test` is a lifecycle task there, not a `Test` task, so `--tests` filtering requires `testDebugUnitTest` (which is what the tasks artifact's own PR-2 task descriptions for `:feature:overlay` already use — task 21's `:core:data:test --tests ...` line is the one place the artifact names the wrong task). Used `:core:data:testDebugUnitTest --tests ...` instead; documented here rather than silently deviating.
2. **Task 27 (`FLAG_LAYOUT_NO_LIMITS` decision procedure) — manual leg not physically executed.** No Android emulator or device is attached in this apply environment (`emulator`/`adb` are both absent from `PATH`). I did not fabricate a device-drag observation. The flag is left **not set**, consistent with the design's structural expectation and the fact that `PetTouchController.snap()` and `OverlayWindowParams.clampToBounds` both already clamp `x`/`y` into in-bounds ranges before every `updateViewLayout` call, so no out-of-bounds coordinate is ever requested by this controller. The outcome, its reasoning, and the still-open manual-device requirement are recorded in `design.md`'s decision-12 section. **What would verify this**: an actual four-edge drag pass on an emulator with 3-button navigation and a real HyperOS device with gesture navigation, per the procedure's own text — flagged as outstanding for a human to run.
3. **Task 31 (API 34 instrumented attempt) — not physically executed.** Same missing-emulator constraint as above (`emulator -list-avds` and `adb devices` both fail with "command not found"). Recorded here rather than assumed: this PR's automated evidence for drag is Robolectric-only (`PetTouchControllerTest`, 7 passing cases using `MotionEvent.obtain` and the fake `Choreographer`/`SnapAnimator` seams, exactly as the design's "API 37 gap" section anticipates). **What would verify this**: `./gradlew :feature:overlay:connectedDebugAndroidTest` against a real API 34 AVD, to measure (not assume) whether the `InputManager.getInstance` gap slice 1 hit also affects this PR's instrumented-capable code, or a real-device manual drag pass — outstanding for a human to run.

## Work Unit 2 Evidence

| Evidence | Value |
|---|---|
| Focused test command and exact result | `./gradlew :feature:overlay:testDebugUnitTest --tests "*PetTouchControllerTest*"` → BUILD SUCCESSFUL. `TEST-*PetTouchControllerTest*.xml`: `tests="7" failures="0" errors="0"` |
| Domain-layer focused command and result | `./gradlew :core:domain:test --tests "*OverlayDragTest*" --tests "*ExceedsSlopTest*"` → BUILD SUCCESSFUL. `TEST-*OverlayDragTest*.xml`: `tests="4" failures="0" errors="0"`; `TEST-*ExceedsSlopTest*.xml`: `tests="4" failures="0" errors="0"` |
| Data-layer focused command and result | `./gradlew :core:data:testDebugUnitTest --tests "*DragStateRepositoryImplTest*"` → BUILD SUCCESSFUL. `TEST-*DragStateRepositoryImplTest*.xml`: `tests="3" failures="0" errors="0"` |
| Params-cap regression command and result | `./gradlew :feature:overlay:testDebugUnitTest --tests "*OverlayWindowParamsTest*"` → BUILD SUCCESSFUL. `TEST-*OverlayWindowParamsTest*.xml`: `tests="4" failures="0" errors="0"` |
| Full PR 2 build check | `./gradlew :core:domain:test :core:data:testDebugUnitTest :feature:overlay:testDebugUnitTest` → BUILD SUCCESSFUL, all above XML counts confirmed non-zero and green |
| DI wiring compile check | `./gradlew :feature:overlay:compileDebugKotlin :feature:overlay:kspDebugKotlin` → BUILD SUCCESSFUL; Hilt resolves `PetTouchController`'s dependencies (`FrameScheduler`, `SnapAnimator`, `DragStateRepository`) with no manual construction outside `PetOverlayService.addOverlayWindow` |
| Runtime harness command/scenario and exact result | **Not executed** — no Android emulator or `adb` available in this apply environment (`which emulator`/`adb devices` both fail: command not found). Robolectric substitutes for the touch/frame-throttle path per the design's own "API 37 gap" acceptance (drag's automated evidence is Robolectric, not instrumented). The manual four-edge drag pass (task 27) and the API 34 instrumented attempt (task 31) remain outstanding, recorded as open items rather than assumed passing |
| Rollback boundary | Revert: `core/domain/.../overlay/{OverlayDrag,OverlayRenderSize}.kt`; `core/data/.../overlay/DragStateRepositoryImpl.kt` + its `BindingsModule.kt` binding; `feature/overlay/.../input/` (6 files: `OverlayAnchor`, `OverlayTapListener`, `FrameScheduler`, `ChoreographerFrameScheduler`, `SnapAnimator`, `PetTouchController`); the `OverlayWindowParams.kt` size-derivation change (`PLACEHOLDER_SIZE_PX` would need to be restored); the `PetOverlayService.kt` touch-controller wiring; the `OverlayModule.kt` `OverlayInputBindingsModule` addition. Work Unit 1 (PR 1) is unaffected — this unit only adds new files and additive wiring |

### Status
14/14 tasks in Work Unit 1 (PR 1) complete. 18/18 tasks in Work Unit 2 (PR 2) complete — with tasks 27 and 31's manual/instrumented legs honestly recorded as not physically executable in this environment (no emulator/device attached), rather than assumed passing. All automated evidence (unit + Robolectric) is green. Work units 3-7 (PR 3-7) not started, per assigned scope. Ready for `sdd-verify` on Work Units 1-2, or for the next `sdd-apply` batch to begin Work Unit 3.

## Work Unit 3 — Fraction persistence and startup read ordering (PR 3, #16)

**Mode**: Standard (strict TDD not active for this project).

### Completed Tasks
- [x] 33. Create `OverlayPositionFraction.kt`
- [x] 34. Modify `OverlayPosition.kt` / `OverlayPositionRepository.kt`: `save()`, fraction-typed flow
- [x] 35. Unit test: fraction round-trip within tolerance
- [x] 36. Unit test: `validOrNull` rejects NaN and out-of-range, never fabricates
- [x] 37. Modify `OverlayPositionRepositoryImpl.kt`: replace int pixel keys with float fraction keys
- [x] 38. Extend `OverlayPositionRepositoryImpl`: legacy int-key `remove()` on first successful save
- [x] 39. Unit test: float keys only, missing → null, NaN/out-of-range → null, no int key exists
- [x] 40. Unit test: legacy int keys are removed on first successful save
- [x] 41. Create `PositionWriter.kt`: cancellable write-at-rest
- [x] 42. Wire `PetTouchController` to `PositionWriter`: cancel on drag start, write at snap settle
- [x] 43. Modify `PetOverlayService.kt`: await-first-read before `addView`, `drop(1)` collection
- [x] 44. Create `OverlayPositionConfig.kt` and bind in `OverlayModule.kt`
- [x] 45. Unit test (Robolectric): startup ordering — stored value / timeout fallback
- [x] 46. Unit test (Robolectric): a new drag cancels a pending write from the previous gesture
- [ ] 47. Manual acceptance pass: kill/restart with no jump; rotation preserves relative position — **BLOCKED: needs a device pass** (recorded, see Issues below)
- [x] 48. Full PR 3 build check

### Files Changed

| File | Action | What Was Done |
|------|--------|---------------|
| `core/domain/.../overlay/OverlayPositionFraction.kt` | Created | Fraction-of-travel-range persisted type (design decision 4); `toPixels`/`ofPixels` conversions; `validOrNull(x, y)` — the single gate a value coming off disk passes through, returning `null` on missing/NaN/infinite/out-of-`0f..1f` input, never a fabricated `0f`, `[POS-1]` `[POS-2]` |
| `core/domain/.../overlay/OverlayPositionRepository.kt` | Modified | `position: Flow<OverlayPositionFraction?>` (was `Flow<OverlayPosition?>`); added `suspend fun save(position: OverlayPositionFraction)` — no pixel type appears in the interface |
| `core/data/.../overlay/OverlayPositionRepositoryImpl.kt` | Modified | `floatPreferencesKey("overlay_position_x_fraction"/"_y_fraction")` replace the slice-1 `intPreferencesKey`s; `position` maps through `OverlayPositionFraction.validOrNull`; `save()` writes both float keys and removes both legacy int keys in the same `edit` block (explicit non-migration, `design.md` "The fraction migration") |
| `core/domain/src/test/.../overlay/OverlayPositionFractionTest.kt` | Created | 2 tests: round-trip within tolerance across 4 screen-size/render-size combinations × 4 fractions; travel-range boundary check |
| `core/domain/src/test/.../overlay/OverlayPositionFractionTest.kt` (second class, same file) | Created | `OverlayPositionFractionValidOrNullTest`: 9 tests — both present/in-range, NaN x, NaN y, out-of-range x, out-of-range y, missing x, missing y, both missing, infinite values |
| `core/data/src/test/.../overlay/OverlayPositionRepositoryImplTest.kt` | Created | 6 tests against a real temp-file DataStore (5) + one mocked-`DataStore` transform test (1, see Deviations): no-keys → `null`; poked NaN → `null`; poked out-of-range → `null`; save-then-read round-trips; no legacy int keys survive a save; legacy int keys present before a save are removed by it |
| `feature/overlay/.../position/PositionWriter.kt` | Created | `@Singleton`, `@Inject constructor(repository, @OverlayApplicationScope scope)` — design decision 7's scope, not `serviceScope`; `cancelPending()`, `writeAtRest(fraction)` (cancels-then-launches, one nullable `Job`); `writeAtRest` is the only call site invoking `repository.save`, `[POS-3]` `[POS-4]` |
| `feature/overlay/.../position/OverlayPositionConfig.kt` | Created | `data class OverlayPositionConfig(val firstReadTimeoutMillis: Long)` |
| `feature/overlay/.../input/PetTouchController.kt` | Modified | New `positionWriter: PositionWriter` constructor parameter; `ACTION_MOVE`'s past-slop transition calls `positionWriter.cancelPending()` (a new gesture supersedes any prior in-flight write, `[POS-4]`); `snap()`'s settle branch (after `dragStateRepository.set(false)`) computes `OverlayPositionFraction.ofPixels(...)` from the final resting pixel position and calls `positionWriter.writeAtRest(fraction)` — the only site in the drag path that ever calls it; `ACTION_MOVE` and every intermediate snap-animation frame never reach it |
| `feature/overlay/.../service/PetOverlayService.kt` | Modified | New `positionWriter`/`positionConfig` injected fields (passed through to `PetTouchController`); `onStartCommand`'s position job now: (1) `withTimeoutOrNull(positionConfig.firstReadTimeoutMillis) { positionRepository.position.first() }`, (2) `addOverlayWindow(resolvePosition(stored))` — `addView` never called before this suspending read completes or times out, (3) a second, independent `positionRepository.position.drop(1).collect { applyPosition(resolvePosition(it)) }` so the awaited emission is never re-applied as a second `updateViewLayout`; new `resolvePosition(fraction: OverlayPositionFraction?): OverlayPosition` helper — `null` (never persisted or timed out) and a real fraction both resolve here against live bounds, `[POS-5]` `[POS-6]` |
| `feature/overlay/.../di/OverlayModule.kt` | Modified | Added `POSITION_FIRST_READ_TIMEOUT_MILLIS = 500L` (injected, never a literal in the service) and `provideOverlayPositionConfig()`. `PositionWriter` needs no explicit `@Provides`/`@Binds` — plain `@Inject constructor` + `@Singleton`, Hilt constructs it directly |
| `feature/overlay/src/test/.../position/PositionWriterTest.kt` | Created | 3 tests (plain `runTest` + `StandardTestDispatcher`, no Robolectric needed): a new drag cancels a pending write from the previous gesture (verified via a slow fake repository whose `save()` observes its own cancellation and a `savedFractions` list showing only the second gesture's value survived); a write with no prior pending completes normally; `cancelPending()` with nothing in flight is a no-op |
| `feature/overlay/src/test/.../service/PetOverlayServiceStartupTest.kt` | Created | 2 Robolectric tests: a repository emitting a stored fraction before the timeout results in exactly one `addView` call; a repository that never emits within the injected 200ms timeout still results in exactly one `addView` call (proving the timeout fallback fires rather than suspending indefinitely) |
| `feature/overlay/src/test/.../input/PetTouchControllerTest.kt` | Modified | Added `FakeOverlayPositionRepository`; `newController`'s default `positionWriter` param now builds a real `PositionWriter` over that fake, so all 7 existing tests keep compiling/passing unchanged; new test: `snap settle writes exactly one fraction, never during ACTION_MOVE or the snap animation` — asserts `saveCount == 0` after DOWN, after past-slop MOVE, and after the intermediate animation frame, then `saveCount == 1` after UP |
| `feature/overlay/src/test/.../service/PetOverlayServiceTest.kt` | Modified | `buildAndCreateService`'s `position` parameter changed from `OverlayPosition` to `OverlayPositionFraction?` (interface type change); all 4 existing tests pass unchanged otherwise |
| `openspec/changes/slice-2-movable-and-yours/design.md` | Modified | Recorded task 47's outcome (manual pass not physically executed, same missing-emulator constraint as work unit 2) directly above the "API 37 gap" section |

### Deviations from Design

1. **`OverlayPositionRepositoryImplTest`'s legacy-removal test uses a mocked `DataStore<Preferences>` transform, not a real temp-file DataStore.** Reproduced independently (a bare two-`dataStore.edit()`-calls-to-the-same-file scratch test, since deleted) that `androidx.datastore-core` 1.2.1's `FileStorage` on **Windows** throws `IOException: Unable to rename ... .tmp to ...` on the *second* write to the same backing file — `Files.move` without `REPLACE_EXISTING` rejects overwriting an existing target, a Windows-JVM-only limitation (Android's real filesystem permits the same rename, which is why the other 5 real-file tests in the same class — each doing exactly one write — pass cleanly, and why this never surfaces on-device). Rather than skip the "legacy keys present before a save are removed" scenario (task 40's explicit requirement), the test fakes the `DataStore<Preferences>` boundary: it seeds a real in-memory `mutablePreferencesOf(legacyX to 42, legacyY to 84)`, captures the exact transform lambda `OverlayPositionRepositoryImpl.save()` submits to `updateData`, applies it to the seeded preferences, and asserts on the result — proving the same production code path (the single `edit` block writing float keys and removing both legacy int keys) without touching a real file twice. Documented here rather than silently working around it.

### Issues Found

1. **Task 47 (manual acceptance pass: kill/restart with no jump; rotation preserves relative position) — not physically executed.** Same missing-emulator/device constraint as work unit 2's tasks 27 and 31 (`emulator`/`adb` both absent from `PATH` in this apply environment). Not fabricated as passing. The fraction-persistence, write-at-rest cancellation, and await-first-read logic all have full automated (JVM + Robolectric) coverage instead, but the end-to-end visual observation this task requires — a real service kill/restart showing no jump, and a real device rotation showing the pet stays at the equivalent relative position — remains outstanding for a human running the app on `emulator-5554` or a real device. Recorded in `design.md` directly above the "API 37 gap" section, following the same pattern as decision 12's procedure outcome.

## Work Unit 3 Evidence

| Evidence | Value |
|---|---|
| Domain-layer focused command and result | `./gradlew :core:domain:test --tests "*OverlayPositionFraction*"` → BUILD SUCCESSFUL. `TEST-*OverlayPositionFractionTest*.xml`: `tests="2" failures="0"`; `TEST-*OverlayPositionFractionValidOrNullTest*.xml`: `tests="9" failures="0"` |
| Data-layer focused command and result | `./gradlew :core:data:testDebugUnitTest --tests "*OverlayPositionRepositoryImplTest*"` → BUILD SUCCESSFUL. `TEST-*OverlayPositionRepositoryImplTest*.xml`: `tests="6" failures="0"` |
| Feature-layer focused commands and results | `./gradlew :feature:overlay:testDebugUnitTest --tests "*PositionWriterTest*"` → `tests="3" failures="0"`; `--tests "*PetOverlayServiceStartupTest*"` → `tests="2" failures="0"`; `--tests "*PetTouchControllerTest*"` → `tests="8" failures="0"` (7 pre-existing + 1 new); `--tests "*PetOverlayServiceTest*"` → `tests="4" failures="0"` (all pre-existing, unaffected by the interface-type change); `--tests "*OverlayWindowParamsTest*"` → `tests="4" failures="0"` (regression check, untouched by this unit) |
| Full PR 3 build check | `./gradlew :core:domain:test :core:data:testDebugUnitTest :feature:overlay:testDebugUnitTest` → BUILD SUCCESSFUL, all above XML counts reconfirmed in the same combined run |
| DI wiring compile check | `./gradlew :feature:overlay:compileDebugKotlin :feature:overlay:kspDebugKotlin` → BUILD SUCCESSFUL; Hilt resolves `PositionWriter` (plain `@Inject constructor`) and `OverlayPositionConfig` into `PetOverlayService` and `PetTouchController` with no manual construction |
| Runtime harness command/scenario and exact result | **Not executed** — no Android emulator or `adb` available in this apply environment (same constraint as work unit 2, reconfirmed: `emulator`/`adb` absent from `PATH`). Task 47's manual kill/restart-and-rotation pass remains outstanding, recorded as an open item in `design.md` rather than assumed passing |
| Rollback boundary | Revert: `core/domain/.../overlay/OverlayPositionFraction.kt` (new file) + `OverlayPositionRepository.kt`'s interface change (back to `Flow<OverlayPosition?>`, no `save()`); `core/data/.../overlay/OverlayPositionRepositoryImpl.kt` (back to int keys, no `save()`); `feature/overlay/.../position/` (2 new files: `PositionWriter`, `OverlayPositionConfig`); `PetTouchController.kt`'s `positionWriter` parameter and its two call sites; `PetOverlayService.kt`'s await-first-read restructuring (back to the old unconditional `collect`); `OverlayModule.kt`'s new config provider. Per `design.md`'s migration note, #16 must be reverted before #15 — reverting #16 alone leaves orphaned float keys on any device that had written them, which read back as `null` and correctly fall back to the resting corner, not a crash |

### Status
16/16 automatable tasks in Work Unit 3 (PR 3) complete; task 47 (manual acceptance pass) is honestly left open — needs a device or emulator, unavailable in this apply environment. All automated evidence (unit + Robolectric) is green with confirmed non-zero XML counts. Work units 4-7 (PR 4-7) not started, per assigned scope. Ready for `sdd-verify` on Work Unit 3, or for the next `sdd-apply` batch to begin Work Unit 4.

---

## Device pass — two defects found on hardware (Redmi 24090RA29G, HyperOS, Android 16)

Both were in the drag release path, and the second was hidden underneath the first: until the
crash was fixed, execution never reached the write where the second defect lived.

### 1. Snap crashed the process on every release

`Animatable.animateTo` suspends on `withFrameNanos`, which requires a `MonotonicFrameClock` in the
calling context. The overlay drives the snap from the service scope (`Dispatchers.Main.immediate`),
which carries none, so releasing the pet threw `IllegalStateException` and killed the process.

Fixed by running the animation on `AndroidUiDispatcher.Main`.

**Why the tests missed it:** PR 2 extracted a `SnapAnimator` interface explicitly "for
testability", and every test injected a fake. The only class that could produce this failure —
`SpringSnapAnimator` — was exercised by nothing. A seam introduced because something is awkward to
test leaves exactly that thing untested unless a test drives the real implementation.
`SpringSnapAnimatorTest` now does, and was confirmed to fail with the production error when the fix
is reverted.

### 2. A stale frame callback undid the release clamp

`ACTION_MOVE` stores an unclamped `pendingY` and schedules a frame. `snap()` clamped `params.y` but
did not cancel that scheduled frame, so a callback still in flight restored the off-screen value
afterwards. A drag above the top edge persisted `y = -0.034`; `validOrNull` then rejected the whole
pair on read and the pet fell back to its resting corner — observed as "I drop it top-left in
landscape and it flies to the bottom-right".

Fixed by dropping the pending callback at the top of `snap()`.

**Why the tests missed it:** all nine controller tests call `runScheduledFrame()` *before*
`upEvent`, draining the queue by hand. The device does not guarantee that ordering. The new
regression case deliberately does not drain.

### Consequent design change (maintainer-directed)

Reading a stored position now distinguishes two cases that were previously collapsed:

- **Absent / non-finite** — stays absent. Nothing is invented, per the project's absence rule.
- **Present but out of range** — pulled to the nearest valid value and reported, because it is known
  intent that overshot. Discarding it threw away the good axis along with the bad one.

Values are clamped on write as well, so read-side recovery is a safety net for what is already on
disk rather than a routine path. Recovery raises a `PetFeedback.WARNING`, rendered as a one-second
radial glow *behind* the pet. `WARNING` rather than `ERROR` deliberately: the pet corrected its own
housekeeping and still placed itself sensibly, and colouring that red would blame the user for it.

### Still open

- Task 27's `FLAG_LAYOUT_NO_LIMITS` procedure. The four-edge drag has not been run against a build
  in which the drag actually works end to end. Injected `adb shell input swipe` is **not** a valid
  instrument on HyperOS: the system consumes the gesture as navigation before the overlay window
  sees it (one such injection launched the camera). This leg needs real fingers, or an emulator with
  3-button navigation.
- Task 31's API 34 instrumented measurement. Only an `android-37.1` system image is installed
  locally; measuring API 34 requires downloading one.
- Task 47's kill-and-restart pass. Rotation was exercised incidentally during the bug hunt;
  process death and restart were not.

## Work Unit 4 — Import and validation pipeline (PR 4, #39a)

**Mode**: Standard (strict TDD not active for this project).

### Completed Tasks
- [x] 49. Create `CharacterId.kt`
- [x] 50. Create `CharacterImportRejection.kt`
- [x] 51. Create `CharacterLibraryConfig.kt`
- [x] 52. Create `Character.kt` and `CharacterRepository.kt` (interfaces, this PR)
- [x] 53. Modify `SpriteSheetDecoder.kt`: expose `validateBounds(bytes): SpriteGridResult`
- [x] 54. Create `CharacterImporter.kt`: tier 1, PNG signature and byte-size ceiling
- [x] 55. Extend `CharacterImporter`: tier 2, bounds via `validateBounds`
- [x] 56. Extend `CharacterImporter`: tier 3, full decode and trailing-transparent scan
- [x] 57. Extend `CharacterImporter`: cap check and finalize-on-confirm move
- [x] 58. Add string resources for every `CharacterImportRejection` case
- [x] 59. Unit test: resource-scan proves no generic "invalid image" string exists
- [x] 60. Add fixtures for import tests (deviation: code-generated, see below)
- [x] 61. Unit test (Robolectric): all three tiers stop at first failure
- [x] 62. Unit test: cap rejection and confirm-only move
- [x] 63. Create `ImportScreen.kt`: Photo Picker launch, no storage permission
- [x] 64. Create `PreviewScreen.kt`: grid, per-row playback, row-to-state mapping, loading state
- [x] 65. Unit test (Robolectric, `createComposeRule`): loading indicator + preview content
- [ ] 66. Attempt the instrumented suite once against an API 34 image — **BLOCKED: no
      emulator/`adb` in this apply environment** (recorded, see Issues below)
- [x] 67. Full PR 4 build check

### Files Changed

| File | Action | What Was Done |
|------|--------|---------------|
| `core/domain/.../character/CharacterId.kt` | Created | `sealed interface CharacterId { BuiltIn(name), Imported(uuid) }`, `[IMPORT-7]` |
| `core/domain/.../character/CharacterImportRejection.kt` | Created | Seven distinct cases, each carrying its measured values (`NotPng`, `TooLarge`, `Oversized`, `NotDivisible`, `Undecodable`, `EmptySheet`, `CapReached`) — no generic catch-all, `[IMPORT-3]` `[IMPORT-4]` |
| `core/domain/.../character/CharacterLibraryConfig.kt` | Created | `data class CharacterLibraryConfig(maxImportedCharacters, maxImportBytes)` — injected |
| `core/domain/.../character/Character.kt` | Created | `data class Character(id, displayName)` |
| `core/domain/.../character/CharacterRepository.kt` | Created | Interface only this PR (`importedCharacters: Flow<Set<Imported>>`, `add`, `remove`); DataStore impl deferred to PR 5, `[IMPORT-7]` |
| `feature/overlay/.../sprite/SpriteSheetDecoder.kt` | Modified | Extracted `validateBounds(bytes): SpriteGridResult` (header + bounds only, zero pixel allocation); `decode()` now calls it first — one implementation, two entry points, no behavior change to the existing decode path |
| `feature/overlay/.../character/CharacterImporter.kt` | Created | Two-phase pipeline: `stage(uri)` (tiers 1+2 — copy to `cacheDir/import/<uuid>.png`, PNG signature, byte ceiling, bounds via `validateBounds`, no pixel buffer allocated on any rejection path) and `decodeAndScan(staged)` (tier 3 — the only suspending call costly enough to need its own loading state, per `character-import`'s "slow validation shows a loading state" requirement); `import(uri)` convenience wrapper runs both in sequence for callers with no intermediate UI. `confirm(import, currentImportedCount)` does the cap check then the finalize-on-confirm move into `filesDir/characters/<uuid>/idle.png` — the **folder** form per design decision 10, never a flat `<uuid>.png`; `abandon(import)` deletes the cache file. The source `Uri` is read exactly once, inside `stage`, never again. Every rejected/abandoned path deletes the cache file so nothing partial survives, `[IMPORT-2]` `[IMPORT-3]` `[IMPORT-5]` `[IMPORT-6]` `[IMPORT-12]` |
| `feature/overlay/.../character/CharacterImportRejectionMessages.kt` | Created | Maps every `CharacterImportRejection` case to its string-resource id and format args |
| `feature/overlay/src/main/res/values/strings.xml` | Created | Seven distinct rejection strings, each formatting that case's measured values; no generic "invalid image"/"invalid file" phrase anywhere, `[IMPORT-4]` |
| `feature/overlay/.../character/ui/CharacterImportController.kt` | Created | Plain `@Inject`-constructed class (not `androidx.lifecycle.ViewModel` — see Deviations) driving `Idle → Staging → DecodingAndScanning → Rejected/Ready` state via a `StateFlow`; `onImagePicked(uri)` runs `stage` then `decodeAndScan`; `reset()` abandons a `Ready`-but-uncommitted import |
| `feature/overlay/.../character/ui/ImportScreen.kt` | Created | Compose screen over `controller.state`; the `Ready` branch defers to `PreviewScreen` via a `LaunchedEffect`-guarded callback (never called directly in the composition body); never constructs a `PickVisualMedia` launcher or requests a storage permission itself — `onPickImage` is the only picking hook, `[IMPORT-1]` |
| `feature/overlay/.../character/ui/PreviewScreen.kt` | Created | Grid (this decoder produces exactly one row), per-row playback via `LaunchedEffect` + `SpriteLayout.cellLeftPx`, row-to-state label ("Maps to: IDLE"), confirm/cancel actions; confirm calls `importer.confirm` (the only call site that moves the file), cancel calls `importer.abandon`, `[IMPORT-8]` `[IMPORT-9]` |
| `feature/overlay/.../di/OverlayModule.kt` | Modified | Added injected `CharacterLibraryConfig` provider (`MAX_IMPORTED_CHARACTERS = 10`, `MAX_IMPORT_BYTES = 10MB`, never literals inside the importer) |
| `feature/overlay/build.gradle.kts` | Modified | Added `testImplementation(libs.bundles.compose.test)` and `testImplementation(libs.androidx.compose.ui.test.manifest)` — Robolectric's native-graphics mode supports `createComposeRule()` under the plain JVM test task, so this needed no `androidTest`/instrumentation and no `activity-compose` (which the module's Compose convention plugin deliberately excludes) |
| `feature/overlay/src/test/.../sprite/SpriteSheetDecoderTest.kt` | Unchanged, reverified | Full regression pass confirms `validateBounds` extraction changed no observable `decode()` behavior |
| `feature/overlay/src/test/.../character/CharacterImporterTest.kt` | Created | 7 Robolectric tests: oversized image rejected at bounds tier with a *structural* zero-full-decode-calls assertion (reusing the existing `BitmapDecoding` call-counting seam from `SpriteSheetDecoderTest`, not a new one); corrupt bytes rejected at header tier; all-transparent row rejected at tier 3 with `EmptySheet`; over-byte-ceiling file rejected at tier 1; import-at-cap rejected with `CapReached`; unconfirmed import leaves no file under `filesDir/characters/`; confirmed import moves the file to the folder path and deletes the cache copy. All cases drive the real `CharacterImporter`/`SpriteSheetDecoder`/`BitmapDecoding.Default` against real PNG bytes (via the pre-existing `SpriteFixtures` generator) — no fake importer |
| `feature/overlay/src/test/.../character/NoGenericRejectionStringTest.kt` | Created | Greps every `strings.xml` under `src/main/res` for generic catch-all phrases (fails on the literal phrase, so its own doc comment had to avoid saying "invalid image" — caught and fixed during this pass); confirms all seven distinct rejection-string names exist |
| `feature/overlay/src/test/.../character/ui/PreviewScreenTest.kt` | Created | 4 Robolectric `createComposeRule` tests: preview shows the row-to-state mapping label; preview shows confirm/cancel actions before commit; `ImportScreen` shows the loading indicator for the duration of a genuinely slow tier-3 decode (real `BitmapDecoding.Default` wrapped with a `Thread.sleep` before delegating — not a decode fake) and then reaches `Ready` |

### Deviations from Design

1. **`CharacterImporter` split into `stage()` + `decodeAndScan()` rather than one `import()` call.**
   `design.md`'s table names the three tiers but task 56 explicitly requires tier 3 to be "a
   suspending call so the caller can surface a loading state while this tier runs" — which is only
   possible if tiers 1–2 (fast) and tier 3 (potentially slow) are separately awaitable. `import()`
   still exists as a convenience wrapper for callers with no intermediate UI (e.g. the test suite).
2. **Task 60's "binary fixtures" are code-generated, not committed PNG files**, extending the exact
   precedent already set by slice 1's `SpriteFixtures.kt` (`feature/overlay/src/test/.../sprite/`),
   which documents the same tradeoff for its own fixtures: inspectable in the diff, trivially
   reviewable, JVM-only via `java.awt`/`ImageIO`, never shipped in `main`. `CharacterImporterTest`
   reuses `SpriteFixtures` directly rather than duplicating a second fixture generator.
3. **No `androidx.lifecycle.ViewModel`/`hiltViewModel()` for `CharacterImportController`.** This
   repo's Compose convention plugin deliberately excludes `activity-compose`
   (`gradle/libs.versions.toml`'s comment on `androidx-activity`), and no `hilt-navigation-compose`
   or `androidx.lifecycle:lifecycle-viewmodel-ktx` dependency exists anywhere in the project yet.
   `design.md` line 256 says ordinary screens *will* use `hiltViewModel()` once actually hosted
   (PR 6/7's scope), but adding that whole stack here — for a screen with no host Activity yet in
   this PR — was judged out of scope. `CharacterImportController` is instead a plain
   `@Inject`-constructed class with its own `CoroutineScope` and an explicit `close()`, matching the
   pattern this codebase already uses for non-Activity-scoped injected state (`PositionWriter`).
   Whoever wires `:app`'s Activity in a later PR owns this instance's lifetime.
4. **`ImportScreen` never constructs a `PickVisualMedia` `ActivityResultLauncher` itself** — for the
   same `activity-compose`-exclusion reason as deviation 3. `onPickImage: () -> Unit` is the only
   hook; the actual launcher registration is deferred to whichever `@AndroidEntryPoint` Activity in
   `:app` eventually hosts this screen (not built in this PR — `:app` currently has no character
   import surface at all). `onImagePicked(uri)` is the corresponding hand-back hook the controller
   exposes.
5. **Tier-2 rejection's measured `widthPx`/`heightPx` come from a second, cheap `decodeBounds` call**
   inside `CharacterImporter`, not from `SpriteGrid.of`/`SpriteGridResult` directly — the existing
   `SpriteSheetFailure.Oversized`/`.NotDivisible` cases in `:core:domain` are parameterless
   (`data object`s), so the concrete dimensions the `character-import` spec's "every rejection names
   the specific rule it broke" requirement demands aren't available from `validateBounds`'s return
   value alone. `bitmapDecoding.decodeBounds` is header-only (no pixel buffer), so this stays
   within the "no pixel buffer allocated" tier-2 contract.

### Issues Found

1. **Task 66 (API 34 instrumented attempt for `ImportScreen`/`PreviewScreen`) — not physically
   executed.** Same missing-emulator/`adb` constraint recorded for tasks 27, 31, and 47 in earlier
   work units. Not fabricated as passing. **What would verify this**:
   `./gradlew :feature:overlay:connectedDebugAndroidTest` against a real API 34 AVD, or a
   real-device manual PNG import pass — outstanding for a human to run.
2. **A real device/emulator manual PNG import pass is separately outstanding** (not itself a
   numbered task in this PR, but named in the tasks artifact's own "Runtime harness" column for
   this work unit). Same constraint.
3. **`feature/overlay` gained its first `androidTest`-independent Compose test dependencies**
   (`testImplementation(libs.bundles.compose.test)` +
   `testImplementation(libs.androidx.compose.ui.test.manifest)`), since no prior PR in this slice
   needed `createComposeRule()` outside `androidTest`. Verified working end-to-end (see Evidence);
   flagged here since it is a build-file dependency change beyond the source files `design.md`'s
   file-changes table lists for this PR.

## Work Unit 4 Evidence

| Evidence | Value |
|---|---|
| Domain-layer compile check | `./gradlew :core:domain:compileKotlin` → BUILD SUCCESSFUL |
| Feature-layer compile + KSP check | `./gradlew :feature:overlay:compileDebugKotlin` → BUILD SUCCESSFUL (includes `kspDebugKotlin`, confirming Hilt resolves `CharacterImporter`/`CharacterImportController` with the new `CharacterLibraryConfig` provider) |
| Test compile check | `./gradlew :feature:overlay:compileDebugUnitTestKotlin` → BUILD SUCCESSFUL after two fix rounds (JUnit `assertTrue`/`assertFalse` arg order; a stray import) |
| Focused test command and exact result | `./gradlew :core:domain:test :feature:overlay:testDebugUnitTest --tests "*CharacterImporterTest*" --tests "*NoGenericRejectionStringTest*" --tests "*PreviewScreenTest*"` → BUILD SUCCESSFUL after fixing a self-referential comment in `strings.xml` that itself matched the "generic phrase" grep, and switching the slow-decode Compose test from `composeRule.waitUntil` to a manual Robolectric main-looper pump (the raw coroutine dispatch wasn't visible to Compose's own idle mechanism). `TEST-*CharacterImporterTest*.xml`: `tests="7" failures="0" errors="0"`; `TEST-*NoGenericRejectionStringTest*.xml`: `tests="2" failures="0" errors="0"`; `TEST-*PreviewScreenTest*.xml`: `tests="3" failures="0" errors="0"` (2 preview-content cases + 1 loading-indicator case, all in the one file task 65 names) |
| Full PR 4 build check | `./gradlew :core:domain:test :feature:overlay:testDebugUnitTest` → BUILD SUCCESSFUL, 12 tests across the new suites all green (confirmed non-zero via the XML above), no regression to `SpriteSheetDecoderTest` or any Work-Unit-1–3 suite |
| Runtime harness command/scenario and exact result | **Not executed** — no Android emulator or `adb` available in this apply environment (same constraint as every prior work unit: `emulator`/`adb` absent from `PATH`). Task 66's instrumented attempt and the real-device manual import pass both remain outstanding, recorded here rather than assumed passing |
| Rollback boundary | Revert: `core/domain/.../character/` (5 new files); `feature/overlay/.../sprite/SpriteSheetDecoder.kt`'s `validateBounds` extraction (behavior-preserving, but still a diff to revert); `feature/overlay/.../character/` (`CharacterImporter.kt`, `CharacterImportRejectionMessages.kt`, `ui/CharacterImportController.kt`, `ui/ImportScreen.kt`, `ui/PreviewScreen.kt`); `feature/overlay/src/main/res/` (new directory, `strings.xml`); the `OverlayModule.kt` `CharacterLibraryConfig` provider addition; `feature/overlay/build.gradle.kts`'s two new `testImplementation` lines. Work Units 1–3 are unaffected — this unit only adds new files and one behavior-preserving extraction to existing slice-1 code |

### Status
16/18 tasks in Work Unit 4 (PR 4) complete; task 66 (API 34 instrumented attempt) is honestly left
open — needs a device or emulator, unavailable in this apply environment, consistent with every
prior work unit's device-dependent tasks. All automated evidence (JVM unit + Robolectric, including
the new Compose-under-Robolectric path) is green with confirmed non-zero XML counts. Work units 5–7
(PR 5–7) not started, per assigned scope. Ready for `sdd-verify` on Work Unit 4, or for the next
`sdd-apply` batch to begin Work Unit 5.

## Work Unit 5 — Character library and cap (PR 5, #39b)

**Mode**: Standard (strict TDD not active for this project).

### Completed Tasks
- [x] 68. Create `CharacterRepositoryImpl.kt`
- [x] 69. Bind `CharacterRepositoryImpl` in `core/data/di/BindingsModule.kt`
- [x] 70. Unit test: cap counts only imported characters, delete updates the set
- [x] 71. Create `LibraryScreen.kt`: list, delete, cap-reached messaging
- [x] 72. Unit test (Robolectric, `createComposeRule`): delete unavailable for built-ins, cap message shown
- [x] 73. Full PR 5 build check

### Files Changed

| File | Action | What Was Done |
|------|--------|---------------|
| `core/data/.../character/CharacterRepositoryImpl.kt` | Created | `stringSetPreferencesKey("characters")` (design decision 8) holding only imported ids; `add`/`remove` edit the DataStore set; `remove` also `deleteRecursively()`s the whole `filesDir/characters/<uuid>/` folder (not just `idle.png`) on `Dispatchers.IO`, so a half-deleted character folder cannot exist as an observable state, `[IMPORT-7]` `[IMPORT-12]` |
| `core/data/di/BindingsModule.kt` | Modified | Added `@Binds bindCharacterRepository(CharacterRepositoryImpl): CharacterRepository` |
| `core/data/src/test/.../character/CharacterRepositoryImplTest.kt` | Created | 4 tests: no imported characters emits an empty set (real temp-file DataStore); adding an id below cap persists and is reflected in the flow (real temp-file DataStore, single write); deleting an id removes it from the persisted set (mocked `DataStore` transform — same Windows double-write-to-one-file limitation as `OverlayPositionRepositoryImplTest`, documented in the test's own kdoc); deleting an id removes the whole character folder including a second animation file (`happy.png`) alongside `idle.png`, not just the idle file — the direct evidence for the folder-not-file deletion binding constraint |
| `feature/overlay/.../character/BuiltInCharacters.kt` | Created | `object BuiltInCharacters { val all: List<Character> }` — one fixed entry, `CharacterId.BuiltIn("default")`, matching the one bundled asset folder already shipped at `assets/pet/default/`; a compile-time list per design.md, never persisted or counted against the cap |
| `feature/overlay/.../character/ui/LibraryScreen.kt` | Created | Lists `BuiltInCharacters.all + importedCharacters` through the one shared `Character` model (`[IMPORT-7]`'s "built-in and imported share one model" requirement); delete button rendered only when `character.id is CharacterId.Imported` (never for a `BuiltIn`); `capReached = importedIds.size >= config.maxImportedCharacters` swaps the "Import a character" action for the same `R.string.import_rejection_cap_reached` message the importer produces, so the reason import is unavailable is visible before the user tries. No active-character selection/switching wired here — that surface and `PetOverlayStateHolder`'s decode lifecycle are out of scope for this work unit per the binding constraints, deferred to work unit 6 |
| `feature/overlay/src/test/.../character/ui/LibraryScreenTest.kt` | Created | 3 Robolectric `createComposeRule` tests: exactly one "Delete" button exists (for the imported entry, never for "Default"); the exact cap-reached string renders when the imported count equals the injected cap; the "Import a character" action renders instead of the cap message when under the cap. Uses a hand-written `FakeCharacterRepository` (`MutableStateFlow`-backed) rather than a mock, matching the project's existing fake-over-mock preference for stateful repositories |

### Deviations from Design

1. **`BuiltInCharacters` and its one entry are not named in `design.md`** — the design says built-ins are "a fixed compile-time list" without naming the list's contents or file location. I placed it in `feature/overlay/character/` (not `:core:domain`) because it names an asset-folder convention (`assets/pet/<name>/`) that is a feature-layer, Android-asset concern, matching where `CharacterSheetLoader` (work unit 6) will consume it. Its single entry (`"default"`) matches the one bundled asset folder already shipped in this repo (`feature/overlay/src/main/assets/pet/default/idle.png`); no new asset was added.
2. **Imported characters' `displayName` is a fixed literal ("Imported character"), not user-supplied metadata.** Neither `character-import`'s spec nor `design.md` defines a name-capture step anywhere in the import flow (picking, staging, preview, or confirm), and `CharacterRepository`'s persisted schema (PR 4) stores only the id set, no name. Rather than inventing an unspecified capture UI, imported entries render a fixed, honest placeholder label; the `Character.displayName` field itself (PR 4) is otherwise unused for imported ids in this work unit.

### Issues Found

None. No task in this work unit named a device/emulator-dependent step; all six tasks are fully automatable and were run to completion.

## Work Unit 5 Evidence

| Evidence | Value |
|---|---|
| Focused test command and exact result | `./gradlew :core:data:testDebugUnitTest --tests "*CharacterRepositoryImplTest*"` → BUILD SUCCESSFUL. `TEST-*CharacterRepositoryImplTest*.xml`: `tests="4" failures="0" errors="0"`. `./gradlew :feature:overlay:testDebugUnitTest --tests "*LibraryScreenTest*"` → BUILD SUCCESSFUL. `TEST-*LibraryScreenTest*.xml`: `tests="3" failures="0" errors="0"` |
| Full PR 5 build check | `./gradlew :core:data:testDebugUnitTest :feature:overlay:testDebugUnitTest` → BUILD SUCCESSFUL, both suites' XML counts reconfirmed in the same combined run, no regression to any prior work unit's tests |
| DI wiring compile check | Included in the full build check above — Hilt resolves `CharacterRepository` to `CharacterRepositoryImpl` (`kspDebugKotlin`/`hiltJavaCompileDebugUnitTest` both succeeded) with no manual construction |
| Runtime harness command/scenario and exact result | N/A — DataStore-backed repository logic and a Compose screen, both fully covered by JVM unit tests and Robolectric per the tasks artifact's own harness column for this work unit ("N/A — DataStore-only logic, Robolectric covers it"). No new Android runtime boundary (service, window, touch input) is crossed by this work unit |
| Rollback boundary | Revert: `core/data/.../character/CharacterRepositoryImpl.kt` (new file) + its `@Binds` in `BindingsModule.kt`; `feature/overlay/.../character/BuiltInCharacters.kt` and `feature/overlay/.../character/ui/LibraryScreen.kt` (new files). Work Units 1–4 are unaffected — PR 4's `CharacterImporter`/`ImportScreen`/`PreviewScreen` still compile and pass standalone with this unit reverted, since none of them reference `CharacterRepositoryImpl` or `LibraryScreen` |

### Status
6/6 tasks in Work Unit 5 (PR 5) complete. All automated evidence (JVM unit + Robolectric) is green
with confirmed non-zero XML counts. Work units 6–7 (PR 6–7) not started, per assigned scope —
explicitly did not touch active-character switching or `PetOverlayStateHolder`'s decode lifecycle,
per this batch's binding constraints. Ready for `sdd-verify` on Work Unit 5, or for the next
`sdd-apply` batch to begin Work Unit 6.

## Work Unit 5.1 — Declared sprite grid, and the import name step (PR 5.1, #69)

Mid-slice scope (design.md decisions 13–14), not part of the original PR count. Decision 15
(tap-to-cycle gesture) is explicitly not part of this unit.

**Mode**: Standard (strict TDD not active for this project).

### Completed Tasks
- [x] 104. Create `SpriteGridDeclaration.kt`
- [x] 105. Rewrite `SpriteGrid.of`: declared grid, not inferred
- [x] 106. Extend `SpriteLayout`: `rows`, `cellTopPx`, row-major frame order
- [x] 107. Unit test: a square sheet is never accepted as a one-frame animation
- [x] 108. Unit test: a declared 6x6 grid over a 36-frame sheet is valid and expressible
- [x] 109. Create `CharacterName.kt`
- [x] 110. Create `CharacterManifestFailure.kt`
- [x] 111. Modify `Character.kt`: `name: CharacterName?`
- [x] 112. Modify `CharacterRepository.kt`: persist the full `Character`
- [x] 113. Modify `CharacterRepositoryImpl.kt`: persist name per uuid
- [x] 114. Unit test: name persists, absence stays absent, remove clears the name key
- [x] 115. Modify `SpriteSheetDecoder.kt`: `validateBounds`/`decode` take a declaration
- [x] 116. Modify `TransparentCellScanner.kt`: row-major trailing scan
- [x] 117. Create `BuiltInCharacterManifestReader.kt`
- [x] 118. Add `assets/pet/default/manifest.properties`
- [x] 119. Modify `PetOverlayStateHolder.kt`: decode `default` through its manifest
- [x] 120. Unit test: bundled manifest reader — missing, malformed, valid
- [x] 121. Modify `CharacterImporter.kt`: `stage` proposes a candidate, never validates one
- [x] 122. Modify `CharacterImporter.kt`: `decodeAndScan` validates the caller's declaration
- [x] 123. Modify `CharacterImporter.kt`: `confirm` returns the id; caller owns `repository.add`
- [x] 124. Modify `CharacterImportController.kt`: `GridEntry` state + `onGridConfirmed`
- [x] 125. Modify `ImportScreen.kt`: render `GridEntry`
- [x] 126. Modify `PreviewScreen.kt`: capture the name, animate the full declared grid
- [x] 127. Modify `LibraryScreen.kt`: render the real name or an honest placeholder
- [x] 128. Add `character_unnamed` string; update `import_rejection_not_divisible` wording
- [x] 129. Update existing tests for the new signatures
- [x] 130. Full PR 5.1 build check

### Files Changed

| File | Action | What Was Done |
|------|--------|---------------|
| `core/domain/.../pet/sprite/SpriteGridDeclaration.kt` | Created | `data class SpriteGridDeclaration(columns, rows)` — the one shape both an import declaration and a bundled manifest describe |
| `core/domain/.../pet/sprite/SpriteGrid.kt` | Modified | `of(widthPx, heightPx, declaration, maxDimensionPx)` — grid is declared, never derived; `NotDivisible` now also covers a non-square resulting cell, not just a remainder; `Oversized`-before-`NotDivisible` order unchanged |
| `core/domain/.../pet/sprite/SpriteLayout.kt` | Modified | `grid.rows`; `frameCount` bound is `columns * rows`; `cellTopPx` added; `cellLeftPx`/`cellTopPx` derive `col`/`row` from `frame % columns` / `frame / columns` |
| `core/domain/.../character/CharacterName.kt` | Created | `validOrNull` — blank/whitespace/`null` all collapse to `null`, mirroring `OverlayPositionFraction.validOrNull` |
| `core/domain/.../character/CharacterManifestFailure.kt` | Created | `Missing` / `Malformed(reason)` — a bundled character with no manifest is one of these, never a guess |
| `core/domain/.../character/Character.kt` | Modified | `displayName: String` → `name: CharacterName?` |
| `core/domain/.../character/CharacterRepository.kt` | Modified | `importedCharacters: Flow<List<Character>>`; `add(character: Character)` replaces `add(id)` |
| `core/data/.../character/CharacterRepositoryImpl.kt` | Modified | One `stringPreferencesKey("character_name_<uuid>")` per imported id, read through `CharacterName.validOrNull`; `remove` clears it too |
| `feature/overlay/.../sprite/SpriteSheetDecoder.kt` | Modified | `validateBounds`/`decode` take `declaration: SpriteGridDeclaration` — the decoder never derives a grid |
| `feature/overlay/.../sprite/TransparentCellScanner.kt` | Modified | Scans `columns * rows - 1` downward through row-major frame indices, not `columns - 1` |
| `feature/overlay/.../character/BuiltInCharacterManifestReader.kt` | Created | Reads `assets/pet/<name>/manifest.properties`; `Missing` when the asset is absent, `Malformed` when keys are absent/non-positive |
| `feature/overlay/src/main/assets/pet/default/manifest.properties` | Created | `columns=6`, `rows=1` — the shipped `idle.png`'s real shape (2046x341, cell 341), unchanged visible behavior |
| `feature/overlay/.../ui/PetOverlayStateHolder.kt` | Modified | Reads the `default` manifest before decoding; a manifest failure collapses to `SpriteSheetFailure.Undecodable` at this one call site — documented deviation below |
| `feature/overlay/.../character/CharacterImporter.kt` | Modified | `stage` now only rejects `NotPng`/`TooLarge`/`Undecodable`/`Oversized` (no grid needed) and returns a `StagedImport` carrying a best-effort `candidate: SpriteGridDeclaration`; `decodeAndScan(staged, declaration)` validates the caller's declaration before the full decode; `confirm` unchanged except it no longer touches the repository — callers own `repository.add` |
| `feature/overlay/.../character/ui/CharacterImportController.kt` | Modified | New `GridEntry(staged, candidate)` state between `Staging` and `DecodingAndScanning`; `onGridConfirmed(declaration)` is the only path into `decodeAndScan` |
| `feature/overlay/.../character/ui/ImportScreen.kt` | Modified | Renders `GridEntry`: two editable numeric fields pre-filled with the candidate, a "Preview" action calling `onGridConfirmed` |
| `feature/overlay/.../character/ui/PreviewScreen.kt` | Modified | Adds a "Name (optional)" field (starts empty, never pre-filled); playback now walks the full declared grid via `cellLeftPx`/`cellTopPx`; confirm calls `importer.confirm` then `repository.add(Character(id, CharacterName.validOrNull(nameText)))` — closes the previously-open gap where a confirmed import was never persisted to the repository at all |
| `feature/overlay/.../character/ui/LibraryScreen.kt` | Modified | Renders `character.name?.value ?: stringResource(R.string.character_unnamed)` — an honest "Unnamed" placeholder, never the old fixed "Imported character" string masquerading as a real name |
| `feature/overlay/src/main/res/values/strings.xml` | Modified | `import_rejection_not_divisible` reworded for a declared (not inferred) grid; added `character_unnamed` |
| `core/domain/src/test/.../pet/sprite/SpriteGridTest.kt` | Rewritten | 9 tests, including the exact regression: a genuinely 6x6 square sheet declared as `1x6` is rejected (`NotDivisible`, non-square cell), and correctly declared `6x6` succeeds |
| `core/domain/src/test/.../pet/sprite/SpriteLayoutTest.kt` | Modified | Added row-wrap (`cellLeftPx`/`cellTopPx` across a row boundary) and a 36-frame-in-a-6x6-grid case |
| `feature/overlay/src/test/.../sprite/SpriteFixtures.kt` | Modified | Added `multiRowSheetBytes(cellSizePx, columns, rows, opaqueFrames)`; `validSheetBytes` now delegates to it with `rows = 1` |
| `feature/overlay/src/test/.../sprite/SpriteSheetDecoderTest.kt` | Rewritten | 7 tests including the specific regression (a 6x6-shaped square sheet declared `1x1` "succeeds" at the wrong 1-frame shape — proving *why* the grid must be declared correctly, not guessed — and correctly declaring `6x6` recovers all 36 frames) and a dedicated 36-frame-through-a-6x6-declaration case |
| `feature/overlay/src/test/.../sprite/TransparentCellScannerTest.kt` | Modified | Added a 6x6 multi-row trailing-clamp case and a fully-opaque 36-frame case |
| `feature/overlay/src/test/.../character/BuiltInCharacterManifestReaderTest.kt` | Created | 2 tests: the shipped `default` manifest reads back `6x1`; a non-existent character folder is `Missing` |
| `feature/overlay/src/test/.../character/CharacterImporterTest.kt` | Modified | Renamed the oversize test (now caught at `stage`, not tier 2); added a candidate-detection test, a wrong-1x6-declaration-over-a-6x6-sheet rejection test, and a correct-6x6-declaration 36-frame success test |
| `feature/overlay/src/test/.../character/ui/PreviewScreenTest.kt` | Modified | Added `repository` to every call site; added two new tests proving an empty name persists `null` and a supplied name persists exactly; the loading-indicator test now drives through the new `GridEntry` step first |
| `feature/overlay/src/test/.../character/ui/LibraryScreenTest.kt` | Modified | `FakeCharacterRepository` now holds `List<Character>`; added tests for a real captured name rendering and an absent name rendering "Unnamed" |
| `core/data/src/test/.../character/CharacterRepositoryImplTest.kt` | Modified | Added a name-persists-with-add test, a name-absent-persists-null test, and extended the mocked-transform delete test to assert the name key is cleared |
| `feature/overlay/src/test/.../ui/PetOverlayClockTest.kt` | Modified | `SpriteGrid(...)` construction updated for the new `rows` parameter (mechanical, no behavior change) |

### Deviations from Design

1. **`PetOverlayStateHolder` collapses a manifest failure to `SpriteSheetFailure.Undecodable`.** The
   typed `CharacterManifestFailure` from `BuiltInCharacterManifestReader` doesn't propagate through
   this call site as its own distinct result — `PetOverlayStateHolder`'s `sheetResult` type is
   `SpriteSheetResult`, and reworking that type to also carry a manifest failure belongs to
   `CharacterSheetLoader`/`PetOverlayStateHolder`'s decode-lifecycle rework, explicitly out of scope
   for this PR (the next PR, #39c). This is the one place the typed failure is deliberately narrowed;
   the reader itself (`BuiltInCharacterManifestReader`) always returns the real typed failure.
2. **`stage()`'s oversize check reads bounds directly via `BitmapDecoding.decodeBounds`, not through
   `SpriteSheetDecoder.validateBounds`.** `validateBounds` now requires a `SpriteGridDeclaration`,
   which doesn't exist yet at the staging step (the grid is declared later, on the preview screen).
   The oversize bound itself doesn't depend on a declared grid, so `stage()` checks it directly
   against the decoded bounds rather than waiting for tier 2.
3. **`CharacterImporter.confirm` no longer calls `CharacterRepository.add` — it never did.** Reading
   the pre-existing code before this PR, `confirm()` only ever moved the file and returned a
   `CharacterId.Imported`; nothing in the codebase called `repository.add` anywhere, so a confirmed
   import was previously never added to the persisted character set at all (a pre-existing gap, not
   introduced here). This PR closes it: `PreviewScreen`'s confirm handler is now the one call site
   that calls `repository.add(Character(id, name))` after a successful `confirm`.
4. **`ImportScreen`'s `GridEntry` UI is intentionally minimal** — two plain numeric text fields, no
   input validation feedback beyond "not a number is ignored". `design.md` doesn't specify the entry
   step's visual design beyond "proposes a detected candidate and lets the user correct it"; a fuller
   treatment (validation messaging, live grid preview) is left to whoever builds the real import
   screen host in `:app` (still out of scope — no Activity hosts these screens yet, per PR 4's
   deviation 4, unchanged in this PR).

### Issues Found

1. **The regression tests are evidence by construction, not by a literal before/after re-run.**
   Per this batch's instructions, I did not revert `SpriteGrid.kt` and re-run the new tests against
   the old inference logic (doing so would require reverting the whole coupled surface — decoder,
   importer, controller, UI, and every touched test — since the new signatures are load-bearing
   everywhere). Instead, `SpriteGridTest`'s and `SpriteSheetDecoderTest`'s new cases directly encode
   the two documented failures from issue #69 (a square sheet declared with the wrong 1-row shape
   succeeds at the wrong shape rather than being silently accepted as *the whole grid in one frame*,
   and a 36-frame 6x6 sheet — impossible under the retired one-row cap — now decodes and animates in
   full) and pass only because `SpriteGrid.of` now validates the declared grid's square-cell and
   divisibility constraints instead of deriving `columns = 1` for any square sheet. The pre-PR
   behavior (`cellSizePx = heightPx`, `columns = widthPx / heightPx`, unconditionally) is preserved
   verbatim in this section's prose and in `design.md`'s decision 13 for anyone who wants to check by
   inspection.
2. **`feature/overlay/src/androidTest/.../PetOverlayRendersTest.kt` was already not compiling before
   this PR** — it constructs `PetOverlayStateHolder` without the `positionRepository` parameter PR 3
   added, and I did not fix it further (it also now needs a `manifestReader` argument). This is
   pre-existing debt in an `androidTest` source set that `testDebugUnitTest`/`test` never compiles,
   so it did not block any verification in this batch, but it remains broken and is flagged here
   rather than silently left for someone else to discover. **What would fix it**: pass a real or fake
   `OverlayPositionRepository` and `BuiltInCharacterManifestReader` into the direct constructor call.

## Work Unit 5.1 Evidence

| Evidence | Value |
|---|---|
| Domain-layer focused command and result | `./gradlew :core:domain:test --tests "*SpriteGridTest*" --tests "*SpriteLayoutTest*"` → BUILD SUCCESSFUL. `TEST-...SpriteGridTest.xml`: `tests="9" failures="0" errors="0"`; `TEST-...SpriteLayoutTest.xml`: `tests="6" failures="0" errors="0"` |
| Full `:core:domain:test` | `./gradlew :core:domain:test` → BUILD SUCCESSFUL (all prior work units' suites unaffected) |
| Data-layer focused command and result | `./gradlew :core:data:testDebugUnitTest --tests "*CharacterRepositoryImplTest*"` (run as part of the full module suite) → `TEST-...CharacterRepositoryImplTest.xml`: `tests="5" failures="0" errors="0"` |
| Full `:core:data:testDebugUnitTest` | `./gradlew :core:data:testDebugUnitTest` → BUILD SUCCESSFUL |
| Feature-layer focused commands and results | `TEST-...SpriteSheetDecoderTest.xml`: `tests="7" failures="0"`; `TEST-...TransparentCellScannerTest.xml`: `tests="5" failures="0"`; `TEST-...CharacterImporterTest.xml`: `tests="10" failures="0"`; `TEST-...BuiltInCharacterManifestReaderTest.xml`: `tests="2" failures="0"`; `TEST-...PreviewScreenTest.xml`: `tests="5" failures="0"`; `TEST-...LibraryScreenTest.xml`: `tests="5" failures="0"` — all confirmed from the same combined run below |
| Full `:feature:overlay:testDebugUnitTest` | `./gradlew :feature:overlay:testDebugUnitTest` → BUILD SUCCESSFUL, no regression to any prior work unit's suite (Work Units 1–5's tests all still pass in the same run) |
| DI wiring compile check | `./gradlew :feature:overlay:compileDebugKotlin` → BUILD SUCCESSFUL (includes `kspDebugKotlin`; Hilt resolves `BuiltInCharacterManifestReader`'s plain `@Inject constructor` and the changed `CharacterImporter`/`CharacterImportController` graph) |
| Runtime harness command/scenario and exact result | **Not executed** — no Android emulator or `adb` available in this apply environment (same constraint as every prior work unit). No task in this unit named a device-dependent step (unlike PR 2/3's manual drag/rotation passes); `PetOverlayStateHolder`'s manifest read is exercised by the existing `PetOverlayRendersTest` androidTest only, which — per Issue 2 above — was already non-compiling before this PR and remains outstanding for a human to fix and run |
| Rollback boundary | Revert: `core/domain/.../pet/sprite/{SpriteGrid,SpriteLayout,SpriteGridDeclaration}.kt`; `core/domain/.../character/{CharacterName,CharacterManifestFailure,Character,CharacterRepository}.kt`; `core/data/.../character/CharacterRepositoryImpl.kt`; `feature/overlay/.../sprite/{SpriteSheetDecoder,TransparentCellScanner}.kt`; `feature/overlay/.../character/{BuiltInCharacterManifestReader,CharacterImporter,BuiltInCharacters}.kt` + `ui/{CharacterImportController,ImportScreen,PreviewScreen,LibraryScreen}.kt`; `feature/overlay/.../ui/PetOverlayStateHolder.kt`; the new `assets/pet/default/manifest.properties`; `strings.xml`'s two string changes; every touched test file listed above. Work Units 1–5 are unaffected in behavior, though PR 5's `CharacterRepositoryImplTest`/`LibraryScreenTest` were rewritten in place (git history preserves the pre-PR-5.1 versions) rather than left as a separate parallel file, since the interface itself changed underneath them |

### Status
27/27 automatable tasks in Work Unit 5.1 (PR 5.1) complete. All automated evidence (JVM unit +
Robolectric) is green with confirmed non-zero XML counts across every new and modified suite. No
device/emulator-dependent task exists in this unit's scope. Decision 15 (tap-to-cycle gesture) was
explicitly not built, per this batch's binding constraints. `PetOverlayStateHolder`'s
decode-lifecycle rework and active-character switching remain out of scope for the next PR (#39c).
Ready for `sdd-verify` on Work Unit 5.1.
