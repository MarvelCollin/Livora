package com.example.livora.ui.expenses

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.livora.data.expenses.CategoryTotal
import com.example.livora.data.expenses.Money
import com.example.livora.data.expenses.Granularity
import com.example.livora.data.expenses.PeriodSummary
import com.example.livora.ui.components.BarChart
import com.example.livora.ui.components.BarPoint
import com.example.livora.ui.components.ChartSlot
import com.example.livora.ui.components.ColorKey
import com.example.livora.ui.components.DonutChart
import com.example.livora.ui.components.DonutSlice
import com.example.livora.ui.components.chartColor
import com.example.livora.ui.components.chartNeutral
import java.time.LocalDate

const val OTHERS_ID = -1L
private const val MAX_SLICES = 7

class FoldedCategories(val shown: List<CategoryTotal>, val folded: List<CategoryTotal>) {
    val foldedIds: Set<Long> get() = folded.map { it.categoryId }.toSet()
}

fun foldCategories(all: List<CategoryTotal>): FoldedCategories =
    if (all.size <= MAX_SLICES + 1) FoldedCategories(all, emptyList())
    else FoldedCategories(all.take(MAX_SLICES), all.drop(MAX_SLICES))

@Composable
fun SpendingChart(summary: PeriodSummary, today: LocalDate, modifier: Modifier = Modifier) {
    val period = summary.period
    val granularity = period.granularity
    val buckets = summary.buckets
    val points = remember(buckets) {
        buckets.map { BarPoint(it.spent.toFloat(), bucketTitle(it, granularity), Money.format(it.spent)) }
    }
    val labels = remember(buckets, period) { axisLabels(period, buckets) }
    val initial = buckets.indexOfFirst { today >= it.from && today <= it.to }.takeIf { it >= 0 }
    val busiest = buckets.indices.maxByOrNull { buckets[it].spent }
    val unit = when (granularity) {
        Granularity.Day -> "day"
        Granularity.Week -> "week"
        Granularity.Month -> "month"
    }
    Column(modifier = modifier) {
        BarChart(
            points = points,
            axisLabels = labels,
            color = chartColor(ChartSlot.Blue),
            chartHeight = 132.dp,
            initialSelected = initial,
            description = "Spending for each $unit of ${periodTitle(period, today)}. Total ${Money.format(summary.spent)}."
        )
        if (busiest != null && buckets[busiest].spent > 0) {
            Text(
                text = "Highest $unit ${bucketTitle(buckets[busiest], granularity)}, ${Money.format(buckets[busiest].spent)}",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp)
            )
        }
    }
}

@Composable
fun CategoryBreakdown(
    summary: PeriodSummary,
    selectedId: Long?,
    onSelect: (Long?) -> Unit,
    modifier: Modifier = Modifier
) {
    val folded = remember(summary.categories) { foldCategories(summary.categories) }
    val neutral = chartNeutral()
    val icons = HashMap<Long, String>()
    folded.shown.forEach { icons[it.categoryId] = it.iconKey }
    val rows = folded.shown.map { it.categoryId to it }
    val slices = folded.shown.map { total ->
        DonutSlice(
            id = total.categoryId,
            label = total.name,
            value = total.amount.toFloat(),
            valueText = Money.format(total.amount),
            shareText = "${percentText(total.amount, summary.spent)} of spending",
            color = if (total.slot < 0) neutral else chartColor(total.slot)
        )
    }.toMutableList()
    if (folded.folded.isNotEmpty()) {
        val amount = folded.folded.sumOf { it.amount }
        slices.add(
            DonutSlice(
                id = OTHERS_ID,
                label = "Others",
                value = amount.toFloat(),
                valueText = Money.format(amount),
                shareText = "${percentText(amount, summary.spent)} of spending",
                color = neutral
            )
        )
    }
    val describe = slices.joinToString(", ") { "${it.label} ${it.valueText}" }

    Column(modifier = modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        DonutChart(
            slices = slices,
            selectedId = selectedId,
            onSelect = onSelect,
            centerLabel = "Spent",
            centerValue = Money.format(summary.spent),
            description = "Spending by category, $describe"
        )
        Spacer(modifier = Modifier.height(12.dp))
        Column(modifier = Modifier.fillMaxWidth()) {
            slices.forEach { slice ->
                val chosen = slice.id == selectedId
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .clickable(role = Role.Button) { onSelect(if (chosen) null else slice.id) },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = CategoryIcons.vector(icons[slice.id] ?: "other"),
                        contentDescription = null,
                        tint = slice.color,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = slice.label,
                        fontSize = 14.sp,
                        fontWeight = if (chosen) FontWeight.Bold else FontWeight.Normal,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f).padding(start = 10.dp)
                    )
                    Text(
                        text = percentText(slice.value.toLong(), summary.spent),
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(end = 16.dp)
                    )
                    Text(
                        text = slice.valueText,
                        fontSize = 14.sp,
                        fontWeight = if (chosen) FontWeight.Bold else FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
        if (rows.isNotEmpty()) {
            Text(
                text = "Tap a category to see only its transactions.",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
            )
        }
    }
}

@Composable
fun HistoryChart(summary: PeriodSummary, modifier: Modifier = Modifier) {
    val history = summary.history ?: return
    val granularity = summary.historyGranularity
    val points = remember(history) {
        history.map { BarPoint(it.spent.toFloat(), bucketTitle(it, granularity), Money.format(it.spent)) }
    }
    val labels = remember(history) { history.map { historyAxis(it, granularity) } }
    val active = history.filter { it.spent > 0 }
    val unit = if (granularity == Granularity.Week) "week" else "month"
    Column(modifier = modifier) {
        BarChart(
            points = points,
            axisLabels = labels,
            color = chartColor(ChartSlot.Blue),
            chartHeight = 112.dp,
            initialSelected = history.lastIndex,
            description = "Spending for the last ${history.size} ${unit}s, " +
                history.joinToString(", ") { "${historyAxis(it, granularity)} ${Money.format(it.spent)}" }
        )
        if (active.size >= 2) {
            Text(
                text = "Average ${Money.format(active.sumOf { it.spent } / active.size)} a $unit over ${active.size} ${unit}s",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp)
            )
        }
    }
}

@Composable
fun KeyFigures(summary: PeriodSummary, modifier: Modifier = Modifier) {
    Row(modifier = modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Figure(label = "Received", value = Money.format(summary.received), modifier = Modifier.weight(1f))
        Figure(
            label = "Net",
            value = Money.signed(summary.net).let { if (summary.net == 0L) Money.format(0) else it },
            modifier = Modifier.weight(1f)
        )
        Figure(label = "Per day", value = Money.format(summary.averagePerDay), modifier = Modifier.weight(1f))
    }
}

@Composable
private fun Figure(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Text(
            text = label,
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            modifier = Modifier.padding(top = 2.dp)
        )
    }
}
