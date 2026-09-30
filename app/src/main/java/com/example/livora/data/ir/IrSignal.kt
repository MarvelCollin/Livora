package com.example.livora.data.ir

class IrSignal(val frequency: Int, val pattern: IntArray) {
    val durationMicros: Long
        get() = pattern.fold(0L) { total, value -> total + value }
}

class BitTiming(val mark: Int, val oneSpace: Int, val zeroSpace: Int)

class IrPulseBuilder(private val frequency: Int) {

    private val pulses = ArrayList<Int>()
    private var expectMark = true

    fun mark(micros: Int): IrPulseBuilder {
        if (micros <= 0) return this
        if (expectMark) {
            pulses.add(micros)
            expectMark = false
        } else {
            pulses[pulses.lastIndex] = pulses.last() + micros
        }
        return this
    }

    fun space(micros: Int): IrPulseBuilder {
        if (micros <= 0 || pulses.isEmpty()) return this
        if (!expectMark) {
            pulses.add(micros)
            expectMark = true
        } else {
            pulses[pulses.lastIndex] = pulses.last() + micros
        }
        return this
    }

    fun header(markMicros: Int, spaceMicros: Int): IrPulseBuilder {
        mark(markMicros)
        return space(spaceMicros)
    }

    fun bitsMsbFirst(value: Long, count: Int, timing: BitTiming): IrPulseBuilder {
        for (index in count - 1 downTo 0) {
            bit((value shr index) and 1L == 1L, timing)
        }
        return this
    }

    fun bitsLsbFirst(value: Long, count: Int, timing: BitTiming): IrPulseBuilder {
        for (index in 0 until count) {
            bit((value shr index) and 1L == 1L, timing)
        }
        return this
    }

    fun bytesLsbFirst(bytes: IntArray, from: Int, to: Int, timing: BitTiming): IrPulseBuilder {
        for (index in from until to) {
            bitsLsbFirst(bytes[index].toLong() and 0xFFL, 8, timing)
        }
        return this
    }

    fun bytesMsbFirst(bytes: IntArray, from: Int, to: Int, timing: BitTiming): IrPulseBuilder {
        for (index in from until to) {
            bitsMsbFirst(bytes[index].toLong() and 0xFFL, 8, timing)
        }
        return this
    }

    fun footer(markMicros: Int, gapMicros: Int): IrPulseBuilder {
        mark(markMicros)
        return space(gapMicros)
    }

    fun build(): IrSignal {
        val result = ArrayList(pulses)
        if (result.size % 2 == 0 && result.isNotEmpty()) result.removeAt(result.lastIndex)
        return IrSignal(frequency, result.toIntArray())
    }

    private fun bit(one: Boolean, timing: BitTiming) {
        mark(timing.mark)
        space(if (one) timing.oneSpace else timing.zeroSpace)
    }
}

internal fun Int.reverseByte(): Int {
    var value = this and 0xFF
    var result = 0
    repeat(8) {
        result = (result shl 1) or (value and 1)
        value = value shr 1
    }
    return result
}
