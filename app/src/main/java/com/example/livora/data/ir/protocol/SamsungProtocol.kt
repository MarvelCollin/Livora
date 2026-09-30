package com.example.livora.data.ir.protocol

import com.example.livora.data.ir.ALL_MODES
import com.example.livora.data.ir.ALL_SWINGS
import com.example.livora.data.ir.AcCapabilities
import com.example.livora.data.ir.AcChange
import com.example.livora.data.ir.AcProtocol
import com.example.livora.data.ir.BASIC_FANS
import com.example.livora.data.ir.BitTiming
import com.example.livora.data.ir.IrPulseBuilder
import com.example.livora.data.ir.IrSignal
import com.example.livora.data.model.AcMode
import com.example.livora.data.model.AcState
import com.example.livora.data.model.FanSpeed
import com.example.livora.data.model.SwingMode

class SamsungProtocol : AcProtocol {

    override val capabilities = AcCapabilities(
        modes = ALL_MODES,
        fanSpeeds = BASIC_FANS,
        swingModes = ALL_SWINGS,
        minTemp = 16,
        maxTemp = 30,
        hasDisplay = true
    )

    private val timing = BitTiming(mark = 586, oneSpace = 1432, zeroSpace = 436)
    private var forceExtended = true

    override fun encode(state: AcState, previous: AcState, change: AcChange): List<IrSignal> {
        if (!state.isPoweredOn && change != AcChange.POWER) return emptyList()
        if (change == AcChange.SLEEP || change == AcChange.ECO) return emptyList()
        val needsExtended = forceExtended || state.isPoweredOn != previous.isPoweredOn
        forceExtended = false
        return listOf(signal(if (needsExtended) extendedBytes(state) else standardBytes(state)))
    }

    internal fun standardBytes(state: AcState): IntArray = applyChecksums(buildState(state))

    internal fun extendedBytes(state: AcState): IntArray {
        val standard = buildState(state)
        val extended = IntArray(SECTION_LENGTH * 3)
        standard.copyInto(extended, 0, 0, SECTION_LENGTH)
        EXTENDED_MIDDLE.copyInto(extended, SECTION_LENGTH)
        standard.copyInto(extended, SECTION_LENGTH * 2, SECTION_LENGTH, SECTION_LENGTH * 2)
        return applyChecksums(extended)
    }

    private fun buildState(state: AcState): IntArray {
        val bytes = RESET_STATE.copyOf()
        val power = if (state.isPoweredOn) 0b11 else 0b00
        bytes[6] = (bytes[6] and 0xCF) or (power shl 4)
        bytes[13] = (bytes[13] and 0xCF) or (power shl 4)

        val swing = when (state.swingMode) {
            SwingMode.OFF -> 0b111
            SwingMode.VERTICAL -> 0b010
            SwingMode.HORIZONTAL -> 0b011
            SwingMode.BOTH -> 0b100
        }
        bytes[9] = (bytes[9] and 0x8F) or (swing shl 4)

        bytes[10] = if (state.isDisplayOn) bytes[10] or 0x10 else bytes[10] and 0xEF
        bytes[11] = (bytes[11] and 0x0F) or ((state.temperature.coerceIn(16, 30) - 16) shl 4)

        val mode = when (state.mode) {
            AcMode.AUTO -> 0
            AcMode.COOL -> 1
            AcMode.DRY -> 2
            AcMode.FAN -> 3
            AcMode.HEAT -> 4
        }
        val fan = if (state.mode == AcMode.AUTO) 6 else when (state.fanSpeed) {
            FanSpeed.AUTO -> 0
            FanSpeed.QUIET, FanSpeed.LOW -> 2
            FanSpeed.MEDIUM -> 4
            FanSpeed.HIGH -> 5
        }
        bytes[12] = (bytes[12] and 0x81) or (fan shl 1) or (mode shl 4)
        return bytes
    }

    private fun sectionChecksum(bytes: IntArray, offset: Int): Int {
        var sum = Integer.bitCount(bytes[offset] and 0xFF)
        sum += Integer.bitCount(bytes[offset + 1] and 0x0F)
        sum += Integer.bitCount((bytes[offset + 2] shr 4) and 0x0F)
        for (index in 3..6) sum += Integer.bitCount(bytes[offset + index] and 0xFF)
        return (sum xor 0xFF) and 0xFF
    }

    private fun applyChecksums(bytes: IntArray): IntArray {
        for (offset in 0 until bytes.size step SECTION_LENGTH) {
            val checksum = sectionChecksum(bytes, offset)
            bytes[offset + 1] = (bytes[offset + 1] and 0x0F) or ((checksum and 0x0F) shl 4)
            bytes[offset + 2] = (bytes[offset + 2] and 0xF0) or ((checksum shr 4) and 0x0F)
        }
        return bytes
    }

    private fun signal(bytes: IntArray): IrSignal {
        val builder = IrPulseBuilder(38000)
        builder.header(690, 17844)
        for (offset in 0 until bytes.size step SECTION_LENGTH) {
            builder.header(3086, 8864)
            builder.bytesLsbFirst(bytes, offset, offset + SECTION_LENGTH, timing)
            builder.footer(timing.mark, 2886)
        }
        return builder.build()
    }

    companion object {
        private const val SECTION_LENGTH = 7
        private val RESET_STATE = intArrayOf(
            0x02, 0x92, 0x0F, 0x00, 0x00, 0x00, 0xF0,
            0x01, 0x02, 0xAE, 0x71, 0x00, 0x15, 0xF0
        )
        private val EXTENDED_MIDDLE = intArrayOf(0x01, 0xD2, 0x0F, 0x00, 0x00, 0x00, 0x00)
    }
}
