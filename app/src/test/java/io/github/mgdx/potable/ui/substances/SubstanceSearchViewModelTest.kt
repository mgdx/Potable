package io.github.mgdx.potable.ui.substances

import androidx.lifecycle.SavedStateHandle
import io.github.mgdx.potable.core.SubstanceMatch
import io.github.mgdx.potable.core.SubstanceSearchResult
import io.github.mgdx.potable.core.model.SeriesPoint
import io.github.mgdx.potable.data.DataError
import io.github.mgdx.potable.data.SubstanceSearchRepository
import io.github.mgdx.potable.ui.FakeAppPreferences
import io.github.mgdx.potable.ui.MainDispatcherRule
import io.github.mgdx.potable.ui.common.LoadError
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.io.IOException
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/** A search of the history by name, a test double for each case. */
internal class FakeSubstanceSearchRepository : SubstanceSearchRepository {
    val queries = mutableListOf<Pair<String, LocalDate>>()
    val cancelled = mutableListOf<String>()

    /** The answer to a query; a query without one waits for [gates]. */
    val answers = mutableMapOf<String, Result<SubstanceSearchResult>>()
    val gates = mutableMapOf<String, CompletableDeferred<SubstanceSearchResult>>()

    override suspend fun search(
        communeCode: String,
        udiCode: String,
        query: String,
        since: LocalDate,
    ): SubstanceSearchResult {
        queries += query to since
        answers[query]?.let { return it.getOrThrow() }
        val gate = gates.getOrPut(query) { CompletableDeferred() }
        try {
            return gate.await()
        } catch (e: CancellationException) {
            cancelled += query
            throw e
        }
    }
}

internal fun match(code: String, label: String, text: String, count: Int = 3) = SubstanceMatch(
    parameterCodes = setOf(code),
    parameterCode = code,
    label = label,
    unit = "µg/L",
    latest = SeriesPoint(
        date = Instant.parse("2026-08-26T12:50:00Z"),
        sampleCode = "P1",
        value = null,
        text = text,
        censored = text.startsWith("<"),
    ),
    measureCount = count,
)

internal fun result(vararg matches: SubstanceMatch, partial: Boolean = false) =
    SubstanceSearchResult(matches.toList(), rowsRead = 10, totalRows = 10, partial = partial)

@OptIn(ExperimentalCoroutinesApi::class)
class SubstanceSearchViewModelTest {
    @get:Rule
    val main = MainDispatcherRule()

    private val repository = FakeSubstanceSearchRepository()

    private val preferences = FakeAppPreferences().apply { historyYearsSetting.value = 5 }

    private fun viewModel(saved: SavedStateHandle = SavedStateHandle()) = SubstanceSearchViewModel(
        repository,
        preferences,
        "45234",
        "045000474",
        Clock.fixed(Instant.parse("2026-11-04T10:00:00Z"), ZoneOffset.UTC),
        saved,
    )

    @Test
    fun `the query is sent once typing pauses, over the history window`() =
        runTest(main.dispatcher) {
            repository.answers["atrazine"] =
                Result.success(result(match("1107", "Atrazine", "<0,02")))
            val vm = viewModel()

            vm.onQueryChange("atra")
            advanceTimeBy(300)
            vm.onQueryChange("atrazine")
            advanceTimeBy(SubstanceSearchViewModel.SEARCH_DEBOUNCE.inWholeMilliseconds - 1)
            runCurrent()
            assertTrue(repository.queries.isEmpty())

            advanceUntilIdle()

            assertEquals(listOf("atrazine" to LocalDate.parse("2021-11-04")), repository.queries)
            val loaded = vm.state.value as SubstanceSearchUiState.Loaded
            assertEquals("<0,02", loaded.result.matches.single().latest.text)
        }

    @Test
    fun `fewer than three characters search nothing`() = runTest(main.dispatcher) {
        val vm = viewModel()

        vm.onQueryChange(" ph ")
        vm.submit()
        advanceUntilIdle()

        assertTrue(repository.queries.isEmpty())
        assertEquals(SubstanceSearchUiState.Idle, vm.state.value)
    }

    @Test
    fun `the search key searches at once`() = runTest(main.dispatcher) {
        repository.answers["nitrates"] = Result.success(result(match("1340", "Nitrates", "4,4")))
        val vm = viewModel()

        vm.onQueryChange("nitrates")
        vm.submit()
        runCurrent()

        assertEquals(listOf("nitrates"), repository.queries.map { it.first })
        assertTrue(vm.state.value is SubstanceSearchUiState.Loaded)
        advanceUntilIdle()
        assertEquals(1, repository.queries.size)
    }

    @Test
    fun `a new query cancels the request of the previous one`() = runTest(main.dispatcher) {
        repository.answers["glyphosate"] =
            Result.success(result(match("1506", "Glyphosate", "0,01")))
        val vm = viewModel()
        vm.onQueryChange("atrazine")
        vm.submit()
        runCurrent()
        assertEquals(SubstanceSearchUiState.Loading("atrazine"), vm.state.value)

        vm.onQueryChange("glyphosate")
        runCurrent()
        assertEquals(listOf("atrazine"), repository.cancelled)
        advanceUntilIdle()

        val loaded = vm.state.value as SubstanceSearchUiState.Loaded
        assertEquals("glyphosate", loaded.query)
    }

    @Test
    fun `nothing found is said with the start of the window`() = runTest(main.dispatcher) {
        repository.answers["chlordecone"] = Result.success(result())
        val vm = viewModel()

        vm.onQueryChange("chlordecone")
        advanceUntilIdle()

        assertEquals(
            SubstanceSearchUiState.Empty("chlordecone", LocalDate.parse("2021-11-04")),
            vm.state.value,
        )
    }

    @Test
    fun `an error offers Retry, which asks again`() = runTest(main.dispatcher) {
        repository.answers["atrazine"] = Result.failure(DataError.Network(IOException()))
        val vm = viewModel()
        vm.onQueryChange("atrazine")
        advanceUntilIdle()
        assertEquals(SubstanceSearchUiState.Error("atrazine", LoadError.NETWORK), vm.state.value)

        repository.answers["atrazine"] = Result.success(result(match("1107", "Atrazine", "0,03")))
        vm.retry()
        advanceUntilIdle()

        assertEquals(2, repository.queries.size)
        assertTrue(vm.state.value is SubstanceSearchUiState.Loaded)
    }

    @Test
    fun `the query typed survives the death of the process`() = runTest(main.dispatcher) {
        repository.answers["atrazine"] = Result.success(result(match("1107", "Atrazine", "0,03")))
        val saved = SavedStateHandle()
        viewModel(saved).onQueryChange("atrazine")

        val restored = viewModel(saved)
        advanceUntilIdle()

        assertEquals("atrazine", restored.query.value)
        assertTrue(restored.state.value is SubstanceSearchUiState.Loaded)
    }
}
