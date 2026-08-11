package com.gcatcode.petmephone.feature.overlay.character.ui

import android.net.Uri
import com.gcatcode.petmephone.core.domain.character.CharacterImportRejection
import com.gcatcode.petmephone.feature.overlay.character.CharacterImportResult
import com.gcatcode.petmephone.feature.overlay.character.CharacterImporter
import com.gcatcode.petmephone.feature.overlay.character.StageResult
import com.gcatcode.petmephone.feature.overlay.character.ValidatedImport
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Drives the pick → stage → (loading) → decode-and-scan pipeline for [ImportScreen]/[PreviewScreen]
 * (tasks 63–65). A plain `@Inject`-constructed class rather than an `androidx.lifecycle.ViewModel`:
 * this module's Compose convention plugin deliberately excludes `activity-compose` and this repo
 * has no `hiltViewModel()` wiring yet (`design.md` scopes that to screens outside the overlay
 * window, added when those screens are actually hosted). The caller (an `@AndroidEntryPoint`
 * Activity, out of scope for this module) owns this instance's lifetime and calls [close] from its
 * `onDestroy`.
 */
class CharacterImportController @Inject constructor(
    private val importer: CharacterImporter,
) {

    sealed interface State {
        data object Idle : State
        data object Staging : State

        /** Tiers 1–2 passed; tier 3 (the only costly one) is running. */
        data object DecodingAndScanning : State
        data class Rejected(val reason: CharacterImportRejection) : State
        data class Ready(val import: ValidatedImport) : State
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val _state = MutableStateFlow<State>(State.Idle)
    val state: StateFlow<State> = _state.asStateFlow()

    /** Called with the [Uri] `PickVisualMedia` returns — no storage permission is ever requested
     * anywhere in this flow. */
    fun onImagePicked(uri: Uri) {
        scope.launch {
            _state.value = State.Staging
            when (val staged = importer.stage(uri)) {
                is StageResult.Rejected -> _state.value = State.Rejected(staged.reason)
                is StageResult.Staged -> {
                    _state.value = State.DecodingAndScanning
                    when (val result = importer.decodeAndScan(staged.staged)) {
                        is CharacterImportResult.Rejected -> _state.value = State.Rejected(result.reason)
                        is CharacterImportResult.Validated -> _state.value = State.Ready(result.import)
                    }
                }
            }
        }
    }

    fun reset() {
        val current = _state.value
        if (current is State.Ready) {
            importer.abandon(current.import)
        }
        _state.value = State.Idle
    }

    fun close() {
        scope.coroutineContext[Job]?.cancel()
    }
}
