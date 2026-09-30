package com.example.livora.data.ir

import android.content.Context
import android.content.SharedPreferences
import com.example.livora.data.model.AcMode
import com.example.livora.data.model.AcState
import com.example.livora.data.model.FanSpeed
import com.example.livora.data.model.SwingMode

data class AcRemote(val brandId: String, val modelIndex: Int)

class AcSettingsStore(context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun loadRemote(): AcRemote = AcRemote(
        brandId = prefs.getString(KEY_BRAND, AcBrands.DEFAULT_ID) ?: AcBrands.DEFAULT_ID,
        modelIndex = prefs.getInt(KEY_MODEL, 0)
    )

    fun saveRemote(remote: AcRemote) {
        prefs.edit()
            .putString(KEY_BRAND, remote.brandId)
            .putInt(KEY_MODEL, remote.modelIndex)
            .apply()
    }

    fun loadState(): AcState {
        val defaults = AcState()
        return AcState(
            isPoweredOn = prefs.getBoolean(KEY_POWER, defaults.isPoweredOn),
            temperature = prefs.getInt(KEY_TEMPERATURE, defaults.temperature),
            mode = enumOf(prefs.getString(KEY_MODE, null), defaults.mode),
            fanSpeed = enumOf(prefs.getString(KEY_FAN, null), defaults.fanSpeed),
            swingMode = enumOf(prefs.getString(KEY_SWING, null), defaults.swingMode),
            isSleepMode = prefs.getBoolean(KEY_SLEEP, defaults.isSleepMode),
            isEnergySaving = prefs.getBoolean(KEY_ECO, defaults.isEnergySaving),
            isDisplayOn = prefs.getBoolean(KEY_DISPLAY, defaults.isDisplayOn),
            timerEndsAtMillis = prefs.getLong(KEY_TIMER_ENDS_AT, 0L)
        )
    }

    fun saveState(state: AcState) {
        prefs.edit()
            .putBoolean(KEY_POWER, state.isPoweredOn)
            .putInt(KEY_TEMPERATURE, state.temperature)
            .putString(KEY_MODE, state.mode.name)
            .putString(KEY_FAN, state.fanSpeed.name)
            .putString(KEY_SWING, state.swingMode.name)
            .putBoolean(KEY_SLEEP, state.isSleepMode)
            .putBoolean(KEY_ECO, state.isEnergySaving)
            .putBoolean(KEY_DISPLAY, state.isDisplayOn)
            .putLong(KEY_TIMER_ENDS_AT, state.timerEndsAtMillis)
            .apply()
    }

    fun registerChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) {
        prefs.registerOnSharedPreferenceChangeListener(listener)
    }

    fun unregisterChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) {
        prefs.unregisterOnSharedPreferenceChangeListener(listener)
    }

    private inline fun <reified T : Enum<T>> enumOf(name: String?, fallback: T): T =
        enumValues<T>().firstOrNull { it.name == name } ?: fallback

    companion object {
        private const val PREFS_NAME = "livora_ac_prefs"
        private const val KEY_BRAND = "brand"
        private const val KEY_MODEL = "model"
        private const val KEY_POWER = "power"
        private const val KEY_TEMPERATURE = "temperature"
        private const val KEY_MODE = "mode"
        private const val KEY_FAN = "fan"
        private const val KEY_SWING = "swing"
        private const val KEY_SLEEP = "sleep"
        private const val KEY_ECO = "eco"
        private const val KEY_DISPLAY = "display"
        private const val KEY_TIMER_ENDS_AT = "timer_ends_at"
    }
}
