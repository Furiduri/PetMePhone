# Proposal: Slice 1 Foundation — module skeleton, convention plugins, catalog, Hilt graph

## Intent

The repository is still an unmodified Android Studio Empty Activity template. Nothing in the
architecture PetMePhone agreed on is expressible yet: the domain layer has no compiler-enforced
isolation from the Android framework, build configuration has no single owner, the version catalog
holds only template leftovers, and no runtime entry point can be constructed with its dependencies.

Issues #1, #2, #3 and #6 buy exactly one thing: a project where the architecture rules are enforced
by the build rather than by discipline. `:core:domain` cannot see Android because Gradle refuses;
six modules cannot drift apart on `minSdk` because one `ProjectConfig` owns it; a foreground
service that holds no state can still receive its collaborators because the Hilt graph exists
before the first component that needs it.

## Scope

### In Scope

| # | Deliverable |
|---|---|
| 1 | Remove template sources via `git rm`; six-module graph (`:app`, `:core:domain`, `:core:data`, `:core:designsystem`, `:feature:overlay`, `:feature:tasks`); explicit `namespace`, `resourcePrefix`, five `gradle.properties` flags. Module scripts deliberately duplicated. |
| 2 | `build-logic` included build with six convention plugins; `ProjectConfig`; module scripts reduced to `plugins {}` + `dependencies {}`. |
| 3 | Complete `libs.versions.toml` (Hilt, Room, KSP, WorkManager, DataStore, Lottie, `dmfs:lib-recur`, Compose BOM, test libs), `compose-ui` / `compose-test` bundles, test source sets per module with zero tests. |
| 6 | `@HiltAndroidApp PetMePhoneApplication` with `Configuration.Provider` + `HiltWorkerFactory`; default `WorkManagerInitializer` removed from the merged manifest; `@Module @InstallIn(SingletonComponent::class)` bindings in `:core:data`; placeholder `@HiltWorker` proven to execute. |

### Out of Scope

- **#11** overlay permission mechanics, **#13** `PetOverlayService`, **#14** `ComposeOverlayHost`,
  **#36** sprite renderer — all deferred. They sit behind **#9** (foreground service type), which
  the exploration narrowed to three options but could not close: the deciding evidence is a device
  test of overlay survival across screen-off, Doze and OEM battery managers.
- Build hardening (signing, R8, `.gitignore`, `allowBackup`) → #4. Konsist architecture test → #5.
  CI → #7. Any application, UI or feature code.
- Gradle TestKit functional tests for the convention plugins — a recorded decision in #2, not an
  omission.

## Capabilities

### New Capabilities

- `build-foundation`: module graph boundaries, convention-plugin ownership of shared build values,
  version-catalog alignment rules, and test-source-set layout.
- `dependency-injection`: Hilt object graph shape, component/scope rules, and WorkManager factory
  wiring.

### Modified Capabilities

None. `openspec/specs/` is empty; this is the first capability set.

## Approach

**Module graph first, abstraction second.** #1 stands up six compiling modules with deliberately
duplicated build scripts. That duplication is the concrete motivation #2 then removes — no
pre-optimization.

**`build-logic` as an included build, not `buildSrc`,** because a `buildSrc` change invalidates the
whole build's configuration. Required mechanics, per #2:

- `pluginManagement { includeBuild("build-logic") }` is the **first** block in root
  `settings.gradle.kts`, before `dependencyResolutionManagement`. Wrong ordering surfaces as
  `Plugin [id: 'com.petmephone.android.compose'] was not found`.
- `build-logic/settings.gradle.kts` declares its own `libs` catalog via
  `from(files("../gradle/libs.versions.toml"))`; plugin classes read it through
  `VersionCatalogsExtension.named("libs")`. Typesafe accessors do not exist inside plugin source.
- `build-logic/convention` applies `kotlin-dsl` and takes AGP and the Kotlin Gradle plugin as
  `implementation` **artifacts**, versioned from the same catalog.
- Plugins apply other plugins with `pluginManager.apply(...)`, which is idempotent — `.hilt` and
  `.room` both applying KSP to `:core:data` is expected, not a bug.
- Six ids: `com.petmephone.jvm.library`, `.android.library`, `.android.application`,
  `.android.compose`, `.android.hilt`, `.android.room`.

**`CommonExtension` is settled — do not re-spike.** On AGP 9.3.1 it has zero type parameters
(verified against `gradle-api-9.3.1.jar` bytecode). `.compose` must be written as:

```kotlin
val ext: CommonExtension = project.extensions.getByType(CommonExtension::class.java)
ext.buildFeatures.compose = true
```

No star projection, no `androidComponents` fallback; configure by property access, since block-style
methods moved to `ApplicationExtension` / `LibraryExtension`. `.compose` also applies
`org.jetbrains.kotlin.plugin.compose` (mandatory on Kotlin 2.2.10) and contributes the Compose BOM
but **no Activity-specific dependency** — the overlay hosts Compose from a Service.

**Catalog and tests land together** (#3) because Turbine and Robolectric cannot be wired into a
source set before they exist in the catalog. BOM-covered Compose artifacts carry no `version.ref`;
`ksp` matches `kotlin` exactly with a comment stating the coupling; `kotlin-compose` uses
`version.ref = "kotlin"`. JUnit4 project-wide, since `ui-test-junit4` needs `@Rule`.

**Hilt last** (#6), because it needs both the plugins and the catalog. Two decisions are recorded
now rather than during M1: the overlay uses a plain `@Inject`-constructed state holder owned by the
`@AndroidEntryPoint` service (not `hiltViewModel()`, which crashes with no `ViewModelStoreOwner`,
and not `EntryPointAccessors`); and the screen receiver is a plain constructor-injected class
registered at runtime, because `SCREEN_ON`/`SCREEN_OFF` never reach a manifest receiver.

## Sequencing

| Step | Must be true before the next step starts |
|---|---|
| #1 | `./gradlew projects` lists exactly six modules; `:core:domain:dependencies` shows zero `androidx.*`/`android.*`; `build --configuration-cache` succeeds twice with reuse. |
| #2 | All six modules build through their plugins; no AGP/Kotlin block config remains outside `build-logic`; config cache still reuses. Land and exercise `:core:domain` and `:core:data` before the `:feature:*` modules consume the plugins — blast radius control. |
| #3 | `./gradlew test` runs across the graph (zero tests is a pass); instrumentation source sets compile; no module declares `JavaVersion.VERSION_11`. |
| #6 | `WorkManagerInitializer` absent from the **merged** manifest; placeholder `@HiltWorker` executes through `HiltWorkerFactory` on device. |

The configuration cache is the through-line: #1 enables it before any annotation processing, #2
first exercises it with KSP, #6 is where Hilt joins. A degradation must fail the issue that caused
it, not become a permanent warning.

## Affected Areas

| Area | Impact | Description |
|------|--------|-------------|
| `settings.gradle.kts` | Modified | `includeBuild("build-logic")` first; six `include(...)` |
| `build-logic/` | New | Included build, `convention` project, six plugins, `ProjectConfig` |
| `gradle/libs.versions.toml` | Modified | Full dependency set, bundles, KSP/Kotlin coupling |
| `gradle.properties` | Modified | Config cache, build cache, parallel, non-transitive R |
| `app/` | Modified | Template removed; `PetMePhoneApplication`, `@AndroidEntryPoint MainActivity`, manifest initializer removal |
| `core/domain`, `core/data`, `core/designsystem` | New | JVM domain; Room/DataStore + Hilt modules; Compose theme |
| `feature/overlay`, `feature/tasks` | New | Empty modules with test source sets |

## Size and delivery

**Frank assessment: this exceeds the 800-line review budget.** Rough authored estimate:
#1 ≈ 150–200 lines, #2 ≈ 350–450, #3 ≈ 200–300, #6 ≈ 200–250 — roughly **900–1200 changed lines**.

Do not ship this as one PR. Each issue is autonomous, with its own acceptance criteria, its own
verification commands, and a clean rollback: revert the PR and the previous state still builds.

### Delivery decision (made, not pending)

`delivery_strategy` is `ask-on-risk`, the estimate exceeded the budget, the question was asked, and
this is the answer:

**Three chained PRs, not four. Issues #1 and #2 ship together.**

They are causally coupled: #1 deliberately duplicates build configuration across six modules so that
#2 has a concrete, visible motivation to remove. Split apart, PR 1 asks a reviewer to approve
duplication whose justification lives in a PR they cannot see yet. Merged, the review sees the
problem and the fix in one place.

| PR | Issues | Targets | Rough size |
|---|---|---|---|
| 1 | #1 + #2 | the slice tracker branch | 500–650 |
| 2 | #3 | PR 1's branch | 200–300 |
| 3 | #6 | PR 2's branch | 200–250 |

`chain_strategy` is **`feature-branch-chain`**: PR 1 targets the slice tracker branch, each child
targets the immediately preceding PR's branch, and only the tracker merges to `master`. `master`
stays untouched until the whole slice closes.

PR 1 is over the 800-line budget on its own. That is the accepted cost of keeping the duplication
and its removal in one review, and it is recorded here rather than discovered at review time.

## Recorded assumptions

Three smaller choices were made without a round trip. They are written here so that changing one
later is a decision, not a discovery.

| Assumption | Basis | If wrong |
|---|---|---|
| `:core:domain` gains `javax.inject` at #6, not #1 | #1 says the module takes no dependencies; #6 permits it | One line moves earlier; nothing else shifts |
| `.room` is extracted as its own plugin despite one consumer | #2 states this explicitly, for symmetry with `.hilt` and to own the KSP arguments | Fold it into `:core:data`; #2 already calls this a legitimate later cleanup |
| The placeholder `@HiltWorker` lives in `androidTest`, not in shipped source | #6 wants the factory proven to execute, not a placeholder worker shipped | Move it to `main` and delete it when M2 workers land |

## Risks

| Risk | Likelihood | Mitigation |
|------|------------|------------|
| Configuration cache degrades once Hilt/KSP are present | Med | Own acceptance line per issue; failure blocks that issue rather than being accepted as a warning |
| A module declares `@Inject` without KSP applied | Med | Error surfaces opaquely in `:app`; the `.hilt` plugin must be applied to every such module, verified per module |
| `@HiltWorker` artifact split (`hilt-work` + separate `hilt-compiler`) unverified on the pinned version | Med | Verify against the pinned version during #6; the instrumented worker test is the backstop |
| `.room` has one consumer and may not earn its keep | Low | Recorded as a revisitable decision, not an oversight |
| Compose absent between #1 and #2 | High (expected) | `:app` renders nothing until `.compose` lands — expected, not a defect |
| Two Compose hosts (`:app`, overlay) drift | Med | Single BOM contributed by `.compose`; no BOM-covered artifact carries a version |

## Cross-cutting rules that apply at this layer

From `docs/build-order.md`. Most bite later, but the foundation must not make them harder:

- **Absence never renders as zero** — model it in the type. No placeholder or smoke UI added here
  may fabricate a zero.
- **Balance values are injected, never literals** — nothing in `build-logic` or the DI graph should
  hardcode a tunable; `ProjectConfig` holds build values only.
- **The foreground service holds no state** — this is why `:core:domain` must be Android-free and
  why no `@ServiceScoped` / `ServiceComponent` binding may be introduced.
- **Permission grants are queried live, never cached** — no DI provider may cache a grant.
- A decision made mid-slice that contradicts an issue must be written back to that issue.

## Rollback Plan

Each issue is one revertible PR. Reverting #6 leaves a building six-module project with a full
catalog. Reverting #3 leaves working convention plugins. Reverting #2 leaves the module skeleton
with duplicated scripts. Reverting #1 returns to the template commit. No data, no migrations, no
release artifacts are involved, so revert is complete at every boundary.

## Dependencies

- Blocked on nothing. #9 remains open but gates only #11/#13/#14/#36, not these four.
- External: AGP 9.3.1, Kotlin 2.2.10, a KSP patch matching Kotlin exactly, Compose BOM 2026.02.01.
- The `@HiltWorker` acceptance test requires a device or emulator (`connectedDebugAndroidTest`).

## Success Criteria

- [ ] `./gradlew projects` lists exactly the six agreed modules
- [ ] `./gradlew :core:domain:dependencies` resolves with zero `androidx.*` / `android.*` artifacts
- [ ] Every module `build.gradle.kts` is a `plugins {}` block plus module-specific `dependencies {}`
- [ ] `compileSdk` / `targetSdk` / `minSdk` / toolchain exist in exactly one `ProjectConfig`
- [ ] No BOM-covered Compose artifact carries an explicit `version.ref`; `ksp` matches `kotlin`
- [ ] `./gradlew test` runs across the graph; instrumentation source sets compile
- [ ] `WorkManagerInitializer` is absent from the merged manifest
- [ ] A placeholder `@HiltWorker` executes through `HiltWorkerFactory` and returns `Result.success()`
- [ ] `./gradlew build --configuration-cache` succeeds twice with cache reuse, after every issue
- [ ] `:app:assembleDebug` produces an installable APK that launches without crashing
