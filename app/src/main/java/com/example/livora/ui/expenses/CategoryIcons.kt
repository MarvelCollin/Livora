package com.example.livora.ui.expenses

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.AttachMoney
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.CardGiftcard
import androidx.compose.material.icons.filled.ChildCare
import androidx.compose.material.icons.filled.Checkroom
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.DirectionsBus
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Flight
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LocalCafe
import androidx.compose.material.icons.filled.LocalGasStation
import androidx.compose.material.icons.filled.LocalHospital
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Pets
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.Savings
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.ShoppingBag
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.Spa
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material.icons.filled.Subscriptions
import androidx.compose.material.icons.filled.VolunteerActivism
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.Work
import androidx.compose.ui.graphics.vector.ImageVector

class CategoryIcon(val key: String, val label: String, val vector: ImageVector)

object CategoryIcons {

    val all: List<CategoryIcon> = listOf(
        CategoryIcon("food", "Food", Icons.Default.Restaurant),
        CategoryIcon("coffee", "Coffee", Icons.Default.LocalCafe),
        CategoryIcon("groceries", "Groceries", Icons.Default.ShoppingCart),
        CategoryIcon("transport", "Transport", Icons.Default.DirectionsBus),
        CategoryIcon("car", "Car", Icons.Default.DirectionsCar),
        CategoryIcon("fuel", "Fuel", Icons.Default.LocalGasStation),
        CategoryIcon("bills", "Bills", Icons.Default.Receipt),
        CategoryIcon("electricity", "Electricity", Icons.Default.Bolt),
        CategoryIcon("water", "Water", Icons.Default.WaterDrop),
        CategoryIcon("internet", "Internet", Icons.Default.Wifi),
        CategoryIcon("phone", "Phone", Icons.Default.PhoneAndroid),
        CategoryIcon("subscription", "Subscriptions", Icons.Default.Subscriptions),
        CategoryIcon("shopping", "Shopping", Icons.Default.ShoppingBag),
        CategoryIcon("clothes", "Clothes", Icons.Default.Checkroom),
        CategoryIcon("fun", "Fun", Icons.Default.Movie),
        CategoryIcon("game", "Games", Icons.Default.SportsEsports),
        CategoryIcon("music", "Music", Icons.Default.MusicNote),
        CategoryIcon("travel", "Travel", Icons.Default.Flight),
        CategoryIcon("health", "Health", Icons.Default.LocalHospital),
        CategoryIcon("fitness", "Fitness", Icons.Default.FitnessCenter),
        CategoryIcon("beauty", "Beauty", Icons.Default.Spa),
        CategoryIcon("home", "Home", Icons.Default.Home),
        CategoryIcon("education", "Education", Icons.Default.School),
        CategoryIcon("books", "Books", Icons.Default.MenuBook),
        CategoryIcon("kids", "Kids", Icons.Default.ChildCare),
        CategoryIcon("pets", "Pets", Icons.Default.Pets),
        CategoryIcon("gift", "Gifts", Icons.Default.CardGiftcard),
        CategoryIcon("charity", "Charity", Icons.Default.VolunteerActivism),
        CategoryIcon("allowance", "Allowance", Icons.Default.Savings),
        CategoryIcon("salary", "Salary", Icons.Default.Payments),
        CategoryIcon("freelance", "Freelance", Icons.Default.Work),
        CategoryIcon("income", "Income", Icons.Default.AttachMoney),
        CategoryIcon("other", "Other", Icons.Default.Category)
    )

    private val byKey = all.associateBy { it.key }

    private val keywords = listOf(
        "food" to listOf("food", "eat", "lunch", "dinner", "breakfast", "meal", "restaurant", "makan"),
        "coffee" to listOf("coffee", "cafe", "kopi", "tea", "boba"),
        "groceries" to listOf("grocer", "market", "supermarket", "belanja"),
        "transport" to listOf("transport", "bus", "train", "ride", "taxi", "ojek", "gojek", "grab", "commute"),
        "car" to listOf("car", "parking", "toll", "mobil"),
        "fuel" to listOf("fuel", "gas", "petrol", "bensin"),
        "bills" to listOf("bill", "tax", "rent", "insurance", "tagihan"),
        "electricity" to listOf("electric", "power", "listrik", "pln"),
        "water" to listOf("water", "pdam", "air"),
        "internet" to listOf("internet", "wifi", "data"),
        "phone" to listOf("phone", "pulsa", "mobile"),
        "subscription" to listOf("subscription", "netflix", "spotify", "premium", "member"),
        "shopping" to listOf("shop", "online", "gadget"),
        "clothes" to listOf("cloth", "shoe", "fashion", "baju"),
        "fun" to listOf("fun", "movie", "cinema", "hobby", "hangout"),
        "game" to listOf("game", "steam", "playstation"),
        "music" to listOf("music", "concert", "konser"),
        "travel" to listOf("travel", "trip", "flight", "hotel", "holiday", "liburan"),
        "health" to listOf("health", "doctor", "clinic", "pharmacy", "medicine", "obat"),
        "fitness" to listOf("gym", "fitness", "sport", "swim"),
        "beauty" to listOf("beauty", "salon", "haircut", "skincare"),
        "home" to listOf("home", "house", "furniture", "rumah", "kos"),
        "education" to listOf("school", "tuition", "course", "class", "campus", "kuliah"),
        "books" to listOf("book", "study", "buku"),
        "kids" to listOf("kid", "baby", "child", "anak"),
        "pets" to listOf("pet", "cat", "dog", "vet"),
        "gift" to listOf("gift", "present", "hadiah"),
        "charity" to listOf("charity", "donat", "zakat", "sedekah"),
        "salary" to listOf("salary", "wage", "gaji"),
        "freelance" to listOf("freelance", "project", "side job")
    )

    fun vector(key: String): ImageVector = (byKey[key] ?: byKey.getValue("other")).vector

    fun suggest(name: String): String? {
        val text = name.trim().lowercase()
        if (text.length < 3) return null
        return keywords.firstOrNull { (_, words) -> words.any { text.contains(it) } }?.first
    }
}

fun accountIcon(name: String): ImageVector {
    val text = name.lowercase()
    return when {
        listOf("cash", "tunai", "uang").any { text.contains(it) } -> Icons.Default.Payments
        listOf("bank", "bca", "bri", "bni", "mandiri", "jago", "cimb", "saving").any { text.contains(it) } -> Icons.Default.AccountBalance
        listOf("card", "credit", "debit", "kartu").any { text.contains(it) } -> Icons.Default.CreditCard
        else -> Icons.Default.AccountBalanceWallet
    }
}
