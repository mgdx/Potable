package io.github.mgdx.potable.data

import android.content.Context
import io.github.mgdx.potable.core.CommuneIndex
import io.github.mgdx.potable.core.model.Commune
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.InputStream

/**
 * Reads `assets/communes.tsv` once and searches it in memory. Reading and
 * every lookup run on [Dispatchers.Default] ([work] in tests), whatever the
 * caller's dispatcher: a search
 * scans 37,000 names at each keystroke and [nearest] computes as many
 * distances, which has no place on the main thread.
 */
class AssetCommuneRepository internal constructor(
    private val work: CoroutineDispatcher = Dispatchers.Default,
    private val open: () -> InputStream,
) : CommuneRepository {
    constructor(context: Context) : this(open = assetOpener(context.applicationContext))

    private val mutex = Mutex()

    @Volatile
    private var index: CommuneIndex? = null

    override suspend fun search(query: String, limit: Int): List<Commune> =
        onIndex { it.search(query, limit) }

    override suspend fun nearest(
        latitude: Double,
        longitude: Double,
        maxDistanceKm: Double?,
    ): Commune? = onIndex { it.nearest(latitude, longitude, maxDistanceKm) }

    override suspend fun preload() {
        index()
    }

    override suspend fun byCode(code: String): Commune? = onIndex { it.byCode(code) }

    private suspend fun <T> onIndex(block: (CommuneIndex) -> T): T {
        val index = index()
        return withContext(work) { block(index) }
    }

    private suspend fun index(): CommuneIndex = index ?: mutex.withLock {
        index ?: withContext(work) {
            open().bufferedReader(Charsets.UTF_8).use { reader ->
                CommuneIndex.parse(reader.lineSequence())
            }
        }.also { index = it }
    }

    private companion object {
        const val ASSET = "communes.tsv"

        fun assetOpener(app: Context): () -> InputStream = { app.assets.open(ASSET) }
    }
}
