package io.github.mgdx.potable.ui.sheet

import java.time.Clock
import java.time.Instant
import java.time.Period

/** How long ago a sample was taken, in the largest whole unit that fits. */
sealed interface RelativeAge {
    /** Taken today, or dated in the future (clock skew): no count to show. */
    data object Today : RelativeAge

    data class Days(val count: Int) : RelativeAge

    data class Weeks(val count: Int) : RelativeAge

    data class Months(val count: Int) : RelativeAge

    data class Years(val count: Int) : RelativeAge

    companion object {
        /**
         * Calendar age of [date] at the [clock]'s current instant: from the
         * date as published ([publishedDate]) to today in France
         * ([FRANCE_ZONE]). Under a week in days, under a month in weeks,
         * under a year in months, then in years.
         */
        fun of(date: Instant, clock: Clock): RelativeAge {
            val sampled = publishedDate(date)
            val today = clock.instant().atZone(FRANCE_ZONE).toLocalDate()
            if (!sampled.isBefore(today)) return Today
            val period = Period.between(sampled, today)
            return when {
                period.years > 0 -> Years(period.years)
                period.months > 0 -> Months(period.months)
                period.days >= DAYS_PER_WEEK -> Weeks(period.days / DAYS_PER_WEEK)
                else -> Days(period.days)
            }
        }

        private const val DAYS_PER_WEEK = 7
    }
}
