package io.github.mgdx.potable.ui.sample

import io.github.mgdx.potable.core.ParameterFamily
import io.github.mgdx.potable.core.model.Sample
import io.github.mgdx.potable.data.DataError
import io.github.mgdx.potable.data.SampleRepository
import io.github.mgdx.potable.ui.MainDispatcherRule
import io.github.mgdx.potable.ui.common.LoadError
import io.github.mgdx.potable.ui.sample
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.io.IOException
import kotlin.time.Duration.Companion.seconds

/** One call per queued answer; with nothing queued, a call waits for [gate]. */
private class FakeSampleRepository : SampleRepository {
    val answers = ArrayDeque<Result<Sample?>>()
    val calls = mutableListOf<Triple<String, String, String>>()
    var gate: CompletableDeferred<Sample?>? = null

    override suspend fun sample(communeCode: String, udiCode: String, sampleCode: String): Sample? {
        calls += Triple(communeCode, udiCode, sampleCode)
        val answer = answers.removeFirstOrNull() ?: return gate!!.await()
        return answer.getOrThrow()
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class SampleReportViewModelTest {
    @get:Rule
    val main = MainDispatcherRule()

    private val samples = FakeSampleRepository()
    private val report = sample().copy(results = listOf(nitrates, ecoli))

    private fun viewModel() = SampleReportViewModel(
        samples,
        "45234",
        "045000474",
        report.code,
        slowThreshold = 10.seconds,
    )

    @Test
    fun `the sample is loaded by its code and grouped by family`() = runTest(main.dispatcher) {
        samples.answers += Result.success(report)
        val viewModel = viewModel()
        advanceUntilIdle()
        assertEquals(listOf(Triple("45234", "045000474", report.code)), samples.calls)
        assertEquals(
            SampleDetailUiState.Loaded(
                report,
                listOf(
                    FamilyGroup(ParameterFamily.MICROBIOLOGY, listOf(ecoli)),
                    FamilyGroup(ParameterFamily.PHYSICOCHEMISTRY, listOf(nitrates)),
                ),
            ),
            viewModel.state.value,
        )
    }

    @Test
    fun `typing filters the results`() = runTest(main.dispatcher) {
        samples.answers += Result.success(report)
        val viewModel = viewModel()
        viewModel.onQueryChange("nitrates")
        advanceUntilIdle()
        assertEquals("nitrates", viewModel.query.value)
        assertEquals(
            SampleDetailUiState.Loaded(
                report,
                listOf(FamilyGroup(ParameterFamily.PHYSICOCHEMISTRY, listOf(nitrates))),
            ),
            viewModel.state.value,
        )
    }

    @Test
    fun `a sample Hub'Eau no longer publishes is unavailable`() = runTest(main.dispatcher) {
        samples.answers += Result.success(null)
        val viewModel = viewModel()
        advanceUntilIdle()
        assertEquals(SampleDetailUiState.Unavailable, viewModel.state.value)
    }

    @Test
    fun `an error waits for retry, which loads again`() = runTest(main.dispatcher) {
        samples.answers += Result.failure(DataError.Network(IOException("offline")))
        val viewModel = viewModel()
        advanceUntilIdle()
        assertEquals(SampleDetailUiState.Error(LoadError.NETWORK), viewModel.state.value)
        assertEquals(1, samples.calls.size)

        samples.gate = CompletableDeferred()
        viewModel.retry()
        runCurrent()
        assertEquals(SampleDetailUiState.Loading, viewModel.state.value)
        samples.gate!!.complete(report)
        advanceUntilIdle()
        assertTrue(viewModel.state.value is SampleDetailUiState.Loaded)
        assertEquals(2, samples.calls.size)

        // Nothing to retry once loaded.
        viewModel.retry()
        advanceUntilIdle()
        assertEquals(2, samples.calls.size)
    }

    @Test
    fun `an unexpected exception does not crash the screen`() = runTest(main.dispatcher) {
        samples.answers += Result.failure(IllegalStateException("bug"))
        val viewModel = viewModel()
        advanceUntilIdle()
        assertEquals(SampleDetailUiState.Error(LoadError.UNEXPECTED), viewModel.state.value)
    }

    @Test
    fun `a slow load is said so`() = runTest(main.dispatcher) {
        samples.gate = CompletableDeferred()
        val viewModel = viewModel()
        runCurrent()
        assertEquals(SampleDetailUiState.Loading, viewModel.state.value)
        assertFalse(viewModel.isSlow.value)
        advanceTimeBy(10.seconds)
        runCurrent()
        assertTrue(viewModel.isSlow.value)

        samples.gate!!.complete(report)
        advanceUntilIdle()
        assertFalse(viewModel.isSlow.value)
    }
}
