package io.github.mgdx.potable.data.alert

import io.github.mgdx.potable.core.model.SampleSummary
import io.github.mgdx.potable.data.follow.Follow

/**
 * Samples published since the previous check of a followed unit (SPEC §5
 * F7), one event per unit and per check. [sample] is the newest of the new
 * samples with an unfavourable conclusion if there is one, otherwise the
 * newest new sample; [unfavourable] is true when at least one new sample
 * is unfavourable. It restitutes the published compliance; see
 * `core.Unfavourable`.
 */
data class AlertEvent(val follow: Follow, val sample: SampleSummary, val unfavourable: Boolean)

/** Run by the app's periodic worker. */
interface AlertChecker {
    /**
     * For every followed unit, asks Hub'Eau for the headers of its samples
     * of the alert window and for its latest sample (light requests),
     * compares their codes with those already seen (`NewAnalysis.newSamples`),
     * records them all as seen, and returns an event for each unit that has
     * a new sample. The first check of a unit only records a baseline and
     * returns nothing. When a new sample is found, the unit's cache is
     * refreshed too (best effort). A unit whose request fails is skipped and
     * checked again next time; the call itself never throws, except for
     * cancellation.
     */
    suspend fun check(): List<AlertEvent>

    /**
     * Records the current samples of the window of every followed unit as
     * seen, without notifying: called when alerts are switched on, so that the
     * first notification is about a sample published afterwards.
     */
    suspend fun setBaseline()

    /**
     * Records the latest samples of [follow], just followed, as seen, unless
     * the unit already has a last seen date: so that a sample published
     * before its first periodic check is announced, not taken as its
     * baseline. Reads the cached latest samples when there are some (the
     * codes are then complete from their day on), otherwise asks Hub'Eau for
     * the window (light requests). Best effort: a failure leaves the unit
     * without a baseline, which its first check then records; never throws,
     * except for cancellation. Called whether the alerts are on or not (when
     * they are switched on, [setBaseline] records every unit again).
     */
    suspend fun setBaseline(follow: Follow)
}
