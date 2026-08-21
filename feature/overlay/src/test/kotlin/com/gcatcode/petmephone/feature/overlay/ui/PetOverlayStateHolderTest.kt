package com.gcatcode.petmephone.feature.overlay.ui

import android.graphics.Bitmap
import com.gcatcode.petmephone.core.domain.balance.BalanceConfig
import com.gcatcode.petmephone.core.domain.character.ActiveCharacterRepository
import com.gcatcode.petmephone.core.domain.character.CharacterId
import com.gcatcode.petmephone.core.domain.metric.MetricReading
import com.gcatcode.petmephone.core.domain.overlay.DragStateRepository
import com.gcatcode.petmephone.core.domain.overlay.OverlayPositionFraction
import com.gcatcode.petmephone.core.domain.overlay.OverlayPositionRepository
import com.gcatcode.petmephone.core.domain.pet.state.DraggingStateProvider
import com.gcatcode.petmephone.core.domain.pet.state.IdleStateProvider
import com.gcatcode.petmephone.core.domain.pet.state.PetStateConfig
import com.gcatcode.petmephone.core.domain.pet.state.PetStateResolver
import com.gcatcode.petmephone.core.domain.task.TaskOccurrence
import com.gcatcode.petmephone.core.domain.task.TaskRepository
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
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * Drives the real reactive wiring `design.md` specifies for `PetOverlayStateHolder`: `active`
 * `mapLatest`-ing into a decode, shared via `stateIn(WhileSubscribed)`. Constructed directly
 * (never through Hilt, and never through a Compose test rule — this exercises the holder's own
 * `StateFlow` wiring, not the composable that consumes it, see `PetOverlayTest`), against a real
 * [CharacterSheetLoader] over real `filesDir` character folders, so the decode timing is genuine
 * rather than faked away — the one place this batch needs a controllable delay uses the same
 * `Thread.sleep`-inside-`BitmapDecoding` seam `PreviewScreenTest`'s `SlowBitmapDecoding` already
 * established.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class PetOverlayStateHolderTest {

    private val context = RuntimeEnvironment.getApplication()

    private class FakeActiveCharacterRepository(initial: CharacterId) : ActiveCharacterRepository {
        val flow = MutableStateFlow(initial)
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

    /** Wraps the real decoder but blocks briefly inside `decodeFull` for one specific byte
     * array's identity, so only the character under test is slow — matching
     * `PreviewScreenTest.SlowBitmapDecoding`'s established seam. */
    private class SlowForBitmapDecoding(private val slowBytes: ByteArray, private val delayMillis: Long) : BitmapDecoding {
        private val delegate = BitmapDecoding.Default()
        override fun decodeBounds(bytes: ByteArray) = delegate.decodeBounds(bytes)
        // Content equality, not reference: the bytes reaching this point are freshly read back
        // from a real `filesDir` file, never the same array instance the fixture originally wrote.
        override fun decodeFull(bytes: ByteArray): Bitmap? {
            if (bytes.contentEquals(slowBytes)) Thread.sleep(delayMillis)
            return delegate.decodeFull(bytes)
        }
    }

    private fun writeCharacterFolder(uuid: String, idleBytes: ByteArray, columns: Int, rows: Int = 1) {
        val dir = File(File(context.filesDir, "characters"), uuid)
        dir.mkdirs()
        File(dir, "manifest.properties").writeBytes("columns=$columns\nrows=$rows\n".toByteArray())
        File(dir, "idle.png").writeBytes(idleBytes)
    }

    private fun buildHolder(
        activeCharacterRepository: ActiveCharacterRepository,
        decoder: SpriteSheetDecoder,
        scope: CoroutineScope,
    ): PetOverlayStateHolder {
        val resolver = PetStateResolver(
            providers = setOf(DraggingStateProvider(), IdleStateProvider()),
            config = PetStateConfig(minimumDwellMillis = 0),
        )
        return PetOverlayStateHolder(
            activeCharacterRepository = activeCharacterRepository,
            sheetLoader = CharacterSheetLoader(context, decoder),
            dragStateRepository = FakeDragStateRepository(),
            stateResolver = resolver,
            screenStateMonitor = ScreenStateMonitor(context, scope),
            positionRepository = FakeOverlayPositionRepository(),
            clock = noOpAppClock(),
            taskRepository = noOpTaskRepository(),
            balanceConfigSource = noOpBalanceConfigSource(),
            animationConfigSource = PetAnimationConfigSource(
                fixedPetAnimationConfigStore(
                    PetAnimationConfig(frameIntervalMillis = 20, minFrameIntervalMillis = 1, stateSharingTimeoutMillis = 0),
                ),
            ),
            scope = scope,
        )
    }

    private fun waitForCondition(timeoutMillis: Long = 5_000, predicate: () -> Boolean) {
        val deadline = System.currentTimeMillis() + timeoutMillis
        while (!predicate()) {
            if (System.currentTimeMillis() > deadline) {
                throw AssertionError("Timed out waiting for condition")
            }
            Thread.sleep(10)
        }
    }

    @Test
    fun `switching characters keeps the previous Ready value until the new one decodes`() {
        val firstBytes = SpriteFixtures.validSheetBytes(cellSizePx = 8, columns = 6)
        val secondBytes = SpriteFixtures.validSheetBytes(cellSizePx = 8, columns = 3)
        writeCharacterFolder("first-uuid", firstBytes, columns = 6)
        writeCharacterFolder("second-uuid", secondBytes, columns = 3)

        val activeRepository = FakeActiveCharacterRepository(CharacterId.Imported("first-uuid"))
        val decoder = SpriteSheetDecoder(SlowForBitmapDecoding(secondBytes, delayMillis = 300), maxDimensionPx = 64)
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val holder = buildHolder(activeRepository, decoder, scope)

        // Subscribing to the StateFlow starts the `WhileSubscribed` sharing (timeout 0 in this
        // test's config, so it starts immediately on the first collector).
        val collectorJob = scope.launch { holder.sheets.collect {} }

        waitForCondition { holder.sheets.value is CharacterSheets.Ready }
        val firstReady = holder.sheets.value as CharacterSheets.Ready
        assertEquals(6, firstReady.idle.layout.grid.columns)

        // Switch to the slow second character. `mapLatest` only emits the *result* of a
        // completed transform, never an intermediate value, so `sheets` naturally keeps
        // reporting the first character's `Ready` value for the whole 300ms decode — the
        // mechanism `design.md`'s "keeps the previous frame visible during a switch" behavior
        // actually rests on: the `StateFlow` simply has nothing newer to report yet.
        activeRepository.flow.value = CharacterId.Imported("second-uuid")
        Thread.sleep(150) // well inside the 300ms decode window
        assertTrue(holder.sheets.value === firstReady)

        // Once the slow decode finishes, the new character's own Ready value takes over.
        waitForCondition(timeoutMillis = 5_000) { holder.sheets.value !== firstReady }
        val secondReady = holder.sheets.value as CharacterSheets.Ready
        assertEquals(3, secondReady.idle.layout.grid.columns)

        collectorJob.cancel()
        scope.cancel()
    }

    @Test
    fun `a fast second switch supersedes a still-decoding first one, per mapLatest`() {
        val staleBytes = SpriteFixtures.validSheetBytes(cellSizePx = 8, columns = 6)
        val freshBytes = SpriteFixtures.validSheetBytes(cellSizePx = 8, columns = 3)
        writeCharacterFolder("stale-uuid", staleBytes, columns = 6)
        writeCharacterFolder("fresh-uuid", freshBytes, columns = 3)

        val activeRepository = FakeActiveCharacterRepository(CharacterId.Imported("never-decoded-uuid"))
        // The stale character is slow so its decode is still in flight when the fresh switch
        // supersedes it.
        val decoder = SpriteSheetDecoder(SlowForBitmapDecoding(staleBytes, delayMillis = 400), maxDimensionPx = 64)
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val holder = buildHolder(activeRepository, decoder, scope)

        val collectorJob = scope.launch { holder.sheets.collect {} }

        waitForCondition { holder.sheets.value is CharacterSheets.Broken } // no folder for "never-decoded-uuid"

        activeRepository.flow.value = CharacterId.Imported("stale-uuid")
        activeRepository.flow.value = CharacterId.Imported("fresh-uuid")

        // The fresh character's fast decode finishes well before the stale one's 400ms delay
        // would. If `mapLatest` did not cancel the stale decode, the holder could still flip to
        // the stale character's Ready value afterward — asserted absent below.
        waitForCondition { holder.sheets.value is CharacterSheets.Ready }
        val readyAfterFresh = holder.sheets.value as CharacterSheets.Ready
        assertEquals(3, readyAfterFresh.idle.layout.grid.columns)

        Thread.sleep(500) // long enough for the stale decode to have completed, were it not cancelled
        val stillReady = holder.sheets.value
        assertTrue(stillReady is CharacterSheets.Ready)
        assertEquals(3, (stillReady as CharacterSheets.Ready).idle.layout.grid.columns)

        collectorJob.cancel()
        scope.cancel()
    }

    /** Fixed non-zero counts, so a `dailyTaskGoal` change actually moves the computed percentage. */
    private class FixedCountTaskRepository : TaskRepository {
        override suspend fun createOneOff(
            title: com.gcatcode.petmephone.core.domain.task.TaskTitle,
            createdAt: java.time.Instant,
            createdDate: java.time.LocalDate,
            points: Int,
        ) = throw UnsupportedOperationException("not used")

        override suspend fun countManuallyCreatedOn(date: java.time.LocalDate) = 5
        override suspend fun countRecurringScheduledOn(date: java.time.LocalDate) = 0
        override fun occurrencesDueOn(date: java.time.LocalDate): Flow<List<TaskOccurrence>> = emptyFlow()
        override fun observeManuallyCreatedOn(date: java.time.LocalDate): Flow<Int> = flowOf(5)
        override fun observeRecurringScheduledOn(date: java.time.LocalDate): Flow<Int> = flowOf(0)
    }

    @Test
    fun `a BalanceConfig change reaches the observed Hunger value live, without reconstructing the holder`() {
        val activeRepository = FakeActiveCharacterRepository(CharacterId.Imported("never-decoded-uuid"))
        val decoder = SpriteSheetDecoder(BitmapDecoding.Default(), maxDimensionPx = 64)
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val resolver = PetStateResolver(
            providers = setOf(DraggingStateProvider(), IdleStateProvider()),
            config = PetStateConfig(minimumDwellMillis = 0),
        )
        val balanceConfigSource = MutableFakeBalanceConfigSource()
        val holder = PetOverlayStateHolder(
            activeCharacterRepository = activeRepository,
            sheetLoader = CharacterSheetLoader(context, decoder),
            dragStateRepository = FakeDragStateRepository(),
            stateResolver = resolver,
            screenStateMonitor = ScreenStateMonitor(context, scope),
            positionRepository = FakeOverlayPositionRepository(),
            clock = noOpAppClock(),
            taskRepository = FixedCountTaskRepository(),
            balanceConfigSource = balanceConfigSource,
            animationConfigSource = PetAnimationConfigSource(
                fixedPetAnimationConfigStore(
                    PetAnimationConfig(frameIntervalMillis = 20, minFrameIntervalMillis = 1, stateSharingTimeoutMillis = 0),
                ),
            ),
            scope = scope,
        )

        val collectorJob = scope.launch { holder.hunger.collect {} }
        waitForCondition { holder.hunger.value is MetricReading.Available }
        val firstPercent = (holder.hunger.value as MetricReading.Available).percent

        balanceConfigSource.mutableConfig.value = BalanceConfig(dailyTaskGoal = 50)

        waitForCondition { (holder.hunger.value as? MetricReading.Available)?.percent != firstPercent }
        val secondPercent = (holder.hunger.value as MetricReading.Available).percent
        assertNotEquals(firstPercent, secondPercent)

        collectorJob.cancel()
        scope.cancel()
    }

    @Test
    fun `a PetAnimationConfig change is reflected without a restart`() {
        val activeRepository = FakeActiveCharacterRepository(CharacterId.Imported("never-decoded-uuid"))
        val decoder = SpriteSheetDecoder(BitmapDecoding.Default(), maxDimensionPx = 64)
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val resolver = PetStateResolver(
            providers = setOf(DraggingStateProvider(), IdleStateProvider()),
            config = PetStateConfig(minimumDwellMillis = 0),
        )
        val animationStore = MutableFakeConfigOverrideStore()
        val holder = PetOverlayStateHolder(
            activeCharacterRepository = activeRepository,
            sheetLoader = CharacterSheetLoader(context, decoder),
            dragStateRepository = FakeDragStateRepository(),
            stateResolver = resolver,
            screenStateMonitor = ScreenStateMonitor(context, scope),
            positionRepository = FakeOverlayPositionRepository(),
            clock = noOpAppClock(),
            taskRepository = noOpTaskRepository(),
            balanceConfigSource = noOpBalanceConfigSource(),
            animationConfigSource = PetAnimationConfigSource(animationStore),
            scope = scope,
        )
        val collectorJob = scope.launch { holder.config.collect {} }

        runBlocking { animationStore.set(PetAnimationConfig.FRAME_INTERVAL_MILLIS, 777L) }

        waitForCondition { holder.config.value.frameIntervalMillis == 777L }
        assertEquals(777L, holder.config.value.frameIntervalMillis)

        collectorJob.cancel()
        scope.cancel()
    }
}
