package com.gcatcode.petmephone.feature.overlay.character.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.gcatcode.petmephone.core.domain.character.Character
import com.gcatcode.petmephone.core.domain.character.CharacterId
import com.gcatcode.petmephone.core.domain.character.CharacterName
import com.gcatcode.petmephone.core.domain.character.CharacterRepository
import com.gcatcode.petmephone.feature.overlay.character.CharacterImporter
import com.gcatcode.petmephone.feature.overlay.character.ValidatedImport
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Shown after the declared grid decodes and scans successfully, before the import commits
 * (character-import spec: "the user sees a preview with grid, per-row playback, and row-to-state
 * mapping before confirming"). The move into `filesDir/characters/<uuid>/idle.png` happens only
 * when the user presses confirm — never before.
 *
 * Also captures the character's name in this same step (design decision 14): the field starts
 * empty, never pre-filled with a fabricated default. An empty field on confirm persists `name =
 * null` — absence stays absent, never a blank string standing in for a real name.
 *
 * Playback advances across the full declared grid (every row, left to right then top to bottom),
 * not just a single row — this decoder no longer assumes one row per sheet.
 */
@Composable
fun PreviewScreen(
    import: ValidatedImport,
    importer: CharacterImporter,
    repository: CharacterRepository,
    currentImportedCount: Int,
    frameIntervalMillis: Long = 150L,
    onImported: (CharacterId.Imported) -> Unit,
    onCancel: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val layout = import.decoded.layout
    val bitmap = import.decoded.bitmap

    var frameIndex by remember(import) { mutableIntStateOf(0) }
    var nameText by remember(import) { mutableStateOf("") }

    LaunchedEffect(import) {
        while (isActive) {
            delay(frameIntervalMillis)
            if (layout.frameCount > 0) {
                frameIndex = (frameIndex + 1) % layout.frameCount
            }
        }
    }

    Column(modifier = Modifier.padding(16.dp)) {
        Text("Preview")
        // Row-to-state mapping: this decoder always maps the sheet to the IDLE animation
        // (one-file-per-animation, per design decision 10).
        Text("Maps to: IDLE")

        OutlinedTextField(
            value = nameText,
            onValueChange = { nameText = it },
            label = { Text("Name (optional)") },
        )

        Canvas(
            modifier = Modifier.fillMaxWidth().height(120.dp),
        ) {
            if (layout.frameCount == 0) return@Canvas
            val cellSizePx = layout.grid.cellSizePx
            val left = layout.cellLeftPx(frameIndex)
            val top = layout.cellTopPx(frameIndex)
            val side = minOf(size.width, size.height).toInt()
            drawImage(
                image = bitmap,
                srcOffset = IntOffset(left, top),
                srcSize = IntSize(cellSizePx, cellSizePx),
                dstOffset = IntOffset(0, 0),
                dstSize = IntSize(side, side),
                filterQuality = FilterQuality.None,
            )
        }

        Button(onClick = {
            scope.launch {
                val result = importer.confirm(import, currentImportedCount)
                result.onSuccess { id ->
                    repository.add(Character(id = id, name = CharacterName.validOrNull(nameText)))
                    onImported(id)
                }
            }
        }) { Text("Confirm import") }

        Button(onClick = {
            importer.abandon(import)
            onCancel()
        }) { Text("Cancel") }
    }
}
