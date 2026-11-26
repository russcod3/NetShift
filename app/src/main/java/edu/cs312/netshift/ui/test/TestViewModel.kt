package edu.cs312.netshift.ui.test

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import edu.cs312.netshift.AppContainer
import edu.cs312.netshift.data.db.entity.TestResultEntity
import edu.cs312.netshift.model.FailureReason
import edu.cs312.netshift.model.MeasurementPhase
import edu.cs312.netshift.model.MeasurementState
import edu.cs312.netshift.model.NetworkSnapshot
import edu.cs312.netshift.model.NetworkTransport
import edu.cs312.netshift.model.StartResult
import edu.cs312.netshift.model.TestTransport
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

data class TestUiState(
    val selectedTransport: TestTransport = TestTransport.WIFI,
    val locationName: String = "",
    val network: NetworkSnapshot = NetworkSnapshot(),
    val measurement: MeasurementState = MeasurementState(),
    val result: TestResultEntity? = null,
    val isStarting: Boolean = false,
    val isRetryingSave: Boolean = false,
    val showDataConfirmation: Boolean = false,
    val message: String? = null,
) {
    val isRunning: Boolean get() = isStarting || measurement.isRunning
}

class TestViewModel(
    private val engineState: StateFlow<MeasurementState>,
    private val startMeasurement: suspend (TestTransport, String) -> StartResult,
    private val cancelMeasurement: () -> Unit,
    private val retrySave: suspend () -> Boolean,
    private val observeResult: (String) -> Flow<TestResultEntity?>,
    network: StateFlow<NetworkSnapshot>,
) : ViewModel() {
    private val _uiState = MutableStateFlow(TestUiState())
    val uiState: StateFlow<TestUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            network.collect { snapshot -> _uiState.update { it.copy(network = snapshot) } }
        }
        viewModelScope.launch {
            engineState.collect { measurement ->
                _uiState.update { current ->
                    if (measurement.testId != null && current.measurement.testId == measurement.testId) {
                        current.copy(measurement = measurement, isStarting = false)
                    } else current
                }
            }
        }
    }

    fun selectTransport(transport: TestTransport) {
        if (!_uiState.value.isRunning) _uiState.update { it.copy(selectedTransport = transport, message = null) }
    }

    fun updateLocation(value: String) {
        _uiState.update { it.copy(locationName = value.take(60), message = null) }
    }

    fun requestStart() {
        val current = _uiState.value
        if (current.isRunning) return
        if (current.locationName.isBlank()) {
            _uiState.update { it.copy(message = "Add a location name before starting the test.") }
            return
        }
        val selectedMobile = current.selectedTransport == TestTransport.CELLULAR
        val metered = current.network.isMetered == true
        if (selectedMobile || metered) {
            _uiState.update { it.copy(showDataConfirmation = true, message = null) }
        } else startConfirmed()
    }

    fun dismissDataConfirmation() = _uiState.update { it.copy(showDataConfirmation = false) }

    fun confirmStart() {
        _uiState.update { it.copy(showDataConfirmation = false) }
        startConfirmed()
    }

    private fun startConfirmed() {
        val current = _uiState.value
        if (current.isRunning || current.locationName.isBlank()) return
        _uiState.update {
            it.copy(isStarting = true, result = null, message = null, measurement = MeasurementState())
        }
        viewModelScope.launch {
            try {
                when (val started = startMeasurement(current.selectedTransport, current.locationName.trim())) {
                is StartResult.Started -> {
                    val initialState = engineState.value.takeIf { it.testId == started.testId }
                        ?: MeasurementState(phase = MeasurementPhase.ACQUIRING, testId = started.testId)
                    _uiState.update { it.copy(measurement = initialState, isStarting = false) }
                    launch {
                        observeResult(started.testId).collect { row ->
                            _uiState.update { state -> if (state.measurement.testId == started.testId) state.copy(result = row) else state }
                        }
                    }
                }
                is StartResult.Rejected -> _uiState.update {
                    it.copy(isStarting = false, message = failureMessage(started.reason))
                }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                _uiState.update { it.copy(isStarting = false, message = "The test couldn't start. Check the connection and try again.") }
            }
        }
    }

    fun cancel() = cancelMeasurement()

    fun retrySaving() {
        if (_uiState.value.isRetryingSave) return
        _uiState.update { it.copy(isRetryingSave = true) }
        viewModelScope.launch {
            val saved = retrySave()
            _uiState.update {
                it.copy(
                    isRetryingSave = false,
                    message = if (saved) "Saved to History." else "Could not save yet. Check device storage and try again.",
                )
            }
        }
    }

    companion object {
        fun factory(container: AppContainer, network: StateFlow<NetworkSnapshot>): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val engine = container.measurementEngine
                TestViewModel(
                    engineState = engine.state,
                    startMeasurement = engine::start,
                    cancelMeasurement = engine::cancel,
                    retrySave = engine::retryUnsaved,
                    observeResult = container.testRepository::observeById,
                    network = network,
                )
            }
        }

        fun failureMessage(reason: String): String = when (reason) {
            FailureReason.NETWORK_UNAVAILABLE -> "The selected network isn't available. Check that Wi-Fi or mobile data is turned on."
            FailureReason.ENDPOINT_UNREACHABLE -> "Couldn't reach the test server before the request timed out. Check the connection and retry."
            FailureReason.ENDPOINT_ERROR -> "The test server returned an unexpected response. Try again later."
            FailureReason.NETWORK_CHANGED -> "The selected network changed during the test. Run it again on one network."
            FailureReason.DEADLINE -> "The test reached its time limit. The measurements collected so far were saved."
            FailureReason.CAPTIVE_PORTAL -> "This network may need a sign-in page before it can reach the internet."
            FailureReason.UNSUPPORTED_VPN, FailureReason.UNSUPPORTED_PROXY -> "Turn off the VPN or proxy and try again."
            FailureReason.PERMISSION_DENIED -> "NetShift couldn't access the selected network. Check the app's permissions."
            FailureReason.TRANSPORT_MISMATCH -> "The selected network type isn't currently active. Try again after switching networks."
            FailureReason.SAVE_FAILED -> "The result couldn't be saved. You can retry saving when storage is available."
            FailureReason.ALREADY_RUNNING -> "A test is already running."
            FailureReason.USER_CANCEL -> "Test cancelled. Any measurements already collected were saved."
            else -> "The test couldn't finish. Check the connection and try again."
        }
    }
}
