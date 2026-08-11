package com.gcatcode.petmephone.feature.overlay.character.ui

import androidx.compose.foundation.clickable
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
import com.gcatcode.petmephone.core.domain.character.ActiveCharacterRepository
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
 *
 * Tapping a row calls [ActiveCharacterRepository.setActive] — the identical call for a built-in
 * and an imported id, with no id-type branching at this call site (`[IMPORT-7]`).
 */
@Composable
fun LibraryScreen(
    repository: CharacterRepository,
    activeCharacterRepository: ActiveCharacterRepository,
    config: CharacterLibraryConfig,
    onImportClick: () -> Unit,
) {
    val importedCharacters by repository.importedCharacters.collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()

    val characters: List<Character> = BuiltInCharacters.all + importedCharacters.sortedBy {
        (it.id as CharacterId.Imported).uuid
    }
    val capReached = importedCharacters.size >= config.maxImportedCharacters

    Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
        Text("Character library")

        LazyColumn {
            items(characters, key = { it.id.libraryKey() }) { character ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                        .clickable {
                            scope.launch { activeCharacterRepository.setActive(character.id) }
                        },
                ) {
                    // An absent name renders an honest placeholder — never the old fixed
                    // "Imported character" string standing in for a real one (design decision 14).
                    Text(character.name?.value ?: stringResource(R.string.character_unnamed))
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
