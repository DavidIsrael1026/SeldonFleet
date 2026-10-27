package com.seldon.fleet.domain.usecase

import com.seldon.fleet.domain.model.Device
import com.seldon.fleet.domain.model.DeviceStatus
import java.util.Locale

/**
 * Resultado completo da avaliação de saúde de um dispositivo.
 * Fornece tanto o status canônico quanto as grandezas derivadas e o motivo estruturado (REQ-06).
 */
data class DeviceHealth(
    val status: DeviceStatus,
    val absoluteDrainMahPerHour: Double?,
    val relativeDrainPctPerHour: Double?,
    val reason: String
)

/**
 * Caso de uso responsável por classificar um dispositivo.
 * Implementa estritamente a ordem de precedência da Seção 4.2 e protege contra divisões por zero (REQ-03).
 */
class ClassifyDevice {

    operator fun invoke(device: Device): DeviceHealth {
        val capacity = device.batteryCapacityMah
        val screenMinutes = device.screenOnMinutes
        val energyMah = device.energyConsumedMah

        // 1º Limiar de Precedência: INDETERMINADO (Dados ausentes ou divisores nulos)
        if (capacity == null || capacity <= 0 ||
            screenMinutes == null || screenMinutes <= 0 ||
            energyMah == null || energyMah < 0.0
        ) {
            val missingFields = mutableListOf<String>()
            if (capacity == null || capacity <= 0) missingFields.add("capacidade da bateria ausente/zero")
            if (screenMinutes == null || screenMinutes <= 0) missingFields.add("tempo de tela ausente/zero")
            if (energyMah == null || energyMah < 0.0) missingFields.add("energia inválida ou mWh sem tensão nominal")

            return DeviceHealth(
                status = DeviceStatus.INDETERMINADO,
                absoluteDrainMahPerHour = null,
                relativeDrainPctPerHour = null,
                reason = "Dados insuficientes: ${missingFields.joinToString(", ")}."
            )
        }

        // Cálculo das derivadas (Seção 4.1 do documento)
        val hours = screenMinutes / 60.0
        val absoluteDrain = energyMah / hours
        val relativeDrain = (absoluteDrain / capacity) * 100.0

        // 2º Limiar de Precedência: CRÍTICO (Dreno >= 12%/h OU falhas >= 3 OU jank >= 15%)
        val isCriticalDrain = relativeDrain >= 12.0
        val isCriticalCrashes = device.crashCount7d >= 3
        val isCriticalJank = device.jankFramesPct >= 15.0

        if (isCriticalDrain || isCriticalCrashes || isCriticalJank) {
            val reasons = mutableListOf<String>()
            if (isCriticalDrain) {
                reasons.add(String.format(Locale.US, "dreno de %.1f%%/h (limiar: >= 12.0%%/h)", relativeDrain))
            }
            if (isCriticalCrashes) {
                reasons.add("${device.crashCount7d} falhas nos últimos 7 dias (limiar: >= 3)")
            }
            if (isCriticalJank) {
                reasons.add(String.format(Locale.US, "%.1f%% de jank frames (limiar: >= 15.0%%)", device.jankFramesPct))
            }

            return DeviceHealth(
                status = DeviceStatus.CRITICO,
                absoluteDrainMahPerHour = absoluteDrain,
                relativeDrainPctPerHour = relativeDrain,
                reason = "Crítico: ${reasons.joinToString("; ")}."
            )
        }

        // 3º Limiar de Precedência: ATENÇÃO (Dreno >= 7%/h OU falhas >= 1 OU jank >= 8%)
        val isWarningDrain = relativeDrain >= 7.0
        val isWarningCrashes = device.crashCount7d >= 1
        val isWarningJank = device.jankFramesPct >= 8.0

        if (isWarningDrain || isWarningCrashes || isWarningJank) {
            val reasons = mutableListOf<String>()
            if (isWarningDrain) {
                reasons.add(String.format(Locale.US, "dreno de %.1f%%/h (limiar: >= 7.0%%/h)", relativeDrain))
            }
            if (isWarningCrashes) {
                reasons.add("${device.crashCount7d} falha(s) recente(s) (limiar: >= 1)")
            }
            if (isWarningJank) {
                reasons.add(String.format(Locale.US, "%.1f%% de jank frames (limiar: >= 8.0%%)", device.jankFramesPct))
            }

            return DeviceHealth(
                status = DeviceStatus.ATENCAO,
                absoluteDrainMahPerHour = absoluteDrain,
                relativeDrainPctPerHour = relativeDrain,
                reason = "Atenção: ${reasons.joinToString("; ")}."
            )
        }

        // 4º Limiar: OK (Nenhum sinal anômalo)
        return DeviceHealth(
            status = DeviceStatus.OK,
            absoluteDrainMahPerHour = absoluteDrain,
            relativeDrainPctPerHour = relativeDrain,
            reason = String.format(
                Locale.US,
                "Dispositivo saudável: dreno de %.1f%%/h, %d falhas, %.1f%% jank.",
                relativeDrain, device.crashCount7d, device.jankFramesPct
            )
        )
    }
}