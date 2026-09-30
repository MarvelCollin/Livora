package com.example.livora.data.ir.protocol

import com.example.livora.data.ir.ALL_FANS
import com.example.livora.data.ir.ALL_MODES
import com.example.livora.data.ir.ALL_SWINGS
import com.example.livora.data.ir.AcCapabilities
import com.example.livora.data.ir.AcChange
import com.example.livora.data.ir.AcProtocol
import com.example.livora.data.ir.BitTiming
import com.example.livora.data.ir.IrPulseBuilder
import com.example.livora.data.ir.IrSignal
import com.example.livora.data.model.AcMode
import com.example.livora.data.model.AcState
import com.example.livora.data.model.FanSpeed

class DaikinProtocol : AcProtocol {

    override val capabilities = AcCapabilities(
        modes = ALL_MODES,
        fanSpeeds = ALL_FANS,
        swingModes = ALL_SWINGS,
        minTemp = 16,
        maxTemp = 30,
        hasEco = true
    )

    private val timing = BitTiming(mark = 428, oneSpace = 1280, zeroSpace = 428)

    override fun encode(state: AcState, previous: AcState, change: AcChange): List<IrSignal> {
        if (!state.isPoweredOn && change != AcChange.POWER) return emptyList()
        if (change == AcChange.SLEEP || change == AcChange.DISPLAY) return emptyList()
        return listOf(signal(buildState(state)))
    }

    private fun buildState(state: AcState): IntArray {
        val bytes = IntArray(STATE_LENGTH)
        bytes[0] = 0x11
        bytes[1] = 0xDA
        bytes[2] = 0x27
        bytes[4] = 0xC5
        bytes[8] = 0x11
        bytes[9] = 0xDA
        bytes[10] = 0x27
        bytes[12] = 0x42
        bytes[16] = 0x11
        bytes[17] = 0xDA
        bytes[18] = 0x27
        bytes[27] = 0x06
        bytes[28] = 0x60
        bytes[31] = 0xC0

        val mode = when (state.mode) {
            AcMode.AUTO -> 0b000
            AcMode.DRY -> 0b010
            AcMode.COOL -> 0b011
            AcMode.HEAT -> 0b100
            AcMode.FAN -> 0b110
        }
        val fan = when (state.fanSpeed) {
            FanSpeed.QUIET -> 0b1011
            FanSpeed.LOW -> 3
            FanSpeed.MEDIUM -> 5
            FanSpeed.HIGH -> 7
            FanSpeed.AUTO -> 0b1010
        }
        val power = if (state.isPoweredOn) 1 else 0
        bytes[21] = power or 0x08 or (mode shl 4)
        bytes[22] = state.temperature.coerceIn(16, 30) * 2
        bytes[24] = (fan shl 4) or (if (state.swingMode.hasVertical) 0x0F else 0x00)
        bytes[25] = if (state.swingMode.hasHorizontal) 0x0F else 0x00
        bytes[32] = if (state.isEnergySaving) 0x04 else 0x00

        bytes[7] = sum(bytes, 0, 7)
        bytes[15] = sum(bytes, 8, 15)
        bytes[34] = sum(bytes, 16, 34)
        return bytes
    }

    private fun sum(bytes: IntArray, start: Int, end: Int): Int {
        var total = 0
        for (index in start until end) total += bytes[index]
        return total and 0xFF
    }

    private fun signal(bytes: IntArray): IrSignal {
        val builder = IrPulseBuilder(38000)
        builder.bitsLsbFirst(0, 5, timing)
        builder.footer(timing.mark, timing.zeroSpace + GAP)
        for ((start, end) in SECTIONS) {
            builder.header(3650, 1623)
            builder.bytesLsbFirst(bytes, start, end, timing)
            builder.footer(timing.mark, timing.zeroSpace + GAP)
        }
        return builder.build()
    }

    companion object {
        private const val STATE_LENGTH = 35
        private const val GAP = 29000
        private val SECTIONS = listOf(0 to 8, 8 to 16, 16 to STATE_LENGTH)
    }
}
