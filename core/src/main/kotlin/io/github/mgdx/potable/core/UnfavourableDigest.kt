package io.github.mgdx.potable.core

import io.github.mgdx.potable.core.model.Compliance
import io.github.mgdx.potable.core.model.SampleSummary

/**
 * The samples of a period whose published outcome is unfavourable
 * ([Unfavourable]), counted by what was published, limits and references
 * told apart as on the sheet (SPEC §5 F3), and the most recent of them,
 * whose conclusion is shown as published. Behind the sheet's box on the last
 * 12 months: it restitutes what the ARS published and adds no judgement of
 * its own (SPEC §14).
 *
 * Each sample counts once, in the first category that applies:
 * - [limitsCount]: a quality limit, bacteriological or physico-chemical, is
 *   published non-compliant;
 * - [referencesOnlyCount]: a quality reference is, and no limit;
 * - [conclusionOnlyCount]: no compliance is, but the published conclusion
 *   states a restriction, a consumption advised against or forbidden, or a
 *   non-compliance.
 */
data class UnfavourableDigest(
    val limitsCount: Int,
    val referencesOnlyCount: Int,
    val conclusionOnlyCount: Int,
    val latest: SampleSummary,
) {
    /** Every sample counted. */
    val count: Int get() = limitsCount + referencesOnlyCount + conclusionOnlyCount

    companion object {
        /**
         * The digest of [samples] (a timeline, in any order), or null when
         * none is unfavourable. A sample listed twice counts once; between
         * samples of the same date, the first by code is the most recent,
         * as in [Samples.group].
         */
        fun of(samples: List<SampleSummary>): UnfavourableDigest? {
            var limits = 0
            var references = 0
            var conclusion = 0
            val found = samples.distinctBy { it.code }.filter { sample ->
                when {
                    sample.limitsNonCompliant() -> limits++
                    sample.referencesNonCompliant() -> references++
                    Unfavourable.conclusionIsUnfavourable(sample.conclusion) -> conclusion++
                    else -> return@filter false
                }
                true
            }
            val latest = found.maxWithOrNull(
                compareBy<SampleSummary> { it.date }.thenByDescending { it.code },
            ) ?: return null
            return UnfavourableDigest(limits, references, conclusion, latest)
        }

        private fun SampleSummary.limitsNonCompliant() =
            bacteriologicalLimits == Compliance.NON_COMPLIANT ||
                physicochemicalLimits == Compliance.NON_COMPLIANT

        private fun SampleSummary.referencesNonCompliant() =
            bacteriologicalReferences == Compliance.NON_COMPLIANT ||
                physicochemicalReferences == Compliance.NON_COMPLIANT
    }
}
