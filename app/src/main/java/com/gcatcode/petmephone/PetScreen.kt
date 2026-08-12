package com.gcatcode.petmephone

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.gcatcode.petmephone.core.domain.character.ActiveCharacterRepository
import com.gcatcode.petmephone.core.domain.character.CharacterId
import com.gcatcode.petmephone.core.domain.character.CharacterRepository
import com.gcatcode.petmephone.feature.overlay.character.BuiltInCharacters

const val PET_SCREEN_TEST_TAG = "pet_screen"
const val PET_STATUS_TEST_TAG = "pet_status"
const val PET_ACTIVE_CHARACTER_TEST_TAG = "pet_active_character"

/**
 * Where the app opens: whether the pet is on screen, and who it currently is.
 *
 * Both values are read live rather than remembered. The overlay grant can be revoked by the system
 * without the app being involved, and the active character can change from the library in the same
 * session, so a cached answer here would be a claim the app has not checked.
 */
@Composable
fun PetScreen(
    isOnScreen: Boolean,
    characterRepository: CharacterRepository,
    activeCharacterRepository: ActiveCharacterRepository,
    onGrantPermission: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val activeId by activeCharacterRepository.active.collectAsState(initial = null)
    val imported by characterRepository.importedCharacters.collectAsState(initial = emptyList())

    Column(modifier = modifier.fillMaxWidth().padding(16.dp).testTag(PET_SCREEN_TEST_TAG)) {
        Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
            Text("On screen", modifier = Modifier.weight(1f))
            Text(
                text = if (isOnScreen) "Yes" else "No",
                modifier = Modifier.testTag(PET_STATUS_TEST_TAG),
            )
        }

        Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
            Text("Active character", modifier = Modifier.weight(1f))
            // Until the first emission arrives there is no answer to give, so the value is left
            // blank rather than filled with a guess at the built-in. An em dash is "not read yet",
            // which is true; naming a character we have not resolved would not be.
            val name = activeId?.let { id ->
                val known = BuiltInCharacters.all + imported
                known.firstOrNull { it.id == id }?.name?.value
                    ?: (id as? CharacterId.BuiltIn)?.name
                    ?: stringResourceUnnamed()
            } ?: "—"
            Text(text = name, modifier = Modifier.testTag(PET_ACTIVE_CHARACTER_TEST_TAG))
        }

        if (!isOnScreen) {
            // Stated plainly, with the action attached. Without the grant the pet cannot exist, and
            // saying so is more useful than a screen that quietly shows "No".
            Text(
                text = "PetMePhone needs permission to draw over other apps before the pet can " +
                    "appear. Nothing else in the app depends on it.",
                modifier = Modifier.padding(vertical = 8.dp),
            )
            Button(onClick = onGrantPermission) { Text("Read about the permission") }
        }
    }
}

/** An imported character whose name was never captured; mirrors the library's own placeholder. */
private fun stringResourceUnnamed(): String = "Unnamed character"
