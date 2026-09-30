package com.example.livora.ui.expenses

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.livora.data.db.AppDatabase
import com.example.livora.data.expenses.ExpenseAccountEntity
import com.example.livora.data.expenses.ExpenseCategoryEntity
import com.example.livora.data.expenses.ExpenseEntity
import com.example.livora.data.expenses.ExpenseMath
import com.example.livora.data.expenses.ExpenseRepository
import com.example.livora.data.expenses.Period
import com.example.livora.data.expenses.PeriodKind
import com.example.livora.data.expenses.PeriodSummary
import com.example.livora.ui.components.Toaster
import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ExpensesUiState(
    val loading: Boolean,
    val period: Period,
    val today: LocalDate,
    val summary: PeriodSummary?,
    val rows: List<ExpenseEntity>,
    val categories: List<ExpenseCategoryEntity>,
    val accounts: List<ExpenseAccountEntity>,
    val budget: Long?,
    val hasAny: Boolean,
    val canPrevious: Boolean,
    val canNext: Boolean
) {
    val categoryById: Map<Long, ExpenseCategoryEntity> by lazy { categories.associateBy { it.id } }
    val accountById: Map<Long, ExpenseAccountEntity> by lazy { accounts.associateBy { it.id } }
}

private class Selection(val kind: PeriodKind, val anchor: LocalDate, val custom: Period?) {
    val period: Period
        get() = if (kind == PeriodKind.Custom && custom != null) custom else Period.of(kind, anchor)
}

class ExpensesViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = ExpenseRepository(AppDatabase.get(application))
    private val selection = MutableStateFlow(Selection(PeriodKind.Month, LocalDate.now(), null))

    @OptIn(ExperimentalCoroutinesApi::class)
    val state: StateFlow<ExpensesUiState> = selection
        .flatMapLatest { chosen ->
            val period = chosen.period
            combine(
                repository.between(ExpenseMath.queryFrom(period).toEpochDay(), period.to.toEpochDay()),
                repository.categories(),
                repository.accounts(),
                repository.budgets(),
                repository.firstDay()
            ) { rows, categories, accounts, budgets, firstDay ->
                val today = LocalDate.now()
                val summary = ExpenseMath.summarize(rows, categories.associateBy { it.id }, period, today)
                val first = period.from.toEpochDay()
                val last = period.to.toEpochDay()
                val custom = period.kind == PeriodKind.Custom
                ExpensesUiState(
                    loading = false,
                    period = period,
                    today = today,
                    summary = summary,
                    rows = rows.filter { it.day in first..last },
                    categories = categories,
                    accounts = accounts,
                    budget = budgets.firstOrNull { it.categoryId == ExpenseRepository.TOTAL_BUDGET }?.monthlyLimit,
                    hasAny = firstDay != null,
                    canPrevious = !custom && firstDay != null && firstDay < first,
                    canNext = !custom && period.to < today
                )
            }
        }
        .flowOn(Dispatchers.Default)
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            ExpensesUiState(
                loading = true,
                period = selection.value.period,
                today = LocalDate.now(),
                summary = null,
                rows = emptyList(),
                categories = emptyList(),
                accounts = emptyList(),
                budget = null,
                hasAny = false,
                canPrevious = false,
                canNext = false
            )
        )

    fun setKind(kind: PeriodKind) = selection.update { Selection(kind, it.anchor, it.custom) }

    fun setCustom(from: LocalDate, to: LocalDate) =
        selection.update { Selection(PeriodKind.Custom, it.anchor, Period.custom(from, minOf(to, LocalDate.now()))) }

    fun previous() = selection.update { chosen ->
        val period = chosen.period
        if (period.kind == PeriodKind.Custom) chosen else Selection(chosen.kind, period.previous().from, chosen.custom)
    }

    fun next() = selection.update { chosen ->
        val period = chosen.period
        if (period.kind == PeriodKind.Custom || period.to >= LocalDate.now()) chosen
        else Selection(chosen.kind, period.next().from, chosen.custom)
    }

    fun currentCustom(): Period? = selection.value.custom

    fun save(item: ExpenseEntity) {
        viewModelScope.launch {
            if (item.id == 0L) repository.add(item) else repository.update(item)
        }
    }

    fun delete(item: ExpenseEntity) {
        viewModelScope.launch {
            repository.delete(item)
            Toaster.success("Deleted", actionLabel = "Undo", onAction = {
                viewModelScope.launch { repository.restore(item) }
            })
        }
    }

    fun addCategory(name: String, income: Boolean, iconKey: String, onCreated: (ExpenseCategoryEntity) -> Unit) {
        viewModelScope.launch {
            val created = repository.addCategory(name, income, iconKey)
            if (created == null) Toaster.error("Enter a name") else onCreated(created)
        }
    }

    fun addAccount(name: String, onCreated: (ExpenseAccountEntity) -> Unit) {
        viewModelScope.launch {
            val created = repository.addAccount(name)
            if (created == null) Toaster.error("Enter a name") else onCreated(created)
        }
    }

    fun setBudget(limit: Long?) {
        viewModelScope.launch { repository.setBudget(limit) }
    }

    fun export(uri: Uri) {
        viewModelScope.launch {
            val count = withContext(Dispatchers.IO) {
                runCatching {
                    getApplication<Application>().contentResolver.openOutputStream(uri)?.use { repository.exportCsv(it) }
                }.getOrNull()
            }
            if (count == null) Toaster.error("Could not save the file") else Toaster.success("Exported $count transactions")
        }
    }
}
