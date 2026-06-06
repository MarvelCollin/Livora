package com.example.livora.ui.dictionary

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.livora.data.dictionary.DictionaryLookupRepository
import com.example.livora.data.model.DictionaryEntry
import com.example.livora.data.model.DictionaryLanguage
import com.example.livora.data.model.LookupResult
import com.example.livora.data.model.QuizQuestion
import com.example.livora.data.supabase.DictionaryDto
import com.example.livora.data.supabase.DictionaryInsertDto
import com.example.livora.data.supabase.DictionaryRepository
import com.example.livora.ui.components.Toaster
import com.example.livora.util.Logger
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

    private val _selectedLanguage = MutableStateFlow(DictionaryLanguage.English)
    val selectedLanguage: StateFlow<DictionaryLanguage> = _selectedLanguage.asStateFlow()

    private val _lookupInProgress = MutableStateFlow(false)
    val lookupInProgress: StateFlow<Boolean> = _lookupInProgress.asStateFlow()

    private val _lookupResult = MutableStateFlow<LookupResult?>(null)
    val lookupResult: StateFlow<LookupResult?> = _lookupResult.asStateFlow()

    private val _suggestion = MutableStateFlow<String?>(null)
    val suggestion: StateFlow<String?> = _suggestion.asStateFlow()

    private val _quiz = MutableStateFlow<List<QuizQuestion>>(emptyList())
    val quiz: StateFlow<List<QuizQuestion>> = _quiz.asStateFlow()

    private val pendingMutations = MutableStateFlow<Set<String>>(emptySet())

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

    fun selectLanguage(language: DictionaryLanguage) {
        _selectedLanguage.value = language
    }

    fun lookup(word: String) {
        val trimmed = word.trim()
        if (trimmed.isBlank() || _lookupInProgress.value) return
        viewModelScope.launch {
            _lookupInProgress.value = true
            _suggestion.value = null
            try {
                val correction = lookupRepository.checkSpelling(trimmed, _selectedLanguage.value.code)
                if (correction != null) {
                    _suggestion.value = correction
                } else {
                    _lookupResult.value = lookupRepository.lookup(trimmed, _selectedLanguage.value.code)
                }
            } catch (t: Throwable) {
                Logger.debug(TAG, "lookup failed: ${t.message}")
                _lookupResult.value = LookupResult("", "", "", "", emptyList(), false)
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
                _lookupResult.value = lookupRepository.lookup(trimmed, _selectedLanguage.value.code)
            } catch (t: Throwable) {
                Logger.debug(TAG, "lookup failed: ${t.message}")
                _lookupResult.value = LookupResult("", "", "", "", emptyList(), false)
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

    fun addEntry(
        word: String,
        translation: String,
        description: String,
        descriptionId: String,
        example: String,
        synonyms: List<String>
    ): Boolean {
        val trimmedWord = word.trim()
        if (trimmedWord.isBlank()) return false
        val id = UUID.randomUUID().toString()
        if (id in pendingMutations.value) return false
        pendingMutations.update { it + id }
        viewModelScope.launch {
            try {
                val inserted = repository.insert(
                    DictionaryInsertDto(
                        id = id,
                        word = trimmedWord,
                        language = _selectedLanguage.value.code,
                        translation = translation.trim(),
                        description = description.trim(),
                        descriptionId = descriptionId.trim(),
                        example = example.trim(),
                        synonyms = synonyms.map { it.trim() }.filter { it.isNotBlank() }.joinToString(", "),
                        createdAt = System.currentTimeMillis()
                    )
                )
                _entries.update { listOf(inserted.toEntry()) + it }
                clearLookup()
                Toaster.success("Saved \"$trimmedWord\"")
            } catch (t: Throwable) {
                Logger.debug(TAG, "addEntry failed: ${t.message}")
                Toaster.error(t.message ?: "Failed to save word")
            } finally {
                pendingMutations.update { it - id }
            }
        }
        return true
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
                        language = entry.language,
                        translation = entry.translation,
                        description = entry.description,
                        descriptionId = entry.descriptionId,
                        example = entry.example,
                        synonyms = entry.synonyms.joinToString(", "),
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

    fun startQuiz() {
        val quizzable = _entries.value.filter { it.translation.isNotBlank() }
        val questions = quizzable.shuffled().mapNotNull { entry ->
            val distractors = quizzable
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

    fun canQuiz(): Boolean = _entries.value.count { it.translation.isNotBlank() } >= 2

    private fun DictionaryDto.toEntry(): DictionaryEntry = DictionaryEntry(
        id = id,
        word = word,
        language = language,
        translation = translation,
        description = description,
        descriptionId = descriptionId,
        example = example,
        synonyms = synonyms.split(",").map { it.trim() }.filter { it.isNotBlank() },
        createdAt = createdAt
    )

    private companion object {
        const val TAG = "DictionaryViewModel"
    }
}
