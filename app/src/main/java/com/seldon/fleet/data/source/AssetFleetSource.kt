package com.seldon.fleet.data.source

import android.content.Context
import com.google.gson.Gson
import com.seldon.fleet.data.dto.DeviceDto
import com.seldon.fleet.data.dto.FleetResponseDto

/**
 * Fonte de dados local responsável exclusivamente por abrir e desserializar o arquivo
 * estático 'assets/frota.json' embarcado no APK (REQ-01).
 * Opera 100% offline (funciona em Modo Avião, sem rede).
 */
class AssetFleetSource(
    private val context: Context,
    private val gson: Gson = Gson(),
    private val assetFileName: String = DEFAULT_ASSET_FILE_NAME
) {

    companion object {
        const val DEFAULT_ASSET_FILE_NAME = "frota.json"
    }

    /**
     * Lê o arquivo JSON da pasta assets/ e retorna a lista bruta de [DeviceDto].
     * Utiliza o bloco '.use { ... }' para fechamento seguro do InputStream.
     */
    fun loadRawDevices(): List<DeviceDto> {
        val jsonContent = context.assets.open(assetFileName).use { inputStream ->
            inputStream.bufferedReader(Charsets.UTF_8).use { reader ->
                reader.readText()
            }
        }

        val fleetResponse = gson.fromJson(jsonContent, FleetResponseDto::class.java)
        return fleetResponse?.devices ?: emptyList()
    }
}