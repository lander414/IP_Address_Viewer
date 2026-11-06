package com.example.hotspotneighborinspector.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.hotspotneighborinspector.model.AutoRefreshInterval
import com.example.hotspotneighborinspector.model.ConnectedDevice
import com.example.hotspotneighborinspector.model.HotspotNetworkInfo
import com.example.hotspotneighborinspector.network.ArpAndNeighborParser
import com.example.hotspotneighborinspector.network.HotspotScannerRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class HotspotUiState(
    val networkInfo: HotspotNetworkInfo = HotspotNetworkInfo(),
    val selectedInterface: String? = null,
    val allDevices: List<ConnectedDevice> = emptyList(),
    val filteredDevices: List<ConnectedDevice> = emptyList(),
    val isScanning: Boolean = false,
    val scanProgress: Float = 0f,
    val searchQuery: String = "",
    val autoRefreshInterval: AutoRefreshInterval = AutoRefreshInterval.OFF
)

class HotspotInspectorViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = HotspotScannerRepository(application.applicationContext)

    private val _uiState = MutableStateFlow(HotspotUiState())
    val uiState: StateFlow<HotspotUiState> = _uiState.asStateFlow()

    private var scanJob: Job? = null
    private var autoRefreshJob: Job? = null

    init {
        _uiState.update { state ->
            state.copy(networkInfo = repository.detectNetworkInfo())
        }
        scanDevices()
    }

    fun scanDevices() {
        if (_uiState.value.isScanning) return
        scanJob?.cancel()
        scanJob = viewModelScope.launch {
            val preferredIface = _uiState.value.selectedInterface
            _uiState.update {
                it.copy(
                    isScanning = true,
                    scanProgress = 0.04f
                )
            }

            val (netInfo, devices) = repository.scanConnectedDevices(
                preferredInterface = preferredIface,
                onProgress = { progress ->
                    _uiState.update { current ->
                        current.copy(scanProgress = progress.coerceIn(0f, 1f))
                    }
                }
            )

            _uiState.update { current ->
                current.copy(
                    networkInfo = netInfo,
                    selectedInterface = netInfo.interfaceName,
                    allDevices = devices,
                    filteredDevices = ArpAndNeighborParser.filterDevices(devices, current.searchQuery),
                    isScanning = false,
                    scanProgress = 1f
                )
            }
        }
    }

    fun selectInterface(interfaceName: String) {
        if (_uiState.value.selectedInterface == interfaceName) return
        scanJob?.cancel()
        _uiState.update {
            it.copy(
                selectedInterface = interfaceName,
                isScanning = false,
                networkInfo = repository.detectNetworkInfo(interfaceName)
            )
        }
        scanDevices()
    }

    fun updateSearchQuery(query: String) {
        _uiState.update { current ->
            current.copy(
                searchQuery = query,
                filteredDevices = ArpAndNeighborParser.filterDevices(current.allDevices, query)
            )
        }
    }

    fun setAutoRefreshInterval(interval: AutoRefreshInterval) {
        autoRefreshJob?.cancel()
        _uiState.update { it.copy(autoRefreshInterval = interval) }
        if (interval == AutoRefreshInterval.OFF) return

        autoRefreshJob = viewModelScope.launch {
            while (isActive) {
                delay(interval.seconds * 1000L)
                if (!_uiState.value.isScanning) {
                    scanDevices()
                }
            }
        }
    }
}
