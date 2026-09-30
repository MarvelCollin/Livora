package com.example.livora.data.ir

import com.example.livora.data.ir.protocol.CoolixProtocol
import com.example.livora.data.ir.protocol.DaikinProtocol
import com.example.livora.data.ir.protocol.GreeProtocol
import com.example.livora.data.ir.protocol.GreeVariant
import com.example.livora.data.ir.protocol.LgProtocol
import com.example.livora.data.ir.protocol.LgVariant
import com.example.livora.data.ir.protocol.MideaProtocol
import com.example.livora.data.ir.protocol.PanasonicProtocol
import com.example.livora.data.ir.protocol.PanasonicVariant
import com.example.livora.data.ir.protocol.SamsungProtocol

class AcModel(val label: String, val detail: String)

class AcBrand(
    val id: String,
    val name: String,
    val alsoWorksWith: String,
    val models: List<AcModel>,
    private val factory: (Int) -> AcProtocol
) {
    fun createProtocol(modelIndex: Int): AcProtocol =
        factory(modelIndex.coerceIn(0, models.lastIndex))
}

object AcBrands {

    const val DEFAULT_ID = "lg"

    val all: List<AcBrand> = listOf(
        AcBrand(
            id = "lg",
            name = "LG",
            alsoWorksWith = "LG Dual Inverter and split units",
            models = listOf(
                AcModel("Standard", "Swing uses the toggle button"),
                AcModel("Explicit swing", "Separate swing on and off, display toggle"),
                AcModel("LG2", "Newer remotes such as AKB74955603")
            )
        ) { index ->
            LgProtocol(LgVariant.entries[index])
        },
        AcBrand(
            id = "samsung",
            name = "Samsung",
            alsoWorksWith = "Samsung split and WindFree units",
            models = listOf(AcModel("Standard", "Samsung AC remotes"))
        ) { SamsungProtocol() },
        AcBrand(
            id = "daikin",
            name = "Daikin",
            alsoWorksWith = "Daikin ARC series remotes",
            models = listOf(AcModel("Standard", "Daikin 280 bit remotes"))
        ) { DaikinProtocol() },
        AcBrand(
            id = "panasonic",
            name = "Panasonic",
            alsoWorksWith = "Panasonic CS and CU series",
            models = listOf(
                AcModel("Remote A", "JKE, common on newer units"),
                AcModel("Remote B", "DKE, has horizontal swing"),
                AcModel("Remote C", "NKE"),
                AcModel("Remote D", "LKE")
            )
        ) { index ->
            PanasonicProtocol(PanasonicVariant.entries[index])
        },
        AcBrand(
            id = "gree",
            name = "Gree",
            alsoWorksWith = "Cooper and Hunter, Sinclair, Tosot",
            models = listOf(
                AcModel("Remote A", "YAW1F"),
                AcModel("Remote B", "YBOFB")
            )
        ) { index ->
            GreeProtocol(GreeVariant.entries[index])
        },
        AcBrand(
            id = "midea",
            name = "Midea",
            alsoWorksWith = "Comfee, Electrolux, Kaysun",
            models = listOf(AcModel("Standard", "Midea 48 bit remotes"))
        ) { MideaProtocol() },
        AcBrand(
            id = "coolix",
            name = "Toshiba and Beko",
            alsoWorksWith = "Bosch, Kelon and other Coolix remotes",
            models = listOf(AcModel("Standard", "Coolix 24 bit remotes"))
        ) { CoolixProtocol() }
    )

    fun find(id: String): AcBrand = all.firstOrNull { it.id == id } ?: all.first()
}
