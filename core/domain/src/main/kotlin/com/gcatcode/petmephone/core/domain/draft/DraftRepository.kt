package com.gcatcode.petmephone.core.domain.draft

import kotlinx.coroutines.flow.Flow

/**
 * The one draft the app keeps (#100).
 *
 * **One draft at a time, structurally.** There is no id in this interface and no way to ask for a
 * second one: a save replaces whatever was there. Two concurrent drafts is state nobody asked for,
 * and the surface that would have to reconcile them does not exist.
 *
 * A second creation started while one is pending therefore either resumes it or replaces it, and
 * that choice belongs to the caller — which is the point at which the user can be told.
 */
interface DraftRepository {

    /**
     * The pending draft, or `null` when there is none. A `Flow` because the overlay and the full
     * app can both be looking at it, and a stale copy is how two surfaces disagree about what the
     * user typed.
     */
    fun current(): Flow<AuthoringDraft?>

    /** Writes [draft], replacing any pending one. Every call bumps `updatedAt`. */
    suspend fun save(draft: AuthoringDraft)

    /**
     * Throws the draft away. This is Cancel, and it is the ONLY thing that discards.
     *
     * Outside tap must keep dismissing the window without discarding — trapping a user inside a
     * form on an overlay, with the real app underneath unreachable, is hostile on a surface where
     * the app is a guest. Which is exactly why Cancel is required rather than merely nice: once
     * outside tap stops discarding, this is the only way to say "not this one", and without it a
     * draft is unabandonable.
     */
    suspend fun discard()
}
