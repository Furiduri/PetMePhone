# Design: Slice 1 Foundation — module graph, convention plugins, catalog, Hilt graph

## Technical Approach

Enforce architecture with the build, not with discipline. Six Gradle modules give the compiler the
boundary; one `ProjectConfig` in an included build gives shared values one owner; one Compose BOM
gives two Compose hosts one version; one Hilt root gives every future entry point its collaborators.
Satisfies `specs/build-foundation/spec.md` and `specs/dependency-injection/spec.md`.

## Module graph

```
:app  ──────────────► :feature:overlay ──┐
  │  └──────────────► :feature:tasks ────┤
  │                                      ├──► :core:designsystem ──► (Compose only)
  ├──────────────────► :core:data ───────┤
  └──────────────────► :core:domain ◄────┘   (all layers may see domain)
```

| Module | Plugin id(s) | May depend on |
|---|---|---|
| `:core:domain` | `jvm.library` | nothing |
| `:core:data` | `android.library`, `android.hilt`, `android.room` | `:core:domain` |
| `:core:designsystem` | `android.library`, `android.compose` | nothing |
| `:feature:overlay` | `android.library`, `android.compose`, `android.hilt` | `:core:domain`, `:core:designsystem` |
| `:feature:tasks` | same as overlay | same as overlay |
| `:app` | `android.application`, `android.compose`, `android.hilt` | all of the above |

Direction is one-way inward. `:core:domain` applies `org.jetbrains.kotlin.jvm` with no `android {}`
block, so there is no idiomatic way to add an Android dependency — this is the enforcement, per #1.
Module-to-module edges default to `implementation`; `api` only for Flow-returning repository
interfaces exposed by `:core:domain`. `:core:data` never leaks Room types upward.

## build-logic layout

```
build-logic/
├── settings.gradle.kts          # own `libs` catalog: from(files("../gradle/libs.versions.toml"))
└── convention/
    ├── build.gradle.kts         # `kotlin-dsl`; AGP + KGP + KSP GP as implementation ARTIFACTS,
    │                            # versioned from the same catalog; gradlePlugin { plugins { register(x6) } }
    └── src/main/kotlin/
        ├── ProjectConfig.kt     # compileSdk 37, minSdk 26, targetSdk 37, jvmToolchain
        ├── CatalogExt.kt        # Project.libs helper
        └── com/petmephone/…     # six plugin classes
```

Catalog access inside plugin source is runtime, never typesafe accessors:

```kotlin
val libs = extensions.getByType<VersionCatalogsExtension>().named("libs")
```

## The six convention plugins

| Plugin | Extension targeted | Configures | Deliberately does NOT |
|---|---|---|---|
| `jvm.library` | `KotlinJvmProjectExtension` | `kotlin.jvm`, `jvmToolchain(ProjectConfig.jvmToolchain)`, JUnit4 test deps | touch AGP; share a base class with the Android family |
| `android.library` | `LibraryExtension` | `com.android.library`, `kotlin.android`, sdk levels, `resourcePrefix`, `testInstrumentationRunner` | set `namespace` (module-specific) |
| `android.application` | `ApplicationExtension` | `com.android.application`, sdk levels, `applicationId`, `versionCode/Name`, runner | signing, R8 (#4) |
| `android.compose` | `CommonExtension` | `kotlin.plugin.compose`, `buildFeatures.compose = true`, Compose BOM + `compose-ui` bundle | add `activity-compose` or any Activity-specific artifact |
| `android.hilt` | — (dependency-only) | `pluginManager.apply(ksp)`, `dagger.hilt.android.plugin`, `hilt-android` + `hilt-android-compiler`, `hilt-work` + `androidx.hilt:hilt-compiler` | declare bindings |
| `android.room` | — | `pluginManager.apply(ksp)`, Room runtime/ktx/compiler, KSP args `room.schemaLocation`, `room.incremental` | exist in more than one consumer (recorded, revisitable — #2) |

`pluginManager.apply` is idempotent: `.hilt` and `.room` both applying KSP to `:core:data` is
expected. `.compose`, per the closed Question B (AGP 9.3.1, verified against jar bytecode):

```kotlin
val ext: CommonExtension = project.extensions.getByType(CommonExtension::class.java)
ext.buildFeatures.compose = true   // property access; no star projection, no androidComponents
```

`ApplicationExtension` and `LibraryExtension` both extend `CommonExtension`, so one plugin covers
both hosts. `jvmToolchain` is set once per plugin family; `kotlinOptions.jvmTarget` is never set.

## Plugin resolution ordering

`pluginManagement { includeBuild("build-logic") }` must be the **first** block of the root
`settings.gradle.kts`, before `dependencyResolutionManagement`. Included builds resolve plugin ids
before the root build's `plugins {}` blocks evaluate; wrong ordering surfaces as
`Plugin [id: 'com.petmephone.android.compose'] was not found`. This is load-bearing, not stylistic.

## Version catalog structure

`[versions]` refs → `[libraries]` → `[bundles]` (`compose-ui`, `compose-test`) → `[plugins]`.
Rules: BOM-covered Compose artifacts carry **no** `version.ref` and the BOM is applied as
`implementation(platform(...))`, never inside a bundle; `ksp = "<kotlin>-<patch>"` with an adjacent
comment stating the coupling; `kotlin-compose` uses `version.ref = "kotlin"`; Room's KSP processor
tracks the Room version, not Kotlin. JUnit4 project-wide, because `ui-test-junit4` needs `@Rule`.

## Hilt graph

Single component: `SingletonComponent`. `ViewModelComponent` only if the Activity gains ViewModels.
No `@ServiceScoped`, no `ServiceComponent` binding — the foreground service holds no state.

```
PetMePhoneApplication (@HiltAndroidApp, Configuration.Provider)
   └─ @Inject HiltWorkerFactory ──► override val workManagerConfiguration
        SingletonComponent
          └─ :core:data  @Module @InstallIn(SingletonComponent::class)
                 @Binds    interface → impl   (interfaces live in :core:domain, javax.inject only)
                 @Provides Room database, DataStore<Preferences>
```

The default initializer is removed in `app/src/main/AndroidManifest.xml` with `tools:node="remove"`
on the nested `<meta-data>` (not on the `<provider>`, which uses `tools:node="merge"`), verified
against the **merged** manifest.

## Test infrastructure

| Module | Source sets | Libraries |
|---|---|---|
| `:core:domain` | `test` | JUnit4, coroutines-test, Turbine, hand-written fakes (no MockK) |
| `:core:data` | `test` | + Robolectric, MockK |
| `:core:designsystem` | `androidTest` | `compose.ui.test` |
| `:feature:overlay` / `:feature:tasks` | `test` + `androidTest` | JUnit4, MockK; `compose.ui.test` |
| `:app` | `androidTest` | smoke + the placeholder `@HiltWorker` |

"Zero tests but the infrastructure works" means: `./gradlew test` runs every test task to success
with zero tests executed, and every `androidTest` source set compiles via
`assembleDebugAndroidTest`. The single exception is the instrumented worker test in #6.

## Architecture decisions

| Decision | Choice | Rejected | Rationale |
|---|---|---|---|
| Shared build code | `build-logic` included build | `buildSrc` | A `buildSrc` edit invalidates the whole build's configuration (#2) |
| Compose extension | `CommonExtension`, property access | star projection, `androidComponents` | Zero type parameters on AGP 9.3.1, jar-verified; block methods moved to the concrete extensions |
| `.room` as its own plugin | Extract despite one consumer | Fold into `:core:data` | Symmetry with `.hilt` and one owner for the KSP args (#2); revisitable |
| Plugin tests | No Gradle TestKit | TestKit suite | Declarative composition; six real consumers fail loudly every build (#2) |
| Compose in the overlay | `@Inject` state holder owned by the `@AndroidEntryPoint` service | `hiltViewModel()`, `EntryPointAccessors` | No `ViewModelStoreOwner` in a Service-hosted `ComposeView`; matches the stateless-service rule (#6) |
| Screen receiver | Plain constructor-injected class, registered at runtime | Manifest receiver, Hilt annotation | `SCREEN_ON`/`SCREEN_OFF` never reach a manifest receiver (#6) |
| Placeholder worker location | `androidTest` | shipped `main` | Prove the factory, do not ship a placeholder (recorded assumption) |
| `jvm.library` base class | Fully separate from the Android family | Shared base | Sharing couples pure-Kotlin compilation to AGP on the classpath, for nothing |

## File changes

| Path | Action |
|---|---|
| `settings.gradle.kts` | Modify — `includeBuild` first, six `include(...)` |
| `gradle.properties` | Modify — config cache, caching, parallel, `nonTransitiveRClass`, `nonFinalResIds` |
| `gradle/libs.versions.toml` | Modify — complete dependency set, bundles, KSP coupling |
| `build-logic/**` | Create — settings, `convention`, `ProjectConfig`, six plugins |
| `app/**` | Modify — `git rm` template, `PetMePhoneApplication`, `@AndroidEntryPoint MainActivity`, manifest override |
| `core/domain`, `core/data`, `core/designsystem`, `feature/overlay`, `feature/tasks` | Create |

## Threat Matrix

N/A — no routing, shell, subprocess, VCS/PR automation, executable-file classification, or
process-integration boundary. Gradle plugin application is build configuration, not process routing.

## PR boundaries

| PR | Issues | Target branch | Must be true before the next PR starts |
|---|---|---|---|
| 1 | #1 + #2 | slice tracker branch | Six modules listed; `:core:domain:dependencies` Android-free; every module script is `plugins {}` + `dependencies {}`; `ProjectConfig` is the sole owner of sdk/toolchain values; `build --configuration-cache` ×2 with reuse |
| 2 | #3 | PR 1's branch | `./gradlew test` green graph-wide with zero tests; `androidTest` source sets compile; no `JavaVersion.VERSION_11`; no double-pinned Compose artifact; config cache still reuses |
| 3 | #6 | PR 2's branch | `WorkManagerInitializer` absent from the merged manifest; placeholder `@HiltWorker` returns `Result.success()` on device; `:app:assembleDebug --configuration-cache` clean |

`chain_strategy` is `feature-branch-chain`; only the tracker merges to `master`. PR 1 exceeds the
800-line budget by accepted, recorded decision — the duplication and its removal review together.
Within PR 1, land `:core:domain` and `:core:data` on the plugins before the `:feature:*` modules,
for blast-radius control.

## Migration / Rollout

No data migration. Each PR is independently revertible and the prior state still builds.

## Open Questions

- [ ] `@HiltWorker` artifact split (`androidx.hilt:hilt-work` + a separate `androidx.hilt:hilt-compiler`
      alongside `hilt-android-compiler`) is unverified on the pinned version — verify during PR 3.
- [ ] Exact KSP patch matching Kotlin 2.2.10 — resolve when writing the catalog in PR 2.
