package com.example.livora.ui.daily

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.Quiz
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.livora.ui.components.TopBar
import com.example.livora.ui.dictionary.DictionaryScreen
import com.example.livora.ui.dictionary.DictionaryViewModel
import com.example.livora.ui.expenses.ExpensesPage
import com.example.livora.ui.people.SegmentTabs
import com.example.livora.ui.todo.TodoScreen
import com.example.livora.ui.todo.TodoViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch

@Composable
fun DailyScreen(
    todoViewModel: TodoViewModel,
    dictionaryViewModel: DictionaryViewModel,
    onOpenTodoDetail: (String) -> Unit,
    onOpenQuiz: () -> Unit
) {
    val pager = rememberPagerState { 3 }
    val segment = pager.currentPage
    val scope = rememberCoroutineScope()
    val taskAdd = remember { MutableSharedFlow<Unit>(extraBufferCapacity = 1) }
    val wordAdd = remember { MutableSharedFlow<Unit>(extraBufferCapacity = 1) }
    val quiz = remember { MutableSharedFlow<Unit>(extraBufferCapacity = 1) }
    val expenseAdd = remember { MutableSharedFlow<Unit>(extraBufferCapacity = 1) }
    val expenseExport = remember { MutableSharedFlow<Unit>(extraBufferCapacity = 1) }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            Column {
                TopBar(
                    title = "Daily",
                    subtitle = when (segment) {
                        0 -> "Routines and streaks"
                        1 -> "Translate to Indonesian and quiz"
                        else -> "Spending this month"
                    },
                    actions = {
                        when (segment) {
                            0 -> IconButton(onClick = { taskAdd.tryEmit(Unit) }) {
                                Icon(
                                    imageVector = Icons.Default.Add,
                                    contentDescription = "Add routine",
                                    tint = MaterialTheme.colorScheme.onSurface
                                )
                            }
                            1 -> {
                                IconButton(onClick = { quiz.tryEmit(Unit) }) {
                                    Icon(
                                        imageVector = Icons.Default.Quiz,
                                        contentDescription = "Start quiz",
                                        tint = if (dictionaryViewModel.canQuiz()) {
                                            MaterialTheme.colorScheme.onSurface
                                        } else {
                                            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                                        }
                                    )
                                }
                                IconButton(onClick = { wordAdd.tryEmit(Unit) }) {
                                    Icon(
                                        imageVector = Icons.Default.Add,
                                        contentDescription = "Add word",
                                        tint = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                            else -> {
                                IconButton(onClick = { expenseExport.tryEmit(Unit) }) {
                                    Icon(
                                        imageVector = Icons.Default.FileDownload,
                                        contentDescription = "Export expenses as CSV",
                                        tint = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                                IconButton(onClick = { expenseAdd.tryEmit(Unit) }) {
                                    Icon(
                                        imageVector = Icons.Default.Add,
                                        contentDescription = "Add expense",
                                        tint = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                        }
                    }
                )
                SegmentTabs(
                    labels = listOf("Tasks", "Dictionary", "Expenses"),
                    selected = segment,
                    onSelect = { scope.launch { pager.animateScrollToPage(it) } }
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, thickness = 0.5.dp)
            }
        }
    ) { innerPadding ->
        HorizontalPager(
            state = pager,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            key = { it }
        ) { page ->
            when (page) {
                0 -> TodoScreen(
                    viewModel = todoViewModel,
                    onOpenDetail = onOpenTodoDetail,
                    addRequests = taskAdd
                )
                1 -> DictionaryScreen(
                    viewModel = dictionaryViewModel,
                    onOpenQuiz = onOpenQuiz,
                    addRequests = wordAdd,
                    quizRequests = quiz
                )
                else -> ExpensesPage(addRequests = expenseAdd, exportRequests = expenseExport)
            }
        }
    }
}
