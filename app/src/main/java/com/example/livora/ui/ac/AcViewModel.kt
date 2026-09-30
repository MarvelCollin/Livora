package com.example.livora.ui.ac

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.livora.data.ir.IrBlasterController
import com.example.livora.data.ir.AcIrEncoder
import com.example.livora.data.model.AcMode
import com.example.livora.data.model.AcState
import com.example.livora.data.model.FanSpeed
import com.example.livora.data.model.SwingMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import com.example.livora.util.Logger
import kotlinx.coroutines.launch

class AcViewModel(application: Application) : AndroidViewModel(application) {

    private val irController = IrBlasterController(application)

    private val _acState = MutableStateFlow(AcState())
    val acState: StateFlow<AcState> = _acState.asStateFlow()

    val isIrAvailable: Boolean get() = irController.isAvailable

    init {
        Logger.debug(TAG, "AcViewModel created. isIrAvailable=$isIrAvailable")
    }

    private fun transmitState(state: AcState) {
        Logger.debug(TAG, "transmitState temp=${state.temperature} mode=${state.mode} fan=${state.fanSpeed}")
        viewModelScope.launch(Dispatchers.IO) {
            val (freq, pattern) = AcIrEncoder.encodeState(state)
            irController.transmit(freq, pattern)
        }
    }

    private fun transmitPowerOff() {
        Logger.debug(TAG, "transmitPowerOff POWER OFF")
        viewModelScope.launch(Dispatchers.IO) {
            val (freq, pattern) = AcIrEncoder.encodePowerOff()
            irController.transmit(freq, pattern)
        }
    }

    fun togglePower() {
        _acState.update { it.copy(isPoweredOn = !it.isPoweredOn) }
        val current = _acState.value
        Logger.debug(TAG, "togglePower -> isPoweredOn=${current.isPoweredOn}")
        if (current.isPoweredOn) transmitState(current) else transmitPowerOff()
    }

    fun powerOn() {
        if (!_acState.value.isPoweredOn) {
            _acState.update { it.copy(isPoweredOn = true) }
            transmitState(_acState.value)
        }
    }

    fun powerOff() {
        if (_acState.value.isPoweredOn) {
            _acState.update { it.copy(isPoweredOn = false) }
            transmitPowerOff()
        }
    }

    fun setTemperature(temp: Int) {
        _acState.update { it.copy(temperature = temp.coerceIn(AcState.MIN_TEMP, AcState.MAX_TEMP)) }
        if (_acState.value.isPoweredOn) transmitState(_acState.value)
    }

    fun increaseTemperature() {
        _acState.update { state ->
            if (state.temperature < AcState.MAX_TEMP) state.copy(temperature = state.temperature + 1)
            else state
        }
        Logger.debug(TAG, "increaseTemperature -> temp=${_acState.value.temperature} isPoweredOn=${_acState.value.isPoweredOn}")
        if (_acState.value.isPoweredOn) transmitState(_acState.value)
    }

    fun decreaseTemperature() {
        _acState.update { state ->
            if (state.temperature > AcState.MIN_TEMP) state.copy(temperature = state.temperature - 1)
            else state
        }
        Logger.debug(TAG, "decreaseTemperature -> temp=${_acState.value.temperature} isPoweredOn=${_acState.value.isPoweredOn}")
        if (_acState.value.isPoweredOn) transmitState(_acState.value)
    }

    fun setMode(mode: AcMode) {
        _acState.update { it.copy(mode = mode) }
        Logger.debug(TAG, "setMode -> mode=$mode isPoweredOn=${_acState.value.isPoweredOn}")
        if (_acState.value.isPoweredOn) transmitState(_acState.value)
    }

    fun setFanSpeed(speed: FanSpeed) {
        _acState.update { it.copy(fanSpeed = speed) }
        Logger.debug(TAG, "setFanSpeed -> speed=$speed isPoweredOn=${_acState.value.isPoweredOn}")
        if (_acState.value.isPoweredOn) transmitState(_acState.value)
    }

    fun setSwingMode(swing: SwingMode) {
        _acState.update { it.copy(swingMode = swing) }
    }

    fun toggleSleepMode() {
        _acState.update { it.copy(isSleepMode = !it.isSleepMode) }
    }

    fun toggleEnergySaving() {
        _acState.update { it.copy(isEnergySaving = !it.isEnergySaving) }
    }

    fun toggleDisplay() {
        _acState.update { it.copy(isDisplayOn = !it.isDisplayOn) }
    }

    fun setTimer(hours: Int) {
        _acState.update { it.copy(timerHours = hours.coerceIn(0, 24)) }
    }

    fun increaseTimer() {
        _acState.update { state ->
            if (state.timerHours < 24) state.copy(timerHours = state.timerHours + 1)
            else state
        }
    }

    fun decreaseTimer() {
        _acState.update { state ->
            if (state.timerHours > 0) state.copy(timerHours = state.timerHours - 1)
            else state
        }
    }

    companion object {
        private const val TAG = "Livora.AcViewModel"
    }
}
