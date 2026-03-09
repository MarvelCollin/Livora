package com.example.livora.data.ir

import android.content.Context
import android.hardware.ConsumerIrManager

class IrBlasterController(context: Context) {

    private val irManager: ConsumerIrManager? =
        context.getSystemService(Context.CONSUMER_IR_SERVICE) as? ConsumerIrManager

    val isAvailable: Boolean
        get() = irManager?.hasIrEmitter() == true

    fun transmit(frequency: Int, pattern: IntArray) {
        if (isAvailable) {
            irManager?.transmit(frequency, pattern)
        }
    }
}
