package com.example.livora.data.ir

import com.example.livora.data.ir.protocol.DaikinProtocol
import com.example.livora.data.ir.protocol.GreeProtocol
import com.example.livora.data.ir.protocol.GreeVariant
import com.example.livora.data.ir.protocol.MideaProtocol
import com.example.livora.data.ir.protocol.MitsubishiProtocol
import com.example.livora.data.ir.protocol.PanasonicProtocol
import com.example.livora.data.ir.protocol.PanasonicVariant
import com.example.livora.data.ir.protocol.SamsungProtocol
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
}
