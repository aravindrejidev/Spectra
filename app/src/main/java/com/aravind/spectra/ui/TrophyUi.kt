package com.aravind.spectra.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aravind.spectra.engage.Tier
import com.aravind.spectra.engage.Trophies
import com.aravind.spectra.engage.Trophy
import com.aravind.spectra.ui.theme.SpectraColors
import kotlinx.coroutines.delay
import java.util.Locale
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

private fun DrawScope.drawMedal(tier: Tier, unlocked: Boolean) {
    val r = size.minDimension / 2f * 0.92f
    val c = center
    val (hi, lo) = if (!unlocked) {
        Color(0xFF8A8F99) to Color(0xFF4A4E56)
    } else when (tier) {
        Tier.GOLD -> Color(0xFFFFE08A) to Color(0xFFB88A1E)
        Tier.SILVER -> Color(0xFFF2F4F8) to Color(0xFF8E96A3)
        Tier.BRONZE -> Color(0xFFF0B27A) to Color(0xFF9A5B2E)
    }
    drawCircle(Color.Black.copy(alpha = 0.35f), r + 1.5.dp.toPx(), c + Offset(0f, 2.dp.toPx()))
    drawCircle(Brush.linearGradient(listOf(hi, lo), start = c - Offset(r, r), end = c + Offset(r, r)), r, c)
    drawCircle(
        Brush.radialGradient(listOf(hi.shade(0.9f), lo.shade(0.85f)), center = c - Offset(r * 0.2f, r * 0.2f), radius = r),
        r * 0.74f, c
    )
    val star = Path()
    val outer = r * 0.46f
    val inner = outer * 0.42f
    for (i in 0 until 10) {
        val rad = if (i % 2 == 0) outer else inner
        val ang = Math.toRadians(-90.0 + i * 36.0)
        val px = c.x + (rad * cos(ang)).toFloat()
        val py = c.y + (rad * sin(ang)).toFloat()
        if (i == 0) star.moveTo(px, py) else star.lineTo(px, py)
    }
    star.close()
    if (unlocked) {
        drawPath(star, Color.White.copy(alpha = 0.92f))
    } else {
        drawPath(star, Color.Black.copy(alpha = 0.45f), style = Stroke(1.5.dp.toPx()))
    }
    drawCircle(Color.Black.copy(alpha = 0.45f), r, c, style = Stroke(1.dp.toPx()))
}

@Composable
private fun Medal(t: Trophy, unlocked: Boolean, selected: Boolean, onClick: () -> Unit) {
    val scale by animateFloatAsState(
        if (selected) 1.12f else 1f,
        spring(dampingRatio = 0.5f, stiffness = 400f),
        label = "medal"
    )
    Column(
        Modifier
            .fillMaxWidth()
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Canvas(Modifier.size(54.dp).graphicsLayer { scaleX = scale; scaleY = scale }) { drawMedal(t.tier, unlocked) }
        Spacer(Modifier.height(4.dp))
        Text(
            t.title,
            fontSize = 10.sp,
            lineHeight = 12.sp,
            maxLines = 2,
            textAlign = TextAlign.Center,
            fontWeight = FontWeight.SemiBold,
            color = if (unlocked) SpectraColors.Ink else SpectraColors.InkSoft.copy(alpha = 0.6f)
        )
    }
}

/** Home-screen shelf with every medal and the user's lifetime numbers. */
@Composable
fun TrophyShelfUnit() {
    val context = LocalContext.current
    LaunchedEffect(Unit) { Trophies.ensureLoaded(context) }
    val unlocked by Trophies.unlocked.collectAsState()
    val stats by Trophies.stats.collectAsState()
    var selected by remember { mutableStateOf<String?>(null) }

    RackPanel("Trophies", lamp = if (unlocked.isEmpty()) SpectraColors.Amber else SpectraColors.Green) {
        Engraved("${unlocked.size} OF ${Trophies.all.size} UNLOCKED", size = 11.sp, spacing = 1.5.sp)
        Spacer(Modifier.height(10.dp))
        for (row in Trophies.all.chunked(4)) {
            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                for (t in row) {
                    Column(Modifier.weight(1f)) {
                        Medal(t, t.id in unlocked, selected == t.id) {
                            selected = if (selected == t.id) null else t.id
                        }
                    }
                }
                repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }

        val sel = Trophies.all.firstOrNull { it.id == selected }
        if (sel != null) {
            Spacer(Modifier.height(8.dp))
            GlassScreen {
                Column(Modifier.fillMaxWidth().padding(14.dp)) {
                    Lcd(
                        sel.title.uppercase(),
                        color = if (sel.id in unlocked) SpectraColors.Amber else SpectraColors.PhosphorDim,
                        size = 14.sp,
                        weight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(4.dp))
                    Lcd(sel.hint, color = SpectraColors.Phosphor, size = 12.sp)
                    Spacer(Modifier.height(4.dp))
                    Lcd(
                        if (sel.id in unlocked) "UNLOCKED" else "LOCKED",
                        color = if (sel.id in unlocked) SpectraColors.Green else SpectraColors.PhosphorDim,
                        size = 11.sp
                    )
                }
            }
        }

        Spacer(Modifier.height(14.dp))
        ReadoutGrid(
            listOf(
                Readout("Tracks analyzed", stats.tracks.toString()),
                Readout("Audio analyzed", String.format(Locale.US, "%.1f h", stats.seconds / 3600.0)),
                Readout(
                    "Fakes caught", stats.fakes.toString(),
                    if (stats.fakes > 0) SpectraColors.Amber else SpectraColors.Phosphor
                ),
                Readout("Best DR", if (stats.bestDr >= 0f) "DR${stats.bestDr.roundToInt()}" else "—"),
                Readout("Loudest", if (stats.loudest > -99f) "${f1(stats.loudest.toDouble())} LUFS" else "—"),
                Readout("Highest rate", if (stats.maxRate > 0) fmtHz(stats.maxRate.toDouble()) else "—")
            )
        )
        if (stats.bestDrName.isNotEmpty() || stats.loudestName.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            Text(
                (if (stats.bestDrName.isNotEmpty()) "Most dynamic: ${stats.bestDrName}. " else "") +
                    (if (stats.loudestName.isNotEmpty()) "Loudest: ${stats.loudestName}." else ""),
                color = SpectraColors.InkSoft,
                fontSize = 12.sp,
                lineHeight = 16.sp
            )
        }
    }
}

/** Slides a plaque in from the top whenever a new medal is unlocked. */
@Composable
fun TrophyToastHost(modifier: Modifier = Modifier) {
    val pending by Trophies.pending.collectAsState()
    val first = pending.firstOrNull()
    var shown by remember { mutableStateOf<Trophy?>(null) }
    val haptic = LocalHapticFeedback.current

    LaunchedEffect(first?.id) {
        if (first != null) {
            shown = first
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            delay(3800)
            Trophies.dismissFirst()
        }
    }

    AnimatedVisibility(
        visible = first != null,
        modifier = modifier,
        enter = slideInVertically(spring(dampingRatio = 0.6f, stiffness = 300f)) { -it } + fadeIn(),
        exit = slideOutVertically { -it } + fadeOut()
    ) {
        val t = shown
        if (t != null) {
            RackPanel("Trophy unlocked", lamp = SpectraColors.Green) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Canvas(Modifier.size(56.dp)) { drawMedal(t.tier, true) }
                    Spacer(Modifier.width(14.dp))
                    Column {
                        Engraved(t.title.uppercase(), size = 14.sp, spacing = 1.sp, color = SpectraColors.Ink)
                        Spacer(Modifier.height(2.dp))
                        Text(t.hint, color = SpectraColors.InkSoft, fontSize = 12.sp, lineHeight = 16.sp)
                    }
                }
            }
        }
    }
}
