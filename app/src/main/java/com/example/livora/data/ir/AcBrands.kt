package com.example.livora.data.ir

import com.example.livora.data.ir.protocol.CoolixProtocol
import com.example.livora.data.ir.protocol.DaikinProtocol
import com.example.livora.data.ir.protocol.GreeProtocol
import com.example.livora.data.ir.protocol.GreeVariant
import com.example.livora.data.ir.protocol.HitachiProtocol
import com.example.livora.data.ir.protocol.LgProtocol
import com.example.livora.data.ir.protocol.LgVariant
import com.example.livora.data.ir.protocol.MideaProtocol
import com.example.livora.data.ir.protocol.MitsubishiHeavyProtocol
import com.example.livora.data.ir.protocol.MitsubishiHeavyVariant
import com.example.livora.data.ir.protocol.MitsubishiProtocol
import com.example.livora.data.ir.protocol.PanasonicProtocol
import com.example.livora.data.ir.protocol.PanasonicVariant
import com.example.livora.data.ir.protocol.SamsungProtocol
import com.example.livora.data.ir.protocol.SharpProtocol
import com.example.livora.data.ir.protocol.SharpVariant
import com.example.livora.data.ir.protocol.TclProtocol
import com.example.livora.data.ir.protocol.ToshibaProtocol

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
            id = "mitsubishi",
            name = "Mitsubishi Electric",
            alsoWorksWith = "MSZ and MSY series",
            models = listOf(AcModel("Standard", "Mitsubishi Electric 144 bit remotes"))
        ) { MitsubishiProtocol() },
        AcBrand(
            id = "mitsubishi_heavy",
            name = "Mitsubishi Heavy",
            alsoWorksWith = "SRK and SRC series",
            models = listOf(
                AcModel("Remote A", "152 bit remotes, newer units"),
                AcModel("Remote B", "88 bit remotes, older units")
            )
        ) { index ->
            MitsubishiHeavyProtocol(MitsubishiHeavyVariant.entries[index])
        },
        AcBrand(
            id = "sharp",
            name = "Sharp",
            alsoWorksWith = "Sharp Plasmacluster inverter units",
            models = listOf(
                AcModel("Remote A", "A907 remotes with heat mode"),
                AcModel("Remote B", "A705 and A903 remotes, cool only")
            )
        ) { index ->
            SharpProtocol(SharpVariant.entries[index])
        },
        AcBrand(
            id = "hitachi",
            name = "Hitachi",
            alsoWorksWith = "Hitachi RAS series",
            models = listOf(AcModel("Standard", "Hitachi 224 bit remotes"))
        ) { HitachiProtocol() },
        AcBrand(
            id = "toshiba",
            name = "Toshiba",
            alsoWorksWith = "Toshiba RAS series",
            models = listOf(
                AcModel("Remote A", "Toshiba native remote"),
                AcModel("Remote B", "Coolix based remote such as RAS-M10YKV-E")
            )
        ) { index ->
            if (index == 0) ToshibaProtocol() else CoolixProtocol()
        },
        AcBrand(
            id = "tcl",
            name = "TCL",
            alsoWorksWith = "TCL TAC and GZ remotes",
            models = listOf(AcModel("Standard", "TCL 112 bit remotes"))
        ) { TclProtocol() },
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
            alsoWorksWith = "Comfee, Electrolux, Kaysun, Carrier",
            models = listOf(
                AcModel("Remote A", "Midea 48 bit remotes"),
                AcModel("Remote B", "Coolix based remotes")
            )
        ) { index ->
            if (index == 0) MideaProtocol() else CoolixProtocol()
        },
        AcBrand(
            id = "other",
            name = "Other Indonesian brands",
            alsoWorksWith = "Polytron, Changhong, Sanken, Akari, Denpoo, Modena",
            models = listOf(
                AcModel("Try 1", "Gree style remotes"),
                AcModel("Try 2", "Midea style remotes"),
                AcModel("Try 3", "Coolix style remotes"),
                AcModel("Try 4", "Gree style remotes, second variant"),
                AcModel("Try 5", "TCL style remotes")
            )
        ) { index ->
            when (index) {
                0 -> GreeProtocol(GreeVariant.YAW1F)
                1 -> MideaProtocol()
                2 -> CoolixProtocol()
                3 -> GreeProtocol(GreeVariant.YBOFB)
                else -> TclProtocol()
            }
        }
    )

    fun find(id: String): AcBrand = all.firstOrNull { it.id == id } ?: all.first()
}
