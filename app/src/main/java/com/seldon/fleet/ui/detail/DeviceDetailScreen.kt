package com.seldon.fleet.ui.detail

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.seldon.fleet.ui.list.FleetDeviceUiItem
import com.seldon.fleet.ui.list.StatusBadgeChip
import com.seldon.fleet.ui.list.toChipColors
import java.util.Locale

/**
 * Tela 2: Detalhe do Dispositivo e Justificativa da Classificação (Seção 2.2 e REQ-06).
 */
@Composable
fun DeviceDetailScreen(
    item: FleetDeviceUiItem,
    onBackClick: () -> Unit,
    onToggleFavorite: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Intercepta o botão Voltar do sistema Android para retornar à lista preservando o filtro (REQ-06)
    BackHandler(onBack = onBackClick)

    val device = item.device
    val health = item.health
    val (statusBgColor, statusTextColor) = health.status.toChipColors()

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFFF8F9FA))
    ) {
        // Barra superior com botão Voltar e estrela de Favorito
        Surface(
            color = Color(0xFF1A237E),
            contentColor = Color.White,
            shadowElevation = 4.dp
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = onBackClick) {
                    Text(
                        text = "← Voltar",
                        color = Color.White,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 8.dp)
                ) {
                    Text(
                        text = "${device.id} • ${device.model}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Detalhes de Telemetria",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFFE8EAF6)
                    )
                }

                IconButton(onClick = onToggleFavorite) {
                    Text(
                        text = if (item.isFavorite) "★" else "☆",
                        fontSize = 26.sp,
                        color = if (item.isFavorite) Color(0xFFFFD54F) else Color.White
                    )
                }
            }
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Cartão de Destaque: Status + Justificativa Textual da Classificação (REQ-06)
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = statusBgColor),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Diagnóstico de Saúde",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = statusTextColor
                        )
                        StatusBadgeChip(status = health.status)
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Text(
                        text = health.reason,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = statusTextColor
                    )
                }
            }

            // Cartão de Métricas Energéticas e Derivadas (REQ-02, Seção 4.1)
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Bateria e Consumo Normalizado",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF1A237E)
                    )

                    DetailAttributeRow(
                        label = "Nível atual da bateria",
                        value = "${device.batteryLevelPct}%"
                    )
                    DetailAttributeRow(
                        label = "Capacidade da bateria",
                        value = device.batteryCapacityMah?.let { "$it mAh" } ?: "Ausente"
                    )
                    DetailAttributeRow(
                        label = "Consumo normalizado (REQ-02)",
                        value = device.energyConsumedMah?.let {
                            String.format(Locale.US, "%.1f mAh", it)
                        } ?: "Indisponível"
                    )
                    DetailAttributeRow(
                        label = "Registro bruto no JSON",
                        value = "${device.rawEnergyConsumed ?: "-"} ${device.rawEnergyUnit ?: ""} (Tensão: ${device.nominalVoltage ?: "-"} V)"
                    )
                    DetailAttributeRow(
                        label = "Tempo de tela ligada",
                        value = device.screenOnMinutes?.let { "$it min" } ?: "Ausente"
                    )
                    DetailAttributeRow(
                        label = "Dreno absoluto calculado",
                        value = health.absoluteDrainMahPerHour?.let {
                            String.format(Locale.US, "%.1f mAh/h", it)
                        } ?: "N/A (Divisor nulo/inválido)"
                    )
                    DetailAttributeRow(
                        label = "Dreno relativo calculado",
                        value = health.relativeDrainPctPerHour?.let {
                            String.format(Locale.US, "%.1f %%/h", it)
                        } ?: "N/A (Divisor nulo/inválido)"
                    )
                }
            }

            // Cartão de Hardware, Estabilidade e Telemetria (Seção 2.2)
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Atributos de Hardware e Estabilidade",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF1A237E)
                    )

                    DetailAttributeRow(label = "Identificador", value = device.id)
                    DetailAttributeRow(label = "Modelo", value = device.model)
                    DetailAttributeRow(label = "Processador (SoC)", value = "${device.soc} (tier: ${device.tier})")
                    DetailAttributeRow(label = "Memória RAM", value = "${device.ramMb} MB")
                    DetailAttributeRow(label = "Nível de API do Android", value = "API ${device.androidApi}")
                    DetailAttributeRow(
                        label = "Taxa de quadros perdidos (Jank)",
                        value = String.format(Locale.US, "%.1f%%", device.jankFramesPct)
                    )
                    DetailAttributeRow(
                        label = "Falhas recentes (7 dias)",
                        value = "${device.crashCount7d} crash(es)"
                    )
                    DetailAttributeRow(
                        label = "Último contato (UTC)",
                        value = device.lastSeen
                    )
                }
            }
        }
    }
}

@Composable
private fun DetailAttributeRow(
    label: String,
    value: String
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                color = Color(0xFF616161),
                modifier = Modifier.weight(1f)
            )
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF212121)
            )
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(Color(0xFFEEEEEE))
        )
    }
}