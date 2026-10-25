package io.github.mgdx.potable.data

/** Why a data source could not answer. Messages carry no user data. */
sealed class DataError(message: String, cause: Throwable? = null) : Exception(message, cause) {
    /** Hub'Eau could not be reached: no connection, DNS, refused or timed-out connection. */
    class Network(cause: Throwable) : DataError("network", cause)

    /**
     * The connection to Hub'Eau was established but the answer did not come
     * within the read timeout: Hub'Eau is too slow, not the user's network.
     */
    class Timeout(cause: Throwable) : DataError("timeout", cause)

    class Http(val code: Int) : DataError("http $code")

    class Format(cause: Throwable) : DataError("format", cause)
}
