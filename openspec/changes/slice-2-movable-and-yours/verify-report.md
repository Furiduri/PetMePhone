```yaml
schema: gentle-ai.verify-result/v1
evidence_revision: sha256:c79af7d524570ecf996f9e8593013e3a4f63c803a5a91570d96173bad70908e8
verdict: fail
blockers: 2
critical_findings: 2
requirements: 39/48
scenarios: 48/62
test_command: "./gradlew :core:domain:test :core:data:testDebugUnitTest :feature:overlay:testDebugUnitTest --rerun-tasks"
test_exit_code: 1
test_output_hash: sha256:a08a68d16dd498f90e74c7bc710808f01ff08d62156218f1680fd7f51487e4d6
build_command: "./gradlew assembleDebug"
build_exit_code: 0
build_output_hash: sha256:36ebc34e601c56ac3bca4778a1c4f8a13075bb039cc83b8a5b014ad2215d3fe0
```

# Verification Report - slice-2-movable-and-yours, PR 6 (tasks 74-92)

Mode: openspec + engram. Scope: `git diff b62d12e..HEAD` (PR 6a + 6b). Tasks 93+ and the known-open
device tasks 27/31/47/66 are out of scope. No adb command was executed against any attached device.

## Verdict: FAIL

Two merge-blocking findings. Everything else in the requested checklist is CONFIRMED.

## Runtime evidence

The declared test command first reported BUILD SUCCESSFUL with every task UP-TO-DATE - no test
actually executed. Re-run with `--rerun-tasks` (92 tasks executed): BUILD FAILED.

Real counts from the `TEST-*.xml` files after the forced re-run:

| Suite | tests | failures | skipped |
|---|---|---|---|
| :core:domain (10 classes) | 50 | 0 | 0 |
| :core:data CharacterRepositoryImplTest | 5 | 0 | 0 |
| :core:data ActiveCharacterRepositoryImplTest | 4 | 2 | 0 |
| :core:data (other 3 classes) | 13 | 0 | 0 |
| :feature:overlay CharacterSheetLoaderTest | 6 | 0 | 0 |
| :feature:overlay PetOverlayStateHolderTest | 2 | 0 | 0 |
| :feature:overlay PetOverlayTest | 2 | 0 | 0 |
| :feature:overlay CharacterImporterTest | 12 | 0 | 0 |
| :feature:overlay (other 16 classes) | 52 | 0 | 1 (ProgrammerArtGenerator, pre-existing) |

No suite compiles-but-runs-zero. `./gradlew assembleDebug` exits 0.
`./gradlew :feature:overlay:compileDebugAndroidTestKotlin` exits 0, so the repaired
PetOverlayRendersTest and the new CharacterSwitchLiveRenderTest both compile.

## CRITICAL (merge-blocking)

### C1 - ActiveCharacterRepositoryImplTest fails deterministically; tasks 78 and 92 are false

`core/data/src/test/kotlin/.../ActiveCharacterRepositoryImplTest.kt:67` and `:86` both fail with
`java.io.IOException: Unable to rename active_character_test.preferences_pb.tmp ... This likely
means that there are multiple instances of DataStore for this file` at
`androidx.datastore.core.FileStorageConnection.writeScope(FileStorage.kt:114)`.

Reproduced twice: in the full forced run, and in isolation
(`--rerun-tasks --tests "*ActiveCharacterRepositoryImplTest*"` -> `4 tests completed, 2 failed`).
The two failing cases are exactly the two `[IMPORT-11]` cases task 78 exists to prove. Task 78
records "Done: file exists, passes, XML confirms count" and task 92 records "build green" - neither
holds at this revision. The likely cause is environmental (Windows cannot rename over a file whose
handle a live `dataStore.data` collector still holds) rather than a defect in
ActiveCharacterRepositoryImpl itself, but a deterministically failing declared verification command
blocks the merge either way and must be shown green before archive.

### C2 - LaunchedEffect key omits `ready`; a switch between value-equal layouts freezes the animation

`feature/overlay/src/main/kotlin/.../ui/PetOverlay.kt:139` recreates the frame-index state with
`remember(ready) { mutableIntStateOf(0) }`, but the clock at `PetOverlay.kt:151` is keyed
`LaunchedEffect(layout, holder.config)`. `SpriteLayout` is a data class
(`core/domain/.../pet/sprite/SpriteLayout.kt:10`) with value equality. When the active character
changes to one whose sheet has the same cellSizePx/columns/rows/frameCount, the keys are unchanged,
so the effect is NOT relaunched: the still-running coroutine keeps incrementing the now-abandoned
MutableIntState while `drawBehind` (`PetOverlay.kt:173`) reads the new one, which stays 0 forever.
The new character renders, frozen on frame 0, until some later layout change restarts the clock.
Two 6x1 imported sheets, or an A->B->A round trip, hit this. It defeats task 85's stated purpose
and no test covers it.

## WARNING

- W1 - task 87's second assertion does not exist. PetOverlayStateHolderTest tests mapLatest
  supersession and last-Ready persistence at the StateFlow level (both real, non-vacuous). It never
  touches frameIndex, and its own comment (`:146-150`) concedes the "keeps the previous frame"
  property falls out of mapLatest rather than out of PetOverlay's lastReady. The composable's
  lastReady branch (`PetOverlay.kt:64`) has zero coverage, and the frame-index reset - the exact
  site of C2 - has zero coverage.
- W2 - one vacuous test. `CharacterSheetLoaderTest.kt:103` ("built-in-shaped and imported-shaped
  sources run the identical decode path") builds two identical fake sources from the same `files`
  map (`:109-110`) and asserts they are equal. It cannot fail and proves nothing about BuiltIn vs
  Imported. The `CharacterId.BuiltIn` asset-folder branch (`CharacterSheetLoader.kt:72-78`) has no
  unit test at all; only the Imported branch is exercised (`:121`).
- W3 - the `[IMPORT-15]` scenario has no covering test. The affordance is drawn correctly, but
  nothing asserts it renders, and BrokenPlaceholder (`PetOverlay.kt:237`) draws no affordance, so
  the spec's "always ... regardless of active character" is literally unmet for a Broken character.
  The red cross is itself code-drawn and unspoofable, so the security intent survives; the wording
  does not.
- W4 - `[RENDER-1]`'s primary scenario is untested. No JVM test proves a non-IDLE row is drawn when
  its file exists; only the fallback and the Broken path are asserted, and only structurally via
  testTag (Robolectric cannot inspect pixels). CharacterSwitchLiveRenderTest compiles but was not
  executed, per the device-safety rule.
- W5 - kdoc overclaims. `ActiveCharacterRepositoryImpl.kt:16-18` says a "stale (deleted-target)"
  pointer resolves to the fallback. It does not: `decode` (`:47-52`) never checks existence. The
  deleted-target case is handled by CharacterRepositoryImpl.remove instead; an externally deleted
  folder still resolves to a Broken render, which is the correct `[IMPORT-14]` behaviour.
- W6 - malformed-pointer edge. A stored `builtin:` with an empty name decodes to
  `CharacterId.BuiltIn("")` (`ActiveCharacterRepositoryImpl.kt:49`) rather than the fallback,
  yielding a permanent Broken render. CharacterId imposes no `require`, so this cannot crash, but
  it is not the documented "never an unresolved state" behaviour.

## SUGGESTION

- `PetOverlay.kt:56-58` writes lastReady during composition. It is idempotent here, but a
  SideEffect or derivedStateOf would keep the composition side-effect-free.
- The tagged pointer encoding is duplicated in `ActiveCharacterRepositoryImpl.encode` (`:42-45`)
  and `LibraryScreen.libraryKey` (`:93-94`).
- `mapLatest { withContext(Dispatchers.IO) { load(id) } }` cannot actually interrupt a decode
  mid-flight (no suspension points inside `load`); cancellation only takes effect at the boundary.
  The test at `PetOverlayStateHolderTest.kt:165` still passes because the stale result is discarded.

## Requested checklist

1. One render path - CONFIRMED. The only id-type branch on the render path is
   `CharacterSheetLoader.assetSourceFor` (`CharacterSheetLoader.kt:71-83`); `load(source)`
   (`:44-67`) is byte-identical for both variants. A repo-wide grep for CharacterId.BuiltIn or
   CharacterId.Imported across non-test main sources finds no other downstream branch: the holder
   (`PetOverlayStateHolder.kt:50-56`), the renderer (PetOverlay.kt) and the switching call sites
   (`LibraryScreen.kt:63-65`, `PreviewScreen.kt:113`) pass the id through untyped. LibraryScreen's
   remaining casts (`:53`, `:74`, `:93`) govern list keys and delete-button visibility, which are
   Imported-only by contract, not rendering.
2. No decode at construction - CONFIRMED. `PetOverlayStateHolder.kt:36-45` has no supertype and its
   properties are pure flow projections; nothing decodes until a collector subscribes.
3. Absence never renders as zero - CONFIRMED. A missing optional file is `continue`
   (`CharacterSheetLoader.kt:60`), never an entry and never a failure. Loading is a distinct sealed
   member and is the seed value (`PetOverlayStateHolder.kt:55`). Broken draws the code-only cross
   (`PetOverlay.kt:65`, `:248`), proven by the passing `PetOverlayTest.kt:131`.
4. IDLE fallback - CONFIRMED. `PetOverlay.kt:131` is exactly `ready.byState[petState] ?: ready.idle`;
   the draw block (`:174-199`) reads only that one loaded sheet.
5. `[IMPORT-15]` ordering - CONFIRMED. `drawIdentityAffordance()` is called at `PetOverlay.kt:205`,
   inside the same drawBehind lambda, after the drawImage at `:189`. Shape and colour are
   compile-time constants (`:215-228`); no decoded pixel participates. The undeclared badge is
   sound: it is the minimum implementation of an existing spec requirement the task list simply
   forgot to enumerate. See W3 for its gaps.
6. `[IMPORT-11]` - CONFIRMED in code, NOT CONFIRMED at runtime. `CharacterRepositoryImpl.kt:74-78`
   compares `active.first() == id` and re-points to the fallback before the DataStore edit and the
   folder delete; the non-active path never touches the pointer. The two tests that would prove it
   are the two failing under C1.
7. Frame index safety - CONFIRMED for the stated hazard, undermined by C2. `remember(ready)`
   (`PetOverlay.kt:139`) plus the defensive `frameIndex % layout.frameCount` (`:173`) make an
   out-of-range index impossible. C2 is the opposite failure mode: the index stops advancing.
8. No service restart - CONFIRMED. setActive -> DataStore -> active -> mapLatest -> StateFlow ->
   collectAsState is a pure reactive chain; no onDestroy/onCreate/addView path is involved.
9. SpriteSheetDecoder unmodified - CONFIRMED. `git log b62d12e..HEAD -- '*SpriteSheetDecoder.kt'`
   returns nothing.

## Recorded deviations - assessed

| Deviation | Assessment |
|---|---|
| confirm() writes manifest.properties | Sound and necessary; without it every imported character reloads as Broken. Covered by a real test (CharacterImporterTest.kt:189). |
| Rollback uses deleteRecursively() | Sound. File.delete() refuses a non-empty dir; the test at CharacterImporterTest.kt:207 forces the failure with an occupied destination and asserts nothing is stranded. Non-vacuous. |
| CharacterLibraryConfig.builtInFallbackName | Sound; injected value, no literal in the repository (OverlayModule.kt:66-68, :112). Matches BuiltInCharacters.all's only entry, "default", whose assets exist. |
| PetAnimationConfig.stateSharingTimeoutMillis | Sound; same injected-config rule (OverlayModule.kt:51, :98). |
| PetOverlayRendersTest.kt repair | Sound; the androidTest source set now compiles (exit 0). |
| Undeclared identity badge | Sound and required by [IMPORT-15]. See checklist item 5 and W3. |

## Task completeness

Tasks 74-92 are all marked [x]. Tasks 78 and 92 are not truthful at this revision (C1). Every other
task claim matches the code.
