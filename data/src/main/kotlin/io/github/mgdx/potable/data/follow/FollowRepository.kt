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
)

/** Implemented with Room. */
interface FollowRepository {
    /**
     * Every follow, in alphabetical order of [Follow.communeName], then of
     * [Follow.udiName] ([FollowOrder]): the list, the search and the alerts
     * all see them in this order.
     */
    val follows: Flow<List<Follow>>

    /**
     * Follows a unit. Idempotent on (communeCode, udiCode): returns the
     * existing follow if there is one.
     */
    suspend fun follow(
        communeCode: String,
        communeName: String,
        udiCode: String,
        udiName: String,
    ): Follow

    /**
     * Stops following and deletes the unit's cached samples and history.
     */
    suspend fun unfollow(id: Long)

    suspend fun find(communeCode: String, udiCode: String): Follow?
}
