package com.example.livora.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
fun AnimatedNumber(
    value: Int,
    modifier: Modifier = Modifier,
    content: @Composable (Int) -> Unit
) {
    AnimatedContent(
        targetState = value,
        modifier = modifier,
        transitionSpec = {
            val rising = targetState > initialState
            (slideInVertically(Motion.enter()) { if (rising) it / 2 else -it / 2 } + fadeIn(Motion.enter()))
                .togetherWith(
                    slideOutVertically(Motion.exit()) { if (rising) -it / 2 else it / 2 } + fadeOut(Motion.exit())
                )
                .using(SizeTransform(clip = false))
        },
        label = "animatedNumber"
    ) { number -> content(number) }
}
