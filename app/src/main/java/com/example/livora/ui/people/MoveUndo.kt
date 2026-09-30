package com.example.livora.ui.people

import android.app.Application
import com.example.livora.data.people.MoveOutcome
import com.example.livora.data.people.PeopleRepository
import com.example.livora.data.people.media.MediaWriter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

object MoveUndo {

    fun run(
        app: Application,
        scope: CoroutineScope,
        repository: PeopleRepository,
        personId: Long,
        outcome: MoveOutcome,
        onDone: suspend () -> Unit
    ) {
        val afterMoves: suspend () -> Unit = {
            if (outcome.duplicated.isNotEmpty()) repository.discardCopies(personId, outcome.duplicated)
            onDone()
        }
        val untrash: () -> Unit = {
            if (outcome.replaced.isEmpty()) {
                scope.launch { afterMoves() }
            } else {
                ConsentBroker.ask(MediaWriter.trashRequest(app, outcome.replaced.map { it.first }, false), scope) {
                    repository.restoreSwaps(outcome.replaced)
                    afterMoves()
                }
            }
        }
        if (outcome.moved.isEmpty()) {
            untrash()
        } else {
            ConsentBroker.ask(MediaWriter.writeRequest(app, outcome.moved), scope) {
                for ((path, group) in outcome.moved.groupBy { outcome.previous[it] ?: "Pictures/" }) {
                    MediaWriter.applyMove(app, group, path)
                }
                repository.syncPhotoDates(outcome.moved)
                repository.dropAiMoves(outcome.moved)
                untrash()
            }
        }
    }
}
