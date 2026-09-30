package com.example.livora.ui.people

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.livora.data.people.MergeSuggestion
import com.example.livora.data.people.PeopleServices
import com.example.livora.data.people.db.PersonSummary
import com.example.livora.ui.components.ToastType
import com.example.livora.ui.components.Toaster
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class MergeCard(
    val suggestion: MergeSuggestion,
    val a: PersonSummary,
    val b: PersonSummary,
    val facesA: List<Long>,
    val facesB: List<Long>
)

sealed interface MergeListState {
    data object Loading : MergeListState
    class Ready(val cards: List<MergeCard>) : MergeListState
}

class MergeSuggestionsViewModel(application: Application) : AndroidViewModel(application) {

    private val services = PeopleServices.get(application)
    private val repository = services.repository

    private val listState = MutableStateFlow<MergeListState>(MergeListState.Loading)
    val state: StateFlow<MergeListState> = listState.asStateFlow()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            listState.value = MergeListState.Loading
            val suggestions = repository.mergeSuggestions()
            val cards = ArrayList<MergeCard>()
            for (s in suggestions) {
                val a = repository.summary(s.personA) ?: continue
                val b = repository.summary(s.personB) ?: continue
                cards.add(MergeCard(s, a, b, repository.topFaceIds(a.id, 4), repository.topFaceIds(b.id, 4)))
            }
            listState.value = MergeListState.Ready(cards)
        }
    }

    private fun remove(card: MergeCard) {
        val current = listState.value as? MergeListState.Ready ?: return
        listState.value = MergeListState.Ready(current.cards.filter { it !== card })
    }

    fun merge(card: MergeCard) {
        viewModelScope.launch {
            val result = repository.samePerson(listOf(card.a.id, card.b.id))
            if (result == null) {
                Toaster.error("These two could not be merged")
                return@launch
            }
            remove(card)
            Toaster.show(
                message = "Merged into one person",
                type = ToastType.Success,
                durationMs = 7000,
                actionLabel = "Undo",
                onAction = {
                    viewModelScope.launch {
                        result.undo.restore()
                        load()
                    }
                }
            )
        }
    }

    fun notTheSame(card: MergeCard) {
        viewModelScope.launch {
            val undo = repository.separate(card.a.id, card.b.id)
            remove(card)
            Toaster.show(
                message = "Kept them apart",
                type = ToastType.Success,
                durationMs = 7000,
                actionLabel = "Undo",
                onAction = {
                    viewModelScope.launch {
                        undo.restore()
                        load()
                    }
                }
            )
        }
    }

    fun mergeAll() {
        val current = listState.value as? MergeListState.Ready ?: return
        val pairs = current.cards.map { Pair(it.a.id, it.b.id) }
        if (pairs.isEmpty()) return
        viewModelScope.launch {
            val undo = repository.mergeGroups(pairs)
            listState.value = MergeListState.Ready(emptyList())
            if (undo == null) {
                Toaster.error("Nothing could be merged")
            } else {
                Toaster.show(
                    message = "Merged ${pairs.size} pairs",
                    type = ToastType.Success,
                    durationMs = 8000,
                    actionLabel = "Undo",
                    onAction = {
                        viewModelScope.launch {
                            undo.restore()
                            load()
                        }
                    }
                )
            }
        }
    }
}
