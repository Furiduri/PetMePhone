package com.gcatcode.petmephone.feature.overlay.character.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.gcatcode.petmephone.core.domain.character.Character
import com.gcatcode.petmephone.core.domain.character.CharacterId
import com.gcatcode.petmephone.core.domain.character.CharacterLibraryConfig
import com.gcatcode.petmephone.core.domain.character.CharacterRepository
import com.gcatcode.petmephone.feature.overlay.R
import com.gcatcode.petmephone.feature.overlay.character.BuiltInCharacters
import kotlinx.coroutines.launch

/**
 * Shows built-in and imported characters together (character-import: "built-in and imported
 * characters share one model and one render path"). Delete is offered only for imported entries —
 * built-ins are never deletable, per the same requirement. Deletion removes the whole character
 * folder, never just its `idle.png` (delegated to [CharacterRepository.remove], which does the
 * folder deletion — see `CharacterRepositoryImpl`).
 *
 * When the imported count has reached [CharacterLibraryConfig.maxImportedCharacters], this screen
 * surfaces the same cap-reached message the importer would produce, so the reason import is
 * unavailable is visible before the user even tries.
 */
@Composable
fun LibraryScreen(
    repository: CharacterRepository,
    config: CharacterLibraryConfig,
    onImportClick: () -> Unit,
) {
    val importedIds by repository.importedCharacters.collectAsState(initial = emptySet())
    val scope = rememberCoroutineScope()

    val importedCharacters: List<Character> = importedIds
        .sortedBy { it.uuid }
        .map { id -> Character(id = id, displayName = "Imported character") }
    val characters: List<Character> = BuiltInCharacters.all + importedCharacters
    val capReached = importedIds.size >= config.maxImportedCharacters

    Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
        Text("Character library")

        LazyColumn {
            items(characters, key = { it.id.libraryKey() }) { character ->
                Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    Text(character.displayName)
                    // No delete affordance for built-ins — they are never deletable.
                    val importedId = character.id as? CharacterId.Imported
                    if (importedId != null) {
                        Button(onClick = { scope.launch { repository.remove(importedId) } }) {
                            Text("Delete")
                        }
                    }
                }
            }
        }

        if (capReached) {
            Text(stringResource(R.string.import_rejection_cap_reached, config.maxImportedCharacters))
        } else {
            Button(onClick = onImportClick) { Text("Import a character") }
        }
    }
}

private fun CharacterId.libraryKey(): String = when (this) {
    is CharacterId.BuiltIn -> "builtin:$name"
    is CharacterId.Imported -> "imported:$uuid"
}
