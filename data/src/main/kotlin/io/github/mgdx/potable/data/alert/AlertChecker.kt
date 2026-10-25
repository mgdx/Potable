package io.github.mgdx.potable.data.alert

import io.github.mgdx.potable.core.model.SampleSummary
import io.github.mgdx.potable.data.follow.Follow

/**
 * A sample published after the last one seen for a followed unit
 * (SPEC §5 F7). [unfavourable] restitutes the published compliance; see
 * `core.Unfavourable`.
 */
data class AlertEvent(val follow: Follow, val sample: SampleSummary, val unfavourable: Boolean)

/** Run by the app's periodic worker. */
interface AlertChecker {
    /**
     * For every followed unit, asks Hub'Eau for its latest sample (a light
     * request), compares its date with the last one seen, records the new
     * one, and returns an event for each unit that has a newer sample.
     * The first check of a unit only records a baseline and returns nothing
     * (`NewAnalysis.isNew`). When a newer sample is found, the unit's cache
     * is refreshed too (best effort). A unit whose request fails is skipped
     * and checked again next time; the call itself never throws, except
     * for cancellation.
     */
    suspend fun check(): List<AlertEvent>

    /**
     * Records the current latest sample of every followed unit as seen,
     * without notifying: called when alerts are switched on, so that the
     * first notification is about a sample published afterwards.
     */
    suspend fun setBaseline()
}
