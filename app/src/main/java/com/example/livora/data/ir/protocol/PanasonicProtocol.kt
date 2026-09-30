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
import com.example.livora.data.ir.VERTICAL_SWINGS
import com.example.livora.data.model.AcMode
import com.example.livora.data.model.AcState
import com.example.livora.data.model.FanSpeed

enum class PanasonicVariant {
    JKE, DKE, NKE, LKE
}

class PanasonicProtocol(private val variant: PanasonicVariant) : AcProtocol {

    override val capabilities = AcCapabilities(
        modes = ALL_MODES,
        fanSpeeds = ALL_FANS,
        swingModes = if (variant == PanasonicVariant.DKE) ALL_SWINGS else VERTICAL_SWINGS,
        minTemp = 16,
        maxTemp = 30
    )

    private val timing = BitTiming(mark = 432, oneSpace = 1296, zeroSpace = 432)

    override fun encode(state: AcState, previous: AcState, change: AcChange): List<IrSignal> {
        if (!state.isPoweredOn && change != AcChange.POWER) return emptyList()
        if (change == AcChange.SLEEP || change == AcChange.ECO || change == AcChange.DISPLAY) {
            return emptyList()
        }
        return listOf(signal(buildState(state)))
    }

    internal fun buildState(state: AcState): IntArray {
        val bytes = KNOWN_GOOD_STATE.copyOf()
        bytes[13] = bytes[13] and 0xF0
        bytes[17] = 0x00
        bytes[21] = bytes[21] and 0xEF
        bytes[23] = 0x81
        bytes[25] = 0x00
        when (variant) {
            PanasonicVariant.LKE -> {
                bytes[13] = bytes[13] or 0x02
                bytes[17] = 0x06
            }
            PanasonicVariant.DKE -> {
                bytes[23] = 0x01
                bytes[25] = 0x06
            }
            PanasonicVariant.NKE -> bytes[17] = 0x06
            PanasonicVariant.JKE -> Unit
        }

        val mode = when (state.mode) {
            AcMode.AUTO -> 0
            AcMode.DRY -> 2
            AcMode.COOL -> 3
            AcMode.HEAT -> 4
            AcMode.FAN -> 6
        }
        val temperature = if (state.mode == AcMode.FAN) 27 else state.temperature.coerceIn(16, 30)
        val power = if (state.isPoweredOn) 1 else 0
        bytes[13] = (bytes[13] and 0x0F) or (mode shl 4)
        bytes[13] = (bytes[13] and 0xFE) or power
        bytes[14] = (bytes[14] and 0xC1) or (temperature shl 1)

        val fan = when (state.fanSpeed) {
            FanSpeed.QUIET -> 0
            FanSpeed.LOW -> 1
            FanSpeed.MEDIUM -> 2
            FanSpeed.HIGH -> 4
            FanSpeed.AUTO -> 7
        }
        val verticalPosition = if (state.swingMode.hasVertical) 0xF else 0x3
        bytes[16] = ((fan + 3) shl 4) or verticalPosition

        val horizontalPosition = when (variant) {
            PanasonicVariant.DKE -> if (state.swingMode.hasHorizontal) 0xD else 0x6
            PanasonicVariant.NKE, PanasonicVariant.LKE -> 0x6
            PanasonicVariant.JKE -> null
        }
        if (horizontalPosition != null) bytes[17] = (bytes[17] and 0xF0) or horizontalPosition

        var checksum = 0xF4
        for (index in 0 until 26) checksum += bytes[index]
        bytes[26] = checksum and 0xFF
        return bytes
    }

    private fun signal(bytes: IntArray): IrSignal {
        val builder = IrPulseBuilder(36700)
        builder.header(3456, 1728)
        builder.bytesLsbFirst(bytes, 0, 8, timing)
        builder.footer(timing.mark, 10000)
        builder.header(3456, 1728)
        builder.bytesLsbFirst(bytes, 8, bytes.size, timing)
        builder.footer(timing.mark, 100000)
        return builder.build()
    }

    companion object {
        private val KNOWN_GOOD_STATE = intArrayOf(
            0x02, 0x20, 0xE0, 0x04, 0x00, 0x00, 0x00, 0x06, 0x02,
            0x20, 0xE0, 0x04, 0x00, 0x00, 0x00, 0x80, 0x00, 0x00,
            0x00, 0x0E, 0xE0, 0x00, 0x00, 0x81, 0x00, 0x00, 0x00
        )
    }
}
