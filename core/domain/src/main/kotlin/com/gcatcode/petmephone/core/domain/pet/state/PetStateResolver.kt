package com.gcatcode.petmephone.core.domain.pet.state

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * Resolves [PetState] from a stream of [PetSnapshot]s by consulting the registered
 * [PetStateProvider]s in descending priority order and returning the first non-null result, or
 * [PetState.IDLE] when none match.
 *
 * Fails fast at construction, never at [resolve]/[states] time, if two providers share a
 * priority — see `pet-state-resolution`'s fail-fast requirement.
 */
class PetStateResolver(
    providers: Set<PetStateProvider>,
    private val config: PetStateConfig,
) {
    private val orderedProviders = providers.sortedByDescending { it.priority }

    init {
        require(providers.distinctBy { it.priority }.size == providers.size) {
            "PetStateResolver requires distinct provider priorities, got: " +
                providers.map { it.priority }
        }
    }

    /** First non-null result across providers in descending priority order, else [PetState.IDLE]. */
    fun resolve(snapshot: PetSnapshot): PetState =
        orderedProviders.firstNotNullOfOrNull { it.evaluate(snapshot) } ?: PetState.IDLE

    /**
     * Resolves every emitted [snapshots] value and applies [config]'s minimum dwell time, so a
     * flapping snapshot does not flicker the emitted state. Never gated by screen-on/off — see
     * `pet-state-resolution`'s screen-off requirement.
     */
    fun states(snapshots: Flow<PetSnapshot>): Flow<PetState> =
        snapshots.map { resolve(it) }.withMinimumDwell(config.minimumDwellMillis)
}

/**
 * Custom dwell operator, per `design.md` decision 2: `distinctUntilChanged()` then
 * delay-the-remainder-and-recheck-latest. Deliberately NOT `debounce`/`sample`, which drop the
 * *first* change until the source goes quiet — this emits immediately whenever the dwell window
 * is already clear, and only coalesces values that arrive faster than [minimumDwellMillis].
 */
private fun <T> Flow<T>.withMinimumDwell(minimumDwellMillis: Long): Flow<T> = flow {
    coroutineScope {
        val buffered = Channel<T>(Channel.CONFLATED)
        val upstreamJob = launch {
            this@withMinimumDwell.distinctUntilChanged().collect { buffered.trySend(it) }
            buffered.close()
        }
        try {
            for (value in buffered) {
                emit(value)
                delay(minimumDwellMillis)
            }
        } finally {
            upstreamJob.cancel()
        }
    }
}
