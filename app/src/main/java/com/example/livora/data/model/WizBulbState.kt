package com.example.livora.data.model

data class WizBulb(
    val ip: String,
    val mac: String,
    val moduleName: String = ""
)

data class WizBulbState(
    val isPoweredOn: Boolean = false,
    val brightness: Int = 100,
    val colorTemp: Int = 4000,
    val r: Int = 255,
    val g: Int = 255,
    val b: Int = 255,
    val sceneId: Int = 0,
    val useRgb: Boolean = false
) {
    companion object {
        const val MIN_BRIGHTNESS = 10
        const val MAX_BRIGHTNESS = 100
        const val MIN_COLOR_TEMP = 2200
        const val MAX_COLOR_TEMP = 6500
    }
}

enum class WizScene(val id: Int, val label: String) {
    OCEAN(1, "Ocean"),
    ROMANCE(2, "Romance"),
    SUNSET(3, "Sunset"),
    PARTY(4, "Party"),
    FIREPLACE(5, "Fireplace"),
    COZY(6, "Cozy"),
    FOREST(7, "Forest"),
    PASTEL_COLORS(8, "Pastel Colors"),
    WAKE_UP(9, "Wake Up"),
    BEDTIME(10, "Bedtime"),
    WARM_WHITE(11, "Warm White"),
    DAYLIGHT(12, "Daylight"),
    COOL_WHITE(13, "Cool White"),
    NIGHT_LIGHT(14, "Night Light"),
    FOCUS(15, "Focus"),
    RELAX(16, "Relax"),
    TRUE_COLORS(17, "True Colors"),
    TV_TIME(18, "TV Time"),
    PLANT_GROWTH(19, "Plant Growth"),
    SPRING(20, "Spring"),
    SUMMER(21, "Summer"),
    FALL(22, "Fall"),
    DEEP_DIVE(23, "Deep Dive"),
    JUNGLE(24, "Jungle"),
    MOJITO(25, "Mojito"),
    CLUB(26, "Club"),
    CHRISTMAS(27, "Christmas"),
    HALLOWEEN(28, "Halloween"),
    CANDLELIGHT(29, "Candlelight"),
    GOLDEN_WHITE(30, "Golden White"),
    PULSE(31, "Pulse"),
    STEAMPUNK(32, "Steampunk")
}
