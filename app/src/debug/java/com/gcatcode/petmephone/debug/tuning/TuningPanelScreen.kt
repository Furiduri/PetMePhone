package com.gcatcode.petmephone.debug.tuning

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.gcatcode.petmephone.core.domain.balance.BalanceConfig
import com.gcatcode.petmephone.core.domain.config.ConfigField
import com.gcatcode.petmephone.core.domain.config.ConfigWriteResult
import com.gcatcode.petmephone.feature.overlay.ui.PetAnimationConfig
import kotlinx.coroutines.launch

/** Order matches [TuningPanelViewModel.rows] exactly — the same eight fields, same order. */
private val TUNING_FIELDS: List<ConfigField<*>> = listOf(
    BalanceConfig.DAILY_TASK_GOAL,
    BalanceConfig.HUNGRY_THRESHOLD_RATIO,
    BalanceConfig.RECURRING_HUNGER_RATIO,
    BalanceConfig.RECURRING_HUNGER_CAP,
    BalanceConfig.STANDARD_TASK_POINTS,
    PetAnimationConfig.FRAME_INTERVAL_MILLIS,
    PetAnimationConfig.MIN_FRAME_INTERVAL_MILLIS,
    PetAnimationConfig.STATE_SHARING_TIMEOUT_MILLIS,
)

const val TUNING_PANEL_TEST_TAG = "tuning_panel"
const val TUNING_RESET_ALL_TEST_TAG = "tuning_reset_all"
const val TUNING_RESTART_TEST_TAG = "tuning_restart"

/**
 * The debug-only panel: all eight registered fields, each showing shipped default, current value,
 * overridden marker, staleness label, and live/deferred label. Names none of the four unreachable
 * configs (`debug-tuning-panel` spec) — nothing here references `PetStateConfig`,
 * `CharacterLibraryConfig`, `OverlayPositionConfig`, or `QuickMenuConfig`.
 */
@Composable
fun TuningPanelScreen(viewModel: TuningPanelViewModel) {
    val rows by viewModel.rows.collectAsState()
    val balanceInUse by viewModel.balanceConfigSource.config.collectAsState()
    val petAnimationInUse by viewModel.petAnimationConfigSource.config.collectAsState()
    val declaredFrameDuration by viewModel.declaredFrameDurationMillis.collectAsState()
    var showResetAllConfirm by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .testTag(TUNING_PANEL_TEST_TAG),
    ) {
        Text(
            text = "PetMePhone balance tuning panel",
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(12.dp),
        )
        Text("in use — balance: $balanceInUse", modifier = Modifier.padding(horizontal = 12.dp))
        Text("in use — pet animation: $petAnimationInUse", modifier = Modifier.padding(horizontal = 12.dp))

        Row(modifier = Modifier.padding(12.dp)) {
            Button(
                onClick = { showResetAllConfirm = true },
                modifier = Modifier.testTag(TUNING_RESET_ALL_TEST_TAG),
            ) { Text("Reset all") }
            Button(
                onClick = { viewModel.restartOverlay() },
                modifier = Modifier.testTag(TUNING_RESTART_TEST_TAG),
            ) { Text("Restart overlay") }
        }

        HorizontalDivider()

        LazyColumn(modifier = Modifier.fillMaxWidth()) {
            items(rows.size) { index ->
                @Suppress("UNCHECKED_CAST")
                val field = TUNING_FIELDS[index] as ConfigField<Comparable<Any>>
                TuningFieldRow(
                    row = rows[index],
                    field = field,
                    viewModel = viewModel,
                    declaredFrameDurationMillis = declaredFrameDuration,
                )
                HorizontalDivider()
            }
        }
    }

    if (showResetAllConfirm) {
        AlertDialog(
            onDismissRequest = { showResetAllConfirm = false },
            title = { Text("Reset every override?") },
            text = { Text("Every one of the eight fields currently holding an entry is deleted.") },
            confirmButton = {
                Button(onClick = {
                    viewModel.resetAll()
                    showResetAllConfirm = false
                }) { Text("Reset all") }
            },
            dismissButton = {
                Button(onClick = { showResetAllConfirm = false }) { Text("Cancel") }
            },
        )
    }
}

/**
 * One row. An out-of-range submission keeps the typed text on screen beside the expected range
 * instead of reverting — [text] is only ever synced from the store on a successful write or a
 * reset, never on rejection.
 */
@Composable
private fun <T : Comparable<T>> TuningFieldRow(
    row: TuningRow,
    field: ConfigField<T>,
    viewModel: TuningPanelViewModel,
    declaredFrameDurationMillis: Long?,
) {
    var text by remember(row.key) { mutableStateOf(row.currentValue) }
    var rejection by remember(row.key) { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    Column(modifier = Modifier.fillMaxWidth().padding(12.dp).testTag("tuning_row_${row.key}")) {
        Text(row.key, style = MaterialTheme.typography.titleSmall)
        Text("group=${row.groupId}  range=${row.rangeLabel}")
        Text("default=${row.shippedDefault}  current=${row.currentValue}  overridden=${row.overridden}")
        Text("staleness=${stalenessLabel(row.staleness)}  application=${row.application}")

        // `application=LIVE` is true about the flow and misleading about the screen whenever the
        // active character declares its own frame duration: `AnimationPacing` prefers the declared
        // value, so this field changes nothing and only the floor still applies. Saying so here is
        // the difference between a maintainer reading a number and a maintainer concluding the
        // panel is broken.
        if (row.key == PetAnimationConfig.FRAME_INTERVAL_MILLIS.key && declaredFrameDurationMillis != null) {
            Text(
                text = "no effect right now: the active character declares its own frame duration " +
                    "(${declaredFrameDurationMillis}ms), which wins over this value. " +
                    "${PetAnimationConfig.MIN_FRAME_INTERVAL_MILLIS.key} still applies as a floor.",
                modifier = Modifier.testTag("tuning_no_effect_${row.key}"),
            )
        }

        Row {
            // `weight` is load-bearing, not cosmetic. Without it the text field claims its full
            // preferred width and pushes Reset past the right edge, where it cannot be tapped at
            // all — the per-field reset requirement then exists in code and is unreachable on a
            // real phone. Found on a 1220px-wide device; a Compose test never sees it, because the
            // test harness gives the screen whatever width the content asks for.
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                modifier = Modifier
                    .weight(1f)
                    .testTag("tuning_input_${row.key}"),
            )
            Button(
                modifier = Modifier.testTag("tuning_set_${row.key}"),
                onClick = {
                scope.launch {
                    when (val parsed = parseTypedValue(field, text)) {
                        is ParsedInput.Unparseable -> rejection = unparseableMessage(field)
                        is ParsedInput.Valid -> {
                            when (val result = viewModel.set(field, parsed.value)) {
                                ConfigWriteResult.Accepted -> {
                                    rejection = null
                                    text = parsed.value.toString()
                                }
                                is ConfigWriteResult.OutOfRange<*> -> rejection = rejectionMessage(result)
                            }
                        }
                    }
                }
            }) { Text("Set") }
            Button(
                onClick = {
                    viewModel.reset(field)
                    text = row.shippedDefault
                    rejection = null
                },
                modifier = Modifier.testTag("tuning_reset_${row.key}"),
            ) { Text("Reset") }
        }

        rejection?.let { message ->
            Text(
                text = "Rejected: $message",
                modifier = Modifier.testTag("tuning_rejection_${row.key}"),
            )
        }
    }
}

private fun stalenessLabel(staleness: Staleness): String = when (staleness) {
    Staleness.Fresh -> "fresh"
    is Staleness.Stale -> "stale (was v${staleness.writtenUnderVersion})"
    Staleness.NotVersioned -> "not versioned"
}
