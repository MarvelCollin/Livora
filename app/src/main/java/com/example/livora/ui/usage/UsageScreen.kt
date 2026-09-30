package com.example.livora.ui.usage

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.livora.ui.components.BackButton
import com.example.livora.ui.components.ChoiceOption
import com.example.livora.ui.components.ChoiceRow
import com.example.livora.ui.components.Design
import com.example.livora.ui.components.Headline
import com.example.livora.ui.components.PreviewNotice
import com.example.livora.ui.components.SectionLabel
import com.example.livora.ui.components.TopBar
import com.example.livora.ui.components.showPreviewOnly
import com.example.livora.ui.people.EmptyBlock
import com.example.livora.ui.people.LinkButton

private enum class UsageRange { Day, Week, Month }

private class AppTime(val name: String, val minutes: Int)
private class UnusedApp(val name: String, val lastOpened: String, val size: String)

private val apps = listOf(
    AppTime("Instagram", 65),
    AppTime("YouTube", 48),
    AppTime("WhatsApp", 36),
    AppTime("Chrome", 22),
    AppTime("Livora", 9)
)

private val unused = listOf(
    UnusedApp("Old Maps Offline", "12 Aug 2026", "312 MB"),
    UnusedApp("Puzzle Quest", "3 Jul 2026", "184 MB"),
    UnusedApp("Scanner Lite", "19 Jun 2026", "46 MB")
)

private val hourly = listOf(0, 0, 0, 0, 0, 0, 4, 12, 9, 6, 3, 8, 14, 10, 5, 7, 11, 18, 24, 38, 48, 30, 12, 3)

@Composable
fun UsageScreen(onBack: () -> Unit) {
    var granted by rememberSaveable { mutableStateOf(false) }
    var range by rememberSaveable { mutableStateOf(UsageRange.Day) }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopBar(
                title = "App usage",
                subtitle = "Screen time and apps you never open",
                navigationIcon = { BackButton(onBack) }
            )
        }
    ) { innerPadding ->
        if (!granted) {
            Column(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
                PreviewNotice()
                EmptyBlock(
                    title = "Allow usage access",
                    body = "Livora reads how long each app is on screen so it can show your screen time. The numbers stay on this phone. You turn this on in system settings, and you can turn it off there at any time.",
                    actionLabel = "Open usage access settings",
                    onAction = { granted = true },
                    secondaryLabel = "Why does this need special access?",
                    onSecondary = { showPreviewOnly() }
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(innerPadding),
                contentPadding = WindowInsets.navigationBars.asPaddingValues()
            ) {
                item(key = "notice") { PreviewNotice() }
                item(key = "range") {
                    Column(modifier = Modifier.padding(horizontal = Design.screenHorizontalPadding, vertical = 12.dp)) {
                        ChoiceRow(
                            options = listOf(
                                ChoiceOption(UsageRange.Day, "Day"),
                                ChoiceOption(UsageRange.Week, "Week"),
                                ChoiceOption(UsageRange.Month, "Month")
                            ),
                            selected = range,
                            enabled = true,
                            onSelect = { range = it }
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(onClick = { showPreviewOnly() }) {
                                Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Previous period", tint = MaterialTheme.colorScheme.onSurface)
                            }
                            Text(
                                text = when (range) {
                                    UsageRange.Day -> "Today, 30 Sep"
                                    UsageRange.Week -> "This week, 28 Sep to 4 Oct"
                                    UsageRange.Month -> "September 2026"
                                },
                                fontSize = 15.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(onClick = {}, enabled = false) {
                                Icon(
                                    Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                    contentDescription = "Next period, not available yet",
                                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                                )
                            }
                        }
                    }
                }
                item(key = "headline") {
                    Column(modifier = Modifier.padding(horizontal = Design.screenHorizontalPadding)) {
                        Headline(
                            label = "Screen time",
                            value = "3 h 12 min",
                            context = "22 min more than yesterday"
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        HourlyChart(values = hourly)
                        Text(
                            text = "Busiest at 20:00 with 48 min. Bars show minutes per hour.",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }
                }
                item(key = "apps-label") {
                    SectionLabel(text = "Most used apps", modifier = Modifier.padding(horizontal = Design.screenHorizontalPadding))
                }
                items(apps.size, key = { "app-$it" }) { index ->
                    AppRow(rank = index + 1, app = apps[index], top = apps.first().minutes)
                }
                item(key = "unused-label") {
                    Column(modifier = Modifier.padding(horizontal = Design.screenHorizontalPadding)) {
                        SectionLabel(text = "Not opened in 30 days")
                        Text(
                            text = "Removing these frees 542 MB. Your data in them is deleted with the app.",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                items(unused.size, key = { "unused-$it" }) { index ->
                    UnusedRow(app = unused[index])
                }
                item(key = "end") { Spacer(modifier = Modifier.height(24.dp)) }
            }
        }
    }
}

private fun formatMinutes(minutes: Int): String =
    if (minutes >= 60) "${minutes / 60} h ${minutes % 60} min" else "$minutes min"

@Composable
private fun HourlyChart(values: List<Int>) {
    val strong = MaterialTheme.colorScheme.primary
    val soft = MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)
    val peak = values.indices.maxByOrNull { values[it] } ?: 0
    val top = (values.maxOrNull() ?: 1).coerceAtLeast(1)
    Column {
        Canvas(modifier = Modifier.fillMaxWidth().height(96.dp)) {
            val slot = size.width / values.size
            val bar = slot * 0.62f
            values.forEachIndexed { hour, minutes ->
                val h = (size.height * minutes / top).coerceAtLeast(if (minutes > 0) 3f else 1.5f)
                drawRoundRect(
                    color = if (hour == peak) strong else soft,
                    topLeft = Offset(hour * slot + (slot - bar) / 2, size.height - h),
                    size = Size(bar, h),
                    cornerRadius = CornerRadius(2f)
                )
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
            horizontalArrangement = androidx.compose.foundation.layout.Arrangement.SpaceBetween
        ) {
            listOf("0", "6", "12", "18", "24").forEach { label ->
                Text(
                    text = label,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun AppRow(rank: Int, app: AppTime, top: Int) {
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 64.dp)
                .padding(horizontal = Design.screenHorizontalPadding, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Apps,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(24.dp)
            )
            Column(modifier = Modifier.weight(1f).padding(horizontal = 14.dp)) {
                Row {
                    Text(
                        text = "$rank. ${app.name}",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = formatMinutes(app.minutes),
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(app.minutes.toFloat() / top)
                            .height(4.dp)
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = if (rank == 1) 1f else 0.5f))
                    )
                }
            }
        }
        HorizontalDivider(
            modifier = Modifier.padding(start = 62.dp, end = Design.screenHorizontalPadding),
            color = MaterialTheme.colorScheme.outlineVariant,
            thickness = 0.5.dp
        )
    }
}

@Composable
private fun UnusedRow(app: UnusedApp) {
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 68.dp)
                .padding(horizontal = Design.screenHorizontalPadding, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = app.name,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "Last opened ${app.lastOpened}, ${app.size}",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            LinkButton(text = "Uninstall", onClick = { showPreviewOnly() })
        }
        HorizontalDivider(
            modifier = Modifier.padding(horizontal = Design.screenHorizontalPadding),
            color = MaterialTheme.colorScheme.outlineVariant,
            thickness = 0.5.dp
        )
    }
}
