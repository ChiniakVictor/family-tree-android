package com.victor.familytree.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val Light = lightColorScheme(
    primary = Color(0xFF2F5D50),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFC8E6D8),
    onPrimaryContainer = Color(0xFF0B2E24),
    secondary = Color(0xFF7A5C3E),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFF1DFC9),
    onSecondaryContainer = Color(0xFF2C1C0B),
    tertiary = Color(0xFF5B5F8F),
    background = Color(0xFFFAF8F3),
    surface = Color(0xFFFAF8F3),
    surfaceVariant = Color(0xFFE6E2D8),
    surfaceContainer = Color(0xFFF0EDE5),
    surfaceContainerHigh = Color(0xFFEAE6DD),
    outlineVariant = Color(0xFFCFC9BC),
)

private val Dark = darkColorScheme(
    primary = Color(0xFF9FD1BD),
    onPrimary = Color(0xFF00382B),
    primaryContainer = Color(0xFF15463A),
    onPrimaryContainer = Color(0xFFC8E6D8),
    secondary = Color(0xFFDDC2A2),
    secondaryContainer = Color(0xFF5A4229),
    onSecondaryContainer = Color(0xFFF1DFC9),
    tertiary = Color(0xFFC0C3F7),
    background = Color(0xFF151A18),
    surface = Color(0xFF151A18),
    surfaceContainer = Color(0xFF1E2421),
    surfaceContainerHigh = Color(0xFF262D2A),
)

object FamilyColors {
    val male = Color(0xFF4F7FB0)
    val female = Color(0xFFC0705A)
    val unknown = Color(0xFF8A8A8A)
    val high = Color(0xFF2E7D32)
    val medium = Color(0xFFB26A00)
    val low = Color(0xFFC62828)
    val unrated = Color(0xFF6D6D6D)
}

@Composable
fun FamilyTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (isSystemInDarkTheme()) Dark else Light, content = content)
}
