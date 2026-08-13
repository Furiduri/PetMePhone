package com.petmephone.spike.imeviability

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.core.content.ContextCompat
import java.time.LocalDateTime

/**
 * Everything the instrument cannot observe for itself, answered explicitly by the person who
 * watched the screen. Grouped into one type so a new question can never be added to the dialog and
 * then silently dropped on the way to the findings file.
 */
data class HumanAnswers(
    val keyboardAppeared: HumanAnswer,
    val fieldVisibility: FieldVisibility,
    val cardMovedOnFocus: HumanAnswer,
    val placementAcceptable: HumanAnswer,
    val videoPaused: HumanAnswer,
    val focusReturned: HumanAnswer,
    val petVisibleAboveKeyboard: HumanAnswer,
    val petMovementQuality: PetMovementQuality,
    val petReturnedToOriginalPosition: HumanAnswer,
)

/**
 * Single-activity entry point. Deliberately unmistakable rather than minimal (design.md's
 * usability note): one mode selector, one Start, one Finish, and a running list of what has been
 * captured so far, because the maintainer will be holding a phone and tapping through this once
 * or twice per device and per strategy.
 */
class SpikeActivity : ComponentActivity() {

    private lateinit var findingsRepository: FindingsRepository
    private var runFinishedReceiver: BroadcastReceiver? = null

    /**
     * The automatic findings of the run that just ended, held between Finish and Save answers.
     *
     * The run is ended the moment Finish is tapped, so the overlay window is gone before the
     * question dialog appears — an overlay window sits above the activity by definition, so leaving
     * it up covers the dialog and the answers cannot be given at all.
     */
    private var pendingRunResult: SpikeRunResult? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        findingsRepository = FindingsRepository(applicationContext)

        setContent {
            MaterialTheme {
                Surface {
                    SpikeScreen(
                        hasOverlayPermission = { canDrawOverlays() },
                        onRequestOverlayPermission = ::requestOverlayPermission,
                        onStartRun = ::startRun,
                        onFinishRun = ::finishRunAndCaptureResult,
                        onRecordHumanAnswers = ::recordHumanAnswers,
                        findingsRepository = findingsRepository,
                    )
                }
            }
        }
    }

    private fun canDrawOverlays(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.M || Settings.canDrawOverlays(this)

    private fun requestOverlayPermission() {
        val intent = Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:$packageName"),
        )
        startActivity(intent)
    }

    private fun startRun(mode: SpikeMode) {
        ContextCompat.startForegroundService(this, SpikeOverlayService.startIntent(this, mode))
    }

    private fun finishRun() {
        startService(SpikeOverlayService.finishIntent(this))
    }

    /**
     * Ends the run immediately, then hands the captured measurements back so the question dialog can
     * open over a screen the overlay has already left.
     *
     * The receiver is registered before the finish request rather than polling afterwards, because
     * the service takes its last sample and removes the window before it publishes anything; reading
     * too early would record a run that had not finished measuring.
     *
     * The automatic side is read from [SpikeOverlayService.lastRunResult] rather than from Intent
     * extras: the samples are structured readings, and flattening them into primitives is the exact
     * step where a missing reading turns into a defaulted `false`.
     */
    private fun finishRunAndCaptureResult(onCaptured: () -> Unit) {
        runFinishedReceiver?.let { runCatching { unregisterReceiver(it) } }
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                pendingRunResult = SpikeOverlayService.lastRunResult
                onCaptured()
                runCatching { unregisterReceiver(this) }
                runFinishedReceiver = null
            }
        }
        runFinishedReceiver = receiver
        val filter = IntentFilter(SpikeOverlayService.ACTION_RUN_FINISHED)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            registerReceiver(receiver, filter)
        }
        finishRun()
    }

    /**
     * Pairs the already-captured automatic measurements with the human answers — one findings entry
     * per run, never split across files.
     */
    private fun recordHumanAnswers(
        mode: SpikeMode,
        answers: HumanAnswers,
        onEntryRecorded: (FindingsEntry) -> Unit,
    ) {
        // Falls back to the service's published result when Finish came from the notification
        // action, which never passes through this activity's receiver.
        val result = pendingRunResult ?: SpikeOverlayService.lastRunResult
        val entry = FindingsEntry(
            timestamp = LocalDateTime.now(),
            mode = result?.mode ?: mode,
            device = DeviceInfo.capture(),
            startYPx = result?.startYPx,
            samples = result?.samples.orEmpty(),
            geometrySignal = result?.geometrySignal
                ?: KeyboardGeometrySignal.NotEnoughSamples,
            controlImeInsetDispatchFired = result?.controlImeInsetDispatchFired == true,
            windowEverReceivedFocus = result?.windowEverReceivedFocus == true,
            windowRemovedCleanly = result?.windowRemovedCleanly == true,
            layoutDrivenSampleCapReached = result?.layoutDrivenSampleCapReached == true,
            // Absent published results record the pet-follow as not applicable rather than as a
            // run that observed no reduction — nothing was measured, so nothing is claimed.
            petFollow = result?.petFollow ?: PetFollowRecord.NOT_APPLICABLE,
            keyboardAppeared = answers.keyboardAppeared,
            fieldVisibility = answers.fieldVisibility,
            cardMovedOnFocus = answers.cardMovedOnFocus,
            placementAcceptable = answers.placementAcceptable,
            videoPausedOnFocus = answers.videoPaused,
            focusReturnedAfterDismissal = answers.focusReturned,
            petVisibleAboveKeyboard = answers.petVisibleAboveKeyboard,
            petMovementQuality = answers.petMovementQuality,
            petReturnedToOriginalPosition = answers.petReturnedToOriginalPosition,
        )
        findingsRepository.append(entry)
        onEntryRecorded(entry)
        pendingRunResult = null
    }

    override fun onDestroy() {
        runFinishedReceiver?.let { runCatching { unregisterReceiver(it) } }
        runFinishedReceiver = null
        super.onDestroy()
    }
}

private enum class RunPhase { IDLE, RUNNING, ASKING_HUMAN }

@Composable
private fun SpikeScreen(
    hasOverlayPermission: () -> Boolean,
    onRequestOverlayPermission: () -> Unit,
    onStartRun: (SpikeMode) -> Unit,
    onFinishRun: (onCaptured: () -> Unit) -> Unit,
    onRecordHumanAnswers: (
        mode: SpikeMode,
        answers: HumanAnswers,
        onRecorded: (FindingsEntry) -> Unit,
    ) -> Unit,
    findingsRepository: FindingsRepository,
) {
    // Seeded from the service, not from a fresh IDLE, because the measurement requires leaving
    // this app for the one under test and coming back. A composable's `remember` does not survive
    // that trip, so an in-progress run would come back looking idle while the overlay was still up,
    // Finish would be unreachable, and the run would produce no findings at all.
    var phase by remember {
        mutableStateOf(
            if (SpikeOverlayService.activeMode != null) RunPhase.RUNNING else RunPhase.IDLE,
        )
    }
    var selectedMode by remember {
        mutableStateOf(SpikeOverlayService.activeMode ?: SpikeMode.FULL_IME)
    }
    var findingsText by remember { mutableStateOf(findingsRepository.readAllOrEmpty()) }
    val context = androidx.compose.ui.platform.LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    // Re-sync on every resume: returning from the app under test must show the run that is
    // genuinely still in progress, whatever this composable last remembered.
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME && phase != RunPhase.ASKING_HUMAN) {
                val running = SpikeOverlayService.activeMode
                if (running != null) {
                    selectedMode = running
                    phase = RunPhase.RUNNING
                } else if (phase == RunPhase.RUNNING &&
                    SpikeOverlayService.lastRunResult != null
                ) {
                    // The run was ended from the notification's Finish action, which never reaches
                    // this activity. Ask the questions anyway; dropping straight to IDLE would
                    // discard a run that was fully measured.
                    phase = RunPhase.ASKING_HUMAN
                } else {
                    phase = RunPhase.IDLE
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            // API 36 forces edge-to-edge: without this the heading sits under the status bar.
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("IME Viability Spike", style = MaterialTheme.typography.headlineSmall)
        Text(
            "Compares keyboard-visibility strategies for an overlay text field. Grant overlay " +
                "permission, pick a mode, tap Start, watch what the card and keyboard do, then " +
                "tap Finish and answer every question.",
        )
        Text(
            "The card is placed LOW on screen on purpose — a field near the top is never covered, " +
                "so it would prove nothing about pan, resize or anchor-to-top.",
            style = MaterialTheme.typography.bodySmall,
        )
        Text(
            "Round 2 is PORTRAIT ONLY. Landscape is already settled: the IME goes fullscreen with " +
                "its own extracted field there, so there is nothing left to solve. Keep the device " +
                "in portrait for every run.",
            style = MaterialTheme.typography.bodySmall,
        )

        if (!hasOverlayPermission()) {
            Button(onClick = onRequestOverlayPermission) {
                Text("Grant overlay permission")
            }
        }

        Text("Mode", style = MaterialTheme.typography.titleMedium)
        // The selected mode is shown by button style, not by a text prefix. The modes measure
        // different strategies, so starting a run in the wrong one silently mislabels the exact
        // distinction this spike exists to make. A filled button against an outlined one is
        // unmistakable at arm's length; a "> " prefix is not.
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SpikeMode.entries.forEach { mode ->
                val selected = mode == selectedMode
                val label = mode.label
                if (selected) {
                    Button(
                        onClick = { selectedMode = mode },
                        enabled = phase == RunPhase.IDLE,
                        modifier = Modifier
                            .fillMaxWidth()
                            .semantics { this.selected = true },
                    ) {
                        Text(label)
                    }
                } else {
                    OutlinedButton(
                        onClick = { selectedMode = mode },
                        enabled = phase == RunPhase.IDLE,
                        modifier = Modifier
                            .fillMaxWidth()
                            .semantics { this.selected = false },
                    ) {
                        Text(label)
                    }
                }
            }
        }
        Text(
            "Selected: ${selectedMode.label} — softInputMode ${selectedMode.softInputModeLabel()}, " +
                "repositions on focus: ${selectedMode.repositionsOnFocus}",
            style = MaterialTheme.typography.bodyMedium,
        )

        when (phase) {
            RunPhase.IDLE -> Button(
                enabled = hasOverlayPermission(),
                onClick = {
                    onStartRun(selectedMode)
                    phase = RunPhase.RUNNING
                },
            ) { Text("Start") }

            RunPhase.RUNNING -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Running: ${selectedMode.label}. The window appears in a few seconds.")
                Button(
                    onClick = {
                        // The run ends here, not after the answers. An overlay window sits above
                        // this activity by definition, so a run still in progress would cover the
                        // question dialog and make it unanswerable.
                        phase = RunPhase.ASKING_HUMAN
                        onFinishRun { /* result captured; the dialog is already up */ }
                    },
                ) { Text("Finish") }
            }

            RunPhase.ASKING_HUMAN -> HumanAnswerDialog(
                mode = selectedMode,
                onAnswered = { answers ->
                    onRecordHumanAnswers(selectedMode, answers) { _ ->
                        findingsText = findingsRepository.readAllOrEmpty()
                    }
                    phase = RunPhase.IDLE
                },
            )
        }

        Text("Findings captured so far", style = MaterialTheme.typography.titleMedium)
        Text(findingsText.ifBlank { "No runs recorded yet." })

        Button(onClick = {
            findingsRepository.shareIntent()?.let { intent ->
                context.startActivity(Intent.createChooser(intent, "Share findings"))
            }
        }) { Text("Share findings file") }

        Text("Findings file path: ${findingsRepository.path()}")
    }
}

@Composable
private fun HumanAnswerDialog(
    mode: SpikeMode,
    onAnswered: (HumanAnswers) -> Unit,
) {
    var keyboardAppeared by remember { mutableStateOf<HumanAnswer?>(null) }
    var fieldVisibility by remember { mutableStateOf<FieldVisibility?>(null) }
    var cardMoved by remember { mutableStateOf<HumanAnswer?>(null) }
    var placementAcceptable by remember { mutableStateOf<HumanAnswer?>(null) }
    var videoPaused by remember { mutableStateOf<HumanAnswer?>(null) }
    var focusReturned by remember { mutableStateOf<HumanAnswer?>(null) }
    var petVisibleAboveKeyboard by remember { mutableStateOf<HumanAnswer?>(null) }
    var petMovementQuality by remember { mutableStateOf<PetMovementQuality?>(null) }
    var petReturned by remember { mutableStateOf<HumanAnswer?>(null) }

    AlertDialog(
        onDismissRequest = {},
        title = { Text("Confirm what you observed — ${mode.label}") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                ChoiceQuestion(
                    question = "Did the keyboard appear at all?",
                    options = HumanAnswer.entries,
                    labelOf = HumanAnswer::label,
                    selected = keyboardAppeared,
                    onSelect = { keyboardAppeared = it },
                )
                ChoiceQuestion(
                    question = "Was the text field visible while typing?",
                    options = FieldVisibility.entries,
                    labelOf = FieldVisibility::label,
                    selected = fieldVisibility,
                    onSelect = { fieldVisibility = it },
                )
                ChoiceQuestion(
                    question = "Did the card visibly jump or move when the field was focused?",
                    options = HumanAnswer.entries,
                    labelOf = HumanAnswer::label,
                    selected = cardMoved,
                    onSelect = { cardMoved = it },
                )
                ChoiceQuestion(
                    question = "Was the resulting placement acceptable to use?",
                    options = HumanAnswer.entries,
                    labelOf = HumanAnswer::label,
                    selected = placementAcceptable,
                    onSelect = { placementAcceptable = it },
                )
                ChoiceQuestion(
                    question = "Did the video underneath pause when the window took focus?",
                    options = HumanAnswer.entries,
                    labelOf = HumanAnswer::label,
                    selected = videoPaused,
                    onSelect = { videoPaused = it },
                )
                ChoiceQuestion(
                    question = "Did focus return correctly to the app underneath after dismissal?",
                    options = HumanAnswer.entries,
                    labelOf = HumanAnswer::label,
                    selected = focusReturned,
                    onSelect = { focusReturned = it },
                )
                // Asked in every mode, not only the two-window one. A mode that adds no pet has an
                // explicit "Not tested" answer available, which is a recorded answer; skipping the
                // question would instead leave a blank that later reads as a measurement.
                ChoiceQuestion(
                    question = "Was the pet visible above the keyboard while typing?",
                    options = HumanAnswer.entries,
                    labelOf = HumanAnswer::label,
                    selected = petVisibleAboveKeyboard,
                    onSelect = { petVisibleAboveKeyboard = it },
                )
                ChoiceQuestion(
                    question = "How did the pet move?",
                    options = PetMovementQuality.entries,
                    labelOf = PetMovementQuality::label,
                    selected = petMovementQuality,
                    onSelect = { petMovementQuality = it },
                )
                ChoiceQuestion(
                    question = "Was the pet returned to its original position after the keyboard closed?",
                    options = HumanAnswer.entries,
                    labelOf = HumanAnswer::label,
                    selected = petReturned,
                    onSelect = { petReturned = it },
                )
            }
        },
        confirmButton = {
            // Every question must be answered. A partially answered run would write defaults into
            // the findings file, and a defaulted value read as a measurement is precisely what
            // invalidated the previous instrument's keyboard-coverage result.
            val complete = keyboardAppeared != null && fieldVisibility != null &&
                cardMoved != null && placementAcceptable != null &&
                videoPaused != null && focusReturned != null &&
                petVisibleAboveKeyboard != null && petMovementQuality != null &&
                petReturned != null
            TextButton(
                enabled = complete,
                onClick = {
                    onAnswered(
                        HumanAnswers(
                            keyboardAppeared = keyboardAppeared!!,
                            fieldVisibility = fieldVisibility!!,
                            cardMovedOnFocus = cardMoved!!,
                            placementAcceptable = placementAcceptable!!,
                            videoPaused = videoPaused!!,
                            focusReturned = focusReturned!!,
                            petVisibleAboveKeyboard = petVisibleAboveKeyboard!!,
                            petMovementQuality = petMovementQuality!!,
                            petReturnedToOriginalPosition = petReturned!!,
                        ),
                    )
                },
            ) { Text("Save answers") }
        },
    )
}

@Composable
private fun <T> ChoiceQuestion(
    question: String,
    options: List<T>,
    labelOf: (T) -> String,
    selected: T?,
    onSelect: (T) -> Unit,
) {
    Column {
        Text(question)
        // Scrollable: the visibility question has four options and would otherwise clip its last
        // one off the dialog edge, making an answer unreachable rather than merely ugly.
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            options.forEach { option ->
                TextButton(onClick = { onSelect(option) }) {
                    val label = labelOf(option)
                    Text(if (selected == option) "[$label]" else label)
                }
            }
        }
    }
}
