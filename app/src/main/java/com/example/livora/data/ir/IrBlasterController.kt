package com.example.livora.data.ir

import android.content.Context
import android.hardware.ConsumerIrManager
import com.example.livora.util.Logger

class IrBlasterController(context: Context) {

    private val irManager: ConsumerIrManager? =
        context.getSystemService(Context.CONSUMER_IR_SERVICE) as? ConsumerIrManager

    val isAvailable: Boolean
        get() = irManager?.hasIrEmitter() == true

    init {
        Logger.debug(TAG, "ConsumerIrManager resolved: ${irManager != null}")
        Logger.debug(TAG, "hasIrEmitter: ${irManager?.hasIrEmitter()}")
        irManager?.carrierFrequencies?.forEach { range ->
            Logger.debug(TAG, "Supported frequency range: ${range.minFrequency} Hz - ${range.maxFrequency} Hz")
        }
    }

    fun transmit(signals: List<IrSignal>): Boolean {
        if (!isAvailable) {
            Logger.debug(TAG, "SKIPPED, IR emitter not available")
            return false
        }
        var sent = true
        signals.forEachIndexed { index, signal ->
            sent = transmit(signal) && sent
            if (index < signals.lastIndex) {
                Thread.sleep(signal.durationMicros / 1000 + INTER_SIGNAL_GAP_MILLIS)
            }
        }
        return sent
    }

    private fun transmit(signal: IrSignal): Boolean {
        Logger.debug(TAG, "transmit frequency=${signal.frequency} pulses=${signal.pattern.size}")
        return try {
            irManager?.transmit(signal.frequency, signal.pattern)
            true
        } catch (e: Exception) {
            Logger.debug(TAG, "transmit FAILED: ${e.message}")
            false
        }
    }

    companion object {
        private const val TAG = "Livora.IrBlaster"
        private const val INTER_SIGNAL_GAP_MILLIS = 120L
    }
}
