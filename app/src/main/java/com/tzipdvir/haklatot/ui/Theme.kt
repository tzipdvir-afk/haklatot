package com.tzipdvir.haklatot.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import com.tzipdvir.haklatot.R

/** "Night blue" design: deep navy with orange, matching the app icon. Always dark. */
object Navy {
    val Bg = Color(0xFF0A1430)
    val Surface = Color(0xFF122150)
    val Surface2 = Color(0xFF1A2C63)
    val Ink = Color(0xFFF3F6FF)
    val Mute = Color(0xFF9DB0DA)
    val Rec = Color(0xFFFF5A3C)
    val Accent = Color(0xFFF59A2E)
    val AccentInk = Color(0xFF2A1400)
    val Chip = Color(0xFF1E3270)
    val Line = Color(0xFF22377A)
}

@OptIn(ExperimentalTextApi::class)
private fun assistant(weight: Int) = Font(
    R.font.assistant,
    weight = FontWeight(weight),
    variationSettings = FontVariation.Settings(FontVariation.weight(weight)),
)

val Assistant = FontFamily(assistant(400), assistant(500), assistant(600), assistant(700), assistant(800))

private val Scheme = darkColorScheme(
    primary = Navy.Accent,
    onPrimary = Navy.AccentInk,
    primaryContainer = Navy.Surface2,
    onPrimaryContainer = Navy.Ink,
    secondary = Navy.Accent,
    onSecondary = Navy.AccentInk,
    tertiaryContainer = Navy.Surface2,
    onTertiaryContainer = Navy.Ink,
    background = Navy.Bg,
    onBackground = Navy.Ink,
    surface = Navy.Surface,
    onSurface = Navy.Ink,
    surfaceVariant = Navy.Chip,
    onSurfaceVariant = Navy.Mute,
    surfaceContainer = Navy.Surface,
    surfaceContainerHigh = Navy.Surface2,
    surfaceContainerHighest = Navy.Surface2,
    outline = Navy.Line,
    outlineVariant = Navy.Line,
    error = Navy.Rec,
    onError = Color.White,
    errorContainer = Color(0xFF5A1A12),
    onErrorContainer = Color(0xFFFFDAD3),
)

private val AppTypography = Typography().let { t ->
    fun TextStyle.a() = copy(fontFamily = Assistant)
    Typography(
        displayLarge = t.displayLarge.a(), displayMedium = t.displayMedium.a(), displaySmall = t.displaySmall.a(),
        headlineLarge = t.headlineLarge.a(), headlineMedium = t.headlineMedium.a(), headlineSmall = t.headlineSmall.a(),
        titleLarge = t.titleLarge.a(), titleMedium = t.titleMedium.a(), titleSmall = t.titleSmall.a(),
        bodyLarge = t.bodyLarge.a(), bodyMedium = t.bodyMedium.a(), bodySmall = t.bodySmall.a(),
        labelLarge = t.labelLarge.a(), labelMedium = t.labelMedium.a(), labelSmall = t.labelSmall.a(),
    )
}

/** App theme. Always right-to-left (Hebrew). */
@Composable
fun HaklatotTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = Scheme, typography = AppTypography) {
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl, content = content)
    }
}
