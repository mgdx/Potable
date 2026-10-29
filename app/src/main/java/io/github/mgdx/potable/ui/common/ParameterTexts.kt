package io.github.mgdx.potable.ui.common

import io.github.mgdx.potable.R
import io.github.mgdx.potable.core.FeaturedParameter
import io.github.mgdx.potable.core.WatchedSubstance

/*
 * The translated names and explanations of the editorial tables (SPEC §4.3),
 * shared by the sheet and the list of every result of a sample.
 */

internal fun FeaturedParameter.nameRes(): Int = when (this) {
    FeaturedParameter.NITRATES -> R.string.featured_nitrates
    FeaturedParameter.HARDNESS -> R.string.featured_hardness
    FeaturedParameter.PH -> R.string.featured_ph
    FeaturedParameter.FREE_CHLORINE -> R.string.featured_free_chlorine
}

internal fun FeaturedParameter.explanationRes(): Int = when (this) {
    FeaturedParameter.NITRATES -> R.string.featured_nitrates_explanation
    FeaturedParameter.HARDNESS -> R.string.featured_hardness_explanation
    FeaturedParameter.PH -> R.string.featured_ph_explanation
    FeaturedParameter.FREE_CHLORINE -> R.string.featured_free_chlorine_explanation
}

internal fun WatchedSubstance.nameRes(): Int = when (this) {
    WatchedSubstance.PFAS -> R.string.watched_pfas
    WatchedSubstance.TOTAL_PESTICIDES -> R.string.watched_pesticides
    WatchedSubstance.TFA -> R.string.watched_tfa
    WatchedSubstance.VINYL_CHLORIDE -> R.string.watched_vinyl_chloride
    WatchedSubstance.LEAD -> R.string.watched_lead
    WatchedSubstance.ARSENIC -> R.string.watched_arsenic
    WatchedSubstance.PERCHLORATE -> R.string.watched_perchlorate
    WatchedSubstance.CHLORDECONE -> R.string.watched_chlordecone
}

internal fun WatchedSubstance.explanationRes(): Int = when (this) {
    WatchedSubstance.PFAS -> R.string.watched_pfas_explanation
    WatchedSubstance.TOTAL_PESTICIDES -> R.string.watched_pesticides_explanation
    WatchedSubstance.TFA -> R.string.watched_tfa_explanation
    WatchedSubstance.VINYL_CHLORIDE -> R.string.watched_vinyl_chloride_explanation
    WatchedSubstance.LEAD -> R.string.watched_lead_explanation
    WatchedSubstance.ARSENIC -> R.string.watched_arsenic_explanation
    WatchedSubstance.PERCHLORATE -> R.string.watched_perchlorate_explanation
    WatchedSubstance.CHLORDECONE -> R.string.watched_chlordecone_explanation
}

/**
 * The explanation shown on the sheet for the parameter published under
 * [parameterCode] in the commune of INSEE code [communeCode]: that of a
 * featured parameter, or of a watched substance where the sheet reads it
 * ([WatchedSubstance.isReadIn]: chlordecone overseas only). Null for every
 * other parameter, which has no editorial text.
 */
internal fun parameterExplanationRes(parameterCode: String, communeCode: String): Int? =
    FeaturedParameter.of(parameterCode)?.explanationRes()
        ?: WatchedSubstance.of(parameterCode)
            ?.takeIf { it.isReadIn(communeCode) }
            ?.explanationRes()
