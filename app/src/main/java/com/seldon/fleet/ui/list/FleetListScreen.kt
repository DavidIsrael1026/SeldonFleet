package com.seldon.fleet.ui.list

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.seldon.fleet.domain.model.DeviceStatus

/**
 * Traduz o estado lógico do domínio para o rótulo visual da camada de apresentação (Seção 4.3).
 */
fun DeviceStatus.toDisplayLabel(): String = when (this) {
    DeviceStatus.OK -> "OK"
    DeviceStatus.ATENCAO -> "ATENÇÃO"
    DeviceStatus.CRITICO -> "CRÍTICO"
    DeviceStatus.INDETERMINADO -> "SEM DADOS"
}

/**
 * Define cores visualmente distintas para cada chip de status (REQ-05).
 */
fun DeviceStatus.toChipColors(): Pair<Color, Color> = when (this) {
    DeviceStatus.OK -> Pair(Color(0xFFE8F5E9), Color(0xFF1B5E20))          // Fundo verde claro, texto verde escuro
    DeviceStatus.ATENCAO -> Pair(Color(0xFFFFF8E1), Color(0xFFE65100))     // Fundo âmbar claro, texto laranja escuro
    DeviceStatus.CRITICO -> Pair(Color(0xFFFFEBEE), Color(0xFFB71C1C))     // Fundo vermelho claro, texto vermelho escuro
    DeviceStatus.INDETERMINADO -> Pair(Color(0xFFEEEEEE), Color(0xFF424242)) // Fundo cinza claro, texto cinza escuro
}

/**
 * Tela 1: Lista da Frota (Seção 2.1, REQ-05, REQ-07).
 */
@Composable
fun FleetListScreen(
    uiState: FleetUiState,
    onSelectFilter: (DeviceStatus?) -> Unit,
    onDeviceClick: (String) -> Unit,
    onToggleFavorite: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFFF8F9FA))
    ) {
        // Cabeçalho superior da Tela 1
        Surface(
            color = Color(0xFF1A237E),
            contentColor = Color.White,
            shadowElevation = 4.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 14.dp)
            ) {
                Text(
                    text = "Seldon Fleet — Monitoramento",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(4.dp))
                val sumOfCounts = uiState.statusCounts.values.sum()
                Text(
                    text = "Total na frota: ${uiState.totalCount} dispositivos (Soma dos estados: $sumOfCounts)",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFFE8EAF6)
                )
            }
        }

        // Barra horizontal de filtros por status com contagem (REQ-05)
        StatusFilterBar(
            selectedStatus = uiState.selectedStatusFilter,
            totalCount = uiState.totalCount,
            statusCounts = uiState.statusCounts,
            onSelectFilter = onSelectFilter
        )

        when {
            uiState.isLoading -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }

            uiState.errorMessage != null -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = uiState.errorMessage,
                        color = Color(0xFFB71C1C),
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
            }

            else -> {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(
                        items = uiState.filteredItems,
                        key = { it.device.id }
                    ) { item ->
                        FleetDeviceRowCard(
                            item = item,
                            onClick = { onDeviceClick(item.device.id) },
                            onToggleFavorite = { onToggleFavorite(item.device.id) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StatusFilterBar(
    selectedStatus: DeviceStatus?,
    totalCount: Int,
    statusCounts: Map<DeviceStatus, Int>,
    onSelectFilter: (DeviceStatus?) -> Unit
) {
    Surface(
        color = Color.White,
        shadowElevation = 2.dp
    ) {
        LazyRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item {
                FilterChip(
                    selected = selectedStatus == null,
                    onClick = { onSelectFilter(null) },
                    label = {
                        Text(
                            text = "Todos ($totalCount)",
                            fontWeight = if (selectedStatus == null) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                )
            }

            items(DeviceStatus.entries) { status ->
                val count = statusCounts[status] ?: 0
                val isSelected = selectedStatus == status
                val (bgColor, textColor) = status.toChipColors()

                FilterChip(
                    selected = isSelected,
                    onClick = { onSelectFilter(status) },
                    label = {
                        Text(
                            text = "${status.toDisplayLabel()} ($count)",
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                        )
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = bgColor,
                        selectedLabelColor = textColor
                    )
                )
            }
        }
    }
}

@Composable
private fun FleetDeviceRowCard(
    item: FleetDeviceUiItem,
    onClick: () -> Unit,
    onToggleFavorite: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Botão de Favorito persistente (REQ-07)
            IconButton(onClick = onToggleFavorite) {
                Text(
                    text = if (item.isFavorite) "★" else "☆",
                    fontSize = 24.sp,
                    color = if (item.isFavorite) Color(0xFFFFB300) else Color(0xFF9E9E9E)
                )
            }

            Spacer(modifier = Modifier.width(6.dp))

            // Identificador, modelo e nível de bateria (Seção 2.1 / REQ-05)
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "${item.device.id} • ${item.device.model}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF212121)
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Bateria atual: ${item.device.batteryLevelPct}%  |  SoC: ${item.device.soc}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color(0xFF616161)
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            // Chip de status visualmente distinto (REQ-05)
            StatusBadgeChip(status = item.health.status)
        }
    }
}

@Composable
fun StatusBadgeChip(status: DeviceStatus) {
    val (backgroundColor, contentColor) = status.toChipColors()
    Surface(
        color = backgroundColor,
        contentColor = contentColor,
        shape = RoundedCornerShape(16.dp)
    ) {
        Text(
            text = status.toDisplayLabel(),
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold
        )
    }
}