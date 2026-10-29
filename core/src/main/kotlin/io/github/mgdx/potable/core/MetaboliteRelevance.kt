package io.github.mgdx.potable.core

/**
 * The classification of a pesticide metabolite for drinking water, as
 * published by ANSES: a relevant one is subject to the quality limit of
 * 0.1 µg/L, a non-relevant one to an indicative value of 0.9 µg/L. A fact of
 * the regulation, never compared with a measure (SPEC §14).
 */
enum class Relevance { RELEVANT, NOT_RELEVANT }

/**
 * One row of the ANSES table, its five columns as published, and the
 * Hub'Eau `code_parametre` it was matched with ([parameterCode], null when
 * none was found).
 */
data class MetaboliteEntry(
    val name: String,
    val cas: String,
    val status: String,
    val opinionYear: Int,
    val opinion: String,
    val parameterCode: String?,
    val matchedBy: String?,
) {
    val relevance: Relevance
        get() = when (status) {
            RELEVANT_STATUS -> Relevance.RELEVANT
            NOT_RELEVANT_STATUS -> Relevance.NOT_RELEVANT
            else -> error("unknown status $status")
        }

    internal companion object {
        const val RELEVANT_STATUS = "Pertinent"
        const val NOT_RELEVANT_STATUS = "Non pertinent"
        val STATUSES = setOf(RELEVANT_STATUS, NOT_RELEVANT_STATUS)
    }
}

/**
 * The ANSES table of the metabolites assessed for drinking water ("Tableau
 * relatif aux métabolites pertinents pour les EDCH", last update July 2025),
 * embedded as `metabolites-pertinence.csv`: its source, licence and the
 * matching with Hub'Eau codes are in the file header and in
 * `docs/hubeau-api.md`. A metabolite missing from the table, or not matched
 * with a Hub'Eau code, has no classification here (null), which says nothing
 * of whether it is relevant.
 */
object MetaboliteRelevance {
    internal const val RESOURCE = "/metabolites-pertinence.csv"

    /** Every row of the table, matched or not, in file order. */
    val entries: List<MetaboliteEntry> by lazy {
        val stream = checkNotNull(MetaboliteRelevance::class.java.getResourceAsStream(RESOURCE)) {
            "missing resource $RESOURCE"
        }
        stream.bufferedReader(Charsets.UTF_8).useLines { parse(it) }
    }

    private val byCode: Map<String, Relevance> by lazy {
        entries.mapNotNull { entry -> entry.parameterCode?.let { it to entry.relevance } }.toMap()
    }

    /** The ANSES classification of the metabolite published under this `code_parametre`, if known. */
    fun of(parameterCode: String): Relevance? = byCode[parameterCode]

    /**
     * Parses the table: `#` lines are comments, the first other line is the
     * header, then one row of seven `;`-separated fields per metabolite.
     * Throws on any malformed row, so that a broken file fails the tests
     * instead of silently dropping a classification.
     */
    internal fun parse(lines: Sequence<String>): List<MetaboliteEntry> = lines
        .filter { it.isNotBlank() && !it.startsWith("#") }
        .drop(1)
        .map { line ->
            val fields = line.split(';')
            require(fields.size == FIELD_COUNT) { "expected $FIELD_COUNT fields: $line" }
            val (name, cas, status, year, opinion) = fields
            val entry = MetaboliteEntry(
                name = name,
                cas = cas,
                status = status,
                opinionYear = requireNotNull(year.toIntOrNull()) { "bad year: $line" },
                opinion = opinion,
                parameterCode = fields[5].ifBlank { null },
                matchedBy = fields[6].ifBlank { null },
            )
            require(status in MetaboliteEntry.STATUSES) { "unknown status: $line" }
            require((entry.parameterCode == null) == (entry.matchedBy == null)) {
                "code without method: $line"
            }
            entry
        }
        .toList()

    private const val FIELD_COUNT = 7
}
