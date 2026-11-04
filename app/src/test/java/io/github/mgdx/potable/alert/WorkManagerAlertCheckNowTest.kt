package io.github.mgdx.potable.alert

import android.content.Context
import androidx.work.Configuration
import androidx.work.NetworkType
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.testing.SynchronousExecutor
import androidx.work.testing.WorkManagerTestInitHelper
import androidx.work.workDataOf
import io.github.mgdx.potable.alert.WorkManagerAlertCheckNow.Companion.toState
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import java.util.UUID

/** "Check now" as handed to WorkManager (work-testing's in-memory instance). */
@RunWith(RobolectricTestRunner::class)
class WorkManagerAlertCheckNowTest {
    private lateinit var context: Context
    private lateinit var checkNow: WorkManagerAlertCheckNow

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
        WorkManagerTestInitHelper.initializeTestWorkManager(
            context,
            Configuration.Builder().setExecutor(SynchronousExecutor()).build(),
        )
        checkNow = WorkManagerAlertCheckNow(context)
    }

    private fun infos(name: String): List<WorkInfo> =
        WorkManager.getInstance(context).getWorkInfosForUniqueWork(name).get()

    @Test
    fun `one check, by the alert worker, on any network, not expedited`() {
        checkNow.start()
        val info = infos(WorkManagerAlertCheckNow.UNIQUE_NAME).single()
        // No network in the test: it waits.
        assertEquals(WorkInfo.State.ENQUEUED, info.state)
        assertEquals(NetworkType.CONNECTED, info.constraints.requiredNetworkType)
        assertEquals(null, info.periodicityInfo)
        val spec = WorkManagerAlertCheckNow.request().workSpec
        assertEquals(AlertWorker::class.java.name, spec.workerClassName)
        assertEquals(false, spec.expedited)
        assertEquals(CheckNowState.Waiting, runBlocking { checkNow.state.first() })
    }

    @Test
    fun `tapping again while one waits adds nothing`() {
        checkNow.start()
        val first = infos(WorkManagerAlertCheckNow.UNIQUE_NAME).single().id
        checkNow.start()
        assertEquals(first, infos(WorkManagerAlertCheckNow.UNIQUE_NAME).single().id)
    }

    @Test
    fun `the periodic plan is left as it is`() {
        val periodic = WorkManagerAlertWork(context)
        periodic.schedule(periodWeeks = 1)
        val planned = infos(WorkManagerAlertWork.UNIQUE_NAME).single()
        checkNow.start()
        val after = infos(WorkManagerAlertWork.UNIQUE_NAME).single()
        assertEquals(planned.id, after.id)
        assertEquals(WorkInfo.State.ENQUEUED, after.state)
    }

    @Test
    fun `nothing asked reads as idle`() {
        assertEquals(CheckNowState.Idle, runBlocking { checkNow.state.first() })
    }

    @Test
    fun `the outcome of the run is read from the output data`() {
        val id = UUID.randomUUID()
        val done = WorkInfo(
            id,
            WorkInfo.State.SUCCEEDED,
            emptySet(),
            workDataOf(AlertWorker.OUTCOME to AlertRun.Outcome.NOTHING_NEW.name),
        )
        assertEquals(CheckNowState.Finished(id, AlertRun.Outcome.NOTHING_NEW), done.toState())
        val cancelled = WorkInfo(id, WorkInfo.State.CANCELLED, emptySet())
        assertEquals(CheckNowState.Finished(id, null), cancelled.toState())
        val running = WorkInfo(id, WorkInfo.State.RUNNING, emptySet())
        assertEquals(CheckNowState.Running, running.toState())
    }
}
