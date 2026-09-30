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
    primary = NightCerulean,
    onPrimary = NightCeruleanOn,
    primaryContainer = NightCeruleanContainer,
    onPrimaryContainer = NightCeruleanContainerOn,
    secondary = NightMarigold,
    onSecondary = NightMarigoldOn,
    secondaryContainer = NightMarigoldContainer,
    onSecondaryContainer = NightMarigoldContainerOn,
    tertiary = NightMarigold,
    onTertiary = NightMarigoldOn,
    tertiaryContainer = NightMarigoldContainer,
    onTertiaryContainer = NightMarigoldContainerOn,
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
    error = NightBrick,
    onError = NightBrickOn,
    errorContainer = NightBrickContainer,
    onErrorContainer = NightBrickContainerOn
)

private val LightColorScheme = lightColorScheme(
    primary = Cerulean,
    onPrimary = Color.White,
    primaryContainer = CeruleanSoft,
    onPrimaryContainer = CeruleanInk,
    secondary = Marigold,
    onSecondary = Color.White,
    secondaryContainer = MarigoldSoft,
    onSecondaryContainer = MarigoldInk,
    tertiary = Marigold,
    onTertiary = Color.White,
    tertiaryContainer = MarigoldSoft,
    onTertiaryContainer = MarigoldInk,
    background = PaperBackground,
    onBackground = PaperInk,
    surface = PaperBackground,
    onSurface = PaperInk,
    surfaceVariant = PaperSurfaceHigh,
    onSurfaceVariant = PaperInkVariant,
    surfaceContainerLowest = PaperSurfaceLowest,
    surfaceContainerLow = PaperSurfaceLow,
    surfaceContainer = PaperSurface,
    surfaceContainerHigh = PaperSurfaceHigh,
    surfaceContainerHighest = PaperSurfaceHighest,
    outline = PaperLine,
    outlineVariant = PaperLineVariant,
    error = Brick,
    onError = Color.White,
    errorContainer = BrickSoft,
    onErrorContainer = BrickInk
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
