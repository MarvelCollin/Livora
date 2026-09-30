package com.example.livora.ui.qr

import android.content.Context
import android.content.Intent
import android.text.format.DateUtils
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.livora.data.qr.QrCodec
import com.example.livora.data.qr.QrContent
import com.example.livora.data.qr.QrHistoryEntity
import com.example.livora.data.qr.QrImageStore
import com.example.livora.data.qr.QrKind
import com.example.livora.data.qr.QrLevel
import com.example.livora.data.qr.QrMatrix
import com.example.livora.data.qr.QrParser
import com.example.livora.data.qr.QrPayloads
import com.example.livora.data.qr.WifiSecurity
import com.example.livora.ui.components.BackButton
import com.example.livora.ui.components.ChoiceOption
import com.example.livora.ui.components.ChoiceRow
import com.example.livora.ui.components.Design
import com.example.livora.ui.components.SectionLabel
import com.example.livora.ui.components.SelectChip
import com.example.livora.ui.components.SkeletonBox
import com.example.livora.ui.components.Toaster
import com.example.livora.ui.components.TopBar
import com.example.livora.ui.people.LinkButton
import com.example.livora.ui.people.OutlineAction
import com.example.livora.ui.people.PrimaryAction
import com.example.livora.ui.people.SegmentTabs
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val createTypes = listOf("Text", "Website", "Wi-Fi", "Contact", "Email", "Phone", "SMS", "Location")

@Composable
fun QrScreen(onBack: () -> Unit, viewModel: QrViewModel = viewModel()) {
    val pager = rememberPagerState { 2 }
    val scope = rememberCoroutineScope()

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            Column {
                TopBar(
                    title = "QR codes",
                    subtitle = "Scan a code or make your own",
                    navigationIcon = { BackButton(onBack) }
                )
                SegmentTabs(
                    labels = listOf("Scan", "Create"),
                    selected = pager.currentPage,
                    onSelect = { scope.launch { pager.animateScrollToPage(it) } }
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, thickness = 0.5.dp)
            }
        }
    ) { innerPadding ->
        HorizontalPager(
            state = pager,
            modifier = Modifier.fillMaxSize().padding(innerPadding),
            key = { it }
        ) { page ->
            if (page == 0) ScanPage(viewModel) else CreatePage()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ScanPage(viewModel: QrViewModel) {
    val context = LocalContext.current
    val history by viewModel.history.collectAsState()
    var opened by rememberSaveable { mutableStateOf<Long?>(null) }
    var confirmClear by rememberSaveable { mutableStateOf(false) }

    val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            viewModel.readPhoto(uri) { text ->
                if (text == null) Toaster.error("No code found in that photo") else viewModel.record(text, true) { opened = it }
            }
        }
    }

    fun scan() {
        val options = GmsBarcodeScannerOptions.Builder()
            .setBarcodeFormats(Barcode.FORMAT_ALL_FORMATS)
            .enableAutoZoom()
            .build()
        GmsBarcodeScanning.getClient(context, options).startScan()
            .addOnSuccessListener { barcode ->
                val raw = barcode.rawValue
                if (raw.isNullOrBlank()) Toaster.error("That code has nothing to read") else viewModel.record(raw, false) { opened = it }
            }
            .addOnFailureListener {
                Toaster.error("Scanning is not available on this phone. You can still scan from a photo.")
            }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = WindowInsets.navigationBars.asPaddingValues()
    ) {
        item(key = "actions") {
            Column(modifier = Modifier.padding(horizontal = Design.screenHorizontalPadding, vertical = 16.dp)) {
                PrimaryAction(text = "Scan a code", onClick = ::scan, modifier = Modifier.fillMaxWidth())
                Spacer(modifier = Modifier.height(8.dp))
                OutlineAction(
                    text = "Scan from a photo",
                    onClick = { photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    text = "Links are never opened for you. You see the full address first.",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 10.dp)
                )
            }
        }
        item(key = "label") {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = Design.screenHorizontalPadding),
                verticalAlignment = Alignment.Bottom
            ) {
                SectionLabel(text = "History", modifier = Modifier.weight(1f))
                if (!history.isNullOrEmpty()) LinkButton(text = "Clear", onClick = { confirmClear = true })
            }
        }
        val list = history
        when {
            list == null -> items(3, key = { "skeleton-$it" }) {
                SkeletonBox(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = Design.screenHorizontalPadding, vertical = 8.dp).height(52.dp),
                    shape = RoundedCornerShape(8.dp)
                )
            }

            list.isEmpty() -> item(key = "empty") {
                Text(
                    text = "Codes you scan show up here so you can find them again. Nothing leaves this phone.",
                    fontSize = 14.sp,
                    lineHeight = 20.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = Design.screenHorizontalPadding, vertical = 12.dp)
                )
            }

            else -> items(list, key = { it.id }) { record ->
                val content = remember(record.value) { QrParser.parse(record.value) }
                Column(modifier = Modifier.animateItem()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 68.dp)
                            .clickable(role = Role.Button) { opened = record.id }
                            .padding(horizontal = Design.screenHorizontalPadding, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = content.kind.label,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = content.title.replace('\n', ' '),
                                fontSize = 15.sp,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1
                            )
                            if (content.warnings.isNotEmpty()) {
                                Text(
                                    text = content.warnings.first(),
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.error,
                                    maxLines = 1
                                )
                            }
                        }
                        Text(
                            text = timeText(record.scannedAt),
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = 12.dp)
                        )
                    }
                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = Design.screenHorizontalPadding),
                        color = MaterialTheme.colorScheme.outlineVariant,
                        thickness = 0.5.dp
                    )
                }
            }
        }
        item(key = "end") { Spacer(modifier = Modifier.height(24.dp)) }
    }

    val current = history?.firstOrNull { it.id == opened }
    if (current != null) {
        ResultSheet(
            record = current,
            onDelete = {
                viewModel.delete(current.id)
                opened = null
            },
            onDismiss = { opened = null }
        )
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("Clear scan history") },
            text = { Text("This removes every scanned code from the list. It cannot be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.clear()
                    confirmClear = false
                }) { Text("Clear") }
            },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("Cancel") } }
        )
    }
}

private fun timeText(millis: Long): String {
    val now = System.currentTimeMillis()
    if (now - millis < DateUtils.MINUTE_IN_MILLIS) return "Just now"
    return DateUtils.getRelativeTimeSpanString(millis, now, DateUtils.MINUTE_IN_MILLIS, DateUtils.FORMAT_ABBREV_RELATIVE).toString()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ResultSheet(record: QrHistoryEntity, onDelete: () -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val content = remember(record.value) { QrParser.parse(record.value) }
    val primary = remember(record.value) { primaryAction(context, content) }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 16.dp)
        ) {
            Text(
                text = content.kind.label,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(6.dp))
            if (content.kind == QrKind.Website) {
                Text(text = emphasizeHost(content.raw), fontSize = 17.sp, color = MaterialTheme.colorScheme.onSurface)
            } else {
                Text(text = content.title, fontSize = 17.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurface)
            }
            Text(
                text = timeText(record.scannedAt) + if (record.fromPhoto) ", from a photo" else "",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp)
            )
            content.warnings.forEach { warning ->
                Text(
                    text = warning,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(top = 12.dp)
                )
            }
            val extra = content.fields.filter { it.label != "Address" && it.value != content.title }
            if (extra.isNotEmpty()) {
                Spacer(modifier = Modifier.height(12.dp))
                extra.forEach { field ->
                    var shown by remember(field.value) { mutableStateOf(!field.secret) }
                    Row(
                        modifier = Modifier.fillMaxWidth().heightIn(min = 44.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = field.label,
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(0.4f)
                        )
                        Text(
                            text = if (shown) field.value else "•".repeat(8),
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(0.6f)
                        )
                        if (field.secret) LinkButton(text = if (shown) "Hide" else "Show", onClick = { shown = !shown })
                    }
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (primary != null) {
                    OutlineAction(text = "Copy", onClick = { copyText(context, record.value) }, modifier = Modifier.weight(1f))
                    OutlineAction(text = "Share", onClick = { shareText(context, record.value) }, modifier = Modifier.weight(1f))
                    PrimaryAction(text = primary.label, onClick = primary.run, modifier = Modifier.weight(1.3f))
                } else {
                    OutlineAction(text = "Share", onClick = { shareText(context, record.value) }, modifier = Modifier.weight(1f))
                    PrimaryAction(text = "Copy", onClick = { copyText(context, record.value) }, modifier = Modifier.weight(1f))
                }
            }
            LinkButton(text = "Delete from history", onClick = onDelete)
        }
    }
}

private fun emphasizeHost(value: String) = buildAnnotatedString {
    val match = Regex("^(https?://)([^/]+)(.*)$", RegexOption.IGNORE_CASE).find(value)
    if (match == null) {
        append(value)
    } else {
        append(match.groupValues[1])
        withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(match.groupValues[2]) }
        append(match.groupValues[3])
    }
}

@Composable
private fun Field(
    value: String,
    onChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    keyboard: KeyboardType = KeyboardType.Text,
    singleLine: Boolean = true
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        singleLine = singleLine,
        keyboardOptions = KeyboardOptions(keyboardType = keyboard),
        modifier = modifier.fillMaxWidth().padding(bottom = 8.dp)
    )
}

@Composable
private fun CreatePage() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var type by rememberSaveable { mutableStateOf(createTypes.first()) }
    var text by rememberSaveable { mutableStateOf("") }
    var ssid by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var security by rememberSaveable { mutableStateOf(WifiSecurity.Wpa) }
    var hidden by rememberSaveable { mutableStateOf(false) }
    var name by rememberSaveable { mutableStateOf("") }
    var phone by rememberSaveable { mutableStateOf("") }
    var email by rememberSaveable { mutableStateOf("") }
    var subject by rememberSaveable { mutableStateOf("") }
    var message by rememberSaveable { mutableStateOf("") }
    var latitude by rememberSaveable { mutableStateOf("") }
    var longitude by rememberSaveable { mutableStateOf("") }
    var level by rememberSaveable { mutableStateOf(QrLevel.M) }

    val payload = when (type) {
        "Text" -> text.trim()
        "Website" -> QrPayloads.website(text)
        "Wi-Fi" -> QrPayloads.wifi(ssid, password, security, hidden)
        "Contact" -> QrPayloads.contact(name, phone, email)
        "Email" -> QrPayloads.email(email, subject, message)
        "Phone" -> QrPayloads.phone(phone)
        "SMS" -> QrPayloads.sms(phone, message)
        else -> QrPayloads.location(latitude, longitude)
    }
    val matrix = remember(payload, level) { QrCodec.encode(payload, level) }
    val ready = matrix != null

    suspend fun save(): android.net.Uri? = withContext(Dispatchers.IO) {
        matrix?.let { QrImageStore.savePng(context, QrImageStore.bitmap(it), "Livora-QR-${System.currentTimeMillis()}") }
    }

    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Column(modifier = Modifier.padding(horizontal = Design.screenHorizontalPadding, vertical = 12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                createTypes.forEach { name ->
                    SelectChip(label = name, selected = name == type, onClick = { type = name })
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
            when (type) {
                "Text" -> Field(text, { text = it.take(600) }, "Text", singleLine = false)
                "Website" -> Field(text, { text = it.take(600) }, "Web address", keyboard = KeyboardType.Uri)
                "Wi-Fi" -> {
                    Field(ssid, { ssid = it.take(32) }, "Network name")
                    ChoiceRow(
                        options = WifiSecurity.entries.map { ChoiceOption(it, it.label) },
                        selected = security,
                        enabled = true,
                        onSelect = { security = it }
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    if (security != WifiSecurity.None) Field(password, { password = it.take(63) }, "Password")
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        SelectChip(label = "Hidden network", selected = hidden, onClick = { hidden = !hidden })
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }
                "Contact" -> {
                    Field(name, { name = it.take(80) }, "Name")
                    Field(phone, { phone = it.take(30) }, "Phone number", keyboard = KeyboardType.Phone)
                    Field(email, { email = it.take(120) }, "Email, optional", keyboard = KeyboardType.Email)
                }
                "Email" -> {
                    Field(email, { email = it.take(120) }, "Email address", keyboard = KeyboardType.Email)
                    Field(subject, { subject = it.take(120) }, "Subject, optional")
                    Field(message, { message = it.take(400) }, "Message, optional", singleLine = false)
                }
                "Phone" -> Field(phone, { phone = it.take(30) }, "Phone number", keyboard = KeyboardType.Phone)
                "SMS" -> {
                    Field(phone, { phone = it.take(30) }, "Phone number", keyboard = KeyboardType.Phone)
                    Field(message, { message = it.take(300) }, "Message", singleLine = false)
                }
                else -> {
                    Field(latitude, { latitude = it.take(20) }, "Latitude, for example -6.2", keyboard = KeyboardType.Decimal)
                    Field(longitude, { longitude = it.take(20) }, "Longitude, for example 106.8", keyboard = KeyboardType.Decimal)
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            QrPreview(matrix = matrix, tooLong = payload.length > QrCodec.MAX_TEXT, modifier = Modifier.align(Alignment.CenterHorizontally))
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Error correction",
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(6.dp))
            ChoiceRow(
                options = QrLevel.entries.map { ChoiceOption(it, it.label) },
                selected = level,
                enabled = true,
                onSelect = { level = it }
            )
            Text(
                text = "Higher levels still scan when the code is dirty or damaged, but the code gets denser.",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 6.dp)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PrimaryAction(
                    text = "Save PNG",
                    onClick = {
                        scope.launch {
                            if (save() != null) Toaster.success("Saved to Pictures/Livora") else Toaster.error("Could not save the image")
                        }
                    },
                    enabled = ready,
                    modifier = Modifier.weight(1f)
                )
                OutlineAction(
                    text = "Share",
                    onClick = {
                        scope.launch {
                            val uri = save()
                            if (uri == null) {
                                Toaster.error("Could not share the image")
                            } else {
                                val send = Intent(Intent.ACTION_SEND)
                                    .setType("image/png")
                                    .putExtra(Intent.EXTRA_STREAM, uri)
                                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                launch(context, Intent.createChooser(send, null))
                            }
                        }
                    },
                    enabled = ready,
                    modifier = Modifier.weight(1f)
                )
            }
            LinkButton(text = "Copy text", onClick = { copyText(context, payload, sensitive = type == "Wi-Fi") }, enabled = ready)
            Spacer(modifier = Modifier.height(24.dp))
            Spacer(modifier = Modifier.navigationBarsPadding())
        }
    }
}

@Composable
private fun QrPreview(matrix: QrMatrix?, tooLong: Boolean, modifier: Modifier = Modifier) {
    val borderColor = MaterialTheme.colorScheme.outlineVariant
    Box(
        modifier = modifier
            .size(232.dp)
            .background(Color.White, RoundedCornerShape(8.dp))
            .border(1.dp, borderColor, RoundedCornerShape(8.dp))
            .semantics { contentDescription = if (matrix == null) "No code yet" else "The QR code you are making" },
        contentAlignment = Alignment.Center
    ) {
        if (matrix == null) {
            Text(
                text = if (tooLong) "That is too much text for one code" else "Fill in the details to see the code",
                fontSize = 13.sp,
                color = Color(0xFF5A798F),
                modifier = Modifier.padding(24.dp)
            )
        } else {
            Canvas(modifier = Modifier.fillMaxSize().padding(4.dp)) {
                val cells = matrix.size + QrCodec.QUIET_ZONE * 2
                val cell = size.minDimension / cells
                for (y in 0 until matrix.size) {
                    for (x in 0 until matrix.size) {
                        if (matrix[x, y]) {
                            drawRect(
                                color = Color(0xFF0E212E),
                                topLeft = Offset((x + QrCodec.QUIET_ZONE) * cell, (y + QrCodec.QUIET_ZONE) * cell),
                                size = Size(cell + 0.5f, cell + 0.5f)
                            )
                        }
                    }
                }
            }
        }
    }
}
