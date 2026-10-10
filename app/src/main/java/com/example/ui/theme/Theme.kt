package com.example.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.TextFieldColors
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val DarkColorScheme = darkColorScheme(
    primary = PrimaryGreen,
    onPrimary = Color.White,
    primaryContainer = PrimaryGreenDark,
    onPrimaryContainer = Color.White,
    secondary = PrimaryGreen,
    onSecondary = Color.White,
    background = DarkAppBackground,
    surface = DarkCardSurface,
    surfaceVariant = DarkCardSurfaceSubtle,
    onBackground = DarkTextPrimary,
    onSurface = DarkTextPrimary,
    onSurfaceVariant = DarkTextSecondary
)

private val LightColorScheme = lightColorScheme(
    primary = PrimaryGreen,
    onPrimary = Color.White,
    primaryContainer = PrimaryGreenLight,
    onPrimaryContainer = DarkText,
    secondary = PrimaryGreenLight,
    onSecondary = Color.White,
    background = AppBackground,
    surface = CardSurface,
    surfaceVariant = CardSurfaceSubtle,
    onBackground = DarkText,
    onSurface = DarkText,
    onSurfaceVariant = MutedText
)

@Composable
fun reclaimTextFieldColors(): TextFieldColors {
    return OutlinedTextFieldDefaults.colors(
        focusedTextColor = DarkText,
        unfocusedTextColor = DarkText,
        focusedBorderColor = PrimaryGreen,
        unfocusedBorderColor = Color(0xFFCBD5E1),
        cursorColor = PrimaryGreen,
        focusedLabelColor = PrimaryGreen,
        unfocusedLabelColor = MutedText,
        focusedPlaceholderColor = SubtleText,
        unfocusedPlaceholderColor = SubtleText,
        focusedContainerColor = CardSurface,
        unfocusedContainerColor = CardSurface,
        disabledContainerColor = CardSurfaceSubtle,
        disabledTextColor = SubtleText
    )
}

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false, // Keep consistent Design System palette
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

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window
            if (window != null) {
                val insetsController = WindowCompat.getInsetsController(window, view)
                // Reverse status bar icon colors relative to app background:
                // When app background is white / light (luminance > 0.5f), status bar icons (time, wifi, network, battery) are DARK.
                // When app background is dark (luminance <= 0.5f), status bar icons are WHITE.
                val isLightBg = colorScheme.background.luminance() > 0.5f
                insetsController.isAppearanceLightStatusBars = isLightBg
                insetsController.isAppearanceLightNavigationBars = isLightBg
            }
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}

