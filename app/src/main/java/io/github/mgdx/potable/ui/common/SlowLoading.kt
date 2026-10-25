package io.github.mgdx.potable.ui.common

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.transformLatest
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * How long a load runs before the screen says that Hub'Eau is slow to
 * answer, rather than leaving a silent indicator for up to a minute.
 */
val SLOW_LOAD_THRESHOLD: Duration = 10.seconds

/**
 * True once this state has been a loading state ([isLoading]) for
 * [threshold] without interruption; false again as soon as it is not.
 *
 * The delay runs on [scope]'s dispatcher, so tests drive it in virtual time.
 */
@OptIn(ExperimentalCoroutinesApi::class)
fun <T> StateFlow<T>.slowLoading(
    scope: CoroutineScope,
    threshold: Duration,
    isLoading: (T) -> Boolean,
): StateFlow<Boolean> = map(isLoading)
    .distinctUntilChanged()
    .transformLatest { loading ->
        emit(false)
        if (loading) {
            delay(threshold)
            emit(true)
        }
    }
    .stateIn(scope, SharingStarted.Eagerly, initialValue = false)
