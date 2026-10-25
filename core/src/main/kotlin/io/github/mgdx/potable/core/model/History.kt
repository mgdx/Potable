package io.github.mgdx.potable.core.model

import java.time.Instant

/**
 * A regulatory bound as published in `limite_qualite_parametre` or
 * `reference_qualite_parametre` ("<=50 mg/L", ">=6,5 et <=9 unité pH"),
 * parsed only to be drawn on a chart (SPEC §5 F5). Either bound may be
 * missing. The application never compares a value with it (SPEC §14).
 */
data class Threshold(val lower: Double?, val upper: Double?, val unit: String?)

/**
 * One measure of a parameter. [value] is `resultat_numerique`; [text] is
 * `resultat_alphanumerique` as published. [censored] is true when the
 * published text is a detection or quantification bound ("<0,01", ">80").
 */
data class SeriesPoint(
    val date: Instant,
    val sampleCode: String,
    val value: Double?,
    val text: String?,
    val censored: Boolean,
)

/** The history of one parameter for one unit, oldest point first. */
data class ParameterSeries(
    val parameterCode: String,
    val label: String,
    val unit: String?,
    val limitText: String?,
    val referenceText: String?,
    val limit: Threshold?,
    val reference: Threshold?,
    val points: List<SeriesPoint>,
)

/** A sample without its results, for the timeline of conclusions (SPEC §5 F5). */
data class SampleSummary(
    val code: String,
    val date: Instant,
    val conclusion: String?,
    val bacteriologicalLimits: Compliance,
    val physicochemicalLimits: Compliance,
    val bacteriologicalReferences: Compliance,
    val physicochemicalReferences: Compliance,
)
