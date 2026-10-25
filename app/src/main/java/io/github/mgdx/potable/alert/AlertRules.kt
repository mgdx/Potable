package io.github.mgdx.potable.alert

import io.github.mgdx.potable.data.alert.AlertEvent

/** Which alerts the user switched on (SPEC §5 F7); both are off by default. */
data class AlertSwitches(val newAnalysis: Boolean, val unfavourable: Boolean) {
    val anyEnabled: Boolean get() = newAnalysis || unfavourable
}

/**
 * The events to notify: every one when new analyses are notified, otherwise
 * only the unfavourable ones when those are, otherwise none.
 */
fun notifiable(events: List<AlertEvent>, switches: AlertSwitches): List<AlertEvent> = when {
    switches.newAnalysis -> events
    switches.unfavourable -> events.filter { it.unfavourable }
    else -> emptyList()
}

/** The two notification channels; an unfavourable conclusion has its own. */
enum class AlertChannel(val id: String) {
    NEW_ANALYSIS("new_analysis"),
    UNFAVOURABLE("unfavourable"),
    ;

    companion object {
        fun of(event: AlertEvent): AlertChannel =
            if (event.unfavourable) UNFAVOURABLE else NEW_ANALYSIS
    }
}

/** Longest conclusion shown in a notification, in characters, ellipsis included. */
const val MAX_CONCLUSION_LENGTH = 240

/**
 * The published conclusion of an unfavourable sample, as the text of its
 * notification: unchanged when short enough, otherwise cut at the last space
 * before [max] characters and ended with an ellipsis. Null when nothing was
 * published, so that the caller falls back on the date of the sample.
 */
fun conclusionText(conclusion: String?, max: Int = MAX_CONCLUSION_LENGTH): String? {
    val text = conclusion?.trim()?.replace(WHITESPACE, " ")
    if (text.isNullOrEmpty()) return null
    if (text.length <= max) return text
    val room = text.substring(0, max - ELLIPSIS.length)
    val space = room.lastIndexOf(' ')
    val cut = if (space > max / 2) room.substring(0, space) else room
    return cut.trimEnd(' ', ',', ';', ':', '.', '-', '—') + ELLIPSIS
}

private val WHITESPACE = Regex("\\s+")
private const val ELLIPSIS = "…"
