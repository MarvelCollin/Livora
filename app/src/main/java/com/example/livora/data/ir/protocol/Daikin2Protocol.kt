package com.example.livora.data.ir.protocol

import com.example.livora.data.ir.ALL_FANS
import com.example.livora.data.ir.ALL_MODES
import com.example.livora.data.ir.AcCapabilities
import com.example.livora.data.ir.AcChange
import com.example.livora.data.ir.AcProtocol
import com.example.livora.data.ir.BitTiming
import com.example.livora.data.ir.IrPulseBuilder
import com.example.livora.data.ir.IrSignal
import com.example.livora.data.ir.VERTICAL_SWINGS
import com.example.livora.data.model.AcMode
import com.example.livora.data.model.AcState
import com.example.livora.data.model.FanSpeed
import java.util.Calendar

class Daikin2Protocol(
    private val minutesOfDay: () -> Int = {
        val now = Calendar.getInstance()
        now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE)
    }
) : AcProtocol {

    override val capabilities = AcCapabilities(
        modes = ALL_MODES,
        fanSpeeds = ALL_FANS,
        swingModes = VERTICAL_SWINGS,
        minTemp = 18,
        maxTemp = 30,
        hasEco = true
    )

    private val timing = BitTiming(mark = 460, oneSpace = 1270, zeroSpace = 420)

    override fun encode(state: AcState, previous: AcState, change: AcChange): List<IrSignal> {
        if (!state.isPoweredOn && change != AcChange.POWER) return emptyList()
        if (change == AcChange.SLEEP || change == AcChange.DISPLAY) return emptyList()
        return listOf(signal(buildState(state)))
    }

    internal fun buildState(state: AcState): IntArray {
        val bytes = IntArray(STATE_LENGTH)
        bytes[0] = 0x11
        bytes[1] = 0xDA
        bytes[2] = 0x27
        bytes[4] = 0x01

        val clock = minutesOfDay().coerceIn(0, 24 * 60 - 1)
        val power = if (state.isPoweredOn) 1 else 0
        bytes[5] = clock and 0xFF
        bytes[6] = ((clock shr 8) and 0x0F) or 0x40 or ((1 - power) shl 7)
        bytes[7] = 0x70
        bytes[8] = 0x28
        bytes[9] = 0x0C
        bytes[10] = 0x80
        bytes[11] = 0x04
        bytes[12] = 0xB0
        bytes[13] = 0x16
        bytes[14] = 0x24
        bytes[17] = 0xBE
        bytes[18] = 0xD0 or (if (state.swingMode.hasVertical) 0xF else 0xE)

        bytes[20] = 0x11
        bytes[21] = 0xDA
        bytes[22] = 0x27
        val mode = when (state.mode) {
            AcMode.AUTO -> 0b000
            AcMode.DRY -> 0b010
            AcMode.COOL -> 0b011
            AcMode.HEAT -> 0b100
            AcMode.FAN -> 0b110
        }
        bytes[25] = power or 0x08 or (mode shl 4)
        val minimum = if (state.mode == AcMode.COOL) 18 else 10
        bytes[26] = state.temperature.coerceIn(minimum, 32) shl 1
        val fan = when (state.fanSpeed) {
            FanSpeed.QUIET -> 0b1011
            FanSpeed.LOW -> 3
            FanSpeed.MEDIUM -> 5
            FanSpeed.HIGH -> 7
            FanSpeed.AUTO -> 0b1010
        }
        bytes[28] = fan shl 4
        bytes[31] = 0x06
        bytes[32] = 0x60
        bytes[35] = 0xC1
        bytes[36] = 0x80 or (if (state.isEnergySaving) 0x04 else 0x00)
        bytes[37] = 0x60

        bytes[19] = sum(bytes, 0, 19)
        bytes[38] = sum(bytes, 20, 38)
        return bytes
    }

    private fun sum(bytes: IntArray, start: Int, end: Int): Int {
        var total = 0
        for (index in start until end) total += bytes[index]
        return total and 0xFF
    }

    private fun signal(bytes: IntArray): IrSignal {
        val builder = IrPulseBuilder(36700)
        builder.header(10024, 25180)
        builder.header(3500, 1728)
        builder.bytesLsbFirst(bytes, 0, 20, timing)
        builder.footer(timing.mark, GAP)
        builder.header(3500, 1728)
        builder.bytesLsbFirst(bytes, 20, STATE_LENGTH, timing)
        builder.footer(timing.mark, GAP)
        return builder.build()
    }

    private companion object {
        const val STATE_LENGTH = 39
        const val GAP = 10024 + 25180
    }
}
