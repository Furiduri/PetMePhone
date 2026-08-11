package com.gcatcode.petmephone.feature.overlay.character.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.gcatcode.petmephone.core.domain.pet.sprite.SpriteGridDeclaration
import com.gcatcode.petmephone.feature.overlay.character.messageResource

/**
 * Picking entry point. Launching `PickVisualMedia` (Photo Picker) itself happens outside this
 * module (an `@AndroidEntryPoint` Activity in `:app`, out of scope here) — this composable never
 * constructs a permission request or an `ActivityResultLauncher` of its own; [onPickImage] is the
 * only hook it exposes. No storage permission is requested anywhere in this flow.
 */
@Composable
fun ImportScreen(
    controller: CharacterImportController,
    onPickImage: () -> Unit,
    onValidated: (CharacterImportController) -> Unit,
) {
    val state by controller.state.collectAsState()

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        when (val current = state) {
            CharacterImportController.State.Idle -> {
                Text("Pick a character image to import.")
                Button(onClick = onPickImage) { Text("Choose photo") }
            }
            CharacterImportController.State.Staging -> {
                Text("Checking the file…")
                CircularProgressIndicator()
            }
            is CharacterImportController.State.GridEntry -> {
                // The grid is declared, not inferred (design decision 13): these fields are
                // pre-filled with a detected candidate but always user-editable, and "Preview" is
                // the only path into the costly decode.
                var columnsText by remember(current) { mutableStateOf(current.candidate.columns.toString()) }
                var rowsText by remember(current) { mutableStateOf(current.candidate.rows.toString()) }

                Text("How many columns and rows does this sheet have?")
                OutlinedTextField(
                    value = columnsText,
                    onValueChange = { columnsText = it },
                    label = { Text("Columns") },
                )
                OutlinedTextField(
                    value = rowsText,
                    onValueChange = { rowsText = it },
                    label = { Text("Rows") },
                )
                Button(onClick = {
                    val columns = columnsText.toIntOrNull()
                    val rows = rowsText.toIntOrNull()
                    if (columns != null && rows != null) {
                        controller.onGridConfirmed(SpriteGridDeclaration(columns = columns, rows = rows))
                    }
                }) { Text("Preview") }
            }
            CharacterImportController.State.DecodingAndScanning -> {
                Text("Reading the animation…")
                CircularProgressIndicator()
            }
            is CharacterImportController.State.Rejected -> {
                val (resId, args) = current.reason.messageResource()
                Text(stringResource(resId, *args.toTypedArray()))
                Button(onClick = { controller.reset() }) { Text("Try again") }
            }
            is CharacterImportController.State.Ready -> {
                // Preview is shown before commit, per character-import's "the user sees a preview
                // before confirming" requirement — this screen never finalizes the import itself.
                // A `LaunchedEffect`, not a direct call, so navigating away happens once per
                // `Ready` identity rather than on every recomposition.
                LaunchedEffect(current) { onValidated(controller) }
            }
        }
    }
}
