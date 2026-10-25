package io.github.mgdx.potable.data.follow

import kotlinx.coroutines.flow.Flow

/**
 * A distribution unit the user follows (SPEC §5 F6, §7 table `suivi`).
 *
 * [communeCode] is the code Hub'Eau publishes under (`Commune.queryCode`);
 * [communeName] and [udiName] are kept as shown when the follow was added,
 * so that the list reads offline.
 */
data class Follow(
    val id: Long,
    val communeCode: String,
    val communeName: String,
    val udiCode: String,
    val udiName: String,
    val position: Int,
    val isPrimary: Boolean,
)

/** Implemented with Room. */
interface FollowRepository {
    /** Every follow, ordered by [Follow.position]. */
    val follows: Flow<List<Follow>>

    /**
     * Follows a unit. Idempotent on (communeCode, udiCode): returns the
     * existing follow if there is one. The first follow becomes primary;
     * a new one is appended at the end.
     */
    suspend fun follow(
        communeCode: String,
        communeName: String,
        udiCode: String,
        udiName: String,
    ): Follow

    /**
     * Stops following and deletes the unit's cached samples and history. If the primary
     * follow is removed, the first remaining one becomes primary.
     */
    suspend fun unfollow(id: Long)

    /** Moves a follow to [newPosition] (0-based, clamped), shifting the others. */
    suspend fun move(id: Long, newPosition: Int)

    suspend fun setPrimary(id: Long)

    suspend fun find(communeCode: String, udiCode: String): Follow?
}
