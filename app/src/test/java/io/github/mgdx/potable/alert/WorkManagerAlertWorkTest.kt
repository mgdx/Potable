package io.github.mgdx.potable.alert

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Configuration
import androidx.work.NetworkType
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.testing.SynchronousExecutor
import androidx.work.testing.WorkManagerTestInitHelper
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import java.util.concurrent.TimeUnit

/** The request really handed to WorkManager (work-testing's in-memory instance). */
@RunWith(RobolectricTestRunner::class)
class WorkManagerAlertWorkTest {
    private lateinit var context: Context
    private lateinit var work: WorkManagerAlertWork

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
        WorkManagerTestInitHelper.initializeTestWorkManager(
            context,
            Configuration.Builder().setExecutor(SynchronousExecutor()).build(),
        )
        work = WorkManagerAlertWork(context)
    }

    private fun infos(): List<WorkInfo> = WorkManager.getInstance(context)
        .getWorkInfosForUniqueWork(WorkManagerAlertWork.UNIQUE_NAME)
        .get()

    @Test
    fun `the check is periodic, on the chosen period, on any network, with default backoff`() {
        work.schedule(periodWeeks = 2)
        val info = infos().single()
        assertEquals(WorkInfo.State.ENQUEUED, info.state)
        assertEquals(TimeUnit.DAYS.toMillis(14), info.periodicityInfo?.repeatIntervalMillis)
        assertEquals(NetworkType.CONNECTED, info.constraints.requiredNetworkType)
        assertEquals(TimeUnit.DAYS.toMillis(14), info.initialDelayMillis)

        val spec = WorkManagerAlertWork.periodicRequest(2).workSpec
        assertEquals(BackoffPolicy.EXPONENTIAL, spec.backoffPolicy)
        assertEquals(AlertWorker::class.java.name, spec.workerClassName)
        assertEquals(false, spec.expedited)
    }

    @Test
    fun `planning again updates the same request instead of adding one`() {
        work.schedule(periodWeeks = 1)
        val first = infos().single().id
        work.schedule(periodWeeks = 4)
        val info = infos().single()
        assertEquals(first, info.id)
        assertEquals(TimeUnit.DAYS.toMillis(28), info.periodicityInfo?.repeatIntervalMillis)
    }

    @Test
    fun `cancelling removes the plan`() {
        work.schedule(periodWeeks = 1)
        work.cancel()
        assertEquals(WorkInfo.State.CANCELLED, infos().single().state)
    }
}
