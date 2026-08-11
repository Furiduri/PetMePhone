package com.gcatcode.petmephone

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import com.gcatcode.petmephone.core.domain.character.ActiveCharacterRepository
import com.gcatcode.petmephone.core.domain.character.CharacterId
import com.gcatcode.petmephone.core.domain.character.CharacterLibraryConfig
import com.gcatcode.petmephone.core.domain.character.CharacterRepository
import com.gcatcode.petmephone.core.domain.permission.OverlayOnboardingHistory
import com.gcatcode.petmephone.core.domain.permission.OverlayOnboardingRepository
import com.gcatcode.petmephone.core.domain.permission.OverlayPermissionChecker
import com.gcatcode.petmephone.core.domain.permission.OverlaySettingsLauncher
import com.gcatcode.petmephone.feature.overlay.character.CharacterImporter
import com.gcatcode.petmephone.feature.overlay.character.ui.CharacterImportController
import com.gcatcode.petmephone.feature.overlay.onboarding.ONBOARDING_PRIMARY_ACTION_TEST_TAG
import com.gcatcode.petmephone.feature.overlay.onboarding.OverlayOnboardingViewModel
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The shell: every menu entry opens its destination, and the menu presents itself by width.
 *
 * The screens themselves were tested before this shell existed. What was never covered is the thing
 * that was actually missing — that a person can get to them at all.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class PetMeAppNavigationTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val settingsLauncher = mockk<OverlaySettingsLauncher>(relaxed = true)
    private val importer = mockk<CharacterImporter>(relaxed = true)

    private val characterRepository = mockk<CharacterRepository> {
        every { importedCharacters } returns flowOf(emptyList())
    }

    private val activeCharacterRepository = mockk<ActiveCharacterRepository> {
        every { active } returns flowOf(CharacterId.BuiltIn("default"))
    }

    private val libraryConfig = CharacterLibraryConfig(
        maxImportedCharacters = 3,
        maxImportBytes = 1_000_000L,
        builtInFallbackName = "default",
    )

    private val importController = mockk<CharacterImportController>(relaxed = true) {
        every { state } returns MutableStateFlow(CharacterImportController.State.Idle)
    }

    private fun onboardingViewModel(granted: Boolean): OverlayOnboardingViewModel =
        OverlayOnboardingViewModel(
            permissionChecker = object : OverlayPermissionChecker {
                override fun canDrawOverlays(): Boolean = granted
            },
            onboardingRepository = object : OverlayOnboardingRepository {
                override val history = flowOf(OverlayOnboardingHistory.NEVER_SHOWN)
                override suspend fun markOnboardingSeen() = Unit
                override suspend fun recordRefusal(atEpochMillis: Long) = Unit
            },
        )

    /**
     * [threshold] is what decides whether the menu stays open. The default puts the test viewport
     * on the compact side; passing `0.dp` forces the permanent-sidebar branch without needing a
     * different device.
     */
    private fun setContent(
        granted: Boolean = true,
        threshold: androidx.compose.ui.unit.Dp = 600.dp,
        onPermissionGranted: () -> Unit = {},
    ) {
        composeRule.setContent {
            PetMeApp(
                onboardingViewModel = onboardingViewModel(granted),
                settingsLauncher = settingsLauncher,
                characterRepository = characterRepository,
                activeCharacterRepository = activeCharacterRepository,
                libraryConfig = libraryConfig,
                importController = importController,
                importer = importer,
                onPickImage = {},
                onPermissionGranted = onPermissionGranted,
                expandedWidthThreshold = threshold,
            )
        }
    }

    private fun openMenu() {
        composeRule.onNodeWithTag(MENU_BUTTON_TEST_TAG).performClick()
    }

    @Test
    fun `the app opens on the pet, with the menu closed`() {
        setContent()

        composeRule.onNodeWithTag(PET_SCREEN_TEST_TAG).assertIsDisplayed()
        composeRule.onNodeWithTag(MENU_BUTTON_TEST_TAG).assertIsDisplayed()
    }

    @Test
    fun `every menu entry opens its own destination`() {
        setContent()

        openMenu()
        composeRule.onNodeWithTag(menuItemTestTag("Characters")).performClick()
        composeRule.onNodeWithText("Character library").assertIsDisplayed()

        openMenu()
        composeRule.onNodeWithTag(menuItemTestTag("Import")).performClick()
        composeRule.onNodeWithText("Choose photo").assertIsDisplayed()

        openMenu()
        composeRule.onNodeWithTag(menuItemTestTag("Permission")).performClick()
        composeRule.onNodeWithTag(ONBOARDING_PRIMARY_ACTION_TEST_TAG).assertIsDisplayed()

        openMenu()
        composeRule.onNodeWithTag(menuItemTestTag("Pet")).performClick()
        composeRule.onNodeWithTag(PET_SCREEN_TEST_TAG).assertIsDisplayed()
    }

    @Test
    fun `the menu offers only destinations that exist`() {
        setContent()
        openMenu()

        // Slice 7's rows are absent, not disabled: a menu row that opens nothing is a promise the
        // app cannot keep. If one of these ever appears, it must have a screen behind it.
        composeRule.onNodeWithText("Tasks").assertDoesNotExist()
        composeRule.onNodeWithText("Statistics").assertDoesNotExist()
        composeRule.onNodeWithText("Journal").assertDoesNotExist()
    }

    @Test
    fun `on a wide layout the menu stays open, with no button needed to reach it`() {
        setContent(threshold = 0.dp)

        composeRule.onNodeWithTag(MENU_SHEET_TEST_TAG).assertIsDisplayed()
        composeRule.onNodeWithTag(MENU_BUTTON_TEST_TAG).assertDoesNotExist()
    }

    @Test
    fun `without the permission the app is still usable, and says why the pet is missing`() {
        setContent(granted = false)

        // Not a gate. The pet is absent and the screen says so, while the menu and every other
        // destination keep working.
        composeRule.onNodeWithTag(PET_STATUS_TEST_TAG).assertIsDisplayed()
        openMenu()
        composeRule.onNodeWithTag(menuItemTestTag("Characters")).performClick()
        composeRule.onNodeWithText("Character library").assertIsDisplayed()
    }

    @Test
    fun `a granted permission switches the pet on without a manual step`() {
        var started = false
        setContent(granted = true, onPermissionGranted = { started = true })

        composeRule.waitForIdle()

        assertTrue("granting must start the overlay service on its own", started)
    }
}
