package com.example.livora.ui.dictionary

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
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
import androidx.compose.foundation.layout.offset
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
import androidx.compose.runtime.derivedStateOf
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.livora.data.dictionary.ClozeBuilder
import com.example.livora.data.model.ClozeLevel
import com.example.livora.data.model.ClozeQuestion
import com.example.livora.data.model.ClozeToken
import com.example.livora.ui.components.AppButton
import com.example.livora.ui.components.ButtonKind
import com.example.livora.ui.components.Tag
import com.example.livora.ui.components.TopBar
import kotlin.math.roundToInt

private val GhostLift = 44.dp
private val DropSlop = 12.dp

private enum class SlotState { Empty, Hover, Filled, Correct, Wrong }

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
    var selectedChip by remember { mutableStateOf<Int?>(null) }
    var checked by remember { mutableStateOf(false) }
    var dragChip by remember { mutableStateOf<Int?>(null) }
    var dragPos by remember { mutableStateOf(Offset.Zero) }
    var ghostSize by remember { mutableStateOf(IntSize.Zero) }
    var containerOrigin by remember { mutableStateOf(Offset.Zero) }
    val blankBounds = remember { mutableMapOf<Int, Rect>() }
    val chipOrigins = remember { mutableMapOf<Int, Offset>() }
    val scrollState = rememberScrollState()
    val density = LocalDensity.current
    val liftPx = with(density) { GhostLift.toPx() }
    val slopPx = with(density) { DropSlop.toPx() }

    fun dropPoint(): Offset = Offset(dragPos.x, dragPos.y - liftPx)

    fun blankAt(point: Offset): Int? =
        blankBounds.entries
            .filter { it.value.inflate(slopPx).contains(point) }
            .minByOrNull { (it.value.center - point).getDistance() }
            ?.key

    val hoverBlank by remember {
        derivedStateOf { if (dragChip == null) null else blankAt(dropPoint()) }
    }

    fun place(chip: Int, blank: Int) {
        if (checked) return
        placed = placed.filterValues { it != chip } + (blank to chip)
        selectedChip = null
    }

    fun isCorrect(slot: ClozeToken.Slot): Boolean =
        placed[slot.index]?.let { question.bank[it].lowercase() in slot.accepted } == true

    LaunchedEffect(checked) {
        if (checked) {
            withFrameNanos { }
            scrollState.animateScrollTo(scrollState.maxValue)
        }
    }

    Box(modifier = modifier.onGloballyPositioned { containerOrigin = it.positionInWindow() }) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState, enabled = dragChip == null)
                .padding(horizontal = 20.dp)
        ) {
            Spacer(modifier = Modifier.height(16.dp))
            QuizProgress(current = number, total = total)
            Spacer(modifier = Modifier.height(20.dp))
            Text(
                text = if (question.level == ClozeLevel.Sentence)
                    "Drag the missing word into the blank"
                else
                    "Drag a word into every blank, then check",
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
                                text = chip?.let { ClozeBuilder.display(question.bank[it], token.capitalize) },
                                trailing = token.trailing,
                                state = when {
                                    !checked -> when {
                                        hoverBlank == token.index -> SlotState.Hover
                                        chip != null -> SlotState.Filled
                                        else -> SlotState.Empty
                                    }
                                    isCorrect(token) -> SlotState.Correct
                                    else -> SlotState.Wrong
                                },
                                onPositioned = { blankBounds[token.index] = it },
                                onClick = {
                                    if (!checked) {
                                        if (chip != null) {
                                            placed = placed - token.index
                                        } else {
                                            selectedChip?.let { place(it, token.index) }
                                        }
                                    }
                                },
                                modifier = Modifier.align(Alignment.CenterVertically)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(28.dp))
            Text(
                text = "Word bank",
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
            )
            Spacer(modifier = Modifier.height(8.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                question.bank.forEachIndexed { id, text ->
                    if (id !in placed.values) {
                        key(id) {
                            val dragModifier = if (checked) {
                                Modifier
                            } else {
                                Modifier.pointerInput(id) {
                                    detectDragGestures(
                                        onDragStart = { offset ->
                                            dragChip = id
                                            selectedChip = null
                                            dragPos = (chipOrigins[id] ?: Offset.Zero) + offset
                                        },
                                        onDragEnd = {
                                            val chip = dragChip
                                            val target = blankAt(dropPoint())
                                            if (chip != null && target != null) place(chip, target)
                                            dragChip = null
                                        },
                                        onDragCancel = { dragChip = null },
                                        onDrag = { change, amount ->
                                            change.consume()
                                            dragPos += amount
                                        }
                                    )
                                }
                            }
                            BankChip(
                                text = text,
                                selected = selectedChip == id,
                                lifted = dragChip == id,
                                enabled = !checked,
                                onClick = { selectedChip = if (selectedChip == id) null else id },
                                modifier = Modifier
                                    .onGloballyPositioned { chipOrigins[id] = it.positionInWindow() }
                                    .then(dragModifier)
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

            Spacer(modifier = Modifier.height(16.dp))
            if (!checked) {
                AppButton(
                    text = "Check answers",
                    onClick = {
                        selectedChip = null
                        checked = true
                    },
                    kind = ButtonKind.Primary,
                    enabled = placed.size == slots.size,
                    modifier = Modifier.fillMaxWidth()
                )
            } else {
                AppButton(
                    text = if (isLast) "Finish" else "Next question",
                    onClick = { onNext(slots.all { isCorrect(it) }) },
                    kind = ButtonKind.Primary,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            Spacer(modifier = Modifier.height(30.dp))
        }

        val lifted = dragChip
        if (lifted != null) {
            Text(
                text = question.bank[lifted],
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier
                    .offset {
                        val point = dropPoint()
                        IntOffset(
                            (point.x - containerOrigin.x - ghostSize.width / 2f).roundToInt(),
                            (point.y - containerOrigin.y - ghostSize.height / 2f).roundToInt()
                        )
                    }
                    .onSizeChanged { ghostSize = it }
                    .shadow(8.dp, RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(12.dp))
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            )
        }
    }
}

@Composable
private fun BlankSlot(
    text: String?,
    trailing: String,
    state: SlotState,
    onPositioned: (Rect) -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scheme = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(10.dp)
    val border = when (state) {
        SlotState.Hover -> scheme.primary
        SlotState.Correct -> scheme.onSurface
        SlotState.Wrong -> scheme.error
        SlotState.Filled -> scheme.onSurface.copy(alpha = 0.5f)
        SlotState.Empty -> scheme.onSurface.copy(alpha = 0.25f)
    }
    val fill = when (state) {
        SlotState.Hover -> scheme.primary.copy(alpha = 0.12f)
        SlotState.Filled, SlotState.Correct -> scheme.onSurface.copy(alpha = 0.08f)
        else -> Color.Transparent
    }
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .defaultMinSize(minWidth = 96.dp, minHeight = 40.dp)
                .onGloballyPositioned { onPositioned(it.boundsInWindow()) }
                .background(fill, shape)
                .border(if (state == SlotState.Hover) 2.dp else 1.dp, border, shape)
                .clip(shape)
                .clickable(role = Role.Button, onClick = onClick)
                .padding(horizontal = 10.dp),
            contentAlignment = Alignment.Center
        ) {
            if (text != null) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = text,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
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
    selected: Boolean,
    lifted: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scheme = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(12.dp)
    Box(
        modifier = modifier
            .alpha(if (lifted) 0.35f else 1f)
            .defaultMinSize(minHeight = 44.dp)
            .clip(shape)
            .background(if (selected) scheme.primary else scheme.surfaceContainerHigh)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            color = if (selected) scheme.onPrimary else scheme.onSurface
        )
    }
}
