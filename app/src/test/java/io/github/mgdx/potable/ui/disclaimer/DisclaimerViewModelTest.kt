package io.github.mgdx.potable.ui.disclaimer

import io.github.mgdx.potable.data.preferences.AppPreferences
import io.github.mgdx.potable.ui.MainDispatcherRule
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DisclaimerViewModelTest {
    @get:Rule
    val main = MainDispatcherRule()

    private class FakePreferences(
        accepted: Boolean,
        val read: CompletableDeferred<Unit> = CompletableDeferred(Unit),
    ) : AppPreferences {
        val stored = MutableStateFlow(accepted)
        override val disclaimerAccepted: Flow<Boolean> = flow {
            // Reading the file takes a moment: nothing until [read] completes.
            read.await()
            emitAll(stored)
        }

        override suspend fun acceptDisclaimer() {
            stored.value = true
        }
    }

    @Test
    fun `nothing is decided before the preference is read`() = runTest(main.dispatcher) {
        val preferences = FakePreferences(accepted = false, read = CompletableDeferred())
        val viewModel = DisclaimerViewModel(preferences)
        runCurrent()
        assertEquals(DisclaimerState.UNKNOWN, viewModel.state.value)
        preferences.read.complete(Unit)
        advanceUntilIdle()
        assertEquals(DisclaimerState.REQUIRED, viewModel.state.value)
    }

    @Test
    fun `the notice is required on first launch, then accepted`() = runTest(main.dispatcher) {
        val preferences = FakePreferences(accepted = false)
        val viewModel = DisclaimerViewModel(preferences)
        advanceUntilIdle()
        assertEquals(DisclaimerState.REQUIRED, viewModel.state.value)
        viewModel.accept()
        advanceUntilIdle()
        assertTrue(preferences.stored.value)
        assertEquals(DisclaimerState.ACCEPTED, viewModel.state.value)
    }

    @Test
    fun `an acknowledged notice is not shown again`() = runTest(main.dispatcher) {
        val viewModel = DisclaimerViewModel(FakePreferences(accepted = true))
        advanceUntilIdle()
        assertEquals(DisclaimerState.ACCEPTED, viewModel.state.value)
    }
}
