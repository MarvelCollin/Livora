package com.example.livora.ui.expenses

import com.example.livora.ui.components.GrowBar
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import com.example.livora.ui.components.ChartSlot
import com.example.livora.ui.components.ColorKey
import com.example.livora.ui.components.chartColor
import com.example.livora.ui.components.chartNeutral
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.livora.ui.components.ChoiceOption
import com.example.livora.ui.components.ChoiceRow
import com.example.livora.ui.components.Design
import com.example.livora.ui.components.Headline
import com.example.livora.ui.components.PreviewNotice
import com.example.livora.ui.components.SectionLabel
import com.example.livora.ui.components.SkeletonBox
import com.example.livora.ui.components.showPreviewOnly
import com.example.livora.ui.people.EmptyBlock
import com.example.livora.ui.people.LinkButton
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow

private enum class MoneyFilter { All, Spent, Received }

@Composable
fun ExpensesPage(addRequests: Flow<Unit>) {
    var loading by remember { mutableStateOf(true) }
    var query by rememberSaveable { mutableStateOf("") }
    var filter by rememberSaveable { mutableStateOf(MoneyFilter.All) }
    var adding by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        delay(450)
        loading = false
    }
    LaunchedEffect(addRequests) { addRequests.collect { adding = true } }

    val shown = ExpenseSamples.transactions.filter { item ->
        val matchesText = query.isBlank() ||
            item.note.contains(query, ignoreCase = true) ||
            item.category.contains(query, ignoreCase = true)
        val matchesType = when (filter) {
            MoneyFilter.All -> true
            MoneyFilter.Spent -> item.amount < 0
            MoneyFilter.Received -> item.amount > 0
        }
        matchesText && matchesType
    }
    val filtering = query.isNotBlank() || filter != MoneyFilter.All

    LazyColumn(modifier = Modifier.fillMaxSize()) {
        item(key = "notice") { PreviewNotice() }

        item(key = "month") {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { showPreviewOnly() }) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Previous month", tint = MaterialTheme.colorScheme.onSurface)
                }
                Text(
                    text = ExpenseSamples.monthLabel,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = {}, enabled = false) {
                    Icon(
                        Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = "Next month, not available for the current month",
                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                    )
                }
            }
        }

        if (loading) {
            item(key = "loading") { ExpensesSkeleton() }
        } else {
            item(key = "headline") {
                Column(modifier = Modifier.padding(horizontal = Design.screenHorizontalPadding, vertical = 8.dp)) {
                    Headline(
                        label = "Spent in September",
                        value = formatRupiah(ExpenseSamples.spent),
                        context = "${formatRupiah(ExpenseSamples.lastMonthDifference)} less than August"
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    BudgetLine(spent = ExpenseSamples.spent, budget = ExpenseSamples.budget)
                }
            }

            item(key = "categories") {
                Column(modifier = Modifier.padding(horizontal = Design.screenHorizontalPadding)) {
                    SectionLabel(text = "Where it went")
                    val top = ExpenseSamples.categories.maxOf { it.second }
                    ExpenseSamples.categories.forEach { (name, amount) ->
                        CategoryBar(
                            name = name,
                            amount = amount,
                            fraction = amount.toFloat() / top,
                            color = categoryColor(name),
                            selected = query.equals(name, ignoreCase = true),
                            onClick = { query = if (query.equals(name, ignoreCase = true)) "" else name }
                        )
                    }
                    Text(
                        text = "Tap a category to see only its transactions.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 6.dp)
                    )
                }
            }

            item(key = "search") {
                Column(modifier = Modifier.padding(horizontal = Design.screenHorizontalPadding)) {
                    SectionLabel(text = "Transactions")
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        placeholder = { Text("Search notes and categories") },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                        trailingIcon = {
                            if (query.isNotEmpty()) {
                                IconButton(onClick = { query = "" }) {
                                    Icon(Icons.Default.Clear, contentDescription = "Clear search")
                                }
                            }
                        },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    ChoiceRow(
                        options = listOf(
                            ChoiceOption(MoneyFilter.All, "All"),
                            ChoiceOption(MoneyFilter.Spent, "Spent"),
                            ChoiceOption(MoneyFilter.Received, "Received")
                        ),
                        selected = filter,
                        enabled = true,
                        onSelect = { filter = it }
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth().heightIn(min = 40.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (filtering) {
                                "Showing ${shown.size} of ${ExpenseSamples.transactions.size}"
                            } else {
                                "${ExpenseSamples.transactions.size} transactions"
                            },
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f)
                        )
                        if (filtering) {
                            LinkButton(text = "Clear filters", onClick = {
                                query = ""
                                filter = MoneyFilter.All
                            })
                        }
                    }
                }
            }

            if (shown.isEmpty()) {
                item(key = "empty") {
                    EmptyBlock(
                        title = "Nothing matches",
                        body = "Try a different word or clear the filters to see every transaction.",
                        actionLabel = "Clear filters",
                        onAction = {
                            query = ""
                            filter = MoneyFilter.All
                        }
                    )
                }
            }

            val days = shown.groupBy { it.day }
            days.forEach { (day, rows) ->
                item(key = "day-$day") { DayHeader(day = day, total = rows.sumOf { it.amount }) }
                items(rows, key = { it.id }) { item ->
                    Column(modifier = Modifier.animateItem()) {
                        TransactionRow(item)
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
    }

    if (adding) {
        AddExpenseSheet(onDismiss = { adding = false })
    }
}

@Composable
private fun BudgetLine(spent: Long, budget: Long) {
    val fraction = (spent.toFloat() / budget).coerceIn(0f, 1f)
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = "Budget ${formatRupiah(budget)}",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = "${(fraction * 100).toInt()}% used",
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
        Spacer(modifier = Modifier.height(6.dp))
        GrowBar(fraction = fraction, color = MaterialTheme.colorScheme.primary, height = 6.dp)
        Text(
            text = "${formatRupiah((budget - spent).coerceAtLeast(0))} left this month",
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 6.dp)
        )
    }
}

@Composable
private fun categoryColor(name: String): Color = when (name) {
    "Food" -> chartColor(ChartSlot.Orange)
    "Groceries" -> chartColor(ChartSlot.Aqua)
    "Transport" -> chartColor(ChartSlot.Blue)
    "Bills" -> chartColor(ChartSlot.Violet)
    "Fun" -> chartColor(ChartSlot.Magenta)
    else -> chartNeutral()
}

@Composable
private fun CategoryBar(
    name: String,
    amount: Long,
    fraction: Float,
    color: Color,
    selected: Boolean,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(vertical = 6.dp)
    ) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            ColorKey(color = color)
            Text(
                text = name,
                fontSize = 14.sp,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f).padding(start = 8.dp)
            )
            Text(
                text = formatRupiah(amount),
                fontSize = 14.sp,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        GrowBar(fraction = fraction.coerceIn(0.02f, 1f), color = color)
    }
}

@Composable
private fun DayHeader(day: String, total: Long) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Design.screenHorizontalPadding)
            .padding(top = 16.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = day,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = signedRupiah(total),
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun TransactionRow(item: Transaction) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .clickable { showPreviewOnly() }
            .padding(horizontal = Design.screenHorizontalPadding, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.note,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Row(modifier = Modifier.padding(top = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                ColorKey(color = categoryColor(item.category))
                Text(
                    text = "${item.category}, ${item.account}",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 6.dp)
                )
            }
        }
        Text(
            text = signedRupiah(item.amount),
            fontSize = 15.sp,
            fontWeight = if (item.amount > 0) FontWeight.SemiBold else FontWeight.Normal,
            color = if (item.amount > 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun ExpensesSkeleton() {
    Column(modifier = Modifier.padding(horizontal = Design.screenHorizontalPadding, vertical = 8.dp)) {
        SkeletonBox(modifier = Modifier.fillMaxWidth(0.35f).height(14.dp), shape = RoundedCornerShape(7.dp))
        Spacer(modifier = Modifier.height(8.dp))
        SkeletonBox(modifier = Modifier.fillMaxWidth(0.6f).height(32.dp), shape = RoundedCornerShape(8.dp))
        Spacer(modifier = Modifier.height(20.dp))
        repeat(6) {
            SkeletonBox(modifier = Modifier.fillMaxWidth().height(48.dp), shape = RoundedCornerShape(8.dp))
            Spacer(modifier = Modifier.height(10.dp))
        }
    }
}
