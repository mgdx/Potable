package io.github.mgdx.potable.data

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.CoroutineContext

/** [Dispatchers.Default] that counts the blocks sent to it. */
class RecordingDispatcher : CoroutineDispatcher() {
    private val count = AtomicInteger()

    val dispatches: Int get() = count.get()

    override fun dispatch(context: CoroutineContext, block: Runnable) {
        count.incrementAndGet()
        Dispatchers.Default.dispatch(context, block)
    }
}
