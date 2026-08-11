package com.gcatcode.petmephone

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.PermanentDrawerSheet
import androidx.compose.material3.PermanentNavigationDrawer
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.material3.rememberDrawerState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.gcatcode.petmephone.core.domain.character.ActiveCharacterRepository
import com.gcatcode.petmephone.core.domain.character.CharacterLibraryConfig
import com.gcatcode.petmephone.core.domain.character.CharacterRepository
import com.gcatcode.petmephone.core.domain.permission.OverlaySettingsLauncher
import com.gcatcode.petmephone.feature.overlay.character.CharacterImporter
import com.gcatcode.petmephone.feature.overlay.character.ui.CharacterImportController
import com.gcatcode.petmephone.feature.overlay.character.ui.ImportScreen
import com.gcatcode.petmephone.feature.overlay.character.ui.LibraryScreen
import com.gcatcode.petmephone.feature.overlay.character.ui.PreviewScreen
import com.gcatcode.petmephone.feature.overlay.onboarding.OverlayOnboardingScreen
import com.gcatcode.petmephone.feature.overlay.onboarding.OverlayOnboardingViewModel
import kotlinx.coroutines.launch

const val PET_ME_APP_TEST_TAG = "pet_me_app"
const val MENU_BUTTON_TEST_TAG = "menu_button"
const val MENU_SHEET_TEST_TAG = "menu_sheet"

/**
 * Material's compact/medium boundary. Below it a permanently open menu would cost about a quarter
 * of the width; at or above it there is room to leave the menu in place.
 */
private val EXPANDED_WIDTH_THRESHOLD = 600.dp

/**
 * The app's shell: one menu, every feature an entry in it.
 *
 * The menu presents itself by available width rather than being two different navigations — it
 * slides over the content on a phone and stays open on anything wider, from a single list of
 * destinations. A permanently open sidebar on a 375dp screen would spend a quarter of it on four
 * rows the user reads for a second at a time.
 *
 * The permission decides where the app opens, never whether it is usable: without it the pet cannot
 * appear, and everything else — the library, importing, reading what the permission does — works
 * exactly the same.
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
    onPermissionGranted: () -> Unit,
    modifier: Modifier = Modifier,
    expandedWidthThreshold: Dp = EXPANDED_WIDTH_THRESHOLD,
) {
    var destination: Destination by remember { mutableStateOf(Destination.Pet) }
    val isGranted by onboardingViewModel.isGranted.collectAsState()
    // Read here and passed down so the preview enforces the real cap. A hardcoded count would let
    // an import past the limit the library screen is busy telling the user about.
    val imported by characterRepository.importedCharacters.collectAsState(initial = emptyList())
    val importedCount = imported.size
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    // The live re-query belongs to the shell, not to the onboarding screen alone: that screen is now
    // one entry among several, so leaving the query there would leave every other destination
    // reading a grant that was never checked.
    LaunchedEffect(Unit) { onboardingViewModel.onResume() }
    LaunchedEffect(isGranted) { if (isGranted) onPermissionGranted() }

    val navigate: (Destination) -> Unit = { target ->
        destination = target
        scope.launch { drawerState.close() }
    }

    BoxWithConstraints(modifier = modifier.fillMaxSize().testTag(PET_ME_APP_TEST_TAG)) {
        val menuStaysOpen = maxWidth >= expandedWidthThreshold

        val menu: @Composable () -> Unit = {
            MenuSheet(

                current = destination,
                onSelect = navigate,
            )
        }

        val content: @Composable () -> Unit = {
            // Android 15 and up force edge-to-edge, so without this the app draws underneath the
            // status bar — on this device a 130px strip that swallowed the top of the bar and most
            // of the only button on the screen. The drawer surface itself stays edge to edge, which
            // is what a Material drawer is supposed to do; only the content is inset.
            Column(modifier = Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
                if (!menuStaysOpen) {
                    TopBar(
                        title = (destination as? MenuDestination)?.label ?: "Preview",
                        onOpenMenu = { scope.launch { drawerState.open() } },
                    )
                }
                Destinations(
                    destination = destination,
                    onDestination = { destination = it },
                    importedCount = importedCount,
                    isGranted = isGranted,
                    onboardingViewModel = onboardingViewModel,
                    settingsLauncher = settingsLauncher,
                    characterRepository = characterRepository,
                    activeCharacterRepository = activeCharacterRepository,
                    libraryConfig = libraryConfig,
                    importController = importController,
                    importer = importer,
                    onPickImage = onPickImage,
                )
            }
        }

        // Back closes the menu before it changes destination, and Pet is the resting place: the
        // first back press from anywhere else returns there rather than leaving the app.
        BackHandler(enabled = drawerState.isOpen || destination != Destination.Pet) {
            when {
                drawerState.isOpen -> scope.launch { drawerState.close() }
                destination is Destination.Preview -> destination = Destination.Import
                else -> destination = Destination.Pet
            }
        }

        if (menuStaysOpen) {
            PermanentNavigationDrawer(drawerContent = { PermanentDrawerSheet { menu() } }) { content() }
        } else {
            ModalNavigationDrawer(
                drawerState = drawerState,
                drawerContent = { ModalDrawerSheet { menu() } },
                content = content,
            )
        }
    }
}

@Composable
private fun MenuSheet(

    current: Destination,
    onSelect: (Destination) -> Unit,
) {
    Column(
        modifier = Modifier
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(12.dp)
            .testTag(MENU_SHEET_TEST_TAG),
    ) {
        Text(
            text = "PetMePhone",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(12.dp),
        )
        for (entry in MENU_DESTINATIONS) {
            val label = (entry as MenuDestination).label
            NavigationDrawerItem(
                label = { Text(label) },
                selected = entry == current,
                onClick = { onSelect(entry) },
                modifier = Modifier.testTag(menuItemTestTag(label)),
            )
        }
        // Nothing else belongs here yet. Slice 7's task list, statistics and journal are absent
        // rather than disabled: a row that opens nothing is a promise the app cannot keep.
    }
}

fun menuItemTestTag(label: String): String = "menu_item_${label.lowercase()}"

/**
 * The bar that opens the menu.
 *
 * A filled button rather than bare text: the previous version was a `TextButton`, which on this
 * surface is indistinguishable from a label, so the only control on the screen did not read as one.
 * 48dp minimum and a content description are the same accessibility floor the onboarding card's
 * buttons meet (`[ONBOARD-7]`) — a control the user cannot reliably hit is not a control.
 */
@Composable
private fun TopBar(title: String, onOpenMenu: () -> Unit) {
    Surface(tonalElevation = 2.dp, modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Button(
                onClick = onOpenMenu,
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .widthIn(min = 48.dp)
                    .semantics { contentDescription = "Open the menu" }
                    .testTag(MENU_BUTTON_TEST_TAG),
            ) {
                Text("☰  Menu")
            }
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(start = 16.dp),
            )
        }
    }
}

@Composable
private fun Destinations(
    destination: Destination,
    onDestination: (Destination) -> Unit,
    importedCount: Int,
    isGranted: Boolean,
    onboardingViewModel: OverlayOnboardingViewModel,
    settingsLauncher: OverlaySettingsLauncher,
    characterRepository: CharacterRepository,
    activeCharacterRepository: ActiveCharacterRepository,
    libraryConfig: CharacterLibraryConfig,
    importController: CharacterImportController,
    importer: CharacterImporter,
    onPickImage: () -> Unit,
) {
    // Sealed `when` with no `else`: a new destination fails to compile until it is drawn.
    when (destination) {
        Destination.Pet -> PetScreen(
            isOnScreen = isGranted,
            characterRepository = characterRepository,
            activeCharacterRepository = activeCharacterRepository,
            onGrantPermission = { onDestination(Destination.Permission) },
        )

        Destination.Characters -> LibraryScreen(
            repository = characterRepository,
            activeCharacterRepository = activeCharacterRepository,
            config = libraryConfig,
            onImportClick = { onDestination(Destination.Import) },
        )

        Destination.Import -> ImportScreen(
            controller = importController,
            onPickImage = onPickImage,
            onValidated = { controller ->
                val state = controller.state.value
                if (state is CharacterImportController.State.Ready) {
                    onDestination(Destination.Preview(state.import))
                }
            },
        )

        // Reachable on purpose, not only when the grant is missing. A revocable permission deserves
        // somewhere to go and read what it does before changing your mind about it.
        Destination.Permission -> OverlayOnboardingScreen(
            viewModel = onboardingViewModel,
            settingsLauncher = settingsLauncher,
        )

        is Destination.Preview -> PreviewScreen(
            import = destination.import,
            importer = importer,
            repository = characterRepository,
            activeCharacterRepository = activeCharacterRepository,
            currentImportedCount = importedCount,
            onImported = {
                importController.reset()
                onDestination(Destination.Characters)
            },
            onCancel = {
                importController.reset()
                onDestination(Destination.Characters)
            },
        )
    }
}
