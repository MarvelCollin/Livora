package com.example.livora.ui.todo

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.livora.data.model.DayActivity
import com.example.livora.data.model.TodoStats
import com.example.livora.ui.components.ActivityGrid
import com.example.livora.ui.components.BarChart
import com.example.livora.ui.components.BarPoint
import com.example.livora.ui.components.ChartSlot
import com.example.livora.ui.components.FigureRow
import com.example.livora.ui.components.GrowBar
import com.example.livora.ui.components.GridDay
import com.example.livora.ui.components.Headline
import com.example.livora.ui.components.SectionLabel
import com.example.livora.ui.components.chartColor
import java.text.SimpleDateFormat
import java.util.Locale

private fun doneText(tasks: Int, minutes: Int): String {
    val count = if (tasks == 1) "1 done" else "$tasks done"
    return if (tasks > 0 && minutes > 0) "$count, ${formatMinutes(minutes)}" else count
}

private fun formatMinutes(minutes: Int): String =
    if (minutes >= 60) "${minutes / 60} h" + if (minutes % 60 > 0) " ${minutes % 60} min" else "" else "$minutes min"

@Composable
fun TaskOverview(
    stats: List<TodoStats>,
    daily: List<DayActivity>,
    heat: List<DayActivity>,
    modifier: Modifier = Modifier
) {
    val total = stats.size
    val done = stats.count { it.isDoneCurrentInterval }
    val streak = stats.maxOfOrNull { it.currentStreak } ?: 0
    val best = stats.maxOfOrNull { it.bestStreak } ?: 0
    val rate = if (stats.isEmpty()) 0 else Math.round(stats.map { it.completionRate }.average() * 100).toInt()
    val accent = chartColor(ChartSlot.Aqua)
    val fullDay = remember { SimpleDateFormat("EEEE, d MMM", Locale.getDefault()) }

    Column(modifier = modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
        Headline(
            label = "Done for now",
            value = "$done of $total",
            context = when {
                total == 0 -> null
                done == total -> "Everything is done. Nice."
                total - done == 1 -> "1 left"
                else -> "${total - done} left"
            }
        )
        Spacer(modifier = Modifier.height(12.dp))
        GrowBar(fraction = if (total == 0) 0f else done.toFloat() / total, color = accent, height = 6.dp)
        Spacer(modifier = Modifier.height(20.dp))
        FigureRow(
            listOf(
                "Streak" to if (streak > 0) "$streak in a row" else "None yet",
                "Best" to if (best > 0) "$best in a row" else "None yet",
                "Success rate" to "$rate%"
            )
        )

        if (daily.isNotEmpty()) {
            SectionLabel(text = "Last 7 days")
            val points = remember(daily) {
                daily.map { BarPoint(it.tasks.toFloat(), fullDay.format(it.dayStart), doneText(it.tasks, it.minutes)) }
            }
            BarChart(
                points = points,
                axisLabels = daily.map { it.label.take(3) },
                color = accent,
                chartHeight = 104.dp,
                initialSelected = daily.lastIndex,
                description = "Routines done each day this week. " + daily.joinToString(", ") { "${it.label} ${it.tasks}" }
            )
            val week = daily.sumOf { it.tasks }
            Text(
                text = if (week == 0) "Nothing done in the last 7 days yet." else "$week done this week",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp)
            )
        }

        if (heat.isNotEmpty()) {
            SectionLabel(text = "Last 5 weeks")
            val todayStart = heat.firstOrNull { it.isToday }?.dayStart ?: Long.MAX_VALUE
            val days = remember(heat) {
                heat.map {
                    GridDay(
                        count = it.tasks,
                        detail = it.label + ", " + doneText(it.tasks, it.minutes),
                        future = it.dayStart > todayStart,
                        today = it.isToday
                    )
                }
            }
            ActivityGrid(
                days = days,
                color = accent,
                description = "Routines done per day over the last 5 weeks. ${heat.sumOf { it.tasks }} in total."
            )
        }
        SectionLabel(text = "Routines")
    }
}
