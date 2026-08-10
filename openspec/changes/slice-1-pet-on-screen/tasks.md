# Tasks: Slice 1 — A pet on screen (#36, IDLE row only)

Ordered implementation checklist. No code lives in this file. Each task states a done condition;
tasks tagged **Verify** carry the exact command apply must run to confirm the claim, because
`failOnNoDiscoveredTests = false` makes a green build alone insufficient — a passing build must be
cross-checked against test counts in `*/build/test-results/**/TEST-*.xml`.

Traceability tags: `[SHEET-n]` → `specs/pet-sprite-sheet/spec.md` requirement n (in file order);
`[RENDER-n]` → `specs/pet-overlay-rendering/spec.md` requirement n (in file order).

---

## PR 0 — Screen-off frame-clock spike (measurement, not a feature)

Goal: answer, with logged emulator evidence, the three questions `design.md` § "PR 0 — the spike,
precisely" poses, and record which of PR 2's design choices each answer forces. No production code
ships from this PR — findings are written to issue #36 and appended to `design.md`.

1. [x] **Build a disposable spike harness in `:feature:overlay`.**
   Add a temporary `LaunchedEffect` inside the existing overlay Composable (or a throwaway
   Composable behind a debug flag) that runs a `delay()`-driven loop at a fixed interval and logs
   `System.currentTimeMillis()` per tick via `Log.d`. This harness is deleted before PR 0 closes —
   it exists only to produce the log evidence for measurement 1.
   Done: harness compiles, deployable to `emulator-5554`.
   Depends on: none.

2. [x] **Register a runtime `BroadcastReceiver` for `ACTION_SCREEN_ON`/`ACTION_SCREEN_OFF` in the spike harness.**
   Log the receive timestamp for each broadcast. This produces the evidence for measurement 2 (does
   the receiver fire at all from a foreground-service context, and at what latency).
   Done: receiver registered and unregistered correctly against the service lifecycle; logs emitted
   for both actions.
   Depends on: Task 1.

3. [x] **Instrument `Choreographer` frame health while the screen is off.**
   Add a `Choreographer.FrameCallback` (or equivalent draw-frame counter) that logs whenever the
   overlay composition produces a new draw frame, to answer measurement 3 (does drawing work
   continue being paid for with the screen off).
   Done: a log line exists per draw frame, distinguishable by screen-on/off state.
   Depends on: Task 1.

4. [x] **Run the spike on `emulator-5554` with the screen off for ≥10 minutes.**
   Deploy the harness, turn the emulator screen off (`adb -s emulator-5554 shell input keyevent
   26`, confirm off state), wait ≥10 minutes, turn it back on, and collect `adb logcat` output for
   the full window.
   Verify: `C:\Users\furid\AppData\Local\Android\Sdk\platform-tools\adb.exe -s emulator-5554 logcat
   -d > spike-log.txt` (capture after the 10-minute window; requires the emulator running).
   Done: a saved log file covering the full off-then-on window exists.
   Depends on: Tasks 1, 2, 3.

5. [x] **Analyze the log and answer the three spike questions explicitly.**
   For each of: (1) does the `delay()` loop keep firing, get Doze-batched, or stop; (2) do
   `ACTION_SCREEN_ON`/`OFF` arrive at the runtime receiver, and with what latency; (3) do draw
   frames keep being produced with the screen off — write one sentence answer with the supporting
   log excerpt.
   Done: three answers, each traceable to a specific log line range.
   Depends on: Task 4.

6. [x] **Determine which PR 2 design elements the findings force, per `design.md`'s decision fork.**
   Apply exactly the mapping already recorded in `design.md`: if (2) fails → suspension trigger
   moves from `BroadcastReceiver` to `DisplayManager.DisplayListener`, and the `StateFlow` source
   changes accordingly; if (1) shows the loop already stops under Doze → keep explicit suspension
   in the clock design, but the PR 2 acceptance test must not assert exact tick timing while the
   screen is off; if (3) shows draw frames continue with the screen off → `drawBehind` must be
   gated on the same screen-on signal, not the clock alone. If all three findings match the
   design's baseline assumptions, record that explicitly too — a spike that changes nothing is
   still a completed, evidenced decision, not a skipped one.
   Done: a short "findings vs. design impact" note, one line per question.
   Depends on: Task 5.

7. [x] **Write the findings back to issue #36 as a comment.**
   Post the three answers and the design-impact note from Tasks 5–6 as a single comment on issue
   #36.
   Done: comment posted, linkable.
   Depends on: Task 6.

8. [x] **Append the findings to `design.md` under "PR 0 — the spike, precisely".**
   Replace the current forward-looking text with the actual measured results and any design
   changes they force (e.g., updating the clock code sample in "The clock and screen-off
   suspension" if the trigger source changed).
   Done: `design.md` reflects the real spike outcome; if no change was forced, the section says so
   explicitly rather than staying silent.
   Depends on: Task 6.

9. [x] **Remove the spike harness (Tasks 1–3) from the overlay module.**
   Delete the temporary logging code; it must not ship in PR 1 or PR 2.
   Verify: `git diff --stat` shows no residual harness files in `feature/overlay/src/main`.
   Done: clean tree, no spike code remains.
   Depends on: Task 8.

10. **Open PR 0 against the slice tracker branch with the findings-only diff (docs + issue link, no code).**
    Done: PR opened, description links the #36 comment from Task 7.
    Depends on: Task 9.

---

## PR 1 — Sprite format contract, header-first decoder, typed failure

Targets the slice tracker branch (`feature-branch-chain`). Est. changed lines: ~400–500 (of the
800 budget) — see Review Workload Forecast below.

### `:core:domain` — pure Kotlin, no Robolectric

11. [x] **Create `PetSpriteRow.kt`.** `[SHEET-2]`
    `enum class PetSpriteRow { IDLE, DRAGGING, HUNGRY, HAPPY, SLEEPING, TYPING }` at
    `core/domain/.../pet/sprite/PetSpriteRow.kt`, fixed order matching the row table.
    Done: compiles; `PetSpriteRow.values()` has exactly 6 entries in that order.
    Depends on: none. Parallelizable with Task 12 only after this exists (12 references it).

12. [x] **Create `SpriteSheetFailure.kt`.** `[SHEET-7]`
    `sealed interface SpriteSheetFailure` with cases `Oversized`, `NotDivisible`, `Undecodable`,
    `EmptyIdleRow`, at `core/domain/.../pet/sprite/SpriteSheetFailure.kt`.
    Done: compiles; four distinct cases exist.
    Depends on: none.

13. [x] **Create `SpriteGrid.kt` with `SpriteGridResult`.** `[SHEET-3]` `[SHEET-4]`
    `data class SpriteGrid(val cellSizePx: Int, val columns: Int)` with companion
    `fun of(widthPx: Int, heightPx: Int, maxDimensionPx: Int): SpriteGridResult`, and
    `sealed interface SpriteGridResult { data class Valid(val grid: SpriteGrid); data class
    Invalid(val failure: SpriteSheetFailure) }`. Validation order: oversize check first (against
    injected `maxDimensionPx`), then `height % 6 != 0`, then `width % (height / 6) != 0` — per the
    confirmed contract: a remainder is a rejection, never truncated or rounded.
    Done: compiles; `maxDimensionPx` is a parameter, not a literal, in `SpriteGrid`.
    Depends on: Task 12.

14. [x] **Create `SpriteLayout.kt`.** `[SHEET-3]`
    `data class SpriteLayout(val grid: SpriteGrid, val frameCounts: List<Int> /* size 6 */)` with
    `fun cellLeftPx(row: PetSpriteRow, frame: Int): Int` and `fun cellTopPx(row: PetSpriteRow):
    Int`, both pure integer arithmetic (`row.ordinal * cellSizePx`, `frame * cellSizePx`), no
    allocations.
    Done: compiles; `frameCounts.size == 6` enforced (e.g., `require`).
    Depends on: Tasks 11, 13.

15. [x] **Unit test: `SpriteGrid.of` divisibility and oversize rules.** `[SHEET-4]` `[SHEET-5]`
    Cases: exact multiples → `Valid`; `height % 6 != 0` → `Invalid(NotDivisible)`;
    `width % (height/6) != 0` → `Invalid(NotDivisible)`; `2048×2048` exactly at bound → `Valid`;
    `>2048` in either axis → `Invalid(Oversized)`, checked before the divisibility checks fire (a
    fixture that is both oversized and non-divisible must fail as `Oversized`, proving the header
    check order).
    Verify: `./gradlew :core:domain:test --tests "*SpriteGridTest*"`, then confirm assertion count
    in `core/domain/build/test-results/test/TEST-*SpriteGridTest*.xml` matches the number of
    written `@Test` methods (guards against `failOnNoDiscoveredTests = false` masking a no-op run).
    Done: file exists, all cases pass, XML confirms non-zero test count.
    Depends on: Task 13.

16. [x] **Unit test: `SpriteLayout` cell arithmetic and frame-count invariant.** `[SHEET-2]` `[SHEET-3]`
    Cases: `cellLeftPx`/`cellTopPx` for row 0 and row 5, first and last frame; `frameCounts` list
    must be exactly size 6 or construction fails.
    Verify: `./gradlew :core:domain:test --tests "*SpriteLayoutTest*"`; confirm test count in
    `core/domain/build/test-results/test/TEST-*SpriteLayoutTest*.xml`.
    Done: file exists, passes, XML confirms count.
    Depends on: Task 14.

17. [x] **Confirm `:core:domain` stays Android-free.** `[SHEET-1]`
    Verify: `./gradlew :core:domain:dependencies --configuration implementation` shows no AGP/
    Android artifact pulled in by the new sprite package (spot-check output for `com.android`
    entries beyond what already existed pre-change).
    Done: dependency report clean.
    Depends on: Tasks 11–14.

### `:feature:overlay` — Android-specific decode path

18. [x] **Create `SpriteSheetResult.kt`.** `[SHEET-7]`
    `sealed interface SpriteSheetResult { data class Loaded(val bitmap: ImageBitmap, val layout:
    SpriteLayout); data class Failed(val failure: SpriteSheetFailure) }` at
    `feature/overlay/.../sprite/SpriteSheetResult.kt`. No nullable `Bitmap?`, no `Result<Bitmap>` —
    per the design's rejected-alternatives table.
    Done: compiles.
    Depends on: Task 14, 12.

19. [x] **Create `SpriteSheetDecoder.kt`: header-first read.** `[SHEET-5]`
    Read bounds only via `BitmapFactory.Options().apply { inJustDecodeBounds = true }`; call
    `SpriteGrid.of(outWidth, outHeight, maxDimensionPx)` before any full decode; on `Invalid`,
    return `SpriteSheetResult.Failed` immediately, with zero further decode calls.
    Done: compiles; the full-decode call path is structurally unreachable when the header check
    fails (e.g., an early return, not a flag checked later).
    Depends on: Task 18, 13.

20. [x] **Extend the decoder: full decode to `ARGB_8888`, never `HARDWARE`.** `[SHEET-6]`
    On `SpriteGrid.of` returning `Valid`, decode with `inPreferredConfig = Bitmap.Config.ARGB_8888`
    (never `HARDWARE`); `BitmapFactory.decode*` returning `null` maps to
    `SpriteSheetResult.Failed(SpriteSheetFailure.Undecodable)`.
    Done: compiles; corrupt-byte input path is covered structurally (returns `Failed`, not a thrown
    exception escaping the decoder).
    Depends on: Task 19.

21. [x] **Create `TransparentCellScanner.kt`.** `[SHEET-8]`
    Scans row 0 (and, per the domain type modeling all six rows, all six rows) from the last cell
    backward per row; a trailing run of fully-transparent cells (every pixel alpha == 0) is
    excluded from that row's frame count; the first non-fully-transparent cell from the end fixes
    the count. If row 0's resulting frame count is 0, return
    `SpriteSheetResult.Failed(SpriteSheetFailure.EmptyIdleRow)`.
    Done: compiles; wired into the decoder's post-decode step.
    Depends on: Task 20.

22. [x] **Unit test (Robolectric, `@Config(sdk = [36])`): header-first ordering never reaches full decode.** `[SHEET-5]`
    Use a decoder seam (e.g., an injectable `BitmapFactory.Options` provider or a fake) to assert
    that for an oversized fixture, the full-decode call is never invoked — not just that the result
    is `Failed`. Robolectric 4.16.1 ships no SDK 37 shadows, so `@Config(sdk = [36])` is required
    per repo convention.
    Verify: `./gradlew :feature:overlay:testDebugUnitTest --tests "*SpriteSheetDecoderTest*"`;
    confirm test count in
    `feature/overlay/build/test-results/testDebugUnitTest/TEST-*SpriteSheetDecoderTest*.xml`.
    Done: file exists, passes, XML confirms non-zero count including the "never decodes" assertion.
    Depends on: Task 21.

23. [x] **Unit test (Robolectric): corrupt bytes, config assertion, transparent-cell clamp.** `[SHEET-6]` `[SHEET-8]` `[SHEET-9]`
    Cases: corrupt PNG bytes → `Failed(Undecodable)`, never a thrown exception, never null result;
    successful decode fixture → bitmap config asserted `ARGB_8888`; an 8-cell row fixture with the
    last 4 cells fully transparent → frame count 4; a row with no transparent cells → frame count
    equals column count; an all-transparent row 0 fixture → `Failed(EmptyIdleRow)`.
    Verify: `./gradlew :feature:overlay:testDebugUnitTest --tests "*SpriteSheetDecoderTest*" --tests
    "*TransparentCellScannerTest*"`; confirm counts in the corresponding
    `feature/overlay/build/test-results/testDebugUnitTest/TEST-*.xml` files.
    Done: files exist, pass, XML confirms counts.
    Depends on: Task 22.

24. [x] **Add binary PNG fixtures for the above tests.** `[SHEET-5]` `[SHEET-6]` `[SHEET-8]`
    Small hand-built or programmatically generated (test-only, not shipped) fixture PNGs: oversized
    dimensions header, non-divisible header, valid divisible sheet, corrupt bytes, transparent-clamp
    row. Store under `feature/overlay/src/test/resources/sprite/`.
    Done: fixtures committed, referenced by Tasks 22–23's tests, no fixture used from `main`
    resources.
    Depends on: none (can run in parallel with Tasks 18–21, before 22–23 consume them).

25. [x] **Full PR 1 build check.**
    Verify: `./gradlew :core:domain:test :feature:overlay:testDebugUnitTest`; then confirm non-zero
    test counts across all new `TEST-*.xml` files listed in Tasks 15, 16, 22, 23 (the
    `failOnNoDiscoveredTests = false` trap means a green run alone does not prove tests executed).
    Done: build green, XML counts confirm real execution.
    Depends on: Tasks 15, 16, 22, 23, 17.

26. **Open PR 1 against the slice tracker branch.**
    Done: PR opened, description links `[SHEET-*]` requirements covered, states line count against
    the ~400–500 estimate.
    Depends on: Task 25.

---

## PR 2 — Renderer, clock, screen-on signal, wiring, programmer-art sheet

Targets PR 1's branch (`feature-branch-chain`). Est. changed lines: ~350–450 (of the 800 budget).
Cannot start until PR 0's findings (Task 8) are recorded, since the clock's design here depends on
them.

27. [x] **Create `ScreenStateMonitor.kt`.** `[RENDER-4]`
    `callbackFlow` over a runtime-registered `BroadcastReceiver` for `ACTION_SCREEN_ON`/
    `ACTION_SCREEN_OFF` (never a manifest receiver — these actions are never delivered to one, per
    the `slice-1-foundation` rule), seeded from `PowerManager.isInteractive` so the first emission
    is real state, `stateIn(WhileSubscribed)` exposing `StateFlow<Boolean>`. **If PR 0's findings
    changed the trigger source** (e.g., to `DisplayManager.DisplayListener`), implement that
    variant instead and note the deviation from `design.md`'s original sketch in this file's
    completion note.
    Done: compiles; seed value is read from `PowerManager`, not assumed `true`.
    Depends on: PR 0 Task 8.

28. [x] **Create `PetAnimationConfig` and wire the frame interval as an injected value.** `[RENDER-3]`
    A small data/config class carrying `frameIntervalMillis` (and any other tunable), provided via
    Hilt in `OverlayModule.kt` — never a literal inside the clock's own code.
    Done: compiles; grep confirms no numeric literal for the interval inside the clock composable
    itself.
    Depends on: none (parallelizable with Task 27).

29. [x] **Create `PetOverlayStateHolder.kt`.** `[SHEET-9]` `[RENDER-1]`
    `@Inject @Singleton` (or scoped per design) holder exposing the decoded `SpriteSheetResult`
    (decoded once via the PR 1 decoder against the bundled asset), the `PetAnimationConfig`, and the
    `ScreenStateMonitor`'s `StateFlow<Boolean>`. Consumed by `@Inject`ion in the renderer, never via
    `hiltViewModel()`, per `design.md`'s "Approach" section.
    Done: compiles; holder has no `ViewModel` supertype.
    Depends on: Tasks 27, 28, PR 1 (decoder, Task 21).

30. [x] **Create `PetOverlay.kt`: IDLE frame drawing.** `[RENDER-1]` `[RENDER-2]`
    Composable drawing row 0 only via `Modifier.drawBehind { drawImage(image, srcOffset, srcSize,
    dstOffset, dstSize) }` against the resident `ImageBitmap`; `ImageBitmap` and the `drawBehind`
    lambda are `remember`ed; source rects come from `SpriteLayout.cellLeftPx`/`cellTopPx`
    (integer arithmetic only, no per-frame object allocation); frame index is
    `mutableIntStateOf`, read only inside the draw lambda (never in composition).
    Done: compiles; no `BitmapPainter` or `Image()` composable used in the per-frame path; no row
    other than `PetSpriteRow.IDLE` is referenced by the draw call.
    Depends on: Task 29.

31. [x] **Add the manual interval clock with screen-off suspension.** `[RENDER-3]` `[RENDER-4]`
    `LaunchedEffect(layout, config) { screenOn.collectLatest { on -> if (!on) return@collectLatest;
    while (isActive) { delay(config.frameIntervalMillis); frameIndex.intValue = (frameIndex.intValue
    + 1) % layout.idleFrameCount } } }` exactly per `design.md`'s code sample, adjusted for any PR 0
    deviation recorded in Task 27. **If PR 0 found true suspension infeasible**, implement the
    documented fallback instead: hold at frame 0 while the screen is off, shipped explicitly, not
    silently.
    Done: compiles; `collectLatest` (or the PR 0-adjusted equivalent) is used so no duplicate timer
    survives a screen toggle; `frameIndex` state lives outside the effect so resume continues from
    the same value.
    Depends on: Task 30, PR 0 Task 8.

32. [x] **Draw the broken-placeholder shape on decode failure.** `[RENDER-5]` `[RENDER-6]`
    When `PetOverlayStateHolder`'s sheet result is `Failed`, `PetOverlay()` draws a distinct shape
    in `DrawScope` (e.g., a filled shape with a contrasting X or crack pattern) instead of the
    sprite path — programmatically drawn, never decoded from an asset, so it cannot itself fail to
    decode.
    Done: compiles; the failure branch and the success branch are structurally distinct (a sealed
    `when` with no `else`), so a missing case fails to compile rather than silently falling through.
    Depends on: Task 30.

33. [x] **Wire `PetOverlayService.kt`: replace the magenta placeholder.** `[RENDER-7]`
    Swap `OverlayPlaceholder()` for `PetOverlay(holder)`, passing the `@Inject`ed
    `PetOverlayStateHolder` the service already has a Hilt graph for; delete the magenta placeholder
    composable entirely — no code path may still reference it.
    Verify: `rg -i magenta feature/overlay/src` (or equivalent search tool) returns no matches.
    Done: magenta composable deleted; `PetOverlayService` gains no new state of its own.
    Depends on: Task 29, 31, 32.

34. [x] **Add the `@Ignore`d `ProgrammerArtGenerator` JUnit test.**
    `feature/overlay/src/test/kotlin/.../sprite/ProgrammerArtGenerator.kt`, a `@Ignore`d JUnit test
    using `java.awt` (test source set only — nothing ships in `main`) that writes a 6-column ×
    6-row PNG. Row 0 (IDLE) gets exactly 4 drawn (non-transparent) frames and 2 fully-transparent
    trailing cells, so the shipped asset exercises the trailing-transparent clamp
    (`TransparentCellScanner`) on every launch. This test is never run by CI (`@Ignore`); it exists
    only as a manually-triggered generator, so no generator code ships to `main` or to a release
    artifact.
    Done: file exists, compiles, is `@Ignore`d; confirm via `./gradlew :feature:overlay:
    testDebugUnitTest --tests "*ProgrammerArtGenerator*"` that it reports as skipped, not executed
    or absent.
    Depends on: PR 1 (uses the same grid/layout types for column/row sizing consistency).

35. [x] **Run `ProgrammerArtGenerator` manually and commit the output asset.**
    Temporarily remove `@Ignore` (or run via IDE "run single test"), execute locally, capture the
    generated PNG, restore `@Ignore`, and commit the PNG to
    `feature/overlay/src/main/assets/pet/idle_default.png`. This is the one built-in IDLE sheet;
    library assets merge into `:app`, so no module script changes are needed (the `android {
    namespace }`-only rule holds).
    Done: `feature/overlay/src/main/assets/pet/idle_default.png` exists in the commit, dimensions
    divide evenly by a 6-row grid, row 0 has 4 non-transparent + 2 transparent trailing cells.
    Depends on: Task 34.

36. [x] **Bind the decoder, config, and monitor in `OverlayModule.kt`.**
    Provide `SpriteSheetDecoder`, `PetAnimationConfig` (with the injected interval value), and
    `ScreenStateMonitor` via Hilt.
    Done: compiles; `PetOverlayStateHolder` resolves from the graph with no manual construction.
    Depends on: Tasks 19–21, 27, 28.

37. [x] **Unit test (`runTest` virtual time): clock advance, suspend, resume.** `[RENDER-3]` `[RENDER-4]`
    Fake `MutableStateFlow<Boolean>` for screen state; assert frame index advances on the injected
    interval while `true`; assert no advancement occurs while `false` over simulated elapsed time;
    assert resume continues from the same index once `true` again (or, if PR 0 forced the frame-0
    fallback, assert the index holds at 0 while off and does not reset unexpectedly).
    Verify: `./gradlew :feature:overlay:testDebugUnitTest --tests "*PetOverlayClockTest*"` (or the
    actual test class name chosen); confirm test count in the corresponding
    `feature/overlay/build/test-results/testDebugUnitTest/TEST-*.xml`.
    Done: file exists, passes, XML confirms count.
    Depends on: Task 31.

38. **Instrumented test on `emulator-5554`: pet visible over another app, magenta gone.** `[RENDER-7]`
    `createComposeRule` (or a manual manifest-driven check) confirming the overlay renders the IDLE
    pet over another foreground app; confirms the magenta composable is not reachable.
    Verify: `./gradlew :feature:overlay:connectedDebugAndroidTest` with `emulator-5554` running and
    connected (`C:\Users\furid\AppData\Local\Android\Sdk\platform-tools\adb.exe devices` shows it
    online first); confirm test count in
    `feature/overlay/build/outputs/androidTest-results/connected/TEST-*.xml`.
    Done: test passes on device, XML confirms count. Requires the emulator.
    Depends on: Task 33, 35.

39. **Instrumented test on `emulator-5554`: decode failure renders the broken shape, never blank.** `[RENDER-5]` `[RENDER-6]`
    Force a `Failed` result (e.g., inject a corrupt-bytes fixture through a debug override) and
    assert the broken-shape path renders, not a blank area.
    Verify: `./gradlew :feature:overlay:connectedDebugAndroidTest --tests
    "*PetOverlayFailurePlaceholderTest*"`; confirm count in the same
    `androidTest-results/connected/TEST-*.xml` location. Requires the emulator.
    Done: test passes, XML confirms count.
    Depends on: Task 32, 38.

40. **Manual acceptance pass on `emulator-5554`: full Success Criteria walk-through.**
    Grant overlay permission, run the service, observe: pet animates IDLE over another app; oversized
    and non-divisible fixtures rejected at header read (spot-check via the unit test evidence, not
    re-derived manually); exactly one resident bitmap (verify via `adb shell dumpsys meminfo` bitmap
    count before/after, or a debug log); frame loop suspends on screen-off (re-confirm PR 0's finding
    holds with the shipped clock, not just the spike harness).
    Done: each proposal Success Criteria checkbox has a concrete observation recorded (log excerpt,
    screenshot, or XML reference), not just "looks fine". Requires the emulator.
    Depends on: Tasks 36, 37, 38, 39.

41. **Full PR 2 build check.**
    Verify: `./gradlew :core:domain:test :feature:overlay:testDebugUnitTest
    :feature:overlay:connectedDebugAndroidTest`; confirm non-zero counts in every `TEST-*.xml`
    touched by Tasks 37, 38, 39 (guards against `failOnNoDiscoveredTests = false` silently passing
    on zero executed tests).
    Done: build green, all XML counts confirmed.
    Depends on: Tasks 37, 38, 39, 40.

42. **Open PR 2 against PR 1's branch.**
    Done: PR opened, description links `[RENDER-*]` requirements covered and the #36 PR-0 findings
    comment, states line count against the ~350–450 estimate, notes this closes issue #36 for the
    IDLE row.
    Depends on: Task 41.

---

## Parallelizable groups

- PR 0: Tasks 1–3 are sequential (each builds on the harness); Tasks 2 and 3 could run as parallel
  edits to the same harness file if done by separate people, but in practice are small enough to
  stay sequential in one pass.
- PR 1: Tasks 11 and 12 are independent (both leaf types) and can run in parallel. Task 24
  (fixtures) can run in parallel with Tasks 18–21 (decoder implementation), since fixtures do not
  depend on decoder code, only on PNG bytes.
- PR 2: Tasks 27 and 28 are independent and can run in parallel (screen monitor vs. config/DI
  plumbing). Task 34 (generator) can run in parallel with Tasks 27–33, since it only depends on PR
  1's domain types, not on the renderer.
- Everything else is sequential: each PR's later tasks consume earlier tasks' types, and PR 2
  cannot start (Task 27) until PR 0's findings (Task 8) exist.

## Review Workload Forecast

Against the 800-line `review_budget_lines`, chained under `feature-branch-chain`:

| PR | Estimated changed lines | Cumulative | Budget status |
|---|---|---|---|
| 0 | ~0 shippable code (spike harness is deleted; only docs/issue-comment diff ships) | ~0 | well under, reviewed alone |
| 1 | ~400–500 (domain types + decoder + scanner + fixtures + JVM/Robolectric tests) | ~400–500 | under budget on its own |
| 2 | ~350–450 (renderer + clock + screen signal + wiring + programmer-art test + asset + instrumented tests) | ~750–950 combined | PR 2 alone is under budget; **combined with PR 1 it is at or over 800** — this is why `chain_strategy: feature-branch-chain` applies and each PR is reviewed independently against the budget, never as one combined diff |

Note: the committed PNG asset (Task 35) is binary and does not count toward the line budget the
same way source diffs do, but its presence should be flagged to reviewers since binary diffs are
not human-reviewable line-by-line.

## Out of scope (no tasks written for these, per instruction)

Quality tiers, `inSampleSize` targeting, user-facing import messages, non-IDLE row behaviour (rows
1–5, state resolution #37), the reactive animation layer (#38, squash/drag/particles), the
character-authoring preview tool, and the virtual-time heap-growth test at full-grid scale (kept as
a future acceptance criterion per the proposal, not built here).

## Apply status note

- **Tasks 10, 26, 42 (open PR)**: not run by the apply executor — the orchestrator that launched
  this apply explicitly instructed "Do NOT push, do NOT open a PR" and owns the PR chain. All
  three PRs' content is committed on the working branch (`feat/compose-overlay-host`) in three
  discrete commits so the boundaries stay legible.
- **Tasks 38, 39 (instrumented tests)**: written, compiled, and deployed to `emulator-5554`, but
  both fail at execution with `java.lang.NoSuchMethodException: android.hardware.input.InputManager
  .getInstance []` — a pre-existing `androidx.test`/Espresso incompatibility with the API 37
  system image, the same class of toolchain gap as the documented Robolectric SDK 36 workaround,
  not a defect in the code under test. The claims those tests target (pet visible over another
  app, animation actually advances, magenta gone) are independently confirmed by direct `adb`
  screenshot evidence in `apply-progress.md` instead.
- **Tasks 40, 41**: partially evidenced (see apply-progress.md's Work Unit Evidence) but left
  unchecked because they depend on 38/39's automated pass, which the environment gap above
  prevents.
