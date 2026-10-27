package io.github.mgdx.potable.ui.follows

import io.github.mgdx.potable.core.TextNormalizer
import io.github.mgdx.potable.data.follow.Follow

/**
 * The follows whose commune or unit name contains [query], ignoring case,
 * accents and punctuation ([TextNormalizer]), or one of whose postal codes
 * starts with it ("450" finds Orléans, 45000), in their order. [postalCodes]
 * maps a commune code to its postal codes; a commune missing from it is found
 * by name only. A blank query keeps them all.
 */
fun filterFollows(
    follows: List<Follow>,
    query: String,
    postalCodes: Map<String, List<String>> = emptyMap(),
): List<Follow> {
    val needle = TextNormalizer.normalize(query)
    if (needle.isEmpty()) return follows
    return follows.filter { follow ->
        needle in TextNormalizer.normalize(follow.communeName) ||
            needle in TextNormalizer.normalize(follow.udiName) ||
            postalCodes[follow.communeCode].orEmpty().any { it.startsWith(needle) }
    }
}
