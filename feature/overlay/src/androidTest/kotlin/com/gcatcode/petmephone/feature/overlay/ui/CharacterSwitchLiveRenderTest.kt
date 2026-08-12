package com.gcatcode.petmephone.feature.overlay.ui

import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.test.platform.app.InstrumentationRegistry
import com.gcatcode.petmephone.core.domain.character.ActiveCharacterRepository
import com.gcatcode.petmephone.core.domain.character.CharacterId
import com.gcatcode.petmephone.core.domain.overlay.DragStateRepository
import com.gcatcode.petmephone.core.domain.overlay.OverlayPositionFraction
import com.gcatcode.petmephone.core.domain.overlay.OverlayPositionRepository
import com.gcatcode.petmephone.core.domain.pet.state.DraggingStateProvider
import com.gcatcode.petmephone.core.domain.pet.state.IdleStateProvider
import com.gcatcode.petmephone.core.domain.pet.state.PetStateConfig
import com.gcatcode.petmephone.core.domain.pet.state.PetStateResolver
import com.gcatcode.petmephone.feature.overlay.character.CharacterSheetLoader
import com.gcatcode.petmephone.feature.overlay.character.CharacterSheets
import com.gcatcode.petmephone.feature.overlay.sprite.BitmapDecoding
import com.gcatcode.petmephone.feature.overlay.sprite.SpriteSheetDecoder
import com.gcatcode.petmephone.feature.overlay.system.ScreenStateMonitor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * `[RENDER-3]` `[IMPORT-13]` — proves the running composable re-renders after
 * `ActiveCharacterRepository.setActive` alone, with no relaunch: constructs
 * [PetOverlayStateHolder] directly (no Hilt graph, no service) and switches between the two real
 * bundled fixture characters (`default`, `default2` — see `design.md`'s "Bundled test characters"
 * section), asserting the captured frame differs before and after the switch.
 *
 * Attempted once against an API 34 image per `design.md`'s per-affected-PR measurement
 * requirement; the real-hardware manual pass remains the primary evidence for the live overlay,
 * per the proposal's risk table.
 */
private object SwitchTestNoPositionRepository : OverlayPositionRepository {
    override val position: Flow<OverlayPositionFraction?> = emptyFlow()
    override val normalizations: Flow<Unit> = emptyFlow()
    override suspend fun save(position: OverlayPositionFraction) = Unit
}

private class FixedDragStateRepository : DragStateRepository {
    private val flow = MutableStateFlow(false)
    override val isDragging get() = flow
    override fun set(dragging: Boolean) {
        flow.value = dragging
    }
}

private class SwitchableActiveCharacterRepository(initial: CharacterId) : ActiveCharacterRepository {
    val flow = MutableStateFlow(initial)
    override val active: Flow<CharacterId> get() = flow
    override suspend fun setActive(id: CharacterId) {
        flow.value = id
    }
}

class CharacterSwitchLiveRenderTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun switchingActiveCharacterRerendersWithoutRelaunch() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val decoder = SpriteSheetDecoder(BitmapDecoding.Default(), maxDimensionPx = 2048)
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val activeCharacterRepository = SwitchableActiveCharacterRepository(CharacterId.BuiltIn("default"))
        val resolver = PetStateResolver(
            providers = setOf(DraggingStateProvider(), IdleStateProvider()),
            config = PetStateConfig(minimumDwellMillis = 0),
        )
        val holder = PetOverlayStateHolder(
            activeCharacterRepository = activeCharacterRepository,
            sheetLoader = CharacterSheetLoader(context, decoder),
            dragStateRepository = FixedDragStateRepository(),
            stateResolver = resolver,
            screenStateMonitor = ScreenStateMonitor(context, scope),
            positionRepository = SwitchTestNoPositionRepository,
            config = PetAnimationConfig(frameIntervalMillis = 150L, minFrameIntervalMillis = 1, stateSharingTimeoutMillis = 0),
            scope = scope,
        )

        composeTestRule.setContent { PetOverlay(holder) }
        waitForReady(holder)
        val beforeSwitch = composeTestRule.onRoot().captureToImage().asAndroidBitmap()

        // The identical `setActive` call an `Imported` id would also use — no id-type branching.
        composeTestRule.runOnIdle { activeCharacterRepository.flow.value = CharacterId.BuiltIn("default2") }
        waitForReadyChange(holder)
        val afterSwitch = composeTestRule.onRoot().captureToImage().asAndroidBitmap()

        assertTrue(
            "expected the composed frame to change after setActive alone, with no relaunch",
            !bitmapsPixelEqual(beforeSwitch, afterSwitch),
        )
    }

    private fun waitForReady(holder: PetOverlayStateHolder, timeoutMillis: Long = 5_000) {
        val deadline = System.currentTimeMillis() + timeoutMillis
        while (holder.sheets.value !is CharacterSheets.Ready) {
            if (System.currentTimeMillis() > deadline) throw AssertionError("Timed out waiting for Ready")
            Thread.sleep(20)
        }
    }

    private fun waitForReadyChange(holder: PetOverlayStateHolder, timeoutMillis: Long = 5_000) {
        val previous = holder.sheets.value
        val deadline = System.currentTimeMillis() + timeoutMillis
        while (holder.sheets.value === previous || holder.sheets.value !is CharacterSheets.Ready) {
            if (System.currentTimeMillis() > deadline) throw AssertionError("Timed out waiting for the switch to resolve")
            Thread.sleep(20)
        }
    }

    private fun bitmapsPixelEqual(a: android.graphics.Bitmap, b: android.graphics.Bitmap): Boolean {
        if (a.width != b.width || a.height != b.height) return false
        for (x in 0 until a.width step 4) {
            for (y in 0 until a.height step 4) {
                if (a.getPixel(x, y) != b.getPixel(x, y)) return false
            }
        }
        return true
    }
}
