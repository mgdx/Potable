package io.github.mgdx.potable.core

import io.github.mgdx.potable.core.model.SampleSummary
import java.time.Instant

object NewAnalysis {
    /**
     * The samples of [candidates] (the headers Hub'Eau publishes for a
     * followed unit over the alert window) not yet shown to the user, newest
     * first (then by code), each once.
     *
     * - No [lastSeen] date (first check of a followed unit): none. The first
     *   pass only records a baseline; notifying then would announce as "new"
     *   analyses that may be months old.
     * - No [seenCodes] (a unit followed before the codes were kept, 0.3.1 and
     *   earlier): those taken strictly after [lastSeen], the former rule, so
     *   that an update does not announce the whole window.
     * - Otherwise, those whose `code_prelevement` is not in [seenCodes], even
     *   empty: two samples of the same day, a sample published late with an
     *   earlier date, several published between two checks are all found.
     */
    fun newSamples(
        lastSeen: Instant?,
        seenCodes: Set<String>?,
        candidates: List<SampleSummary>,
    ): List<SampleSummary> {
        if (lastSeen == null) return emptyList()
        val fresh =
            if (seenCodes == null) {
                candidates.filter { it.date > lastSeen }
            } else {
                candidates.filter { it.code !in seenCodes }
            }
        return fresh
            .distinctBy { it.code }
            .sortedWith(compareByDescending<SampleSummary> { it.date }.thenBy { it.code })
    }
}
