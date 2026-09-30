package com.example.livora.ui.usage

import androidx.compose.foundation.layout.Arrangement
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import com.example.livora.ui.components.SkeletonBox
import androidx.compose.runtime.produceState
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.size
import com.example.livora.ui.components.GrowBar
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.livora.data.apps.AppInfo
import com.example.livora.data.apps.InstalledApps
import com.example.livora.ui.components.AppIcon
import com.example.livora.ui.components.BackButton
import com.example.livora.ui.components.BarChart
import com.example.livora.ui.components.BarPoint
import com.example.livora.ui.components.ChartSlot
import com.example.livora.ui.components.ChoiceOption
import com.example.livora.ui.components.ChoiceRow
import com.example.livora.ui.components.Design
import com.example.livora.ui.components.Headline
import com.example.livora.ui.components.PreviewNotice
import com.example.livora.ui.components.SectionLabel
import com.example.livora.ui.components.TopBar
import com.example.livora.ui.components.chartColor
import com.example.livora.ui.components.showPreviewOnly
import com.example.livora.ui.people.EmptyBlock
import com.example.livora.ui.people.LinkButton

private enum class UsageRange { Day, Week, Month }

private class RangeData(
    val period: String,
    val headlineLabel: String,
    val headline: String,
    val context: String,
    val points: List<BarPoint>,
    val axis: List<String?>,
    val hint: String,
    val factor: Int,
    val description: String
)

private val topMinutes = listOf(65, 48, 36, 22, 9)
private val unusedSizes = listOf("312 MB", "184 MB", "46 MB")
private val unusedDates = listOf("12 Aug 2026", "3 Jul 2026", "19 Jun 2026")

private val hourly = listOf(0, 0, 0, 0, 0, 0, 4, 12, 9, 6, 3, 8, 14, 10, 5, 7, 11, 18, 24, 38, 48, 30, 12, 3)
private val weekly = listOf(172, 210, 178, 240, 196, 268, 192)
private val weekNames = listOf("Sunday", "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday")
private val monthly = List(30) { 120 + ((it * 37 + 11) % 150) }

private fun formatMinutes(minutes: Int): String =
    if (minutes >= 60) "${minutes / 60} h ${minutes % 60} min" else "$minutes min"

private fun dataFor(range: UsageRange): RangeData = when (range) {
    UsageRange.Day -> RangeData(
        period = "Today, 30 Sep",
        headlineLabel = "Screen time",
        headline = "3 h 12 min",
        context = "22 min more than yesterday",
        points = hourly.mapIndexed { hour, minutes ->
            BarPoint(minutes.toFloat(), "%02d:00 to %02d:00".format(hour, (hour + 1) % 24), formatMinutes(minutes))
        },
        axis = List(24) { if (it in listOf(0, 6, 12, 18, 23)) "$it" else null },
        hint = "Touch or drag along the bars to see each hour.",
        factor = 1,
        description = "Bar chart of minutes per hour today. Busiest at 20:00 with 48 minutes."
    )
    UsageRange.Week -> RangeData(
        period = "This week, 27 Sep to 3 Oct",
        headlineLabel = "Daily average",
        headline = "3 h 34 min",
        context = "18 min less than last week",
        points = weekly.mapIndexed { day, minutes -> BarPoint(minutes.toFloat(), weekNames[day], formatMinutes(minutes)) },
        axis = weekNames.map { it.take(3) },
        hint = "Touch or drag along the bars to see each day.",
        factor = 7,
        description = "Bar chart of screen time for each day this week. Saturday is the highest with 4 hours 28 minutes."
    )
    UsageRange.Month -> RangeData(
        period = "September 2026",
        headlineLabel = "Daily average",
        headline = "3 h 27 min",
        context = "9 min more than August",
        points = monthly.mapIndexed { day, minutes -> BarPoint(minutes.toFloat(), "${day + 1} Sep", formatMinutes(minutes)) },
        axis = List(30) { if (it in listOf(0, 7, 14, 21, 28)) "${it + 1}" else null },
        hint = "Touch or drag along the bars to see each day.",
        factor = 28,
        description = "Bar chart of screen time for each day of September."
    )
}

@Composable
fun UsageScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    var granted by rememberSaveable { mutableStateOf(false) }
    var range by rememberSaveable { mutableStateOf(UsageRange.Day) }
    val loaded by produceState<List<AppInfo>?>(null) {
        value = withContext(Dispatchers.Default) { InstalledApps.launcherApps(context) }
    }
    val apps = loaded.orEmpty()
    val top = apps.take(topMinutes.size)
    val unused = apps.drop(topMinutes.size).take(unusedSizes.size)
    val data = remember(range) { dataFor(range) }

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
                if (top.isNotEmpty()) {
                    Row(
                        modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 24.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        top.take(4).forEach { app ->
                            AppIcon(packageName = app.packageName, label = app.label, size = 44.dp)
                        }
                    }
                }
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
                                text = data.period,
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
                        Headline(label = data.headlineLabel, value = data.headline, context = data.context)
                        Spacer(modifier = Modifier.height(16.dp))
                        BarChart(
                            points = data.points,
                            axisLabels = data.axis,
                            color = chartColor(ChartSlot.Blue),
                            description = data.description
                        )
                        Text(
                            text = data.hint,
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }
                }
                item(key = "apps-label") {
                    SectionLabel(
                        text = if (range == UsageRange.Day) "Most used apps" else "Most used apps, total",
                        modifier = Modifier.padding(horizontal = Design.screenHorizontalPadding)
                    )
                }
                if (loaded == null) {
                    items(topMinutes.size, key = { "app-skeleton-$it" }) { AppRowSkeleton() }
                } else if (top.isEmpty()) {
                    item(key = "no-apps") {
                        Text(
                            text = "No launchable apps were found on this phone.",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = Design.screenHorizontalPadding, vertical = 12.dp)
                        )
                    }
                }
                items(top.size, key = { "app-${top[it].packageName}" }) { index ->
                    AppRow(
                        rank = index + 1,
                        app = top[index],
                        minutes = topMinutes[index] * data.factor,
                        peak = topMinutes.first() * data.factor
                    )
                }
                if (unused.isNotEmpty()) {
                    item(key = "unused-label") {
                        Column(modifier = Modifier.padding(horizontal = Design.screenHorizontalPadding)) {
                            SectionLabel(text = "Not opened in 30 days")
                            Text(
                                text = "Removing these frees ${unused.indices.sumOf { unusedSizes[it].substringBefore(' ').toInt() }} MB. Your data in them is deleted with the app.",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    items(unused.size, key = { "unused-${unused[it].packageName}" }) { index ->
                        UnusedRow(app = unused[index], lastOpened = unusedDates[index], size = unusedSizes[index])
                    }
                }
                item(key = "end") { Spacer(modifier = Modifier.height(24.dp)) }
            }
        }
    }
}

@Composable
private fun AppRow(rank: Int, app: AppInfo, minutes: Int, peak: Int) {
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 68.dp)
                .padding(horizontal = Design.screenHorizontalPadding, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AppIcon(packageName = app.packageName, label = app.label, size = 40.dp)
            Column(modifier = Modifier.weight(1f).padding(start = 14.dp)) {
                Row {
                    Text(
                        text = app.label,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = formatMinutes(minutes),
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))
                GrowBar(
                    fraction = minutes.toFloat() / peak,
                    color = chartColor(ChartSlot.Blue).copy(alpha = if (rank == 1) 1f else 0.55f)
                )
            }
        }
        HorizontalDivider(
            modifier = Modifier.padding(start = 70.dp, end = Design.screenHorizontalPadding),
            color = MaterialTheme.colorScheme.outlineVariant,
            thickness = 0.5.dp
        )
    }
}

@Composable
private fun AppRowSkeleton() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 68.dp)
            .padding(horizontal = Design.screenHorizontalPadding, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        SkeletonBox(modifier = Modifier.size(40.dp), shape = RoundedCornerShape(10.dp))
        Column(modifier = Modifier.weight(1f).padding(start = 14.dp)) {
            SkeletonBox(modifier = Modifier.width(120.dp).height(14.dp), shape = RoundedCornerShape(7.dp))
            Spacer(modifier = Modifier.height(10.dp))
            SkeletonBox(modifier = Modifier.fillMaxWidth().height(4.dp), shape = RoundedCornerShape(2.dp))
        }
    }
}

@Composable
private fun UnusedRow(app: AppInfo, lastOpened: String, size: String) {
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 72.dp)
                .padding(horizontal = Design.screenHorizontalPadding, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AppIcon(packageName = app.packageName, label = app.label, size = 40.dp)
            Column(modifier = Modifier.weight(1f).padding(start = 14.dp)) {
                Text(
                    text = app.label,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1
                )
                Text(
                    text = "Last opened $lastOpened, $size",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            LinkButton(text = "Uninstall", onClick = { showPreviewOnly() })
        }
        HorizontalDivider(
            modifier = Modifier.padding(start = 70.dp, end = Design.screenHorizontalPadding),
            color = MaterialTheme.colorScheme.outlineVariant,
            thickness = 0.5.dp
        )
    }
}
