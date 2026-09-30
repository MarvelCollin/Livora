package com.example.livora.ui.qr

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.net.wifi.WifiNetworkSuggestion
import android.os.Build
import android.os.PersistableBundle
import android.provider.ContactsContract
import android.provider.Settings
import com.example.livora.data.qr.QrContent
import com.example.livora.data.qr.QrKind
import com.example.livora.ui.components.Toaster

class QrAction(val label: String, val run: () -> Unit)

fun copyText(context: Context, text: String, sensitive: Boolean = false) {
    val clipboard = context.getSystemService(ClipboardManager::class.java) ?: return
    val clip = ClipData.newPlainText("Livora", text)
    if (sensitive && Build.VERSION.SDK_INT >= 33) {
        clip.description.extras = PersistableBundle().apply { putBoolean(ClipDescription.EXTRA_IS_SENSITIVE, true) }
    }
    clipboard.setPrimaryClip(clip)
    if (Build.VERSION.SDK_INT < 33) Toaster.info("Copied")
}

fun shareText(context: Context, text: String) {
    val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
    launch(context, Intent.createChooser(send, null))
}

fun launch(context: Context, intent: Intent) {
    try {
        context.startActivity(intent)
    } catch (e: ActivityNotFoundException) {
        Toaster.error("No app on this phone can do that")
    }
}

fun primaryAction(context: Context, content: QrContent): QrAction? = when (content.kind) {
    QrKind.Website -> content.url?.let { url ->
        QrAction("Open link") { launch(context, Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
    }

    QrKind.WiFi -> {
        val ssid = content.field("Network").orEmpty()
        val security = content.field("Security").orEmpty()
        val password = content.field("Password")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && ssid.isNotBlank() && security != "WEP") {
            QrAction("Add network") {
                val builder = WifiNetworkSuggestion.Builder().setSsid(ssid)
                if (security != "None" && password != null) builder.setWpa2Passphrase(password)
                if (content.field("Hidden") == "Yes") builder.setIsHiddenSsid(true)
                val intent = Intent(Settings.ACTION_WIFI_ADD_NETWORKS)
                    .putParcelableArrayListExtra(Settings.EXTRA_WIFI_NETWORK_LIST, arrayListOf(builder.build()))
                launch(context, intent)
            }
        } else if (password != null) {
            QrAction("Copy password") { copyText(context, password, sensitive = true) }
        } else {
            null
        }
    }

    QrKind.Contact -> QrAction("Add contact") {
        val intent = Intent(ContactsContract.Intents.Insert.ACTION).apply {
            type = ContactsContract.RawContacts.CONTENT_TYPE
            putExtra(ContactsContract.Intents.Insert.NAME, content.field("Name"))
            content.field("Phone")?.let { putExtra(ContactsContract.Intents.Insert.PHONE, it) }
            content.field("Email")?.let { putExtra(ContactsContract.Intents.Insert.EMAIL, it) }
        }
        launch(context, intent)
    }

    QrKind.Email -> QrAction("Write email") {
        val intent = Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:" + content.field("Address").orEmpty())).apply {
            content.field("Subject")?.let { putExtra(Intent.EXTRA_SUBJECT, it) }
            content.field("Message")?.let { putExtra(Intent.EXTRA_TEXT, it) }
        }
        launch(context, intent)
    }

    QrKind.Phone -> QrAction("Call") {
        launch(context, Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + content.field("Number").orEmpty())))
    }

    QrKind.Sms -> QrAction("Write message") {
        val intent = Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:" + content.field("Number").orEmpty())).apply {
            content.field("Message")?.let { putExtra("sms_body", it) }
        }
        launch(context, intent)
    }

    QrKind.Location -> QrAction("Open in maps") {
        val point = content.field("Latitude").orEmpty() + "," + content.field("Longitude").orEmpty()
        launch(context, Intent(Intent.ACTION_VIEW, Uri.parse("geo:$point?q=$point")))
    }

    QrKind.Text -> null
}
