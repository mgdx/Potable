package io.github.mgdx.potable.core

import java.text.Normalizer
import java.util.Locale

object TextNormalizer {
    private val marks = Regex("\\p{M}+")
    private val separators = Regex("[-'’.]")
    private val spaces = Regex("\\s+")

    /** Abbreviations expanded when they stand as a whole word. */
    private val abbreviations = mapOf("st" to "saint", "ste" to "sainte")

    /**
     * Lowercase, no accents (œ → oe, æ → ae), "-", "'", "’" and "." turned
     * into spaces, spaces collapsed and trimmed. The isolated words "st" and
     * "ste" become "saint" and "sainte", so that both spellings match.
     */
    fun normalize(text: String): String = words(text).joinToString(" ") { expand(it) }

    /**
     * [normalize], except that the last word is left as typed when it is an
     * abbreviation ("st", "ste"); null when it is not one. While the user
     * types, a last word "st" may be the start of "Strasbourg" as well as an
     * abbreviation of "saint": a search tries both forms.
     */
    internal fun normalizeKeepingLastAbbreviation(text: String): String? {
        val words = words(text)
        val last = words.lastOrNull()?.takeIf { it in abbreviations } ?: return null
        return (words.dropLast(1).map { expand(it) } + last).joinToString(" ")
    }

    private fun expand(word: String) = abbreviations[word] ?: word

    private fun words(text: String): List<String> {
        val lower = text.lowercase(Locale.ROOT)
            .replace("œ", "oe")
            .replace("æ", "ae")
        val unaccented = marks.replace(Normalizer.normalize(lower, Normalizer.Form.NFD), "")
        val collapsed = spaces.replace(separators.replace(unaccented, " "), " ").trim()
        return if (collapsed.isEmpty()) emptyList() else collapsed.split(' ')
    }
}
