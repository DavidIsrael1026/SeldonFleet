package com.seldon.fleet.domain.model

/**
 * Entidade limpa que representa um dispositivo da frota.
 * O valor de consumo 'energyConsumedMah' já chega normalizado em mAh pela camada data (REQ-02).
 * Campos com '?' comportam dados ausentes ou inválidos de telemetria (Defeito 2 / REQ-03).
 */
data class Device(
    val id: String,
    val model: String,
    val soc: String,
    val tier: String,
    val ramMb: Int,
    val androidApi: Int,
    val batteryLevelPct: Int,
    val batteryCapacityMah: Int?,
    val screenOnMinutes: Int?,
    val energyConsumedMah: Double?,
    val rawEnergyConsumed: Double?,
    val rawEnergyUnit: String?,
    val nominalVoltage: Double?,
    val jankFramesPct: Double,
    val crashCount7d: Int,
    val lastSeen: String
)