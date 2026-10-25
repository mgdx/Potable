package io.github.mgdx.potable.core

import io.github.mgdx.potable.core.model.Compliance
import io.github.mgdx.potable.core.model.SampleSummary

/**
 * Whether a sample's **published** outcome is unfavourable (SPEC §5 F7:
 * non-compliance, consumption advised against or forbidden). It restitutes
 * the ARS outcome and adds no judgement of its own (SPEC §14): no score, no
 * threshold applied to a measure, only what the ARS published.
 *
 * A sample is unfavourable when:
 * - one of its four published compliances is [Compliance.NON_COMPLIANT]
 *   (limits or references, bacteriological or physico-chemical); or
 * - its published conclusion states a non-compliance ("non conforme",
 *   "non-conformité", "ne satisfaisant pas aux références de qualité"), a
 *   consumption advised against ("déconseillée"), forbidden or restricted
 *   ("interdiction", "restriction d'usage", "ne pas consommer"), even for
 *   part of the population only (infants, pregnant women).
 *
 * A non-compliance with the **references** alone ("conforme aux limites de
 * qualité et non conforme aux références de qualité") counts: it is a
 * non-compliance the ARS published, and SPEC §5 F7 does not tell limits from
 * references. A "D" compliance (compliant under derogation) alone does not,
 * but its conclusion usually states "non conforme aux limites", which does.
 *
 * The conclusion is read case- and accent-insensitively, and also with its
 * spaces removed, since Hub'Eau texts often carry stray spaces inside words
 * ("déc onseillée", "non conformi te"). A mention does not count when the
 * text dismisses it in the same clause: negated ("ne nécessite pas de
 * restriction", "sans restriction", "aucune restriction"), lifted ("levée de
 * l'interdiction", "l'interdiction a été levée"), about an earlier sample
 * ("fait suite à une non-conformité", "après une non-conformité", "ayant
 * entraîné une demande d'interdiction"), or conditional within its sentence
 * ("au-delà de cette valeur, il est recommandé de ne pas consommer").
 * Real conclusions behind these rules: `docs/hubeau-api.md`, "Conclusions
 * défavorables"; the choice is recorded in `docs/decisions.md`.
 */
object Unfavourable {
    fun isUnfavourable(sample: SampleSummary): Boolean = listOf(
        sample.bacteriologicalLimits,
        sample.physicochemicalLimits,
        sample.bacteriologicalReferences,
        sample.physicochemicalReferences,
    ).any { it == Compliance.NON_COMPLIANT } ||
        conclusionIsUnfavourable(sample.conclusion)

    /** Whether the published [conclusion] states an unfavourable outcome (see [Unfavourable]). */
    internal fun conclusionIsUnfavourable(conclusion: String?): Boolean {
        if (conclusion.isNullOrBlank()) return false
        return sentenceBreaks.split(conclusion).any { sentence ->
            var before = ""
            sentence.split(',').any { clause ->
                val text = TextNormalizer.normalize(clause)
                val found = statesOutcome(text, before)
                before += " $text"
                found
            }
        }
    }

    /** Sentences end on these; "." is lost by [TextNormalizer.normalize], so split first. */
    private val sentenceBreaks = Regex("[.;:!?]")

    /** Matched on the normalised clause with its spaces removed. */
    private val outcomes =
        Regex(
            "deconseill|interdi|restriction|nonconform|impropre|nonpotable" +
                "|nepas(?:consomm|boire|utilis)|nedoi(?:t|vent)pasetre(?:consomm|bu|utilis)" +
                "|(?:nesatisf[a-z]{0,6}pas|nonsatisfaisante?s?)a(?:ux)?(?:la|les)?" +
                "(?:limite|reference|exigence)",
        )

    /** Shortly before a mention, in its clause: the mention is negated, lifted or past. */
    private val dismissedBefore =
        Regex(
            "\\b(?:pas|sans|aucune?|lever|levee|leve|suite (?:a|au|aux)" +
                "|apres (?:une|des|la|les)|fin a|ayant entraine[a-z]*)\\b",
        )

    /** After a mention, in its clause: it has been lifted. */
    private val liftedAfter = Regex("\\b(?:levee|leve)\\b")

    /** Before a mention, in its sentence: the mention is a hypothesis. */
    private val conditional = Regex("\\b(?:au dela|en cas de?|si)\\b")

    /** How far before a mention [dismissedBefore] is looked for, in characters. */
    private const val DISMISSAL_WINDOW = 50

    /**
     * Whether the normalised [clause] states an outcome that
     * [sentenceBefore], the normalised start of its sentence, does not
     * make conditional.
     */
    private fun statesOutcome(clause: String, sentenceBefore: String): Boolean {
        val compact = StringBuilder(clause.length)
        val position = IntArray(clause.length)
        clause.forEachIndexed { index, char ->
            if (char != ' ') {
                position[compact.length] = index
                compact.append(char)
            }
        }
        return outcomes.findAll(compact).any { match ->
            val start = position[match.range.first]
            val end = position[match.range.last] + 1
            val before = clause.substring(0, start)
            !dismissedBefore.containsMatchIn(before.takeLast(DISMISSAL_WINDOW)) &&
                !liftedAfter.containsMatchIn(clause.substring(end)) &&
                !conditional.containsMatchIn("$sentenceBefore $before")
        }
    }
}
