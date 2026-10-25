package io.github.mgdx.potable.core

import io.github.mgdx.potable.core.hubeau.UdiRow
import io.github.mgdx.potable.core.model.Udi
import io.github.mgdx.potable.core.model.UdiChoice

object UdiSelection {
    /** `nom_quartier` value meaning "the whole commune". */
    private const val WHOLE_COMMUNE = "totalite"

    /**
     * Keeps only the most recent year present in [rows] (an UDI that served
     * the commune in an earlier year but no longer does is dropped, SPEC §15),
     * then one [Udi] per `code_reseau`. The name is the network's name that
     * year; the districts of a network listed several times are joined with
     * ", ", without duplicates; "totalité" or an empty district gives null.
     * Rows whose `annee` is missing or unreadable are ignored.
     *
     * No UDI gives [UdiChoice.None], one [UdiChoice.Single], several
     * [UdiChoice.Multiple] sorted by name.
     */
    fun choose(rows: List<UdiRow>): UdiChoice {
        val dated = rows.mapNotNull { row -> row.annee?.trim()?.toIntOrNull()?.let { it to row } }
        val year = dated.maxOfOrNull { it.first } ?: return UdiChoice.None
        val byNetwork = LinkedHashMap<String, MutableList<UdiRow>>()
        dated.filter { it.first == year }
            .forEach { (_, row) -> byNetwork.getOrPut(row.codeReseau) { mutableListOf() }.add(row) }
        val udis = byNetwork.map { (code, group) ->
            val name = group.firstNotNullOfOrNull { it.nomReseau?.takeIf(String::isNotBlank) }
            Udi(code = code, name = name ?: code, district = district(group), year = year)
        }
        return when (udis.size) {
            0 -> UdiChoice.None
            1 -> UdiChoice.Single(udis.single())
            else -> UdiChoice.Multiple(udis.sortedWith(byName))
        }
    }

    private val byName = compareBy<Udi> { TextNormalizer.normalize(it.name) }
        .thenBy { it.name }
        .thenBy { it.code }

    private fun district(group: List<UdiRow>): String? = group
        .mapNotNull { it.nomQuartier?.trim()?.takeIf(String::isNotEmpty) }
        .filter { TextNormalizer.normalize(it) != WHOLE_COMMUNE }
        .distinct()
        .joinToString(", ")
        .ifEmpty { null }
}
