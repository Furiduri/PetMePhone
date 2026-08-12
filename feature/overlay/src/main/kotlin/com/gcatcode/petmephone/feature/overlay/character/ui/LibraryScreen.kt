package com.gcatcode.petmephone.feature.overlay.character.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TextButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
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
    // Until the first emission there is no active character to mark, so none is marked. Assuming
    // the built-in would put "On screen" next to a character that may not be the one on screen.
    val activeId by activeCharacterRepository.active.collectAsState(initial = null)
    val scope = rememberCoroutineScope()

    val characters: List<Character> = BuiltInCharacters.all + importedCharacters.sortedBy {
        (it.id as CharacterId.Imported).uuid
    }
    val capReached = importedCharacters.size >= config.maxImportedCharacters

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        // Title and the import action share one row: the action belongs to the list as a whole, and
        // putting it beside the heading keeps it on screen no matter how long the list gets.
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Character library",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.weight(1f),
            )
            if (!capReached) {
                Button(
                    onClick = onImportClick,
                    modifier = Modifier.heightIn(min = 48.dp).testTag(LIBRARY_IMPORT_TEST_TAG),
                ) {
                    Text("Import")
                }
            }
        }

        if (capReached) {
            Text(
                text = stringResource(R.string.feature_overlay_import_rejection_cap_reached, config.maxImportedCharacters),
                modifier = Modifier.padding(bottom = 8.dp),
            )
        }

        HorizontalDivider()

        // Weighted, so the list takes the space that is left rather than all of it. Unweighted, a
        // LazyColumn inside a Column claims the full height and anything after it is pushed off the
        // screen entirely.
        LazyColumn(modifier = Modifier.weight(1f)) {
            items(characters, key = { it.id.libraryKey() }) { character ->
                val isActive = character.id == activeId
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 56.dp)
                        .clickable {
                            scope.launch { activeCharacterRepository.setActive(character.id) }
                        }
                        .padding(horizontal = 4.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        // An absent name renders an honest placeholder — never the old fixed
                        // "Imported character" string standing in for a real one (design decision 14).
                        Text(
                            text = character.name?.value ?: stringResource(R.string.feature_overlay_character_unnamed),
                            style = MaterialTheme.typography.bodyLarge,
                        )
                        // Which one is on screen right now, stated rather than left to be inferred
                        // from a colour a colour-blind user may not separate.
                        if (isActive) {
                            Text(
                                text = "On screen",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }

                    // No delete affordance for built-ins — they are never deletable.
                    val importedId = character.id as? CharacterId.Imported
                    if (importedId != null) {
                        TextButton(
                            onClick = { scope.launch { repository.remove(importedId) } },
                            modifier = Modifier.heightIn(min = 48.dp).padding(start = 8.dp),
                        ) {
                            Text("Delete")
                        }
                    }
                }
                HorizontalDivider()
            }
        }
    }
}

const val LIBRARY_IMPORT_TEST_TAG = "library_import"

private fun CharacterId.libraryKey(): String = when (this) {
    is CharacterId.BuiltIn -> "builtin:$name"
    is CharacterId.Imported -> "imported:$uuid"
}
