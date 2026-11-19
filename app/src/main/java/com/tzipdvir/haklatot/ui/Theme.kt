package com.tzipdvir.haklatot.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection

// Colors taken from the icon: deep blue background, orange sound waves.
private val Light = lightColorScheme(
    primary = Color(0xFF074C9F),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD6E3FF),
    onPrimaryContainer = Color(0xFF001B3F),
    secondary = Color(0xFFE8730C),
    onSecondary = Color.White,
    tertiaryContainer = Color(0xFFFFDCC2),
    onTertiaryContainer = Color(0xFF2E1500),
    background = Color(0xFFF8F9FF),
    surface = Color(0xFFF8F9FF),
)

private val Dark = darkColorScheme(
    primary = Color(0xFFA9C7FF),
    onPrimary = Color(0xFF003064),
    primaryContainer = Color(0xFF00468F),
    onPrimaryContainer = Color(0xFFD6E3FF),
    secondary = Color(0xFFFFB77C),
    onSecondary = Color(0xFF4C2700),
    tertiaryContainer = Color(0xFF6B3A00),
    onTertiaryContainer = Color(0xFFFFDCC2),
    background = Color(0xFF111318),
    surface = Color(0xFF111318),
)

/** App theme. Always right-to-left (Hebrew), light/dark follows the phone. */
@Composable
fun HaklatotTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (isSystemInDarkTheme()) Dark else Light) {
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl, content = content)
    }
}
