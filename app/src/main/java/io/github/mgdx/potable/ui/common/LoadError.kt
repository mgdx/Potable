package io.github.mgdx.potable.ui.common

import io.github.mgdx.potable.data.DataError

/** Why a screen could not load its data, as the user is told about it. */
enum class LoadError {
    /** Hub'Eau could not be reached: no connection, connect timeout, DNS. */
    NETWORK,

    /** Hub'Eau was reached but took longer than the read timeout to answer. */
    TOO_SLOW,

    /** Hub'Eau answered with an HTTP error. */
    UNAVAILABLE,

    /** Hub'Eau answered something that could not be read. */
    UNEXPECTED_RESPONSE,

    /** Anything else went wrong: a bug, caught so that the screen does not crash. */
    UNEXPECTED,
    ;

    companion object {
        fun from(error: DataError): LoadError = when (error) {
            is DataError.Network -> NETWORK
            is DataError.Timeout -> TOO_SLOW
            is DataError.Http -> UNAVAILABLE
            is DataError.Format -> UNEXPECTED_RESPONSE
        }
    }
}
