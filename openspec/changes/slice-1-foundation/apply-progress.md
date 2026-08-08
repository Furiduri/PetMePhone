# Apply progress: slice-1-foundation — PR 1 (issues #1 + #2)

Status: PR 1 complete (tasks 1.1–1.36 all `[x]`). PR 2 (#3) and PR 3 (#6) not started —
out of scope for this apply pass.

## Commits (in order)

1. `feat(build): stand up six-module skeleton with duplicated scripts (#1)` — sub-group A.
2. `feat(build): extract build-logic convention plugins, remove duplication (#2)` — sub-group B.
3. `docs(sdd): mark PR 1 tasks complete in slice-1-foundation` — tasks.md checkbox update.

## Verified

- `./gradlew projects` — exactly six modules (`:app`, `:core:domain`, `:core:data`,
  `:core:designsystem`, `:feature:overlay`, `:feature:tasks`).
- `./gradlew :core:domain:dependencies` — zero `androidx.*`/`android.*` artifacts, before and
  after the convention-plugin extraction.
- `./gradlew :core:domain:compileKotlin` — succeeds, `NO-SOURCE` (empty module), no Android SDK
  needed.
- `./gradlew build --configuration-cache` — succeeds twice in a row at both sub-group A and
  sub-group B checkpoints; second run reports "Configuration cache entry reused" each time,
  including once Hilt/KSP/Room are in the graph.
- `./gradlew :app:assembleDebug` — produces `app/build/outputs/apk/debug/app-debug.apk` at both
  checkpoints. On-device install/launch NOT verified — no device or emulator in this environment
  (`adb devices` empty, no AVDs). Flagged as a risk, not silently skipped.
- Repo-wide grep for `VERSION_11` / `kotlinOptions` / stray `compileSdk`/`minSdk`/`targetSdk`/
  `jvmToolchain` literals outside `build-logic` — none found in `app/core/feature`.
- Every module `build.gradle.kts` reduced to `plugins {}` + (namespace-only) `android {}` +
  `dependencies {}` — see Key Learning below on the literal-reading tension with the spec text.

## Environment findings (recorded in the PR 2 commit message, repeated here for search)

1. AGP 9.3.1 ships built-in Kotlin compilation. Applying the classic
   `org.jetbrains.kotlin.android` plugin fails outright against AGP 9.3.1's new extension types
   (`ApplicationExtensionImpl` cannot cast to the old `BaseExtension`). Built-in Kotlin must stay
   enabled; `org.jetbrains.kotlin.android` / `.jvm` are never applied to Android modules (only
   `:core:domain`, a pure-JVM module, applies `org.jetbrains.kotlin.jvm`).
2. Built-in Kotlin, in turn, refuses to let KSP register its generated source directories via the
   classic `kotlin.sourceSets` DSL ("Using kotlin.sourceSets DSL to add Kotlin sources is not
   allowed with built-in Kotlin"). Hilt and Room both depend on KSP. Fix: AGP's own error message
   names the escape hatch — `android.disallowKotlinSourceSets=false` in `gradle.properties`
   (documented there as experimental/deprecated-eventually, but functioning).
3. `LibraryExtension.defaultConfig` has no `targetSdk` property in AGP 9.3.1 (unresolved reference
   at compile time) — only `ApplicationExtension.defaultConfig` does. Library modules therefore
   set `minSdk` only.
4. The KSP patch matching Kotlin 2.2.10 was resolved from Maven Central's own metadata (not
   guessed): `ksp = "2.2.10-2.0.2"`. Hilt (`2.60.1`), Room (`2.8.4`), and `androidx.hilt`
   (`1.4.0`) were resolved the same way (`curl` against `repo1.maven.org` / `dl.google.com`
   `maven-metadata.xml`), ahead of #3's catalog-completion task, because #2's
   `android.hilt`/`android.room` plugins need real, resolvable versions to compile and to apply to
   `:core:data` (and `.hilt` to `:feature:overlay`, `:feature:tasks`, `:app`).

## Recorded interpretation (spec/task tension — not silently resolved)

`specs/build-foundation/spec.md` Requirement "Module scripts are plugin application plus
dependencies only" reads literally as "no `android {}` ... block appears in the module script
itself." `design.md`'s own task list (1.30) says namespace is "not owned by the plugin" and must
be set per module. Since AGP has no way to set `namespace` outside an `android {}` block, and
`design.md` is binding, five of six module scripts (all but `:core:domain`, which needs no
`android {}` block at all) carry a minimal `android { namespace = "..." }` block alongside
`plugins {}` and `dependencies {}`. `resourcePrefix` is NOT a literal anywhere — the
`android.library` convention plugin derives it from `project.path` (e.g. `:core:data` →
`core_data_`), which is what design.md's plugin table implies by listing `resourcePrefix` as
plugin-configured. This reading was necessary to proceed; it should be confirmed or corrected at
verify/review time.

## Risks / follow-ups for verify

- On-device APK launch (`:app:assembleDebug` install + launch) not verified — no
  device/emulator available. `:app:assembleDebug` alone succeeded at every checkpoint.
- The `android.disallowKotlinSourceSets=false` property is marked experimental by AGP 9.3.1 and
  will presumably need re-verification against future AGP releases; this is now a real dependency
  of the build (Hilt + Room), not a cosmetic choice.
- `ksp`, `hilt`, `hiltExt` (androidx.hilt), and `room` catalog versions were added ahead of #3's
  "complete the catalog" task, scoped strictly to what #2's convention plugins need to compile.
  PR 2 (#3) should treat these as already-resolved, not re-litigate them, unless a compatibility
  issue is found.
