package com.gcatcode.petmephone.core.data.config

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import com.gcatcode.petmephone.core.domain.habit.DaySegmentBoundaries
import java.time.LocalTime
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The user's segmentation resolves like any other config, and a collectively invalid stored triple
 * falls back to the shipped segmentation as a whole rather than a mix. Robolectric for the same
 * reason [BalanceConfigSourceImplTest] needs it.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class DaySegmentBoundariesSourceImplTest {

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private lateinit var dataStore: DataStore<Preferences>
    private lateinit var store: PreferencesConfigOverrideStore
    private lateinit var source: DaySegmentBoundariesSourceImpl

    private fun setUp() {
        val file = temporaryFolder.root.resolve("day_segment_boundaries_test.preferences_pb")
        dataStore = PreferenceDataStoreFactory.create(produceFile = { file })
        store = PreferencesConfigOverrideStore(dataStore)
        source = DaySegmentBoundariesSourceImpl(store)
    }

    @Test
    fun `an empty store yields the shipped segmentation`() = runTest {
        setUp()

        val resolved = source.boundaries.first()

        assertEquals(DaySegmentBoundaries.SHIPPED, resolved)
    }

    @Test
    fun `a user who moves their day to 10am is observed without a restart`() = runTest {
        setUp()

        store.set(DaySegmentBoundaries.DAY_START_MINUTES, 10 * 60)

        val resolved = source.boundaries.first { it.dayStart == LocalTime.of(10, 0) }
        assertEquals(LocalTime.of(10, 0), resolved.dayStart)
        assertEquals(LocalTime.of(12, 0), resolved.afternoonStart)
    }

    @Test
    fun `a collectively non-ascending store falls back to the whole shipped segmentation`() = runTest {
        setUp()

        // Each write is individually in range, so no per-field validation refuses it — the day now
        // starts after the afternoon does. A partial mix here would be a segmentation the user
        // never chose and never shipped, which is harder to recognise as wrong than the defaults.
        store.set(DaySegmentBoundaries.DAY_START_MINUTES, 13 * 60)

        val resolved = source.boundaries.first { it == DaySegmentBoundaries.SHIPPED }
        assertEquals(DaySegmentBoundaries.SHIPPED, resolved)
    }
}
