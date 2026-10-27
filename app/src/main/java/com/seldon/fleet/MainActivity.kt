package com.seldon.fleet

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.seldon.fleet.data.mapper.DeviceMapper
import com.seldon.fleet.data.repository.FleetRepositoryImpl
import com.seldon.fleet.data.repository.fleetFavoritesDataStore
import com.seldon.fleet.data.source.AssetFleetSource
import com.seldon.fleet.domain.usecase.ClassifyDevice
import com.seldon.fleet.ui.detail.DeviceDetailScreen
import com.seldon.fleet.ui.list.FleetListScreen
import com.seldon.fleet.ui.list.FleetListViewModel

/**
 * Activity principal da aplicação Seldon Fleet.
 * Realiza a composição de dependências (Seção 6.3) e alterna entre a Tela 1 (Lista)
 * e a Tela 2 (Detalhe) preservando integralmente o estado do filtro (REQ-05, REQ-06, REQ-07).
 */
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Composição manual das dependências apontando para o domínio (Seção 6.1 e 6.3)
        val assetSource = AssetFleetSource(context = applicationContext)
        val deviceMapper = DeviceMapper()
        val repository = FleetRepositoryImpl(
            assetFleetSource = assetSource,
            deviceMapper = deviceMapper,
            dataStore = applicationContext.fleetFavoritesDataStore
        )
        val classifyDevice = ClassifyDevice()

        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    val viewModel: FleetListViewModel = viewModel(
                        factory = FleetListViewModel.provideFactory(
                            repository = repository,
                            classifyDevice = classifyDevice
                        )
                    )

                    val uiState by viewModel.uiState.collectAsState()
                    val selectedDetailItem = uiState.selectedDeviceForDetail

                    if (selectedDetailItem != null) {
                        DeviceDetailScreen(
                            item = selectedDetailItem,
                            onBackClick = { viewModel.onNavigateBackToList() },
                            onToggleFavorite = {
                                viewModel.onToggleFavorite(selectedDetailItem.device.id)
                            }
                        )
                    } else {
                        FleetListScreen(
                            uiState = uiState,
                            onSelectFilter = { status -> viewModel.onSelectStatusFilter(status) },
                            onDeviceClick = { deviceId -> viewModel.onOpenDeviceDetail(deviceId) },
                            onToggleFavorite = { deviceId -> viewModel.onToggleFavorite(deviceId) }
                        )
                    }
                }
            }
        }
    }
}