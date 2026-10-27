package io.github.mgdx.potable.ui.follows

import io.github.mgdx.potable.core.TextNormalizer
import io.github.mgdx.potable.data.follow.Follow

/**
 * The follows whose commune or unit name contains [query], ignoring case,
 * accents and punctuation ([TextNormalizer]), in their order. A blank query
 * keeps them all.
 */
fun filterFollows(follows: List<Follow>, query: String): List<Follow> {
    val needle = TextNormalizer.normalize(query)
    if (needle.isEmpty()) return follows
    return follows.filter { follow ->
        needle in TextNormalizer.normalize(follow.communeName) ||
            needle in TextNormalizer.normalize(follow.udiName)
    }
}
