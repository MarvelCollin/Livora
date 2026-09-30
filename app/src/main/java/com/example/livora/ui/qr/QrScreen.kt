package com.example.livora.ui.qr

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
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.livora.ui.components.BackButton
import com.example.livora.ui.components.ChoiceOption
import com.example.livora.ui.components.ChoiceRow
import com.example.livora.ui.components.Design
import com.example.livora.ui.components.PreviewNotice
import com.example.livora.ui.components.SectionLabel
import com.example.livora.ui.components.SelectChip
import com.example.livora.ui.components.TopBar
import com.example.livora.ui.components.showPreviewOnly
import com.example.livora.ui.people.LinkButton
import com.example.livora.ui.people.OutlineAction
import com.example.livora.ui.people.PrimaryAction
import com.example.livora.ui.people.SegmentTabs
import kotlinx.coroutines.launch

private class ScanRecord(val type: String, val value: String, val whenText: String, val warning: String? = null)

private val history = listOf(
    ScanRecord("Website", "https://bit.ly/3xYzAbc", "Today, 09:12", "Shortened link. Check where it goes before you open it."),
    ScanRecord("Wi-Fi", "KopiKita_5G", "Yesterday, 16:40"),
    ScanRecord("Text", "Table 12, order 4471", "28 Sep, 12:05"),
    ScanRecord("Website", "http://menu.warungmakan.id/today", "24 Sep, 19:30", "This address is not encrypted. Do not enter passwords on it."),
    ScanRecord("Contact", "Rina Putri, 0812 555 0142", "20 Sep, 08:15")
)

private val createTypes = listOf("Text", "Website", "Wi-Fi", "Contact", "Email", "Phone", "SMS", "Location")

@Composable
fun QrScreen(onBack: () -> Unit) {
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
            if (page == 0) ScanPage() else CreatePage()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ScanPage() {
    var opened by rememberSaveable { mutableStateOf<Int?>(null) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = WindowInsets.navigationBars.asPaddingValues()
    ) {
        item(key = "notice") { PreviewNotice() }
        item(key = "actions") {
            Column(modifier = Modifier.padding(horizontal = Design.screenHorizontalPadding, vertical = 16.dp)) {
                PrimaryAction(text = "Scan a code", onClick = { showPreviewOnly() }, modifier = Modifier.fillMaxWidth())
                Spacer(modifier = Modifier.height(8.dp))
                OutlineAction(text = "Scan from a photo", onClick = { showPreviewOnly() }, modifier = Modifier.fillMaxWidth())
                Text(
                    text = "Links are never opened for you. You see the full address first.",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 10.dp)
                )
            }
        }
        item(key = "label") {
            SectionLabel(text = "History", modifier = Modifier.padding(horizontal = Design.screenHorizontalPadding))
        }
        items(history.size, key = { "scan-$it" }) { index ->
            val record = history[index]
            Column(modifier = Modifier.animateItem()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 68.dp)
                        .clickable { opened = index }
                        .padding(horizontal = Design.screenHorizontalPadding, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = record.type,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = record.value,
                            fontSize = 15.sp,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1
                        )
                    }
                    Text(
                        text = record.whenText,
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
        item(key = "end") { Spacer(modifier = Modifier.height(24.dp)) }
    }

    val current = opened
    if (current != null) {
        val record = history[current]
        ModalBottomSheet(
            onDismissRequest = { opened = null },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            Column(
                modifier = Modifier
                    .navigationBarsPadding()
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 16.dp)
            ) {
                Text(
                    text = record.type,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(text = emphasizeHost(record.value), fontSize = 17.sp, color = MaterialTheme.colorScheme.onSurface)
                Text(
                    text = record.whenText,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp)
                )
                if (record.warning != null) {
                    Text(
                        text = record.warning,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(top = 12.dp)
                    )
                }
                Spacer(modifier = Modifier.height(16.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlineAction(text = "Copy", onClick = { showPreviewOnly() }, modifier = Modifier.weight(1f))
                    OutlineAction(text = "Share", onClick = { showPreviewOnly() }, modifier = Modifier.weight(1f))
                    PrimaryAction(
                        text = if (record.type == "Website") "Open link" else "Use",
                        onClick = { showPreviewOnly() },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

private fun emphasizeHost(value: String) = buildAnnotatedString {
    val match = Regex("^(https?://)([^/]+)(.*)$").find(value)
    if (match == null) {
        append(value)
    } else {
        append(match.groupValues[1])
        withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(match.groupValues[2]) }
        append(match.groupValues[3])
    }
}

@Composable
private fun CreatePage() {
    var type by rememberSaveable { mutableStateOf(createTypes.first()) }
    var text by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var level by rememberSaveable { mutableStateOf("M") }
    val payload = if (type == "Wi-Fi") "WIFI:S:$text;P:$password;;" else text

    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        PreviewNotice()
        Column(modifier = Modifier.padding(horizontal = Design.screenHorizontalPadding, vertical = 8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            createTypes.forEach { name ->
                SelectChip(label = name, selected = name == type, onClick = { type = name })
            }
        }
        Spacer(modifier = Modifier.height(12.dp))
        OutlinedTextField(
            value = text,
            onValueChange = { text = it.take(200) },
            label = { Text(inputLabel(type)) },
            supportingText = { Text("The code updates as you type.") },
            modifier = Modifier.fillMaxWidth()
        )
        if (type == "Wi-Fi") {
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = password,
                onValueChange = { password = it.take(63) },
                label = { Text("Password") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        }
        Spacer(modifier = Modifier.height(16.dp))
        QrPreview(payload = payload, modifier = Modifier.align(Alignment.CenterHorizontally))
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "Error correction",
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.height(6.dp))
        ChoiceRow(
            options = listOf("L", "M", "Q", "H").map { ChoiceOption(it, it) },
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
                onClick = { showPreviewOnly() },
                enabled = payload.isNotBlank(),
                modifier = Modifier.weight(1f)
            )
            OutlineAction(
                text = "Share",
                onClick = { showPreviewOnly() },
                enabled = payload.isNotBlank(),
                modifier = Modifier.weight(1f)
            )
        }
        LinkButton(text = "Copy text", onClick = { showPreviewOnly() }, enabled = payload.isNotBlank())
        Spacer(modifier = Modifier.height(24.dp))
        Spacer(modifier = Modifier.navigationBarsPadding())
        }
    }
}

private fun inputLabel(type: String) = when (type) {
    "Website" -> "Web address"
    "Wi-Fi" -> "Network name"
    "Contact" -> "Name and phone number"
    "Email" -> "Email address"
    "Phone" -> "Phone number"
    "SMS" -> "Number and message"
    "Location" -> "Latitude and longitude"
    else -> "Text"
}

@Composable
private fun QrPreview(payload: String, modifier: Modifier = Modifier) {
    val cells = 25
    val matrix = remember(payload) { pseudoMatrix(payload, cells) }
    val borderColor = MaterialTheme.colorScheme.outlineVariant
    Box(
        modifier = modifier
            .size(216.dp)
            .background(Color.White, RoundedCornerShape(8.dp))
            .border(1.dp, borderColor, RoundedCornerShape(8.dp))
            .padding(14.dp),
        contentAlignment = Alignment.Center
    ) {
        if (payload.isBlank()) {
            Text(
                text = "Type something to see the code",
                fontSize = 13.sp,
                color = Color(0xFF5A798F)
            )
        } else {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val cell = size.minDimension / cells
                for (y in 0 until cells) {
                    for (x in 0 until cells) {
                        if (matrix[y][x]) {
                            drawRoundRect(
                                color = Color(0xFF0E212E),
                                topLeft = Offset(x * cell, y * cell),
                                size = Size(cell + 0.5f, cell + 0.5f),
                                cornerRadius = CornerRadius(cell * 0.12f)
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun pseudoMatrix(payload: String, n: Int): Array<BooleanArray> {
    val grid = Array(n) { BooleanArray(n) }
    var seed = payload.fold(17L) { acc, c -> acc * 31 + c.code }
    fun next(): Boolean {
        seed = seed * 6364136223846793005L + 1442695040888963407L
        return (seed ushr 33) % 2L == 0L
    }
    for (y in 0 until n) for (x in 0 until n) grid[y][x] = next()
    fun finder(ox: Int, oy: Int) {
        for (y in -1..7) for (x in -1..7) {
            val gx = ox + x
            val gy = oy + y
            if (gx !in 0 until n || gy !in 0 until n) continue
            val edge = x == 0 || x == 6 || y == 0 || y == 6
            val core = x in 2..4 && y in 2..4
            grid[gy][gx] = x in 0..6 && y in 0..6 && (edge || core)
        }
    }
    finder(0, 0)
    finder(n - 7, 0)
    finder(0, n - 7)
    return grid
}
