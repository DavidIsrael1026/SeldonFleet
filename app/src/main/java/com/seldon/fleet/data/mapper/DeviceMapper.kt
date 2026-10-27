package com.seldon.fleet.data.mapper

import com.seldon.fleet.data.dto.DeviceDto
import com.seldon.fleet.domain.model.Device

/**
 * Responsável por converter o modelo bruto do JSON (DeviceDto) para a entidade limpa de domínio (Device),
 * realizando a normalização física de unidades de energia (REQ-02).
 */
class DeviceMapper {

    fun mapToDomain(dto: DeviceDto): Device {
        val normalizedEnergyMah = normalizeEnergyToMah(
            energyConsumed = dto.energyConsumed,
            energyUnit = dto.energyUnit,
            nominalVoltage = dto.nominalVoltage
        )

        return Device(
            id = dto.id ?: "ID-DESCONHECIDO",
            model = dto.model ?: "Modelo Desconhecido",
            soc = dto.soc ?: "SoC Desconhecido",
            tier = dto.tier ?: "mid",
            ramMb = dto.ramMb ?: 0,
            androidApi = dto.androidApi ?: 0,
            batteryLevelPct = (dto.batteryLevelPct ?: 0).coerceIn(0, 100),
            batteryCapacityMah = dto.batteryCapacityMah,
            screenOnMinutes = dto.screenOnMinutes,
            energyConsumedMah = normalizedEnergyMah,
            rawEnergyConsumed = dto.energyConsumed,
            rawEnergyUnit = dto.energyUnit,
            nominalVoltage = dto.nominalVoltage,
            jankFramesPct = dto.jankFramesPct ?: 0.0,
            crashCount7d = dto.crashCount7d ?: 0,
            lastSeen = dto.lastSeen ?: "Desconhecido"
        )
    }

    fun mapToDomainList(dtos: List<DeviceDto>): List<Device> {
        return dtos.map { mapToDomain(it) }
    }

    /**
     * Converte o consumo energético para mAh conforme Seção 3.3 (Defeito 1) e REQ-02:
     * - Se "mAh": mantém o valor original.
     * - Se "mWh": divide por nominalVoltage (desde que nominalVoltage > 0).
     * - Caso contrário (ou se faltar tensão válida em mWh): retorna null para que o domínio classifique como INDETERMINADO.
     */
    private fun normalizeEnergyToMah(
        energyConsumed: Double?,
        energyUnit: String?,
        nominalVoltage: Double?
    ): Double? {
        if (energyConsumed == null || energyConsumed < 0.0 || energyUnit.isNullOrBlank()) {
            return null
        }

        return when (energyUnit.trim().lowercase()) {
            "mah" -> energyConsumed
            "mwh" -> {
                if (nominalVoltage != null && nominalVoltage > 0.0) {
                    energyConsumed / nominalVoltage
                } else {
                    null
                }
            }
            else -> null
        }
    }
}