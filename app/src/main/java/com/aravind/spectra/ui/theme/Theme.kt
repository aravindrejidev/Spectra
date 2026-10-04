package com.aravind.spectra.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

object SpectraColors {
    // backdrop (dark walnut)
    val WoodTop = Color(0xFF3A2A1E)
    val WoodBottom = Color(0xFF140E0A)

    // brushed aluminium
    val MetalHi = Color(0xFFF1F2F5)
    val MetalMid = Color(0xFFCBCFD6)
    val MetalLo = Color(0xFF9DA2AC)
    val MetalEdgeDark = Color(0xFF4B4F57)
    val Ink = Color(0xFF23262B)
    val InkSoft = Color(0xFF555A64)

    // glass screens + phosphor
    val ScreenTop = Color(0xFF0C1411)
    val ScreenBottom = Color(0xFF030504)
    val Phosphor = Color(0xFF6DFFB4)
    val PhosphorDim = Color(0xFF3A8F69)

    // lamps
    val Amber = Color(0xFFFFB347)
    val Red = Color(0xFFFF5A4D)
    val Green = Color(0xFF4DFF9A)
    val Blue = Color(0xFF59B6FF)
}

private val Scheme = darkColorScheme(
    primary = SpectraColors.Phosphor,
    background = SpectraColors.WoodBottom,
    surface = SpectraColors.MetalMid,
    onBackground = SpectraColors.MetalHi,
    onSurface = SpectraColors.Ink
)

@Composable
fun SpectraTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = Scheme, content = content)
}
