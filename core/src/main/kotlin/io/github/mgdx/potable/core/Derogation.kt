package io.github.mgdx.potable.core

import io.github.mgdx.potable.core.model.Compliance
import io.github.mgdx.potable.core.model.Sample

/**
 * Whether a sample has to do with a derogation: a temporary authorisation,
 * granted by the prefect, to distribute water above a quality limit. Only
 * says that the word is there, so that the app can explain it; never a
 * verdict (SPEC §14).
 */
object Derogation {
    /**
     * The words looked for, once normalised ([TextNormalizer]): "dérogation",
     * "dérogatoire" (« valeurs dérogatoires », Seine-et-Marne), "déroger"
     * (« autorisant la PRPDE à déroger temporairement »), with an optional
     * plural "s". Hub'Eau texts carry stray spaces inside words (« l a
     * situation », « évoluti on »): one is allowed between any two letters.
     * A whole word only: "Rogerville" after "de" is not "déroger".
     */
    private val words = listOf("derogation", "derogatoire", "deroger")

    private val pattern = Regex(
        words.joinToString(
            separator = "|",
            prefix = "(?<![\\p{L}\\p{N}])(?:",
            postfix = ")(?: ?s)?(?![\\p{L}\\p{N}])",
        ) { word -> word.toList().joinToString(" ?") },
    )

    /** True when [sample] is published compliant under a derogation or its conclusion mentions one. */
    fun concerns(sample: Sample): Boolean =
        underDerogation(sample) || mentionedIn(sample.conclusion)

    /** True when one of the four compliance codes of [sample] is "D". */
    fun underDerogation(sample: Sample): Boolean = listOf(
        sample.bacteriologicalLimits,
        sample.physicochemicalLimits,
        sample.bacteriologicalReferences,
        sample.physicochemicalReferences,
    ).any { it == Compliance.COMPLIANT_UNDER_DEROGATION }

    /** True when [conclusion] mentions a derogation, whatever its case, accents or stray spaces. */
    fun mentionedIn(conclusion: String?): Boolean {
        if (conclusion.isNullOrBlank()) return false
        return pattern.containsMatchIn(TextNormalizer.normalize(conclusion))
    }
}
