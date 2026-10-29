package io.github.mgdx.potable.alert

import io.github.mgdx.potable.core.model.Compliance
import io.github.mgdx.potable.core.model.SampleSummary
import io.github.mgdx.potable.data.alert.AlertChecker
import io.github.mgdx.potable.data.alert.AlertEvent
import io.github.mgdx.potable.data.follow.Follow
import io.github.mgdx.potable.data.preferences.AppPreferences
import io.github.mgdx.potable.ui.follow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import java.time.Instant

fun event(id: Long, unfavourable: Boolean, conclusion: String? = "Eau conforme.") = AlertEvent(
    follow = follow(id, udiCode = "04500012$id"),
    sample = SampleSummary(
        code = "S$id",
        date = Instant.parse("2026-10-0${id}T08:00:00Z"),
        conclusion = conclusion,
        bacteriologicalLimits = Compliance.NON_COMPLIANT.takeIf { unfavourable }
            ?: Compliance.COMPLIANT,
        physicochemicalLimits = Compliance.COMPLIANT,
        bacteriologicalReferences = Compliance.COMPLIANT,
        physicochemicalReferences = Compliance.COMPLIANT,
    ),
    unfavourable = unfavourable,
)

class AlertPreferences(newAnalysis: Boolean = false, unfavourable: Boolean = false) :
    AppPreferences {
    override val disclaimerAccepted: Flow<Boolean> = MutableStateFlow(true)

    override suspend fun acceptDisclaimer() = Unit

    override val alertNewAnalysis = MutableStateFlow(newAnalysis)
    override val alertUnfavourable = MutableStateFlow(unfavourable)
    override val alertPeriodWeeks = MutableStateFlow(AppPreferences.DEFAULT_ALERT_WEEKS)
}

/** Returns [events] at each check; counts the calls. */
class FakeChecker(var events: List<AlertEvent> = emptyList()) : AlertChecker {
    var checks = 0
    var baselines = 0

    /** Thrown by [setBaseline] when set. */
    var baselineError: Exception? = null

    override suspend fun check(): List<AlertEvent> {
        checks++
        return events
    }

    override suspend fun setBaseline() {
        baselines++
        baselineError?.let { throw it }
    }

    /** The units given to [setBaseline] one by one. */
    val followBaselines = mutableListOf<Follow>()

    override suspend fun setBaseline(follow: Follow) {
        followBaselines += follow
    }
}
