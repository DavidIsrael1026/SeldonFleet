package com.seldon.fleet.domain.usecase

import com.seldon.fleet.domain.model.Device
import com.seldon.fleet.domain.model.DeviceStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Suíte obrigatória de testes unitários na JVM para a regra de negócio ClassifyDevice (Seção 7.1 e REQ-08).
 * Executa em milissegundos sem emulador.
 */
class ClassifyDeviceTest {

    private val classifyDevice = ClassifyDevice()

    private fun createBaseDevice(
        batteryCapacityMah: Int? = 5000,
        screenOnMinutes: Int? = 60, // 60 min = 1 hora exata (facilita o cálculo de fronteira!)
        energyConsumedMah: Double? = 250.0, // 250 mAh em 1h num aparelho de 5000 mAh = 5.0 %/h
        jankFramesPct: Double = 2.0,
        crashCount7d: Int = 0
    ): Device {
        return Device(
            id = "DEV-TEST",
            model = "Test Phone",
            soc = "Test SoC",
            tier = "mid",
            ramMb = 8192,
            androidApi = 34,
            batteryLevelPct = 80,
            batteryCapacityMah = batteryCapacityMah,
            screenOnMinutes = screenOnMinutes,
            energyConsumedMah = energyConsumedMah,
            rawEnergyConsumed = energyConsumedMah,
            rawEnergyUnit = "mAh",
            nominalVoltage = 3.85,
            jankFramesPct = jankFramesPct,
            crashCount7d = crashCount7d,
            lastSeen = "2026-10-20T03:00:00Z"
        )
    }

    @Test
    fun `capacidade nula ou igual a zero retorna INDETERMINADO sem NaN ou excecao`() {
        val deviceZeroCap = createBaseDevice(batteryCapacityMah = 0)
        val deviceNullCap = createBaseDevice(batteryCapacityMah = null)

        val resultZero = classifyDevice(deviceZeroCap)
        val resultNull = classifyDevice(deviceNullCap)

        assertEquals(DeviceStatus.INDETERMINADO, resultZero.status)
        assertNull(resultZero.relativeDrainPctPerHour)

        assertEquals(DeviceStatus.INDETERMINADO, resultNull.status)
        assertNull(resultNull.relativeDrainPctPerHour)
    }

    @Test
    fun `tempo de tela igual a zero ou ausente retorna INDETERMINADO sem divisao por zero`() {
        val deviceZeroScreen = createBaseDevice(screenOnMinutes = 0)
        val deviceNullScreen = createBaseDevice(screenOnMinutes = null)

        val resultZero = classifyDevice(deviceZeroScreen)
        val resultNull = classifyDevice(deviceNullScreen)

        assertEquals(DeviceStatus.INDETERMINADO, resultZero.status)
        assertNull(resultZero.absoluteDrainMahPerHour)

        assertEquals(DeviceStatus.INDETERMINADO, resultNull.status)
        assertNull(resultNull.absoluteDrainMahPerHour)
    }

    @Test
    fun `fronteira inferior do limiar de ATENCAO - imediatamente abaixo permanece OK`() {
        // Capacidade 5000 mAh, 60 min (1h): 349.5 mAh -> dreno relativo = 6.99 %/h (< 7.0 %/h)
        val deviceBelowWarning = createBaseDevice(
            batteryCapacityMah = 5000,
            screenOnMinutes = 60,
            energyConsumedMah = 349.5,
            jankFramesPct = 7.99,
            crashCount7d = 0
        )

        val result = classifyDevice(deviceBelowWarning)
        assertEquals(DeviceStatus.OK, result.status)
        assertEquals(6.99, result.relativeDrainPctPerHour!!, 0.001)
    }

    @Test
    fun `fronteira superior do limiar de ATENCAO - exatamente no limiar promove para ATENCAO`() {
        // Capacidade 5000 mAh, 60 min (1h): 350.0 mAh -> dreno relativo = 7.00 %/h (>= 7.0 %/h)
        val deviceAtWarningDrain = createBaseDevice(energyConsumedMah = 350.0)
        val deviceAtWarningCrash = createBaseDevice(energyConsumedMah = 200.0, crashCount7d = 1)
        val deviceAtWarningJank = createBaseDevice(energyConsumedMah = 200.0, jankFramesPct = 8.0)

        assertEquals(DeviceStatus.ATENCAO, classifyDevice(deviceAtWarningDrain).status)
        assertEquals(DeviceStatus.ATENCAO, classifyDevice(deviceAtWarningCrash).status)
        assertEquals(DeviceStatus.ATENCAO, classifyDevice(deviceAtWarningJank).status)
    }

    @Test
    fun `fronteira inferior do limiar de CRITICO - imediatamente abaixo permanece ATENCAO`() {
        // Capacidade 5000 mAh, 60 min (1h): 599.5 mAh -> dreno relativo = 11.99 %/h (< 12.0 %/h)
        val deviceBelowCritical = createBaseDevice(
            batteryCapacityMah = 5000,
            screenOnMinutes = 60,
            energyConsumedMah = 599.5,
            jankFramesPct = 14.99,
            crashCount7d = 2
        )

        val result = classifyDevice(deviceBelowCritical)
        assertEquals(DeviceStatus.ATENCAO, result.status)
        assertEquals(11.99, result.relativeDrainPctPerHour!!, 0.001)
    }

    @Test
    fun `fronteira superior do limiar de CRITICO - exatamente no limiar promove para CRITICO`() {
        // Capacidade 5000 mAh, 60 min (1h): 600.0 mAh -> dreno relativo = 12.00 %/h (>= 12.0 %/h)
        val deviceAtCriticalDrain = createBaseDevice(energyConsumedMah = 600.0)
        val deviceAtCriticalCrash = createBaseDevice(energyConsumedMah = 200.0, crashCount7d = 3)
        val deviceAtCriticalJank = createBaseDevice(energyConsumedMah = 200.0, jankFramesPct = 15.0)

        assertEquals(DeviceStatus.CRITICO, classifyDevice(deviceAtCriticalDrain).status)
        assertEquals(DeviceStatus.CRITICO, classifyDevice(deviceAtCriticalCrash).status)
        assertEquals(DeviceStatus.CRITICO, classifyDevice(deviceAtCriticalJank).status)
    }

    @Test
    fun `precedencia entre sinais - um sinal CRITICO vence um sinal de ATENCAO simultaneo`() {
        // Possui crashCount7d = 1 (sinal de ATENCAO) e jank = 9.8% (sinal de ATENCAO),
        // mas dreno relativo = 13.4 %/h (670 mAh / 5000 mAh em 1h -> sinal CRITICO)
        val deviceWithMixedSignals = createBaseDevice(
            batteryCapacityMah = 5000,
            screenOnMinutes = 60,
            energyConsumedMah = 670.0,
            jankFramesPct = 9.8,
            crashCount7d = 1
        )

        val result = classifyDevice(deviceWithMixedSignals)
        assertEquals(DeviceStatus.CRITICO, result.status)
        assertTrue(result.reason.contains("13.4%/h"))
    }
}