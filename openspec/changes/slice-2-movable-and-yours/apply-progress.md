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
14/14 tasks in Work Unit 1 (PR 1) complete. Work units 2-7 (PR 2-7) not started, per assigned scope. Ready for `sdd-verify` on this work unit, or for the next `sdd-apply` batch to begin Work Unit 2.
