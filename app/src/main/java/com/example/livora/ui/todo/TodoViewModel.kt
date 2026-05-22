package com.example.livora.ui.todo

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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
import com.example.livora.util.Logger
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID

class TodoViewModel : ViewModel() {

    private val repository = TodoRepository()

    private val _todos = MutableStateFlow<List<Todo>>(emptyList())
    private val _completions = MutableStateFlow<List<TodoCompletion>>(emptyList())

    private val _stats = MutableStateFlow<List<TodoStats>>(emptyList())
    val stats: StateFlow<List<TodoStats>> = _stats.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val pendingToggles = MutableStateFlow<Set<String>>(emptySet())
    private val pendingMutations = MutableStateFlow<Set<String>>(emptySet())

    init {
        refresh()
    }

    fun refresh() {
        if (_isLoading.value) return
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
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
                _error.value = t.message ?: "Failed to load tasks"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun clearError() {
        _error.value = null
    }

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
                            durationUnit = durationUnit.name
                        )
                    )
                    _todos.update { list -> list.map { if (it.id == existing.id) updated.toTodo() else it } }
                }
                recompute()
            } catch (t: Throwable) {
                Logger.debug(TAG, "upsertTodo failed: ${t.message}")
                _error.value = t.message ?: "Failed to save task"
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
                _error.value = t.message ?: "Failed to update task"
            } finally {
                pendingToggles.update { it - todoId }
            }
        }
    }

    fun deleteTodo(todo: Todo) {
        if (todo.id in pendingMutations.value) return
        pendingMutations.update { it + todo.id }
        viewModelScope.launch {
            try {
                repository.deleteTodo(todo.id)
                _todos.update { list -> list.filterNot { it.id == todo.id } }
                _completions.update { list -> list.filterNot { it.todoId == todo.id } }
                recompute()
            } catch (t: Throwable) {
                Logger.debug(TAG, "delete failed: ${t.message}")
                _error.value = t.message ?: "Failed to delete task"
            } finally {
                pendingMutations.update { it - todo.id }
            }
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

    private fun TodoDto.toTodo(): Todo = Todo(
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

    private fun CompletionDto.toCompletion(): TodoCompletion = TodoCompletion(
        id = id,
        todoId = todoId,
        completedAt = completedAt
    )

    private companion object {
        const val TAG = "TodoViewModel"
    }
}
