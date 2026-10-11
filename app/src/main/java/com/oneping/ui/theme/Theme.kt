package com.oneping.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/** Palette built around the hummingbird logo's purple (#5F138E). */
private val Light = lightColorScheme(
    primary = Color(0xFF5F138E),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFF0DBFF),
    onPrimaryContainer = Color(0xFF2C0050),
    secondary = Color(0xFF665A6F),
    secondaryContainer = Color(0xFFEDDDF6),
    onSecondaryContainer = Color(0xFF211829),
    tertiary = Color(0xFF805157),
    background = Color(0xFFFFF7FD),
    surface = Color(0xFFFFF7FD),
    surfaceContainerLow = Color(0xFFF8F0FA),
    surfaceContainer = Color(0xFFF2EAF4),
    surfaceVariant = Color(0xFFEADFEC),
    onSurfaceVariant = Color(0xFF4C4450),
    outlineVariant = Color(0xFFCEC3D0),
)

private val Dark = darkColorScheme(
    primary = Color(0xFFDCB8FF),
    onPrimary = Color(0xFF47007A),
    primaryContainer = Color(0xFF5F138E),
    onPrimaryContainer = Color(0xFFF0DBFF),
    secondary = Color(0xFFD0C1DA),
    secondaryContainer = Color(0xFF4E4357),
    onSecondaryContainer = Color(0xFFEDDDF6),
    tertiary = Color(0xFFF3B7BE),
    background = Color(0xFF161218),
    surface = Color(0xFF161218),
    surfaceContainerLow = Color(0xFF1E1A21),
    surfaceContainer = Color(0xFF231E25),
    surfaceVariant = Color(0xFF4C4450),
    onSurfaceVariant = Color(0xFFCEC3D0),
    outlineVariant = Color(0xFF4C4450),
)

@Composable
fun OnePingTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (isSystemInDarkTheme()) Dark else Light, content = content)
}
