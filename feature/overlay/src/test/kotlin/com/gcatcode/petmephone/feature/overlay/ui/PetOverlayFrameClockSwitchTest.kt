package com.gcatcode.petmephone.feature.overlay.ui

import android.os.Looper
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createComposeRule
import com.gcatcode.petmephone.core.domain.character.ActiveCharacterRepository
import com.gcatcode.petmephone.core.domain.character.CharacterId
import com.gcatcode.petmephone.core.domain.overlay.DragStateRepository
import com.gcatcode.petmephone.core.domain.overlay.OverlayPositionFraction
import com.gcatcode.petmephone.core.domain.overlay.OverlayPositionRepository
import com.gcatcode.petmephone.core.domain.pet.state.DraggingStateProvider
import com.gcatcode.petmephone.core.domain.pet.state.IdleStateProvider
import com.gcatcode.petmephone.core.domain.pet.state.PetState
import com.gcatcode.petmephone.core.domain.pet.state.PetStateConfig
import com.gcatcode.petmephone.core.domain.pet.state.PetStateResolver
import com.gcatcode.petmephone.feature.overlay.character.CharacterSheetLoader
import com.gcatcode.petmephone.feature.overlay.character.CharacterSheets
import com.gcatcode.petmephone.feature.overlay.sprite.BitmapDecoding
import com.gcatcode.petmephone.feature.overlay.sprite.SpriteFixtures
import com.gcatcode.petmephone.feature.overlay.sprite.SpriteSheetDecoder
import com.gcatcode.petmephone.feature.overlay.system.ScreenStateMonitor
import java.io.File
import java.util.concurrent.CopyOnWriteArrayList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows
import org.robolectric.annotation.Config

/**
 * `[RENDER-3]` regression for the PR 6 verification finding C2: `SpriteLayout` is a data class
 * with value equality, so switching to a character whose sheet happens to share the same
 * grid/frameCount as the previous one left `LaunchedEffect(layout, holder.config)`'s keys
 * unchanged. The effect never relaunched, and the coroutine it started kept mutating the
 * *previous* character's now-abandoned `MutableIntState` from `remember(ready)`, while the draw
 * lambda read the new one — pinned at frame 0 forever.
 *
 * This project's JVM/Robolectric Compose tests cannot capture real pixels (confirmed while
 * building this test: `captureToImage` hangs indefinitely against a live, perpetually-rescheduling
 * animation coroutine, and a manual `View.draw` onto a `Bitmap` never executes Compose's own draw
 * path under Robolectric). [ReadyPet]'s test-only `onFrameAdvance` hook (default no-op, so every
 * production call site is unaffected) is the only available way to observe the clock without a
 * physical device — see its kdoc for the full rationale. It is read here, never trusted as pixel
 * proof, but it exercises the exact composable and the exact `remember`/`LaunchedEffect` keys the
 * bug lives in, not a re-implementation of the loop.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class PetOverlayFrameClockSwitchTest {

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

    private class FakeDragStateRepository : DragStateRepository {
        private val flow = MutableStateFlow(false)
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

    /** Writes a same-shaped (6 columns, 1 row) `idle.png` character, so every fixture used here
     * produces value-equal [com.gcatcode.petmephone.core.domain.pet.sprite.SpriteLayout]s. */
    private fun writeCharacter(uuid: String) {
        val dir = File(File(context.filesDir, "characters"), uuid)
        dir.mkdirs()
        File(dir, "manifest.properties").writeBytes("columns=6\nrows=1\n".toByteArray())
        File(dir, "idle.png").writeBytes(SpriteFixtures.validSheetBytes(cellSizePx = 8, columns = 6))
    }

    private fun waitForCondition(timeoutMillis: Long = 5_000, predicate: () -> Boolean) {
        val deadline = System.currentTimeMillis() + timeoutMillis
        while (!predicate()) {
            Shadows.shadowOf(Looper.getMainLooper()).idle()
            composeRule.mainClock.advanceTimeBy(50)
            if (System.currentTimeMillis() > deadline) {
                throw AssertionError("Timed out waiting for condition")
            }
            Thread.sleep(5)
        }
    }

    @Test
    fun `switching to a value-equal layout keeps the frame clock advancing on the new character`() {
        writeCharacter("character-a")
        writeCharacter("character-b")

        val activeCharacterRepository = FakeActiveCharacterRepository(CharacterId.Imported("character-a"))
        val resolver = PetStateResolver(
            providers = setOf(DraggingStateProvider(), IdleStateProvider()),
            config = PetStateConfig(minimumDwellMillis = 0),
        )
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        val holder = PetOverlayStateHolder(
            activeCharacterRepository = activeCharacterRepository,
            sheetLoader = CharacterSheetLoader(context, decoder),
            dragStateRepository = FakeDragStateRepository(),
            stateResolver = resolver,
            screenStateMonitor = ScreenStateMonitor(context, scope),
            positionRepository = FakeOverlayPositionRepository(),
            config = PetAnimationConfig(frameIntervalMillis = 5, minFrameIntervalMillis = 1, stateSharingTimeoutMillis = 0),
            scope = scope,
        )

        val advances = CopyOnWriteArrayList<Pair<CharacterSheets.Ready, Int>>()

        composeRule.setContent {
            val sheets by holder.sheets.collectAsState()
            val petState by holder.petState.collectAsState()
            var lastReady by remember { mutableStateOf<CharacterSheets.Ready?>(null) }
            val current = sheets
            if (current is CharacterSheets.Ready) lastReady = current
            val readyToShow = (current as? CharacterSheets.Ready) ?: lastReady
            readyToShow?.let {
                ReadyPet(it, petState, holder, onFrameAdvance = { ready, index -> advances.add(ready to index) })
            }
        }

        waitForCondition { holder.sheets.value is CharacterSheets.Ready }
        val readyA = holder.sheets.value as CharacterSheets.Ready
        waitForCondition { advances.any { (ready, _) -> ready === readyA } }

        composeRule.runOnIdle {
            kotlinx.coroutines.runBlocking { activeCharacterRepository.setActive(CharacterId.Imported("character-b")) }
        }
        waitForCondition {
            val current = holder.sheets.value
            current is CharacterSheets.Ready && current !== readyA
        }
        val readyB = holder.sheets.value as CharacterSheets.Ready
        assertTrue(
            "fixture must build value-equal layouts for this scenario to be meaningful",
            readyA.idle.layout == readyB.idle.layout && readyA != readyB,
        )

        // A frozen clock keeps advancing the OLD, now-abandoned `ready` (character-a) forever and
        // never advances the new one at all.
        waitForCondition(timeoutMillis = 2_000) { advances.any { (ready, index) -> ready === readyB && index >= 1 } }
        assertTrue(
            "the frame clock never advanced on the newly-active character — it froze on frame 0",
            advances.any { (ready, index) -> ready === readyB && index >= 1 },
        )

        scope.cancel()
    }
}
