package io.github.mgdx.potable.data

import android.content.Context
import io.github.mgdx.potable.core.CommuneFile
import io.github.mgdx.potable.core.CommuneIndex
import io.github.mgdx.potable.core.model.Commune
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.InputStream

/** Reads `assets/communes.tsv` once and searches it in memory. */
class AssetCommuneRepository internal constructor(private val open: () -> InputStream) :
    CommuneRepository {
    constructor(context: Context) : this(open = assetOpener(context.applicationContext))

    private val mutex = Mutex()

    @Volatile
    private var index: CommuneIndex? = null

    override suspend fun search(query: String, limit: Int): List<Commune> =
        index().search(query, limit)

    override suspend fun nearest(
        latitude: Double,
        longitude: Double,
        maxDistanceKm: Double?,
    ): Commune? = index().nearest(latitude, longitude, maxDistanceKm)

    override suspend fun preload() {
        index()
    }

    override suspend fun byCode(code: String): Commune? = index().byCode(code)

    private suspend fun index(): CommuneIndex = index ?: mutex.withLock {
        index ?: withContext(Dispatchers.Default) {
            val communes =
                open().bufferedReader(Charsets.UTF_8).use { reader ->
                    CommuneFile.parse(reader.lineSequence())
                }
            CommuneIndex(communes)
        }.also { index = it }
    }

    private companion object {
        const val ASSET = "communes.tsv"

        fun assetOpener(app: Context): () -> InputStream = { app.assets.open(ASSET) }
    }
}
