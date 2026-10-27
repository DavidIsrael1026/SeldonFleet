package com.seldon.fleet.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.seldon.fleet.data.mapper.DeviceMapper
import com.seldon.fleet.data.source.AssetFleetSource
import com.seldon.fleet.domain.model.Device
import com.seldon.fleet.domain.repository.DeviceRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.io.IOException

/**
 * Instância do DataStore para persistir favoritos (REQ-07).
 */
val Context.fleetFavoritesDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "seldon_fleet_favorites_prefs"
)

/**
 * Implementação concreta de [DeviceRepository] (Inversão de Dependência - Seção 6.1).
 */
class FleetRepositoryImpl(
    private val assetFleetSource: AssetFleetSource,
    private val deviceMapper: DeviceMapper,
    private val dataStore: DataStore<Preferences>,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : DeviceRepository {

    companion object {
        private val FAVORITE_IDS_KEY = stringSetPreferencesKey("favorite_device_ids")
    }

    override suspend fun getDevices(): List<Device> = withContext(ioDispatcher) {
        val rawDtos = assetFleetSource.loadRawDevices()
        deviceMapper.mapToDomainList(rawDtos)
    }

    /**
     * Cumpre o contrato DeviceRepository exigido pelo compilador.
     */
    override fun getFavoriteIds(): Flow<Set<String>> {
        return dataStore.data
            .catch { exception ->
                if (exception is IOException) {
                    emit(emptyPreferences())
                } else {
                    throw exception
                }
            }
            .map { preferences ->
                preferences[FAVORITE_IDS_KEY] ?: emptySet()
            }
    }

    /**
     * Mantém compatibilidade caso a camada UI chame observeFavoriteIds.
     */
    fun observeFavoriteIds(): Flow<Set<String>> = getFavoriteIds()

    override suspend fun toggleFavorite(deviceId: String) {
        dataStore.edit { preferences ->
            val currentFavorites = preferences[FAVORITE_IDS_KEY] ?: emptySet()
            val updatedFavorites = if (currentFavorites.contains(deviceId)) {
                currentFavorites - deviceId
            } else {
                currentFavorites + deviceId
            }
            preferences[FAVORITE_IDS_KEY] = updatedFavorites
        }
    }
}