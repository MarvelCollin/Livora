package com.example.livora.data.ir

import com.example.livora.data.ir.protocol.Daikin216Protocol
import com.example.livora.data.ir.protocol.Daikin2Protocol
import com.example.livora.data.ir.protocol.DaikinProtocol
import com.example.livora.data.ir.protocol.GreeProtocol
import com.example.livora.data.ir.protocol.GreeVariant
import com.example.livora.data.ir.protocol.HitachiProtocol
import com.example.livora.data.ir.protocol.MideaProtocol
import com.example.livora.data.ir.protocol.MitsubishiHeavyProtocol
import com.example.livora.data.ir.protocol.MitsubishiHeavyVariant
import com.example.livora.data.ir.protocol.MitsubishiProtocol
import com.example.livora.data.ir.protocol.PanasonicProtocol
import com.example.livora.data.ir.protocol.PanasonicVariant
import com.example.livora.data.ir.protocol.SamsungProtocol
import com.example.livora.data.ir.protocol.SharpProtocol
import com.example.livora.data.ir.protocol.SharpVariant
import com.example.livora.data.ir.protocol.TclProtocol
import com.example.livora.data.ir.protocol.ToshibaProtocol
import com.example.livora.data.model.AcMode
import com.example.livora.data.model.AcState
import com.example.livora.data.model.FanSpeed
import com.example.livora.data.model.SwingMode
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class AcVectorsTest {

    private fun ints(vararg values: Int) = values

    @Test
    fun greeYbofbOnMatchesLibraryCapture() {
        val state = AcState(
            isPoweredOn = true,
            temperature = 23,
            mode = AcMode.COOL,
            fanSpeed = FanSpeed.LOW,
            swingMode = SwingMode.VERTICAL
        )
        val bytes = GreeProtocol(GreeVariant.YBOFB).buildState(state)
        assertArrayEquals(ints(0x59, 0x07, 0x20, 0x50, 0x01, 0x20, 0x00, 0xC0), bytes)
    }

    @Test
    fun greeYaw1fOnSetsModelBit() {
        val state = AcState(
            isPoweredOn = true,
            temperature = 23,
            mode = AcMode.COOL,
            fanSpeed = FanSpeed.LOW,
            swingMode = SwingMode.VERTICAL
        )
        val bytes = GreeProtocol(GreeVariant.YAW1F).buildState(state)
        assertArrayEquals(ints(0x59, 0x07, 0x60, 0x50, 0x01, 0x20, 0x00, 0xC0), bytes)
    }

    @Test
    fun greeOffMatchesLibraryCapture() {
        val state = AcState(
            isPoweredOn = false,
            temperature = 23,
            mode = AcMode.COOL,
            fanSpeed = FanSpeed.LOW,
            swingMode = SwingMode.VERTICAL
        )
        val bytes = GreeProtocol(GreeVariant.YBOFB).buildState(state)
        assertArrayEquals(ints(0x51, 0x07, 0x20, 0x50, 0x01, 0x20, 0x00, 0x40), bytes)
    }

    @Test
    fun toshibaCoolHighMatchesLibraryState() {
        val state = AcState(isPoweredOn = true, temperature = 17, mode = AcMode.COOL, fanSpeed = FanSpeed.HIGH)
        val bytes = ToshibaProtocol().mainState(state)
        assertArrayEquals(ints(0xF2, 0x0D, 0x03, 0xFC, 0x01, 0x00, 0xC1, 0x00, 0xC0), bytes)
    }

    @Test
    fun toshibaSwingMessagesMatchLibraryState() {
        val protocol = ToshibaProtocol()
        val on = AcState(isPoweredOn = true, swingMode = SwingMode.VERTICAL)
        val off = AcState(isPoweredOn = true, swingMode = SwingMode.OFF)
        assertArrayEquals(ints(0xF2, 0x0D, 0x01, 0xFE, 0x21, 0x01, 0x20), protocol.swingState(on))
        assertArrayEquals(ints(0xF2, 0x0D, 0x01, 0xFE, 0x21, 0x02, 0x23), protocol.swingState(off))
    }

    @Test
    fun mitsubishiMatchesLibraryDefaultState() {
        val state = AcState(
            isPoweredOn = true,
            temperature = 22,
            mode = AcMode.HEAT,
            fanSpeed = FanSpeed.QUIET,
            swingMode = SwingMode.OFF
        )
        val bytes = MitsubishiProtocol().buildState(state)
        val expected = ints(0x23, 0xCB, 0x26, 0x01, 0x00, 0x20, 0x08, 0x06, 0x30, 0x45)
        assertArrayEquals(expected, bytes.copyOfRange(0, 10))
        assertEquals(0x00, bytes[16])
        var sum = 0
        for (index in 0 until 17) sum += bytes[index]
        assertEquals(sum and 0xFF, bytes[17])
    }

    @Test
    fun mitsubishiOffCoolMatchesCapturePrefix() {
        val state = AcState(isPoweredOn = false, temperature = 24, mode = AcMode.COOL, fanSpeed = FanSpeed.AUTO)
        val bytes = MitsubishiProtocol().buildState(state)
        assertArrayEquals(ints(0x23, 0xCB, 0x26, 0x01, 0x00, 0x00, 0x18, 0x08, 0x36), bytes.copyOfRange(0, 9))
    }

    @Test
    fun tclOnCool16MatchesLibraryCapture() {
        val state = AcState(isPoweredOn = true, temperature = 16, mode = AcMode.COOL, fanSpeed = FanSpeed.AUTO)
        val bytes = TclProtocol().buildState(state)
        assertArrayEquals(
            ints(0x23, 0xCB, 0x26, 0x01, 0x00, 0x24, 0x03, 0x0F, 0x00, 0x00, 0x00, 0x00, 0x80, 0xCB),
            bytes
        )
    }

    @Test
    fun tclAutoModeMatchesLibraryCaptureBody() {
        val state = AcState(isPoweredOn = true, temperature = 24, mode = AcMode.AUTO, fanSpeed = FanSpeed.AUTO)
        val bytes = TclProtocol().buildState(state)
        assertArrayEquals(
            ints(0x23, 0xCB, 0x26, 0x01, 0x00, 0x24, 0x08, 0x07, 0x00, 0x00, 0x00, 0x00, 0x80),
            bytes.copyOfRange(0, 13)
        )
    }

    @Test
    fun samsungStandardCool16MatchesLibraryCapture() {
        val state = AcState(
            isPoweredOn = true,
            temperature = 16,
            mode = AcMode.COOL,
            fanSpeed = FanSpeed.AUTO,
            swingMode = SwingMode.OFF
        )
        val bytes = SamsungProtocol().standardBytes(state)
        assertArrayEquals(
            ints(0x02, 0x92, 0x0F, 0x00, 0x00, 0x00, 0xF0, 0x01, 0xF2, 0xFE, 0x71, 0x00, 0x11, 0xF0),
            bytes
        )
    }

    @Test
    fun samsungStandardFanModeLowMatchesLibraryCapture() {
        val state = AcState(
            isPoweredOn = true,
            temperature = 24,
            mode = AcMode.FAN,
            fanSpeed = FanSpeed.LOW,
            swingMode = SwingMode.OFF
        )
        val bytes = SamsungProtocol().standardBytes(state)
        assertArrayEquals(
            ints(0x02, 0x92, 0x0F, 0x00, 0x00, 0x00, 0xF0, 0x01, 0xC2, 0xFE, 0x71, 0x80, 0x35, 0xF0),
            bytes
        )
    }

    @Test
    fun samsungExtendedPowerOffMatchesLibraryCapture() {
        val state = AcState(
            isPoweredOn = false,
            temperature = 24,
            mode = AcMode.COOL,
            fanSpeed = FanSpeed.LOW,
            swingMode = SwingMode.VERTICAL
        )
        val bytes = SamsungProtocol().extendedBytes(state)
        assertArrayEquals(
            ints(
                0x02, 0xB2, 0x0F, 0x00, 0x00, 0x00, 0xC0,
                0x01, 0xD2, 0x0F, 0x00, 0x00, 0x00, 0x00,
                0x01, 0x12, 0xAF, 0x71, 0x80, 0x15, 0xC0
            ),
            bytes
        )
    }

    @Test
    fun daikinHeatQuietMatchesLibraryState() {
        val state = AcState(
            isPoweredOn = false,
            temperature = 21,
            mode = AcMode.HEAT,
            fanSpeed = FanSpeed.QUIET,
            swingMode = SwingMode.OFF
        )
        val bytes = DaikinProtocol().buildState(state)
        val expected = ints(
            0x11, 0xDA, 0x27, 0x00, 0xC5, 0x00, 0x00, 0xD7,
            0x11, 0xDA, 0x27, 0x00, 0x42, 0x00, 0x00, 0x54,
            0x11, 0xDA, 0x27, 0x00, 0x00, 0x48, 0x2A, 0x00, 0xB0, 0x00,
            0x00, 0x06, 0x60, 0x00, 0x00, 0xC0, 0x00
        )
        assertArrayEquals(expected, bytes.copyOfRange(0, expected.size))
    }

    @Test
    fun mideaChecksumMatchesLibraryVectors() {
        val protocol = MideaProtocol()
        assertEquals(0x62, protocol.checksum(0xA1826FFFFF00L))
        assertEquals(0x70, protocol.checksum(0xA18177FFFF00L))
        assertEquals(0x61, protocol.checksum(0xA1806FFFFF00L))
        assertEquals(0x6C, protocol.checksum(0xA18A6FFFFF00L))
    }

    @Test
    fun panasonicJkeOffCoolMatchesRealCapturePrefix() {
        val state = AcState(
            isPoweredOn = false,
            temperature = 25,
            mode = AcMode.COOL,
            fanSpeed = FanSpeed.AUTO,
            swingMode = SwingMode.VERTICAL
        )
        val bytes = PanasonicProtocol(PanasonicVariant.JKE).buildState(state)
        val expected = ints(
            0x02, 0x20, 0xE0, 0x04, 0x00, 0x00, 0x00, 0x06, 0x02,
            0x20, 0xE0, 0x04, 0x00, 0x30, 0x32, 0x80, 0xAF, 0x00
        )
        assertArrayEquals(expected, bytes.copyOfRange(0, expected.size))
    }

    private val sharpOff = AcState(isPoweredOn = false)

    @Test
    fun sharpPowerOnAutoMatchesRealRemote() {
        val state = AcState(isPoweredOn = true, temperature = 15, mode = AcMode.AUTO, fanSpeed = FanSpeed.AUTO)
        val bytes = SharpProtocol(SharpVariant.A907).buildState(state, sharpOff, AcChange.POWER)
        assertArrayEquals(
            ints(0xAA, 0x5A, 0xCF, 0x10, 0x00, 0x11, 0x20, 0x00, 0x08, 0x80, 0x00, 0xE0, 0x01),
            bytes
        )
    }

    @Test
    fun sharpPowerOffMatchesRealRemote() {
        val state = AcState(isPoweredOn = false, temperature = 15, mode = AcMode.AUTO, fanSpeed = FanSpeed.AUTO)
        val previous = AcState(isPoweredOn = true)
        val bytes = SharpProtocol(SharpVariant.A907).buildState(state, previous, AcChange.POWER)
        assertArrayEquals(
            ints(0xAA, 0x5A, 0xCF, 0x10, 0x00, 0x21, 0x20, 0x00, 0x08, 0x80, 0x00, 0xE0, 0x31),
            bytes
        )
    }

    @Test
    fun sharpCoolTemperatureMatchesRealRemote() {
        val state = AcState(isPoweredOn = true, temperature = 28, mode = AcMode.COOL, fanSpeed = FanSpeed.AUTO)
        val bytes = SharpProtocol(SharpVariant.A907).buildState(state, state, AcChange.TEMPERATURE)
        assertArrayEquals(
            ints(0xAA, 0x5A, 0xCF, 0x10, 0xCD, 0x31, 0x22, 0x00, 0x08, 0x80, 0x04, 0xE0, 0x51),
            bytes
        )
    }

    @Test
    fun sharpFanSpeedsMatchRealRemote() {
        val protocol = SharpProtocol(SharpVariant.A907)
        val low = AcState(isPoweredOn = true, temperature = 28, mode = AcMode.COOL, fanSpeed = FanSpeed.LOW)
        val medium = low.copy(fanSpeed = FanSpeed.MEDIUM)
        val high = low.copy(fanSpeed = FanSpeed.HIGH)
        assertArrayEquals(
            ints(0xAA, 0x5A, 0xCF, 0x10, 0xCD, 0x31, 0x42, 0x00, 0x08, 0x80, 0x05, 0xE0, 0x21),
            protocol.buildState(low, low, AcChange.FAN)
        )
        assertArrayEquals(
            ints(0xAA, 0x5A, 0xCF, 0x10, 0xCD, 0x31, 0x32, 0x00, 0x08, 0x80, 0x05, 0xE0, 0x51),
            protocol.buildState(medium, medium, AcChange.FAN)
        )
        assertArrayEquals(
            ints(0xAA, 0x5A, 0xCF, 0x10, 0xCD, 0x31, 0x72, 0x00, 0x08, 0x80, 0x05, 0xE0, 0x11),
            protocol.buildState(high, high, AcChange.FAN)
        )
    }

    @Test
    fun sharpA705PowerOnCoolMatchesRealRemote() {
        val state = AcState(isPoweredOn = true, temperature = 16, mode = AcMode.COOL, fanSpeed = FanSpeed.AUTO)
        val bytes = SharpProtocol(SharpVariant.A705).buildState(state, sharpOff, AcChange.POWER)
        assertArrayEquals(
            ints(0xAA, 0x5A, 0xCF, 0x10, 0xD1, 0x11, 0x22, 0x00, 0x08, 0x80, 0x00, 0xF0, 0xF1),
            bytes
        )
    }

    @Test
    fun hitachiCool16MatchesRealCapture() {
        val state = AcState(isPoweredOn = true, temperature = 16, mode = AcMode.COOL, fanSpeed = FanSpeed.AUTO)
        assertArrayEquals(
            ints(
                0x80, 0x08, 0x0C, 0x02, 0xFD, 0x80, 0x7F, 0x88, 0x48, 0x80,
                0x20, 0x04, 0x00, 0x80, 0x00, 0x00, 0x00, 0x01, 0x00, 0x00,
                0x00, 0x00, 0x00, 0x00, 0x80, 0x00, 0x00, 0xAC
            ),
            HitachiProtocol().buildState(state)
        )
    }

    @Test
    fun hitachiHeat32HighMatchesRealCapture() {
        val state = AcState(isPoweredOn = true, temperature = 32, mode = AcMode.HEAT, fanSpeed = FanSpeed.HIGH)
        assertArrayEquals(
            ints(
                0x80, 0x08, 0x0C, 0x02, 0xFD, 0x80, 0x7F, 0x88, 0x48, 0x10,
                0xC0, 0x02, 0x00, 0xA0, 0x00, 0x00, 0x00, 0x01, 0x00, 0x00,
                0x00, 0x00, 0x00, 0x00, 0x80, 0x00, 0x00, 0xD0
            ),
            HitachiProtocol().buildState(state)
        )
    }

    @Test
    fun mitsubishiHeavy152HeatMaxMatchesLibraryExample() {
        val state = AcState(
            isPoweredOn = true,
            temperature = 24,
            mode = AcMode.HEAT,
            fanSpeed = FanSpeed.HIGH,
            swingMode = SwingMode.VERTICAL
        )
        val bytes = MitsubishiHeavyProtocol(MitsubishiHeavyVariant.ZMS_152).buildState(state)
        val expected = ints(
            0xAD, 0x51, 0x3C, 0xE5, 0x1A, 0x0C, 0xF3, 0x07,
            0xF8, 0x04, 0xFB, 0x00, 0xFF, 0x00, 0xFF, 0x00,
            0xFF, 0x80, 0x7F
        )
        val actual = bytes.copyOf()
        actual[13] = 0x00
        actual[14] = 0xFF
        assertArrayEquals(expected, actual)
    }

    @Test
    fun mitsubishiHeavy88DryMatchesLibraryExampleTail() {
        val state = AcState(
            isPoweredOn = true,
            temperature = 25,
            mode = AcMode.DRY,
            fanSpeed = FanSpeed.AUTO,
            swingMode = SwingMode.OFF
        )
        val bytes = MitsubishiHeavyProtocol(MitsubishiHeavyVariant.ZJS_88).buildState(state)
        assertArrayEquals(ints(0xAD, 0x51, 0x3C, 0xD9, 0x26), bytes.copyOfRange(0, 5))
        assertArrayEquals(ints(0x00, 0xFF, 0x8A, 0x75), bytes.copyOfRange(7, 11))
    }

    @Test
    fun daikin2OffMatchesRealCapture() {
        val state = AcState(
            isPoweredOn = false,
            temperature = 19,
            mode = AcMode.AUTO,
            fanSpeed = FanSpeed.AUTO,
            swingMode = SwingMode.OFF
        )
        val bytes = Daikin2Protocol { 0x37A }.buildState(state)
        assertArrayEquals(ints(0x11, 0xDA, 0x27, 0x00, 0x01, 0x7A, 0xC3, 0x70, 0x28, 0x0C), bytes.copyOfRange(0, 10))
        assertArrayEquals(
            ints(
                0x80, 0x04, 0xB0, 0x16, 0x24, 0x00, 0x00, 0xBE
            ),
            bytes.copyOfRange(10, 18)
        )
        assertArrayEquals(
            ints(
                0x11, 0xDA, 0x27, 0x00, 0x00, 0x08, 0x26, 0x00, 0xA0, 0x00,
                0x00, 0x06, 0x60, 0x00, 0x00, 0xC1, 0x80, 0x60, 0xE7
            ),
            bytes.copyOfRange(20, 39)
        )
    }

    @Test
    fun daikin2PowerOnUsesClockAndModeBits() {
        val state = AcState(
            isPoweredOn = true,
            temperature = 20,
            mode = AcMode.COOL,
            fanSpeed = FanSpeed.AUTO,
            swingMode = SwingMode.VERTICAL
        )
        val bytes = Daikin2Protocol { 0x230 }.buildState(state)
        assertEquals(0x30, bytes[5])
        assertEquals(0x42, bytes[6])
        assertEquals(0x39, bytes[25])
        assertEquals(0x28, bytes[26])
        assertEquals(0xDF, bytes[18])
    }

    @Test
    fun daikin216OffAutoMatchesLibraryState() {
        val state = AcState(
            isPoweredOn = false,
            temperature = 19,
            mode = AcMode.AUTO,
            fanSpeed = FanSpeed.AUTO,
            swingMode = SwingMode.OFF
        )
        assertArrayEquals(
            ints(
                0x11, 0xDA, 0x27, 0xF0, 0x00, 0x00, 0x00, 0x02,
                0x11, 0xDA, 0x27, 0x00, 0x00, 0x00, 0x26, 0x00, 0xA0, 0x00,
                0x00, 0x00, 0x00, 0x00, 0x00, 0xC0, 0x00, 0x00, 0x98
            ),
            Daikin216Protocol().buildState(state)
        )
    }
}
