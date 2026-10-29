package io.github.mgdx.potable.ui.navigation

import io.github.mgdx.potable.data.follow.Follow
import kotlinx.serialization.Serializable

/*
 * The destinations of the navigation graph (PotableNavHost), as type-safe
 * routes: Navigation encodes and decodes their arguments.
 */

/** The search, start of the graph. */
@Serializable
data object SearchRoute

@Serializable
data object AboutRoute

@Serializable
data object SettingsRoute

/** "My communes" (SPEC §5 F6). */
@Serializable
data object FollowsRoute

/**
 * Resolution, then choice when there are several, of the units of
 * [communeCode] (the code Hub'Eau publishes it under); [choose] always offers
 * the choice (the user asked to change).
 */
@Serializable
data class UdisRoute(
    val communeCode: String,
    val communeName: String = "",
    val choose: Boolean = false,
)

/**
 * The "my water" sheet of a commune's unit; [change] offers to go back to
 * the choice of unit, for a commune that has several. Without [udiName], the
 * unit is named by its code.
 */
@Serializable
data class SheetRoute(
    val communeCode: String,
    val udiCode: String,
    val communeName: String = "",
    val udiName: String? = null,
    val change: Boolean = false,
) {
    companion object {
        fun of(follow: Follow) =
            SheetRoute(follow.communeCode, follow.udiCode, follow.communeName, follow.udiName)
    }
}

/**
 * Every result of the sample the sheet below holds. The sample is not passed
 * nor loaded again: the screen reads the sheet's ViewModel, scoped to the
 * sheet's back stack entry; the code only checks it is the same.
 */
@Serializable
data class SampleRoute(val sampleCode: String)

/** The timeline of the conclusions of a unit's samples (SPEC §5 F5). */
@Serializable
data class TimelineRoute(
    val communeCode: String,
    val udiCode: String,
    val communeName: String = "",
)

/**
 * The history screen of one parameter (SPEC §5 F5) of the unit [udiCode]
 * serving [communeCode] (the code Hub'Eau publishes the commune under).
 *
 * [parameterCodes] are every `code_parametre` the parameter is published
 * under (hardness has two: `FeaturedParameter.codes`, or
 * [io.github.mgdx.potable.ui.history.historyCodes] from a single code); see
 * [of]. [label] is the title until the series is loaded; the API label
 * (`libelle_parametre`) then replaces it. The window opens on the history
 * setting and can be changed there.
 */
@Serializable
data class ParameterHistoryRoute(
    val communeCode: String,
    val udiCode: String,
    val parameterCodes: List<String>,
    val label: String,
) {
    companion object {
        /** The route of [parameterCodes], which must not be empty. */
        fun of(
            communeCode: String,
            udiCode: String,
            parameterCodes: Set<String>,
            label: String,
        ): ParameterHistoryRoute {
            require(parameterCodes.isNotEmpty()) { "no parameter code" }
            return ParameterHistoryRoute(communeCode, udiCode, parameterCodes.toList(), label)
        }
    }
}

/**
 * The full report of any sample of a unit, loaded by its code (SPEC §5 F4),
 * opened from a parameter's history or from the timeline.
 */
@Serializable
data class ReportRoute(val communeCode: String, val udiCode: String, val sampleCode: String)

/**
 * Every result of the sample of the report below. Like [SampleRoute],
 * nothing is loaded again: the screen reads the report's ViewModel, scoped
 * to the report's back stack entry.
 */
@Serializable
data class ReportResultsRoute(val communeCode: String, val udiCode: String, val sampleCode: String)
