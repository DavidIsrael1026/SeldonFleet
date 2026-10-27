package com.seldon.fleet.domain.model

/**
 * Estados canônicos de saúde operacional de um dispositivo no domínio.
 * Conforme Seção 4.3 do documento técnico:
 * Os rótulos visuais pertencem à camada UI; o domínio conhece apenas os estados lógicos.
 */
enum class DeviceStatus {
    OK,
    ATENCAO,
    CRITICO,
    INDETERMINADO
}