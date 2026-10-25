package io.github.mgdx.potable.core

import io.github.mgdx.potable.core.model.Commune
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/** In-memory search over the embedded communes, without any network call. */
class CommuneIndex(communes: List<Commune>) {
    private class Entry(val commune: Commune, val name: String)

    /**
     * Entries in tie-break order: current commune before a delegated or
     * former commune or an arrondissement, then largest population (unknown
     * last), then shortest name, then alphabetical. A search then only has to
     * sort by score, which it does with stable buckets.
     */
    private val entries: List<Entry> = communes
        .map { Entry(it, TextNormalizer.normalize(it.name)) }
        .sortedWith(
            compareBy<Entry> { it.commune.hubEauCode != null }
                .thenBy(nullsLast(reverseOrder())) { it.commune.population }
                .thenBy { it.name.length }
                .thenBy { it.name }
                .thenBy { it.commune.code },
        )

    /**
     * Places [nearest] may return: current communes, plus the municipal
     * arrondissements of Paris, Lyon and Marseille, recognised by their
     * `hubEauCode` (the commune they belong to, fixed by the PLM law, as in
     * tools/generate_communes.py). An arrondissement is a finer answer than
     * its commune's single centroid, and its `queryCode` still leads to the
     * commune on Hub'Eau. Delegated and associated communes stay out: their
     * name is a former one.
     */
    private val byCode: Map<String, Commune> = communes.associateBy { it.code }

    private val locatable: List<Commune> = communes.filter {
        it.hubEauCode == null || it.hubEauCode in PLM_COMMUNES
    }

    /**
     * Communes matching [query], best first, at most [limit].
     *
     * The query is normalized like the names ([TextNormalizer]). A numeric
     * query matches postal codes (exact code, then prefix), then the exact
     * INSEE code. Any other query ranks names: exact, then starting with the
     * query, then with a word starting with the query, then containing it.
     * Within a rank: current commune first, then largest population (entries
     * without one last), then shortest name, then alphabetical. An empty
     * query gives an empty list.
     *
     * A last word "st" or "ste" may be a prefix being typed ("st" towards
     * "Strasbourg") as well as an abbreviation of "saint"/"sainte": both
     * forms are tried, and at equal rank the literal one comes first.
     */
    fun search(query: String, limit: Int = 20): List<Commune> {
        val q = TextNormalizer.normalize(query)
        if (q.isEmpty() || limit <= 0) return emptyList()
        val buckets = List(BUCKETS) { ArrayList<Commune>() }
        val rank: (Entry) -> Int = if (q.all { it in '0'..'9' }) {
            numericRank(q)
        } else {
            val literal = TextNormalizer.normalizeKeepingLastAbbreviation(query)
            if (literal == null) textRank(q) else eitherRank(textRank(literal), textRank(q))
        }
        for (entry in entries) {
            val r = rank(entry)
            if (r >= 0 && buckets[r].size < limit) buckets[r] += entry.commune
        }
        return buckets.flatten().take(limit)
    }

    private fun numericRank(q: String): (Entry) -> Int = { entry ->
        val postal = entry.commune.postalCodes
        when {
            postal.any { it == q } -> 0
            postal.any { it.startsWith(q) } -> 1
            entry.commune.code == q -> 2
            else -> NO_MATCH
        }
    }

    /** Best of two text ranks, [first] winning a tie: ranks span 0 until 2 × [TEXT_RANKS]. */
    private fun eitherRank(first: (Entry) -> Int, second: (Entry) -> Int): (Entry) -> Int =
        { entry ->
            val a = first(entry).let { if (it == NO_MATCH) Int.MAX_VALUE else 2 * it }
            val b = second(entry).let { if (it == NO_MATCH) Int.MAX_VALUE else 2 * it + 1 }
            minOf(a, b).let { if (it == Int.MAX_VALUE) NO_MATCH else it }
        }

    private fun textRank(q: String): (Entry) -> Int {
        val wordStart = " $q"
        return { entry ->
            val name = entry.name
            when {
                name == q -> 0
                name.startsWith(q) -> 1
                name.contains(wordStart) -> 2
                name.contains(q) -> 3
                else -> NO_MATCH
            }
        }
    }

    /**
     * The entry whose INSEE code is exactly [code] (current, delegated or
     * former commune, arrondissement), or null. To name the commune behind a
     * [Commune.hubEauCode].
     */
    fun byCode(code: String): Commune? = byCode[code]

    /**
     * The current commune or municipal arrondissement (see [locatable])
     * whose centroid is closest to the given point (haversine distance).
     * Null if there is none, or if that centroid is farther than
     * [maxDistanceKm] when one is given (e.g. a position outside France).
     */
    fun nearest(latitude: Double, longitude: Double, maxDistanceKm: Double? = null): Commune? {
        val distance = { c: Commune -> haversine(latitude, longitude, c.latitude, c.longitude) }
        val closest = locatable.minByOrNull(distance) ?: return null
        if (maxDistanceKm != null && distance(closest) * EARTH_RADIUS_KM > maxDistanceKm) {
            return null
        }
        return closest
    }

    private companion object {
        const val TEXT_RANKS = 4
        const val BUCKETS = 2 * TEXT_RANKS
        const val NO_MATCH = -1

        /** Mean Earth radius (IUGG), to turn a central angle into kilometres. */
        const val EARTH_RADIUS_KM = 6371.0088

        /** Communes divided into municipal arrondissements: Paris, Lyon, Marseille. */
        val PLM_COMMUNES = setOf("75056", "69123", "13055")

        /** Central angle between two points, enough to compare distances. */
        fun haversine(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
            val dLat = Math.toRadians(lat2 - lat1)
            val dLon = Math.toRadians(lon2 - lon1)
            val a = sin(dLat / 2).let { it * it } +
                cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
                sin(dLon / 2).let { it * it }
            return 2 * asin(min(1.0, sqrt(a)))
        }
    }
}
