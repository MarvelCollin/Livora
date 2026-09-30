package com.example.livora.data.ir

import com.example.livora.data.model.AcMode
import com.example.livora.data.model.AcState
import com.example.livora.data.model.FanSpeed
import com.example.livora.data.model.SwingMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AcProtocolsTest {

    private val coolState = AcState(
        isPoweredOn = true,
        temperature = 24,
        mode = AcMode.COOL,
        fanSpeed = FanSpeed.AUTO
    )

    private fun decodeBits(signal: IrSignal, headerPulses: Int, bitCount: Int, oneThreshold: Int): Long {
        var value = 0L
        for (index in 0 until bitCount) {
            val space = signal.pattern[headerPulses + index * 2 + 1]
            value = (value shl 1) or if (space > oneThreshold) 1L else 0L
        }
        return value
    }

    @Test
    fun everyBrandAndModelProducesWellFormedSignals() {
        for (brand in AcBrands.all) {
            for (modelIndex in brand.models.indices) {
                val protocol = brand.createProtocol(modelIndex)
                val state = protocol.capabilities.sanitize(coolState)
                for (change in AcChange.entries) {
                    val signals = protocol.encode(state, coolState.copy(isPoweredOn = false), change)
                    for (signal in signals) {
                        assertTrue("${brand.id}/$modelIndex/$change is empty", signal.pattern.isNotEmpty())
                        assertEquals("${brand.id}/$modelIndex/$change must end on a mark", 1, signal.pattern.size % 2)
                        assertTrue("${brand.id}/$modelIndex/$change has non positive pulse", signal.pattern.all { it > 0 })
                    }
                }
                assertTrue("${brand.id}/$modelIndex power off", protocol.encodePowerOff().isNotEmpty())
            }
        }
    }

    @Test
    fun lgStateCodeMatchesKnownLayout() {
        val protocol = AcBrands.find("lg").createProtocol(0)
        val signal = protocol.encode(coolState, coolState, AcChange.TEMPERATURE).single()
        assertEquals(0x880095EL, decodeBits(signal, 2, 28, 1000))
    }

    @Test
    fun lgHeatModeUsesModeBitsWithoutTouchingPower() {
        val protocol = AcBrands.find("lg").createProtocol(0)
        val heat = coolState.copy(mode = AcMode.HEAT)
        val code = decodeBits(protocol.encode(heat, coolState, AcChange.MODE).single(), 2, 28, 1000)
        assertEquals(4L, (code shr 12) and 0x7L)
        assertEquals(0L, (code shr 18) and 0x3L)
        assertEquals(0x88L, code shr 20)
    }

    @Test
    fun lgPowerOffIsTheKnownOffCommand() {
        val protocol = AcBrands.find("lg").createProtocol(0)
        val code = decodeBits(protocol.encodePowerOff().single(), 2, 28, 1000)
        assertEquals(0x88C0051L, code)
    }

    @Test
    fun lgSwingUsesToggleAndExplicitCodes() {
        val swinging = coolState.copy(swingMode = SwingMode.VERTICAL)
        val toggle = AcBrands.find("lg").createProtocol(0).encode(swinging, coolState, AcChange.SWING)
        assertEquals(0x8810001L, decodeBits(toggle.single(), 2, 28, 1000))
        val explicit = AcBrands.find("lg").createProtocol(1).encode(swinging, coolState, AcChange.SWING)
        assertEquals(0x8813149L, decodeBits(explicit.first(), 2, 28, 1000))
    }

    @Test
    fun lgSwingCodesCarryValidChecksums() {
        val codes = longArrayOf(0x8810001L, 0x8813149L, 0x881315AL, 0x881316BL, 0x881317CL, 0x88C0051L, 0x88C00A6L)
        for (code in codes) {
            var value = code shr 4
            var sum = 0
            repeat(6) {
                sum += (value and 0xFL).toInt()
                value = value shr 4
            }
            assertEquals(code and 0xF, (sum and 0xF).toLong())
        }
    }

    @Test
    fun coolixDefaultStateMatchesLibraryDefault() {
        val protocol = AcBrands.find("coolix").createProtocol(0)
        val state = AcState(isPoweredOn = true, temperature = 25, mode = AcMode.AUTO, fanSpeed = FanSpeed.AUTO)
        val signal = protocol.encode(state, state, AcChange.MODE).single()
        val first = 0xB2L shl 16 or (0x1FL shl 8) or 0xC8L
        val bytes = LongArray(3) { index ->
            decodeBits(
                IrSignal(38000, signal.pattern.copyOfRange(2 + index * 32, 2 + index * 32 + 32)),
                0,
                8,
                1000
            )
        }
        assertEquals(first, (bytes[0] shl 16) or (bytes[1] shl 8) or bytes[2])
    }

    @Test
    fun midea48BitChecksumMatchesLibraryReferenceState() {
        val protocol = AcBrands.find("midea").createProtocol(0)
        val state = AcState(isPoweredOn = true, temperature = 25, mode = AcMode.AUTO, fanSpeed = FanSpeed.AUTO)
        val code = decodeBits(protocol.encode(state, state, AcChange.MODE).single(), 2, 48, 1000)
        assertEquals(0xA1L, code shr 40)
        assertEquals(0xFFFFL, (code shr 8) and 0xFFFFL)
        var sum = 0
        for (index in 1..5) sum += ((code shr (index * 8)) and 0xFFL).toInt().reverseByte()
        assertEquals(code and 0xFFL, ((256 - sum) and 0xFF).reverseByte().toLong())
    }

    @Test
    fun samsungPowerChangeSendsExtendedMessage() {
        val protocol = AcBrands.find("samsung").createProtocol(0)
        val signal = protocol.encode(coolState, coolState.copy(isPoweredOn = false), AcChange.POWER).single()
        assertEquals(2 + 3 * (2 + 56 * 2 + 2) - 1, signal.pattern.size)
    }

    @Test
    fun daikinStateHasThreeSectionsWithChecksums() {
        val protocol = AcBrands.find("daikin").createProtocol(0)
        val signal = protocol.encode(coolState, coolState, AcChange.MODE).single()
        val headerBits = 5 * 2 + 2
        val bits = LongArray(8) { index ->
            decodeBits(
                IrSignal(38000, signal.pattern.copyOfRange(headerBits + 2 + index * 16, headerBits + 2 + index * 16 + 16)),
                0,
                8,
                800
            )
        }
        val bytes = bits.map { java.lang.Long.reverse(it shl 56).toInt() and 0xFF }
        assertEquals(0x11, bytes[0])
        assertEquals(0xDA, bytes[1])
        assertEquals(0x27, bytes[2])
        assertEquals(bytes.take(7).sum() and 0xFF, bytes[7])
    }
}
