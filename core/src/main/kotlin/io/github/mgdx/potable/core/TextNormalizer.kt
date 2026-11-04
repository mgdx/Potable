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

    private fun words(text: String): List<String> = fastWords(text) ?: slowWords(text)

    /** The reference definition of the words of [text]; [fastWords] gives the same, faster. */
    internal fun slowWords(text: String): List<String> {
        val lower = text.lowercase(Locale.ROOT)
            .replace("œ", "oe")
            .replace("æ", "ae")
        val unaccented = marks.replace(Normalizer.normalize(lower, Normalizer.Form.NFD), "")
        val collapsed = spaces.replace(separators.replace(unaccented, " "), " ").trim()
        return if (collapsed.isEmpty()) emptyList() else collapsed.split(' ')
    }

    /**
     * [slowWords] in one pass over the characters, without regular
     * expressions: the referential normalizes its 37,000 names at startup.
     * Characters from U+0080 to U+036F (Latin-1 Supplement, Latin Extended-A
     * and B, IPA, spacing modifiers, combining marks) are folded one by one
     * with [slowWords]'s own steps (lowercase, œ/æ, decomposition, marks
     * dropped), which gives the same result as folding the whole text: no
     * character of that range changes case according to its neighbours, and
     * decomposition only reorders marks, which are dropped. Null, to leave
     * the text to [slowWords], when it has another character (Greek, emoji…)
     * or one that [slowWords] keeps inside a word but trims at either end
     * (control characters, no-break space). TextNormalizerTest checks that
     * both agree on every embedded name and on random text.
     */
    internal fun fastWords(text: String): List<String>? {
        val words = ArrayList<String>(4)
        val word = StringBuilder(text.length)
        fun endWord() {
            if (word.isNotEmpty()) {
                words += word.toString()
                word.setLength(0)
            }
        }
        for (c in text) {
            when {
                c in 'a'..'z' || c in '0'..'9' -> word.append(c)
                c in 'A'..'Z' -> word.append(c + ('a' - 'A'))
                c == ' ' || c == '-' || c == '\'' || c == '.' || c == '’' -> endWord()
                c == '\t' || c == '\n' || c == '\u000B' || c == '\u000C' || c == '\r' -> endWord()
                c < ' ' || c == NO_BREAK_SPACE -> return null
                c.code < ASCII_END -> word.append(c)
                c.code < FOLDED_END -> word.append(folded[c.code - ASCII_END])
                else -> return null
            }
        }
        endWord()
        return words
    }

    private const val ASCII_END = 0x80
    private const val FOLDED_END = 0x370
    private const val NO_BREAK_SPACE = '\u00A0'

    /** The fold of each character from [ASCII_END] until [FOLDED_END], as [slowWords] does it. */
    private val folded: Array<String> = Array(FOLDED_END - ASCII_END) { i ->
        val c = (ASCII_END + i).toChar().toString()
        val lower = c.lowercase(Locale.ROOT).replace("œ", "oe").replace("æ", "ae")
        val result = marks.replace(Normalizer.normalize(lower, Normalizer.Form.NFD), "")
        // None folds to a separator or to a character trimmed by slowWords.
        check(c == NO_BREAK_SPACE.toString() || result.none { it.isWhitespace() || it in "-'’." })
        result
    }
}
