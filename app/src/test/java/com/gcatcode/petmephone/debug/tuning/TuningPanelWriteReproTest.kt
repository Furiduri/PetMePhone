package com.gcatcode.petmephone.debug.tuning

import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import com.gcatcode.petmephone.core.domain.balance.BalanceConfig
import com.gcatcode.petmephone.core.domain.config.BalanceConfigSource
import com.gcatcode.petmephone.core.domain.config.ConfigField
import com.gcatcode.petmephone.core.domain.config.ConfigOverrideStore
import com.gcatcode.petmephone.core.domain.config.ConfigWriteResult
import com.gcatcode.petmephone.core.domain.config.StoredOverride
import com.gcatcode.petmephone.feature.overlay.ui.PetAnimationConfig
import com.gcatcode.petmephone.feature.overlay.ui.PetAnimationConfigSource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Drives the real panel to find out what value actually reaches the store when a number is typed
 * into a row and Set is pressed.
 *
 * On a device, typing 600 into `frameIntervalMillis` left the persisted store holding 20. Reading
 * the parser, the view model and the row/field pairing found nothing wrong with any of them, which
 * leaves the Compose surface — the one layer this change shipped with no test at all.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class TuningPanelWriteReproTest {

    @get:Rule
    val composeRule = createComposeRule()

    /** Records every write verbatim, so the assertion sees exactly what the UI submitted. */
    private class RecordingStore : ConfigOverrideStore {
        val writes = mutableListOf<Pair<String, Any>>()
        /** Only the writes the store actually accepted, keyed by field. */
        val accepted = mutableMapOf<String, Any>()
        private val entries = MutableStateFlow<Map<String, Any>>(emptyMap())

        @Suppress("UNCHECKED_CAST")
        override fun <T : Comparable<T>> override(field: ConfigField<T>): Flow<StoredOverride<T>> =
            entries.map { current ->
                val stored = current[field.key]
                if (stored == null) {
                    StoredOverride.Absent
                } else {
                    StoredOverride.Present(stored as T, writtenUnderVersion = field.group.currentVersion)
                }
            }

        override suspend fun <T : Comparable<T>> set(field: ConfigField<T>, value: T): ConfigWriteResult {
            writes += field.key to (value as Any)
            if (value < field.min || value > field.max) {
                return ConfigWriteResult.OutOfRange(field.key, field.min, field.max, value)
            }
            entries.value = entries.value + (field.key to value)
            accepted[field.key] = value
            return ConfigWriteResult.Accepted
        }

        override suspend fun <T : Comparable<T>> reset(field: ConfigField<T>) {
            entries.value = entries.value - field.key
        }
    }

    private class FakeBalanceConfigSource : BalanceConfigSource {
        override val config: StateFlow<BalanceConfig> = MutableStateFlow(BalanceConfig())
    }

    private fun viewModelWith(store: RecordingStore) = TuningPanelViewModel(
        store = store,
        balanceConfigSource = FakeBalanceConfigSource(),
        petAnimationConfigSource = PetAnimationConfigSource(store),
        appContext = ApplicationProvider.getApplicationContext(),
    )

    @Test
    fun `typing 600 into frameIntervalMillis submits 600, not some other number`() {
        val store = RecordingStore()
        composeRule.setContent { TuningPanelScreen(viewModel = viewModelWith(store)) }

        val key = PetAnimationConfig.FRAME_INTERVAL_MILLIS.key

        // The rows arrive from a flow, so they are not present on the first composition.
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodes(hasScrollAction())
                .fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNode(hasScrollAction())
            .performScrollToNode(hasTestTag("tuning_row_$key"))

        composeRule.onNodeWithTag("tuning_input_$key").performTextClearance()
        composeRule.onNodeWithTag("tuning_input_$key").performTextInput("600")
        composeRule.onNodeWithTag("tuning_set_$key").performClick()
        composeRule.waitForIdle()

        assertEquals(listOf<Pair<String, Any>>(key to 600L), store.writes)
    }

    /**
     * The exact sequence performed on the device: 600, then 2000, then 5000. The last is above the
     * declared maximum of 2000 and must be refused, leaving the previous accepted value in place.
     */
    @Test
    fun `the 600 then 2000 then 5000 sequence ends with 2000 stored and 5000 refused`() {
        val store = RecordingStore()
        composeRule.setContent { TuningPanelScreen(viewModel = viewModelWith(store)) }

        val key = PetAnimationConfig.FRAME_INTERVAL_MILLIS.key
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodes(hasScrollAction())
                .fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNode(hasScrollAction())
            .performScrollToNode(hasTestTag("tuning_row_$key"))

        listOf("600", "2000", "5000").forEach { typed ->
            composeRule.onNodeWithTag("tuning_input_$key").performTextClearance()
            composeRule.onNodeWithTag("tuning_input_$key").performTextInput(typed)
            composeRule.onNodeWithTag("tuning_set_$key").performClick()
            composeRule.waitForIdle()
        }

        assertEquals(
            listOf<Pair<String, Any>>(key to 600L, key to 2000L, key to 5000L),
            store.writes,
        )
        // 5000 was submitted and refused, so the last value the store actually holds is 2000.
        assertEquals(2000L, store.accepted[key])
        composeRule.onNodeWithTag("tuning_rejection_$key").assertExists()
    }
}
