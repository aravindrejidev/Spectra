package com.aravind.spectra.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import com.aravind.spectra.settings.AppSettings

/** True while the Modern look (the default) is selected in Settings > Appearance. */
val isModernUi: Boolean
    get() = AppSettings.uiStyle == AppSettings.STYLE_MODERN

/** Colours of the Modern look: near-black glass cards with a red accent. */
object ModernColors {
    // page
    val BgTop = Color(0xFF0D0D12)
    val BgBottom = Color(0xFF050507)

    // glass cards and the recessed areas inside them
    val CardTop = Color(0xFF1A1B21)
    val CardBottom = Color(0xFF0F1014)
    val InsetTop = Color(0xFF0C0C10)
    val InsetBottom = Color(0xFF07070A)

    // brand accent
    val Accent = Color(0xFFFF2D3F)
    val AccentHi = Color(0xFFFF5260)
    val AccentLo = Color(0xFFC4101F)
    val AccentDeep = Color(0xFF6E0A14)

    // text
    val TextHi = Color(0xFFF4F5F8)
    val TextMid = Color(0xFFA3A6B3)
    val TextLow = Color(0xFF8C8F9E)
    val TextFaint = Color(0xFF6F7280)

    // status
    val Good = Color(0xFF3DDC84)
    val Warn = Color(0xFFFFB84D)
    val Bad = Color(0xFFFF4D5A)
    val Info = Color(0xFF5AA9FF)

    // frames (cover art, avatars)
    val FrameDark = Color(0xFF2A2B33)
    val FrameLight = Color(0xFF3C3D48)
}

/**
 * The palette every screen reads. Each value follows the selected look, so one piece of UI code
 * serves both the Modern and the Classic (rack unit) style. Exports (image / PDF) keep using
 * [SpectraColors] directly so they look the same in either style.
 */
object Pal {
    private val modern: Boolean get() = isModernUi

    /** Main text. */
    val Ink: Color get() = if (modern) ModernColors.TextHi else SpectraColors.Ink

    /** Secondary text and captions. */
    val InkSoft: Color get() = if (modern) ModernColors.TextMid else SpectraColors.InkSoft

    /** Values on a display (Classic: phosphor green, Modern: bright white). */
    val Phosphor: Color get() = if (modern) ModernColors.TextHi else SpectraColors.Phosphor

    /** Dim labels on a display. */
    val PhosphorDim: Color get() = if (modern) ModernColors.TextLow else SpectraColors.PhosphorDim

    /** Graph lines, probes and progress (Classic: phosphor green, Modern: red accent). */
    val Plot: Color get() = if (modern) ModernColors.Accent else SpectraColors.Phosphor

    /** The call-to-action glow (Classic: green, Modern: red accent). */
    val Cta: Color get() = if (modern) ModernColors.Accent else SpectraColors.Green

    val Amber: Color get() = if (modern) ModernColors.Warn else SpectraColors.Amber
    val Red: Color get() = if (modern) ModernColors.Bad else SpectraColors.Red
    val Green: Color get() = if (modern) ModernColors.Good else SpectraColors.Green
    val Blue: Color get() = if (modern) ModernColors.Info else SpectraColors.Blue

    /** Page titles on the backdrop. */
    val Title: Color get() = if (modern) ModernColors.TextHi else SpectraColors.MetalHi

    /** Small print on the backdrop. */
    val Footer: Color get() = if (modern) ModernColors.TextFaint else Color(0xFFB9A58F)

    /** Font of readout text: monospace like an LCD (Classic) or the normal app font (Modern). */
    val Mono: FontFamily get() = if (modern) FontFamily.Default else FontFamily.Monospace

    val FrameDark: Color get() = if (modern) ModernColors.FrameDark else SpectraColors.MetalEdgeDark
    val FrameLight: Color get() = if (modern) ModernColors.FrameLight else SpectraColors.MetalLo
}
