package com.gcatcode.petmephone

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.activity.compose.BackHandler
import com.gcatcode.petmephone.core.domain.character.ActiveCharacterRepository
import com.gcatcode.petmephone.core.domain.character.CharacterLibraryConfig
import com.gcatcode.petmephone.core.domain.character.CharacterRepository
import com.gcatcode.petmephone.core.domain.permission.OverlaySettingsLauncher
import com.gcatcode.petmephone.feature.overlay.character.CharacterImporter
import com.gcatcode.petmephone.feature.overlay.character.ValidatedImport
import com.gcatcode.petmephone.feature.overlay.character.ui.CharacterImportController
import com.gcatcode.petmephone.feature.overlay.character.ui.ImportScreen
import com.gcatcode.petmephone.feature.overlay.character.ui.LibraryScreen
import com.gcatcode.petmephone.feature.overlay.character.ui.PreviewScreen
import com.gcatcode.petmephone.feature.overlay.onboarding.OverlayOnboardingScreen
import com.gcatcode.petmephone.feature.overlay.onboarding.OverlayOnboardingViewModel
import com.gcatcode.petmephone.feature.overlay.onboarding.ReEntryCard

/**
 * Where the app can be. Four destinations, no deep links and no process-death restore requirement,
 * so a sealed type and a `when` carry the whole graph — a routing library would add a dependency
 * and a vocabulary this codebase uses nowhere else.
 */
sealed interface Destination {
    data object Onboarding : Destination
    data object Library : Destination
    data object Import : Destination
    data class Preview(val import: ValidatedImport) : Destination
}

const val PET_ME_APP_TEST_TAG = "pet_me_app"
const val ONBOARDING_DECLINE_TEST_TAG = "onboarding_decline"

/**
 * The app's only navigation host.
 *
 * Every screen below already existed, tested, with the callbacks this function passes; none of them
 * changed to be reachable. What was missing was the caller.
 *
 * The permission decides where the app *opens*, never whether it is usable: declining leaves the
 * user in the library with the re-entry card, because the only thing refusing costs is the pet on
 * screen (`[ONBOARD-4]`). A screen the user cannot leave after declining is the pattern the
 * dark-pattern audit exists to catch.
 */
@Composable
fun PetMeApp(
    onboardingViewModel: OverlayOnboardingViewModel,
    settingsLauncher: OverlaySettingsLauncher,
    characterRepository: CharacterRepository,
    activeCharacterRepository: ActiveCharacterRepository,
    libraryConfig: CharacterLibraryConfig,
    importController: CharacterImportController,
    importer: CharacterImporter,
    onPickImage: () -> Unit,
    permissionGranted: Boolean,
    onPermissionGranted: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var destination: Destination by remember {
        mutableStateOf(if (permissionGranted) Destination.Library else Destination.Onboarding)
    }

    val isGranted by onboardingViewModel.isGranted.collectAsState()
    val importedCharacters by characterRepository.importedCharacters.collectAsState(initial = emptyList())
    var reEntryDismissed by remember { mutableStateOf(false) }

    // The live re-query belongs to the host, not to the onboarding screen alone. Opening straight
    // into the library means that screen never composes, so leaving the query there would leave
    // `isGranted` false while the permission was in fact granted — and the re-entry card would
    // offer to fix something that is not broken.
    LaunchedEffect(Unit) { onboardingViewModel.onResume() }

    // Granting through Settings and coming back advances the flow with no manual refresh, because
    // the re-query above is re-run on resume (`[ONBOARD-3]`).
    LaunchedEffect(isGranted) {
        if (isGranted) {
            onPermissionGranted()
            if (destination == Destination.Onboarding) destination = Destination.Library
        }
    }

    BackHandler(enabled = destination != Destination.Library) {
        destination = when (destination) {
            is Destination.Preview -> Destination.Import
            else -> Destination.Library
        }
    }

    Column(modifier = modifier.fillMaxSize().testTag(PET_ME_APP_TEST_TAG)) {
        when (val current = destination) {
            Destination.Onboarding -> {
                // Weighted, not free-standing: the screen fills its own space internally, so
                // without this it takes the whole column and pushes the exit below the fold —
                // which would make "you can always decline" true in code and false on screen.
                OverlayOnboardingScreen(
                    viewModel = onboardingViewModel,
                    settingsLauncher = settingsLauncher,
                    modifier = Modifier.weight(1f),
                )
                // The way out. The screen itself has no decline control, and rather than change a
                // tested composable the host supplies the exit — declining is a navigation
                // decision, not something the onboarding copy needs to own.
                TextButton(
                    onClick = { destination = Destination.Library },
                    modifier = Modifier.padding(horizontal = 24.dp).testTag(ONBOARDING_DECLINE_TEST_TAG),
                ) {
                    Text("Not now")
                }
            }

            Destination.Library -> {
                if (!isGranted && !reEntryDismissed) {
                    ReEntryCard(
                        onReopenOnboarding = { destination = Destination.Onboarding },
                        onDismiss = { reEntryDismissed = true },
                        modifier = Modifier.padding(16.dp),
                    )
                }
                LibraryScreen(
                    repository = characterRepository,
                    activeCharacterRepository = activeCharacterRepository,
                    config = libraryConfig,
                    onImportClick = { destination = Destination.Import },
                )
            }

            Destination.Import -> {
                ImportScreen(
                    controller = importController,
                    onPickImage = onPickImage,
                    onValidated = { controller ->
                        val state = controller.state.value
                        if (state is CharacterImportController.State.Ready) {
                            destination = Destination.Preview(state.import)
                        }
                    },
                )
            }

            is Destination.Preview -> {
                PreviewScreen(
                    import = current.import,
                    importer = importer,
                    repository = characterRepository,
                    activeCharacterRepository = activeCharacterRepository,
                    currentImportedCount = importedCharacters.size,
                    onImported = {
                        importController.reset()
                        destination = Destination.Library
                    },
                    onCancel = {
                        importController.reset()
                        destination = Destination.Library
                    },
                )
            }
        }
    }
}
