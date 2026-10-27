package com.seldon.fleet.domain.repository

import com.seldon.fleet.domain.model.Device
import kotlinx.coroutines.flow.Flow

/**
 * Contrato de repositório definido pelo domínio (Inversão de Dependência - Seção 6.1).
 * data/ depende deste contrato; domain/ desconhece como os dados são obtidos ou persistidos.
 */
interface DeviceRepository {
    suspend fun getDevices(): List<Device>
    fun getFavoriteIds(): Flow<Set<String>>
    suspend fun toggleFavorite(deviceId: String)
}