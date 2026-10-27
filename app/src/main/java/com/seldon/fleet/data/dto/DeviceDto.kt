package com.seldon.fleet.data.dto

import com.google.gson.annotations.SerializedName

/**
 * Representa o objeto raiz do arquivo assets/frota.json (Seção 3.1).
 */
data class FleetResponseDto(
    @SerializedName("schemaVersion")
    val schemaVersion: String? = null,

    @SerializedName("exportedAt")
    val exportedAt: String? = null,

    @SerializedName("devices")
    val devices: List<DeviceDto>? = emptyList()
)

/**
 * Espelha fielmente cada registro bruto do arquivo frota.json, inclusive os defeitos (Seção 3.2 e 3.3).
 * Todos os campos são anuláveis (? = null) para garantir que o parser nunca quebre diante de campos ausentes (REQ-03).
 */
data class DeviceDto(
    @SerializedName("id")
    val id: String? = null,

    @SerializedName("model")
    val model: String? = null,

    @SerializedName("soc")
    val soc: String? = null,

    @SerializedName("tier")
    val tier: String? = null,

    @SerializedName("ramMb")
    val ramMb: Int? = null,

    @SerializedName("androidApi")
    val androidApi: Int? = null,

    @SerializedName("batteryCapacityMah")
    val batteryCapacityMah: Int? = null,

    @SerializedName("batteryLevelPct")
    val batteryLevelPct: Int? = null,

    @SerializedName("screenOnMinutes")
    val screenOnMinutes: Int? = null,

    @SerializedName("energyConsumed")
    val energyConsumed: Double? = null,

    @SerializedName("energyUnit")
    val energyUnit: String? = null,

    @SerializedName("nominalVoltage")
    val nominalVoltage: Double? = null,

    @SerializedName("jankFramesPct")
    val jankFramesPct: Double? = null,

    @SerializedName("crashCount7d")
    val crashCount7d: Int? = null,

    @SerializedName("lastSeen")
    val lastSeen: String? = null
)