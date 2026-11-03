package com.example.qingtu

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightPageBg = Color(0xFFF6F7F3)
private val LightDeepGreen = Color(0xFF496B5B)
private val LightSoftGreen = Color(0xFFE7EFE8)
private val LightInk = Color(0xFF26332C)
private val LightMuted = Color(0xFF89938B)

@Composable
internal fun QingTuTheme(darkTheme: Boolean, content: @Composable () -> Unit) {
    val scheme = if (darkTheme) androidx.compose.material3.darkColorScheme(
        primary = Color(0xFFA8C9B2),
        onPrimary = Color(0xFF183528),
        background = Color(0xFF121A16),
        surface = Color(0xFF1D2822),
        onSurface = Color(0xFFE4ECE6),
        onBackground = Color(0xFFE4ECE6),
        secondaryContainer = Color(0xFF2D4035),
        onSecondaryContainer = Color(0xFFDCE9DF),
        surfaceVariant = Color(0xFF29362E),
        onSurfaceVariant = Color(0xFFB1BEB5)
    ) else androidx.compose.material3.lightColorScheme(
        primary = LightDeepGreen,
        onPrimary = Color.White,
        background = LightPageBg,
        surface = Color.White,
        onSurface = LightInk,
        onBackground = LightInk,
        secondaryContainer = LightSoftGreen,
        onSecondaryContainer = LightInk,
        surfaceVariant = Color(0xFFE9EEE9),
        onSurfaceVariant = LightMuted
    )
    MaterialTheme(colorScheme = scheme, content = content)
}

internal val PageBg: Color @Composable get() = MaterialTheme.colorScheme.background
internal val DeepGreen: Color @Composable get() = MaterialTheme.colorScheme.primary
internal val SoftGreen: Color @Composable get() = MaterialTheme.colorScheme.secondaryContainer
internal val Ink: Color @Composable get() = MaterialTheme.colorScheme.onBackground
internal val Muted: Color @Composable get() = MaterialTheme.colorScheme.onSurfaceVariant
