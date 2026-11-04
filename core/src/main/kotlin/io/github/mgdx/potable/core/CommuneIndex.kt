package io.github.mgdx.potable.core

import io.github.mgdx.potable.core.model.Commune
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * In-memory search over the embedded communes, without any network call.
 *
 * The 37,000 entries stay in parallel arrays rather than one object per
 * entry: their texts are [Packed] end to end, so that a search is a few
 * `indexOf` over one string, and a [Commune] is only built for a result.
 */
class CommuneIndex private constructor(rows: Rows) {
    constructor(communes: List<Commune>) : this(
        Rows().apply {
            communes.forEach { c ->
                require(c.postalCodes.none { it.isEmpty() || ',' in it }) {
                    "empty postal code or one with a \",\""
                }
                add(
                    c.code,
                    c.name,
                    c.postalCodes.joinToString(","),
                    c.latitude,
                    c.longitude,
                    c.hubEauCode,
                    c.population,
                )
            }
        },
    )

    private val size = rows.size

    /**
     * Entries are stored in tie-break order: current commune before a
     * delegated or former commune or an arrondissement, then largest
     * population (unknown last), then shortest name, then alphabetical. A
     * search then only has to sort by score, which it does with stable
     * buckets. order[i] is the position in the input of the i-th entry.
     */
    private val order: IntArray = rows.order()

    private val codes = Packed(size) { rows.codes[order[it]] }
    private val names = Packed(size) { rows.names[order[it]] }

    /** Names normalized by [TextNormalizer]: what a search compares. */
    private val keys = Packed(size) { rows.keys[order[it]] }

    /** Postal codes of each entry as in the file, "," separated. */
    private val postalCodes = Packed(size) { rows.postalCodes[order[it]] }
    private val latitudes = DoubleArray(size) { rows.latitudes[order[it]] }
    private val longitudes = DoubleArray(size) { rows.longitudes[order[it]] }
    private val hubEauCodes = Array(size) { rows.hubEauCodes[order[it]] }
    private val populations = IntArray(size) { rows.populations[order[it]] }

    /**
     * Entries by code, then by position in the input, for a binary search:
     * [byCode] gives the last entry of a code, as a map built in input order
     * would.
     */
    private val byCodeOrder: IntArray

    /**
     * Entries [nearest] may return, in input order: current communes, plus
     * the municipal arrondissements of Paris, Lyon and Marseille, recognised
     * by their `hubEauCode` (the commune they belong to, fixed by the PLM law,
     * as in tools/generate_communes.py). An arrondissement is a finer answer
     * than its commune's single centroid, and its `queryCode` still leads to
     * the commune on Hub'Eau. Delegated and associated communes stay out:
     * their name is a former one.
     */
    private val locatable: IntArray

    init {
        val entryOf = IntArray(size)
        order.forEachIndexed { entry, input -> entryOf[input] = entry }
        byCodeOrder = (0 until size)
            .sortedWith(compareBy<Int> { rows.codes[it] }.thenBy { it })
            .map { entryOf[it] }
            .toIntArray()
        locatable = (0 until size)
            .filter { rows.hubEauCodes[it].let { hub -> hub == null || hub in PLM_COMMUNES } }
            .map { entryOf[it] }
            .toIntArray()
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
        val ranks = IntArray(size) { NO_MATCH }
        if (q.all { it in '0'..'9' }) {
            rankPostalCodes(q, ranks)
            for (i in codeRange(q)) {
                val entry = byCodeOrder[i]
                ranks[entry] = min(ranks[entry], CODE_RANK)
            }
        } else {
            val literal = TextNormalizer.normalizeKeepingLastAbbreviation(query)
            if (literal == null) {
                rankNames(q, ranks, factor = 1, offset = 0)
            } else {
                // Ranks span 0 until 2 × TEXT_RANKS, the literal form winning a tie.
                rankNames(literal, ranks, factor = 2, offset = 0)
                rankNames(q, ranks, factor = 2, offset = 1)
            }
        }
        val buckets = List(BUCKETS) { ArrayList<Int>() }
        for (entry in 0 until size) {
            val r = ranks[entry]
            if (r != NO_MATCH && buckets[r].size < limit) buckets[r] += entry
        }
        return buckets.asSequence().flatten().take(limit).map(::commune).toList()
    }

    /** Exact postal code 0, prefix of a postal code 1. */
    private fun rankPostalCodes(q: String, ranks: IntArray) {
        val text = postalCodes.text
        var entry = 0
        var at = text.indexOf(q)
        while (at >= 0) {
            while (postalCodes.end(entry) < at) entry++
            if (at == postalCodes.start(entry) || text[at - 1] == ',') {
                val next = text[at + q.length]
                val r = if (next == ',' || next == Packed.END) 0 else 1
                ranks[entry] = min(ranks[entry], r)
            }
            at = text.indexOf(q, at + 1)
        }
    }

    /** Exact name 0, name starting with [q] 1, a word starting with it 2, containing it 3. */
    private fun rankNames(q: String, ranks: IntArray, factor: Int, offset: Int) {
        val text = keys.text
        var entry = 0
        var at = text.indexOf(q)
        while (at >= 0) {
            while (keys.end(entry) < at) entry++
            val r = when {
                at != keys.start(entry) -> if (text[at - 1] == ' ') 2 else 3
                at + q.length == keys.end(entry) -> 0
                else -> 1
            }
            ranks[entry] = min(ranks[entry], factor * r + offset)
            at = text.indexOf(q, at + 1)
        }
    }

    /** Positions in [byCodeOrder] of the entries whose code is [code], in input order. */
    private fun codeRange(code: String): IntRange {
        var low = 0
        var high = size
        while (low < high) {
            val mid = (low + high) ushr 1
            if (codes.compare(byCodeOrder[mid], code) < 0) low = mid + 1 else high = mid
        }
        var end = low
        while (end < size && codes.compare(byCodeOrder[end], code) == 0) end++
        return low until end
    }

    /**
     * The entry whose INSEE code is exactly [code] (current, delegated or
     * former commune, arrondissement), or null. To name the commune behind a
     * [Commune.hubEauCode].
     */
    fun byCode(code: String): Commune? =
        codeRange(code).lastOrNull()?.let { commune(byCodeOrder[it]) }

    /**
     * The current commune or municipal arrondissement (see [locatable])
     * whose centroid is closest to the given point (haversine distance).
     * Null if there is none, or if that centroid is farther than
     * [maxDistanceKm] when one is given (e.g. a position outside France).
     */
    fun nearest(latitude: Double, longitude: Double, maxDistanceKm: Double? = null): Commune? {
        var closest = -1
        var closestDistance = 0.0
        for (entry in locatable) {
            val d = haversine(latitude, longitude, latitudes[entry], longitudes[entry])
            if (closest < 0 || d.compareTo(closestDistance) < 0) {
                closest = entry
                closestDistance = d
            }
        }
        if (closest < 0) return null
        if (maxDistanceKm != null && closestDistance * EARTH_RADIUS_KM > maxDistanceKm) return null
        return commune(closest)
    }

    private fun commune(entry: Int) = Commune(
        code = codes[entry],
        name = names[entry],
        postalCodes = postalCodes[entry].split(',').filter { it.isNotEmpty() },
        latitude = latitudes[entry],
        longitude = longitudes[entry],
        hubEauCode = hubEauCodes[entry],
        population = populations[entry].takeIf { it != NO_POPULATION },
    )

    /** Strings end to end, each followed by [END], read by position. */
    private class Packed(count: Int, value: (Int) -> String) {
        val text: String
        private val starts = IntArray(count + 1)

        init {
            val builder = StringBuilder()
            for (i in 0 until count) {
                val s = value(i)
                require(END !in s) { "line break in an entry" }
                starts[i] = builder.length
                builder.append(s).append(END)
            }
            starts[count] = builder.length
            text = builder.toString()
        }

        fun start(i: Int) = starts[i]

        /** Position of the [END] after the i-th string. */
        fun end(i: Int) = starts[i + 1] - 1

        operator fun get(i: Int): String = text.substring(start(i), end(i))

        /** The i-th string compared to [other], as [String.compareTo] would. */
        fun compare(i: Int, other: String): Int {
            val start = start(i)
            val length = end(i) - start
            for (k in 0 until min(length, other.length)) {
                val c = text[start + k] - other[k]
                if (c != 0) return c
            }
            return length - other.length
        }

        companion object {
            const val END = '\n'
        }
    }

    /** The entries in input order, while the index is built. */
    private class Rows {
        val codes = ArrayList<String>()
        val names = ArrayList<String>()
        val keys = ArrayList<String>()
        val postalCodes = ArrayList<String>()
        val hubEauCodes = ArrayList<String?>()
        var latitudes = DoubleArray(INITIAL_CAPACITY)
        var longitudes = DoubleArray(INITIAL_CAPACITY)
        var populations = IntArray(INITIAL_CAPACITY)
        val size get() = codes.size

        /** One instance per distinct `hubEauCode`: many entries share one. */
        private val hubs = HashMap<String, String>()

        fun add(
            code: String,
            name: String,
            postalCodes: String,
            latitude: Double,
            longitude: Double,
            hubEauCode: String?,
            population: Int?,
        ) {
            if (size == latitudes.size) {
                latitudes = latitudes.copyOf(2 * size)
                longitudes = longitudes.copyOf(2 * size)
                populations = populations.copyOf(2 * size)
            }
            latitudes[size] = latitude
            longitudes[size] = longitude
            populations[size] = population ?: NO_POPULATION
            codes += code
            names += name
            keys += TextNormalizer.normalize(name)
            this.postalCodes += postalCodes
            hubEauCodes += hubEauCode?.let { hubs.getOrPut(it) { it } }
        }

        /** Input positions in tie-break order (see [CommuneIndex.order]); the sort is stable. */
        fun order(): IntArray = (0 until size).sortedWith { a, b ->
            compareValues(hubEauCodes[a] != null, hubEauCodes[b] != null).takeIf { it != 0 }
                ?: comparePopulations(populations[a], populations[b]).takeIf { it != 0 }
                ?: compareValues(keys[a].length, keys[b].length).takeIf { it != 0 }
                ?: keys[a].compareTo(keys[b]).takeIf { it != 0 }
                ?: codes[a].compareTo(codes[b])
        }.toIntArray()

        /** Largest first, unknown last. */
        private fun comparePopulations(a: Int, b: Int): Int = when {
            a == NO_POPULATION -> if (b == NO_POPULATION) 0 else 1
            b == NO_POPULATION -> -1
            else -> b.compareTo(a)
        }
    }

    companion object {
        /**
         * Reads the embedded TSV ([CommuneFile] format) straight into an
         * index, without building a [Commune] per entry.
         *
         * @throws IllegalArgumentException on a malformed line.
         */
        fun parse(lines: Sequence<String>): CommuneIndex {
            val rows = Rows()
            CommuneFile.read(lines, rows::add)
            return CommuneIndex(rows)
        }

        private const val TEXT_RANKS = 4
        private const val BUCKETS = 2 * TEXT_RANKS
        private const val CODE_RANK = 2
        private const val NO_MATCH = Int.MAX_VALUE
        private const val NO_POPULATION = Int.MIN_VALUE
        private const val INITIAL_CAPACITY = 1024

        /** Mean Earth radius (IUGG), to turn a central angle into kilometres. */
        private const val EARTH_RADIUS_KM = 6371.0088

        /** Communes divided into municipal arrondissements: Paris, Lyon, Marseille. */
        private val PLM_COMMUNES = setOf("75056", "69123", "13055")

        /** Central angle between two points, enough to compare distances. */
        private fun haversine(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
            val dLat = Math.toRadians(lat2 - lat1)
            val dLon = Math.toRadians(lon2 - lon1)
            val a = sin(dLat / 2).let { it * it } +
                cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
                sin(dLon / 2).let { it * it }
            return 2 * asin(min(1.0, sqrt(a)))
        }
    }
}
