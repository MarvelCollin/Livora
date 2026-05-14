package com.example.livora.ui.todo

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import com.example.livora.data.model.Todo
import com.example.livora.data.model.TodoDurationUnit
import com.example.livora.data.model.TodoIntervalUnit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

class TodoViewModel(application: Application) : AndroidViewModel(application) {

    private val prefs = application.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _todos = MutableStateFlow<List<Todo>>(emptyList())
    val todos: StateFlow<List<Todo>> = _todos.asStateFlow()

    init {
        loadTodos()
    }

    private fun loadTodos() {
        val raw = prefs.getString(KEY_TODOS, null) ?: return
        val loaded = mutableListOf<Todo>()
        val array = JSONArray(raw)
        for (index in 0 until array.length()) {
            val item = array.getJSONObject(index)
            loaded.add(
                Todo(
                    id = item.getString("id"),
                    title = item.getString("title"),
                    notes = item.optString("notes", ""),
                    intervalValue = item.optInt("intervalValue", 1).coerceAtLeast(1),
                    intervalUnit = item.optString("intervalUnit", TodoIntervalUnit.Day.name).toIntervalUnit(),
                    durationValue = item.optInt("durationValue", 30).coerceAtLeast(1),
                    durationUnit = item.optString("durationUnit", TodoDurationUnit.Minute.name).toDurationUnit(),
                    isCompleted = item.optBoolean("isCompleted", false),
                    createdAt = item.optLong("createdAt", System.currentTimeMillis())
                )
            )
        }
        _todos.value = loaded.sortedTodos()
    }

    private fun saveTodos(todos: List<Todo>) {
        val array = JSONArray()
        todos.forEach { todo ->
            array.put(
                JSONObject()
                    .put("id", todo.id)
                    .put("title", todo.title)
                    .put("notes", todo.notes)
                    .put("intervalValue", todo.intervalValue)
                    .put("intervalUnit", todo.intervalUnit.name)
                    .put("durationValue", todo.durationValue)
                    .put("durationUnit", todo.durationUnit.name)
                    .put("isCompleted", todo.isCompleted)
                    .put("createdAt", todo.createdAt)
            )
        }
        prefs.edit().putString(KEY_TODOS, array.toString()).apply()
    }

    fun upsertTodo(
        existing: Todo?,
        title: String,
        notes: String,
        intervalValue: Int,
        intervalUnit: TodoIntervalUnit,
        durationValue: Int,
        durationUnit: TodoDurationUnit
    ): Boolean {
        val trimmedTitle = title.trim()
        if (trimmedTitle.isBlank() || intervalValue < 1 || durationValue < 1) return false
        val nextTodo = Todo(
            id = existing?.id ?: UUID.randomUUID().toString(),
            title = trimmedTitle,
            notes = notes.trim(),
            intervalValue = intervalValue,
            intervalUnit = intervalUnit,
            durationValue = durationValue,
            durationUnit = durationUnit,
            isCompleted = existing?.isCompleted ?: false,
            createdAt = existing?.createdAt ?: System.currentTimeMillis()
        )
        val nextTodos = if (existing == null) {
            _todos.value + nextTodo
        } else {
            _todos.value.map { if (it.id == existing.id) nextTodo else it }
        }.sortedTodos()
        _todos.value = nextTodos
        saveTodos(nextTodos)
        return true
    }

    fun toggleCompleted(todo: Todo) {
        val nextTodos = _todos.value.map {
            if (it.id == todo.id) it.copy(isCompleted = !it.isCompleted) else it
        }.sortedTodos()
        _todos.value = nextTodos
        saveTodos(nextTodos)
    }

    fun deleteTodo(todo: Todo) {
        val nextTodos = _todos.value.filterNot { it.id == todo.id }.sortedTodos()
        _todos.value = nextTodos
        saveTodos(nextTodos)
    }

    private fun List<Todo>.sortedTodos(): List<Todo> =
        sortedWith(compareBy<Todo> { it.isCompleted }.thenByDescending { it.createdAt })

    private fun String.toIntervalUnit(): TodoIntervalUnit =
        TodoIntervalUnit.entries.firstOrNull { it.name == this } ?: TodoIntervalUnit.Day

    private fun String.toDurationUnit(): TodoDurationUnit =
        TodoDurationUnit.entries.firstOrNull { it.name == this } ?: TodoDurationUnit.Minute

    companion object {
        private const val PREFS_NAME = "livora_todo_prefs"
        private const val KEY_TODOS = "todos"
    }
}
