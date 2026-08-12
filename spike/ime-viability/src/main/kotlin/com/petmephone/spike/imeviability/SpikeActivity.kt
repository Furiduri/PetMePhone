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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import java.time.LocalDateTime

/**
 * Single-activity entry point. Deliberately unmistakable rather than minimal (design.md's
 * usability note): one mode selector, one Start, one Finish, and a running list of what has been
 * captured so far, because the maintainer will be holding a phone and tapping through this once
 * or twice per device.
 */
class SpikeActivity : ComponentActivity() {

    private lateinit var findingsRepository: FindingsRepository
    private var runFinishedReceiver: BroadcastReceiver? = null

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
                        onFinishRun = ::finishRun,
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
     * Registers a receiver just before Finish is expected, so the automatic measurements land
     * paired with the two human answers collected right after — one findings entry per run, never
     * split across files.
     */
    private fun recordHumanAnswers(
        mode: SpikeMode,
        videoPaused: HumanAnswer,
        focusReturned: HumanAnswer,
        onAutomaticResultCaptured: (FindingsEntry) -> Unit,
    ) {
        runFinishedReceiver?.let { unregisterReceiver(it) }
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                val receivedMode = intent.getStringExtra(SpikeOverlayService.EXTRA_MODE)
                    ?.let(SpikeMode::valueOf) ?: mode
                val entry = FindingsEntry(
                    timestamp = LocalDateTime.now(),
                    mode = receivedMode,
                    device = DeviceInfo.capture(),
                    keyboardAppeared = intent.getBooleanExtra(
                        SpikeOverlayService.EXTRA_KEYBOARD_APPEARED,
                        false,
                    ),
                    keyboardCoversField = intent.getBooleanExtra(
                        SpikeOverlayService.EXTRA_KEYBOARD_COVERS_FIELD,
                        false,
                    ),
                    imeInsetCallbackFired = intent.getBooleanExtra(
                        SpikeOverlayService.EXTRA_IME_CALLBACK_FIRED,
                        false,
                    ),
                    windowEverReceivedFocus = intent.getBooleanExtra(
                        SpikeOverlayService.EXTRA_EVER_RECEIVED_FOCUS,
                        false,
                    ),
                    windowRemovedCleanly = intent.getBooleanExtra(
                        SpikeOverlayService.EXTRA_REMOVED_CLEANLY,
                        false,
                    ),
                    videoPausedOnFocus = videoPaused,
                    focusReturnedAfterDismissal = focusReturned,
                )
                findingsRepository.append(entry)
                onAutomaticResultCaptured(entry)
                unregisterReceiver(this)
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
    onFinishRun: () -> Unit,
    onRecordHumanAnswers: (
        mode: SpikeMode,
        videoPaused: HumanAnswer,
        focusReturned: HumanAnswer,
        onCaptured: (FindingsEntry) -> Unit,
    ) -> Unit,
    findingsRepository: FindingsRepository,
) {
    var phase by remember { mutableStateOf(RunPhase.IDLE) }
    var selectedMode by remember { mutableStateOf(SpikeMode.FOCUS_ONLY) }
    var findingsText by remember { mutableStateOf(findingsRepository.readAllOrEmpty()) }
    val context = androidx.compose.ui.platform.LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("IME Viability Spike", style = MaterialTheme.typography.headlineSmall)
        Text(
            "Measures whether in-overlay text entry is viable. Grant overlay permission, pick a " +
                "mode, tap Start, switch to an app playing a video, watch what happens, then tap " +
                "Finish and answer the two questions.",
        )

        if (!hasOverlayPermission()) {
            Button(onClick = onRequestOverlayPermission) {
                Text("Grant overlay permission")
            }
        }

        Text("Mode", style = MaterialTheme.typography.titleMedium)
        // The selected mode is shown by button style, not by a text prefix. The two modes measure
        // different things — focus alone versus focus plus a keyboard — so starting a run in the
        // wrong one silently mislabels the exact distinction this spike exists to make. A filled
        // button against an outlined one is unmistakable at arm's length; a "> " prefix is not.
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SpikeMode.entries.forEach { mode ->
                val selected = mode == selectedMode
                val label = mode.label
                if (selected) {
                    Button(
                        onClick = { selectedMode = mode },
                        enabled = phase == RunPhase.IDLE,
                        modifier = Modifier.semantics { this.selected = true },
                    ) {
                        Text(label)
                    }
                } else {
                    OutlinedButton(
                        onClick = { selectedMode = mode },
                        enabled = phase == RunPhase.IDLE,
                        modifier = Modifier.semantics { this.selected = false },
                    ) {
                        Text(label)
                    }
                }
            }
        }
        Text(
            "Selected: ${selectedMode.label}",
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
                Text("Running: ${selectedMode.label}. Switch to the app under test now.")
                Button(onClick = { phase = RunPhase.ASKING_HUMAN }) { Text("Finish") }
            }

            RunPhase.ASKING_HUMAN -> HumanAnswerDialog(
                mode = selectedMode,
                onAnswered = { videoPaused, focusReturned ->
                    onRecordHumanAnswers(selectedMode, videoPaused, focusReturned) { _ ->
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
    onAnswered: (videoPaused: HumanAnswer, focusReturned: HumanAnswer) -> Unit,
) {
    var videoPaused by remember { mutableStateOf<HumanAnswer?>(null) }
    var focusReturned by remember { mutableStateOf<HumanAnswer?>(null) }

    AlertDialog(
        onDismissRequest = {},
        title = { Text("Confirm what you observed — ${mode.label}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                AnswerQuestion(
                    question = "Did the video underneath pause when the window took focus?",
                    selected = videoPaused,
                    onSelect = { videoPaused = it },
                )
                AnswerQuestion(
                    question = "Did focus return correctly to the app underneath after dismissal?",
                    selected = focusReturned,
                    onSelect = { focusReturned = it },
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = videoPaused != null && focusReturned != null,
                onClick = { onAnswered(videoPaused!!, focusReturned!!) },
            ) { Text("Save answers") }
        },
    )
}

@Composable
private fun AnswerQuestion(
    question: String,
    selected: HumanAnswer?,
    onSelect: (HumanAnswer) -> Unit,
) {
    Column {
        Text(question)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            HumanAnswer.entries.forEach { answer ->
                TextButton(onClick = { onSelect(answer) }) {
                    Text(if (selected == answer) "[${answer.label}]" else answer.label)
                }
            }
        }
    }
}
