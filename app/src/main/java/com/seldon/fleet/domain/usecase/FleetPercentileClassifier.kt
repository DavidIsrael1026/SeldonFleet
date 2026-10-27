package com.seldon.fleet.domain.usecase

import com.seldon.fleet.domain.model.Device
import com.seldon.fleet.domain.model.DeviceStatus
import java.util.Locale
import kotlin.math.ceil

/**
 * TRILHA A — IA Aplicada à Otimização (Seção 10 da Especificação Técnica).
 *
 * Segunda implementação de classificação orientada pelos dados da própria frota (Data-Driven),
 * combinando estatística não-paramétrica (Percentis P75 e P90 de dreno relativo) e limiares
 * de Jank contextualizados pelo 'tier' de hardware do SoC (low, mid, high).
 *
 * Totalmente isolada em domain/ em Kotlin puro (Zero import android.* - REQ-04).
 */
class FleetPercentileClassifier(
    fleetDevices: List<Device>,
    private val warningDrainPercentile: Double = 0.75,
    private val criticalDrainPercentile: Double = 0.90
) {

    /**
     * Limiares aprendidos dinamicamente a partir da distribuição empírica da frota.
     */
    val learnedWarningDrainThreshold: Double
    val learnedCriticalDrainThreshold: Double
    val tierWarningJankThresholds: Map<String, Double>

    init {
        val validDrains = fleetDevices.mapNotNull { computeRelativeDrain(it) }.sorted()

        learnedWarningDrainThreshold = if (validDrains.isNotEmpty()) {
            percentile(validDrains, warningDrainPercentile)
        } else {
            7.0
        }

        learnedCriticalDrainThreshold = if (validDrains.isNotEmpty()) {
            percentile(validDrains, criticalDrainPercentile)
        } else {
            12.0
        }

        // Calcula o P85 de jank dentro de cada tier (low, mid, high) para evitar penalizar
        // GPUs de entrada com o mesmo rigor de flagships topo de linha
        tierWarningJankThresholds = fleetDevices
            .groupBy { it.tier.lowercase() }
            .mapValues { (_, devicesInTier) ->
                val janks = devicesInTier.map { it.jankFramesPct }.sorted()
                if (janks.isNotEmpty()) {
                    percentile(janks, 0.85).coerceAtLeast(6.0)
                } else {
                    8.0
                }
            }
    }

    operator fun invoke(device: Device): DeviceHealth {
        val capacity = device.batteryCapacityMah
        val screenMinutes = device.screenOnMinutes
        val energyMah = device.energyConsumedMah

        // 1º Limiar (Invariante de Segurança Física - REQ-03): Dados ausentes ou divisores nulos
        if (capacity == null || capacity <= 0 ||
            screenMinutes == null || screenMinutes <= 0 ||
            energyMah == null || energyMah < 0.0
        ) {
            return DeviceHealth(
                status = DeviceStatus.INDETERMINADO,
                absoluteDrainMahPerHour = null,
                relativeDrainPctPerHour = null,
                reason = "Dados insuficientes para inferência estatística (divisor nulo ou ausente)."
            )
        }

        val hours = screenMinutes / 60.0
        val absoluteDrain = energyMah / hours
        val relativeDrain = (absoluteDrain / capacity) * 100.0

        val tierKey = device.tier.lowercase()
        val dynamicWarningJank = tierWarningJankThresholds[tierKey] ?: 8.0

        // 2º Limiar: CRÍTICO orientado por percentil P90 da frota
        val isCriticalDrain = relativeDrain >= learnedCriticalDrainThreshold
        val isCriticalCrashes = device.crashCount7d >= 3
        val isCriticalJank = device.jankFramesPct >= 15.0

        if (isCriticalDrain || isCriticalCrashes || isCriticalJank) {
            return DeviceHealth(
                status = DeviceStatus.CRITICO,
                absoluteDrainMahPerHour = absoluteDrain,
                relativeDrainPctPerHour = relativeDrain,
                reason = String.format(
                    Locale.US,
                    "Crítico (Trilha A - P90): dreno de %.1f%%/h (limiar P90 da frota: %.1f%%/h).",
                    relativeDrain,
                    learnedCriticalDrainThreshold
                )
            )
        }

        // 3º Limiar: ATENÇÃO orientado por percentil P75 da frota e Jank por tier
        val isWarningDrain = relativeDrain >= learnedWarningDrainThreshold
        val isWarningCrashes = device.crashCount7d >= 1
        val isWarningJank = device.jankFramesPct >= dynamicWarningJank

        if (isWarningDrain || isWarningCrashes || isWarningJank) {
            return DeviceHealth(
                status = DeviceStatus.ATENCAO,
                absoluteDrainMahPerHour = absoluteDrain,
                relativeDrainPctPerHour = relativeDrain,
                reason = String.format(
                    Locale.US,
                    "Atenção (Trilha A - P75): dreno de %.1f%%/h (P75: %.1f%%/h), crashes=%d, jank=%.1f%%.",
                    relativeDrain,
                    learnedWarningDrainThreshold,
                    device.crashCount7d,
                    device.jankFramesPct
                )
            )
        }

        // 4º Limiar: OK (Comportamento dentro da distribuição normal da frota)
        return DeviceHealth(
            status = DeviceStatus.OK,
            absoluteDrainMahPerHour = absoluteDrain,
            relativeDrainPctPerHour = relativeDrain,
            reason = String.format(
                Locale.US,
                "Saudável na distribuição da frota: dreno %.1f%%/h abaixo do P75 (%.1f%%/h).",
                relativeDrain,
                learnedWarningDrainThreshold
            )
        )
    }

    companion object {
        fun computeRelativeDrain(device: Device): Double? {
            val capacity = device.batteryCapacityMah
            val screenMinutes = device.screenOnMinutes
            val energyMah = device.energyConsumedMah
            if (capacity == null || capacity <= 0 ||
                screenMinutes == null || screenMinutes <= 0 ||
                energyMah == null || energyMah < 0.0
            ) {
                return null
            }
            val hours = screenMinutes / 60.0
            val absoluteDrain = energyMah / hours
            return (absoluteDrain / capacity) * 100.0
        }

        /**
         * Cálculo determinístico de percentil por interpolação linear sobre lista ordenada.
         */
        fun percentile(sortedValues: List<Double>, p: Double): Double {
            require(sortedValues.isNotEmpty()) { "A lista não pode ser vazia" }
            if (sortedValues.size == 1) return sortedValues.first()
            val clampedP = p.coerceIn(0.0, 1.0)
            val index = clampedP * (sortedValues.size - 1)
            val lower = index.toInt()
            val upper = ceil(index).toInt()
            if (lower == upper) return sortedValues[lower]
            val weight = index - lower
            return sortedValues[lower] * (1.0 - weight) + sortedValues[upper] * weight
        }
    }
}