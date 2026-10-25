package io.github.mgdx.potable.ui.sheet

import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.DecimalStyle
import java.time.format.FormatStyle
import java.util.Locale

/** Where the data are published: "today" is the current date in France. */
val FRANCE_ZONE: ZoneId = ZoneId.of("Europe/Paris")

/**
 * The calendar date of a sample, as Hub'Eau publishes it.
 *
 * The times of `date_prelevement` are most probably French legal time,
 * wrongly suffixed with `Z` (`docs/hubeau-api.md`). Reading them in Paris
 * would add one or two hours and move a late sample to the next day; they are
 * therefore read in UTC, which gives back the literal published date:
 * "2026-08-26T23:30:00Z" is the 26th (`docs/decisions.md`).
 */
fun publishedDate(date: Instant): LocalDate = date.atOffset(ZoneOffset.UTC).toLocalDate()

/**
 * This formatter with the digits of [locale] (non-Latin digits in some RTL scripts,
 * Latin ones in French...), like `NumberFormat` and the `%d` of the strings.
 * `java.time` otherwise writes Latin digits in every language, which SPEC
 * §11.2 rules out: digits follow the locale, they are never forced.
 */
fun DateTimeFormatter.withDigitsOf(locale: Locale): DateTimeFormatter =
    withLocale(locale).withDecimalStyle(DecimalStyle.of(locale))

/** The localized date format of [style] in [locale], digits included. */
fun localizedDateFormatter(style: FormatStyle, locale: Locale): DateTimeFormatter =
    DateTimeFormatter.ofLocalizedDate(style).withDigitsOf(locale)

/** [publishedDate] in the long date format of [locale]. */
fun formatSampleDate(date: Instant, locale: Locale): String =
    localizedDateFormatter(FormatStyle.LONG, locale).format(publishedDate(date))

/**
 * The published time of each of several samples taken the same day, to tell
 * them apart: the time of [dates] read in UTC, like [publishedDate], when
 * every one has its own time and none is midnight (a `date_prelevement`
 * without time reads as midnight); otherwise null for every one, and the
 * screen numbers them instead.
 */
fun distinctSampleTimes(dates: List<Instant>): List<LocalTime?> {
    val times = dates.map { it.atOffset(ZoneOffset.UTC).toLocalTime() }
    val distinct = times.toSet().size == times.size && LocalTime.MIDNIGHT !in times
    return if (distinct) times else times.map { null }
}

/** A sample's published time in the short time format of [locale], digits included. */
fun formatSampleTime(time: LocalTime, locale: Locale): String =
    DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withDigitsOf(locale).format(time)

/** When the cache was last filled, as a medium date of [locale] in [zone]. */
fun formatFetchedDate(fetchedAt: Instant, locale: Locale, zone: ZoneId): String =
    localizedDateFormatter(FormatStyle.MEDIUM, locale).format(fetchedAt.atZone(zone).toLocalDate())
