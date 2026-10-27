package io.github.mgdx.potable.data.follow

import java.text.Collator
import java.util.Locale

/**
 * The order of the follows: by commune name, then by unit name, as a French
 * dictionary sorts them (accents and case only break ties, "Évry" comes
 * between "Eu" and "Ez"), then by id so that the order is total.
 *
 * The collation is French whatever the language of the device: the names
 * are those Hub'Eau publishes, French place names, and the list reads the
 * same in English and in French.
 */
internal object FollowOrder {
    /** A new comparator each time: a [Collator] is not safe to share between threads. */
    fun comparator(): Comparator<Follow> {
        val collator = Collator.getInstance(Locale.FRENCH).apply { strength = Collator.TERTIARY }
        return compareBy<Follow, String>(collator) { it.communeName }
            .thenBy(collator) { it.udiName }
            .thenBy { it.id }
    }
}
