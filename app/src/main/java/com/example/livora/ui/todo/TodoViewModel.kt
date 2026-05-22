package com.example.livora.ui.todo

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.livora.data.model.Todo
import com.example.livora.data.model.TodoCompletion
import com.example.livora.data.model.TodoDurationUnit
import com.example.livora.data.model.TodoIntervalUnit
import com.example.livora.data.model.TodoScheduleCalculator
import com.example.livora.data.model.TodoStats
import com.example.livora.data.supabase.SupabaseCompletionDto
import com.example.livora.data.supabase.SupabaseCompletionInsertDto
import com.example.livora.data.supabase.SupabaseTodoDto
import com.example.livora.data.supabase.SupabaseTodoInsertDto
import com.example.livora.data.supabase.SupabaseTodoRepository
import com.example.livora.data.supabase.SupabaseTodoUpdateDto
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.UUID

class TodoViewModel : ViewModel() {

    private val repository = SupabaseTodoRepository()

    private val _todos = MutableStateFlow<List<Todo>>(emptyList())
    private val _completions = MutableStateFlow<List<TodoCompletion>>(emptyList())

    private val _stats = MutableStateFlow<List<TodoStats>>(emptyList())
    val stats: StateFlow<List<TodoStats>> = _stats.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            val todos = repository.fetchAllTodos().map { it.toTodo() }
            val completions = repository.fetchAllCompletions().map { it.toCompletion() }
            _todos.value = todos
            _completions.value = completions
            recompute()
        }
    }

    fun statsFor(id: String): TodoStats? = _stats.value.firstOrNull { it.todo.id == id }

    fun upsertTodo(
        existing: Todo?,
        title: String,
        notes: String,
        intervalValue: Int,
        intervalUnit: TodoIntervalUnit,
        timeOfDay: String?,
        durationValue: Int,
        durationUnit: TodoDurationUnit
    ): Boolean {
        val trimmedTitle = title.trim()
        if (trimmedTitle.isBlank() || intervalValue < 1 || durationValue < 1) return false
        val trimmedNotes = notes.trim()
        val sanitizedTime = timeOfDay?.takeIf { it.isNotBlank() }
        viewModelScope.launch {
            if (existing == null) {
                val inserted = repository.insertTodo(
                    SupabaseTodoInsertDto(
                        id = UUID.randomUUID().toString(),
                        title = trimmedTitle,
                        notes = trimmedNotes,
                        intervalValue = intervalValue,
                        intervalUnit = intervalUnit.name,
                        timeOfDay = sanitizedTime,
                        durationValue = durationValue,
                        durationUnit = durationUnit.name,
                        createdAt = System.currentTimeMillis()
                    )
                )
                _todos.value = _todos.value + inserted.toTodo()
            } else {
                val updated = repository.updateTodo(
                    id = existing.id,
                    dto = SupabaseTodoUpdateDto(
                        title = trimmedTitle,
                        notes = trimmedNotes,
                        intervalValue = intervalValue,
                        intervalUnit = intervalUnit.name,
                        timeOfDay = sanitizedTime,
                        durationValue = durationValue,
                        durationUnit = durationUnit.name
                    )
                )
                _todos.value = _todos.value.map { if (it.id == existing.id) updated.toTodo() else it }
            }
            recompute()
        }
        return true
    }

    fun toggleCurrentInterval(todoId: String) {
        val stats = _stats.value.firstOrNull { it.todo.id == todoId } ?: return
        viewModelScope.launch {
            if (stats.isDoneCurrentInterval) {
                val intervalMs = TodoScheduleCalculator.intervalMs(stats.todo)
                val now = System.currentTimeMillis()
                val rangeStart = now - intervalMs + 1
                val targetId = _completions.value
                    .filter { it.todoId == todoId && it.completedAt in rangeStart..now }
                    .maxByOrNull { it.completedAt }
                    ?.id
                if (targetId != null) {
                    repository.deleteCompletion(targetId)
                    _completions.value = _completions.value.filterNot { it.id == targetId }
                }
            } else {
                val inserted = repository.insertCompletion(
                    SupabaseCompletionInsertDto(
                        id = UUID.randomUUID().toString(),
                        todoId = todoId,
                        completedAt = System.currentTimeMillis()
                    )
                )
                _completions.value = _completions.value + inserted.toCompletion()
            }
            recompute()
        }
    }

    fun deleteTodo(todo: Todo) {
        viewModelScope.launch {
            repository.deleteTodo(todo.id)
            _todos.value = _todos.value.filterNot { it.id == todo.id }
            _completions.value = _completions.value.filterNot { it.todoId == todo.id }
            recompute()
        }
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
    }

    private fun SupabaseTodoDto.toTodo(): Todo = Todo(
        id = id,
        title = title,
        notes = notes,
        intervalValue = intervalValue,
        intervalUnit = TodoIntervalUnit.valueOf(intervalUnit),
        timeOfDay = timeOfDay,
        durationValue = durationValue,
        durationUnit = TodoDurationUnit.valueOf(durationUnit),
        createdAt = createdAt
    )

    private fun SupabaseCompletionDto.toCompletion(): TodoCompletion = TodoCompletion(
        id = id,
        todoId = todoId,
        completedAt = completedAt
    )
}
