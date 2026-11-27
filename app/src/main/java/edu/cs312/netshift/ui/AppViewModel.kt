package edu.cs312.netshift.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import edu.cs312.netshift.AppContainer
import edu.cs312.netshift.model.NetworkSnapshot
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn

class AppViewModel(private val container: AppContainer) : ViewModel() {
    private val subscription = SharingStarted.WhileSubscribed(stopTimeoutMillis = 5_000)
    val network = container.connectivityObserver.observe()
        .stateIn(viewModelScope, subscription, NetworkSnapshot())
    val tests = container.testRepository.observeRecent()
        .stateIn(viewModelScope, subscription, emptyList())
    val comparisons = container.testRepository.observeComparisons()
        .stateIn(viewModelScope, subscription, emptyList())
    val handoffs = container.handoffRepository.observeRecent()
        .stateIn(viewModelScope, subscription, emptyList())

    fun observeTest(id: String) = container.testRepository.observeById(id)

    companion object {
        fun factory(container: AppContainer): ViewModelProvider.Factory = viewModelFactory {
            initializer { AppViewModel(container) }
        }
    }
}
