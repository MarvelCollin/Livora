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

class Daikin216Protocol : AcProtocol {

    override val capabilities = AcCapabilities(
        modes = ALL_MODES,
        fanSpeeds = ALL_FANS,
        swingModes = ALL_SWINGS,
        minTemp = 16,
        maxTemp = 30
    )

    private val timing = BitTiming(mark = 420, oneSpace = 1300, zeroSpace = 450)

    override fun encode(state: AcState, previous: AcState, change: AcChange): List<IrSignal> {
        if (!state.isPoweredOn && change != AcChange.POWER) return emptyList()
        if (change == AcChange.SLEEP || change == AcChange.ECO || change == AcChange.DISPLAY) {
            return emptyList()
        }
        return listOf(signal(buildState(state)))
    }

    internal fun buildState(state: AcState): IntArray {
        val bytes = IntArray(STATE_LENGTH)
        bytes[0] = 0x11
        bytes[1] = 0xDA
        bytes[2] = 0x27
        bytes[3] = 0xF0
        bytes[8] = 0x11
        bytes[9] = 0xDA
        bytes[10] = 0x27

        val mode = when (state.mode) {
            AcMode.AUTO -> 0b000
            AcMode.DRY -> 0b010
            AcMode.COOL -> 0b011
            AcMode.HEAT -> 0b100
            AcMode.FAN -> 0b110
        }
        val power = if (state.isPoweredOn) 1 else 0
        bytes[13] = power or (mode shl 4)
        bytes[14] = state.temperature.coerceIn(10, 32) shl 1
        val fan = when (state.fanSpeed) {
            FanSpeed.QUIET -> 0b1011
            FanSpeed.LOW -> 3
            FanSpeed.MEDIUM -> 5
            FanSpeed.HIGH -> 7
            FanSpeed.AUTO -> 0b1010
        }
        bytes[16] = (fan shl 4) or (if (state.swingMode.hasVertical) 0x0F else 0x00)
        bytes[17] = if (state.swingMode.hasHorizontal) 0x0F else 0x00
        bytes[23] = 0xC0

        bytes[7] = sum(bytes, 0, 7)
        bytes[26] = sum(bytes, 8, 26)
        return bytes
    }

    private fun sum(bytes: IntArray, start: Int, end: Int): Int {
        var total = 0
        for (index in start until end) total += bytes[index]
        return total and 0xFF
    }

    private fun signal(bytes: IntArray): IrSignal {
        val builder = IrPulseBuilder(38000)
        builder.header(3440, 1750)
        builder.bytesLsbFirst(bytes, 0, 8, timing)
        builder.footer(timing.mark, GAP)
        builder.header(3440, 1750)
        builder.bytesLsbFirst(bytes, 8, STATE_LENGTH, timing)
        builder.footer(timing.mark, GAP)
        return builder.build()
    }

    private companion object {
        const val STATE_LENGTH = 27
        const val GAP = 29650
    }
}
