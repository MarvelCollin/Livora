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
import com.example.livora.data.ir.reverseByte
import com.example.livora.data.model.AcMode
import com.example.livora.data.model.AcState
import com.example.livora.data.model.FanSpeed

class MideaProtocol : AcProtocol {

    override val capabilities = AcCapabilities(
        modes = ALL_MODES,
        fanSpeeds = BASIC_FANS,
        swingModes = VERTICAL_SWINGS,
        minTemp = 17,
        maxTemp = 30,
        hasSleep = true,
        hasEco = true,
        hasDisplay = true
    )

    private val timing = BitTiming(mark = 560, oneSpace = 1680, zeroSpace = 560)

    override fun encode(state: AcState, previous: AcState, change: AcChange): List<IrSignal> {
        if (!state.isPoweredOn && change != AcChange.POWER) return emptyList()
        return when (change) {
            AcChange.POWER, AcChange.TEMPERATURE, AcChange.MODE, AcChange.FAN, AcChange.SLEEP ->
                listOf(signal(stateCode(state)))
            AcChange.SWING -> listOf(signal(SWING_TOGGLE))
            AcChange.ECO -> listOf(signal(ECO_TOGGLE))
            AcChange.DISPLAY -> listOf(signal(LIGHT_TOGGLE))
        }
    }

    private fun stateCode(state: AcState): Long {
        val mode = when (state.mode) {
            AcMode.COOL -> 0
            AcMode.DRY -> 1
            AcMode.AUTO -> 2
            AcMode.HEAT -> 3
            AcMode.FAN -> 4
        }
        val fan = when (state.fanSpeed) {
            FanSpeed.AUTO -> 0
            FanSpeed.QUIET, FanSpeed.LOW -> 1
            FanSpeed.MEDIUM -> 2
            FanSpeed.HIGH -> 3
        }
        val power = if (state.isPoweredOn) 1 else 0
        val sleep = if (state.isSleepMode) 1 else 0
        val modeByte = mode or (fan shl 3) or (sleep shl 6) or (power shl 7)
        val temperatureByte = state.temperature.coerceIn(17, 30) - 17
        val body = (0xA1L shl 40) or
            (modeByte.toLong() shl 32) or
            (temperatureByte.toLong() shl 24) or
            (0xFFL shl 16) or
            (0xFFL shl 8)
        return body or checksum(body).toLong()
    }

    internal fun checksum(code: Long): Int {
        var sum = 0
        for (index in 1..5) {
            sum += ((code shr (index * 8)) and 0xFFL).toInt().reverseByte()
        }
        return ((256 - sum) and 0xFF).reverseByte()
    }

    private fun signal(code: Long): IrSignal {
        val mask = 0xFFFFFFFFFFFFL
        val builder = IrPulseBuilder(38000)
        for (payload in longArrayOf(code, code.inv() and mask)) {
            builder.header(4480, 4480)
            builder.bitsMsbFirst(payload, 48, timing)
            builder.footer(timing.mark, 5600)
        }
        return builder.build()
    }

    companion object {
        private const val SWING_TOGGLE = 0xA201FFFFFF7CL
        private const val ECO_TOGGLE = 0xA202FFFFFF7EL
        private const val LIGHT_TOGGLE = 0xA208FFFFFF75L
    }
}
