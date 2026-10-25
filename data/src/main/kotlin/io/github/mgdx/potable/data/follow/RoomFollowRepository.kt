package io.github.mgdx.potable.data.follow

import androidx.room.withTransaction
import io.github.mgdx.potable.data.local.FollowEntity
import io.github.mgdx.potable.data.local.PotableDatabase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/**
 * [FollowRepository] over the `suivi` table. Positions are kept contiguous
 * (0 to n - 1) and exactly one follow is primary as soon as there is one.
 */
class RoomFollowRepository(private val database: PotableDatabase) : FollowRepository {
    private val dao = database.follows()
    private val cache = database.cache()
    private val history = database.history()

    override val follows: Flow<List<Follow>> =
        dao.observeAll().map { rows -> rows.map { it.toFollow() } }.distinctUntilChanged()

    override suspend fun follow(
        communeCode: String,
        communeName: String,
        udiCode: String,
        udiName: String,
    ): Follow = database.withTransaction {
        dao.find(communeCode, udiCode)?.toFollow() ?: run {
            val count = dao.all().size
            val row =
                FollowEntity(
                    communeCode = communeCode,
                    communeName = communeName,
                    udiCode = udiCode,
                    udiName = udiName,
                    position = count,
                    principale = count == 0,
                )
            row.copy(id = dao.insert(row)).toFollow()
        }
    }

    override suspend fun unfollow(id: Long) {
        database.withTransaction {
            val removed = dao.byId(id) ?: return@withTransaction
            dao.delete(id)
            cache.deleteUnitResults(removed.communeCode, removed.udiCode)
            cache.deleteUnitSamples(removed.communeCode, removed.udiCode)
            cache.deleteUnitSync(removed.communeCode, removed.udiCode)
            history.deleteUnitPoints(removed.communeCode, removed.udiCode)
            history.deleteUnitSeries(removed.communeCode, removed.udiCode)
            history.deleteUnitTimeline(removed.communeCode, removed.udiCode)
            history.deleteUnitTimelineSync(removed.communeCode, removed.udiCode)
            val remaining = dao.all()
            remaining.forEachIndexed { index, row ->
                if (row.position != index) dao.setPosition(row.id, index)
            }
            if (removed.principale) remaining.firstOrNull()?.let { dao.makePrimary(it.id) }
        }
    }

    override suspend fun move(id: Long, newPosition: Int) {
        database.withTransaction {
            val rows = dao.all().toMutableList()
            val index = rows.indexOfFirst { it.id == id }
            if (index < 0) return@withTransaction
            val moved = rows.removeAt(index)
            rows.add(newPosition.coerceIn(0, rows.size), moved)
            rows.forEachIndexed { position, row ->
                if (row.position != position) dao.setPosition(row.id, position)
            }
        }
    }

    override suspend fun setPrimary(id: Long) {
        database.withTransaction {
            if (dao.byId(id) != null) dao.makePrimary(id)
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
        position = position,
        isPrimary = principale,
    )
}
