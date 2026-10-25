package io.github.mgdx.potable.core

import io.github.mgdx.potable.core.model.SampleSummary

/**
 * The samples of a period whose published outcome is unfavourable
 * ([Unfavourable]): how many, and the most recent of them, whose conclusion
 * is shown as published. Behind the sheet's box on the last 12 months: it
 * counts what the ARS published and adds no judgement of its own (SPEC §14).
 */
data class UnfavourableDigest(val count: Int, val latest: SampleSummary) {
    companion object {
        /**
         * The digest of [samples] (a timeline, in any order), or null when
         * none is unfavourable. A sample listed twice counts once; between
         * samples of the same date, the first by code is the most recent,
         * as in [Samples.group].
         */
        fun of(samples: List<SampleSummary>): UnfavourableDigest? {
            val found = samples.distinctBy { it.code }.filter(Unfavourable::isUnfavourable)
            val latest = found.maxWithOrNull(
                compareBy<SampleSummary> { it.date }.thenByDescending { it.code },
            ) ?: return null
            return UnfavourableDigest(found.size, latest)
        }
    }
}
