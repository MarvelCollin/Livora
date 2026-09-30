package com.example.livora.data.ir.protocol

import com.example.livora.data.ir.AcCapabilities
import com.example.livora.data.ir.AcChange
import com.example.livora.data.ir.AcProtocol
import com.example.livora.data.ir.BASIC_FANS
import com.example.livora.data.ir.BitTiming
import com.example.livora.data.ir.ByteFrame
import com.example.livora.data.ir.IrSignal
import com.example.livora.data.ir.VERTICAL_SWINGS
import com.example.livora.data.model.AcMode
import com.example.livora.data.model.AcState
import com.example.livora.data.model.FanSpeed

enum class SharpVariant {
    A907, A705
}

class SharpProtocol(private val variant: SharpVariant) : AcProtocol {

    override val capabilities = AcCapabilities(
        modes = if (variant == SharpVariant.A907) {
            listOf(AcMode.COOL, AcMode.HEAT, AcMode.DRY, AcMode.AUTO)
        } else {
            listOf(AcMode.COOL, AcMode.DRY, AcMode.AUTO)
        },
        fanSpeeds = BASIC_FANS,
        swingModes = VERTICAL_SWINGS,
        minTemp = 15,
        maxTemp = 30
    )

    private val frame = ByteFrame(
        frequency = 38000,
        headerMark = 3800,
        headerSpace = 1900,
        timing = BitTiming(mark = 470, oneSpace = 1400, zeroSpace = 500),
        footerMark = 470,
        gap = 100000
    )

    override fun encode(state: AcState, previous: AcState, change: AcChange): List<IrSignal> {
        if (!state.isPoweredOn && change != AcChange.POWER) return emptyList()
        if (change == AcChange.SLEEP || change == AcChange.ECO || change == AcChange.DISPLAY) {
            return emptyList()
        }
        return listOf(frame.encode(buildState(state, previous, change)))
    }

    internal fun buildState(state: AcState, previous: AcState, change: AcChange): IntArray {
        val bytes = IntArray(STATE_LENGTH)
        bytes[0] = 0xAA
        bytes[1] = 0x5A
        bytes[2] = 0xCF
        bytes[3] = 0x10

        val mode = when (state.mode) {
            AcMode.AUTO, AcMode.FAN -> 0b00
            AcMode.HEAT -> if (variant == SharpVariant.A907) 0b01 else 0b00
            AcMode.COOL -> 0b10
            AcMode.DRY -> 0b11
        }
        val fixedTemperature = mode == 0b00 || mode == 0b11
        val fan = if (fixedTemperature) {
            0b010
        } else {
            when (state.fanSpeed) {
                FanSpeed.AUTO -> 0b010
                FanSpeed.QUIET, FanSpeed.LOW -> 0b100
                FanSpeed.MEDIUM -> 0b011
                FanSpeed.HIGH -> 0b111
            }
        }

        bytes[4] = if (fixedTemperature) {
            0
        } else {
            (if (variant == SharpVariant.A705) 0xD0 else 0xC0) or (state.temperature.coerceIn(15, 30) - 15)
        }

        val powerSpecial = when {
            change == AcChange.POWER -> when {
                !state.isPoweredOn -> POWER_OFF
                previous.isPoweredOn -> POWER_ON
                else -> POWER_ON_FROM_OFF
            }
            else -> POWER_ON
        }
        bytes[5] = (powerSpecial shl 4) or 0x01
        bytes[6] = mode or (fan shl 4)
        bytes[7] = 0x00
        bytes[8] = 0x08

        var special = SPECIAL_POWER
        if (change == AcChange.SWING) {
            special = SPECIAL_SWING
            bytes[8] = bytes[8] or if (state.swingMode.hasVertical) SWING_TOGGLE else SWING_OFF
        } else if (change == AcChange.FAN) {
            special = SPECIAL_FAN
        } else if (change == AcChange.TEMPERATURE && !fixedTemperature) {
            special = SPECIAL_TEMPERATURE
        }
        bytes[9] = 0x80
        bytes[10] = special
        bytes[11] = if (variant == SharpVariant.A705) 0xF0 else 0xE0
        bytes[12] = 0x01

        var xor = 0
        for (index in 0 until STATE_LENGTH - 1) xor = xor xor bytes[index]
        xor = xor xor (bytes[12] and 0x0F)
        xor = xor xor ((xor shr 4) and 0x0F)
        bytes[12] = (bytes[12] and 0x0F) or ((xor and 0x0F) shl 4)
        return bytes
    }

    private companion object {
        const val STATE_LENGTH = 13
        const val POWER_ON = 3
        const val POWER_OFF = 2
        const val POWER_ON_FROM_OFF = 1
        const val SPECIAL_POWER = 0x00
        const val SPECIAL_TEMPERATURE = 0x04
        const val SPECIAL_FAN = 0x05
        const val SPECIAL_SWING = 0x06
        const val SWING_OFF = 0b010
        const val SWING_TOGGLE = 0b111
    }
}
