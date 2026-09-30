package com.example.livora.data.ir.protocol

import com.example.livora.data.ir.ALL_MODES
import com.example.livora.data.ir.AcCapabilities
import com.example.livora.data.ir.AcChange
import com.example.livora.data.ir.AcProtocol
import com.example.livora.data.ir.BASIC_FANS
import com.example.livora.data.ir.BitTiming
import com.example.livora.data.ir.IrPulseBuilder
import com.example.livora.data.ir.IrSignal
import com.example.livora.data.ir.VERTICAL_SWINGS
import com.example.livora.data.model.AcMode
import com.example.livora.data.model.AcState
import com.example.livora.data.model.FanSpeed

class CoolixProtocol : AcProtocol {

    override val capabilities = AcCapabilities(
        modes = ALL_MODES,
        fanSpeeds = BASIC_FANS,
        swingModes = VERTICAL_SWINGS,
        minTemp = 17,
        maxTemp = 30,
        hasSleep = true,
        hasDisplay = true
    )

    private val timing = BitTiming(mark = 552, oneSpace = 1656, zeroSpace = 552)

    override fun encode(state: AcState, previous: AcState, change: AcChange): List<IrSignal> {
        if (!state.isPoweredOn) {
            return if (change == AcChange.POWER) listOf(signal(OFF)) else emptyList()
        }
        return when (change) {
            AcChange.POWER, AcChange.TEMPERATURE, AcChange.MODE, AcChange.FAN ->
                listOf(signal(stateCode(state)))
            AcChange.SWING -> listOf(signal(SWING_TOGGLE))
            AcChange.SLEEP -> listOf(signal(SLEEP_TOGGLE))
            AcChange.DISPLAY -> listOf(signal(LED_TOGGLE))
            AcChange.ECO -> emptyList()
        }
    }

    private fun stateCode(state: AcState): Long {
        val fanMode = state.mode == AcMode.FAN
        val modeBits = when (state.mode) {
            AcMode.COOL -> 0
            AcMode.DRY, AcMode.FAN -> 1
            AcMode.AUTO -> 2
            AcMode.HEAT -> 3
        }
        val temperatureBits = if (fanMode) {
            FAN_TEMPERATURE_CODE
        } else {
            TEMPERATURE_CODES[state.temperature.coerceIn(17, 30) - 17]
        }
        val quietMode = state.mode == AcMode.AUTO || state.mode == AcMode.DRY
        val fanBits = when (state.fanSpeed) {
            FanSpeed.AUTO -> if (quietMode) 0b000 else 0b101
            FanSpeed.QUIET, FanSpeed.LOW -> 0b100
            FanSpeed.MEDIUM -> 0b010
            FanSpeed.HIGH -> 0b001
        }
        val low = (temperatureBits shl 4) or (modeBits shl 2)
        val middle = SENSOR_IGNORED or (fanBits shl 5)
        return (SIGNATURE shl 16) or (middle.toLong() shl 8) or low.toLong()
    }

    private fun signal(code: Long): IrSignal {
        val builder = IrPulseBuilder(38000)
        repeat(2) {
            builder.header(4692, 4416)
            for (shift in intArrayOf(16, 8, 0)) {
                val value = (code shr shift) and 0xFFL
                builder.bitsMsbFirst(value, 8, timing)
                builder.bitsMsbFirst(value xor 0xFFL, 8, timing)
            }
            builder.footer(timing.mark, 5244)
        }
        return builder.build()
    }

    companion object {
        private const val SIGNATURE = 0xB2L
        private const val SENSOR_IGNORED = 0x1F
        private const val FAN_TEMPERATURE_CODE = 0b1110
        private const val OFF = 0xB27BE0L
        private const val SWING_TOGGLE = 0xB26BE0L
        private const val SLEEP_TOGGLE = 0xB2E003L
        private const val LED_TOGGLE = 0xB5F5A5L
        private val TEMPERATURE_CODES = intArrayOf(
            0b0000, 0b0001, 0b0011, 0b0010, 0b0110, 0b0111, 0b0101,
            0b0100, 0b1100, 0b1101, 0b1001, 0b1000, 0b1010, 0b1011
        )
    }
}
