package com.example.livora.ui.ac

import android.app.Application
import android.content.SharedPreferences
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.livora.data.ir.AcBrand
import com.example.livora.data.ir.AcBrands
import com.example.livora.data.ir.AcCapabilities
import com.example.livora.data.ir.AcChange
import com.example.livora.data.ir.AcProtocol
import com.example.livora.data.ir.AcRemote
import com.example.livora.data.ir.AcSettingsStore
import com.example.livora.data.ir.AcTimer
import com.example.livora.data.ir.IrBlasterController
import com.example.livora.data.ir.IrSignal
import com.example.livora.data.model.AcMode
import com.example.livora.data.model.AcState
import com.example.livora.data.model.FanSpeed
import com.example.livora.data.model.SwingMode
import com.example.livora.util.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class AcViewModel(application: Application) : AndroidViewModel(application) {

    private val irController = IrBlasterController(application)
    private val store = AcSettingsStore(application)
    private val transmitQueue = Channel<List<IrSignal>>(Channel.UNLIMITED)

    private var protocol: AcProtocol

    private val _remote = MutableStateFlow(store.loadRemote())
    val remote: StateFlow<AcRemote> = _remote.asStateFlow()

    private val _capabilities: MutableStateFlow<AcCapabilities>
    val capabilities: StateFlow<AcCapabilities>

    private val _acState: MutableStateFlow<AcState>
    val acState: StateFlow<AcState>

    private val _transmitFailed = MutableStateFlow(false)
    val transmitFailed: StateFlow<Boolean> = _transmitFailed.asStateFlow()

    val isIrAvailable: Boolean get() = irController.isAvailable

    val currentBrand: AcBrand get() = AcBrands.find(_remote.value.brandId)

    private val storeListener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
        val stored = loadStoredState()
        if (stored != _acState.value) _acState.value = stored
    }

    init {
        protocol = createProtocol(_remote.value)
        _capabilities = MutableStateFlow(protocol.capabilities)
        capabilities = _capabilities.asStateFlow()
        _acState = MutableStateFlow(loadStoredState())
        acState = _acState.asStateFlow()

        viewModelScope.launch(Dispatchers.IO) {
            for (signals in transmitQueue) {
                _transmitFailed.value = !irController.transmit(signals)
            }
        }
        store.registerChangeListener(storeListener)
        Logger.debug(TAG, "AcViewModel created. isIrAvailable=$isIrAvailable brand=${_remote.value.brandId}")
    }

    override fun onCleared() {
        store.unregisterChangeListener(storeListener)
        transmitQueue.close()
        super.onCleared()
    }

    fun selectRemote(brandId: String, modelIndex: Int) {
        val remote = AcRemote(brandId, modelIndex)
        store.saveRemote(remote)
        _remote.value = remote
        protocol = createProtocol(remote)
        _capabilities.value = protocol.capabilities
        val sanitized = protocol.capabilities.sanitize(_acState.value)
        _acState.value = sanitized
        store.saveState(sanitized)
    }

    fun sendTestSignal(brandId: String, modelIndex: Int) {
        val testProtocol = AcBrands.find(brandId).createProtocol(modelIndex)
        val testState = testProtocol.capabilities.sanitize(
            AcState(isPoweredOn = true, temperature = 20, mode = AcMode.COOL, fanSpeed = FanSpeed.AUTO)
        )
        transmitQueue.trySend(testProtocol.encode(testState, AcState(isPoweredOn = false), AcChange.POWER))
    }

    fun togglePower() {
        setPower(!_acState.value.isPoweredOn)
    }

    fun powerOn() = setPower(true)

    fun powerOff() = setPower(false)

    fun applyScene(temperature: Int, mode: AcMode) {
        commitChange(AcChange.POWER) {
            it.copy(isPoweredOn = true, temperature = temperature, mode = mode)
        }
    }

    fun setTemperature(temp: Int) {
        commitChange(AcChange.TEMPERATURE) { it.copy(temperature = temp) }
    }

    fun increaseTemperature() {
        commitChange(AcChange.TEMPERATURE) { it.copy(temperature = it.temperature + 1) }
    }

    fun decreaseTemperature() {
        commitChange(AcChange.TEMPERATURE) { it.copy(temperature = it.temperature - 1) }
    }

    fun setMode(mode: AcMode) {
        commitChange(AcChange.MODE) { it.copy(mode = mode) }
    }

    fun setFanSpeed(speed: FanSpeed) {
        commitChange(AcChange.FAN) { it.copy(fanSpeed = speed) }
    }

    fun setSwingMode(swing: SwingMode) {
        commitChange(AcChange.SWING) { it.copy(swingMode = swing) }
    }

    fun toggleSleepMode() {
        commitChange(AcChange.SLEEP) { it.copy(isSleepMode = !it.isSleepMode) }
    }

    fun toggleEnergySaving() {
        commitChange(AcChange.ECO) { it.copy(isEnergySaving = !it.isEnergySaving) }
    }

    fun toggleDisplay() {
        commitChange(AcChange.DISPLAY) { it.copy(isDisplayOn = !it.isDisplayOn) }
    }

    fun setTimerHours(hours: Int) {
        val context = getApplication<Application>()
        val clamped = hours.coerceIn(0, MAX_TIMER_HOURS)
        val endsAt = if (clamped == 0) 0L else System.currentTimeMillis() + clamped * HOUR_MILLIS
        val next = _acState.value.copy(timerEndsAtMillis = endsAt)
        _acState.value = next
        store.saveState(next)
        if (endsAt == 0L) AcTimer.cancel(context) else AcTimer.schedule(context, endsAt)
    }

    private fun setPower(on: Boolean) {
        if (!on) clearTimer()
        commitChange(AcChange.POWER) {
            it.copy(
                isPoweredOn = on,
                temperature = if (on && !it.isPoweredOn) DEFAULT_ON_TEMPERATURE else it.temperature,
                timerEndsAtMillis = if (on) it.timerEndsAtMillis else 0L
            )
        }
    }

    private fun clearTimer() {
        if (_acState.value.timerEndsAtMillis > 0L) AcTimer.cancel(getApplication())
    }

    private fun commitChange(change: AcChange, transform: (AcState) -> AcState) {
        val previous = _acState.value
        val next = _capabilities.value.sanitize(transform(previous))
        val isToggle = change == AcChange.SWING || change == AcChange.SLEEP ||
            change == AcChange.ECO || change == AcChange.DISPLAY
        if (isToggle && next == previous) return
        _acState.value = next
        store.saveState(next)
        if (!next.isPoweredOn && change != AcChange.POWER) return
        val signals = protocol.encode(next, previous, change)
        Logger.debug(TAG, "apply change=$change signals=${signals.size} state=$next")
        if (signals.isNotEmpty()) transmitQueue.trySend(signals)
    }

    private fun loadStoredState(): AcState {
        val stored = _capabilities.value.sanitize(store.loadState())
        return if (stored.timerEndsAtMillis in 1..System.currentTimeMillis()) {
            stored.copy(timerEndsAtMillis = 0L)
        } else {
            stored
        }
    }

    private fun createProtocol(remote: AcRemote): AcProtocol =
        AcBrands.find(remote.brandId).createProtocol(remote.modelIndex)

    companion object {
        private const val TAG = "Livora.AcViewModel"
        private const val MAX_TIMER_HOURS = 12
        private const val DEFAULT_ON_TEMPERATURE = 20
        private const val HOUR_MILLIS = 3_600_000L
    }
}
