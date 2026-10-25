package io.github.mgdx.potable.core

import java.time.Instant

object NewAnalysis {
    /**
     * Whether the [latest] sample date seen on Hub'Eau is newer than the one
     * already shown to the user, [lastSeen].
     *
     * - No [latest] date (no sample at all): nothing new.
     * - No [lastSeen] date (first check of a followed commune): **not** new.
     *   The first pass only records a baseline; notifying then would announce
     *   as "new" an analysis that may be months old.
     * - Otherwise, new only if strictly more recent.
     */
    fun isNew(lastSeen: Instant?, latest: Instant?): Boolean = when {
        latest == null -> false
        lastSeen == null -> false
        else -> latest > lastSeen
    }
}
