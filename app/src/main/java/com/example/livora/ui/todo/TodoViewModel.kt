package com.example.livora.ui.todo

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.livora.data.model.DayActivity
import com.example.livora.data.model.Todo
import com.example.livora.data.model.TodoCompletion
import com.example.livora.data.model.TodoDurationUnit
import com.example.livora.data.model.TodoIntervalUnit
import com.example.livora.data.model.TodoScheduleCalculator
import com.example.livora.data.model.TodoStats
import com.example.livora.data.supabase.CompletionDto
import com.example.livora.data.supabase.CompletionInsertDto
import com.example.livora.data.supabase.TodoDto
import com.example.livora.data.supabase.TodoInsertDto
import com.example.livora.data.supabase.TodoRepository
import com.example.livora.data.supabase.TodoUpdateDto
import com.example.livora.ui.components.Toaster
import com.example.livora.util.Logger
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.UUID

class TodoViewModel : ViewModel() {

    private val repository = TodoRepository()

    private val _todos = MutableStateFlow<List<Todo>>(emptyList())
    private val _completions = MutableStateFlow<List<TodoCompletion>>(emptyList())

    private val _stats = MutableStateFlow<List<TodoStats>>(emptyList())
    val stats: StateFlow<List<TodoStats>> = _stats.asStateFlow()

    private val _dailyActivity = MutableStateFlow<List<DayActivity>>(emptyList())
    val dailyActivity: StateFlow<List<DayActivity>> = _dailyActivity.asStateFlow()

    private val _heatmap = MutableStateFlow<List<DayActivity>>(emptyList())
    val heatmap: StateFlow<List<DayActivity>> = _heatmap.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val pendingToggles = MutableStateFlow<Set<String>>(emptySet())
    private val pendingMutations = MutableStateFlow<Set<String>>(emptySet())

    private val _runningTimers = MutableStateFlow<Map<String, Long>>(emptyMap())
    val runningTimers: StateFlow<Map<String, Long>> = _runningTimers.asStateFlow()
    private val timerJobs = mutableMapOf<String, Job>()

    init {
        refresh()
    }

    fun refresh() {
        if (_isLoading.value) return
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val (todos, completions) = coroutineScope {
                    val todosDeferred = async { repository.fetchAllTodos() }
                    val completionsDeferred = async { repository.fetchAllCompletions() }
                    awaitAll(todosDeferred, completionsDeferred)
                    todosDeferred.await() to completionsDeferred.await()
                }
                _todos.value = todos.map { it.toTodo() }
                _completions.value = completions.map { it.toCompletion() }
                recompute()
            } catch (t: Throwable) {
                Logger.debug(TAG, "refresh failed: ${t.message}")
                Toaster.error(t.message ?: "Failed to load tasks")
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun upsertTodo(
        existing: Todo?,
        title: String,
        notes: String,
        intervalValue: Int,
        intervalUnit: TodoIntervalUnit,
        timeOfDay: String?,
        durationValue: Int,
        durationUnit: TodoDurationUnit,
        hasTimer: Boolean
    ): Boolean {
        val trimmedTitle = title.trim()
        if (trimmedTitle.isBlank() || intervalValue < 1 || durationValue < 1) return false
        val trimmedNotes = notes.trim()
        val sanitizedTime = timeOfDay?.takeIf { it.isNotBlank() }
        val mutationKey = existing?.id ?: "new"
        if (mutationKey in pendingMutations.value) return false
        pendingMutations.update { it + mutationKey }
        viewModelScope.launch {
            try {
                if (existing == null) {
                    val inserted = repository.insertTodo(
                        TodoInsertDto(
                            id = UUID.randomUUID().toString(),
                            title = trimmedTitle,
                            notes = trimmedNotes,
                            intervalValue = intervalValue,
                            intervalUnit = intervalUnit.name,
                            timeOfDay = sanitizedTime,
                            durationValue = durationValue,
                            durationUnit = durationUnit.name,
                            hasTimer = hasTimer,
                            createdAt = System.currentTimeMillis()
                        )
                    )
                    _todos.update { it + inserted.toTodo() }
                } else {
                    val updated = repository.updateTodo(
                        id = existing.id,
                        dto = TodoUpdateDto(
                            title = trimmedTitle,
                            notes = trimmedNotes,
                            intervalValue = intervalValue,
                            intervalUnit = intervalUnit.name,
                            timeOfDay = sanitizedTime,
                            durationValue = durationValue,
                            durationUnit = durationUnit.name,
                            hasTimer = hasTimer
                        )
                    )
                    _todos.update { list -> list.map { if (it.id == existing.id) updated.toTodo() else it } }
                }
                recompute()
                Toaster.success(if (existing == null) "Task added" else "Task updated")
            } catch (t: Throwable) {
                Logger.debug(TAG, "upsertTodo failed: ${t.message}")
                Toaster.error(t.message ?: "Failed to save task")
            } finally {
                pendingMutations.update { it - mutationKey }
            }
        }
        return true
    }

    fun toggleCurrentInterval(todoId: String) {
        if (todoId in pendingToggles.value) return
        val current = _stats.value.firstOrNull { it.todo.id == todoId } ?: return
        pendingToggles.update { it + todoId }
        viewModelScope.launch {
            try {
                if (current.isDoneCurrentInterval) {
                    val intervalMs = TodoScheduleCalculator.intervalMs(current.todo)
                    val now = System.currentTimeMillis()
                    val rangeStart = now - intervalMs + 1
                    val targetId = _completions.value
                        .filter { it.todoId == todoId && it.completedAt in rangeStart..now }
                        .maxByOrNull { it.completedAt }
                        ?.id
                    if (targetId != null) {
                        repository.deleteCompletion(targetId)
                        _completions.update { list -> list.filterNot { it.id == targetId } }
                    }
                } else {
                    val inserted = repository.insertCompletion(
                        CompletionInsertDto(
                            id = UUID.randomUUID().toString(),
                            todoId = todoId,
                            completedAt = System.currentTimeMillis()
                        )
                    )
                    _completions.update { it + inserted.toCompletion() }
                }
                recompute()
            } catch (t: Throwable) {
                Logger.debug(TAG, "toggle failed: ${t.message}")
                Toaster.error(t.message ?: "Failed to update task")
            } finally {
                pendingToggles.update { it - todoId }
            }
        }
    }

    fun deleteTodo(todo: Todo) {
        if (todo.id in pendingMutations.value) return
        cancelTimer(todo.id)
        pendingMutations.update { it + todo.id }
        viewModelScope.launch {
            try {
                repository.deleteTodo(todo.id)
                _todos.update { list -> list.filterNot { it.id == todo.id } }
                _completions.update { list -> list.filterNot { it.todoId == todo.id } }
                recompute()
                Toaster.success("Task deleted")
            } catch (t: Throwable) {
                Logger.debug(TAG, "delete failed: ${t.message}")
                Toaster.error(t.message ?: "Failed to delete task")
            } finally {
                pendingMutations.update { it - todo.id }
            }
        }
    }

    fun startTimer(todoId: String) {
        if (todoId in timerJobs) return
        val todo = _todos.value.firstOrNull { it.id == todoId } ?: return
        if (!todo.hasTimer) return
        val totalMs = timerDurationMs(todo)
        if (totalMs <= 0) return
        val job = viewModelScope.launch {
            var remaining = totalMs
            _runningTimers.update { it + (todoId to remaining) }
            while (remaining > 0) {
                delay(1000)
                remaining -= 1000
                _runningTimers.update { it + (todoId to remaining.coerceAtLeast(0)) }
            }
            timerJobs.remove(todoId)
            _runningTimers.update { it - todoId }
            completeFromTimer(todoId)
        }
        timerJobs[todoId] = job
    }

    fun cancelTimer(todoId: String) {
        timerJobs.remove(todoId)?.cancel()
        _runningTimers.update { it - todoId }
    }

    private fun completeFromTimer(todoId: String) {
        val current = _stats.value.firstOrNull { it.todo.id == todoId } ?: return
        Toaster.success("Timer done: \"${current.todo.title}\"")
        if (!current.isDoneCurrentInterval) {
            toggleCurrentInterval(todoId)
        }
    }

    private fun timerDurationMs(todo: Todo): Long {
        val unitMs = when (todo.durationUnit) {
            TodoDurationUnit.Minute -> 60_000L
            TodoDurationUnit.Hour -> 3_600_000L
        }
        return unitMs * todo.durationValue.coerceAtLeast(0)
    }

    private fun recompute() {
        val now = System.currentTimeMillis()
        val grouped = _completions.value.groupBy { it.todoId }
        val computed = _todos.value.map { todo ->
            val ts = grouped[todo.id]?.map { it.completedAt } ?: emptyList()
            TodoScheduleCalculator.stats(todo, ts, now)
        }
        _stats.value = computed.sortedWith(
            compareBy<TodoStats> { it.isDoneCurrentInterval }
                .thenByDescending { it.todo.createdAt }
        )
        _dailyActivity.value = computeDailyActivity()
        _heatmap.value = computeHeatmap()
    }

    private fun computeHeatmap(): List<DayActivity> {
        val durationByTodo = _todos.value.associate { it.id to durationMinutes(it) }
        val today = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val todayStart = today.timeInMillis
        val cursor = (today.clone() as Calendar).apply {
            firstDayOfWeek = Calendar.MONDAY
            set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
            add(Calendar.DAY_OF_YEAR, -(HEATMAP_WEEKS - 1) * 7)
        }
        val labelFormat = SimpleDateFormat("EEE, d MMM", Locale.getDefault())
        val byDay = _completions.value.groupBy { completion ->
            Calendar.getInstance().apply {
                timeInMillis = completion.completedAt
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }.timeInMillis
        }
        return (0 until HEATMAP_WEEKS * 7).map {
            val dayStart = cursor.timeInMillis
            val done = byDay[dayStart].orEmpty()
            cursor.add(Calendar.DAY_OF_YEAR, 1)
            DayActivity(
                dayStart = dayStart,
                label = labelFormat.format(dayStart),
                tasks = done.size,
                minutes = done.sumOf { durationByTodo[it.todoId] ?: 0 },
                isToday = dayStart == todayStart
            )
        }
    }

    private fun computeDailyActivity(): List<DayActivity> {
        val durationByTodo = _todos.value.associate { it.id to durationMinutes(it) }
        val calendar = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val todayStart = calendar.timeInMillis
        val dayMs = 86_400_000L
        val labelFormat = SimpleDateFormat("EEE", Locale.getDefault())
        return (6 downTo 0).map { offset ->
            val dayStart = todayStart - offset * dayMs
            val dayEnd = dayStart + dayMs
            val dayCompletions = _completions.value.filter { it.completedAt in dayStart until dayEnd }
            val minutes = dayCompletions.sumOf { durationByTodo[it.todoId] ?: 0 }
            DayActivity(
                dayStart = dayStart,
                label = labelFormat.format(dayStart),
                tasks = dayCompletions.size,
                minutes = minutes,
                isToday = offset == 0
            )
        }
    }

    private fun durationMinutes(todo: Todo): Int {
        val unit = if (todo.durationUnit == TodoDurationUnit.Hour) 60 else 1
        return todo.durationValue.coerceAtLeast(0) * unit
    }

    private fun TodoDto.toTodo(): Todo = Todo(
        id = id,
        title = title,
        notes = notes,
        intervalValue = intervalValue,
        intervalUnit = TodoIntervalUnit.valueOf(intervalUnit),
        timeOfDay = timeOfDay,
        durationValue = durationValue,
        durationUnit = TodoDurationUnit.valueOf(durationUnit),
        hasTimer = hasTimer,
        createdAt = createdAt
    )

    private fun CompletionDto.toCompletion(): TodoCompletion = TodoCompletion(
        id = id,
        todoId = todoId,
        completedAt = completedAt
    )

    private companion object {
        const val TAG = "TodoViewModel"
        const val HEATMAP_WEEKS = 5
    }
}
