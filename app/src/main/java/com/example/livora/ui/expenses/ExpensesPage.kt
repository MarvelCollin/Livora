package com.example.livora.ui.expenses

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.TrendingDown
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDateRangePickerState
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
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.livora.data.expenses.ExpenseEntity
import com.example.livora.data.expenses.Money
import com.example.livora.data.expenses.Granularity
import com.example.livora.data.expenses.Period
import com.example.livora.data.expenses.PeriodKind
import com.example.livora.data.expenses.PeriodSummary
import com.example.livora.ui.components.ChoiceOption
import com.example.livora.ui.components.ChoiceRow
import com.example.livora.ui.components.Design
import com.example.livora.ui.components.GrowBar
import com.example.livora.ui.components.Headline
import com.example.livora.ui.components.SectionLabel
import com.example.livora.ui.components.SkeletonBox
import com.example.livora.ui.components.statusGood
import com.example.livora.ui.people.EmptyBlock
import com.example.livora.ui.people.LinkButton
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate
import java.time.Instant
import java.time.ZoneOffset

private enum class MoneyFilter { All, Spent, Received }

@Composable
fun ExpensesPage(
    addRequests: Flow<Unit>,
    exportRequests: Flow<Unit>,
    viewModel: ExpensesViewModel = viewModel()
) {
    val state by viewModel.state.collectAsState()
    var query by rememberSaveable { mutableStateOf("") }
    var filter by rememberSaveable { mutableStateOf(MoneyFilter.All) }
    var selectedCategory by rememberSaveable { mutableStateOf<Long?>(null) }
    var adding by rememberSaveable { mutableStateOf(false) }
    var editingId by rememberSaveable { mutableStateOf<Long?>(null) }
    var budgeting by rememberSaveable { mutableStateOf(false) }
    var pickingRange by rememberSaveable { mutableStateOf(false) }

    val exporter = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        if (uri != null) viewModel.export(uri)
    }

    LaunchedEffect(addRequests) { addRequests.collect { adding = true } }
    LaunchedEffect(exportRequests) { exportRequests.collect { exporter.launch("livora-expenses-${LocalDate.now()}.csv") } }
    LaunchedEffect(state.period.from, state.period.to) { selectedCategory = null }

    val summary = state.summary
    val folded = remember(summary?.categories) { summary?.let { foldCategories(it.categories) } }
    val categoryIds: Set<Long>? = when (selectedCategory) {
        null -> null
        OTHERS_ID -> folded?.foldedIds.orEmpty()
        else -> setOf(selectedCategory!!)
    }
    val shown = remember(state.rows, query, filter, categoryIds, state.categories, state.accounts) {
        state.rows.filter { item ->
            val category = state.categoryById[item.categoryId]?.name.orEmpty()
            val account = state.accountById[item.accountId]?.name.orEmpty()
            val matchesText = query.isBlank() ||
                item.note.contains(query, ignoreCase = true) ||
                category.contains(query, ignoreCase = true) ||
                account.contains(query, ignoreCase = true)
            val matchesType = when (filter) {
                MoneyFilter.All -> true
                MoneyFilter.Spent -> item.amount < 0
                MoneyFilter.Received -> item.amount > 0
            }
            val matchesCategory = categoryIds == null || item.categoryId in categoryIds
            matchesText && matchesType && matchesCategory
        }
    }
    val filtering = query.isNotBlank() || filter != MoneyFilter.All || selectedCategory != null
    val clearFilters = {
        query = ""
        filter = MoneyFilter.All
        selectedCategory = null
    }

    LazyColumn(modifier = Modifier.fillMaxSize()) {
        when {
            state.loading -> item(key = "loading") { ExpensesSkeleton() }

            !state.hasAny -> item(key = "first") {
                EmptyBlock(
                    title = "No transactions yet",
                    body = "Add what you spend or receive and this page turns into charts of where your money goes.",
                    actionLabel = "Add expense",
                    onAction = { adding = true }
                )
            }

            summary != null -> {
                item(key = "period") {
                    PeriodBar(
                        state = state,
                        onKind = { kind -> if (kind == PeriodKind.Custom) pickingRange = true else viewModel.setKind(kind) },
                        onPrevious = viewModel::previous,
                        onNext = viewModel::next,
                        onEditRange = { pickingRange = true }
                    )
                }

                item(key = "headline") {
                    Column(modifier = Modifier.padding(horizontal = Design.screenHorizontalPadding, vertical = 8.dp)) {
                        Headline(
                            label = spentLabel(state.period, state.today),
                            value = Money.format(summary.spent)
                        )
                        Comparison(summary = summary, today = state.today)
                        if (state.period.kind == PeriodKind.Month) {
                            Spacer(modifier = Modifier.height(16.dp))
                            BudgetLine(
                                summary = summary,
                                budget = state.budget,
                                today = state.today,
                                onEdit = { budgeting = true }
                            )
                        }
                        Spacer(modifier = Modifier.height(20.dp))
                        KeyFigures(summary = summary)
                        summary.biggest?.let { biggest ->
                            BiggestLine(item = biggest, name = state.categoryById[biggest.categoryId]?.name)
                        }
                    }
                }

                if (summary.count == 0) {
                    item(key = "empty-month") {
                        EmptyBlock(
                            title = "Nothing logged in ${periodTitle(state.period, state.today)}",
                            body = "Add a transaction or pick another period.",
                            actionLabel = "Add expense",
                            onAction = { adding = true }
                        )
                    }
                } else {
                    if (summary.spent > 0) {
                        item(key = "daily") {
                            Column(modifier = Modifier.padding(horizontal = Design.screenHorizontalPadding)) {
                                SectionLabel(
                                    text = when (state.period.granularity) {
                                        Granularity.Day -> "Daily spending"
                                        Granularity.Week -> "Weekly spending"
                                        Granularity.Month -> "Monthly spending"
                                    }
                                )
                                SpendingChart(summary = summary, today = state.today)
                            }
                        }
                        item(key = "categories") {
                            Column(modifier = Modifier.padding(horizontal = Design.screenHorizontalPadding)) {
                                SectionLabel(text = "Where it went")
                                CategoryBreakdown(
                                    summary = summary,
                                    selectedId = selectedCategory,
                                    onSelect = { id ->
                                        selectedCategory = id
                                        if (id != null && filter == MoneyFilter.Received) filter = MoneyFilter.All
                                    }
                                )
                            }
                        }
                    }
                    summary.history?.let { history ->
                        item(key = "trend") {
                            Column(modifier = Modifier.padding(horizontal = Design.screenHorizontalPadding)) {
                                SectionLabel(
                                    text = "Last ${history.size} " + if (summary.historyGranularity == Granularity.Week) "weeks" else "months"
                                )
                                HistoryChart(summary = summary)
                            }
                        }
                    }
                }

                item(key = "search") {
                    Column(modifier = Modifier.padding(horizontal = Design.screenHorizontalPadding)) {
                        SectionLabel(text = "Transactions")
                        OutlinedTextField(
                            value = query,
                            onValueChange = { query = it },
                            placeholder = { Text("Search transactions") },
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
                            val scope = selectedCategory?.let { id ->
                                if (id == OTHERS_ID) "Others" else state.categoryById[id]?.name
                            }
                            Text(
                                text = when {
                                    filtering -> "Showing ${shown.size} of ${state.rows.size}" + (scope?.let { " in $it" } ?: "")
                                    else -> "${state.rows.size} transactions"
                                },
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.weight(1f)
                            )
                            if (filtering) LinkButton(text = "Clear filters", onClick = clearFilters)
                        }
                    }
                }

                if (shown.isEmpty() && state.rows.isNotEmpty()) {
                    item(key = "empty") {
                        EmptyBlock(
                            title = "Nothing matches",
                            body = "Try a different word or clear the filters to see every transaction.",
                            actionLabel = "Clear filters",
                            onAction = clearFilters
                        )
                    }
                }

                val days = shown.groupBy { it.day }
                days.forEach { (day, rows) ->
                    item(key = "day-$day") {
                        DayHeader(
                            title = dayLabel(LocalDate.ofEpochDay(day), state.today),
                            total = rows.sumOf { it.amount }
                        )
                    }
                    items(rows, key = { it.id }) { item ->
                        Column(modifier = Modifier.animateItem()) {
                            TransactionRow(
                                item = item,
                                category = state.categoryById[item.categoryId]?.name ?: "Uncategorized",
                                slot = state.categoryById[item.categoryId]?.slot ?: -1,
                                iconKey = state.categoryById[item.categoryId]?.iconKey ?: "other",
                                account = state.accountById[item.accountId]?.name ?: "Unknown",
                                onClick = { editingId = item.id }
                            )
                            HorizontalDivider(
                                modifier = Modifier.padding(horizontal = Design.screenHorizontalPadding),
                                color = MaterialTheme.colorScheme.outlineVariant,
                                thickness = 0.5.dp
                            )
                        }
                    }
                }
                item(key = "end") { Spacer(modifier = Modifier.height(24.dp).navigationBarsPadding()) }
            }
        }
    }

    if (adding || editingId != null) {
        val existing = editingId?.let { id -> state.rows.firstOrNull { it.id == id } }
        if (editingId == null || existing != null) {
            ExpenseSheet(
                existing = existing,
                state = state,
                onSave = viewModel::save,
                onDelete = viewModel::delete,
                onAddCategory = viewModel::addCategory,
                onAddAccount = viewModel::addAccount,
                onDismiss = {
                    adding = false
                    editingId = null
                }
            )
        }
    }

    if (pickingRange) {
        RangePickerDialog(
            initial = if (state.period.kind == PeriodKind.Custom) state.period else viewModel.currentCustom(),
            today = state.today,
            onConfirm = { from, to ->
                viewModel.setCustom(from, to)
                pickingRange = false
            },
            onDismiss = { pickingRange = false }
        )
    }

    if (budgeting) {
        BudgetSheet(
            current = state.budget,
            onSave = viewModel::setBudget,
            onDismiss = { budgeting = false }
        )
    }
}

@Composable
private fun PeriodBar(
    state: ExpensesUiState,
    onKind: (PeriodKind) -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onEditRange: () -> Unit
) {
    val custom = state.period.kind == PeriodKind.Custom
    Column(modifier = Modifier.padding(horizontal = Design.screenHorizontalPadding, vertical = 8.dp)) {
        ChoiceRow(
            options = listOf(
                ChoiceOption(PeriodKind.Week, "Week"),
                ChoiceOption(PeriodKind.Month, "Month"),
                ChoiceOption(PeriodKind.Year, "Year"),
                ChoiceOption(PeriodKind.Custom, "Custom")
            ),
            selected = state.period.kind,
            enabled = true,
            onSelect = onKind
        )
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (custom) {
                Text(
                    text = periodTitle(state.period, state.today),
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f).padding(vertical = 12.dp)
                )
                LinkButton(text = "Change dates", onClick = onEditRange)
            } else {
                IconButton(onClick = onPrevious, enabled = state.canPrevious) {
                    Icon(
                        Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                        contentDescription = "Previous period",
                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = if (state.canPrevious) 1f else 0.3f)
                    )
                }
                Text(
                    text = periodTitle(state.period, state.today),
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = onNext, enabled = state.canNext) {
                    Icon(
                        Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = "Next period",
                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = if (state.canNext) 1f else 0.3f)
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RangePickerDialog(
    initial: Period?,
    today: LocalDate,
    onConfirm: (LocalDate, LocalDate) -> Unit,
    onDismiss: () -> Unit
) {
    val todayMillis = today.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
    val from = initial?.from ?: today.minusDays(29)
    val to = initial?.to ?: today
    val picker = rememberDateRangePickerState(
        initialSelectedStartDateMillis = from.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
        initialSelectedEndDateMillis = to.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
        selectableDates = object : SelectableDates {
            override fun isSelectableDate(utcTimeMillis: Long): Boolean = utcTimeMillis <= todayMillis
        }
    )
    val start = picker.selectedStartDateMillis
    val end = picker.selectedEndDateMillis
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                enabled = start != null && end != null,
                onClick = {
                    if (start != null && end != null) {
                        onConfirm(
                            Instant.ofEpochMilli(start).atZone(ZoneOffset.UTC).toLocalDate(),
                            Instant.ofEpochMilli(end).atZone(ZoneOffset.UTC).toLocalDate()
                        )
                    }
                }
            ) { Text("Apply") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    ) {
        DateRangePicker(
            state = picker,
            modifier = Modifier.height(520.dp),
            title = { Text("Pick a range", modifier = Modifier.padding(start = 24.dp, top = 16.dp)) }
        )
    }
}

@Composable
private fun Comparison(summary: PeriodSummary, today: LocalDate) {
    val period = summary.period
    val current = today in period && period.kind != PeriodKind.Custom
    val previous = previousName(period, today)
    val change = summary.change
    val hasBasis = summary.previousToDate > 0 || summary.previousSpent > 0
    val span = when (period.kind) {
        PeriodKind.Week -> "week"
        PeriodKind.Month -> "month"
        PeriodKind.Year -> "year"
        PeriodKind.Custom -> "period"
    }
    Column(modifier = Modifier.padding(top = 4.dp)) {
        if (hasBasis) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (change != 0L) {
                    Icon(
                        imageVector = if (change < 0) Icons.AutoMirrored.Filled.TrendingDown else Icons.AutoMirrored.Filled.TrendingUp,
                        contentDescription = null,
                        tint = if (change < 0) statusGood() else MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(18.dp)
                    )
                }
                Text(
                    text = when {
                        change == 0L -> "Same as $previous" + if (current) " so far" else ""
                        else -> "${Money.format(change)} ${if (change < 0) "less" else "more"} than $previous" +
                            if (current) " at this point" else ""
                    },
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = if (change != 0L) 6.dp else 0.dp)
                )
            }
        } else {
            Text(
                text = "Nothing to compare with in $previous",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        summary.projected?.let { projected ->
            Text(
                text = "On pace for ${Money.format(projected)} by the end of the $span",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp)
            )
        }
    }
}

@Composable
private fun BudgetLine(summary: PeriodSummary, budget: Long?, today: LocalDate, onEdit: () -> Unit) {
    if (budget == null) {
        Row(
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "No monthly budget yet",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f)
            )
            LinkButton(text = "Set budget", onClick = onEdit, emphasis = true)
        }
        return
    }
    val spent = summary.spent
    val over = spent > budget
    val fraction = (spent.toFloat() / budget).coerceIn(0f, 1f)
    val current = today in summary.period
    val remainingDays = if (current) (summary.period.to.toEpochDay() - today.toEpochDay() + 1).toInt() else 0
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "Budget ${Money.format(budget)}",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = "${(spent * 100 / budget).coerceAtMost(999)}% used",
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface
            )
            LinkButton(text = "Edit", onClick = onEdit)
        }
        GrowBar(
            fraction = fraction,
            color = if (over) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
            height = 6.dp
        )
        Text(
            text = when {
                over -> "${Money.format(spent - budget)} over budget"
                current && remainingDays > 0 ->
                    "${Money.format(budget - spent)} left, about ${Money.format((budget - spent) / remainingDays)} a day"
                else -> "${Money.format(budget - spent)} left"
            },
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 6.dp)
        )
    }
}

@Composable
private fun BiggestLine(item: ExpenseEntity, name: String?) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "Biggest expense",
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = "${Money.format(item.amount)}, " + item.note.ifBlank { name ?: "Uncategorized" },
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1
        )
    }
}

@Composable
private fun DayHeader(title: String, total: Long) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Design.screenHorizontalPadding)
            .padding(top = 16.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = Money.signed(total),
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun TransactionRow(
    item: ExpenseEntity,
    category: String,
    slot: Int,
    iconKey: String,
    account: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = Design.screenHorizontalPadding, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = CategoryIcons.vector(iconKey),
            contentDescription = null,
            tint = categoryColor(slot),
            modifier = Modifier.padding(end = 14.dp).size(22.dp)
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.note.ifBlank { category },
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1
            )
            Text(
                text = "$category, $account",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 3.dp)
            )
        }
        Text(
            text = Money.signed(item.amount),
            fontSize = 15.sp,
            fontWeight = if (item.amount > 0) FontWeight.SemiBold else FontWeight.Normal,
            color = if (item.amount > 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(start = 12.dp)
        )
    }
}

@Composable
private fun ExpensesSkeleton() {
    Column(modifier = Modifier.padding(horizontal = Design.screenHorizontalPadding, vertical = 16.dp)) {
        SkeletonBox(modifier = Modifier.fillMaxWidth(0.35f).height(14.dp), shape = RoundedCornerShape(7.dp))
        Spacer(modifier = Modifier.height(8.dp))
        SkeletonBox(modifier = Modifier.fillMaxWidth(0.6f).height(32.dp), shape = RoundedCornerShape(8.dp))
        Spacer(modifier = Modifier.height(24.dp))
        SkeletonBox(modifier = Modifier.fillMaxWidth().height(160.dp), shape = RoundedCornerShape(12.dp))
        Spacer(modifier = Modifier.height(20.dp))
        repeat(4) {
            SkeletonBox(modifier = Modifier.fillMaxWidth().height(48.dp), shape = RoundedCornerShape(8.dp))
            Spacer(modifier = Modifier.height(10.dp))
        }
    }
}
