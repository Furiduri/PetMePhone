```yaml
schema: gentle-ai.verify-result/v1
evidence_revision: sha256:dc497bb1076cf728939f9da5541e62a894f1de4f649d25c65c8322f6c0ea4e5b
verdict: pass_with_warnings
blockers: 0
critical_findings: 0
requirements: 26/26
scenarios: 51/51
test_command: "./gradlew assembleDebug testDebugUnitTest :core:domain:test assembleDebugAndroidTest lintDebug --stacktrace --rerun-tasks"
test_exit_code: 0
test_output_hash: sha256:dc497bb1076cf728939f9da5541e62a894f1de4f649d25c65c8322f6c0ea4e5b
build_command: "./gradlew assembleDebug testDebugUnitTest :core:domain:test assembleDebugAndroidTest lintDebug --stacktrace --rerun-tasks"
build_exit_code: 0
build_output_hash: sha256:dc497bb1076cf728939f9da5541e62a894f1de4f649d25c65c8322f6c0ea4e5b
```

# Verification Report - slice-3-b-a-pet-you-can-talk-to

**Change**: slice-3-b-a-pet-you-can-talk-to (#17, #18 spike)
**Mode**: full artifacts (proposal, design, tasks, apply-progress, six delta specs, spike findings)
**Evidence revision**: git aa1cd22949103ee38a4d6aaaf7d0e522d7ed02c3 on master (envelope evidence_revision is the sha256 of the green CI-gate log); six chained PRs (#80, #81, #83, #84, #85, #86) merged.
**Verdict**: PASS WITH WARNINGS

## Artifact retrieval

The Engram MCP tools (mem_search, mem_get_observation, mem_save) were NOT available in this
executor tool set. Artifacts were read from disk under
openspec/changes/slice-3-b-a-pet-you-can-talk-to/ (proposal.md, design.md, tasks.md,
apply-progress.md, specs/*/spec.md, spike-findings/*). This report is therefore persisted as a
file; the Engram copy could not be written and remains owed.

## 1. Completeness

| Phase | Tasks | State |
|---|---|---|
| 1 - minSdk bump | 1.1-1.4 | 4/4 complete |
| 2 - spike module | 2.1-2.8 | 8/8 complete (build-side; the device run is the maintainer) |
| 3 - pure domain | 3.1-3.6 | 6/6 complete |
| 4 - hunger plumbing | 4.1-4.8 | 8/8 complete |
| 5 - controller + service | 5.1-5.9 | 9/9 complete |
| 5.10 | - | OPEN, maintainer-blocking (real hardware) |
| 6 - card UI + a11y | 6.1-6.7 | 7/7 complete |
| 6.8 | - | OPEN, maintainer-blocking (manual TalkBack) |
| 7 - docs + deviation | 7.1-7.3 | 3/3 complete |

43 of 45 tasks checked. 5.10 and 6.8 are correctly classified as maintainer-blocking rather than
missed: both require a physical device, adb-injected input does not reach the overlay on the
maintainer HyperOS device, and the design testing-strategy table already names that row as "not
closable by this pipeline". They are NOT counted as incomplete work and do NOT block archive.

Task state matches code state on every task inspected (see section 2).

## 2. Correctness - code read, not documents trusted

| Claim | Evidence read | Result |
|---|---|---|
| minSdk = 30, single owner | build-logic/.../ProjectConfig.kt:9 minSdk = 30; the only other occurrences are the two convention plugins reading ProjectConfig.minSdk, plus the spike module documented literal | OK |
| No androidx.window | gradle/libs.versions.toml has zero window matches | OK |
| No pre-30 inset compat branch | Repo-wide SDK_INT scan: remaining hits are the spike module, Theme.kt (API 31 dynamic colour, still reachable), and a test comment. No inset compat branch survives | OK |
| MetricReading shape | core/domain/.../metric/MetricReading.kt - Loading / Available(percent: Int) / Unavailable; percent exists only on Available | OK |
| Reducer is total, no BackPressed | QuickMenuState.kt - nested exhaustive when, no else; all five events from Open map to Closed; QuickMenuEvent has no BackPressed case | OK |
| Hunger reactive off Room + day flow | ObserveHunger.kt - private todayFlow emits clock.today(), then delays to the next local midnight; flatMapLatest, combine into calculateHunger | OK |
| Today via AppClock | ObserveHunger takes AppClock; no LocalDate.now() or System.currentTimeMillis() in it | OK |
| No cached count in overlay | PetOverlayStateHolder.hunger is observeHunger().map to Available then stateIn(scope, WhileSubscribed(0), Loading); no count field | OK |
| Happiness/Energy plain vals | val happiness: MetricReading = MetricReading.Unavailable, same for energy | OK |
| Card window non-focusable | QuickMenuWindowParams.create sets FLAG_NOT_FOCUSABLE or FLAG_NOT_TOUCH_MODAL or FLAG_WATCH_OUTSIDE_TOUCH and nothing else; FLAG_ALT_FOCUSABLE_IM absent | OK |
| onTap seam only | PetOverlayService.onPetTapped maps OverlayAnchor to QuickMenuAnchor and calls onEvent(PetTapped); drag and screen-off come from existing reactive signals (dragStateRepository.isDragging, stateHolder.screenOn), so the touch layer gains nothing | OK |
| No back wiring | No OnBackPressedDispatcher, BackHandler, KEYCODE_BACK, or setViewTreeOnBackPressedDispatcherOwner in the quickmenu package; NoBackGestureCodeTest scans the source text and asserts zero hits | OK |
| Removal, not hiding | The controller owns addView/removeView on its own host; QuickMenuWindowControllerTest asserts each dismissal path removes once and clears the field, with a second event proving the field was cleared | OK |
| 48dp targets + descriptions | QuickMenuCard.kt - launch button heightIn/widthIn(min = 48.dp) plus contentDescription; add-task control sizeIn(minWidth = 48.dp, minHeight = 48.dp) plus contentDescription, enabled = false | OK |
| DAO / DI | TaskDao.observeManuallyCreatedOn is a Flow<Int> @Query with no numeric literal; TaskRepositoryImpl.observeRecurringScheduledOn is flowOf(0), documented; DataModule provides ObserveHunger, TaskDao, TaskOccurrenceDao | OK |

## 3. Coherence - design decisions against shipped code

| # | Decision | Followed |
|---|---|---|
| 1 | minSdk 30, sole owner, compat branches deleted | Yes |
| 2 | MetricReading in :core:domain/metric/ | Yes |
| 3 | Happiness/Energy plain vals | Yes |
| 4 | Reactive Hunger keyed on a day flow | Yes |
| 6 | Non-focusable card window | Yes |
| 7 | No back gesture anywhere | Yes, structurally enforced |
| 8 | Dismissal removes the window | Yes |
| 9 | Total reducer, no undismissable state | Yes |
| 13 | Separate :spike:ime-viability module | Yes - own applicationId, no :app reference, no dependency on :feature:overlay or :core:* |
| 10/11 | Placement signature, configured card size | DEVIATED - see WARNING 1 |

## 4. Build and test evidence

Command (the exact CI gate):

    ./gradlew assembleDebug testDebugUnitTest :core:domain:test assembleDebugAndroidTest lintDebug --stacktrace --rerun-tasks
    BUILD SUCCESSFUL in 5m 30s
    548 actionable tasks: 548 executed
    exit code 0

- --rerun-tasks was used; 548 executed confirms nothing was served from cache.
- JUnit XML aggregate across all modules: 282 tests, 0 failures, 0 errors, 1 skipped. The single
  skip is ProgrammerArtGenerator :: generate idle png, a pre-existing sprite-art generator
  unrelated to this change.
- Per module: :core:domain 112, :feature:overlay 124, :core:data 38, :app 8.
- lintDebug passed as part of the same invocation.

Transient failures observed before the green run, recorded honestly:

1. Run 1 failed while packing the local build cache (a tar entry closed before the byte count in
   its header was written) - a cache-write I/O fault, not a compile or test failure.
2. Run 2 was killed mid-build by a gradlew --stop I issued in the same shell command - operator
   error, not a code signal.
3. Run 3 failed with the documented Windows file-lock: FileSystemException on
   feature/overlay/.../bundleLibCompileToJarDebug/classes.jar, used by another process, alongside
   a "Detected multiple Kotlin daemon sessions" warning.
4. After a separate gradlew --stop (2 daemons stopped), the exact same command ran clean and
   green. Only that final run is the evidence above.

Instrumented tests were assembled, not executed. assembleDebugAndroidTest compiles the androidTest
APK; it does not run it, and connected* tasks are forbidden in this pipeline because they target
the maintainer physical phone. Instrumented evidence (QuickMenuWindowLifecycleTest, 4 tests, and
QuickMenuBackGestureDoesNotDismissTest) comes from the apply phase emulator-5554 run recorded in
apply-progress.md, not from this verification. Scenarios resting solely on it are marked
APPLY-EVIDENCE below rather than PASS.

## 5. Spec compliance matrix

Authoritative totals counted from the six delta specs: 26 requirements, 51 scenarios.

Counter semantics, stated so the envelope numbers cannot be misread: the envelope reports 26/26
and 51/51 meaning every requirement and scenario was assessed and given an explicit disposition,
NOT that all 51 were executed as passing tests in this pipeline. 44 scenarios are closed by tests
that ran here; 2 rest on apply-phase emulator evidence; 5 are maintainer-device-only and the specs
themselves label them so. The per-scenario dispositions below are authoritative over the counters.

| Capability | Reqs | Scenarios | Verified here | Notes |
|---|---|---|---|---|
| build-foundation | 2 | 5 | 5 PASS | source scan plus ProjectConfig |
| hunger-metric | 1 | 4 | 4 PASS | ObserveHungerTest, TaskDaoObserveCountsTest, TaskRepositoryImplTest |
| quick-menu-positioning | 5 | 11 | 11 PASS | QuickMenuPlacementTest - corners, mid-edges, clamping, insets, determinism |
| overlay-metric-display | 4 | 6 | 6 PASS | type-level plus MetricRow and QuickMenuCard Robolectric tests |
| overlay-quick-menu | 8 | 18 | 14 PASS, 2 APPLY-EVIDENCE, 2 DEVICE-ONLY | see below |
| ime-viability-spike | 6 | 7 | 2 PASS, 5 DEVICE-ONLY | findings committed for one OEM skin |

Scenario dispositions that are not a plain PASS:

| Scenario | Status | Why |
|---|---|---|
| Card window is separate from the pet window; pet LayoutParams bit-for-bit unchanged | APPLY-EVIDENCE | QuickMenuWindowLifecycleTest, run on emulator-5554 during apply; assembled but not executed here |
| Pressing back while the card is open does not dismiss it | APPLY-EVIDENCE | QuickMenuBackGestureDoesNotDismissTest (instrumented) plus NoBackGestureCodeTest (executed here) |
| The app underneath never receives onWindowFocusChanged(false) | DEVICE-ONLY | task 5.10, maintainer-blocking |
| Manual TalkBack pass | DEVICE-ONLY | task 6.8, maintainer-blocking |
| Spike installs and runs; records findings; all five questions; focus-only cost; focus return | DEVICE-ONLY | maintainer-executed; findings committed under spike-findings/ |
| Findings record exists in the diff | PASS | redmi-note-14-pro-hyperos3-api36.md carries seven runs with a device identifier and a per-run video-pause answer |
| No IME implementation exists outside the spike | PASS with caveat | no EditText, BasicTextField, or showSoftInput in the overlay window; two pre-existing OutlinedTextFields in character/ui/ full-screen composables predate this change |

## 6. Tracked deviations - confirmed recorded, not defects

- The #17 back-gesture criterion is not met, by design decision 7. Recorded in design.md (decision
  7 plus a dedicated section), in specs/overlay-quick-menu/spec.md under "Requirement:
  Back-gesture dismissal is out of scope for this change" with an explicit statement that the #17
  criterion is NOT met, and in a comment on GitHub issue #17. Enforced structurally by
  NoBackGestureCodeTest. Confirmed on all three surfaces.
- "Happiness and Energy render loading" was superseded by decision 3. Confirmed in code and in
  apply-progress.md phase 7.3.
- "Fully visible on an API 26-29 device" is void under decision 1. Confirmed.
- The spike covers one OEM skin (Xiaomi HyperOS 3 / Android 16 / API 36). Issue #18 asks for two;
  the second is tracked in issue #82. conclusions.md explicitly documents that the recorded
  keyboardAppeared false is a known-bad instrument reading rather than a null result, and that the
  recorded "keyboard covers field" value is not a measurement at all because it is only computed
  inside the keyboardAppeared branch. That is recorded honestly rather than ticked.

## 7. Issues

CRITICAL: none.

WARNING 1 - the placement and window-params signatures drifted from design.md (decisions 10, 11).
The design specifies place(...) returning OverlayPosition and QuickMenuWindowParams.create(position)
with a fixed card height from QuickMenuConfig. Shipped code returns a richer
QuickMenuPlacementResult carrying a VerticalAnchor, and create(placement, widthPx) uses
WRAP_CONTENT height with gravity derived from that anchor. The kdoc explains why: a fixed height
was guessed wrong twice, the second time caught by QuickMenuCardFitsTest. This is a genuine
improvement and breaks no spec requirement, but the design Interfaces block and decision 11 still
describe the superseded shape. QuickMenuWindowParams.kt also retains an apparently unused
OverlayPosition import.

WARNING 2 - the accessibility test asserts 48dp for one of the two interactive nodes.
QuickMenuCardAccessibilityTest asserts assertHeightIsAtLeast(48.dp), assertWidthIsAtLeast(48.dp),
and a content description only on the launch button; the add-task control is only asserted to be
disabled. The spec scenario says every interactive element. Source inspection confirms the
add-task control does carry sizeIn(minWidth = 48.dp, minHeight = 48.dp) and a contentDescription,
so the requirement is met in code - but shrinking that control below 48dp or dropping its
description would not turn any test red. A guard gap, not a behaviour defect.

WARNING 3 - NoBackGestureCodeTest scans only the quickmenu package, so back wiring introduced
under service/ would not fail it. Already self-reported in apply-progress.md; a manual search
confirms no such reference exists today.

SUGGESTION 1 - TaskRepositoryImpl.observeRecurringScheduledOn returns flowOf(0), a constant in
production code. Justified today because no schema column distinguishes generated occurrences, and
documented in place, but it should fall out when slice 4 lands.

SUGGESTION 2 - the Engram copy of this report is owed; the MCP tools were unavailable here.

SUGGESTION 3 - the spike keyboardAppeared signal is known-bad. Fix the instrument before a second
OEM skin is measured for #82, or that run will record the same non-measurement.

## 8. Verdict

PASS WITH WARNINGS. The CI gate is genuinely green with --rerun-tasks (548 of 548 tasks executed,
282 tests, 0 failures). Every machine-verifiable spec scenario that can be closed from this
pipeline is closed by a test whose assertion was read, not merely whose name was trusted. No
CRITICAL issue blocks archive. Tasks 5.10 and 6.8 remain open by construction and are correctly
classified as maintainer-blocking.
