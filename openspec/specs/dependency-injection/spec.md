# dependency-injection

Delta spec for the change `slice-1-foundation` (issue #6). Defines the Hilt object graph shape,
component and scope rules, and WorkManager factory wiring that must hold once this change lands.
Scope boundary: this establishes the DI foundation only. `PetOverlayService`, workers, and the
screen receiver do not exist yet and are out of scope for this spec.

## ADDED Requirements

### Requirement: Application is the single Hilt root and WorkManager configuration provider
`PetMePhoneApplication` SHALL be annotated `@HiltAndroidApp`, SHALL implement
`Configuration.Provider`, SHALL inject `HiltWorkerFactory`, and SHALL override the Kotlin
property `workManagerConfiguration` (not a Java-style getter).

#### Scenario: Application class shape
- **GIVEN** `PetMePhoneApplication` after this change
- **WHEN** its source is inspected
- **THEN** it carries `@HiltAndroidApp`, implements `Configuration.Provider`, has a field or
  constructor injection point for `HiltWorkerFactory`, and overrides
  `val workManagerConfiguration: Configuration`

#### Scenario: Single WorkManager instance at cold start
- **GIVEN** the app has cold-started
- **WHEN** `WorkManager.getInstance(context)` is called
- **THEN** no `IllegalStateException` is thrown, proving exactly one WorkManager initialisation
  occurred, using the custom `Configuration`

### Requirement: Default WorkManager initializer is removed from the merged manifest
The default `androidx.startup` `WorkManagerInitializer` entry SHALL be absent from the app's
**merged** manifest, so that `HiltWorkerFactory` is the only factory WorkManager ever uses.

#### Scenario: Merged manifest has no default initializer
- **GIVEN** the manifest `tools:node="remove"` override on the `WorkManagerInitializer`
  `<meta-data>` entry
- **WHEN** `./gradlew :app:processDebugMainManifest` runs and the merged manifest at
  `app/build/intermediates/merged_manifest/debug/AndroidManifest.xml` is inspected
- **THEN** no `WorkManagerInitializer` meta-data entry is present

### Requirement: MainActivity is a Hilt entry point
`MainActivity` SHALL be annotated `@AndroidEntryPoint`.

#### Scenario: Activity is annotated
- **GIVEN** `MainActivity` after this change
- **WHEN** its source is inspected
- **THEN** it carries `@AndroidEntryPoint`

### Requirement: Domain layer stays free of Dagger/Hilt imports
Repository interfaces SHALL live in `:core:domain` and MUST import only `javax.inject` types
(`@Inject`, `@Qualifier`, `@Scope`), never any `dagger.*` or `dagger.hilt.*` type.

#### Scenario: Domain module has no Dagger imports
- **GIVEN** repository interfaces declared in `:core:domain`
- **WHEN** the module's source is searched for `dagger.` imports
- **THEN** none are found; only `javax.inject` types appear

### Requirement: Hilt bindings live in :core:data, not in :core:domain
`@Module @InstallIn(SingletonComponent::class)` binding objects SHALL live in `:core:data`, using
`@Binds` for interface-to-implementation bindings and `@Provides` only where real construction
logic exists (the Room database instance, the `DataStore<Preferences>` instance).

#### Scenario: Binding objects located and shaped correctly
- **GIVEN** the repository-implementation bindings for Room and DataStore
- **WHEN** `:core:data` is inspected
- **THEN** each `@Module @InstallIn(SingletonComponent::class)` object lives in `:core:data`;
  interface-to-implementation bindings use `@Binds`; only the Room database and the
  `DataStore<Preferences>` construction use `@Provides`

### Requirement: No service-scoped Hilt bindings exist
No `@ServiceScoped` annotation and no `ServiceComponent` binding SHALL be introduced anywhere in
the graph, since the foreground service holds no state.

#### Scenario: Searching for service-scoped bindings finds none
- **GIVEN** the complete Hilt graph after this change
- **WHEN** the codebase is searched for `@ServiceScoped` or `ServiceComponent`
- **THEN** no occurrence is found

### Requirement: KSP is applied to every module that declares an annotation Hilt processes
The convention plugin from #2 SHALL apply KSP to every module that declares `@Inject`, `@Module`,
or `@HiltWorker` — not only `:app` — so that Dagger component generation in `:app` never fails
due to a missing processor in a dependency module.

#### Scenario: Every Hilt-annotated module has KSP applied
- **GIVEN** the set of modules declaring `@Inject`, `@Module`, or `@HiltWorker`
- **WHEN** each such module's applied plugins are inspected
- **THEN** each one has KSP applied (directly or transitively through `.hilt` / `.room`)

### Requirement: A placeholder HiltWorker proves the factory wiring end-to-end
A placeholder `@HiltWorker` (kept out of shipped `main` source per the proposal's recorded
assumption) SHALL execute successfully through `HiltWorkerFactory` and return `Result.success()`
with its injected dependency present, verified on-device or on an emulator.

#### Scenario: Placeholder worker executes and succeeds
- **GIVEN** a placeholder `@HiltWorker` enqueued via `WorkManagerTestInitHelper`
- **WHEN** the work is driven to completion in `connectedDebugAndroidTest`
- **THEN** it returns `Result.success()` and its injected dependency is present and non-null

#### Scenario: Removing @HiltWorker fails at execution, not compile time
- **GIVEN** the placeholder worker with `@HiltWorker` deliberately removed
- **WHEN** the worker is enqueued and driven
- **THEN** compilation succeeds but the worker fails at execution time, because
  `HiltWorkerFactory` falls through to the default factory and cannot construct it

### Requirement: Configuration cache tolerates Hilt and KSP
`./gradlew :app:assembleDebug --configuration-cache` SHALL succeed with no Hilt- or
KSP-attributable configuration-cache warning, continuing the through-line established in #1 and
#2.

#### Scenario: Assembling debug with Hilt present reuses the configuration cache
- **GIVEN** the Hilt object graph from this change is wired into `:app`
- **WHEN** `./gradlew :app:assembleDebug --configuration-cache` runs
- **THEN** it succeeds with no configuration-cache warning attributable to Hilt or KSP
