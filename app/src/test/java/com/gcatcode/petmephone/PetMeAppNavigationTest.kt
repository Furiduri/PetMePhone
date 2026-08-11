package com.gcatcode.petmephone

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
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
import com.gcatcode.petmephone.feature.overlay.onboarding.REENTRY_CARD_TEST_TAG
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
 * Every destination is reachable, and declining is not a dead end.
 *
 * The screens themselves were already tested before this host existed — what was never covered is
 * the thing that was actually missing: that a person can get to them at all.
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

    private fun setContent(permissionGranted: Boolean, onPermissionGranted: () -> Unit = {}) {
        composeRule.setContent {
            PetMeApp(
                onboardingViewModel = onboardingViewModel(permissionGranted),
                settingsLauncher = settingsLauncher,
                characterRepository = characterRepository,
                activeCharacterRepository = activeCharacterRepository,
                libraryConfig = libraryConfig,
                importController = importController,
                importer = importer,
                onPickImage = {},
                permissionGranted = permissionGranted,
                onPermissionGranted = onPermissionGranted,
            )
        }
    }

    @Test
    fun `without the permission the app opens on onboarding, not on a blank screen`() {
        setContent(permissionGranted = false)

        composeRule.onNodeWithTag(ONBOARDING_PRIMARY_ACTION_TEST_TAG).assertIsDisplayed()
    }

    @Test
    fun `declining leaves the app usable, landing in the library rather than trapping the user`() {
        setContent(permissionGranted = false)

        composeRule.onNodeWithTag(ONBOARDING_DECLINE_TEST_TAG).performClick()

        // Onboarding is behind us, and the re-entry card is on screen. The card renders only in the
        // library destination, so its presence is the positive proof of where we landed.
        //
        // Deliberately not asserting on the library's own title: it sits inside a LazyColumn, and
        // with the card stacked above it in a test viewport that item is never composed at all.
        // That is a layout fact about lazy lists, not evidence about navigation.
        composeRule.onNodeWithTag(ONBOARDING_PRIMARY_ACTION_TEST_TAG).assertDoesNotExist()
        composeRule.onNodeWithTag(REENTRY_CARD_TEST_TAG).assertIsDisplayed()
    }

    @Test
    fun `with the permission already granted the app opens on the library`() {
        setContent(permissionGranted = true)

        composeRule.onNodeWithText("Character library").assertIsDisplayed()
    }

    @Test
    fun `the library reaches the import screen`() {
        setContent(permissionGranted = true)

        composeRule.onNodeWithText("Import a character").performClick()

        composeRule.onNodeWithText("Choose photo").assertIsDisplayed()
    }

    @Test
    fun `a granted permission starts the overlay service`() {
        var started = false
        setContent(permissionGranted = true, onPermissionGranted = { started = true })

        composeRule.waitForIdle()

        assertTrue("granting must switch the pet on without a manual step", started)
    }
}
