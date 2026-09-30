package com.example.livora.ui.usage

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.net.Uri
import android.provider.Settings
import android.text.format.DateUtils
import android.text.format.Formatter
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.livora.data.usage.AppUsage
import com.example.livora.data.usage.UnusedApp
import com.example.livora.data.usage.UsageAccess
import com.example.livora.ui.components.AppIcon
import com.example.livora.ui.components.BackButton
import com.example.livora.ui.components.BarChart
import com.example.livora.ui.components.BarPoint
import com.example.livora.ui.components.ChartSlot
import com.example.livora.ui.components.ChoiceOption
import com.example.livora.ui.components.ChoiceRow
import com.example.livora.ui.components.Design
import com.example.livora.ui.components.GrowBar
import com.example.livora.ui.components.Headline
import com.example.livora.ui.components.SectionLabel
import com.example.livora.ui.components.SkeletonBox
import com.example.livora.ui.components.Toaster
import com.example.livora.ui.components.TopBar
import com.example.livora.ui.components.chartColor
import com.example.livora.ui.people.EmptyBlock
import com.example.livora.ui.people.LinkButton

@Composable
fun UsageScreen(onBack: () -> Unit, viewModel: UsageViewModel = viewModel()) {
    val context = LocalContext.current
    val state by viewModel.state.collectAsState()
    val owner = LocalLifecycleOwner.current
    var explaining by rememberSaveable { mutableStateOf(false) }
    var openPackage by rememberSaveable { mutableStateOf<String?>(null) }

    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) viewModel.onResume()
        }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }

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
        when (state.permission) {
            null -> Column(modifier = Modifier.fillMaxSize().padding(innerPadding)) { UsageSkeleton() }

            false -> AccessGate(
                modifier = Modifier.fillMaxSize().padding(innerPadding),
                onOpenSettings = {
                    try {
                        context.startActivity(UsageAccess.settingsIntent(context))
                    } catch (e: ActivityNotFoundException) {
                        Toaster.error("This phone has no usage access screen")
                    }
                },
                onWhy = { explaining = true }
            )

            true -> {
                val view = state.view
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(innerPadding),
                    contentPadding = WindowInsets.navigationBars.asPaddingValues()
                ) {
                    item(key = "range") {
                        Column(modifier = Modifier.padding(horizontal = Design.screenHorizontalPadding, vertical = 12.dp)) {
                            ChoiceRow(
                                options = listOf(
                                    ChoiceOption(UsageRange.Day, "Day"),
                                    ChoiceOption(UsageRange.Week, "Week"),
                                    ChoiceOption(UsageRange.Month, "Month")
                                ),
                                selected = state.range,
                                enabled = true,
                                onSelect = viewModel::setRange
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                val canPrevious = view?.canPrevious == true
                                val canNext = view?.canNext == true
                                IconButton(onClick = viewModel::previous, enabled = canPrevious) {
                                    Icon(
                                        Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                                        contentDescription = "Previous period",
                                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = if (canPrevious) 1f else 0.3f)
                                    )
                                }
                                Text(
                                    text = view?.period.orEmpty(),
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.weight(1f)
                                )
                                IconButton(onClick = viewModel::next, enabled = canNext) {
                                    Icon(
                                        Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                        contentDescription = "Next period",
                                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = if (canNext) 1f else 0.3f)
                                    )
                                }
                            }
                        }
                    }

                    if (view == null) {
                        item(key = "loading") { UsageSkeleton() }
                    } else {
                        item(key = "headline") {
                            Column(modifier = Modifier.padding(horizontal = Design.screenHorizontalPadding)) {
                                Headline(label = view.headlineLabel, value = view.headline, context = view.context)
                                Spacer(modifier = Modifier.height(16.dp))
                                val points = remember(view) {
                                    view.chart.values.mapIndexed { index, value ->
                                        BarPoint(value, view.chart.titles[index], view.chart.valueTexts[index])
                                    }
                                }
                                BarChart(
                                    points = points,
                                    axisLabels = view.chart.axis,
                                    color = chartColor(ChartSlot.Blue),
                                    initialSelected = view.chart.initial,
                                    description = view.chart.description
                                )
                                Text(
                                    text = if (view.hasData) view.hint else "Nothing recorded for this period. Android keeps about a week of detail, so Livora saves your usage from now on.",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(top = 8.dp)
                                )
                            }
                        }

                        if (view.apps.isNotEmpty()) {
                            item(key = "apps-label") {
                                SectionLabel(
                                    text = if (view.range == UsageRange.Day) "Most used apps" else "Most used apps, total",
                                    modifier = Modifier.padding(horizontal = Design.screenHorizontalPadding)
                                )
                            }
                            val top = view.apps.take(10)
                            items(top, key = { "app-${it.packageName}" }) { app ->
                                AppRow(
                                    app = app,
                                    peak = top.first().millis,
                                    first = app === top.first(),
                                    onClick = { openPackage = app.packageName }
                                )
                            }
                        }
                    }

                    val unused = state.unused
                    if (unused == null) {
                        item(key = "unused-loading") {
                            Column(modifier = Modifier.padding(horizontal = Design.screenHorizontalPadding)) {
                                SectionLabel(text = "Not opened in 30 days")
                                repeat(2) { AppRowSkeleton() }
                            }
                        }
                    } else if (unused.isNotEmpty()) {
                        item(key = "unused-label") {
                            val freed = unused.sumOf { it.sizeBytes ?: 0L }
                            Column(modifier = Modifier.padding(horizontal = Design.screenHorizontalPadding)) {
                                SectionLabel(text = "Not opened in 30 days")
                                Text(
                                    text = (if (freed > 0) "Removing these frees ${Formatter.formatShortFileSize(context, freed)}. " else "") +
                                        "Your data in them is deleted with the app.",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        items(unused, key = { "unused-${it.packageName}" }) { app ->
                            UnusedRow(
                                app = app,
                                onUninstall = { uninstall(context, app.packageName) }
                            )
                        }
                    }
                    item(key = "end") { Spacer(modifier = Modifier.height(24.dp)) }
                }

                val selected = openPackage?.let { pkg -> state.view?.apps?.firstOrNull { it.packageName == pkg } }
                if (selected != null && view != null) {
                    AppSheet(
                        app = selected,
                        totalScreenTime = view.total,
                        days = view.days,
                        daily = view.range == UsageRange.Day,
                        lastUsed = state.lastUsed[selected.packageName],
                        onDismiss = { openPackage = null }
                    )
                }
            }
        }
    }

    if (explaining) {
        AlertDialog(
            onDismissRequest = { explaining = false },
            title = { Text("Why usage access") },
            text = {
                Text(
                    "Android keeps the time each app is on screen away from other apps unless you allow Usage access. " +
                        "Livora only reads how long each app was open and when it was last used. It never sees what you do inside an app, and nothing leaves this phone."
                )
            },
            confirmButton = { TextButton(onClick = { explaining = false }) { Text("Got it") } }
        )
    }
}

private fun uninstall(context: Context, packageName: String) {
    try {
        context.startActivity(Intent(Intent.ACTION_DELETE, Uri.parse("package:$packageName")))
    } catch (e: ActivityNotFoundException) {
        Toaster.error("Could not open the uninstall screen")
    }
}

@Composable
private fun AccessGate(modifier: Modifier, onOpenSettings: () -> Unit, onWhy: () -> Unit) {
    Column(modifier = modifier) {
        EmptyBlock(
            title = "Allow usage access",
            body = "Livora reads how long each app is on screen so it can show your screen time. The numbers stay on this phone. You turn this on in system settings, and you can turn it off there at any time.",
            actionLabel = "Open usage access settings",
            onAction = onOpenSettings,
            secondaryLabel = "Why does this need special access?",
            onSecondary = onWhy
        )
    }
}

@Composable
private fun UsageSkeleton() {
    Column(modifier = Modifier.padding(horizontal = Design.screenHorizontalPadding, vertical = 12.dp)) {
        SkeletonBox(modifier = Modifier.fillMaxWidth(0.3f).height(14.dp), shape = RoundedCornerShape(7.dp))
        Spacer(modifier = Modifier.height(8.dp))
        SkeletonBox(modifier = Modifier.fillMaxWidth(0.5f).height(32.dp), shape = RoundedCornerShape(8.dp))
        Spacer(modifier = Modifier.height(20.dp))
        SkeletonBox(modifier = Modifier.fillMaxWidth().height(160.dp), shape = RoundedCornerShape(12.dp))
        Spacer(modifier = Modifier.height(20.dp))
        repeat(4) { AppRowSkeleton() }
    }
}

@Composable
private fun AppRow(app: AppUsage, peak: Long, first: Boolean, onClick: () -> Unit) {
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 68.dp)
                .clickable(role = Role.Button, onClick = onClick)
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
                        text = formatDuration(app.millis),
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))
                GrowBar(
                    fraction = if (peak > 0) app.millis.toFloat() / peak else 0f,
                    color = chartColor(ChartSlot.Blue).copy(alpha = if (first) 1f else 0.55f)
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
            .padding(vertical = 10.dp),
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
private fun UnusedRow(app: UnusedApp, onUninstall: () -> Unit) {
    val context = LocalContext.current
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
                val opened = app.lastUsed?.let { "Last opened ${formatDate(context, it)}" } ?: "Never opened"
                val size = app.sizeBytes?.let { ", ${Formatter.formatShortFileSize(context, it)}" }.orEmpty()
                Text(
                    text = opened + size,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            LinkButton(text = "Uninstall", onClick = onUninstall)
        }
        HorizontalDivider(
            modifier = Modifier.padding(start = 70.dp, end = Design.screenHorizontalPadding),
            color = MaterialTheme.colorScheme.outlineVariant,
            thickness = 0.5.dp
        )
    }
}

private fun formatDate(context: Context, millis: Long): String =
    DateUtils.formatDateTime(context, millis, DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_SHOW_YEAR or DateUtils.FORMAT_ABBREV_MONTH)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AppSheet(
    app: AppUsage,
    totalScreenTime: Long,
    days: Int,
    daily: Boolean,
    lastUsed: Long?,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val sheet = rememberModalBottomSheetState()
    val isUserApp = remember(app.packageName) {
        try {
            context.packageManager.getApplicationInfo(app.packageName, 0).flags and ApplicationInfo.FLAG_SYSTEM == 0
        } catch (e: Exception) {
            false
        }
    }
    val launch = remember(app.packageName) { context.packageManager.getLaunchIntentForPackage(app.packageName) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheet,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AppIcon(packageName = app.packageName, label = app.label, size = 48.dp)
                Column(modifier = Modifier.padding(start = 14.dp)) {
                    Text(
                        text = app.label,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "${formatDuration(app.millis)} in this period",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
            SheetFigure(
                label = "Share of screen time",
                value = if (totalScreenTime > 0) "${Math.round(app.millis * 100.0 / totalScreenTime)}%" else "0%"
            )
            if (!daily && days > 1) {
                SheetFigure(label = "Average per day", value = formatDuration(app.millis / days))
            }
            SheetFigure(
                label = "Last opened",
                value = lastUsed?.let {
                    DateUtils.formatDateTime(
                        context, it,
                        DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_SHOW_TIME or DateUtils.FORMAT_ABBREV_MONTH
                    )
                } ?: "Not known"
            )
            Spacer(modifier = Modifier.height(8.dp))
            if (launch != null) {
                SheetAction(icon = Icons.AutoMirrored.Filled.OpenInNew, label = "Open ${app.label}") {
                    onDismiss()
                    context.startActivity(launch)
                }
            }
            SheetAction(icon = Icons.Default.Info, label = "App info in Settings") {
                onDismiss()
                context.startActivity(
                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${app.packageName}"))
                )
            }
            if (isUserApp) {
                SheetAction(icon = Icons.Default.DeleteOutline, label = "Uninstall") {
                    onDismiss()
                    uninstall(context, app.packageName)
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun SheetFigure(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().heightIn(min = 40.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = label, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            text = value,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun SheetAction(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .clickable(role = Role.Button, onClick = onClick),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(22.dp)
        )
        Text(
            text = label,
            fontSize = 15.sp,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(start = 16.dp)
        )
    }
}
