package io.github.mgdx.potable.data

import io.github.mgdx.potable.core.model.Commune

/** Offline commune lookup over the embedded file; never touches the network. */
interface CommuneRepository {
    suspend fun search(query: String, limit: Int = 20): List<Commune>

    /** See [io.github.mgdx.potable.core.CommuneIndex.nearest]. */
    suspend fun nearest(
        latitude: Double,
        longitude: Double,
        maxDistanceKm: Double? = null,
    ): Commune?

    /** Reads the referential ahead of the first search; the default does nothing. */
    suspend fun preload() {}

    /**
     * The entry with exactly this INSEE code (current, delegated or former
     * commune, arrondissement), or null; e.g. to name the commune behind a
     * `hubEauCode`. The default finds nothing, for fakes that do not need it.
     */
    suspend fun byCode(code: String): Commune? = null
}
