# Design: A persisted, validated store behind the injected configuration (#91)

## Technical approach

Four seams, in dependency order.

1. **A typed field descriptor** (`ConfigField<T>`) in `:core:domain` carries a field's frozen storage
   key, its shipped default, its valid range, and its override group. It is the single object every
   reader, writer and validator agrees on.
2. **A pure resolution function** over `(descriptor, stored override) -> Resolved<T>`. No Android, no
   DataStore, no coroutines — the whole matrix (absent, present, out-of-range, stale) is a JVM test.
3. **One generic `ConfigOverrideStore`** interface in `:core:domain`, one Preferences-DataStore
   implementation in `:core:data`, `@Binds` in `BindingsModule`. Its write API takes exactly one
   field, so there is no whole-config entry point to misuse.
4. **Two config sources** that fold resolved fields into a whole config object and expose it as a
   `StateFlow`. Pure domain functions keep taking a plain snapshot; only providers change.

Scope is `BalanceConfig` and `PetAnimationConfig` only. The four snapshot configs keep their bare
providers — the tracked deviation recorded in the proposal.

## Architecture decisions

| # | Decision | Choice | Rejected | Rationale |
|---|---|---|---|---|
| 1 | **Store shape** (open question 1) | **One generic store keyed by a typed `ConfigField<T>` descriptor.** `suspend fun <T> set(field: ConfigField<T>, value: T): ConfigWriteResult`, `fun <T> override(field: ConfigField<T>): Flow<StoredOverride<T>>`, `suspend fun <T> reset(field: ConfigField<T>)` | One typed store per config class (`BalanceConfigStore`, `PetAnimationConfigStore`) | Type safety is not lost: the descriptor's `T` is what makes `set` accept only the field's own type, so the call site is as safe as a per-class store. What differs is enumeration and surface. #92's panel iterates a **field registry**, one list of descriptors, rather than switching over N store classes — and N is 2 now and 6 after the follow-up slice, so a per-class shape triples the interface/impl/`@Binds` count for no new guarantee. The typed rejection reason is also expressible once (`ConfigWriteResult.OutOfRange`) instead of once per store. Review surface: one interface + one impl now, unchanged later |
| 1a | Descriptor is a **closed** hierarchy | `sealed class ConfigField<T>` with exactly `IntField`, `LongField`, `DoubleField` subclasses | An open generic `ConfigField<T>` with a supplied codec | Preferences keys are typed. A closed set makes the implementation's mapping an exhaustive `when` with no reflection, no codec plumbing, and no way to add a type without the compiler pointing at the impl |
| 2 | **How ranges are declared** (open question 2) | **A `companion object` in the config class's own file**, one `val` descriptor per overridable field, declared immediately below the constructor, each with a one-line KDoc pointing at the field's KDoc rather than duplicating it | A sibling top-level `BalanceConfigFields` object; per-field annotations (`@IntRange`); a range table in the store | Kotlin cannot attach metadata to a `data class val` without annotation processing, and `@IntRange` is an `androidx` import forbidden in `:core:domain`. A sibling object splits the range from the field across files and lets the two drift silently. The companion is the closest a plain Kotlin file gets to "beside the field": same file, same class, adjacent lines, and it matches the repo's KDoc-heavy style with zero new machinery. The descriptor is also the *only* way to reach the store, so "declared once, enforced through the same path" is structural, not a convention |
| 2a | `PetAnimationConfig`'s descriptors live beside `PetAnimationConfig` | In `:feature:overlay`, using the `:core:domain` `ConfigField` vocabulary | Moving `PetAnimationConfig` into `:core:domain` | The proposal's "in the domain" is about the **enforcement path**, not the file's module. Moving the class is an architecture change beyond #91. The vocabulary and the validating code path are domain-owned; only the literal range values sit next to the class they bound. This is also what proves the seam (decision 5) |
| 3 | Staleness | Per **override group**, and it records the exact version written. One extra key per group: `config_override.balance.written_under_version` | A per-field version stamp; one global stamp | Settled by the maintainer. Per-field triples the key count to express a fact that is identical across a group; global would flag an animation override on a balance revision. `PetAnimationConfig` declares **no** version notion, so its group carries no stamp and is never stale — "each config group carries its own staleness notion, or none" |
| 4 | Rejection | `sealed interface ConfigWriteResult { data object Accepted; data class OutOfRange<T>(val key: String, val min: T, val max: T, val offending: T) }` | Throwing; returning `Boolean`; returning a message string | Typed data, no display copy — #92 owns wording and needs the numbers back without parsing a string. Not an exception, because an out-of-range write from a tuning panel is an expected outcome, not a defect |
| 5 | The `:feature:overlay` seam | `PetAnimationConfigSource` is a plain `@Singleton @Inject` class **in `:feature:overlay`** depending only on the `:core:domain` `ConfigOverrideStore` interface. Hilt resolves it to the `:core:data` impl in the app graph | A main-source `:feature:overlay -> :core:data` dependency | Forbidden by the proposal. This is the load-bearing part: the follow-up slice inherits a *proven* path for the three other overlay-module configs, not an untested one |
| 6 | Observability without pure signature changes | `BalanceConfigSource` is an **interface in `:core:domain`** exposing `val config: StateFlow<BalanceConfig>`; impl in `:core:data`; `@Binds`. `ObserveHunger` and `CreateOneOffTask` keep their exact constructors and are built by two tiny pure factory classes taking `(deps)` and applying `(BalanceConfig)` per call | Passing the flow into the use cases; re-providing a snapshot per injection | `PetOverlayStateHolder` lives in `:feature:overlay` and must reach the balance flow, so the *source* has to be a domain interface too — same seam as decision 5. The state holder does `configSource.config.flatMapLatest { factory(it).invoke() }`, so a `dailyTaskGoal` change re-derives Hunger live. A snapshot provider cannot do this: consumers hold the constructed use case, so the value would freeze at injection |
| 7 | Corrupt / failed read | `dataStore.data.catch { if (it is IOException) emit(emptyPreferences()) else throw it }`. Every field is resolved independently through `resolve`, and the config object is constructed **only** from resolve outputs | A try/catch returning a cached or partially built config | `emptyPreferences()` means every key is absent, and absent resolves to the shipped default by definition — so a corrupt store yields the complete shipped config, field for field, with no code path that can produce a partial one. A wrong-typed stored value also reads as `null` from `Preferences`, which is the same absent branch |
| 8 | Key namespace and rename | Frozen string on the descriptor: `config_override.<group>.<field_snake_case>`. Renaming the Kotlin property does **not** change it. A deliberate key rename adds `previousKeys: List<String>` to the descriptor, read as an ordered fallback and removed inside the same `edit` as the next write | Deriving the key from the property name; silent rename | Deriving it makes a refactor rename silently lose every user's override. The explicit-legacy-key shape mirrors `OverlayPositionRepositoryImpl`'s documented non-migration exactly. A golden code test pins the full key set, so an accidental rename fails a test instead of a device |

## How eager persistence is made structurally impossible

Three defences, none of them vigilance.

1. **API shape.** `ConfigOverrideStore` has no method taking a collection, a `vararg`, or a config
   object. `set` takes one `ConfigField<T>` and one `T`. `reset(field)` calls `preferences.remove(key)`
   — never a write of the current default.
2. **One `edit` call site.** The implementation has exactly one private
   `edit { it[key] = value; it[versionKey] = ... }` helper. Every public method routes through it.
3. **`ConfigStoreNoBulkWriteCodeTest`** — a source-walking test in the
   `QuickMenuNoPersistenceCodeTest` shape, scanning `core/data/.../config/`,
   `core/domain/.../config/` and the two config files:
   - `dataStore.edit` appears exactly once in `:core:data`'s config package;
   - `putAll`, `preferences.clear()`, `Preferences.Pair` appear nowhere;
   - `BalanceConfig(` and `PetAnimationConfig(` never appear inside a write path;
   - the domain interface source contains no `List<`, `Map<`, `vararg`, or `Config)` in any
     `suspend fun set`/`save` signature.
4. **`ConfigStoreNoZeroSubstitutionCodeTest`** — `?: 0`, `?: 0L`, `?: 0.0`, `.orZero`,
   `getOrDefault` appear nowhere in either config package. Absence must reach `resolve`, not a literal.

## Data flow

```
[read]   DataStore.data ──catch(IOException)→ emptyPreferences()      decision 7
             │
             ├─ preferences[field.key]  → StoredOverride.Absent | Present(value, writtenUnder)
             │
             └─► resolve(field, stored)                    [:core:domain, pure, no Android]
                     Absent                 → Resolved(default,  DEFAULT)
                     Present, in range      → Resolved(value,    OVERRIDE, stale?)
                     Present, out of range  → Resolved(default,  DEFAULT_RANGE_NARROWED)
                          │
                          └─► BalanceConfigSourceImpl folds ALL fields → BalanceConfig
                                  → StateFlow<BalanceConfig>            decision 6

[use]    PetOverlayStateHolder: config.flatMapLatest { ObserveHungerFactory(it)() }
         DataModule:            CreateOneOffTaskFactory(source.config.value)()
         ObserveHunger / CreateOneOffTask constructors: UNCHANGED

[write]  #92 (later) ─► store.set(BalanceConfig.DAILY_TASK_GOAL, 12)
                            in range  → edit { [key]=12; [balance.written_under_version]=1 } → Accepted
                            out       → no edit at all                → OutOfRange(key,min,max,12)

[reset]  store.reset(field) ─► edit { remove(key) }        never a default rewrite
```

## Interfaces

```kotlin
// :core:domain/config/ConfigField.kt — pure, no android.*, no datastore
sealed class ConfigField<T : Comparable<T>>(
    /** Frozen storage key. NEVER derived from the property name — see design decision 8. */
    val key: String,
    val group: ConfigGroup,
    val shippedDefault: T,
    val min: T,
    val max: T,
    val previousKeys: List<String> = emptyList(),
) {
    class IntField(...) : ConfigField<Int>(...)
    class LongField(...) : ConfigField<Long>(...)
    class DoubleField(...) : ConfigField<Double>(...)
}

/** A group is the staleness unit (decision 3). `version = null` means the group has no staleness notion. */
data class ConfigGroup(val id: String, val currentVersion: Int?)

sealed interface StoredOverride<out T> {
    data object Absent : StoredOverride<Nothing>
    data class Present<T>(val value: T, val writtenUnderVersion: Int?) : StoredOverride<T>
}

enum class ResolutionSource { SHIPPED_DEFAULT, OVERRIDE, SHIPPED_DEFAULT_RANGE_NARROWED }
data class Resolved<T>(val value: T, val source: ResolutionSource, val staleFrom: Int?)

/** Total. Never returns zero, never throws, never touches Android. */
fun <T : Comparable<T>> resolve(field: ConfigField<T>, stored: StoredOverride<T>): Resolved<T>

// :core:domain/config/ConfigOverrideStore.kt
interface ConfigOverrideStore {
    fun <T : Comparable<T>> override(field: ConfigField<T>): Flow<StoredOverride<T>>
    suspend fun <T : Comparable<T>> set(field: ConfigField<T>, value: T): ConfigWriteResult
    suspend fun <T : Comparable<T>> reset(field: ConfigField<T>)   // removes the entry
}

sealed interface ConfigWriteResult {
    data object Accepted : ConfigWriteResult
    data class OutOfRange<T>(val key: String, val min: T, val max: T, val offending: T) : ConfigWriteResult
}

// :core:domain/config/BalanceConfigSource.kt  (decision 6 — interface in domain so :feature:overlay may read it)
interface BalanceConfigSource { val config: StateFlow<BalanceConfig> }

// :core:domain/balance/BalanceConfig.kt — decision 2, ranges beside the fields
data class BalanceConfig(/* fields unchanged */) {
    companion object {
        val GROUP = ConfigGroup("balance", currentVersion = 1)
        /** Range for [dailyTaskGoal]; a goal of 0 would make Hunger divide by nothing. */
        val DAILY_TASK_GOAL = ConfigField.IntField("config_override.balance.daily_task_goal", GROUP, 10, 1, 100)
        /** Range for [hungryThresholdRatio]; see that field's KDoc for the semantics. */
        val HUNGRY_THRESHOLD_RATIO = ConfigField.DoubleField("config_override.balance.hungry_threshold_ratio", GROUP, 0.6, 0.0, 1.0)
        // ... one per overridable field; `version` is NOT overridable
        val ALL: List<ConfigField<*>> = listOf(...)   // #92's registry (decision 1)
    }
}
```

`PetAnimationConfig` gains the same companion, with `ConfigGroup("pet_animation", currentVersion = null)`.

## File changes

| Path | Action | Purpose |
|---|---|---|
| `core/domain/.../config/ConfigField.kt` | Create | Descriptor, group, `StoredOverride` (decisions 1, 1a, 3, 8) |
| `core/domain/.../config/ConfigResolution.kt` | Create | Pure `resolve`, `Resolved`, `ResolutionSource` (decision 7) |
| `core/domain/.../config/ConfigOverrideStore.kt` | Create | Store interface + `ConfigWriteResult` (decisions 1, 4) |
| `core/domain/.../config/BalanceConfigSource.kt` | Create | Observable source interface (decision 6) |
| `core/domain/.../balance/BalanceConfig.kt` | Modify | Companion descriptors; `version` becomes load-bearing |
| `core/domain/.../balance/ObserveHungerFactory.kt` | Create | Pure `(BalanceConfig) -> ObserveHunger`; `ObserveHunger` untouched |
| `core/domain/.../task/CreateOneOffTaskFactory.kt` | Create | Same shape for task creation |
| `core/data/.../config/PreferencesConfigOverrideStore.kt` | Create | Single `edit` helper, exhaustive key mapping, `catch → emptyPreferences()` |
| `core/data/.../config/BalanceConfigSourceImpl.kt` | Create | Folds resolved fields into a `StateFlow<BalanceConfig>` |
| `core/data/.../di/BindingsModule.kt` | Modify | `@Binds` store + `BalanceConfigSource` |
| `core/data/.../di/DataModule.kt` | Modify | `provideBalanceConfig` reads `source.config.value`; two factory providers |
| `feature/overlay/.../ui/PetAnimationConfig.kt` | Modify | Companion descriptors, no version group |
| `feature/overlay/.../ui/PetAnimationConfigSource.kt` | Create | `@Singleton` class in `:feature:overlay`, depends on the domain interface only (decision 5) |
| `feature/overlay/.../di/OverlayModule.kt` | Modify | **Only** `providePetAnimationConfig`; the other four providers and all 14 `const val` untouched |
| `feature/overlay/.../ui/PetOverlayStateHolder.kt` | Modify | `flatMapLatest` over the balance flow and the animation flow |
| `core/data/.../di/BalanceConfigInjectionTest.kt` | Modify | See below |
| `core/data/src/test/.../config/*CodeTest.kt` | Create | The two guard rails above |

### What `BalanceConfigInjectionTest` becomes

It currently asserts `BalanceConfig() == entryPoint.balanceConfig()`. That is now a claim about an
**empty store**, not about the provider, so it is split into three tests over the same Hilt graph with
a `@TestInstallIn` module replacing `providePreferencesDataStore` with a temp-file instance:

1. *Empty store yields exactly the shipped defaults* — the original assertion, with its precondition
   now explicit in the name and the store proven empty first.
2. *One override changes one field* — write `DAILY_TASK_GOAL`, assert that field changed and every
   other field is still its shipped default. This is the proposal's "case that matters most".
3. *A corrupt store yields the complete shipped defaults* — the whole object equals `BalanceConfig()`,
   asserted as one equality so a partially-zeroed config cannot pass.

## Testing strategy

| Layer | What | How |
|---|---|---|
| Unit `:core:domain` (pure JVM) | `resolve` total over the matrix: absent → default; present in range → override; present out of range → default + `RANGE_NARROWED`; present with an older group version → value kept + `staleFrom` set; boundary values at `min` and `max` **accepted**, one step outside **rejected**, per field type | JUnit4, no Android, no Robolectric |
| Unit `:core:domain` (pure JVM) | `resolve` never returns zero for any `StoredOverride` input, including a `Present(0, …)` outside range; both factory classes produce use cases whose behaviour tracks the config passed | JUnit4 |
| Unit `:core:domain` (pure JVM) | Every descriptor in `BalanceConfig.ALL` and `PetAnimationConfig.ALL` has `min <= shippedDefault <= max`, a unique key, and a key matching `config_override\.[a-z_]+\.[a-z_]+` | JUnit4 over the registry |
| Code test (source scan) | `ConfigStoreNoBulkWriteCodeTest`, `ConfigStoreNoZeroSubstitutionCodeTest`, and a **frozen-key golden** test pinning the exact key strings (decision 8) | `File.walkTopDown()`, `QuickMenuNoPersistenceCodeTest` shape |
| Code test (source scan) | `feature/overlay/build.gradle.kts` main-source deps contain no `:core:data`, only `testImplementation` does (decision 5) | Text scan of the build file |
| Robolectric + Hilt `:core:data` | The three `BalanceConfigInjectionTest` cases above, plus: reset **removes** the key (assert absence in the raw `Preferences`, not just the resolved default) | `HiltAndroidRule` + `@TestInstallIn` temp-file DataStore, sdk 36 |
| Robolectric `:core:data` | Round trip through the real store: write, read back, out-of-range write returns `OutOfRange` and leaves the store byte-identical; observability — collecting `config` sees the new value after a `set` with no re-injection | `runTest` + temp-file DataStore |
| Robolectric `:feature:overlay` | `PetAnimationConfigSource` resolves through an in-memory fake `ConfigOverrideStore` (domain interface, so no `:core:data` import); a change emits a new `PetAnimationConfig` | `runTest` + fake |
| Manual, per device | That an animation-pace change is visible without restarting the service. Not closable by the pipeline | Device pass |

**Not instrumented (`androidTest`).** Every case above is reachable under Robolectric with a temp-file
DataStore; an `androidTest` variant would assert the same thing more slowly on a device where the
proposal's real risks (eager writes, zero substitution) are structural and already caught by source
scans. The proposal's "instrumented round trip" is satisfied by the Robolectric round-trip rows.

## Threat matrix

| Boundary | Applicable | Expected behavior | RED test |
|---|---|---|---|
| Untrusted value crossing the store's write boundary | **Applicable** — `set` is the only mutation entry point and #92 will drive it from user input | Out of range is **rejected with a typed reason and no `edit` call at all** — never clamped, never partially written. `NaN`/infinite `Double` fails the range comparison and is rejected on the same path | Boundary and one-step-outside cases per field type; a rejected write leaves the raw `Preferences` unchanged |
| Corrupt / unreadable persisted state | **Applicable** — a truncated `petmephone_prefs` file | Complete shipped defaults, asserted as whole-object equality; never zero, never partial | The third `BalanceConfigInjectionTest` case |
| Routing, shell, subprocess, VCS/PR automation, executable-file classification, process integration | N/A | No such boundary exists in this change | — |

## Migration / rollout

No migration. Absence is the initial state on every existing install, so a first launch after this
change reads exactly today's values. Rollback per the proposal: the two provider bodies return to
their bare form and the persisted keys become inert. Frozen keys (decision 8) mean a re-apply after a
revert resumes on the same overrides.

## Revised changed-line estimate (code only)

| Component | Estimate |
|---|---|
| `ConfigField` + `ConfigGroup` + `StoredOverride` (KDoc-heavy) | 70–95 |
| `resolve` + `Resolved` + `ResolutionSource` | 45–65 |
| `ConfigOverrideStore` + `ConfigWriteResult` + `BalanceConfigSource` | 45–60 |
| Companion descriptors on the two config classes | 45–65 |
| `PreferencesConfigOverrideStore` | 90–130 |
| `BalanceConfigSourceImpl` + `PetAnimationConfigSource` | 60–85 |
| Two pure factory classes | 20–30 |
| Provider rewiring (`DataModule`, `BindingsModule`, one `OverlayModule` provider, `PetOverlayStateHolder`) | 45–70 |
| Guard-rail code tests (3) | 70–110 |
| Pure JVM tests (matrix, boundaries, staleness, registry invariants) | 150–210 |
| Robolectric tests (`BalanceConfigInjectionTest` split, round trip, reset, overlay source) | 130–190 |
| **Total** | **~770–1,110** |

**800-line budget risk: High.** Dropping the instrumented tier removed ~80–120 lines against the
proposal's forecast, and decision 1's single generic store removed a second interface/impl/`@Binds`
triple, but the top of the range still overshoots. The split the proposal names holds exactly on
this design's seams:

- **Slice A** — `:core:domain` only: `ConfigField`, `resolve`, `ConfigOverrideStore`,
  `BalanceConfigSource`, the two companions, the two factories, and every pure JVM test.
  **~375–525 lines.** Ships green with no consumer: nothing is wired yet.
- **Slice B** — `:core:data` + `:feature:overlay`: the DataStore implementation, both sources,
  provider rewiring, the guard-rail code tests, and the Robolectric tier. **~395–585 lines.**

## Open questions

- [ ] `min`/`max` for each of the eight overridable fields have no product reference. They are chosen
      here to exclude values that break an invariant (`dailyTaskGoal >= 1`, `minFrameIntervalMillis`
      at or above one 60 Hz frame), not to express a taste. #92's first real tuning session is the
      first evidence any of them is right.
- [ ] `ConfigField<T : Comparable<T>>` covers ordered scalars only. `CharacterLibraryConfig`'s
      `builtInFallbackName: String` is a set-membership constraint, not a range — the follow-up slice
      must extend the sealed hierarchy with an `EnumeratedField`, not stretch `min`/`max` onto it.
- [ ] Whether `PetOverlayStateHolder`'s `flatMapLatest` over the balance flow re-subscribes the task
      repository query on every config change is unmeasured. It is correct either way; it may be
      wasteful if a tuning panel writes on every slider tick, which is #92's debounce to own.

## Tracked deviation — the instrumented test tier is dropped

The proposal listed three instrumented cases: a real persisted round trip, a corrupt store resolving to
defaults, and an observable config change reaching a running overlay with no restart. This design moves
all three under Robolectric with a temp-file DataStore, and the maintainer accepted that in full.

Two of the three are genuinely covered. A round trip and a corrupt read are persistence behaviour, and a
temp-file DataStore under Robolectric exercises the same code path a device would.

The third is a deliberate, accepted gap. Issue #91 asks for an observable config change reaching a
**running** overlay, which is a live `TYPE_APPLICATION_OVERLAY` window behind a foreground service.
Robolectric proves the flow emits; it does not prove the pet on screen changed pace. That criterion is
therefore satisfied by inference rather than by observation, and this repository has a standing lesson
that launching is not verifying.

Recorded here so that a later failure of a live config change is diagnosed against a known gap rather
than treated as a surprise. Restoring the single overlay-liveness instrumented test is the cheapest
remedy if that gap ever bites.
