package com.example.livora.ui.dictionary

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.livora.data.dictionary.DictionaryLookupRepository
import com.example.livora.data.model.DictionaryEntry
import com.example.livora.data.model.LookupResult
import com.example.livora.data.model.QuizMode
import com.example.livora.data.model.QuizQuestion
import com.example.livora.data.supabase.DictionaryDto
import com.example.livora.data.supabase.DictionaryInsertDto
import com.example.livora.data.supabase.DictionaryRepository
import com.example.livora.ui.components.Toaster
import com.example.livora.util.Logger
import com.example.livora.util.UserMessages
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID

class DictionaryViewModel : ViewModel() {

    private val repository = DictionaryRepository()
    private val lookupRepository = DictionaryLookupRepository()

    private val _entries = MutableStateFlow<List<DictionaryEntry>>(emptyList())
    val entries: StateFlow<List<DictionaryEntry>> = _entries.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _lookupInProgress = MutableStateFlow(false)
    val lookupInProgress: StateFlow<Boolean> = _lookupInProgress.asStateFlow()

    private val _lookupResult = MutableStateFlow<LookupResult?>(null)
    val lookupResult: StateFlow<LookupResult?> = _lookupResult.asStateFlow()

    private val _suggestion = MutableStateFlow<String?>(null)
    val suggestion: StateFlow<String?> = _suggestion.asStateFlow()

    private val _quiz = MutableStateFlow<List<QuizQuestion>>(emptyList())
    val quiz: StateFlow<List<QuizQuestion>> = _quiz.asStateFlow()

    private val pendingMutations = MutableStateFlow<Set<String>>(emptySet())
    private var _quizMode: QuizMode = QuizMode.All

    init {
        refresh()
    }

    fun refresh() {
        if (_isLoading.value) return
        viewModelScope.launch {
            _isLoading.value = true
            try {
                _entries.value = repository.fetchAll().map { it.toEntry() }
            } catch (t: Throwable) {
                Logger.debug(TAG, "refresh failed: ${t.message}")
                Toaster.error(t.message ?: "Failed to load dictionary")
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun lookup(word: String) {
        val trimmed = word.trim()
        if (trimmed.isBlank() || _lookupInProgress.value) return
        viewModelScope.launch {
            _lookupInProgress.value = true
            _suggestion.value = null
            try {
                val correction = lookupRepository.checkSpelling(trimmed)
                if (correction != null) {
                    _suggestion.value = correction
                } else {
                    _lookupResult.value = lookupRepository.lookup(trimmed)
                }
            } catch (t: Throwable) {
                Logger.debug(TAG, "lookup failed: ${t.message}")
                _lookupResult.value = LookupResult("", emptyList(), "")
            } finally {
                _lookupInProgress.value = false
            }
        }
    }

    fun performLookup(word: String) {
        val trimmed = word.trim()
        if (trimmed.isBlank() || _lookupInProgress.value) return
        _suggestion.value = null
        viewModelScope.launch {
            _lookupInProgress.value = true
            try {
                _lookupResult.value = lookupRepository.lookup(trimmed)
            } catch (t: Throwable) {
                Logger.debug(TAG, "lookup failed: ${t.message}")
                _lookupResult.value = LookupResult("", emptyList(), "")
            } finally {
                _lookupInProgress.value = false
            }
        }
    }

    fun dismissSuggestion() {
        _suggestion.value = null
    }

    fun clearLookup() {
        _lookupResult.value = null
        _suggestion.value = null
    }

    suspend fun saveEntry(
        word: String,
        translation: String,
        synonyms: List<String>,
        example: String
    ): String? {
        val trimmedWord = word.trim()
        if (trimmedWord.isBlank()) return "Enter the word first."
        if (translation.isBlank()) return "Add the Indonesian translation."
        if (_entries.value.any { it.word.equals(trimmedWord, ignoreCase = true) }) {
            return "\"$trimmedWord\" is already in your dictionary."
        }
        val id = UUID.randomUUID().toString()
        return viewModelScope.async {
            try {
                val inserted = repository.insert(
                    DictionaryInsertDto(
                        id = id,
                        word = trimmedWord,
                        translation = translation.trim(),
                        synonyms = synonyms.map { it.trim() }.filter { it.isNotBlank() }.joinToString(", "),
                        example = example.trim(),
                        correctCount = 0,
                        wrongCount = 0,
                        createdAt = System.currentTimeMillis()
                    )
                )
                _entries.update { listOf(inserted.toEntry()) + it }
                clearLookup()
                Toaster.success("Saved \"$trimmedWord\"")
                null
            } catch (t: Throwable) {
                Logger.debug(TAG, "saveEntry failed: ${t.message}")
                UserMessages.saveFailure(t, "this word")
            }
        }.await()
    }

    fun deleteEntry(entry: DictionaryEntry) {
        if (entry.id in pendingMutations.value) return
        pendingMutations.update { it + entry.id }
        viewModelScope.launch {
            try {
                repository.delete(entry.id)
                _entries.update { list -> list.filterNot { it.id == entry.id } }
                Toaster.success(
                    message = "Deleted \"${entry.word}\"",
                    actionLabel = "Undo",
                    onAction = { restoreEntry(entry) }
                )
            } catch (t: Throwable) {
                Logger.debug(TAG, "deleteEntry failed: ${t.message}")
                Toaster.error(t.message ?: "Failed to delete word")
            } finally {
                pendingMutations.update { it - entry.id }
            }
        }
    }

    private fun restoreEntry(entry: DictionaryEntry) {
        if (entry.id in pendingMutations.value) return
        pendingMutations.update { it + entry.id }
        viewModelScope.launch {
            try {
                val inserted = repository.insert(
                    DictionaryInsertDto(
                        id = entry.id,
                        word = entry.word,
                        translation = entry.translation,
                        synonyms = entry.synonyms.joinToString(", "),
                        example = entry.example,
                        correctCount = entry.correctCount,
                        wrongCount = entry.wrongCount,
                        createdAt = entry.createdAt
                    )
                )
                _entries.update { (it + inserted.toEntry()).sortedByDescending { e -> e.createdAt } }
            } catch (t: Throwable) {
                Logger.debug(TAG, "restoreEntry failed: ${t.message}")
                Toaster.error(t.message ?: "Failed to restore word")
            } finally {
                pendingMutations.update { it - entry.id }
            }
        }
    }

    fun startQuiz(mode: QuizMode = QuizMode.All) {
        _quizMode = mode
        val pool = _entries.value.filter { it.translation.isNotBlank() }
        val selected = when (mode) {
            QuizMode.All -> pool.shuffled()
            QuizMode.Hardest -> {
                val withMistakes = pool.filter { it.wrongCount > 0 }
                val ordered = if (withMistakes.isNotEmpty()) withMistakes else pool
                ordered.sortedWith(
                    compareByDescending<DictionaryEntry> { it.wrongCount }
                        .thenByDescending { it.attempts }
                )
            }
        }
        val questions = selected.mapNotNull { entry ->
            val distractors = pool
                .filter { it.id != entry.id && !it.translation.equals(entry.translation, ignoreCase = true) }
                .map { it.translation }
                .distinct()
                .shuffled()
                .take(3)
            val options = (distractors + entry.translation).distinct().shuffled()
            if (options.size < 2) {
                null
            } else {
                QuizQuestion(
                    entry = entry,
                    options = options,
                    correctIndex = options.indexOf(entry.translation)
                )
            }
        }
        _quiz.value = questions
    }

    fun restartQuiz() = startQuiz(_quizMode)

    fun recordAnswer(entryId: String, isCorrect: Boolean) {
        val entry = _entries.value.firstOrNull { it.id == entryId } ?: return
        val newCorrect = entry.correctCount + if (isCorrect) 1 else 0
        val newWrong = entry.wrongCount + if (isCorrect) 0 else 1
        _entries.update { list ->
            list.map { if (it.id == entryId) it.copy(correctCount = newCorrect, wrongCount = newWrong) else it }
        }
        viewModelScope.launch {
            try {
                repository.updateStats(entryId, newCorrect, newWrong)
            } catch (t: Throwable) {
                Logger.debug(TAG, "recordAnswer failed: ${t.message}")
            }
        }
    }

    fun canQuiz(): Boolean = _entries.value.count { it.translation.isNotBlank() } >= 2

    fun hardestCount(): Int = _entries.value.count { it.translation.isNotBlank() && it.wrongCount > 0 }

    private fun DictionaryDto.toEntry(): DictionaryEntry = DictionaryEntry(
        id = id,
        word = word,
        translation = translation,
        synonyms = synonyms.split(",").map { it.trim() }.filter { it.isNotBlank() },
        example = example,
        correctCount = correctCount,
        wrongCount = wrongCount,
        createdAt = createdAt
    )

    private companion object {
        const val TAG = "DictionaryViewModel"
    }
}
