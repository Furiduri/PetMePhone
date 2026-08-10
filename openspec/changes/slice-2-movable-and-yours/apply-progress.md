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
