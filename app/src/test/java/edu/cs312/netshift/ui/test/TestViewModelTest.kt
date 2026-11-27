package edu.cs312.netshift.ui.test

import androidx.lifecycle.viewModelScope
import edu.cs312.netshift.data.db.entity.TestResultEntity
import edu.cs312.netshift.model.MeasurementPhase
import edu.cs312.netshift.model.MeasurementState
import edu.cs312.netshift.model.NetworkSnapshot
import edu.cs312.netshift.model.NetworkTransport
import edu.cs312.netshift.model.StartResult
import edu.cs312.netshift.model.TestTransport
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TestViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @Before fun setUp() { Dispatchers.setMain(dispatcher) }
    @After fun tearDown() { Dispatchers.resetMain() }

    @Test fun blankLocationShowsValidationAndDoesNotStart() = runTest(dispatcher) {
        var starts = 0
        val vm = viewModel(start = { _, _ -> starts++; StartResult.Started("id") })
        advanceUntilIdle()

        vm.requestStart()
        advanceUntilIdle()

        assertEquals(0, starts)
        assertEquals("Add a location name before starting the test.", vm.uiState.value.message)
        vm.viewModelScope.cancel()
    }

    @Test fun mobileNeedsConfirmationBeforeStarting() = runTest(dispatcher) {
        var starts = 0
        var selected: TestTransport? = null
        val vm = viewModel(start = { transport, _ -> starts++; selected = transport; StartResult.Started("mobile-1") })
        advanceUntilIdle()
        vm.updateLocation("School")
        vm.selectTransport(TestTransport.CELLULAR)

        vm.requestStart()
        advanceUntilIdle()
        assertTrue(vm.uiState.value.showDataConfirmation)
        assertEquals(0, starts)

        vm.confirmStart()
        advanceUntilIdle()
        assertEquals(1, starts)
        assertEquals(TestTransport.CELLULAR, selected)
        assertEquals("mobile-1", vm.uiState.value.measurement.testId)
        vm.viewModelScope.cancel()
    }

    @Test fun meteredWifiAlsoNeedsConfirmation() = runTest(dispatcher) {
        val network = MutableStateFlow(NetworkSnapshot(transport = NetworkTransport.WIFI, isMetered = true))
        val vm = viewModel(network = network)
        advanceUntilIdle()
        vm.updateLocation("Home")

        vm.requestStart()
        advanceUntilIdle()

        assertTrue(vm.uiState.value.showDataConfirmation)
        vm.viewModelScope.cancel()
    }

    @Test fun failedStartIsShownAsReadableMessage() = runTest(dispatcher) {
        val vm = viewModel(start = { _, _ -> StartResult.Rejected("NETWORK_UNAVAILABLE") })
        advanceUntilIdle()
        vm.updateLocation("Home")
        vm.requestStart()
        advanceUntilIdle()

        assertFalse(vm.uiState.value.isStarting)
        assertTrue(vm.uiState.value.message!!.contains("isn't available"))
        vm.viewModelScope.cancel()
    }

    @Test fun cancelAndSaveRetryReachTheirEngineActions() = runTest(dispatcher) {
        var cancelled = false
        var retried = false
        val vm = viewModel(cancel = { cancelled = true }, retry = { retried = true; true })
        advanceUntilIdle()

        vm.cancel()
        vm.retrySaving()
        advanceUntilIdle()

        assertTrue(cancelled)
        assertTrue(retried)
        assertEquals("Saved to History.", vm.uiState.value.message)
        vm.viewModelScope.cancel()
    }

    private fun viewModel(
        start: suspend (TestTransport, String) -> StartResult = { _, _ -> StartResult.Started("wifi-1") },
        cancel: () -> Unit = {},
        retry: suspend () -> Boolean = { true },
        network: MutableStateFlow<NetworkSnapshot> = MutableStateFlow(NetworkSnapshot(isMetered = false)),
    ) = TestViewModel(
        engineState = MutableStateFlow(MeasurementState(phase = MeasurementPhase.IDLE)),
        startMeasurement = start,
        cancelMeasurement = cancel,
        retrySave = retry,
        observeResult = { id -> MutableStateFlow(TestResultEntity(startedAtEpochMs = 1L, requestedTransport = "WIFI", id = id)) },
        network = network,
    )
}
