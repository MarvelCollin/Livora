package com.example.livora.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme = darkColorScheme(
    primary = NightPrimary,
    onPrimary = NightOnPrimary,
    primaryContainer = NightPrimaryContainer,
    onPrimaryContainer = NightText,
    secondary = NightPeach,
    onSecondary = PeachInk,
    secondaryContainer = NightPeachContainer,
    onSecondaryContainer = NightText,
    tertiary = NightPeach,
    onTertiary = PeachInk,
    tertiaryContainer = NightPeachContainer,
    onTertiaryContainer = NightText,
    background = NightBackground,
    onBackground = NightText,
    surface = NightBackground,
    onSurface = NightText,
    surfaceVariant = NightSurfaceHigh,
    onSurfaceVariant = NightTextVariant,
    surfaceContainerLowest = NightSurfaceLowest,
    surfaceContainerLow = NightSurfaceLow,
    surfaceContainer = NightSurface,
    surfaceContainerHigh = NightSurfaceHigh,
    surfaceContainerHighest = NightSurfaceHighest,
    outline = NightLine,
    outlineVariant = NightLineVariant,
    error = NightDanger,
    onError = ClayDangerInk,
    errorContainer = NightDangerContainer,
    onErrorContainer = ClayDangerSoft
)

private val LightColorScheme = lightColorScheme(
    primary = SageStrong,
    onPrimary = Color.White,
    primaryContainer = SageSoft,
    onPrimaryContainer = SageInk,
    secondary = PeachStrong,
    onSecondary = Color.White,
    secondaryContainer = PeachSoft,
    onSecondaryContainer = PeachInk,
    tertiary = PeachStrong,
    onTertiary = Color.White,
    tertiaryContainer = PeachSoft,
    onTertiaryContainer = PeachInk,
    background = SageBackground,
    onBackground = SageInk,
    surface = SageBackground,
    onSurface = SageInk,
    surfaceVariant = SageSurfaceHigh,
    onSurfaceVariant = SageInkVariant,
    surfaceContainerLowest = SageSurfaceLowest,
    surfaceContainerLow = SageSurfaceLow,
    surfaceContainer = SageSurface,
    surfaceContainerHigh = SageSurfaceHigh,
    surfaceContainerHighest = SageSurfaceHighest,
    outline = SageLine,
    outlineVariant = SageSurfaceHighest,
    error = ClayDanger,
    onError = Color.White,
    errorContainer = ClayDangerSoft,
    onErrorContainer = ClayDangerInk
)

@Composable
fun AppTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }

        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
