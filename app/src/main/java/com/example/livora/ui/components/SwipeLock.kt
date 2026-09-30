package com.example.livora.ui.components

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.compositionLocalOf

val LocalSwipeLock = compositionLocalOf<MutableState<Boolean>?> { null }
