package com.gcatcode.petmephone.feature.overlay.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.createComposeRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Every overlay surface must render in the app's own palette, not Material's baseline.
 *
 * This was a real defect found on device: `PetMePhoneTheme` is applied by `MainActivity`, and a
 * `WindowManager`-hosted view never passes through it, so the quick-menu card came up in Material's
 * default purple. The pet had hidden the gap for two slices because a sprite shows no theme colours.
 *
 * The assertion is on the light scheme's `primary` specifically because that value differs between
 * the app palette and Material's default — asserting on something both schemes share would pass
 * with the theme wrapper deleted.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ComposeOverlayHostThemeTest {

    @get:Rule
    val composeRule = createComposeRule()

    /** `PetMePhoneTheme`'s light `primary`, from the design system's `LightGreen` scheme. */
    private val appLightPrimary = Color(0xFF2E6B4F)

    /** Material 3's baseline light `primary`, i.e. what an unthemed composition falls back to. */
    private val materialDefaultLightPrimary = Color(0xFF6750A4)

    @Test
    fun `content hosted in the overlay renders in the app palette, not Material's default`() {
        var observed: Color? = null

        composeRule.setContent {
            val host = ComposeOverlayHost(
                context = androidx.compose.ui.platform.LocalContext.current,
                content = { observed = MaterialTheme.colorScheme.primary },
            )
            HostContent(host)
        }
        composeRule.waitForIdle()

        // Fails if the PetMePhoneTheme wrapper is removed from ComposeOverlayHost.Content(): the
        // composition would then fall back to Material's baseline scheme and observe 0xFF6750A4.
        assertEquals("overlay content is not using the app theme", appLightPrimary, observed)
        assertNotEquals(
            "overlay content fell back to Material's default palette",
            materialDefaultLightPrimary,
            observed,
        )
    }

    @Composable
    private fun HostContent(host: ComposeOverlayHost) {
        // Invokes the host's own Content(), which is where the theme wrapper lives. Composing the
        // `content` lambda directly would bypass the very thing under test.
        host.Content()
    }
}
