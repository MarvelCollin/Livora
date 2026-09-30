package com.example.livora.ui.expenses

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.filled.Add
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import com.example.livora.ui.components.InlineChip
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.livora.data.expenses.ExpenseAccountEntity
import com.example.livora.data.expenses.ExpenseCategoryEntity
import com.example.livora.data.expenses.ExpenseEntity
import com.example.livora.data.expenses.Money
import com.example.livora.ui.components.ChoiceOption
import com.example.livora.ui.components.ChoiceRow
import com.example.livora.ui.components.Motion
import com.example.livora.ui.components.SelectChip
import com.example.livora.ui.components.SuccessCheck
import com.example.livora.ui.components.pressScale
import com.example.livora.ui.components.statusGood
import com.example.livora.ui.people.LinkButton
import com.example.livora.ui.people.PrimaryAction
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import kotlinx.coroutines.delay

private enum class Kind { Expense, Income }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExpenseSheet(
    existing: ExpenseEntity?,
    state: ExpensesUiState,
    onSave: (ExpenseEntity) -> Unit,
    onDelete: (ExpenseEntity) -> Unit,
    onAddCategory: (String, Boolean, String, (ExpenseCategoryEntity) -> Unit) -> Unit,
    onAddAccount: (String, (ExpenseAccountEntity) -> Unit) -> Unit,
    onDismiss: () -> Unit
) {
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var kind by rememberSaveable { mutableStateOf(if ((existing?.amount ?: -1L) > 0) Kind.Income else Kind.Expense) }
    var digits by rememberSaveable { mutableStateOf(existing?.let { Math.abs(it.amount).toString() }.orEmpty()) }
    var categoryId by rememberSaveable { mutableStateOf(existing?.categoryId) }
    var accountId by rememberSaveable { mutableStateOf(existing?.accountId) }
    var note by rememberSaveable { mutableStateOf(existing?.note.orEmpty()) }
    var dayEpoch by rememberSaveable { mutableStateOf(existing?.day ?: state.today.toEpochDay()) }
    var savedText by remember { mutableStateOf<String?>(null) }
    var pickingDate by remember { mutableStateOf(false) }
    var naming by remember { mutableStateOf<String?>(null) }

    val isIncome = kind == Kind.Income
    val categories = state.categories.filter { it.income == isIncome }
    val chosenCategory = categories.firstOrNull { it.id == categoryId } ?: categories.firstOrNull()
    val chosenAccount = state.accounts.firstOrNull { it.id == accountId } ?: state.accounts.firstOrNull()
    val amount = Money.parseDigits(digits)
    val day = LocalDate.ofEpochDay(dayEpoch)
    val noun = if (isIncome) "income" else "expense"

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheet,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        AnimatedContent(
            targetState = savedText,
            transitionSpec = { fadeIn(Motion.enter()) togetherWith fadeOut(Motion.exit()) },
            label = "expenseSheet"
        ) { done ->
            if (done != null) {
                SavedState(text = done, onFinished = onDismiss)
            } else {
                Column(
                    modifier = Modifier
                        .navigationBarsPadding()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 20.dp)
                ) {
                    Text(
                        text = if (existing == null) "Add $noun" else "Edit $noun",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    ChoiceRow(
                        options = listOf(ChoiceOption(Kind.Expense, "Expense"), ChoiceOption(Kind.Income, "Income")),
                        selected = kind,
                        enabled = true,
                        onSelect = {
                            kind = it
                            categoryId = null
                        }
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = Money.format(amount),
                        fontSize = 36.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (amount > 0) MaterialTheme.colorScheme.onSurface
                        else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f)
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    ChipRow(
                        items = categories,
                        label = { it.name },
                        icon = { CategoryIcons.vector(it.iconKey) },
                        selectedIndex = categories.indexOfFirst { it.id == chosenCategory?.id },
                        onPick = { categoryId = it.id },
                        onNew = { naming = "category" }
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    ChipRow(
                        items = state.accounts,
                        label = { it.name },
                        icon = { accountIcon(it.name) },
                        selectedIndex = state.accounts.indexOfFirst { it.id == chosenAccount?.id },
                        onPick = { accountId = it.id },
                        onNew = { naming = "account" }
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = note,
                        onValueChange = { note = it.take(60) },
                        label = { Text("Note, optional") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = dayLabel(day, state.today),
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f)
                        )
                        LinkButton(text = "Change date", onClick = { pickingDate = true })
                    }
                    Keypad(
                        onDigit = { key -> if (digits.length < 12) digits = (digits + key).trimStart('0') },
                        onBackspace = { digits = digits.dropLast(1) }
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    PrimaryAction(
                        text = if (existing == null) "Save $noun" else "Save changes",
                        enabled = amount > 0 && chosenCategory != null && chosenAccount != null,
                        onClick = {
                            val category = chosenCategory ?: return@PrimaryAction
                            val account = chosenAccount ?: return@PrimaryAction
                            onSave(
                                ExpenseEntity(
                                    id = existing?.id ?: 0L,
                                    amount = if (isIncome) amount else -amount,
                                    categoryId = category.id,
                                    accountId = account.id,
                                    note = note.trim(),
                                    day = dayEpoch,
                                    createdAt = existing?.createdAt ?: System.currentTimeMillis()
                                )
                            )
                            savedText = when {
                                existing != null -> "Changes saved"
                                isIncome -> "Income added"
                                else -> "Expense added"
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (existing != null) {
                        LinkButton(
                            text = "Delete this $noun",
                            onClick = {
                                onDelete(existing)
                                onDismiss()
                            },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                }
            }
        }
    }

    if (pickingDate) {
        val todayMillis = state.today.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        val picker = rememberDatePickerState(
            initialSelectedDateMillis = day.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
            selectableDates = object : SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long): Boolean = utcTimeMillis <= todayMillis
            }
        )
        DatePickerDialog(
            onDismissRequest = { pickingDate = false },
            confirmButton = {
                TextButton(onClick = {
                    picker.selectedDateMillis?.let { millis ->
                        dayEpoch = Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate().toEpochDay()
                    }
                    pickingDate = false
                }) { Text("Done") }
            },
            dismissButton = { TextButton(onClick = { pickingDate = false }) { Text("Cancel") } }
        ) {
            DatePicker(state = picker)
        }
    }

    naming?.let { target ->
        NameDialog(
            title = if (target == "category") "New ${if (isIncome) "income " else ""}category" else "New account",
            showIcons = target == "category",
            onConfirm = { name, iconKey ->
                naming = null
                if (target == "category") {
                    onAddCategory(name, isIncome, iconKey) { categoryId = it.id }
                } else {
                    onAddAccount(name) { accountId = it.id }
                }
            },
            onDismiss = { naming = null }
        )
    }
}

@Composable
private fun <T> ChipRow(
    items: List<T>,
    label: (T) -> String,
    icon: (T) -> ImageVector,
    selectedIndex: Int,
    onPick: (T) -> Unit,
    onNew: () -> Unit
) {
    val list = rememberLazyListState(initialFirstVisibleItemIndex = selectedIndex.coerceAtLeast(0))
    LaunchedEffect(selectedIndex, items.size) {
        if (selectedIndex >= 0) list.reveal(selectedIndex)
    }
    LazyRow(
        state = list,
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        itemsIndexed(items) { index, item ->
            InlineChip(
                label = label(item),
                icon = icon(item),
                selected = index == selectedIndex,
                onClick = { onPick(item) }
            )
        }
        item { InlineChip(label = "New", icon = Icons.Default.Add, selected = false, onClick = onNew) }
    }
}

private suspend fun LazyListState.reveal(index: Int) {
    val info = layoutInfo.visibleItemsInfo.firstOrNull { it.index == index }
    val fits = info != null && info.offset >= 0 && info.offset + info.size <= layoutInfo.viewportEndOffset
    if (!fits) animateScrollToItem(index)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun NameDialog(
    title: String,
    showIcons: Boolean,
    onConfirm: (String, String) -> Unit,
    onDismiss: () -> Unit
) {
    var name by remember { mutableStateOf("") }
    var picked by remember { mutableStateOf<String?>(null) }
    val chosen = picked ?: CategoryIcons.suggest(name) ?: "other"
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it.take(24) },
                    label = { Text("Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                if (showIcons) {
                    Text(
                        text = "Icon",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 16.dp, bottom = 4.dp)
                    )
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        maxItemsInEachRow = 6
                    ) {
                        CategoryIcons.all.forEach { option ->
                            val selected = option.key == chosen
                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(CircleShape)
                                    .then(
                                        if (selected) Modifier.border(2.dp, MaterialTheme.colorScheme.primary, CircleShape)
                                        else Modifier
                                    )
                                    .clickable(role = Role.RadioButton) { picked = option.key },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = option.vector,
                                    contentDescription = option.label,
                                    tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(name, chosen) }, enabled = name.isNotBlank()) { Text("Add") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BudgetSheet(current: Long?, onSave: (Long?) -> Unit, onDismiss: () -> Unit) {
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var digits by rememberSaveable { mutableStateOf(current?.toString().orEmpty()) }
    val amount = Money.parseDigits(digits)

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
            Text(
                text = "Monthly budget",
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = "How much you plan to spend each month. It applies to every month.",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp)
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = Money.format(amount),
                fontSize = 36.sp,
                fontWeight = FontWeight.Bold,
                color = if (amount > 0) MaterialTheme.colorScheme.onSurface
                else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f)
            )
            Keypad(
                onDigit = { key -> if (digits.length < 12) digits = (digits + key).trimStart('0') },
                onBackspace = { digits = digits.dropLast(1) }
            )
            Spacer(modifier = Modifier.height(12.dp))
            PrimaryAction(
                text = "Save budget",
                enabled = amount > 0,
                onClick = {
                    onSave(amount)
                    onDismiss()
                },
                modifier = Modifier.fillMaxWidth()
            )
            if (current != null) {
                LinkButton(
                    text = "Remove budget",
                    onClick = {
                        onSave(null)
                        onDismiss()
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun SavedState(text: String, onFinished: () -> Unit) {
    LaunchedEffect(Unit) {
        delay(1100)
        onFinished()
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 20.dp, vertical = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        SuccessCheck(color = statusGood(), size = 88.dp)
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = text,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
fun Keypad(onDigit: (String) -> Unit, onBackspace: () -> Unit) {
    val rows = listOf(
        listOf("1", "2", "3"),
        listOf("4", "5", "6"),
        listOf("7", "8", "9"),
        listOf("000", "0", "back")
    )
    Column(modifier = Modifier.padding(top = 4.dp)) {
        rows.forEach { row ->
            Row(modifier = Modifier.fillMaxWidth()) {
                row.forEach { key ->
                    KeypadKey(
                        label = key,
                        modifier = Modifier.weight(1f),
                        onClick = { if (key == "back") onBackspace() else onDigit(key) }
                    )
                }
            }
        }
    }
}

@Composable
private fun KeypadKey(label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val interaction = remember { MutableInteractionSource() }
    Box(
        modifier = modifier
            .pressScale(interaction, 0.9f)
            .heightIn(min = 56.dp)
            .clickable(
                interactionSource = interaction,
                indication = LocalIndication.current,
                role = Role.Button,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        if (label == "back") {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.Backspace,
                contentDescription = "Delete last digit",
                tint = MaterialTheme.colorScheme.onSurface
            )
        } else {
            Text(
                text = label,
                fontSize = 22.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}
