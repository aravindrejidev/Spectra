package com.aravind.spectra.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

object SpectraColors {
    val Void = Color(0xFF08090D)
    val Card = Color(0xFF13141C)
    val CardAlt = Color(0xFF1B1D29)
    val Border = Color(0xFF262838)
    val Text1 = Color(0xFFEEF0F6)
    val Text2 = Color(0xFF8B91A7)
    val Text3 = Color(0xFF5A6079)
    val Accent = Color(0xFF7C6CF0)
    val AccentSoft = Color(0x247C6CF0)
    val Warn = Color(0xFFFF6A6A)
    val WarnSoft = Color(0x1FFF6A6A)
    val Good = Color(0xFF4FD68C)
    val GoodSoft = Color(0x1F4FD68C)
}

private val DarkColors = darkColorScheme(
    background = SpectraColors.Void,
    surface = SpectraColors.Card,
    primary = SpectraColors.Accent,
    onBackground = SpectraColors.Text1,
    onSurface = SpectraColors.Text1,
    outline = SpectraColors.Border
)

@Composable
fun SpectraTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = DarkColors, content = content)
}
