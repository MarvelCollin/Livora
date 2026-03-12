package com.example.livora.ui.bulb

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.livora.data.model.WizBulb
import com.example.livora.data.model.WizBulbState
import com.example.livora.data.model.WizScene
import com.example.livora.data.network.WizBulbController
import com.example.livora.util.LivoraLogger
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class BulbViewModel(application: Application) : AndroidViewModel(application) {

    private val wizController = WizBulbController()
    private val prefs = application.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _discoveredBulbs = MutableStateFlow<List<WizBulb>>(emptyList())
    val discoveredBulbs: StateFlow<List<WizBulb>> = _discoveredBulbs.asStateFlow()

    private val _connectedBulb = MutableStateFlow<WizBulb?>(null)
    val connectedBulb: StateFlow<WizBulb?> = _connectedBulb.asStateFlow()

    private val _bulbState = MutableStateFlow(WizBulbState())
    val bulbState: StateFlow<WizBulbState> = _bulbState.asStateFlow()

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    init {
        loadSavedBulb()
    }

    private fun loadSavedBulb() {
        val ip = prefs.getString(KEY_BULB_IP, null)
        val mac = prefs.getString(KEY_BULB_MAC, null)
        val moduleName = prefs.getString(KEY_BULB_MODULE, "") ?: ""
        if (ip != null && mac != null) {
            val bulb = WizBulb(ip = ip, mac = mac, moduleName = moduleName)
            _connectedBulb.value = bulb
            LivoraLogger.debug(TAG, "Loaded saved bulb ${bulb.mac} at ${bulb.ip}")
            refreshBulbState()
        }
    }

    private fun saveBulb(bulb: WizBulb) {
        prefs.edit()
            .putString(KEY_BULB_IP, bulb.ip)
            .putString(KEY_BULB_MAC, bulb.mac)
            .putString(KEY_BULB_MODULE, bulb.moduleName)
            .apply()
    }

    private fun clearSavedBulb() {
        prefs.edit()
            .remove(KEY_BULB_IP)
            .remove(KEY_BULB_MAC)
            .remove(KEY_BULB_MODULE)
            .apply()
    }

    fun scanForBulbs() {
        _isScanning.value = true
        viewModelScope.launch {
            val bulbs = wizController.discoverBulbs()
            _discoveredBulbs.value = bulbs
            _isScanning.value = false
            LivoraLogger.debug(TAG, "Scan complete, found ${bulbs.size} bulb(s)")
        }
    }

    fun connectToBulb(bulb: WizBulb) {
        _connectedBulb.value = bulb
        saveBulb(bulb)
        LivoraLogger.debug(TAG, "Connected to bulb ${bulb.mac} at ${bulb.ip}")
        refreshBulbState()
    }

    fun disconnectBulb() {
        _connectedBulb.value = null
        _bulbState.value = WizBulbState()
        clearSavedBulb()
        LivoraLogger.debug(TAG, "Disconnected from bulb")
    }

    fun refreshBulbState() {
        val bulb = _connectedBulb.value ?: return
        viewModelScope.launch {
            val state = wizController.getBulbState(bulb.ip)
            if (state != null) {
                _bulbState.value = state
                LivoraLogger.debug(TAG, "Refreshed state: on=${state.isPoweredOn} brightness=${state.brightness}")
            }
        }
    }

    fun togglePower() {
        val bulb = _connectedBulb.value ?: return
        val newPower = !_bulbState.value.isPoweredOn
        _bulbState.update { it.copy(isPoweredOn = newPower) }
        viewModelScope.launch {
            wizController.setPower(bulb.ip, newPower)
        }
    }

    fun powerOn() {
        val bulb = _connectedBulb.value ?: return
        if (!_bulbState.value.isPoweredOn) {
            _bulbState.update { it.copy(isPoweredOn = true) }
            viewModelScope.launch {
                wizController.setPower(bulb.ip, true)
            }
        }
    }

    fun powerOff() {
        val bulb = _connectedBulb.value ?: return
        if (_bulbState.value.isPoweredOn) {
            _bulbState.update { it.copy(isPoweredOn = false) }
            viewModelScope.launch {
                wizController.setPower(bulb.ip, false)
            }
        }
    }

    fun setBrightness(brightness: Int) {
        val bulb = _connectedBulb.value ?: return
        val clamped = brightness.coerceIn(WizBulbState.MIN_BRIGHTNESS, WizBulbState.MAX_BRIGHTNESS)
        _bulbState.update { it.copy(brightness = clamped, isPoweredOn = true) }
        viewModelScope.launch {
            wizController.setBrightness(bulb.ip, clamped)
        }
    }

    fun increaseBrightness() {
        val current = _bulbState.value.brightness
        if (current < WizBulbState.MAX_BRIGHTNESS) {
            setBrightness(current + 10)
        }
    }

    fun decreaseBrightness() {
        val current = _bulbState.value.brightness
        if (current > WizBulbState.MIN_BRIGHTNESS) {
            setBrightness(current - 10)
        }
    }

    fun setColorTemperature(temp: Int) {
        val bulb = _connectedBulb.value ?: return
        val clamped = temp.coerceIn(WizBulbState.MIN_COLOR_TEMP, WizBulbState.MAX_COLOR_TEMP)
        _bulbState.update { it.copy(colorTemp = clamped, useRgb = false, sceneId = 0, isPoweredOn = true) }
        viewModelScope.launch {
            wizController.setColorTemperature(bulb.ip, clamped, _bulbState.value.brightness)
        }
    }

    fun setRgbColor(r: Int, g: Int, b: Int) {
        val bulb = _connectedBulb.value ?: return
        _bulbState.update { it.copy(r = r, g = g, b = b, useRgb = true, sceneId = 0, isPoweredOn = true) }
        viewModelScope.launch {
            wizController.setRgb(bulb.ip, r, g, b, _bulbState.value.brightness)
        }
    }

    fun setScene(scene: WizScene) {
        val bulb = _connectedBulb.value ?: return
        _bulbState.update { it.copy(sceneId = scene.id, useRgb = false, isPoweredOn = true) }
        viewModelScope.launch {
            wizController.setScene(bulb.ip, scene.id)
        }
    }

    fun processVoiceCommand(text: String) {
        val lower = text.lowercase()
        LivoraLogger.debug(TAG, "Bulb voice command: $lower")
        when {
            lower.contains("light") && (lower.contains("on") || lower.contains("turn on")) -> {
                if (!_bulbState.value.isPoweredOn) togglePower()
            }
            lower.contains("light") && (lower.contains("off") || lower.contains("turn off")) -> {
                if (_bulbState.value.isPoweredOn) togglePower()
            }
            lower.contains("bright") && lower.contains("up") -> increaseBrightness()
            lower.contains("bright") && lower.contains("down") -> decreaseBrightness()
            lower.contains("warm") -> setColorTemperature(2700)
            lower.contains("cool") && lower.contains("white") -> setColorTemperature(6500)
            lower.contains("daylight") -> setColorTemperature(5000)
            else -> {
                val brightnessMatch = Regex("(\\d+)\\s*(%|percent)").find(lower)
                if (brightnessMatch != null) {
                    val value = brightnessMatch.groupValues[1].toIntOrNull()
                    if (value != null) {
                        setBrightness(value.coerceIn(WizBulbState.MIN_BRIGHTNESS, WizBulbState.MAX_BRIGHTNESS))
                    }
                }
            }
        }
    }

    companion object {
        private const val TAG = "Livora.BulbViewModel"
        private const val PREFS_NAME = "livora_bulb_prefs"
        private const val KEY_BULB_IP = "bulb_ip"
        private const val KEY_BULB_MAC = "bulb_mac"
        private const val KEY_BULB_MODULE = "bulb_module"
    }
}
