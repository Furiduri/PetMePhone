# Tasks: Slice 2 — A pet you can move, and make yours (#37, #15, #16, #39, #12)

Ordered implementation checklist. No code lives in this file. Each task states a done condition;
tasks tagged **Verify** carry the exact command apply must run to confirm the claim, because
`failOnNoDiscoveredTests = false` makes a green build alone insufficient — a passing build must be
cross-checked against test counts in `*/build/test-results/**/TEST-*.xml`.

Traceability tags: `[STATE-n]` → `specs/pet-state-resolution/spec.md`, `[DRAG-n]` →
`specs/overlay-drag/spec.md`, `[POS-n]` → `specs/overlay-position-persistence/spec.md`,
`[IMPORT-n]` → `specs/character-import/spec.md`, `[ONBOARD-n]` →
`specs/overlay-onboarding-ui/spec.md`, `[RENDER-n]` → `specs/pet-overlay-rendering/spec.md`
(delta), `[SHEET-n]` → `specs/pet-sprite-sheet/spec.md` (delta) — n counts requirements in file
order within each spec.

---

## PR 1 — State types, resolver, dwell, DRAGGING + IDLE providers (#37)

Targets the slice tracker branch (`feature-branch-chain`). Est. changed lines: ~260 (of the 800
budget). No dependency on any other PR.

### `:core:domain`

1. [x] **Create `PetState.kt`.** `[STATE-3]` `[RENDER-1]`
   `enum class PetState { IDLE, DRAGGING, TYPING, HAPPY, SLEEPING, HUNGRY }` at
   `core/domain/.../pet/state/PetState.kt`, names map to animation filenames.
   Done: compiles; six entries in that order.
   Depends on: none.

2. [x] **Create `PetSnapshot.kt`.** `[STATE-1]`
   `data class PetSnapshot(val isDragging: Boolean)` — the only field with a real producer in this
   slice (decision 1). No `Instant`, duration, or event list.
   Done: compiles.
   Depends on: none. Parallelizable with Task 1.

3. [x] **Create `PetStateProvider.kt`.** `[STATE-2]`
   `interface PetStateProvider { val priority: Int; fun evaluate(snapshot: PetSnapshot): PetState? }`
   at `core/domain/.../pet/state/PetStateProvider.kt`.
   Done: compiles.
   Depends on: Tasks 1, 2.

4. [x] **Create `PetStateConfig.kt`.** `[STATE-6]`
   `data class PetStateConfig(val minimumDwellMillis: Long)` — injected, never a literal inside the
   resolver.
   Done: compiles.
   Depends on: none. Parallelizable with Tasks 1–3.

5. [x] **Create `PetStateResolver.kt`: construction fail-fast and `resolve()`.** `[STATE-3]` `[STATE-4]`
   `class PetStateResolver(providers: Set<PetStateProvider>, private val config: PetStateConfig)`
   with an `init` block that throws if `providers.distinctBy { it.priority }.size != providers.size`;
   `fun resolve(snapshot: PetSnapshot): PetState` iterates providers in descending priority order,
   returns the first non-null result, else `PetState.IDLE`.
   Done: compiles; duplicate-priority construction throws before any `evaluate` call.
   Depends on: Task 3, 4.

6. [x] **Extend `PetStateResolver` with `states(): Flow<PetState>` and the dwell operator.** `[STATE-6]`
   Custom `Flow` operator: `distinctUntilChanged()` then delay-the-remainder-and-recheck-latest
   against `config.minimumDwellMillis`, per `design.md` decision 2 (never `debounce`/`sample`,
   which drop the first change).
   Done: compiles; no screen-on/off gating anywhere in this class (`[STATE-7]`).
   Depends on: Task 5.

7. [x] **Create `DraggingStateProvider.kt`.** `[STATE-5]`
   Reads `DragStateRepository.isDragging` value passed through the snapshot (not the repository
   itself, per `[STATE-2]`'s purity rule); registered at a priority higher than IDLE; returns
   `PetState.DRAGGING` when `snapshot.isDragging`, else `null`.
   Done: compiles; `evaluate` reads only its `PetSnapshot` argument.
   Depends on: Task 3.

8. [x] **Create `IdleStateProvider.kt`.** `[STATE-3]`
   Lowest priority; returns `null` always (resolver's own IDLE fallback in `[STATE-3]` covers the
   "no provider matches" case, but this provider exists as the extension-point exemplar per
   `design.md`'s file-changes table).
   Done: compiles.
   Depends on: Task 3.

9. [x] **Create `DragStateRepository.kt` (interface only, this PR).** `[DRAG-6]`
   `interface DragStateRepository { val isDragging: StateFlow<Boolean>; fun set(dragging: Boolean) }`
   at `core/domain/.../overlay/DragStateRepository.kt`. Implementation lands in PR 2 alongside its
   first writer.
   Done: compiles.
   Depends on: none. Parallelizable with Tasks 1–8.

10. [x] **Unit test: resolver order-independence and IDLE fallback.** `[STATE-3]`
    Cases: same provider set in two injection orders resolves identically; no provider matches →
    IDLE; DRAGGING true → DRAGGING regardless of provider registration order.
    Verify: `./gradlew :core:domain:test --tests "*PetStateResolverTest*"`; confirm test count in
    `core/domain/build/test-results/test/TEST-*PetStateResolverTest*.xml`.
    Done: file exists, passes, XML confirms count.
    Depends on: Tasks 5, 7, 8.

11. [x] **Unit test: fail-fast duplicate priority and full registered-set distinctness.** `[STATE-4]`
    Cases: two providers with identical priority throws at construction, never at `evaluate`; the
    full DI-shaped set (DRAGGING, IDLE) has strictly distinct priorities.
    Verify: `./gradlew :core:domain:test --tests "*PetStateResolverTest*"`; confirm count in the
    same XML file as Task 10 (or a distinct test class if split).
    Done: passes, XML confirms count.
    Depends on: Task 5.

12. [x] **Unit test (`runTest` virtual time): dwell coalesces a flapping snapshot.** `[STATE-6]`
    Assert no two distinct emissions land closer together than the injected dwell duration when the
    snapshot flow alternates rapidly across the window; assert screen-on/off has no effect on
    emission (`[STATE-7]` — a fake "off" input still produces resolved-state emissions).
    Verify: `./gradlew :core:domain:test --tests "*PetStateResolverFlowTest*"`; confirm count in
    `core/domain/build/test-results/test/TEST-*PetStateResolverFlowTest*.xml`.
    Done: file exists, passes, XML confirms count.
    Depends on: Task 6.

### Wiring

13. [x] **Bind `DraggingStateProvider` and `IdleStateProvider` `@IntoSet` in `OverlayModule.kt`.**
    Also provide `PetStateConfig(minimumDwellMillis)` as injected config (never a literal) and bind
    `PetStateResolver` from the graph.
    Done: compiles; `OverlayModule` resolves `PetStateResolver` with no manual construction.
    Depends on: Tasks 6, 7, 8.

14. [x] **Full PR 1 build check.**
    Verify: `./gradlew :core:domain:test`; confirm non-zero test counts across all new
    `TEST-*.xml` files from Tasks 10–12 (`failOnNoDiscoveredTests = false` trap).
    Done: build green, XML counts confirm real execution.
    Depends on: Tasks 10, 11, 12, 13.

---

## PR 2 — Drag and horizontal edge snap (#15)

Targets PR 1's branch (`feature-branch-chain`). Est. changed lines: ~380 (of the 800 budget).

### `:core:domain`

15. [x] **Create `OverlayDrag.kt`: `ScreenEdge`, `nearestEdge`, `exceedsSlop`.** `[DRAG-9]`
    `enum class ScreenEdge { LEFT, RIGHT }`; `fun nearestEdge(xPx: Int, screenWidthPx: Int,
    renderSizePx: Int): ScreenEdge` — exact centre snaps RIGHT (design decision 6); `fun
    exceedsSlop(dxPx: Float, dyPx: Float, slopPx: Int): Boolean`. Pure, no Android import.
    Done: compiles in `:core:domain` with no Android dependency.
    Depends on: none.

16. [x] **Create `OverlayRenderSize.kt`.** `[IMPORT-10]`
    `object OverlayRenderSize { const val MAX_RENDER_SIZE_PX = 220 }` at
    `core/domain/.../overlay/OverlayRenderSize.kt` — the named cap replacing the "placeholder"
    constant (design decision 11).
    Done: compiles.
    Depends on: none. Parallelizable with Task 15.

17. [x] **Unit test: `nearestEdge` table including the exact-centre tie-break.** `[DRAG-9]`
    Table of horizontal positions vs. screen bounds; assert exact centre resolves RIGHT.
    Verify: `./gradlew :core:domain:test --tests "*OverlayDragTest*"`; confirm count in
    `core/domain/build/test-results/test/TEST-*OverlayDragTest*.xml`.
    Done: file exists, passes, XML confirms count.
    Depends on: Task 15.

18. [x] **Unit test: `exceedsSlop` boundary cases.** `[DRAG-2]`
    Cases: distance below slop → false; distance exactly at slop → false (design: movement must
    *exceed*, not equal); distance past slop → true.
    Verify: same command/target as Task 17 (or a dedicated `*ExceedsSlopTest*`); confirm XML count.
    Done: passes, XML confirms count.
    Depends on: Task 15.

### `:core:data`

19. [x] **Create `DragStateRepositoryImpl.kt`.** `[DRAG-6]`
    `@Singleton` `MutableStateFlow<Boolean>`-backed implementation of `DragStateRepository`
    (interface from PR 1). No persistence — in-memory only.
    Done: compiles; `set(dragging)` updates the exposed `StateFlow` and nothing is written to
    DataStore.
    Depends on: PR 1 Task 9.

20. [x] **Bind `DragStateRepositoryImpl` in `core/data/di/BindingsModule.kt`.**
    `@Binds` `DragStateRepository` to `DragStateRepositoryImpl`.
    Done: compiles; resolves from the Hilt graph.
    Depends on: Task 19.

21. [x] **Unit test: DRAGGING reports true only between drag start and drag end.** `[DRAG-6]`
    Assert the `StateFlow` never touches persistence — no DataStore fake/mock is exercised by this
    test at all.
    Verify: `./gradlew :core:data:test --tests "*DragStateRepositoryImplTest*"`; confirm count in
    `core/data/build/test-results/test/TEST-*DragStateRepositoryImplTest*.xml`.
    Done: file exists, passes, XML confirms count.
    Depends on: Task 19.

### `:feature:overlay`

22. [x] **Modify `OverlayWindowParams.kt`: derive size from `OverlayRenderSize.MAX_RENDER_SIZE_PX`; delete `PLACEHOLDER_SIZE_PX`.** `[IMPORT-10]`
    Window width/height come from the cap constant; the old placeholder-named constant is removed
    entirely (design decision 11).
    Verify: `rg -i PLACEHOLDER_SIZE_PX feature/overlay/src` returns no matches.
    Done: compiles; no residual reference.
    Depends on: Task 16.

23. [x] **Create `OverlayAnchor.kt` and `OverlayTapListener.kt`.** `[DRAG-3]`
    `OverlayAnchor` is the value passed to `onTap`; `OverlayTapListener` is a functional interface
    (`fun interface`) the controller invokes on a genuine tap (slop never exceeded).
    Done: compiles.
    Depends on: none. Parallelizable with Task 22.

24. [x] **Create `PetTouchController.kt`: DOWN/MOVE/UP state machine.** `[DRAG-1]` `[DRAG-2]` `[DRAG-3]` `[DRAG-4]`
    Single `View.OnTouchListener` attached once to `ComposeOverlayHost` (design decision 5, never
    Compose `pointerInput`). DOWN records `rawX/Y` and current `params.x/y`. MOVE calls
    `exceedsSlop`; once past slop, calls `DragStateRepository.set(true)` and updates pending
    coordinates; schedules at most one `Choreographer.postFrameCallback` per pending update, which
    issues at most one `updateViewLayout` per rendered frame. UP: slop never exceeded → invoke
    `onTap(OverlayAnchor)`, no window position change, no snap; slop exceeded → proceed to snap
    (Task 25).
    Done: compiles; `updateViewLayout` call sites are structurally reachable only from the single
    frame callback, never directly from `ACTION_MOVE`.
    Depends on: Tasks 15, 19 (repository), 23.

25. [x] **Extend `PetTouchController`: snap animation on release after a drag.** `[DRAG-5]` `[DRAG-7]`
    On UP after exceeding slop: compute `nearestEdge` against current `x`, animate horizontally via
    `Animatable` + `spring()` updating `params.x` only; `y` stays exactly at the release value
    (frozen, never touched by the snap). Nearest-edge computation accounts for navigation bar
    insets so the resting `y` bound never overlaps the nav bar. On settle: `DragStateRepository.set(false)`.
    Done: compiles; the animation writes only `params.x` during the snap; insets are read from
    `WindowInsets`, not a literal.
    Depends on: Task 24.

26. [x] **Extend `PetTouchController`: cancellation on service destroy mid-drag.** `[DRAG-8]`
    Expose a `cancel()` (or equivalent lifecycle hook) that cancels any pending `Choreographer`
    frame callback and any running snap-animation coroutine; wire it from `PetOverlayService`'s
    `onDestroy`.
    Done: compiles; cancellation removes the frame callback via `Choreographer.removeFrameCallback`
    (or the injected seam's equivalent) and cancels the animation `Job`/coroutine scope.
    Depends on: Task 25.

27. [x] **`FLAG_LAYOUT_NO_LIMITS` decision procedure.** `[DRAG-7]`
    **Outcome: the flag stays UNSET.**
    Device pass performed on a Redmi Note 14 Pro 5G (Android 16 / API 36, HyperOS 3.0) with gesture
    navigation — leg (b) of the procedure. The four-edge drag showed no visible clipping and no
    refused coordinate; the maintainer confirmed the pet reaches every edge and corner correctly.
    Supporting measurement, read from `adb shell dumpsys window windows`: the overlay window's
    parent frame is `[0,130][1220,2660]`, i.e. the system already excludes the status bar and the
    gesture bar, and a stored x-fraction of `1.0` resolves to `x = 1000 = 1220 - 220`, flush against
    the usable right edge. Nothing is clipped because nothing is asked to draw under the bars.
    Because the flag is NOT set, the conditional obligation in this task — explicit `y` clamping
    against nav-bar insets — does not apply. Task 25's inset read stays a read.
    Honest limitation: leg (a), the emulator with 3-button navigation, was NOT executed. Closed on
    the real-device leg alone by maintainer decision, which is the stricter of the two environments
    for this question.
    Done: outcome recorded here and in `design.md` decision 12.
    Depends on: Task 25.

28. [x] **Wire `PetOverlayService.kt`: attach `PetTouchController`, cancel on destroy.**
    Service constructs/injects the controller and attaches it as `ComposeOverlayHost`'s
    `OnTouchListener` when the window is added; calls `cancel()` (Task 26) from `onDestroy`.
    Done: compiles; `PetOverlayService` gains no new state field of its own — the controller and
    repository are the state, per the proposal's cross-cutting rule.
    Depends on: Task 26.

29. [x] **Unit test (Robolectric, `MotionEvent.obtain`, fake `Choreographer` seam): sub-slop, past-slop, throttle, cancellation.** `[DRAG-1]` `[DRAG-2]` `[DRAG-3]` `[DRAG-4]` `[DRAG-8]`
    Cases: sub-slop touch leaves `params` untouched and fires `onTap` exactly once; past-slop
    follows the finger; `updateViewLayout` call count ≤ frame count via the fake `Choreographer`
    seam even when `ACTION_MOVE` arrives above refresh rate; destroy mid-drag cancels the pending
    frame callback and the animation coroutine with no crash from a callback holding a dead view;
    if Task 27 set the flag, an additional case asserts `y` stays clamped against nav-bar insets.
    Verify: `./gradlew :feature:overlay:testDebugUnitTest --tests "*PetTouchControllerTest*"`;
    confirm test count in
    `feature/overlay/build/test-results/testDebugUnitTest/TEST-*PetTouchControllerTest*.xml`.
    Done: file exists, passes, XML confirms count.
    Depends on: Tasks 24, 25, 26, 27.

30. [x] **Unit test (Robolectric): snap direction and vertical-preservation, params derive from the cap.** `[DRAG-5]` `[IMPORT-10]`
    Cases: release closer to left edge snaps left with a spring (not a jump); vertical coordinate
    at snap completion equals the coordinate at release; `OverlayWindowParams` size equals
    `MAX_RENDER_SIZE_PX` and `FLAG_NOT_FOCUSABLE` remains set.
    Verify: `./gradlew :feature:overlay:testDebugUnitTest --tests "*PetTouchControllerTest*"
    --tests "*OverlayWindowParamsTest*"`; confirm counts in both `TEST-*.xml` files.
    Done: files exist, pass, XML confirms counts.
    Depends on: Tasks 22, 25.

31. [ ] **Attempt the instrumented suite once against an API 34 image; record the result.**
    **BLOCKED: no emulator or `adb` in the apply environment.** Not executed, and not assumed away.
    Run the existing connected-test target against an API 34 emulator (not 37) to measure whether
    the `InputManager.getInstance` gap clears; record the outcome in `design.md`'s "Open questions"
    or a PR-body note, per the design's per-affected-PR measurement requirement.
    Verify: `./gradlew :feature:overlay:connectedDebugAndroidTest` against an API 34 AVD.
    Done: result recorded (pass, same failure, or different failure), not assumed.
    Depends on: Task 29.

32. [x] **Full PR 2 build check.**
    Verify: `./gradlew :core:domain:test :core:data:test :feature:overlay:testDebugUnitTest`;
    confirm non-zero counts across all new `TEST-*.xml` files from Tasks 17, 18, 21, 29, 30.
    Done: build green, XML counts confirm real execution.
    Depends on: Tasks 17, 18, 21, 29, 30, 31.

---

## PR 3 — Fraction persistence and startup read ordering (#16)

Targets PR 2's branch (`feature-branch-chain`). Est. changed lines: ~240 (of the 800 budget).

### `:core:domain`

33. [x] **Create `OverlayPositionFraction.kt`.** `[POS-1]`
    `data class OverlayPositionFraction(val x: Float, val y: Float)` — fraction of the travel range
    (design decision 4), with `fun toPixels(widthPx: Int, heightPx: Int, renderSizePx: Int):
    OverlayPosition` and companion `fun ofPixels(...)`, `fun validOrNull(x: Float?, y: Float?):
    OverlayPositionFraction?` returning `null` on NaN or out-of-`0f..1f`-range input.
    Done: compiles; `validOrNull` never returns a fabricated `0f` when an input is absent.
    Depends on: none.

34. [x] **Modify `OverlayPosition.kt` / `OverlayPositionRepository.kt`: `save()`, fraction-typed flow.** `[POS-1]` `[POS-2]`
    `interface OverlayPositionRepository { val position: Flow<OverlayPositionFraction?>; suspend
    fun save(position: OverlayPositionFraction) }`.
    Done: compiles; no pixel type appears in the interface.
    Depends on: Task 33.

35. [x] **Unit test: fraction round-trip within tolerance.** `[POS-1]`
    `toPixels` then `ofPixels` returns the original fraction within floating-point tolerance, across
    a table of screen sizes and render sizes.
    Verify: `./gradlew :core:domain:test --tests "*OverlayPositionFractionTest*"`; confirm count in
    `core/domain/build/test-results/test/TEST-*OverlayPositionFractionTest*.xml`.
    Done: file exists, passes, XML confirms count.
    Depends on: Task 33.

36. [x] **Unit test: `validOrNull` rejects NaN and out-of-range, never fabricates.** `[POS-2]`
    Cases: both present and in range → non-null; either NaN → `null`; either outside `0f..1f` →
    `null`; missing → `null`. No case in the suite asserts a fabricated `0f`.
    Verify: same command as Task 35; confirm XML count.
    Done: passes, XML confirms count.
    Depends on: Task 33.

### `:core:data`

37. [x] **Modify `OverlayPositionRepositoryImpl.kt`: replace int pixel keys with float fraction keys.** `[POS-1]` `[POS-2]`
    Remove `intPreferencesKey("overlay_position_x"/"_y")`; add
    `floatPreferencesKey("overlay_position_x_fraction"/"_y_fraction")`; `position` maps stored
    values through `OverlayPositionFraction.validOrNull`, emitting `null` when either key is absent,
    non-finite, or out of range — never `0f`.
    Done: compiles; no `intPreferencesKey` for position remains in the file.
    Depends on: Task 34.

38. [x] **Extend `OverlayPositionRepositoryImpl`: legacy int-key `remove()` on first successful save.** `[POS-1]`
    Per the design's explicit non-migration: the first successful `save()` calls `remove()` on both
    legacy int keys inside the same `DataStore.edit` block, in addition to writing the new float
    keys.
    Done: compiles; a single `edit` block performs both the write and the legacy removal.
    Depends on: Task 37.

39. [x] **Unit test: float keys only, missing → null, NaN/out-of-range → null, no int key exists.** `[POS-1]` `[POS-2]`
    `runTest` + temp-file DataStore. Cases: no keys written → `position` emits `null`; a
    directly-poked NaN or out-of-range float → `null`; confirm the schema has zero
    `intPreferencesKey` for position.
    Verify: `./gradlew :core:data:test --tests "*OverlayPositionRepositoryImplTest*"`; confirm count
    in `core/data/build/test-results/test/TEST-*OverlayPositionRepositoryImplTest*.xml`.
    Done: file exists, passes, XML confirms count.
    Depends on: Task 37.

40. [x] **Unit test: legacy int keys are removed on first successful save.** `[POS-1]`
    Seed the temp DataStore with legacy int keys present (simulating a hypothetical prior write),
    call `save()`, and assert both int keys are absent afterward and the new float keys hold the
    saved value.
    Verify: same command as Task 39; confirm XML count.
    Done: passes, XML confirms count.
    Depends on: Task 38.

### `:feature:overlay`

41. [x] **Create `PositionWriter.kt`: cancellable write-at-rest.** `[POS-3]` `[POS-4]`
    `@Singleton`, `@OverlayApplicationScope` (design decision 7, never `serviceScope`). Holds one
    nullable `Job`. `cancelPending()` called on drag start; `writeAtRest(fraction)` cancels any
    prior job then launches a new one calling `repository.save`. One `DataStore.edit` per completed
    gesture.
    Done: compiles; `writeAtRest` is the only call site that invokes `repository.save`.
    Depends on: Task 34.

42. [x] **Wire `PetTouchController` (PR 2) to `PositionWriter`: cancel on drag start, write at snap settle.** `[POS-3]` `[POS-4]`
    Drag start (past-slop transition) calls `positionWriter.cancelPending()`; snap settle (after
    `DragStateRepository.set(false)`) calls `positionWriter.writeAtRest(fraction)` with the fraction
    computed from the final resting pixel position.
    Done: compiles; `ACTION_MOVE` and intermediate animation frames never call `writeAtRest`.
    Depends on: Task 41, PR 2 Task 25.

43. [x] **Modify `PetOverlayService.kt`: await-first-read before `addView`, `drop(1)` collection.** `[POS-5]` `[POS-6]`
    `val stored = withTimeoutOrNull(positionConfig.firstReadTimeoutMillis) {
    positionRepository.position.first() }`; `addOverlayWindow(stored?.toPixels(w, h,
    MAX_RENDER_SIZE_PX) ?: restingCorner())`; then `serviceScope.launch {
    positionRepository.position.drop(1).collect { ... } }`. Both a `null` stored value and a
    timeout collapse to the same computed-corner branch.
    Done: compiles; `addView` is never called before this suspending read completes or times out;
    `drop(1)` present.
    Depends on: Task 42.

44. [x] **Create `OverlayPositionConfig.kt` and bind in `OverlayModule.kt`.**
    `data class OverlayPositionConfig(val firstReadTimeoutMillis: Long)`, provided via Hilt — never
    a literal inside `PetOverlayService`.
    Done: compiles; grep confirms no numeric literal for the timeout inside the service class.
    Depends on: none. Parallelizable with Tasks 41–43.

45. [x] **Unit test (Robolectric): startup ordering — stored value shows no intermediate default frame; timeout falls back.** `[POS-5]` `[POS-6]`
    Cases: a fake repository emitting a stored value before the timeout results in `addView` called
    once, directly at the stored position; a repository that never emits within the injected timeout
    results in `addView` called at the computed resting corner, not blocked indefinitely.
    Verify: `./gradlew :feature:overlay:testDebugUnitTest --tests "*PetOverlayServiceStartupTest*"`;
    confirm count in
    `feature/overlay/build/test-results/testDebugUnitTest/TEST-*PetOverlayServiceStartupTest*.xml`.
    Done: file exists, passes, XML confirms count.
    Depends on: Task 43.

46. [x] **Unit test (Robolectric): a new drag cancels a pending write from the previous gesture.** `[POS-4]`
    Simulate a snap settle triggering `writeAtRest`, then start a new drag before the write
    completes; assert the in-flight write is cancelled and only the new gesture's eventual resting
    position is persisted.
    Verify: `./gradlew :feature:overlay:testDebugUnitTest --tests "*PositionWriterTest*"`; confirm
    count in `feature/overlay/build/test-results/testDebugUnitTest/TEST-*PositionWriterTest*.xml`.
    Done: file exists, passes, XML confirms count.
    Depends on: Task 42.

47. [ ] **Manual acceptance pass: kill/restart with no jump; rotation preserves relative position.** `[POS-7]` `[POS-8]`
    On `emulator-5554` (or a real device): drag to an edge, kill the service, restart it, confirm no
    jump; rotate the device mid-session and confirm the pet stays on screen at the equivalent
    relative position. Record the observation (screenshot or log) in the PR body.
    Done: both observations recorded.
    Depends on: Task 43. Requires the emulator or a device.

48. [x] **Full PR 3 build check.**
    Verify: `./gradlew :core:domain:test :core:data:test :feature:overlay:testDebugUnitTest`;
    confirm non-zero counts across all new `TEST-*.xml` files from Tasks 35, 36, 39, 40, 45, 46.
    Done: build green, XML counts confirm real execution.
    Depends on: Tasks 35, 36, 39, 40, 45, 46, 47.

---

## PR 4 — Import and validation pipeline (#39a)

Targets PR 3's branch (`feature-branch-chain`). Est. changed lines: ~520 (of the 800 budget).
Carries most of the slice's risk.

### `:core:domain`

49. [x] **Create `CharacterId.kt`.** `[IMPORT-7]`
    `sealed interface CharacterId { data class BuiltIn(val name: String); data class
    Imported(val uuid: String) }` at `core/domain/.../character/CharacterId.kt`.
    Done: compiles.
    Depends on: none.

50. [x] **Create `CharacterImportRejection.kt`.** `[IMPORT-3]` `[IMPORT-4]`
    `sealed interface CharacterImportRejection { data object NotPng; data class
    TooLarge(actualBytes, maxBytes); data class Oversized(widthPx, heightPx, maxPx); data class
    NotDivisible(widthPx, heightPx); data object Undecodable; data object EmptySheet; data class
    CapReached(cap) }` — every case carries its measured values.
    Done: compiles; seven distinct cases exist, none of them a generic "invalid" catch-all.
    Depends on: none. Parallelizable with Task 49.

51. [x] **Create `CharacterLibraryConfig.kt`.**
    `data class CharacterLibraryConfig(val maxImportedCharacters: Int, val maxImportBytes: Long)` —
    injected, never a literal.
    Done: compiles.
    Depends on: none. Parallelizable with Tasks 49–50.

52. [x] **Create `Character.kt` and `CharacterRepository.kt` (interfaces, this PR).** `[IMPORT-7]`
    `Character` models an id plus display metadata; `CharacterRepository` exposes the persisted
    character id set. Implementation and DataStore binding land in PR 5 with the library UI.
    Done: compiles.
    Depends on: Task 49.

### `:feature:overlay` — decoder extension

53. [x] **Modify `SpriteSheetDecoder.kt`: expose `validateBounds(bytes): SpriteGridResult`.**
    Header-only path (bounds decode + `SpriteGrid.of`) factored out so the existing `decode` calls
    it — one implementation, two entry points, avoiding a second copy of tier-2/3 logic.
    Done: compiles; `decode`'s existing behavior is unchanged (no regression to PR 1/PR 2 of
    slice 1's decoder tests).
    Depends on: none (modifies existing slice-1 code).

### `:feature:overlay` — import pipeline

54. [x] **Create `CharacterImporter.kt`: tier 1, PNG signature and byte-size ceiling.** `[IMPORT-3]` `[IMPORT-2]`
    Copy the Photo-Picker-selected `Uri` to `cacheDir/import/<uuid>.png` first (design decision 9);
    then check the 8-byte PNG magic signature and `maxImportBytes` ceiling with no pixel buffer
    allocated. On failure, return `CharacterImportRejection.NotPng` or `.TooLarge` with measured
    values; the source `Uri` is never read again after the copy.
    Done: compiles; a failing tier-1 case allocates zero pixel buffers.
    Depends on: Task 50, 51.

55. [x] **Extend `CharacterImporter`: tier 2, bounds via `validateBounds`.** `[IMPORT-3]`
    On tier-1 pass, call `SpriteSheetDecoder.validateBounds` against the cached bytes; on
    `Invalid(Oversized)`/`Invalid(NotDivisible)`, map to the corresponding
    `CharacterImportRejection` case with measured dimensions; no full-resolution decode occurs on
    this path.
    Done: compiles; the full-decode call is structurally unreachable when tier 2 fails.
    Depends on: Task 53, 54.

56. [x] **Extend `CharacterImporter`: tier 3, full decode and trailing-transparent scan.** `[IMPORT-3]` `[IMPORT-6]`
    On tier-2 pass, full-decode via the existing decoder and `TransparentCellScanner`; an
    all-transparent IDLE row maps to `CharacterImportRejection.EmptySheet`. Exposes a suspending
    call so the caller can surface a loading state while this tier runs.
    Done: compiles; tier 3 is unreachable when tier 1 or 2 fails.
    Depends on: Task 55.

57. [x] **Extend `CharacterImporter`: cap check and finalize-on-confirm move.** `[IMPORT-3]` `[IMPORT-12]`
    Before finalize, check the current character count against `CharacterLibraryConfig
    .maxImportedCharacters`; over cap → `CharacterImportRejection.CapReached(cap)`. On confirm
    (never before), move the validated file from `cacheDir/import/<uuid>.png` to
    `filesDir/characters/<uuid>/idle.png` — the folder form (design decision 10, superseding the
    flat wording in the original `character-import` spec text). A rejected or abandoned import
    leaves nothing in the library.
    Done: compiles; the move only happens after explicit confirm; the destination is the folder
    path, never `<uuid>.png` flat.
    Depends on: Task 56.

58. [x] **Add string resources for every `CharacterImportRejection` case, each naming its measured rule.** `[IMPORT-4]`
    One string resource per rejection case, each formatting the case's carried values (e.g.
    "Image is 2200×2200, maximum is 2048×2048"). No generic "invalid image" string exists anywhere
    in the module.
    Done: seven distinct strings exist, each references its case's fields.
    Depends on: Task 50.

59. [x] **Unit test: resource-scan proves no generic "invalid image" string exists.** `[IMPORT-4]`
    Greps all `strings.xml` resources under `feature/overlay` for a generic catch-all phrase; fails
    if one is found.
    Verify: `./gradlew :feature:overlay:testDebugUnitTest --tests "*NoGenericRejectionStringTest*"`;
    confirm count in
    `feature/overlay/build/test-results/testDebugUnitTest/TEST-*NoGenericRejectionStringTest*.xml`.
    Done: file exists, passes, XML confirms count.
    Depends on: Task 58.

60. [x] **Add binary fixtures for import tests.** `[IMPORT-3]`
    Fixture PNGs: corrupt bytes, oversized header, non-divisible header, valid sheet, all-transparent
    IDLE row, over-byte-ceiling file. Store under `feature/overlay/src/test/resources/import/`.
    Done: fixtures committed, referenced by Task 61, no fixture used from `main` resources.
    Depends on: none (parallelizable with Tasks 53–58).

61. [x] **Unit test (Robolectric): all three tiers stop at first failure, over byte fixtures.** `[IMPORT-3]` `[IMPORT-5]`
    Cases per tier: oversized image rejected at bounds tier with zero full-decode calls (structural
    assertion via an injectable seam, not just the result); corrupt bytes rejected at header tier
    with a distinct message, never a crash; valid sheet with an all-transparent IDLE row rejected at
    tier 3 with `EmptySheet`; over-byte-ceiling file rejected at tier 1.
    Verify: `./gradlew :feature:overlay:testDebugUnitTest --tests "*CharacterImporterTest*"`;
    confirm count in
    `feature/overlay/build/test-results/testDebugUnitTest/TEST-*CharacterImporterTest*.xml`.
    Done: file exists, passes, XML confirms count.
    Depends on: Task 57, 60.

62. [x] **Unit test: cap rejection and confirm-only move.** `[IMPORT-12]` `[IMPORT-2]`
    Cases: import attempted at cap → `CapReached(cap)` with guidance message; a validated-but-not-
    confirmed import leaves no file under `filesDir/characters/`; a confirmed import moves the file
    to the folder path and the cache copy is gone.
    Verify: same command target as Task 61, distinct test class if split; confirm XML count.
    Done: passes, XML confirms count.
    Depends on: Task 57.

### UI — pick, preview

63. [x] **Create `ImportScreen.kt`: Photo Picker launch, no storage permission.** `[IMPORT-1]`
    `PickVisualMedia` launcher; on selection, hand the `Uri` to `CharacterImporter`. No storage
    permission request anywhere in the flow.
    Done: compiles; grep confirms no `READ_MEDIA_IMAGES`/`READ_EXTERNAL_STORAGE` permission request
    added by this screen.
    Depends on: Task 54.

64. [x] **Create `PreviewScreen.kt`: grid, per-row playback, row-to-state mapping, loading state during tier 3.** `[IMPORT-8]` `[IMPORT-9]`
    After tiers 1–2 pass, show a loading indicator while tier 3 (Task 56) runs; on success, animate
    each detected row using the same `SpriteLayout` arithmetic the renderer uses, and label which
    `PetState` each row maps to (IDLE, under one-file-per-animation naming). Import commits only on
    explicit user confirm.
    Done: compiles; the move (Task 57) is not invoked until the confirm action fires.
    Depends on: Task 56, 63.

65. [x] **Unit test (Robolectric, `createComposeRule`): loading indicator visible during tier 3; preview shows grid/playback/mapping.** `[IMPORT-8]` `[IMPORT-9]`
    Assert the loading state is visible for the duration of a slow (fake-delayed) tier-3 call;
    assert the preview surface exposes the grid, per-row animation, and the row-to-state label.
    Verify: `./gradlew :feature:overlay:testDebugUnitTest --tests "*PreviewScreenTest*"`; confirm
    count in
    `feature/overlay/build/test-results/testDebugUnitTest/TEST-*PreviewScreenTest*.xml`.
    Done: file exists, passes, XML confirms count.
    Depends on: Task 64.

66. [ ] **Attempt the instrumented suite once against an API 34 image for `ImportScreen`/`PreviewScreen`; record the result.**
    Same measurement obligation as PR 2 Task 31, applied to this PR's new instrumented-capable
    screens.
    Verify: `./gradlew :feature:overlay:connectedDebugAndroidTest` against an API 34 AVD.
    Done: result recorded.
    Depends on: Task 64.

67. [x] **Full PR 4 build check.**
    Verify: `./gradlew :core:domain:test :feature:overlay:testDebugUnitTest`; confirm non-zero
    counts across all new `TEST-*.xml` files from Tasks 59, 61, 62, 65.
    Done: build green, XML counts confirm real execution.
    Depends on: Tasks 59, 61, 62, 65, 66.

---

## PR 5 — Character library and cap (#39b)

Targets PR 4's branch (`feature-branch-chain`). Est. changed lines: ~340 (of the 800 budget).

### `:core:data`

68. [x] **Create `CharacterRepositoryImpl.kt`.** `[IMPORT-7]` `[IMPORT-12]`
    DataStore-backed implementation of `CharacterRepository` (PR 4 interface) using
    `stringSetPreferencesKey("characters")` (design decision 8) for the imported id set. Built-in
    characters are not stored — they are a fixed compile-time list, never counted against the cap.
    Done: compiles; the cap check reads only the imported set's size.
    Depends on: PR 4 Task 52.

69. [x] **Bind `CharacterRepositoryImpl` in `core/data/di/BindingsModule.kt`.**
    `@Binds` `CharacterRepository` to `CharacterRepositoryImpl`.
    Done: compiles; resolves from the Hilt graph.
    Depends on: Task 68.

70. [x] **Unit test: cap counts only imported characters, delete updates the set.** `[IMPORT-12]`
    `runTest` + temp-file DataStore. Cases: adding an imported id below cap succeeds; adding at cap
    is rejected upstream (repository itself just reports count — the rejection message lives in
    `CharacterImporter`); deleting an id removes it from the persisted set and the corresponding
    file is deleted from `filesDir/characters/<uuid>/`.
    Verify: `./gradlew :core:data:test --tests "*CharacterRepositoryImplTest*"`; confirm count in
    `core/data/build/test-results/test/TEST-*CharacterRepositoryImplTest*.xml`.
    Done: file exists, passes, XML confirms count.
    Depends on: Task 68.

### `:feature:overlay`

71. [x] **Create `LibraryScreen.kt`: list, delete, cap-reached messaging.** `[IMPORT-12]`
    Shows built-in and imported characters; delete action available only for imported ones (never
    built-ins); surfaces the cap-reached rejection message (Task 58's `CapReached` string) when
    import is attempted at cap.
    Done: compiles; no delete affordance rendered for a built-in character.
    Depends on: Task 68.

72. [x] **Unit test (Robolectric, `createComposeRule`): delete unavailable for built-ins, cap message shown.** `[IMPORT-12]`
    Assert the delete action is absent/disabled for a built-in entry; assert the cap-reached message
    renders when import is attempted at cap.
    Verify: `./gradlew :feature:overlay:testDebugUnitTest --tests "*LibraryScreenTest*"`; confirm
    count in
    `feature/overlay/build/test-results/testDebugUnitTest/TEST-*LibraryScreenTest*.xml`.
    Done: file exists, passes, XML confirms count.
    Depends on: Task 71.

73. [x] **Full PR 5 build check.**
    Verify: `./gradlew :core:data:test :feature:overlay:testDebugUnitTest`; confirm non-zero counts
    across all new `TEST-*.xml` files from Tasks 70, 72.
    Done: build green, XML counts confirm real execution.
    Depends on: Tasks 70, 72.

---

## PR 6 — Switching surface and the reactive `PetOverlayStateHolder` (#39c)

Targets PR 5.1's branch (`feature-branch-chain`). Originally estimated at ~420 changed lines; the
real cost of tasks 74–82 alone was 691, so PR 6 ships as **two chained PRs** under the cached
`auto-chain` delivery strategy. The estimate was wrong for two reasons worth recording: injecting
the built-in fallback name rather than hardcoding it rippled through four existing test files, and
tasks 79–82 uncovered that the PR 4/5.1 import pipeline never persisted the user's confirmed grid
(see PR 6a below), which had to be fixed before the loader could decode any real import.

Largest single rework in the slice (proposal's top risk).

### Delivery split

| PR | Branch | Tasks | Changed lines | Contents |
|----|--------|-------|---------------|----------|
| PR 6a | `feat/slice-2-active-character-and-sheet-loading` | 74–82 | 691 | Active-character repository and character sheet loading. No user-visible behaviour change: the loader exists but nothing renders through it yet. |
| PR 6b | `feat/slice-2-live-character-switching` | 83–92 | est. ~400 | Reactive state holder and renderer rework — the switch the user actually sees. Targets PR 6a's branch. |

The seam is deliberate: 6a is infrastructure that changes nothing on screen, so it can be reviewed
and reverted on its own; 6b is where the pixels move. A revert of 6b alone leaves 6a's loader
unused but harmless.

PR 6a also carries two items outside its literal task text, both recorded in `apply-progress.md`:
`CharacterImporter.confirm()` now writes `manifest.properties` for an imported character (without
it every imported character resolves to `Broken` on reload), and the confirm rollback uses
`deleteRecursively()` so a failure part-way cannot strand a half-built character folder.

### `:core:domain`

74. [x] **Create `ActiveCharacterRepository.kt` (interface).** `[RENDER-3]`
    `interface ActiveCharacterRepository { val active: Flow<CharacterId>; suspend fun
    setActive(id: CharacterId) }`.
    Done: compiles.
    Depends on: PR 4 Task 49.

### `:core:data`

75. [x] **Create `ActiveCharacterRepositoryImpl.kt`.** `[RENDER-3]` `[IMPORT-11]`
    DataStore-backed using `stringPreferencesKey("active_character")` (design decision 8). Falls
    back to a built-in `CharacterId.BuiltIn` when no active pointer is stored, or when deletion
    (Task 76) removes the currently active pointer's target.
    Done: compiles.
    Depends on: Task 74.

76. [x] **Extend `CharacterRepositoryImpl` (PR 5): deleting the active character falls back to a built-in.** `[IMPORT-11]`
    Deleting a character checks whether it is currently active; if so, calls
    `ActiveCharacterRepositoryImpl.setActive(builtInFallback)` before or as part of the same delete
    transaction.
    Done: compiles; deleting a non-active character leaves the active pointer untouched.
    Depends on: Task 75, PR 5 Task 68.

77. [x] **Bind `ActiveCharacterRepositoryImpl` in `core/data/di/BindingsModule.kt`.**
    Done: compiles; resolves from the Hilt graph.
    Depends on: Task 75.

78. [x] **Unit test: deleting the active character falls back to a built-in; deleting a non-active one leaves the pointer untouched.** `[IMPORT-11]`
    `runTest` + temp-file DataStore.
    Verify: `./gradlew :core:data:testDebugUnitTest --tests "*ActiveCharacterRepositoryImplTest*" --rerun-tasks`;
    confirm count in `core/data/build/test-results/testDebugUnitTest/TEST-*ActiveCharacterRepositoryImplTest*.xml`.
    Done: file exists, 4/4 pass, XML confirms count. Corrected post-PR-6-verify (finding C1): the
    original run was un-stubbed plain JUnit, where `Build.VERSION.SDK_INT` reads as `0`, routing
    DataStore's file rename through the legacy `File.renameTo` path — which cannot overwrite an
    existing target on Windows, so every `edit()` past the first failed with a misleading "multiple
    instances of DataStore" `IOException`. Not a production defect: `ActiveCharacterRepositoryImpl`
    and `CharacterRepositoryImpl` were never asked to share more than one live instance. Fixed by
    running the test under `@RunWith(RobolectricTestRunner::class) @Config(sdk = [36])`, matching
    `OverlayPermissionCheckerImplTest`'s existing pattern, which takes DataStore's real
    `Files.move(..., REPLACE_EXISTING)` path.
    Depends on: Task 76.

### `:feature:overlay` — character sheet loading

79. [x] **Create `CharacterAssetSource.kt`.** `[SHEET-1]`
    `fun interface CharacterAssetSource { fun open(animationFileName: String): InputStream? }` —
    `null` return models absence, never an exception.
    Done: compiles.
    Depends on: none.

80. [x] **Create `CharacterSheets.kt`.** `[RENDER-1]` `[RENDER-2]`
    `sealed interface CharacterSheets { data object Loading; data class Ready(val byState:
    Map<PetState, SpriteSheetResult.Loaded>, val idle: SpriteSheetResult.Loaded); data class
    Broken(val failure: SpriteSheetFailure) }`.
    Done: compiles.
    Depends on: Task 79, PR 1 Task 1.

81. [x] **Create `CharacterSheetLoader.kt`: BuiltIn vs. Imported source selection, decode via existing `SpriteSheetDecoder`.** `[SHEET-1]` `[SHEET-2]` `[IMPORT-13]`
    Picks the `CharacterAssetSource` from the `CharacterId` (assets/pet/<name>/ for `BuiltIn`,
    `filesDir/characters/<uuid>/` for `Imported`); decodes each known animation filename through the
    unmodified slice-1 `SpriteSheetDecoder`; a missing optional file is an ordinary absence (simply
    absent from the map); returns `Broken` only when `idle.png` is absent or fails to decode —
    covering the "file deleted outside the app" case.
    Done: compiles; the same loader code path runs for both `BuiltIn` and `Imported` ids (no
    id-type branching beyond source selection), satisfying the "one model, one render path"
    requirement.
    Depends on: Task 79, 80.

82. [x] **Unit test (Robolectric): loader treats a missing optional file as absence, missing `idle.png` as `Broken`, identical path for built-in vs. imported.** `[SHEET-1]` `[SHEET-2]` `[IMPORT-13]`
    Fake `CharacterAssetSource` fixtures for both source types.
    Verify: `./gradlew :feature:overlay:testDebugUnitTest --tests "*CharacterSheetLoaderTest*"`;
    confirm count in
    `feature/overlay/build/test-results/testDebugUnitTest/TEST-*CharacterSheetLoaderTest*.xml`.
    Done: file exists, passes, XML confirms count.
    Depends on: Task 81.

### `:feature:overlay` — reactive state holder and renderer rework

83. [x] **Rework `PetOverlayStateHolder.kt`: stop decode-at-construction, project from `ActiveCharacterRepository`.** `[RENDER-3]`
    `val sheets: StateFlow<CharacterSheets> = activeCharacterRepository.active.mapLatest { id ->
    withContext(io) { sheetLoader.load(id) } }.stateIn(scope, WhileSubscribed(5_000),
    CharacterSheets.Loading)` per `design.md`. `mapLatest` so a fast switch cancels the stale decode.
    Done: compiles; the holder has no `ViewModel` supertype (unchanged rule from slice 1);
    construction no longer performs a decode.
    Depends on: Task 81, Task 77 (active repository binding).

84. [x] **Rework `PetOverlay.kt`: draw the resolved `PetState`'s animation, fall back to IDLE when absent.** `[RENDER-1]`
    Draw frames from `sheets.byState[resolvedState] ?: sheets.idle` when `Ready`; the identity
    affordance draws after `drawImage`, in the same `DrawScope`, so no imported pixel can paint over
    it (`[IMPORT-15]`).
    Done: compiles; no row/state other than the resolved one or the IDLE fallback is referenced by
    the draw call for a given frame.
    Depends on: Task 83, PR 1 Task 6 (resolver `states()` flow).

85. [x] **Extend `PetOverlay.kt`: keep the last `Ready` value visible during a switch; reset `frameIndex` on `Ready` identity change.** `[RENDER-3]`
    Remember the last `Ready` value and keep drawing it while the new one is `Loading`; only `Ready`
    or `Broken` replaces it. Key the frame-index `remember` on the `Ready` identity so a stale index
    never indexes past a new sheet's row (frame counts differ between characters).
    Done: compiles; a `Loading` emission never blanks or flashes the broken-placeholder while a
    prior `Ready` value exists.
    Depends on: Task 84.

86. [x] **Extend `PetOverlay.kt`: switching without app/service relaunch, missing file at load renders visibly-broken.** `[RENDER-3]` `[IMPORT-14]`
    Confirm (structurally, via the reactive `mapLatest` chain) that an `ActiveCharacterRepository
    .setActive` call alone drives a redraw with no service restart; confirm a `Broken` result (idle
    file missing/corrupt) renders the existing broken-placeholder shape, never nothing and never a
    crash.
    Done: compiles; no code path requires an `onDestroy`/`onCreate` cycle to reflect a switch.
    Depends on: Task 85.

87. [x] **Unit test (Robolectric): switching keeps the previous frame until `Ready`, resets frame index on new `Ready` identity.** `[RENDER-3]`
    Fake `ActiveCharacterRepository` emitting two ids with a delayed second load; assert the first
    `Ready` frame stays visible during the second `Loading` window; assert `frameIndex` resets when
    the second `Ready` arrives with a different frame count.
    Verify: `./gradlew :feature:overlay:testDebugUnitTest --tests "*PetOverlayStateHolderTest*"`;
    confirm count in
    `feature/overlay/build/test-results/testDebugUnitTest/TEST-*PetOverlayStateHolderTest*.xml`.
    Done: file exists, passes, XML confirms count.
    Depends on: Task 85.

88. [x] **Unit test (Robolectric): renderer falls back to IDLE when the resolved state's file is absent; renders broken placeholder when idle itself is missing.** `[RENDER-1]` `[IMPORT-14]`
    Two fixtures: a `Ready` sheet with only `idle.png` and a resolved state of e.g. `DRAGGING` →
    draws from `idle`; a `Broken` result → draws the broken placeholder, never blank.
    Verify: `./gradlew :feature:overlay:testDebugUnitTest --tests "*PetOverlayTest*"`; confirm count
    in `feature/overlay/build/test-results/testDebugUnitTest/TEST-*PetOverlayTest*.xml`.
    Done: file exists, passes, XML confirms count.
    Depends on: Task 84.

89. [x] **Wire `LibraryScreen`/switching UI action to `ActiveCharacterRepository.setActive`.** `[IMPORT-7]`
    Selecting a character in the library (or confirming an import) calls `setActive`; built-in and
    imported ids use the identical call.
    Done: compiles; no id-type branching in the call site beyond passing the `CharacterId`.
    Depends on: Task 83, PR 5 Task 71.

90. [x] **Bind `SheetLoader`, `ActiveCharacterRepository` consumers, and any new config in `OverlayModule.kt`.**
    Wire the loader and repository into the Hilt graph consumed by `PetOverlayStateHolder`.
    Done: compiles; `PetOverlayStateHolder` resolves from the graph with no manual construction.
    Depends on: Task 83, 89.

91. [x] **Instrumented test on `emulator-5554` (or best-effort API 34): live re-render after a switch with the service running.** `[RENDER-3]` `[IMPORT-13]`
    Confirms the running service re-renders the new active character without relaunch, matching the
    proposal's Success Criteria. Subject to the same API 37 `InputManager` gap as slice 1 — attempt
    once against API 34, record the result; the real-device manual pass remains primary evidence.
    Verify: `./gradlew :feature:overlay:connectedDebugAndroidTest --tests
    "*CharacterSwitchLiveRenderTest*"` against an API 34 AVD if available; record outcome regardless.
    Done: test written and attempted; outcome recorded.
    Depends on: Task 90.

92. [x] **Full PR 6 build check.**
    Verify: `./gradlew :core:domain:test :core:data:testDebugUnitTest :feature:overlay:testDebugUnitTest --rerun-tasks`;
    confirm non-zero counts across all new `TEST-*.xml` files from Tasks 78, 82, 87, 88.
    Done: build green under a forced `--rerun-tasks`, XML counts confirm real execution. Corrected
    post-PR-6-verify: the original "Done" was recorded against a stale, `UP-TO-DATE` Gradle run that
    never actually executed the tests (see Task 78's corrected note for C1, and the C2 fix below).
    Depends on: Tasks 78, 82, 87, 88, 91.

92a. [x] **Fix: `LaunchedEffect` key omits `ready`, freezing the frame clock on a switch between
    value-equal layouts.** `[RENDER-3]` (PR 6 verify finding C2)
    `SpriteLayout` (`core/domain/.../pet/sprite/SpriteLayout.kt`) is a data class with value
    equality. `PetOverlay.kt`'s `LaunchedEffect(layout, holder.config)` did not relaunch when the
    active character changed to one whose sheet has the same grid/frameCount: the abandoned
    coroutine from the previous `remember(ready)` kept incrementing the old, now-unread
    `MutableIntState`, while the draw lambda read the new one, pinned at frame 0 forever.
    Fixed by adding `ready` to the effect's key set: `LaunchedEffect(ready, layout, holder.config)`.
    `layout` and `holder.config` stay in the key set — a state change on the *same* character still
    legitimately changes `layout` and must still restart the clock.
    Regression test: `PetOverlayFrameClockSwitchTest` (`feature/overlay/src/test/.../ui/`), built
    against two on-disk fixture characters with identical grids/frameCounts. Confirmed failing
    against the pre-fix code (single-key `LaunchedEffect(layout, holder.config)`) and passing
    against the fix, both under a forced `--rerun-tasks` run.
    This project's JVM/Robolectric Compose tests cannot capture real pixels for a live,
    perpetually-rescheduling animation coroutine (`captureToImage` hangs; a manual `View.draw` onto
    a `Bitmap` never executes Compose's real draw path under Robolectric — confirmed empirically
    while building this test, matching the verify report's W4 finding). `ReadyPet` therefore gained
    a test-only `onFrameAdvance: (CharacterSheets.Ready, Int) -> Unit = { _, _ -> }` parameter
    (default no-op — every production call site, including `PetOverlay`'s, is unaffected) and was
    widened from `private` to `internal` so the test can call it directly. This is the one
    deviation from "do not touch production behaviour merely to make a test pass": it adds
    observability, not behaviour, and there was no pixel-based alternative available in this
    environment.
    Verify: `./gradlew :feature:overlay:testDebugUnitTest --tests "*PetOverlayFrameClockSwitchTest*" --rerun-tasks`.
    Done: fails pre-fix, passes post-fix, both confirmed by direct local re-run.
    Depends on: Task 92.

---

## PR 7 — Onboarding screen (#12)

Targets PR 6's branch (`feature-branch-chain`). Est. changed lines: ~300 (of the 800 budget).
Ordering dependency only — independent in content, placed last to spread review load.

### `:feature:overlay`

93. [x] **Create `OverlayOnboardingViewModel.kt`: live re-query on resume, refusal state.** `[ONBOARD-3]` `[ONBOARD-4]`
    On screen resume, re-queries the existing `OverlayPermissionChecker` interface (shipped in #11)
    rather than trusting a locally held boolean; persists a "refused once" flag so future launches
    do not auto-show onboarding.
    Done: compiles; the re-query call site is the mechanics-layer live check, not a cached field.
    Depends on: none (consumes existing #11 interfaces).

94. [x] **Create `OverlayOnboardingScreen.kt`: four required claims, primary action delegates to mechanics layer.** `[ONBOARD-1]` `[ONBOARD-2]`
    States: what appears (small pet drawn over other apps); no read/interact/screen-capture of
    other apps; no data leaves the device; permission is revocable anytime from Settings. Primary
    action calls only the mechanics layer's settings-launch function — no direct `Settings` intent
    constructed by the screen.
    Done: compiles; all four claims present in the composable's text content; grep confirms no
    `Intent(Settings...)` construction inside this file.
    Depends on: Task 93.

95. [x] **Create `ReEntryCard.kt`: passive, dismissible re-entry affordance.** `[ONBOARD-5]`
    Rendered elsewhere in the app after a refusal; re-launches `OverlayOnboardingScreen` on
    interaction; dismissible without re-launching.
    Done: compiles.
    Depends on: Task 93.

96. [x] **Audit onboarding and re-entry copy for dark patterns.** `[ONBOARD-6]`
    Review every string used by Tasks 94–95 for urgency language, misleading claims, or any
    implication the app is unusable without the permission.
    Done: audit note recorded (e.g. in the PR body) confirming none found, or copy revised until
    none exists.
    Depends on: Task 94, 95.

97. [x] **Accessibility pass: content descriptions and 48dp touch targets on every interactive element.** `[ONBOARD-7]`
    Every button/interactive element on `OverlayOnboardingScreen` and `ReEntryCard` gets a content
    description and a minimum 48dp touch target; verify light/dark theme and large font scale
    rendering.
    Done: compiles; each interactive element inspected has both properties.
    Depends on: Task 94, 95.

98. [x] **Unit test (Robolectric, `createComposeRule`): all four claims present; primary action calls mechanics layer exactly once, no direct Settings intent.** `[ONBOARD-1]` `[ONBOARD-2]`
    Verify: `./gradlew :feature:overlay:testDebugUnitTest --tests
    "*OverlayOnboardingScreenTest*"`; confirm count in
    `feature/overlay/build/test-results/testDebugUnitTest/TEST-*OverlayOnboardingScreenTest*.xml`.
    Done: file exists, passes, XML confirms count.
    Depends on: Task 94.

99. [x] **Unit test (Robolectric): re-query on resume advances the flow on grant, leaves the app usable on refusal; no auto-show after one refusal; re-entry affordance re-launches.** `[ONBOARD-3]` `[ONBOARD-4]` `[ONBOARD-5]`
    Fake `OverlayPermissionChecker` returning granted/refused across a simulated resume.
    Verify: `./gradlew :feature:overlay:testDebugUnitTest --tests
    "*OverlayOnboardingViewModelTest*"`; confirm count in
    `feature/overlay/build/test-results/testDebugUnitTest/TEST-*OverlayOnboardingViewModelTest*.xml`.
    Done: file exists, passes, XML confirms count.
    Depends on: Task 93.

100. [x] **Unit test: accessibility properties present on every interactive element.** `[ONBOARD-7]`
    Verify: `./gradlew :feature:overlay:testDebugUnitTest --tests
    "*OverlayOnboardingAccessibilityTest*"`; confirm count in the corresponding `TEST-*.xml`.
    Done: file exists, passes, XML confirms count.
    Depends on: Task 97.

101. [ ] **Attempt the instrumented suite once against an API 34 image for the onboarding screen; record the result.**
    Same per-PR measurement obligation as PR 2/PR 4.
    Verify: `./gradlew :feature:overlay:connectedDebugAndroidTest` against an API 34 AVD.
    Done: result recorded.
    Depends on: Task 94.

102. [ ] **Manual TalkBack pass over the whole onboarding screen; record in the PR.** `[ONBOARD-8]`
    Device or emulator with TalkBack enabled, full pass over `OverlayOnboardingScreen` and
    `ReEntryCard`.
    Done: pass completed and noted in the PR body, per the spec's explicit requirement that this be
    recorded.
    Depends on: Task 97. Requires a device or emulator with TalkBack.
    **DEFERRED to the user's own device session** — this apply environment cannot drive a real
    TalkBack announcement pass interactively. Not fabricated as passing; see `apply-progress.md`
    Work Unit 7, Issue 1.

103. [x] **Full PR 7 build check.**
    Verify: `./gradlew :feature:overlay:testDebugUnitTest`; confirm non-zero counts across all new
    `TEST-*.xml` files from Tasks 98, 99, 100.
    Done: build green, XML counts confirm real execution.
    Depends on: Tasks 98, 99, 100, 101, 102, 96.

---

## Parallelizable groups

- PR 1: Tasks 1, 2, 4, 9 are independent leaf types and can run in parallel; Task 9 (repository
  interface) has no other dependency in this PR and can run alongside all of Tasks 1–8.
- PR 2: Tasks 15 and 16 are independent domain leaves; Task 23 can run parallel to Task 22.
- PR 3: Task 44 (config) is independent of Tasks 41–43 (writer/wiring/startup) until the final
  bind-together task.
- PR 4: Tasks 49, 50, 51 are independent leaf types; Task 60 (fixtures) can run parallel to
  Tasks 53–58 since fixtures depend only on PNG bytes, not on importer code.
- PR 6: Task 79 (asset source) can run parallel to Task 74 (active repository interface); Task 80
  depends on both but not on any repository implementation.
- Everything else is sequential within its PR: each PR's later tasks consume earlier tasks' types,
  and no PR may start before its predecessor branch exists per the design's PR-boundary table.

## Review Workload Forecast

| Field | Value |
|-------|-------|
| Estimated changed lines | ~260 + ~380 + ~240 + ~520 + ~340 + ~420 + ~300 ≈ 2460 total, chained |
| 800-line budget risk | High (combined); each individual PR is under budget |
| Chained PRs recommended | Yes |
| Suggested split | PR 1 → PR 2 → PR 3 → PR 4 → PR 5 → PR 5.1 → PR 6a → PR 6b → PR 7 |
| Delivery strategy | auto-chain |
| Chain strategy | feature-branch-chain |

Decision needed before apply: No
Chained PRs recommended: Yes
Chain strategy: feature-branch-chain
800-line budget risk: High

### Suggested Work Units

| Unit | Goal | Likely PR | Focused test command | Runtime harness | Rollback boundary |
|------|------|-----------|----------------------|-----------------|-------------------|
| 1 | State types, resolver, dwell, DRAGGING + IDLE providers | PR 1 | `./gradlew :core:domain:test --tests "*PetStateResolver*"` | N/A — pure JVM logic, no Android harness needed | `core/domain/.../pet/state/` deleted, `OverlayModule` bindings reverted; no other unit depends on this being present at runtime yet |
| 2 | Drag, throttle, horizontal snap, `onTap`, cap constant | PR 2 | `./gradlew :feature:overlay:testDebugUnitTest --tests "*PetTouchController*"` | `emulator-5554` manual four-edge drag pass (`FLAG_LAYOUT_NO_LIMITS` procedure) | Touch listener detach + `DragStateRepositoryImpl` removal; PR 1 unaffected |
| 3 | Fraction persistence, `PositionWriter`, await-first-read | PR 3 | `./gradlew :core:data:test --tests "*OverlayPositionRepositoryImplTest*"` | `emulator-5554` kill/restart + rotation manual pass | Revert leaves orphaned float keys, reads `null`, falls back to resting corner — the correct first-launch behaviour; must revert before PR 2 per design's migration note |
| 4 | Import pipeline, three tiers, rejections, preview | PR 4 | `./gradlew :feature:overlay:testDebugUnitTest --tests "*CharacterImporterTest*"` | API 34 instrumented attempt for `ImportScreen`/`PreviewScreen`; real-device manual PNG import | Imported files under `filesDir/characters/` persist unread after a revert; no other PR's code path is reachable without this one shipped |
| 5 | Character library, cap, delete | PR 5 | `./gradlew :core:data:test --tests "*CharacterRepositoryImplTest*"` | N/A — DataStore-only logic, Robolectric covers it | Library screen and repository impl removed independently; PR 4's importer still compiles standalone |
| 6 | Active-character switching, reactive `PetOverlayStateHolder` | PR 6 | `./gradlew :feature:overlay:testDebugUnitTest --tests "*PetOverlayStateHolderTest*"` | API 34 instrumented attempt for live-switch; real-device manual switch-while-running pass | Active pointer falls back to built-in on revert; largest single revert but self-contained to the holder/loader/renderer files |
| 7 | Onboarding screen, copy, re-entry card | PR 7 | `./gradlew :feature:overlay:testDebugUnitTest --tests "*OverlayOnboardingScreenTest*"` | Manual TalkBack pass (required by spec, recorded in PR) | Fully independent screen; revert removes onboarding files only, no other unit depends on it |

## PR 5.1 — Declared sprite grid, and the import name step (#69)

Mid-slice scope, decided after PR 5 landed (decisions 13–14 in `design.md`). Targets PR 5's branch
(`feature-branch-chain`). Not part of the original PR count; inserted between PR 5 and PR 6 because
PR 6's `CharacterSheetLoader`/`PetOverlayStateHolder` rework needs a working multi-row grid and a
real character name to build on, per `design.md`'s "why this lands before the switching surface".
Decision 15 (tap-to-cycle gesture) is explicitly **not** part of this unit.

### `:core:domain`

104. [x] **Create `SpriteGridDeclaration.kt`.**
     `data class SpriteGridDeclaration(val columns: Int, val rows: Int)` — the one place both an
     import-time user declaration and a bundled-character manifest describe a grid shape.
     Done: compiles.
     Depends on: none.

105. [x] **Rewrite `SpriteGrid.of`: declared grid, not inferred.** `[SHEET-n]` (delta)
     `SpriteGrid` gains `rows`; `of(widthPx, heightPx, declaration, maxDimensionPx)` checks
     `Oversized` first (unchanged order), then rejects as `NotDivisible` when either axis doesn't
     divide evenly by the declared count *or* the resulting cell isn't square — never a clamp, never
     a truncation. A square sheet with a declared 1x1/1-row guess is no longer silently accepted as
     one frame; a correctly declared 6x6 is valid.
     Done: compiles; existing `Oversized`-before-`NotDivisible` order test still passes.
     Depends on: Task 104.

106. [x] **Extend `SpriteLayout`: `rows`, `cellTopPx`, frame order left-to-right then top-to-bottom.**
     `frameCount` bound becomes `0..grid.columns * grid.rows`; `cellLeftPx`/`cellTopPx` derive
     `col = frame % columns`, `row = frame / columns`.
     Done: compiles; a frame index past the last column wraps to row 1's first cell.
     Depends on: Task 105.

107. [x] **Unit test: a square sheet is never accepted as a one-frame animation.** `[SHEET-n]`
     The specific regression this PR exists to close — must fail against the pre-PR `SpriteGrid.of`.
     Verify: `./gradlew :core:domain:test --tests "*SpriteGridTest*"`; confirm count in
     `core/domain/build/test-results/test/TEST-*SpriteGridTest*.xml`.
     Done: file exists, passes, XML confirms count.
     Depends on: Task 105.

108. [x] **Unit test: a declared 6x6 grid over a 36-frame sheet is valid and expressible.** `[SHEET-n]`
     A 6-column-wide, 6-row sheet — impossible to express under the old one-row contract — validates
     and its `frameCount` can reach 36.
     Verify: same command as Task 107; confirm XML count.
     Done: passes, XML confirms count.
     Depends on: Task 105.

109. [x] **Create `CharacterName.kt`.** `[IMPORT-n]` (delta)
     `data class CharacterName(val value: String)` with `companion fun validOrNull(raw: String?):
     CharacterName?` — trims, `null`/blank stays `null`, never a fabricated default or an empty
     string standing in for "no name" (mirrors `OverlayPositionFraction.validOrNull`).
     Done: compiles; no case returns a non-null value for blank/whitespace-only input.
     Depends on: none.

110. [x] **Create `CharacterManifestFailure.kt`.**
     `sealed interface CharacterManifestFailure { data object Missing; data class Malformed(reason:
     String) }` — a bundled character with no manifest is one of these, never a guessed grid.
     Done: compiles.
     Depends on: none.

111. [x] **Modify `Character.kt`: `name: CharacterName?` replaces the fabricated-string `displayName`.**
     Done: compiles; no call site can construct a `Character` with an invented name string.
     Depends on: Task 109.

112. [x] **Modify `CharacterRepository.kt`: persist the full `Character`, not just the id.**
     `val importedCharacters: Flow<List<Character>>`; `suspend fun add(character: Character)`.
     Done: compiles.
     Depends on: Task 111.

### `:core:data`

113. [x] **Modify `CharacterRepositoryImpl.kt`: persist name per uuid alongside the id set.**
     One `stringPreferencesKey("character_name_<uuid>")` per imported character, read through
     `CharacterName.validOrNull` — an absent or blank stored name reads back `null`, never `""`.
     `remove` also clears that key.
     Done: compiles; `importedCharacters` emits real `CharacterName?` values, not a fixed literal.
     Depends on: Task 112.

114. [x] **Unit test: name persists, absence stays absent, remove clears the name key.**
     Verify: `./gradlew :core:data:testDebugUnitTest --tests "*CharacterRepositoryImplTest*"`;
     confirm count in `core/data/build/test-results/test/TEST-*CharacterRepositoryImplTest*.xml`.
     Done: file exists, passes, XML confirms count.
     Depends on: Task 113.

### `:feature:overlay` — decoder and bundled manifest

115. [x] **Modify `SpriteSheetDecoder.kt`: `validateBounds`/`decode` take a `SpriteGridDeclaration`.**
     Done: compiles; the decoder never derives a grid on its own.
     Depends on: Task 105.

116. [x] **Modify `TransparentCellScanner.kt`: scan trailing frames in row-major order.**
     The last frame index (`columns * rows - 1`) is the scan's starting point, not `columns - 1`.
     Done: compiles.
     Depends on: Task 106.

117. [x] **Create `BuiltInCharacterManifestReader.kt`.**
     Reads `assets/pet/<name>/manifest.properties` (`columns`/`rows` keys); returns
     `CharacterManifestFailure.Missing` when the asset doesn't exist, `.Malformed` when the keys are
     absent or non-positive, else a valid `SpriteGridDeclaration`.
     Done: compiles.
     Depends on: Task 104, 110.

118. [x] **Add `feature/overlay/src/main/assets/pet/default/manifest.properties`.**
     `columns=6`, `rows=1` — the existing bundled sheet's real shape (2046x341, cell 341, matching
     the pre-PR inferred value exactly, so the shipped asset still decodes identically).
     Done: `default` decodes through the declared path with the same visible result as before.
     Depends on: Task 117.

119. [x] **Modify `PetOverlayStateHolder.kt`: decode `default` through its manifest, not inference.**
     Reads the manifest via `BuiltInCharacterManifestReader`; a manifest failure collapses to
     `SpriteSheetFailure.Undecodable` at this one call site (documented deviation — the typed
     `CharacterManifestFailure` propagates properly once `CharacterSheetLoader` replaces this class
     in the next PR, out of scope here).
     Done: compiles; existing rendering tests unaffected.
     Depends on: Task 118.

120. [x] **Unit test: bundled manifest reader — missing file, malformed keys, valid grid.**
     Verify: `./gradlew :feature:overlay:testDebugUnitTest --tests
     "*BuiltInCharacterManifestReaderTest*"`; confirm count in
     `feature/overlay/build/test-results/testDebugUnitTest/TEST-*BuiltInCharacterManifestReaderTest*.xml`.
     Done: file exists, passes, XML confirms count.
     Depends on: Task 117.

### `:feature:overlay` — import pipeline: declared grid + name capture

121. [x] **Modify `CharacterImporter.kt`: `stage` proposes a detected candidate grid, never validates one.**
     Tier 1 unchanged. The new bounds-only check after staging rejects only `Oversized` (dimension
     bound, no grid needed yet); `StagedImport` carries the measured `widthPx`/`heightPx` and a
     best-effort `SpriteGridDeclaration` candidate (`rows = 1`, `columns = widthPx / heightPx`) for
     the preview screen to pre-fill and the user to correct.
     Done: compiles; no `NotDivisible`/square-cell rejection happens before a declaration exists.
     Depends on: Task 115.

122. [x] **Modify `CharacterImporter.kt`: `decodeAndScan` validates the caller-supplied declaration.**
     Takes `declaration: SpriteGridDeclaration`; `SpriteGrid.of` runs against it before the full
     decode, exactly as tier 2 did before, just against a declared rather than inferred grid.
     Done: compiles; a declaration that doesn't divide the image, or isn't square, is
     `NotDivisible`, never clamped or truncated.
     Depends on: Task 121.

123. [x] **Modify `CharacterImporter.kt`: `confirm` takes the captured `CharacterName?`.**
     The move into `filesDir/characters/<uuid>/idle.png` is unchanged; `confirm` now returns the
     `CharacterId.Imported` for the caller to build a `Character` and call
     `CharacterRepository.add` — closing the previously-open gap where a confirmed import was never
     added to the repository at all.
     Done: compiles; a `confirm` call site that never calls `repository.add` no longer exists.
     Depends on: Task 122, Task 112.

124. [x] **Modify `CharacterImportController.kt`: add a `GridEntry` state between staging and decode.**
     `Idle → Staging → GridEntry(candidate) → DecodingAndScanning → Rejected|Ready`. A new
     `onGridConfirmed(declaration)` call re-enters `decodeAndScan` with the user's (possibly
     corrected) declaration.
     Done: compiles; `decodeAndScan` is unreachable before a declaration exists, satisfying "the grid
     is declared, not inferred" at the UI boundary too.
     Depends on: Task 122.

125. [x] **Modify `ImportScreen.kt`: render `GridEntry` — editable columns/rows, pre-filled with the candidate.**
     Two numeric fields seeded from the detected candidate; a "Preview" action calls
     `controller.onGridConfirmed`.
     Done: compiles; the fields are pre-filled, not blank, and are user-editable.
     Depends on: Task 124.

126. [x] **Modify `PreviewScreen.kt`: capture the name; animate across the full declared grid.**
     Adds a name text field (no pre-filled default — see Task 109); playback now advances through
     `layout.grid.columns * layout.grid.rows` frames using `cellLeftPx`/`cellTopPx`, not just
     `columns`. Confirm calls `importer.confirm(...)` then, on success,
     `repository.add(Character(id, CharacterName.validOrNull(nameText)))`.
     Done: compiles; an empty name field confirms successfully and persists `name = null`, never a
     fabricated string.
     Depends on: Task 123, Task 124.

127. [x] **Modify `LibraryScreen.kt`: render the real name; an absent name renders an honest placeholder.**
     `character.name?.value ?: stringResource(R.string.character_unnamed)` — the placeholder string
     names the absence explicitly ("Unnamed"), never reusing the old fixed "Imported character"
     string as if it were a real name.
     Done: compiles; two imported characters with different supplied names render different text.
     Depends on: Task 126.

128. [x] **Add `character_unnamed` string resource; update `import_rejection_not_divisible` wording for a declared (not inferred) grid.**
     Done: strings present; `NoGenericRejectionStringTest` still finds no generic catch-all phrase.
     Depends on: none.

129. [x] **Update existing tests for the new signatures: `SpriteSheetDecoderTest`,
     `TransparentCellScannerTest`, `SpriteFixtures` (multi-row fixture), `CharacterImporterTest`,
     `PreviewScreenTest`, `LibraryScreenTest`, `CharacterRepositoryImplTest`.**
     Every test that constructed a `SpriteGrid`/called `decode`/`validateBounds` without a
     declaration, or asserted the old fixed "Imported character" string, is updated to the declared
     contract.
     Verify: `./gradlew :core:domain:test :core:data:testDebugUnitTest
     :feature:overlay:testDebugUnitTest`; confirm non-zero counts across all touched `TEST-*.xml`
     files.
     Done: build green, no regression to any prior work unit's suite.
     Depends on: Tasks 105–128.

130. [x] **Full PR 5.1 build check.**
     Verify: `./gradlew :core:domain:test :core:data:testDebugUnitTest
     :feature:overlay:testDebugUnitTest`; confirm non-zero counts across every new/modified
     `TEST-*.xml` file from Tasks 107, 108, 114, 120, 129.
     Done: build green, XML counts confirm real execution.
     Depends on: Task 129.

---

## Out of scope (no tasks written for these, per instruction)

TYPING, HAPPY, SLEEPING, HUNGRY providers and `CelebrationTracker` (slices 3–5); quick menu, IME,
cross-fade/squash animation, cutout avoidance, multi-display, Play UGC classification confirmation
(flagged, not resolved here — a Play Console action, not code), and permission mechanics (#11,
already shipped, only consumed by PR 7).
