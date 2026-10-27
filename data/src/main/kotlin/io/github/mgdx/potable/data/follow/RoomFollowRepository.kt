package io.github.mgdx.potable.data.follow

import androidx.room.withTransaction
import io.github.mgdx.potable.data.local.FollowEntity
import io.github.mgdx.potable.data.local.PotableDatabase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/**
 * [FollowRepository] over the `suivi` table, sorted by name ([FollowOrder]).
 * The `position` column is a leftover of the manual order, dropped
 * (docs/decisions.md, 2026-10-27): it stays in the schema to avoid a
 * migration, is written 0 and never read. The `principale` column is a leftover of the primary
 * commune, dropped in 0.1.2 (docs/decisions.md, 2026-10-27): it stays in the
 * schema to avoid a migration, is written false and never read.
 * Unfollowing keeps the unit's cache (it is that of any unit looked at) and
 * forgets the date its alerts have seen.
 */
class RoomFollowRepository(private val database: PotableDatabase) : FollowRepository {
    private val dao = database.follows()
    private val cache = database.cache()

    override val follows: Flow<List<Follow>> =
        dao.observeAll()
            .map { rows -> rows.map { it.toFollow() }.sortedWith(FollowOrder.comparator()) }
            .distinctUntilChanged()

    override suspend fun follow(
        communeCode: String,
        communeName: String,
        udiCode: String,
        udiName: String,
    ): Follow = database.withTransaction {
        dao.find(communeCode, udiCode)?.toFollow() ?: run {
            val row =
                FollowEntity(
                    communeCode = communeCode,
                    communeName = communeName,
                    udiCode = udiCode,
                    udiName = udiName,
                    position = 0,
                    principale = false,
                )
            row.copy(id = dao.insert(row)).toFollow()
        }
    }

    override suspend fun unfollow(id: Long) {
        database.withTransaction {
            val removed = dao.byId(id) ?: return@withTransaction
            dao.delete(id)
            // The cache stays, like that of any unit looked at; only the
            // alert state goes, so that following it again starts from a
            // new baseline (SPEC §5 F7).
            cache.clearLastSeenSampleDate(removed.communeCode, removed.udiCode)
        }
    }

    override suspend fun find(communeCode: String, udiCode: String): Follow? =
        dao.find(communeCode, udiCode)?.toFollow()

    private fun FollowEntity.toFollow() = Follow(
        id = id,
        communeCode = communeCode,
        communeName = communeName,
        udiCode = udiCode,
        udiName = udiName,
    )
}
