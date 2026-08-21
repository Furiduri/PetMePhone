# Exploration: config-store-persisted-overrides

**Issue**: #91 — feat(config): a persisted, validated store behind the injected configuration
**Phase**: sdd-explore
**Status**: done — ready for proposal

## Current State

### Config classes

| Config | File | Fields | Defaults source |
| --- | --- | --- | --- |
| `BalanceConfig` | `core/domain/.../balance/BalanceConfig.kt` | `dailyTaskGoal: Int = 10`, `hungryThresholdRatio: Double = 0.6`, `recurringHungerRatio: Int = 3`, `recurringHungerCap: Int = 4`, `standardTaskPoints: Int = 1`, `version: Int = 1` | Kotlin default parameters |
| `PetStateConfig` | `core/domain/.../pet/state/PetStateConfig.kt` | `minimumDwellMillis: Long` | `OverlayModule.PET_STATE_MINIMUM_DWELL_MILLIS = 400L` |
| `CharacterLibraryConfig` | `core/domain/.../character/CharacterLibraryConfig.kt` | `maxImportedCharacters: Int`, `maxImportBytes: Long`, `builtInFallbackName: String` | `OverlayModule` consts (`10`, 10MB, `"default"`) |
| `PetAnimationConfig` | `feature/overlay/.../ui/PetAnimationConfig.kt` | `frameIntervalMillis: Long`, `minFrameIntervalMillis: Long`, `stateSharingTimeoutMillis: Long` | `OverlayModule` consts (`150L`, `16L`, `5_000L`) |
| `OverlayPositionConfig` | `feature/overlay/.../position/OverlayPositionConfig.kt` | `firstReadTimeoutMillis: Long` | `OverlayModule` const `500L` |
| `QuickMenuConfig` | `feature/overlay/.../quickmenu/QuickMenuConfig.kt` | `cardWidthDp: Int`, `maxCardHeightDp: Int`, `gapDp: Int`, `taskTitleMaxLength: Int`, `inputContentMinHeightDp: Int` | `OverlayModule` consts (`280`, `420`, `8`, `140`, `120`) |

`OverlayModule.kt` holds **14** `private const val` declarations, not the thirteen the issue states. The
difference is immaterial to scope, but the count in the issue should not be trusted as an inventory.

`BalanceConfig()` is provided bare in `DataModule.kt` (`:core:data`). Its KDoc already anticipates this
change: a future DataStore-backed override changes only that function's body, no call site.

`BalanceConfig.version` is declared and documented but **nothing reads it today** — it is pure metadata.
This issue makes it load-bearing.

### Module graph

- `:core:domain` — pure JVM, no Android, no Hilt, no DataStore. Only `kotlinx-coroutines-core`.
- `:core:data` — depends on `:core:domain`. Already declares `androidx.datastore.preferences` and Room,
  and already provides a `DataStore<Preferences>` instance (`petmephone_prefs`) in `DataModule.kt`.
- `:feature:overlay` — depends on `:core:domain` and `:core:designsystem` in **main** source. It depends
  on `:core:data` only in **test** source. This is the key structural obstacle: three of the six configs
  live here and have no main-source path to the DataStore instance.
- `:app` — depends on all of the above and hosts `@HiltAndroidApp`. Hilt aggregates
  `@InstallIn(SingletonComponent::class)` modules across the whole classpath reachable from `:app`.

### Established patterns

- **Interface-in-domain / impl-in-data / `@Binds`-in-`BindingsModule`** is the repo's shape for every
  stateful capability (`OverlayPositionRepository`, `ActiveCharacterRepository`, `TaskRepository`).
  This is the natural seam for a config-override store, and it avoids a new `:feature:overlay → :core:data`
  main-source dependency.
- **Persistence**: `androidx-datastore-preferences` v1.2.1 is already declared. Proto DataStore is not
  declared anywhere. Room exists but is used for task entities only.
- **Tests**: pure JVM tests under `src/test/kotlin` mirroring the main package structure; Hilt-graph tests
  use `@HiltAndroidTest` + `HiltAndroidRule` + Robolectric (sdk 36).
- **Code tests**: `QuickMenuNoPersistenceCodeTest.kt` and siblings walk a source directory with
  `File.walkTopDown()` and assert forbidden string literals are absent from a package. This is the repo's
  mechanism for encoding an architectural *negative* constraint as a running test. It is a strong
  candidate for enforcing this issue's eager-write and absence-never-zero guard rails.

## Affected Areas

- `BalanceConfig.kt` — per-field validation ranges declared beside each field; `version` becomes load-bearing.
- `PetStateConfig.kt`, `CharacterLibraryConfig.kt` — snapshot configs; still overridable; must document
  that they take effect on next service start.
- `PetAnimationConfig.kt` — must become observable.
- `OverlayPositionConfig.kt`, `QuickMenuConfig.kt` — snapshot.
- `OverlayModule.kt` — every `@Provides` returning a bare `const val`-backed config resolves through the
  store instead. The `const val` declarations stay where they are as shipped defaults; the issue forbids
  migrating them into a new constants object.
- `DataModule.kt` — `provideBalanceConfig()` routes through the store; likely hosts the implementation.
- **New**: a config-override store interface in `:core:domain`, implementation in `:core:data`, bound in
  `BindingsModule.kt`.
- `BalanceConfigInjectionTest.kt` — currently asserts bare defaults resolve through Hilt; that stays true
  only when no override is stored, so it needs updating.
- **New** JVM tests for the resolution matrix and **new** instrumented tests for the persisted round trip.

## Approaches

| # | Approach | Effort | Verdict |
| --- | --- | --- | --- |
| 1 | Preferences DataStore, sparse key per field, pure resolution function in `:core:domain` | Medium | **Recommended** |
| 2 | Proto DataStore, one message per config, optional fields | High | Rejected |
| 3 | Room table, one row per override key | Medium-High | Rejected |

**1 — Preferences DataStore, sparse keys.** Reuse the existing `petmephone_prefs` instance. One
`Preferences.Key<T>` per overridable field under a namespaced name. A pure
`resolve(stored, default, range)` function in `:core:domain`. Sparse absence maps naturally onto "no key
present", which is exactly the nullable-override semantics the issue demands. Cost: keys are
stringly-typed, so a rename must be handled deliberately.

**2 — Proto DataStore.** Stronger typing and has-field absence semantics for free, but it is a new
dependency plus a protobuf Gradle plugin and a codegen step across six config classes. Too much surface
for a change the issue deliberately scopes to the store and nothing else.

**3 — Room.** Reuses an existing dependency and gives transactional writes, but SQL and migrations are a
heavy mechanism for a sparse key-value need that Preferences DataStore already serves in this same module.

### Open design decisions carried into propose/design

- **Store shape**: a single generic store keyed by field name, or one typed store per config class.
- **Validation ranges**: sibling declaration object versus inline metadata beside each field.
- **Version staleness**: stored once per override group, or once per field. This changes the resolution
  function's signature.

### Observability without rewriting pure call sites

`BalanceConfig` is already passed as a plain constructor parameter into pure functions (`CreateOneOffTask`,
`ObserveHunger`). Making it observable means the **provider** exposes a `StateFlow<BalanceConfig>` for
reactive consumers, while pure domain functions keep taking a plain snapshot. The reactive layer reads the
current value off the flow and passes it in. No pure signature changes.

## Risks

- **Eager persistence of all fields** — the issue's own dominant risk. DataStore's `edit` block operates
  over the whole `Preferences` map, so a naive "save the whole config object" API is an easy trap.
- **Module-graph friction** — if design reaches for a direct `:feature:overlay → :core:data` dependency
  instead of the domain-interface seam, that is a real architecture change beyond this issue's framing and
  must be flagged, not added silently.
- **Version staleness shape undecided** — affects the resolution signature.
- **Absence resolving to zero** — easy to violate in a generic get-or-default helper that substitutes `0`
  on a deserialization failure. Needs an explicit corrupt-read test.
- **Scope creep** — no tuning-panel UI (#92), no new tunable fields, no constants-object migration.

## Review Workload Forecast

| Area | Estimate (code only) |
| --- | --- |
| Domain store interface + resolution/validation logic | 120–180 |
| Validation ranges beside six configs' fields | 60–100 |
| DataStore-backed implementation | 150–220 |
| Provider rewiring in `OverlayModule` and `DataModule` | 80–120 |
| Structural code tests for the guard rails | 40–70 |
| Pure JVM tests (resolution matrix, reset, staleness, boundaries) | 200–300 |
| Instrumented tests (round trip, corrupt store, observable) | 100–150 |
| **Total** | **~750–1,140** |

**Likely exceeds the 800-line review budget.** A natural split is: slice A — domain store interface,
validation ranges, resolution logic and pure tests; slice B — DataStore implementation, provider rewiring
and instrumented tests.

## Ready for Proposal

Yes, with the three open design decisions above carried forward explicitly.
