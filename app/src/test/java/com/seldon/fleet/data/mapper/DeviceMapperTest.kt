package com.seldon.fleet.data.mapper

import com.seldon.fleet.data.dto.DeviceDto
import com.seldon.fleet.domain.model.DeviceStatus
import com.seldon.fleet.domain.usecase.ClassifyDevice
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Testes unitários na JVM para validar a normalização de unidades de energia (REQ-02).
 */
class DeviceMapperTest {

    private val mapper = DeviceMapper()
    private val classifyDevice = ClassifyDevice()

    @Test
    fun `equivalencia de unidade - mesmo consumo fisico em mAh e mWh produz mesmo dreno e mesmo status`() {
        // Aparelho A: 980 mAh diretos
        val dtoMah = DeviceDto(
            id = "DEV-MAH",
            model = "Galaxy A54",
            batteryCapacityMah = 5000,
            screenOnMinutes = 186,
            energyConsumed = 980.0,
            energyUnit = "mAh",
            nominalVoltage = 3.85,
            jankFramesPct = 4.1,
            crashCount7d = 0
        )

        // Aparelho B: 3773 mWh a 3.85 V (3773 / 3.85 = 980 mAh exatos - Anexo A do PDF)
        val dtoMwh = dtoMah.copy(
            id = "DEV-MWH",
            energyConsumed = 3773.0,
            energyUnit = "mWh",
            nominalVoltage = 3.85
        )

        val deviceFromMah = mapper.mapToDomain(dtoMah)
        val deviceFromMwh = mapper.mapToDomain(dtoMwh)

        assertNotNull(deviceFromMah.energyConsumedMah)
        assertNotNull(deviceFromMwh.energyConsumedMah)
        assertEquals(deviceFromMah.energyConsumedMah!!, deviceFromMwh.energyConsumedMah!!, 0.001)

        val healthMah = classifyDevice(deviceFromMah)
        val healthMwh = classifyDevice(deviceFromMwh)

        assertEquals(healthMah.relativeDrainPctPerHour!!, healthMwh.relativeDrainPctPerHour!!, 0.001)
        assertEquals(DeviceStatus.OK, healthMah.status)
        assertEquals(healthMah.status, healthMwh.status)
    }

    @Test
    fun `registro em mWh sem tensao nominal valida resulta em energia nula e status INDETERMINADO`() {
        val dtoSemTensao = DeviceDto(
            id = "DEV-NO-VOLT",
            batteryCapacityMah = 5000,
            screenOnMinutes = 120,
            energyConsumed = 3000.0,
            energyUnit = "mWh",
            nominalVoltage = null
        )

        val device = mapper.mapToDomain(dtoSemTensao)
        assertNull(device.energyConsumedMah)

        val health = classifyDevice(device)
        assertEquals(DeviceStatus.INDETERMINADO, health.status)
    }
}