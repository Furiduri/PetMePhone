# Tasks: Slice 1 Foundation — module skeleton, convention plugins, catalog, Hilt graph

## Review Workload Forecast

| Field | Value |
|-------|-------|
| Estimated changed lines | 900–1200 (PR1 500–650, PR2 200–300, PR3 200–250) |
| 400-line budget risk | High |
| Chained PRs recommended | Yes |
| Suggested split | PR 1 (#1+#2) → PR 2 (#3) → PR 3 (#6) |
| Delivery strategy | ask-on-risk |
| Chain strategy | feature-branch-chain |

Decision needed before apply: No
Chained PRs recommended: Yes
Chain strategy: feature-branch-chain
400-line budget risk: High

Recorded decision (proposal.md "Delivery decision"): PR 1 is already accepted to exceed the 800-line
budget on its own — duplication (#1) and its removal (#2) review together, not a new alarm.

### Suggested Work Units

| Unit | Goal | Likely PR | Focused test command | Runtime harness | Rollback boundary |
|------|------|-----------|----------------------|-----------------|-------------------|
| 1 | Six-module skeleton, template removed, Android-free domain | PR 1 | `./gradlew :core:domain:dependencies` | N/A — build-only, no runtime UI beyond blank launch | `git revert` PR 1 returns to template commit |
| 2 | `build-logic` convention plugins replace duplicated scripts | PR 1 | `./gradlew build --configuration-cache` (×2, reuse) | N/A — build-only | Revert PR 1 leaves module skeleton with duplicated scripts (pre-#2 state not separately revertible since merged) |
| 3 | Complete catalog + test source sets, zero tests | PR 2 | `./gradlew test` | `./gradlew :feature:overlay:assembleDebugAndroidTest` (compiles, no run) | Revert PR 2 leaves working convention plugins |
| 4 | Hilt graph + WorkManager factory + placeholder worker | PR 3 | `./gradlew :app:assembleDebug --configuration-cache` | `./gradlew :app:connectedDebugAndroidTest` — **needs device/emulator** | Revert PR 3 leaves a building six-module project with full catalog |

## Commit boundary note

The repo is currently an unmodified Android Studio template, with uncommitted edits to
`docs/build-order.md` from the exploration phase. Commit `docs/build-order.md` (recording the
Question B finding and the defect corrections) as its own first commit, before any PR 1 work
starts, so template removal and doc corrections stay in separate, independently reviewable diffs.

## PR 1: Issues #1 + #2 — target: slice tracker branch

### Sub-group A — #1 module skeleton (deliberately duplicated scripts)

- [x] 1.1 `git rm` `MainActivity.kt`, `ui/theme/*`, generated test stubs; remove `buildFeatures { compose = true }` and the Compose dependency block from `app/build.gradle.kts`.
- [x] 1.2 Edit `settings.gradle.kts` to `include(":app", ":core:domain", ":core:data", ":core:designsystem", ":feature:overlay", ":feature:tasks")`.
- [x] 1.3 Create `core/domain/build.gradle.kts` applying `org.jetbrains.kotlin.jvm` only, no `android {}` block, no dependencies.
- [x] 1.4 Create `core/data/build.gradle.kts`, `core/designsystem/build.gradle.kts`, `feature/overlay/build.gradle.kts`, `feature/tasks/build.gradle.kts` as `com.android.library` with explicit `namespace` and `android.resourcePrefix` each; add empty `src/main/kotlin` and `src/main/AndroidManifest.xml` where required.
- [x] 1.5 Reduce `app/build.gradle.kts` to `com.android.application` with explicit `namespace`, no Compose.
- [x] 1.6 Add `gradle.properties` flags: `org.gradle.configuration-cache`, `org.gradle.caching`, `org.gradle.parallel`, `android.nonTransitiveRClass`, `android.nonFinalResIds`.
- [x] 1.7 Establish the `implementation`-by-default / `api`-only-for-Flow-returning-domain-interfaces dependency rule in module scripts (no `api` usage yet, since no cross-module deps exist at this point).
- [x] 1.8 Verify: `./gradlew projects` lists exactly the six modules — satisfies spec `build-foundation` Requirement "Six-module graph with fixed identities".
- [x] 1.9 Verify: `./gradlew :core:domain:dependencies` shows zero `androidx.*`/`android.*` artifacts — satisfies Requirement "Domain layer is Android-free, enforced by the build".
- [x] 1.10 Verify: `./gradlew :core:domain:compileKotlin` succeeds with no Android SDK on the compile classpath.
- [x] 1.11 Verify: `./gradlew build --configuration-cache` succeeds twice in a row, second run reports reuse.
- [x] 1.12 Verify: `./gradlew :app:assembleDebug` produces an installable APK that launches to a blank screen without crashing. (APK built successfully; on-device launch not verified — no device/emulator in this environment.)

### Sub-group B — #2 convention-plugin extraction (removes the duplication above)

- [x] 1.13 Create `build-logic/settings.gradle.kts` with its own `libs` catalog: `versionCatalogs { create("libs") { from(files("../gradle/libs.versions.toml")) } }`.
- [x] 1.14 Create `build-logic/convention/build.gradle.kts` applying `` `kotlin-dsl` ``, with AGP + Kotlin Gradle Plugin as `implementation` artifacts versioned from the shared catalog.
- [x] 1.15 Ensure root `settings.gradle.kts` has `pluginManagement { includeBuild("build-logic") }` as the **first** block, before `dependencyResolutionManagement`.
- [x] 1.16 Create `build-logic/convention/src/main/kotlin/ProjectConfig.kt` — sole owner of `compileSdk` (37), `minSdk` (26), `targetSdk` (37), `jvmToolchain`.
- [x] 1.17 Create `build-logic/convention/src/main/kotlin/CatalogExt.kt` — `Project.libs` runtime accessor via `VersionCatalogsExtension.named("libs")`.
- [x] 1.18 Create plugin `com.petmephone.jvm.library` — configures `KotlinJvmProjectExtension`, `jvmToolchain(ProjectConfig.jvmToolchain)`, JUnit4 test deps; no shared base class with the Android family.
- [x] 1.19 Create plugin `com.petmephone.android.library` — configures `LibraryExtension`: `com.android.library`, `kotlin.android`, sdk levels from `ProjectConfig`, `resourcePrefix`, `testInstrumentationRunner`; does not set `namespace` (module-specific).
- [x] 1.20 Create plugin `com.petmephone.android.application` — configures `ApplicationExtension`: `com.android.application`, sdk levels, `applicationId`, `versionCode`/`versionName`, runner.
- [x] 1.21 Create plugin `com.petmephone.android.compose` targeting `CommonExtension` (zero type parameters, per closed Question B): `ext.buildFeatures.compose = true` via property access, no star projection, no `androidComponents` fallback; applies `org.jetbrains.kotlin.plugin.compose`; contributes Compose BOM via `implementation(platform(...))`; no Activity-specific artifact.
- [x] 1.22 Create plugin `com.petmephone.android.hilt` — `pluginManager.apply("com.google.devtools.ksp")`, `dagger.hilt.android.plugin`, `hilt-android` + `hilt-android-compiler`, `hilt-work` + `androidx.hilt:hilt-compiler`; declares no bindings.
- [x] 1.23 Create plugin `com.petmephone.android.room` — `pluginManager.apply("com.google.devtools.ksp")`, Room runtime/ktx/compiler, KSP args `room.schemaLocation`/`room.incremental`; single consumer (`:core:data`) is a recorded, revisitable decision.
- [x] 1.24 Register all six plugin ids in `build-logic/convention/build.gradle.kts` via `gradlePlugin { plugins { register(...) } }`.
- [x] 1.25 Rewrite `core/domain/build.gradle.kts` to apply `com.petmephone.jvm.library` only, plus `dependencies {}`.
- [x] 1.26 Rewrite `core/data/build.gradle.kts` to apply `com.petmephone.android.library`, `com.petmephone.android.hilt`, `com.petmephone.android.room`, plus `dependencies { implementation(project(":core:domain")) }`. Land and verify this module before `:feature:*` (blast-radius control per design.md).
- [x] 1.27 Rewrite `core/designsystem/build.gradle.kts` to apply `com.petmephone.android.library`, `com.petmephone.android.compose`, plus `dependencies {}`.
- [x] 1.28 Rewrite `feature/overlay/build.gradle.kts` and `feature/tasks/build.gradle.kts` to apply `com.petmephone.android.library`, `com.petmephone.android.compose`, `com.petmephone.android.hilt`, plus `dependencies { implementation(project(":core:domain")); implementation(project(":core:designsystem")) }`.
- [x] 1.29 Rewrite `app/build.gradle.kts` to apply `com.petmephone.android.application`, `com.petmephone.android.compose`, `com.petmephone.android.hilt`, plus `dependencies {}` referencing all modules.
- [x] 1.30 Set explicit `namespace` per module in each rewritten `build.gradle.kts` (not owned by the plugin). (`applicationId` also moved into the application plugin, since there is exactly one consumer.)
- [x] 1.31 Confirm no module hand-sets `kotlinOptions.jvmTarget` or `JavaVersion.VERSION_11`; toolchain comes solely from `ProjectConfig`.
- [x] 1.32 Verify: every module `build.gradle.kts` is reduced to a `plugins {}` block plus module-specific `dependencies {}` — satisfies spec Requirement "Module scripts are plugin application plus dependencies only". (Five of six modules also carry a minimal `android { namespace = ... }` block — see Key Learning on the namespace/spec tension below.)
- [x] 1.33 Verify: `pluginManagement { includeBuild("build-logic") }` ordering — satisfies Requirement "Included build resolves before the root build's plugin blocks"; a wrong-order regression surfaces as `Plugin [id: 'com.petmephone.android.compose'] was not found`.
- [x] 1.34 Verify: `./gradlew :core:domain:dependencies` still zero `androidx.*`/`android.*` after plugin extraction.
- [x] 1.35 Verify: `./gradlew build --configuration-cache` succeeds twice, second run reports reuse (first cache exercise against KSP).
- [x] 1.36 Verify: `:app:assembleDebug` still produces an installable APK.

## PR 2: Issue #3 — target: PR 1's branch

- [ ] 2.1 **Resolve the exact KSP patch version matching Kotlin 2.2.10** (`ksp = "2.2.10-<patch>"`) by checking the KSP release listing for the matching Kotlin build; this is an unresolved implementation item called out explicitly, not a footnote.
- [ ] 2.2 Add `[versions]` entries to `gradle/libs.versions.toml`: Hilt, Room, ksp (from 2.1, with adjacent coupling comment), WorkManager, DataStore, Lottie, `dmfs:lib-recur`, Compose BOM, test libraries (JUnit4, kotlinx-coroutines-test, Turbine, Robolectric, MockK, `compose.ui.test`).
- [ ] 2.3 Add `[libraries]` entries for all of the above; ensure BOM-covered Compose artifacts (`ui`, `ui-graphics`, `foundation`, `material3`, `ui-tooling-preview`) carry no `version.ref`.
- [ ] 2.4 Set `kotlin-compose` plugin entry to `version.ref = "kotlin"` (not a literal).
- [ ] 2.5 Add `[bundles]` `compose-ui` and `compose-test`; keep the BOM itself outside any bundle, applied via `implementation(platform(...))`.
- [ ] 2.6 Wire `test` source set with JUnit4, kotlinx-coroutines-test, Turbine, hand-written fakes in `:core:domain`.
- [ ] 2.7 Wire `test` source set with JUnit4, Robolectric, kotlinx-coroutines-test, Turbine, MockK in `:core:data`.
- [ ] 2.8 Wire `androidTest` source set with `compose.ui.test` in `:core:designsystem`.
- [ ] 2.9 Wire `test` + `androidTest` source sets (JUnit4, MockK, `compose.ui.test`) in `:feature:overlay` and `:feature:tasks`.
- [ ] 2.10 Wire `androidTest` smoke source set in `:app`.
- [ ] 2.11 Confirm no module declares `JavaVersion.VERSION_11`; re-check after catalog changes.
- [ ] 2.12 Verify: `./gradlew test` runs across the graph with zero tests executed, all green — satisfies spec Requirement "Test source sets exist with zero tests".
- [ ] 2.13 Verify: `./gradlew :feature:overlay:assembleDebugAndroidTest` (and equivalent for `:feature:tasks`, `:core:designsystem`, `:app`) compiles — satisfies Requirement "Instrumentation source sets compile".
- [ ] 2.14 Verify: catalog inspection confirms no BOM-covered artifact carries `version.ref`, and `ksp` Kotlin-version prefix equals `kotlin` exactly.
- [ ] 2.15 Verify: `./gradlew build --configuration-cache` succeeds twice with reuse after catalog + test wiring.

## PR 3: Issue #6 — target: PR 2's branch

- [ ] 3.1 **Verify the `@HiltWorker` artifact split** (`androidx.hilt:hilt-work` + separate `androidx.hilt:hilt-compiler`, distinct from `hilt-android-compiler`) against the pinned Hilt version resolved in PR 2; adjust `com.petmephone.android.hilt` plugin dependencies if the split differs from the design assumption. This is an unresolved implementation item, not a footnote.
- [ ] 3.2 Create `app/src/main/kotlin/.../PetMePhoneApplication.kt`: `@HiltAndroidApp`, implements `Configuration.Provider`, injects `HiltWorkerFactory`, overrides the Kotlin property `val workManagerConfiguration: Configuration` (not a Java getter).
- [ ] 3.3 Register `PetMePhoneApplication` as `android:name` in `AndroidManifest.xml`.
- [ ] 3.4 Add manifest override removing the default WorkManager initializer: `tools:node="remove"` on the nested `<meta-data android:name="androidx.work.WorkManagerInitializer">`, `tools:node="merge"` on the enclosing `<provider>`.
- [ ] 3.5 Create minimal `MainActivity` annotated `@AndroidEntryPoint`.
- [ ] 3.6 Define repository interfaces in `:core:domain` using only `javax.inject` types (`@Inject`, `@Qualifier`, `@Scope`); no `dagger.*` imports.
- [ ] 3.7 Create `@Module @InstallIn(SingletonComponent::class)` binding objects in `:core:data`: `@Binds` for interface-to-implementation bindings; `@Provides` only for the Room database instance and the `DataStore<Preferences>` instance.
- [ ] 3.8 Confirm KSP is applied (directly or via `.hilt`/`.room`) to every module declaring `@Inject`, `@Module`, or `@HiltWorker` — not only `:app`.
- [ ] 3.9 Confirm no `@ServiceScoped` annotation or `ServiceComponent` binding exists anywhere in the graph (grep-verifiable).
- [ ] 3.10 Document the Compose-without-Activity decision (plain `@Inject`-constructed state holder owned by the `@AndroidEntryPoint` service, not `hiltViewModel()` or `EntryPointAccessors`) and the screen-receiver decision (plain constructor-injected class registered at runtime) in the PR description — decisions only, no `PetOverlayService`/receiver code in this issue's scope.
- [ ] 3.11 Create a placeholder `@HiltWorker` in `app/src/androidTest` (per the recorded assumption — not shipped `main`) with `@AssistedInject constructor(@Assisted context: Context, @Assisted params: WorkerParameters, ...)`.
- [ ] 3.12 Write the instrumented test enqueuing the placeholder worker via `WorkManagerTestInitHelper` and driving it to completion, asserting `Result.success()` and non-null injected dependency. **Needs a device or emulator** (`connectedDebugAndroidTest`) — flag before running; may not be available in this environment.
- [ ] 3.13 Write the companion instrumented test with `@HiltWorker` deliberately removed from the placeholder, asserting compile succeeds but execution fails. **Needs a device or emulator.**
- [ ] 3.14 Verify: `./gradlew :app:processDebugMainManifest` — merged manifest has no `WorkManagerInitializer` meta-data — satisfies spec `dependency-injection` Requirement "Default WorkManager initializer is removed from the merged manifest".
- [ ] 3.15 Verify: cold start then `WorkManager.getInstance(context)` throws no `IllegalStateException` — satisfies Requirement "Application is the single Hilt root and WorkManager configuration provider" (scenario "Single WorkManager instance at cold start"). **Needs a device or emulator.**
- [ ] 3.16 Verify: `./gradlew :app:assembleDebug --configuration-cache` succeeds with no Hilt/KSP-attributable configuration-cache warning.
- [ ] 3.17 Verify: `./gradlew :app:connectedDebugAndroidTest` — placeholder worker test suite passes. **Needs a device or emulator; flag as environment-dependent.**

## Key Learnings

1. The proposal's causal-coupling rationale for merging #1 and #2 into one PR is explicit: split apart, a reviewer approves duplication whose justification lives in a PR they cannot see.
2. Question B (`CommonExtension` zero type parameters on AGP 9.3.1) is closed by jar-bytecode evidence, so PR 1's `.compose` plugin task must not re-spike it.
3. Two implementation items are genuinely unresolved and must land as their own tasks: the KSP patch version (PR 2, before catalog completion) and the `@HiltWorker` artifact split (PR 3, before worker wiring).
4. Three tasks in PR 3 require a physical device or emulator (`connectedDebugAndroidTest`), which may not be available in this execution environment and should be flagged early.
5. The repository currently has uncommitted `docs/build-order.md` edits from the exploration phase that should be committed separately before PR 1 work begins.
