package com.example.livora.ui.dictionary

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.livora.data.model.DictionaryLanguage
import com.example.livora.ui.components.TopBar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DictionaryQuizScreen(
    viewModel: DictionaryViewModel,
    onBack: () -> Unit
) {
    val quiz by viewModel.quiz.collectAsState()
    var index by remember { mutableIntStateOf(0) }
    var score by remember { mutableIntStateOf(0) }
    var selected by remember(index) { mutableStateOf<Int?>(null) }
    var hintShown by remember(index) { mutableStateOf(false) }
    var finished by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopBar(
                title = "Quiz",
                subtitle = if (quiz.isNotEmpty() && !finished) "Question ${index + 1} of ${quiz.size}" else null,
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                        )
                    }
                },
                actions = {
                    val hintEntry = quiz.getOrNull(index)?.entry
                    val hintAvailable = !finished && hintEntry != null &&
                        (hintEntry.synonyms.isNotEmpty() || hintEntry.example.isNotBlank())
                    if (hintAvailable) {
                        IconButton(onClick = { hintShown = true }) {
                            Icon(
                                imageVector = Icons.Outlined.Lightbulb,
                                contentDescription = "Hint",
                                tint = if (hintShown)
                                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f)
                                else
                                    MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            )
        }
    ) { innerPadding ->
        if (quiz.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Add at least 2 words with translations to start a quiz.",
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                    modifier = Modifier.padding(horizontal = 32.dp)
                )
            }
            return@Scaffold
        }

        if (finished) {
            QuizResult(
                score = score,
                total = quiz.size,
                onRestart = {
                    viewModel.startQuiz()
                    index = 0
                    score = 0
                    finished = false
                },
                onBack = onBack,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            )
            return@Scaffold
        }

        val question = quiz[index]
        val answered = selected != null

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
        ) {
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "What is the Indonesian for",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = question.entry.word,
                    fontSize = 30.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = DictionaryLanguage.fromCode(question.entry.language).label,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    modifier = Modifier
                        .background(
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f),
                            shape = RoundedCornerShape(50)
                        )
                        .padding(horizontal = 10.dp, vertical = 3.dp)
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            if (hintShown && (question.entry.synonyms.isNotEmpty() || question.entry.example.isNotBlank())) {
                HintCard(
                    synonyms = question.entry.synonyms,
                    example = question.entry.example
                )
                Spacer(modifier = Modifier.height(18.dp))
            }

            question.options.forEachIndexed { i, option ->
                OptionRow(
                    text = option,
                    state = optionState(i, selected, question.correctIndex),
                    onClick = {
                        if (selected == null) {
                            selected = i
                            if (i == question.correctIndex) score++
                        }
                    }
                )
                Spacer(modifier = Modifier.height(10.dp))
            }

            if (answered) {
                Spacer(modifier = Modifier.height(8.dp))
                ExplanationCard(
                    correct = question.options[question.correctIndex],
                    descriptionId = question.entry.descriptionId,
                    description = question.entry.description,
                    example = question.entry.example
                )
                Spacer(modifier = Modifier.height(20.dp))
                Text(
                    text = if (index == quiz.lastIndex) "Finish" else "Next question",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.surface,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            color = MaterialTheme.colorScheme.onSurface,
                            shape = RoundedCornerShape(14.dp)
                        )
                        .clickable {
                            if (index == quiz.lastIndex) finished = true else index++
                        }
                        .padding(vertical = 14.dp)
                )
            }

            Spacer(modifier = Modifier.height(30.dp))
        }
    }
}

private enum class OptionState { Idle, Correct, Wrong, MissedCorrect }

private fun optionState(index: Int, selected: Int?, correctIndex: Int): OptionState {
    if (selected == null) return OptionState.Idle
    return when {
        index == correctIndex -> if (selected == correctIndex) OptionState.Correct else OptionState.MissedCorrect
        index == selected -> OptionState.Wrong
        else -> OptionState.Idle
    }
}

@Composable
private fun OptionRow(
    text: String,
    state: OptionState,
    onClick: () -> Unit
) {
    val borderColor = when (state) {
        OptionState.Correct, OptionState.MissedCorrect -> MaterialTheme.colorScheme.onSurface
        OptionState.Wrong -> MaterialTheme.colorScheme.error
        OptionState.Idle -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.15f)
    }
    val background = when (state) {
        OptionState.Correct -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
        OptionState.MissedCorrect -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f)
        else -> Color.Transparent
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(color = background, shape = RoundedCornerShape(14.dp))
            .border(width = 1.dp, color = borderColor, shape = RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = text,
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f)
        )
        when (state) {
            OptionState.Correct, OptionState.MissedCorrect -> Icon(
                imageVector = Icons.Default.Check,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.onSurface
            )
            OptionState.Wrong -> Icon(
                imageVector = Icons.Default.Close,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.error
            )
            OptionState.Idle -> {}
        }
    }
}

@Composable
private fun ExplanationCard(
    correct: String,
    descriptionId: String,
    description: String,
    example: String
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f),
                shape = RoundedCornerShape(14.dp)
            )
            .padding(16.dp)
    ) {
        Text(
            text = "Answer",
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = correct,
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface
        )
        val explanation = descriptionId.ifBlank { description }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = explanation.ifBlank { "No explanation available for this word." },
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f)
        )
        if (description.isNotBlank() && descriptionId.isNotBlank()) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = description,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f)
            )
        }
        if (example.isNotBlank()) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "“$example”",
                fontSize = 12.sp,
                fontStyle = FontStyle.Italic,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f)
            )
        }
    }
}

@Composable
private fun HintCard(
    synonyms: List<String>,
    example: String
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f),
                shape = RoundedCornerShape(14.dp)
            )
            .padding(16.dp)
    ) {
        Text(
            text = "Hint",
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
        )
        if (synonyms.isNotEmpty()) {
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.horizontalScroll(rememberScrollState())
            ) {
                synonyms.forEach { synonym ->
                    Text(
                        text = synonym,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f),
                        modifier = Modifier
                            .background(
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.07f),
                                shape = RoundedCornerShape(50)
                            )
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
            }
        }
        if (example.isNotBlank()) {
            Spacer(modifier = Modifier.height(if (synonyms.isNotEmpty()) 10.dp else 8.dp))
            Text(
                text = "“$example”",
                fontSize = 13.sp,
                fontStyle = FontStyle.Italic,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f)
            )
        }
    }
}

@Composable
private fun QuizResult(
    score: Int,
    total: Int,
    onRestart: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "$score / $total",
            fontSize = 48.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = resultMessage(score, total),
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f)
        )
        Spacer(modifier = Modifier.height(32.dp))
        Text(
            text = "Try again",
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.surface,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    color = MaterialTheme.colorScheme.onSurface,
                    shape = RoundedCornerShape(14.dp)
                )
                .clickable(onClick = onRestart)
                .padding(vertical = 14.dp)
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = "Back to dictionary",
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
            modifier = Modifier
                .clickable(onClick = onBack)
                .padding(vertical = 8.dp)
        )
    }
}

private fun resultMessage(score: Int, total: Int): String {
    if (total == 0) return ""
    val ratio = score.toFloat() / total.toFloat()
    return when {
        ratio >= 0.9f -> "Excellent recall"
        ratio >= 0.6f -> "Nice work, keep going"
        ratio >= 0.3f -> "Getting there"
        else -> "Keep practicing"
    }
}
