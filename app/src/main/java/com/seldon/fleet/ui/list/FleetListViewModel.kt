package com.seldon.fleet.ui.list

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.seldon.fleet.domain.model.Device
import com.seldon.fleet.domain.model.DeviceStatus
import com.seldon.fleet.domain.repository.DeviceRepository
import com.seldon.fleet.domain.usecase.ClassifyDevice
import com.seldon.fleet.domain.usecase.DeviceHealth
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Representa um item já processado para exibição na interface (Tela 1 e Tela 2).
 * Une os dados limpos do dispositivo, o resultado da classificação e o estado de favorito.
 */
data class FleetDeviceUiItem(
    val device: Device,
    val health: DeviceHealth,
    val isFavorite: Boolean
)

/**
 * Fotografia completa do estado atual da interface (Padrão UDF - Unidirectional Data Flow).
 */
data class FleetUiState(
    val isLoading: Boolean = true,
    val errorMessage: String? = null,
    val allItems: List<FleetDeviceUiItem> = emptyList(),
    val filteredItems: List<FleetDeviceUiItem> = emptyList(),
    val selectedStatusFilter: DeviceStatus? = null, // null = mostra "Todos"
    val statusCounts: Map<DeviceStatus, Int> = emptyMap(),
    val selectedDeviceForDetail: FleetDeviceUiItem? = null
) {
    val totalCount: Int get() = allItems.size
}

/**
 * ViewModel responsável por coordenar o estado da Tela 1 (Lista) e Tela 2 (Detalhe).
 * Recebe [DeviceRepository] e [ClassifyDevice] por injeção via construtor (Seção 6.3).
 */
class FleetListViewModel(
    private val repository: DeviceRepository,
    private val classifyDevice: ClassifyDevice
) : ViewModel() {

    private val _uiState = MutableStateFlow(FleetUiState())
    val uiState: StateFlow<FleetUiState> = _uiState.asStateFlow()

    private var cachedDevices: List<Device> = emptyList()
    private var currentFavoriteIds: Set<String> = emptySet()

    init {
        loadFleetAndObserveFavorites()
    }

    private fun loadFleetAndObserveFavorites() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            try {
                // 1. Carrega os dispositivos de assets/frota.json (REQ-01)
                cachedDevices = repository.getDevices()

                // 2. Observa continuamente as alterações de favoritos no DataStore (REQ-07)
                repository.getFavoriteIds().collectLatest { favoriteIds ->
                    currentFavoriteIds = favoriteIds
                    rebuildUiState()
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = "Falha ao carregar frota local: ${e.message}"
                    )
                }
            }
        }
    }

    /**
     * Seleciona ou limpa o filtro por status na Tela 1 (REQ-05).
     */
    fun onSelectStatusFilter(status: DeviceStatus?) {
        _uiState.update { current ->
            val newFilter = if (current.selectedStatusFilter == status) null else status
            current.copy(selectedStatusFilter = newFilter)
        }
        rebuildUiState()
    }

    /**
     * Abre a Tela 2 (Detalhe) para o dispositivo clicado (REQ-06).
     */
    fun onOpenDeviceDetail(deviceId: String) {
        val target = _uiState.value.allItems.find { it.device.id == deviceId }
        _uiState.update { it.copy(selectedDeviceForDetail = target) }
    }

    /**
     * Retorna da Tela 2 para a Tela 1 preservando o filtro que estava aplicado (REQ-06).
     */
    fun onNavigateBackToList() {
        _uiState.update { it.copy(selectedDeviceForDetail = null) }
    }

    /**
     * Marca ou desmarca um dispositivo como favorito de forma persistente (REQ-07).
     */
    fun onToggleFavorite(deviceId: String) {
        viewModelScope.launch {
            repository.toggleFavorite(deviceId)
        }
    }

    private fun rebuildUiState() {
        val allItems = cachedDevices.map { device ->
            FleetDeviceUiItem(
                device = device,
                health = classifyDevice(device),
                isFavorite = currentFavoriteIds.contains(device.id)
            )
        }

        // Calcula a contagem de cada um dos 4 estados canônicos (REQ-05)
        val counts = DeviceStatus.entries.associateWith { status ->
            allItems.count { it.health.status == status }
        }

        val activeFilter = _uiState.value.selectedStatusFilter
        val filtered = if (activeFilter == null) {
            allItems
        } else {
            allItems.filter { it.health.status == activeFilter }
        }

        // Se a Tela 2 estiver aberta e o usuário favoritar o aparelho, atualiza o item aberto
        val currentDetailId = _uiState.value.selectedDeviceForDetail?.device?.id
        val updatedDetail = if (currentDetailId != null) {
            allItems.find { it.device.id == currentDetailId }
        } else {
            null
        }

        _uiState.update {
            it.copy(
                isLoading = false,
                errorMessage = null,
                allItems = allItems,
                filteredItems = filtered,
                statusCounts = counts,
                selectedDeviceForDetail = updatedDetail
            )
        }
    }

    companion object {
        /**
         * Fábrica para Injeção de Dependência manual via construtor (Seção 6.3).
         */
        fun provideFactory(
            repository: DeviceRepository,
            classifyDevice: ClassifyDevice
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return FleetListViewModel(repository, classifyDevice) as T
            }
        }
    }
}