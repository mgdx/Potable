package io.github.mgdx.potable.ui.udi

import io.github.mgdx.potable.core.TextNormalizer
import io.github.mgdx.potable.core.model.Udi

/**
 * Above this many units, the choice offers a filter field (Marseille lists
 * about 176): up to it, the list is read at a glance.
 */
const val UDI_FILTER_THRESHOLD = 8

/** Whether the choice among [count] units offers the filter field. */
fun showsUdiFilter(count: Int): Boolean = count > UDI_FILTER_THRESHOLD

/**
 * The units whose name or district contains [query], ignoring case and
 * accents ([TextNormalizer]); all of them, in their order, when the query
 * is blank.
 */
fun filterUdis(udis: List<Udi>, query: String): List<Udi> {
    val needle = TextNormalizer.normalize(query)
    if (needle.isEmpty()) return udis
    return udis.filter { udi ->
        TextNormalizer.normalize(udi.name).contains(needle) ||
            udi.district?.let { TextNormalizer.normalize(it).contains(needle) } == true
    }
}
