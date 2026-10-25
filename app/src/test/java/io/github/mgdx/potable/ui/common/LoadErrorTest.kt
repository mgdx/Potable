package io.github.mgdx.potable.ui.common

import io.github.mgdx.potable.data.DataError
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.IOException
import java.net.SocketTimeoutException

class LoadErrorTest {
    @Test
    fun `a slow Hub'Eau is not blamed on the connection`() {
        assertEquals(
            LoadError.TOO_SLOW,
            LoadError.from(DataError.Timeout(SocketTimeoutException())),
        )
        assertEquals(LoadError.NETWORK, LoadError.from(DataError.Network(IOException())))
        assertEquals(LoadError.UNAVAILABLE, LoadError.from(DataError.Http(500)))
        assertEquals(LoadError.UNEXPECTED_RESPONSE, LoadError.from(DataError.Format(Exception())))
    }
}
