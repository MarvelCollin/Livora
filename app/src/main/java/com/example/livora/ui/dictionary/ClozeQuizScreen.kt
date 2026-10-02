package com.example.livora.ui.dictionary

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.livora.data.dictionary.ClozeBuilder
import com.example.livora.data.model.ClozeLevel
import com.example.livora.data.model.ClozeQuestion
import com.example.livora.data.model.ClozeToken
import com.example.livora.ui.components.AppButton
import com.example.livora.ui.components.ButtonKind
import com.example.livora.ui.components.Motion
import com.example.livora.ui.components.Tag
import com.example.livora.ui.components.TopBar
import com.example.livora.ui.components.pressScale

private val BlankTextStyle = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.SemiBold)

private enum class SlotState { Empty, Active, Filled, Correct, Wrong }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ClozeQuizScreen(
    viewModel: DictionaryViewModel,
    questions: List<ClozeQuestion>,
    onBack: () -> Unit
) {
    var index by remember { mutableIntStateOf(0) }
    var score by remember { mutableIntStateOf(0) }
    var finished by remember { mutableStateOf(false) }
    val level = questions.first().level

    Scaffold(
        topBar = {
            TopBar(
                title = if (level == ClozeLevel.Sentence) "Sentence quiz" else "Paragraph quiz",
                subtitle = if (!finished) "Question ${index + 1} of ${questions.size}" else null,
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                        )
                    }
                }
            )
        }
    ) { innerPadding ->
        if (finished) {
            QuizResult(
                score = score,
                total = questions.size,
                synonymScore = 0,
                synonymTotal = 0,
                onRestart = {
                    viewModel.restartQuiz()
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

        val current = index.coerceAtMost(questions.lastIndex)
        key(current) {
            ClozeQuestionContent(
                question = questions[current],
                number = current + 1,
                total = questions.size,
                isLast = current == questions.lastIndex,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                onNext = { correct ->
                    if (correct) score++
                    if (current == questions.lastIndex) finished = true else index++
                }
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ClozeQuestionContent(
    question: ClozeQuestion,
    number: Int,
    total: Int,
    isLast: Boolean,
    modifier: Modifier,
    onNext: (Boolean) -> Unit
) {
    val slots = question.slots
    var placed by remember { mutableStateOf(mapOf<Int, Int>()) }
    var activeBlank by remember { mutableStateOf<Int?>(null) }
    var checked by remember { mutableStateOf(false) }
    val scrollState = rememberScrollState()
    val haptic = LocalHapticFeedback.current
    val density = LocalDensity.current
    val textMeasurer = rememberTextMeasurer()

    val blankWidth = remember(question, density) {
        val widest = question.bank.maxOf { word ->
            textMeasurer.measure(ClozeBuilder.display(word, true), BlankTextStyle).size.width
        }
        with(density) { widest.toDp() } + 52.dp
    }.coerceIn(96.dp, 220.dp)

    val firstEmpty = slots.firstOrNull { it.index !in placed }?.index
    val target = activeBlank?.takeIf { it !in placed } ?: firstEmpty
    val usedChips = placed.values.toSet()

    fun isCorrect(slot: ClozeToken.Slot): Boolean =
        placed[slot.index]?.let { question.bank[it].lowercase() in slot.accepted } == true

    fun place(chip: Int) {
        val blank = target ?: return
        placed = placed + (blank to chip)
        activeBlank = null
        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
    }

    fun tapBlank(blank: Int) {
        if (checked) return
        if (blank in placed) placed = placed - blank
        activeBlank = blank
        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
    }

    fun reset() {
        placed = emptyMap()
        activeBlank = null
    }

    LaunchedEffect(checked) {
        if (checked) {
            withFrameNanos { }
            scrollState.animateScrollTo(scrollState.maxValue)
        }
    }

    Column(modifier = modifier) {
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(scrollState)
                .padding(horizontal = 20.dp)
        ) {
            Spacer(modifier = Modifier.height(16.dp))
            QuizProgress(current = number, total = total)
            Spacer(modifier = Modifier.height(20.dp))
            Text(
                text = if (question.level == ClozeLevel.Sentence)
                    "Tap a word to fill the blank"
                else
                    "Tap a word to fill the highlighted blank. Tap a filled blank to change it",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
            )
            Spacer(modifier = Modifier.height(14.dp))

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                question.tokens.forEach { token ->
                    when (token) {
                        is ClozeToken.Word -> Text(
                            text = token.value,
                            fontSize = 17.sp,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.align(Alignment.CenterVertically)
                        )
                        is ClozeToken.Slot -> {
                            val chip = placed[token.index]
                            BlankSlot(
                                number = token.index + 1,
                                text = chip?.let { ClozeBuilder.display(question.bank[it], token.capitalize) },
                                trailing = token.trailing,
                                minWidth = blankWidth,
                                state = when {
                                    !checked -> when {
                                        token.index == target -> SlotState.Active
                                        chip != null -> SlotState.Filled
                                        else -> SlotState.Empty
                                    }
                                    isCorrect(token) -> SlotState.Correct
                                    else -> SlotState.Wrong
                                },
                                onClick = { tapBlank(token.index) },
                                modifier = Modifier.align(Alignment.CenterVertically)
                            )
                        }
                    }
                }
            }

            if (checked) {
                val correctCount = slots.count { isCorrect(it) }
                Spacer(modifier = Modifier.height(20.dp))
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
                        text = if (correctCount == slots.size) "All ${slots.size} correct" else "$correctCount of ${slots.size} correct",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "“${ClozeBuilder.fill(question)}”",
                        fontSize = 13.sp,
                        fontStyle = FontStyle.Italic,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f)
                    )
                    if (question.meanings.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "Indonesian",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            question.meanings.forEach { (word, meaning) -> Tag(text = "$word · $meaning") }
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(20.dp))
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)
                )
                .padding(horizontal = 20.dp, vertical = 16.dp)
        ) {
            if (!checked) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Word bank",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                    )
                    Text(
                        text = "${placed.size} of ${slots.size} filled",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                }
                Spacer(modifier = Modifier.height(10.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    question.bank.forEachIndexed { id, word ->
                        BankChip(
                            text = word,
                            used = id in usedChips,
                            enabled = id !in usedChips && target != null,
                            onClick = { place(id) }
                        )
                    }
                }
                Spacer(modifier = Modifier.height(14.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AppButton(
                        text = "Reset",
                        onClick = { reset() },
                        enabled = placed.isNotEmpty(),
                        modifier = Modifier.weight(1f)
                    )
                    AppButton(
                        text = "Check answers",
                        onClick = {
                            activeBlank = null
                            checked = true
                        },
                        kind = ButtonKind.Primary,
                        enabled = placed.size == slots.size,
                        modifier = Modifier.weight(2f)
                    )
                }
            } else {
                AppButton(
                    text = if (isLast) "Finish" else "Next question",
                    onClick = { onNext(slots.all { isCorrect(it) }) },
                    kind = ButtonKind.Primary,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

@Composable
private fun BlankSlot(
    number: Int,
    text: String?,
    trailing: String,
    minWidth: Dp,
    state: SlotState,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scheme = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(10.dp)
    val borderColor by animateColorAsState(
        targetValue = when (state) {
            SlotState.Active -> scheme.primary
            SlotState.Correct -> scheme.onSurface
            SlotState.Wrong -> scheme.error
            SlotState.Filled -> scheme.onSurface.copy(alpha = 0.5f)
            SlotState.Empty -> scheme.onSurface.copy(alpha = 0.25f)
        },
        animationSpec = Motion.quick(),
        label = "blankBorder"
    )
    val fillColor by animateColorAsState(
        targetValue = when (state) {
            SlotState.Active -> scheme.primary.copy(alpha = 0.12f)
            SlotState.Filled, SlotState.Correct -> scheme.onSurface.copy(alpha = 0.08f)
            else -> Color.Transparent
        },
        animationSpec = Motion.quick(),
        label = "blankFill"
    )
    val borderWidth by animateDpAsState(
        targetValue = if (state == SlotState.Active) 2.dp else 1.dp,
        animationSpec = Motion.quick(),
        label = "blankBorderWidth"
    )
    val description = buildString {
        append("Blank $number, ")
        append(if (text == null) "empty" else "$text, tap to remove")
        if (state == SlotState.Active) append(", next to fill")
    }
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .defaultMinSize(minWidth = minWidth, minHeight = 44.dp)
                .background(fillColor, shape)
                .border(borderWidth, borderColor, shape)
                .clip(shape)
                .semantics(mergeDescendants = true) { contentDescription = description }
                .clickable(role = Role.Button, onClick = onClick)
                .padding(horizontal = 10.dp),
            contentAlignment = Alignment.Center
        ) {
            AnimatedContent(
                targetState = text,
                transitionSpec = {
                    (fadeIn(Motion.quick()) + scaleIn(Motion.quick(), initialScale = 0.85f)) togetherWith fadeOut(Motion.quick())
                },
                label = "blankWord"
            ) { word ->
                if (word != null) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = word,
                            style = BlankTextStyle,
                            color = scheme.onSurface
                        )
                        when (state) {
                            SlotState.Correct -> Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = "Correct",
                                modifier = Modifier.size(16.dp),
                                tint = scheme.onSurface
                            )
                            SlotState.Wrong -> Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Wrong",
                                modifier = Modifier.size(16.dp),
                                tint = scheme.error
                            )
                            else -> Unit
                        }
                    }
                } else {
                    Spacer(modifier = Modifier.size(1.dp))
                }
            }
        }
        if (trailing.isNotEmpty()) {
            Text(
                text = trailing,
                fontSize = 17.sp,
                color = scheme.onSurface
            )
        }
    }
}

@Composable
private fun BankChip(
    text: String,
    used: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scheme = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(12.dp)
    val interaction = remember { MutableInteractionSource() }
    val alpha by animateFloatAsState(
        targetValue = if (used) 0.28f else 1f,
        animationSpec = Motion.quick(),
        label = "chipAlpha"
    )
    Box(
        modifier = modifier
            .alpha(alpha)
            .pressScale(interaction, 0.94f)
            .defaultMinSize(minHeight = 48.dp)
            .clip(shape)
            .background(scheme.surfaceContainerHigh)
            .clickable(
                interactionSource = interaction,
                indication = LocalIndication.current,
                enabled = enabled,
                role = Role.Button,
                onClick = onClick
            )
            .padding(horizontal = 16.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            color = scheme.onSurface
        )
    }
}
