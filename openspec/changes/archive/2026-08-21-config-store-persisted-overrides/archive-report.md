# Archive Report: config-store-persisted-overrides

**Change**: config-store-persisted-overrides (persisted, validated config override store backing BalanceConfig and PetAnimationConfig, issue #91)  
**Archived**: 2026-08-21  
**Status**: Complete and verified. All three PRs (#121, #122, #123) merged to master. CI gate green. Real device verified. Change is ready for delivery.

## Final State Authority and Status Resolution

This archive report records the final state of the change AT CLOSE, after all implementation and verification work. The launch prompt's explicit final-state facts outrank intermediate snapshots per the Final-State Authority hierarchy.

### Terminal Delivery Facts

1. **All three PRs implemented, verified, and merged to master**:
   - PR #121 (`feat(config): domain vocabulary and pure resolution logic`) — Phase 1, `:core:domain` only
   - PR #122 (`feat(config): back BalanceConfig with the persisted override store`) — Phase 2, `:core:data` store implementation + BalanceConfig source
   - PR #123 (`feat(config): animate PetAnimationConfig through persisted overrides`) — Phase 2 continued, `:feature:overlay` animation source wiring
   - Merge commit on master: `3ca8841`
   - All three PRs chained in dependency order, all merged

2. **Verification status**:
   - **Branch verification**: Each slice independently verified green with the full CI command:
     - `./gradlew assembleDebug testDebugUnitTest :core:domain:test assembleDebugAndroidTest lintDebug --rerun-tasks`
     - Result: BUILD SUCCESSFUL, zero UP-TO-DATE (confirming --rerun-tasks ran every task)
   - **Device verification** (Redmi Note 14 Pro, HyperOS, API 36):
     - Cold start with cleared app data: no crash observed
     - Foreground service started successfully
     - `APPLICATION_OVERLAY` window verified to exist
     - Pet animation measured frame-by-frame: six raw screencaps diffed to detect pixel-region changes, proving animation was active and responsive
     - This ruled out the DataStore-read-in-provider blocking the cold start
   - **Conclusion**: All implementation delivered and verified on real hardware. The change is ready for archive and delivery.

3. **Master CI gate status**:
   - Current head: `3ca8841` (merge commit of PR #123)
   - Full gate command: `./gradlew assembleDebug testDebugUnitTest :core:domain:test assembleDebugAndroidTest lintDebug --rerun-tasks`
   - Result: BUILD SUCCESSFUL
   - All new tests passing

### Final Task Completion

All 23 implementation tasks marked complete (`[x]` in tasks.md):
- Phase 1 (PR #121): 14 tasks ✓
- Phase 2 (PR #122, #123): 9 tasks ✓

No unchecked implementation tasks remain. Per tasks.md, every task delivered is present in the code and merged.

## Tracked Deviations Recorded

Two intentional scope deviations are recorded in this archive per the proposal and design documents:

### 1. Only BalanceConfig and PetAnimationConfig are routed through the store

**Recorded in**: `proposal.md` § "Tracked deviation — reduced scope", `design.md` decision 5

**Status**: This is exactly what shipped. The four snapshot configs (`PetStateConfig`, `CharacterLibraryConfig`, `OverlayPositionConfig`, `QuickMenuConfig`) keep their bare providers.

**Why**: These two are the configs with live consumers and the ones a tuning session actually judges. The snapshot four have no second reader — an override there is read once at service start and proves nothing a JVM test has not already proven. Including them adds line count without adding proof, in a change already forecast near the review budget.

**Follow-up debt**: Issue #91's criterion "no provider returns a bare compile-time constant any more" is **not met**, deliberately. The follow-up slice owes: the four snapshot configs resolved from the same store, their `OverlayModule` providers rewired, their validation ranges declared, and documentation that each takes effect on the next service start.

### 2. The instrumented test tier was dropped

**Recorded in**: `proposal.md` § "Revised review-workload forecast", `design.md` § "Tracked deviation — the instrumented test tier is dropped"

**Status**: This is exactly what shipped. Three originally-proposed instrumented test cases moved to Robolectric with temp-file DataStore.

**Coverage**: Two of three are genuinely covered:
- Round trip through the real store: write, read back, out-of-range write returns `OutOfRange` and leaves the store byte-identical ✓
- Corrupt read (`IOException`) yields the complete shipped-default object (never zero or partial) ✓

**Deliberate gap**: The third case — an observable config change reaching a **running** overlay (a live `TYPE_APPLICATION_OVERLAY` window behind a foreground service) — is satisfied by inference, not observation.
- Robolectric proves the flow emits a new config object
- Device verification above proves resolution works and the pet animates on real hardware
- Device verification did not prove propagation of an override change reaching a running overlay, because no override can be written until the tuning panel (#92) exists
- This gap is therefore **open and known**. Future maintenance: if a live config change fails to propagate, this gap should be suspected before a resolver bug. Restoring the single overlay-liveness instrumented test is the cheapest remedy.

## Specs Merged to Source of Truth

One delta spec merged into main spec store:

| Domain | Action | Requirements | Scenarios | Details |
|--------|--------|--------------|-----------|---------|
| `config-override-store` | Created | 10 | 48 | Per-field nullable overrides, reset-as-delete, absence-to-default resolution, validation-rejects-never-clamps, version staleness per group, observable sources, no whole-config write API |

Main spec created:
- `openspec/specs/config-override-store/spec.md` ✓ (copied mechanically from delta spec, no Read→Write truncation)

## Verification Checklist

- [x] All 23 tasks complete in tasks.md
- [x] No unchecked implementation tasks
- [x] CI gate green on master (3ca8841): BUILD SUCCESSFUL, --rerun-tasks confirmed all tasks executed
- [x] Device verification green: cold start no crash, foreground service started, overlay window verified, animation measured and responsive
- [x] Delta spec copied mechanically to main spec store (config-override-store/spec.md)
- [x] Change folder moved to archive (2026-08-21-config-store-persisted-overrides)
- [x] Archive contains all artifacts: proposal, design, tasks, exploration, specs/
- [x] Active changes directory no longer has this change
- [x] Archive report generated

## Audit Trail

**Spec merge verification**: Delta spec copied mechanically with `cp`; diff -r verified empty (no truncation or alteration).

**Archive move verification**: `git mv` succeeded; source verified gone; archived folder verified present with all original content; diff -r verified empty against pre-move snapshot.

## Key Findings for Next Reader

1. **The seam is proven but only half-exercised**: Decision 5 (`:feature:overlay` depending only on `:core:domain` interface, with Hilt resolving to the `:core:data` impl in the app graph) is correctly implemented. The follow-up slice can inherit this proven path for the three snapshot configs without re-engineering.

2. **The gap is known and isolated**: Propagation of an override to a running overlay is inference, not observation. No instrumented test exists for this. If it breaks, check the flow emission first (covered by Robolectric), then the overlay-window lifecycle interaction (not covered).

3. **Out-of-range rejection is structural**: Three defences, all in place:
   - API shape: `set` takes one field only, no whole-config entry point
   - One `edit` call site: every write routes through the same helper
   - Source-scanning tests: `ConfigStoreNoBulkWriteCodeTest`, `ConfigStoreNoZeroSubstitutionCodeTest`, golden key test

4. **Absence is protected**: `emptyPreferences()` on failed read means every key is absent, and absent resolves to shipped default by definition. A corrupt stored value reads as `null`, which is the same absent branch. No code path produces a partial or zeroed config.

5. **Staleness is per-group**: `BalanceConfig.version` is 1. `PetAnimationConfig` has no version (`currentVersion = null`). A balance revision never flags an animation override as stale.

## SDD Cycle Status

✓ Proposal: Accepted  
✓ Specification: 1 spec (config-override-store, merged to main)  
✓ Design: Documented (with tracked deviations recorded above)  
✓ Implementation: 3 PRs, all merged to master  
✓ Verification: Green on branch, green on master head, device verified  
✓ Archive: Complete  

**Ready for delivery**.
