# Tasks: A persisted, validated store behind the injected configuration (#91)

## Review Workload Forecast

| Field | Value |
|-------|-------|
| Estimated changed lines (code only, docs excluded) | ~1,055 total — Slice A ~460, Slice B ~595 |
| 400-line budget risk | High for both slices individually if not split further; each slice alone is close to or over 400 |
| 800-line budget risk | High — the change as a whole (~1,055) exceeds the 800-line budget even after dropping the instrumented tier |
| Chained PRs recommended | Yes — required. The total exceeds the budget, and Slice A/Slice B are a natural, dependency-ordered seam: Slice A ships green with no consumer wired, Slice B wires the consumers |
| Decision needed before apply | Yes — confirm the two-PR chain (Slice A → Slice B) before `sdd-apply` begins |
| PR boundary | PR 1 = Phase 1 (tasks 1.1–1.14, `:core:domain` only). PR 2 = Phase 2 (tasks 2.1–2.23, `:core:data` + `:feature:overlay`), targeting PR 1's branch |

### Suggested Work Units

| Unit | Goal | Likely PR | Focused test command | Runtime harness | Rollback boundary |
|------|------|-----------|----------------------|-----------------|-------------------|
| 1 | Descriptors, pure resolution, store/source interfaces, range companions on both configs, two pure factories, all pure JVM tests | PR 1 | `./gradlew :core:domain:test --rerun-tasks` | N/A — pure JVM | Revert `core/domain/.../config/`, `BalanceConfig.kt`, and `feature/overlay/.../ui/PetAnimationConfig.kt` companion diffs |
| 2 | DataStore-backed store impl, observable sources, `@Binds`/provider rewiring, guard-rail + golden code tests, Robolectric tier | PR 2 | `./gradlew :core:data:testDebugUnitTest :feature:overlay:testDebugUnitTest --rerun-tasks` | Robolectric, temp-file DataStore | Revert `core/data/.../config/`, `feature/overlay/.../ui/PetAnimationConfigSource.kt`, `BindingsModule.kt`, `DataModule.kt`, `OverlayModule.kt`, `PetOverlayStateHolder.kt` diffs |

## Phase 1: `:core:domain` — descriptors, resolution, ranges, factories (PR 1)

- [ ] 1.1 RED: write `ConfigFieldTest` — the sealed hierarchy exposes exactly `IntField`, `LongField`,
      `DoubleField`; a descriptor's `T` constrains `set`/`override`/`reset` call sites at compile
      time (no reflection); `ConfigGroup(id, currentVersion)` with `currentVersion = null` means no
      staleness notion. (Satisfies "Each field's valid range is declared once, in the domain, beside
      the field" and design decisions 1, 1a.)
- [ ] 1.2 GREEN: create `core/domain/.../config/ConfigField.kt` — `ConfigField<T : Comparable<T>>`
      sealed class with `key`, `group`, `shippedDefault`, `min`, `max`, `previousKeys`; `ConfigGroup`;
      `StoredOverride<T>` (`Absent`, `Present(value, writtenUnderVersion)`). No `android.*` import.
      (Design decisions 1, 1a, 3, 8.)
- [ ] 1.3 RED: write `ConfigResolutionTest` — total over the matrix: absent → `Resolved(default,
      SHIPPED_DEFAULT, null)`; present in range → `Resolved(value, OVERRIDE, staleFrom?)`; present
      out of range → `Resolved(default, SHIPPED_DEFAULT_RANGE_NARROWED, null)`; present with an older
      group version → value kept and `staleFrom` set to the recorded version; present with the
      current version → `staleFrom = null`; boundary values at `min` and `max` accepted, one step
      outside rejected, per field type (`Int`, `Long`, `Double`, including `NaN`/infinite `Double`).
      `resolve` never returns zero for any input, including `Present(0, …)` outside range. (Satisfies
      "Resolution follows a two-state contract per field", "An override survives an unrelated
      shipped-default change", "Absence never resolves to zero", "Validation rejects an out-of-range
      write", "Version staleness is tracked per override group" — the pure half of each.)
- [ ] 1.4 GREEN: create `core/domain/.../config/ConfigResolution.kt` — `Resolved<T>`,
      `ResolutionSource`, `fun <T : Comparable<T>> resolve(field: ConfigField<T>, stored:
      StoredOverride<T>): Resolved<T>`. Pure, no Android, no coroutines. (Design decision 7's pure
      half.)
- [ ] 1.5 RED: write `ConfigWriteResultTest` — `OutOfRange` carries `key`, `min`, `max`, `offending`
      typed to the field's `T`, not a string; a compile-level check that no store method signature
      accepts a collection, `vararg`, or a whole config object. (Satisfies "Validation rejects an
      out-of-range write; nothing is clamped and nothing is persisted" — the typed-reason half; "No
      whole-config write exists" — the interface-shape half.)
- [ ] 1.6 GREEN: create `core/domain/.../config/ConfigOverrideStore.kt` — `ConfigOverrideStore`
      interface (`override`, `set`, `reset`, each taking exactly one `ConfigField<T>`) and
      `ConfigWriteResult` (`Accepted`, `OutOfRange<T>`). (Design decisions 1, 4.)
- [ ] 1.7 GREEN: create `core/domain/.../config/BalanceConfigSource.kt` — `interface
      BalanceConfigSource { val config: StateFlow<BalanceConfig> }`, in `:core:domain` so
      `:feature:overlay` can depend on it without a `:core:data` main-source dependency. (Satisfies
      "Both BalanceConfig and PetAnimationConfig are observable without a restart" — the seam half;
      design decisions 5, 6.)
- [ ] 1.8 RED: extend `BalanceConfigTest` — every descriptor in `BalanceConfig.ALL` has `min <=
      shippedDefault <= max`, a key matching `config_override\.balance\.[a-z_]+`, and no duplicate
      keys within `ALL`; `version` is not itself an overridable field. (Satisfies "Each field's valid
      range is declared once, in the domain, beside the field"; design decision 2.)
- [ ] 1.9 GREEN: modify `core/domain/.../balance/BalanceConfig.kt` — add `companion object { val
      GROUP = ConfigGroup("balance", currentVersion = 1); val DAILY_TASK_GOAL = ...; ...; val ALL:
      List<ConfigField<*>> }`, one descriptor per overridable field, each with a one-line KDoc
      pointing at the field's own KDoc. `version` becomes load-bearing for staleness. Field values
      and the class's constructor are otherwise unchanged. (Design decision 2.)
- [ ] 1.10 RED: extend `PetAnimationConfigTest` (`:feature:overlay`) — same registry-invariant
      assertions as 1.8, applied to `PetAnimationConfig.ALL`; `GROUP.currentVersion == null`.
      (Design decision 2a.)
- [ ] 1.11 GREEN: modify `feature/overlay/.../ui/PetAnimationConfig.kt` — companion descriptors using
      the `:core:domain` `ConfigField` vocabulary; `ConfigGroup("pet_animation", currentVersion =
      null)`. No new main-source dependency is introduced by this task — `ConfigField` already lives
      in `:core:domain`, which `:feature:overlay` already depends on. (Design decision 2a.)
- [ ] 1.12 RED: write `ObserveHungerFactoryTest` and `CreateOneOffTaskFactoryTest` — each factory,
      given a `BalanceConfig`, builds a use case whose behavior tracks the fields of that config (e.g.
      a different `dailyTaskGoal` changes `ObserveHunger`'s computed ratio); `ObserveHunger`'s and
      `CreateOneOffTask`'s own constructors are asserted unchanged by this change (still take their
      original non-config dependencies). (Satisfies "Both BalanceConfig and PetAnimationConfig are
      observable without a restart" — the "pure functions keep taking a plain snapshot parameter"
      scenario; design decision 6.)
- [ ] 1.13 GREEN: create `core/domain/.../balance/ObserveHungerFactory.kt` and
      `core/domain/.../task/CreateOneOffTaskFactory.kt` — each a pure `(deps) -> (BalanceConfig) ->
      UseCase` factory. `ObserveHunger` and `CreateOneOffTask` themselves are not modified.
- [ ] 1.14 Run `./gradlew :core:domain:test --rerun-tasks`. No consumer is wired yet; `:core:data` and
      `:feature:overlay` main source are untouched by this phase except for 1.11's companion, which
      adds no new dependency edge.

## Phase 2: `:core:data` + `:feature:overlay` — persistence, wiring, guard rails (PR 2, depends on PR 1)

- [ ] 2.1 RED: write `PreferencesConfigOverrideStoreTest` (Robolectric, temp-file DataStore) — `set`
      with an in-range value persists and `override(field)` emits `Present`; `set` with an
      out-of-range value returns `OutOfRange(key, min, max, offending)` and leaves the raw
      `Preferences` byte-identical to before the call; `reset` removes the key (assert absence in the
      raw `Preferences`, not just the resolved default); a rejected write on a field that already
      holds a valid override leaves that override unchanged. (Satisfies "Validation rejects an
      out-of-range write", "Reset deletes the entry, never rewrites the current default as a value",
      "No whole-config write exists" — the persisted half of each.)
- [ ] 2.2 GREEN: create `core/data/.../config/PreferencesConfigOverrideStore.kt` — implements
      `ConfigOverrideStore` over the existing `petmephone_prefs` `DataStore<Preferences>` instance;
      exactly one private `edit { ... }` helper that every public method routes through;
      `dataStore.data.catch { if (it is IOException) emit(emptyPreferences()) else throw it }`.
      (Design decisions 7, 8; the "one `edit` call site" defence.)
- [ ] 2.3 RED: write `ConfigKeysGoldenTest` — asserts the exact frozen key string for every descriptor
      in `BalanceConfig.ALL` and `PetAnimationConfig.ALL` against a hardcoded expected list. Fails
      loudly if a key changes for any reason, including an IDE rename of the Kotlin property. (Design
      decision 8 — "a golden test pins the full key set".)
- [ ] 2.4 GREEN: confirm 2.3 passes against 1.9/1.11's committed keys; no production change expected.
      If it fails, the key was derived incorrectly — fix the descriptor, never the test's expected
      value, unless the change is a deliberate rename (which also requires `previousKeys`).
- [ ] 2.5 RED: write `ConfigStoreNoBulkWriteCodeTest` (source scan, `File.walkTopDown()`, the
      `QuickMenuNoPersistenceCodeTest` shape) over `core/data/.../config/`, `core/domain/.../config/`,
      and the two config class files — `dataStore.edit` appears exactly once in `:core:data`'s config
      package; `putAll`, `preferences.clear()`, `Preferences.Pair` appear nowhere; `BalanceConfig(`
      and `PetAnimationConfig(` never appear inside a write path; the domain interface source
      contains no `List<`, `Map<`, `vararg`, or `Config)` in any `suspend fun set`/`save` signature.
      (Satisfies "No whole-config write exists; writing one field never touches another" —
      structurally, not by convention.)
- [ ] 2.6 GREEN: confirm 2.5 passes against 2.2's implementation; adjust the implementation, never the
      test, if it fails.
- [ ] 2.7 RED: write `ConfigStoreNoZeroSubstitutionCodeTest` (source scan, same shape) — `?: 0`, `?:
      0L`, `?: 0.0`, `.orZero`, `getOrDefault` appear nowhere in either config package. (Satisfies
      "Absence never resolves to zero, and never to a partially-zeroed config" — structurally.)
- [ ] 2.8 GREEN: confirm 2.7 passes against 2.2's implementation.
- [ ] 2.9 RED: write `BalanceConfigSourceImplTest` (Robolectric, temp-file DataStore) — folding every
      field's `resolve` output into a `BalanceConfig` yields the shipped-default object when the
      store is empty; a corrupt/failing read (`IOException`) yields the complete shipped-default
      object asserted as one whole-object equality, never a partially-zeroed one; collecting `config`
      observes a new value after a `set`, with no re-injection. (Satisfies "Absence never resolves to
      zero" — the corrupt-read scenario; "Both BalanceConfig and PetAnimationConfig are observable
      without a restart" — the BalanceConfig scenario.)
- [ ] 2.10 GREEN: create `core/data/.../config/BalanceConfigSourceImpl.kt` — implements
      `BalanceConfigSource`, folds `resolve` over every `BalanceConfig.ALL` descriptor plus the store,
      exposes `StateFlow<BalanceConfig>` built only from `resolve` outputs (design decision 7 — no
      code path can construct a partial config).
- [ ] 2.11 GREEN: modify `core/data/.../di/BindingsModule.kt` — `@Binds` `ConfigOverrideStore` to
      `PreferencesConfigOverrideStore`, `@Binds` `BalanceConfigSource` to `BalanceConfigSourceImpl`.
- [ ] 2.12 RED: write/extend `BalanceConfigInjectionTest` scaffolding — a `@TestInstallIn` module
      replacing `providePreferencesDataStore` with a temp-file instance is available to the whole
      test class.
- [ ] 2.13 GREEN: modify `core/data/.../di/DataModule.kt` — `provideBalanceConfig()` now reads
      `balanceConfigSource.config.value`; add providers for `ObserveHunger` and `CreateOneOffTask`
      built through `ObserveHungerFactory`/`CreateOneOffTaskFactory` against the current config value.
      `ObserveHunger`'s and `CreateOneOffTask`'s own constructors remain unchanged. (Design decision
      6.)
- [ ] 2.14 GREEN: rewrite `core/data/.../di/BalanceConfigInjectionTest.kt` into three cases over the
      2.12 Hilt graph: (a) *empty store yields exactly the shipped defaults* — the store proven empty
      first, then `entryPoint.balanceConfig() == BalanceConfig()`; (b) *one override changes one
      field* — write `DAILY_TASK_GOAL`, assert that field changed and every other field is still its
      shipped default; (c) *a corrupt store yields the complete shipped defaults* — asserted as one
      whole-object equality. (Satisfies "A field with an entry resolves to the entry's value", the
      proposal's "case that matters most", and "Absence never resolves to zero" — through the
      injected Hilt graph, not the source impl in isolation.)
- [ ] 2.15 RED: write `PetAnimationConfigSourceTest` (Robolectric, `:feature:overlay`) — resolves
      `PetAnimationConfig` through an in-memory fake `ConfigOverrideStore` (the `:core:domain`
      interface only, so the test needs no `:core:data` import); a fake `set` on a field emits a new
      `PetAnimationConfig` from `config`.
- [ ] 2.16 GREEN: create `feature/overlay/.../ui/PetAnimationConfigSource.kt` — a plain `@Singleton
      @Inject` class in `:feature:overlay` depending only on the `:core:domain` `ConfigOverrideStore`
      interface; Hilt resolves the injected instance to the `:core:data` impl in the app graph.
      (Design decision 5 — the load-bearing seam; satisfies "No main-source `:feature:overlay ->
      :core:data` dependency".)
- [ ] 2.17 GREEN: modify `feature/overlay/.../di/OverlayModule.kt` — only `providePetAnimationConfig`
      is rewired through `PetAnimationConfigSource`; the other four providers and all 14 `const val`
      declarations are untouched.
- [ ] 2.18 RED: write/extend `PetOverlayStateHolderTest` — a `BalanceConfig.dailyTaskGoal` change
      reaches the observed Hunger value live, without reconstructing the state holder; a
      `PetAnimationConfig` change is likewise reflected without a restart.
- [ ] 2.19 GREEN: modify `feature/overlay/.../ui/PetOverlayStateHolder.kt` —
      `configSource.config.flatMapLatest { ObserveHungerFactory(it)() }` for the balance flow, and the
      equivalent `flatMapLatest` for the animation flow. (Satisfies "Both BalanceConfig and
      PetAnimationConfig are observable without a restart" — the consumer-visible half; "Pure domain
      functions keep taking a plain snapshot parameter" is upheld because `ObserveHunger`'s
      constructor is untouched.)
- [ ] 2.20 RED: write a text-scan test (or extend an existing build-file guard) asserting
      `feature/overlay/build.gradle.kts` main-source dependencies contain no `:core:data`; only a
      `testImplementation` entry for `:core:data` is present. (Design decision 5's structural
      enforcement; satisfies "No main-source `:feature:overlay -> :core:data` dependency" for the
      build graph itself, not just the source it compiles.)
- [ ] 2.21 GREEN: confirm 2.20 passes; add the `testImplementation(project(":core:data"))` line to
      `feature/overlay/build.gradle.kts` if `PetAnimationConfigSourceTest`'s fake needs no `:core:data`
      type at all, this task is a no-op confirmation only.
- [ ] 2.22 Run `./gradlew :core:data:testDebugUnitTest :feature:overlay:testDebugUnitTest
      --rerun-tasks`.
- [ ] 2.23 Run `./gradlew assembleDebug testDebugUnitTest :core:domain:test lintDebug --stacktrace
      --rerun-tasks` (the real CI gate command, per the repo's standing lesson that a partial command
      is not the gate).

## Not in scope for this task list

No UI task exists here — #92's tuning panel is a separate change. No task adds a new tunable field.
No task migrates the existing `private const val` declarations into a constants object. No task
routes `PetStateConfig`, `CharacterLibraryConfig`, `OverlayPositionConfig`, or `QuickMenuConfig`
through the store; they keep their current bare providers, per the tracked deviation in
`proposal.md` and `design.md`.
