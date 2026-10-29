package io.github.mgdx.potable.core

import io.github.mgdx.potable.core.hubeau.UdiRow
import io.github.mgdx.potable.core.model.Udi
import io.github.mgdx.potable.core.model.UdiChoice

object UdiSelection {
    /**
     * Normalized `nom_quartier` values meaning "the whole commune", compared
     * with the whole text (docs/hubeau-api.md, survey of 2026-10-29). A
     * wording that names a part of the commune ("totalité sauf hameau",
     * "commune sauf …", "<delegated commune> / toute la commune") is not one,
     * nor are "commune" and "ville", which some agencies use for the main
     * network beside named hamlets.
     */
    private val wholeCommune = setOf(
        "totalite",
        "en totalite",
        "totalite commune",
        "totalite de la commune",
        "la totalite de la commune",
        "la commune en totalite",
        "commune entiere",
        "toute la commune",
        "ensemble",
        "ensemble commune",
        "ensemble de la commune",
        "l ensemble de la commune",
        "entierement",
        "en entier",
        "tout",
    )

    /**
     * Keeps only the most recent year present in [rows] (an UDI that served
     * the commune in an earlier year but no longer does is dropped, SPEC §15),
     * then one [Udi] per `code_reseau`. The name is the network's name that
     * year; the districts of a network listed several times are joined with
     * ", ", without duplicates; a wording of the whole commune ("totalité",
     * "commune entière"…), an empty district or one without any letter or
     * digit ("-") gives null.
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

    /**
     * A district with neither letter nor digit is a placeholder, not a name:
     * Hub'Eau publishes "-" for most units (and once "_").
     */
    private fun district(group: List<UdiRow>): String? = group
        .mapNotNull { it.nomQuartier?.trim()?.takeIf { name -> name.any(Char::isLetterOrDigit) } }
        .filter { TextNormalizer.normalize(it) !in wholeCommune }
        .distinct()
        .joinToString(", ")
        .ifEmpty { null }
}
