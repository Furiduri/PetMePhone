package com.gcatcode.petmephone.debug.tuning

import android.content.Context
import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gcatcode.petmephone.core.domain.balance.BalanceConfig
import com.gcatcode.petmephone.core.domain.character.ActiveCharacterRepository
import com.gcatcode.petmephone.core.domain.config.BalanceConfigSource
import com.gcatcode.petmephone.core.domain.config.ConfigField
import com.gcatcode.petmephone.core.domain.config.ConfigOverrideStore
import com.gcatcode.petmephone.core.domain.config.ConfigWriteResult
import com.gcatcode.petmephone.core.domain.config.StoredOverride
import com.gcatcode.petmephone.feature.overlay.character.CharacterSheetLoader
import com.gcatcode.petmephone.feature.overlay.character.CharacterSheets
import com.gcatcode.petmephone.feature.overlay.service.PetOverlayService
import com.gcatcode.petmephone.feature.overlay.ui.PetAnimationConfig
import com.gcatcode.petmephone.feature.overlay.ui.PetAnimationConfigSource
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Reads and writes exclusively through [ConfigOverrideStore] (design decisions 3, 3a) — no second
 * write path, no raw persistence exists here. [BalanceConfigSource] and [PetAnimationConfigSource]
 * are injected only for the independent "in use" readout the screen renders beside the rows.
 */
@HiltViewModel
class TuningPanelViewModel @Inject constructor(
    private val store: ConfigOverrideStore,
    val balanceConfigSource: BalanceConfigSource,
    val petAnimationConfigSource: PetAnimationConfigSource,
    @ApplicationContext private val appContext: Context,
    private val activeCharacterRepository: ActiveCharacterRepository,
    private val sheetLoader: CharacterSheetLoader,
) : ViewModel() {

    /**
     * One flow per registered field, in the fixed order the screen renders them: [BalanceConfig.ALL]
     * then [PetAnimationConfig.ALL] — the same eight fields `tuningRowOf`'s registry-coverage
     * invariant is pinned against.
     */
    private fun <T : Comparable<T>> rowFlow(field: ConfigField<T>) =
        store.override(field).map { stored -> tuningRowOf(field, stored) }

    /** One flow per registered field, over the single [TUNING_FIELDS] list the screen renders. */
    private val rowFlows = TUNING_FIELDS.map { field -> rowFlow(field) }

    /** Exactly eight rows, one per registered field, re-emitted live from the store. */
    val rows: StateFlow<List<TuningRow>> =
        combine(rowFlows) { rowsArray -> rowsArray.toList() }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /**
     * The frame duration the active character declares in its own manifest, or `null` when it
     * declares none.
     *
     * `AnimationPacing` prefers a character's declared duration over the configured default, so
     * whenever this is non-null `frameIntervalMillis` has no effect on screen at all and only
     * `minFrameIntervalMillis` still applies, as a floor. Without this, the panel labels that field
     * `LIVE` and is telling the truth about the flow while a maintainer watches a value they set do
     * nothing — the wrong-conclusion failure issue #92 names. Observed for real: three values were
     * typed, nothing moved, and the panel looked broken.
     */
    val declaredFrameDurationMillis: StateFlow<Long?> = activeCharacterRepository.active
        .mapLatest { id ->
            withContext(Dispatchers.IO) { (sheetLoader.load(id) as? CharacterSheets.Ready)?.frameDurationMillis }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** The only write path: routes straight to [ConfigOverrideStore.set], no second validation. */
    suspend fun <T : Comparable<T>> set(field: ConfigField<T>, value: T): ConfigWriteResult =
        store.set(field, value)

    /** Per-field reset, no confirmation — the store's own contract, restated nowhere else. */
    fun <T : Comparable<T>> reset(field: ConfigField<T>) {
        viewModelScope.launch { store.reset(field) }
    }

    /**
     * Resets every one of the eight fields currently holding an entry. Called only after the
     * caller (the screen's confirm dialog) has already gotten explicit confirmation — this function
     * itself never asks.
     */
    fun resetAll() {
        viewModelScope.launch {
            resetIfPresent(BalanceConfig.DAILY_TASK_GOAL)
            resetIfPresent(BalanceConfig.HUNGRY_THRESHOLD_RATIO)
            resetIfPresent(BalanceConfig.RECURRING_HUNGER_RATIO)
            resetIfPresent(BalanceConfig.RECURRING_HUNGER_CAP)
            resetIfPresent(BalanceConfig.STANDARD_TASK_POINTS)
            resetIfPresent(PetAnimationConfig.FRAME_INTERVAL_MILLIS)
            resetIfPresent(PetAnimationConfig.MIN_FRAME_INTERVAL_MILLIS)
            resetIfPresent(PetAnimationConfig.STATE_SHARING_TIMEOUT_MILLIS)
        }
    }

    private suspend fun <T : Comparable<T>> resetIfPresent(field: ConfigField<T>) {
        if (store.override(field).first() is StoredOverride.Present) store.reset(field)
    }

    /**
     * `stopService` then `startService` on the same [Intent] shape `MainActivity.startOverlayService`
     * uses (design decision 5). No method is added to [PetOverlayService] for this.
     */
    fun restartOverlay() {
        appContext.stopService(Intent(appContext, PetOverlayService::class.java))
        appContext.startService(Intent(appContext, PetOverlayService::class.java))
    }
}
