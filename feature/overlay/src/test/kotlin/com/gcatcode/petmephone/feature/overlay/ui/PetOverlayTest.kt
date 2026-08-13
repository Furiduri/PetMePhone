package com.gcatcode.petmephone.feature.overlay.ui

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
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
import com.gcatcode.petmephone.feature.overlay.sprite.SpriteFixtures
import com.gcatcode.petmephone.feature.overlay.sprite.SpriteSheetDecoder
import com.gcatcode.petmephone.feature.overlay.system.ScreenStateMonitor
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * `[RENDER-1]` `[IMPORT-14]` — the renderer falls back to the active character's `idle.png` when
 * the resolved state's own file is absent, and draws the visibly-broken placeholder (never a
 * blank window, never a crash) when `idle.png` itself is missing.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class PetOverlayTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val context = RuntimeEnvironment.getApplication()
    private val decoder = SpriteSheetDecoder(BitmapDecoding.Default(), maxDimensionPx = 64)

    private class FakeActiveCharacterRepository(initial: CharacterId) : ActiveCharacterRepository {
        private val flow = MutableStateFlow(initial)
        override val active: Flow<CharacterId> get() = flow
        override suspend fun setActive(id: CharacterId) {
            flow.value = id
        }
    }

    private class FakeDragStateRepository(dragging: Boolean) : DragStateRepository {
        private val flow = MutableStateFlow(dragging)
        override val isDragging get() = flow
        override fun set(dragging: Boolean) {
            flow.value = dragging
        }
    }

    private class FakeOverlayPositionRepository : OverlayPositionRepository {
        override val position: Flow<OverlayPositionFraction?> = emptyFlow()
        override val normalizations: Flow<Unit> = emptyFlow()
        override suspend fun save(position: OverlayPositionFraction) = Unit
    }

    private fun buildHolder(id: CharacterId, isDragging: Boolean, scope: CoroutineScope): PetOverlayStateHolder {
        val resolver = PetStateResolver(
            providers = setOf(DraggingStateProvider(), IdleStateProvider()),
            config = PetStateConfig(minimumDwellMillis = 0),
        )
        return PetOverlayStateHolder(
            activeCharacterRepository = FakeActiveCharacterRepository(id),
            sheetLoader = CharacterSheetLoader(context, decoder),
            dragStateRepository = FakeDragStateRepository(isDragging),
            stateResolver = resolver,
            screenStateMonitor = ScreenStateMonitor(context, scope),
            positionRepository = FakeOverlayPositionRepository(),
            observeHunger = noOpObserveHunger(),
            config = PetAnimationConfig(frameIntervalMillis = 20, minFrameIntervalMillis = 1, stateSharingTimeoutMillis = 0),
            scope = scope,
        )
    }

    private fun waitForCondition(timeoutMillis: Long = 5_000, predicate: () -> Boolean) {
        val deadline = System.currentTimeMillis() + timeoutMillis
        while (!predicate()) {
            org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
            if (System.currentTimeMillis() > deadline) {
                throw AssertionError("Timed out waiting for condition")
            }
            Thread.sleep(10)
        }
    }

    @Test
    fun `a resolved state with no bound file falls back to idle, never a blank or broken render`() {
        // Only idle.png exists; DRAGGING has no file of its own, so the pet is set dragging (which
        // the real resolver resolves to PetState.DRAGGING) while the character has no dragging.png.
        val uuid = "idle-only-uuid"
        val dir = File(File(context.filesDir, "characters"), uuid)
        dir.mkdirs()
        File(dir, "manifest.properties").writeBytes("columns=6\nrows=1\n".toByteArray())
        File(dir, "idle.png").writeBytes(SpriteFixtures.validSheetBytes(cellSizePx = 8, columns = 6))

        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        val holder = buildHolder(CharacterId.Imported(uuid), isDragging = true, scope = scope)

        composeRule.setContent { PetOverlay(holder) }

        waitForCondition { holder.sheets.value is CharacterSheets.Ready }
        val ready = holder.sheets.value as CharacterSheets.Ready
        assert(!ready.byState.containsKey(com.gcatcode.petmephone.core.domain.pet.state.PetState.DRAGGING)) {
            "fixture must have no dragging.png for this scenario to be meaningful"
        }

        // The renderer must still show the pet's animation (never blank, never the broken
        // placeholder) by falling back to `idle.png`.
        composeRule.onNodeWithTag(READY_PET_TEST_TAG).assertExists()
        composeRule.onAllNodesWithTag(BROKEN_PLACEHOLDER_TEST_TAG).assertCountEquals(0)

        scope.cancel()
    }

    @Test
    fun `a missing idle png renders the broken placeholder, never nothing and never a crash`() {
        // No character folder exists at all for this id, so CharacterSheetLoader reports Broken.
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        val holder = buildHolder(CharacterId.Imported("no-such-uuid"), isDragging = false, scope = scope)

        composeRule.setContent { PetOverlay(holder) }

        waitForCondition { holder.sheets.value is CharacterSheets.Broken }

        composeRule.onNodeWithTag(BROKEN_PLACEHOLDER_TEST_TAG).assertExists()
        composeRule.onAllNodesWithTag(READY_PET_TEST_TAG).assertCountEquals(0)

        scope.cancel()
    }
}
