package com.aravind.spectra.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aravind.spectra.settings.AppSettings
import com.aravind.spectra.ui.theme.SpectraColors

/** Round metal back button with a chevron. */
@Composable
fun BackButton(onClick: () -> Unit) {
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.9f else 1f, spring(dampingRatio = 0.55f, stiffness = 500f), label = "back")
    Canvas(
        Modifier
            .size(44.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clickable(interactionSource = src, indication = null, onClick = onClick)
    ) {
        val r = size.minDimension / 2f
        val c = center
        drawCircle(Color.Black.copy(alpha = 0.4f), r, c + Offset(0f, 2.dp.toPx()))
        drawCircle(
            Brush.linearGradient(listOf(Color(0xFFF4F5F7), Color(0xFF9EA3AD)), start = c - Offset(r, r), end = c + Offset(r, r)),
            r, c
        )
        drawCircle(Color(0xFF50555F), r, c, style = Stroke(1.dp.toPx()))
        val chevron = Path().apply {
            moveTo(c.x + r * 0.12f, c.y - r * 0.34f)
            lineTo(c.x - r * 0.2f, c.y)
            lineTo(c.x + r * 0.12f, c.y + r * 0.34f)
        }
        drawPath(chevron, SpectraColors.Ink, style = Stroke(r * 0.16f, cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}

/** Round metal gear button that opens Settings. */
@Composable
fun GearButton(onClick: () -> Unit) {
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.9f else 1f, spring(dampingRatio = 0.55f, stiffness = 500f), label = "gear")
    Canvas(
        Modifier
            .size(40.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                rotationZ = if (pressed) 45f else 0f
            }
            .clickable(interactionSource = src, indication = null, onClick = onClick)
    ) {
        val r = size.minDimension / 2f
        val c = center
        val dark = Color(0xFF3A3E46)
        drawCircle(Color.Black.copy(alpha = 0.4f), r, c + Offset(0f, 2.dp.toPx()))
        drawCircle(
            Brush.linearGradient(listOf(Color(0xFFF4F5F7), Color(0xFF9EA3AD)), start = c - Offset(r, r), end = c + Offset(r, r)),
            r, c
        )
        drawCircle(Color(0xFF50555F), r, c, style = Stroke(1.dp.toPx()))
        for (k in 0 until 8) {
            rotate(k * 45f, c) {
                drawRoundRect(
                    dark,
                    Offset(c.x - r * 0.09f, c.y - r * 0.68f),
                    Size(r * 0.18f, r * 0.26f),
                    CornerRadius(r * 0.04f)
                )
            }
        }
        drawCircle(dark, r * 0.5f, c)
        drawCircle(Color(0xFFD6D9DF), r * 0.22f, c)
    }
}

/** Big page title on the wood backdrop, with a back button above it. */
@Composable
internal fun PageHeader(title: String, onBack: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(top = 6.dp, bottom = 4.dp)) {
        BackButton(onBack)
        Spacer(Modifier.height(12.dp))
        Text(
            title,
            color = SpectraColors.MetalHi,
            fontSize = 34.sp,
            fontWeight = FontWeight.Bold,
            style = TextStyle(shadow = Shadow(Color.Black.copy(alpha = 0.7f), Offset(0f, 3f), 8f))
        )
    }
}

@Composable
internal fun RowTitle(text: String) {
    Text(text, color = SpectraColors.Ink, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
}

@Composable
internal fun RowSub(text: String) {
    Text(text, color = SpectraColors.InkSoft, fontSize = 12.sp, lineHeight = 16.sp)
}

@Composable
internal fun Chevron() {
    Canvas(Modifier.size(width = 10.dp, height = 16.dp)) {
        val p = Path().apply {
            moveTo(1.dp.toPx(), 1.dp.toPx())
            lineTo(size.width - 1.dp.toPx(), size.height / 2f)
            lineTo(1.dp.toPx(), size.height - 1.dp.toPx())
        }
        drawPath(p, SpectraColors.InkSoft, style = Stroke(2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}

/** An engraved groove between rows. */
@Composable
internal fun RowDivider() {
    Column(Modifier.fillMaxWidth()) {
        Box(Modifier.fillMaxWidth().height(1.dp).background(Color.Black.copy(alpha = 0.14f)))
        Box(Modifier.fillMaxWidth().height(1.dp).background(Color.White.copy(alpha = 0.7f)))
    }
}

/** Toggle switch: metal knob sliding in a groove that lights up when on. */
@Composable
fun HwSwitch(checked: Boolean, onChange: (Boolean) -> Unit) {
    val x by animateFloatAsState(if (checked) 1f else 0f, spring(dampingRatio = 0.6f, stiffness = 500f), label = "switch")
    val haptic = LocalHapticFeedback.current
    Canvas(
        Modifier
            .size(width = 58.dp, height = 32.dp)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                if (AppSettings.haptics) haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onChange(!checked)
            }
    ) {
        val w = size.width
        val h = size.height
        drawRoundRect(
            Brush.verticalGradient(listOf(Color(0xFF3A3D44), Color(0xFFD7DAE0))),
            cornerRadius = CornerRadius(h / 2f)
        )
        if (x > 0f) {
            drawRoundRect(
                SpectraColors.Green.copy(alpha = (0.55f * x).coerceIn(0f, 1f)),
                Offset(3.dp.toPx(), 3.dp.toPx()),
                Size(w - 6.dp.toPx(), h - 6.dp.toPx()),
                CornerRadius(h / 2f)
            )
        }
        val r = h / 2f - 2.dp.toPx()
        val cx = h / 2f + x * (w - h)
        val cy = h / 2f
        drawCircle(Color.Black.copy(alpha = 0.35f), r, Offset(cx + 1.dp.toPx(), cy + 2.dp.toPx()))
        drawCircle(
            Brush.radialGradient(
                listOf(Color(0xFFFAFAFA), Color(0xFFA9AEB8)),
                center = Offset(cx - r * 0.3f, cy - r * 0.3f),
                radius = r * 1.4f
            ),
            r, Offset(cx, cy)
        )
        drawCircle(Color(0xFF50555F), r, Offset(cx, cy), style = Stroke(1.dp.toPx()))
    }
}

@Composable
fun NavRow(title: String, subtitle: String?, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            RowTitle(title)
            if (subtitle != null) RowSub(subtitle)
        }
        Chevron()
    }
}

@Composable
fun SwitchRow(title: String, subtitle: String?, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            RowTitle(title)
            if (subtitle != null) RowSub(subtitle)
        }
        HwSwitch(checked, onChange)
    }
}

@Composable
fun ChoiceRow(title: String, subtitle: String?, options: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(vertical = 10.dp)) {
        RowTitle(title)
        if (subtitle != null) RowSub(subtitle)
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            options.forEachIndexed { i, label ->
                HwButton(label, Modifier.weight(1f), active = selected == i, height = 40.dp, compact = true) { onSelect(i) }
            }
        }
    }
}

@Composable
fun SliderRow(title: String, valueText: String, value: Float, range: ClosedFloatingPointRange<Float>, onChange: (Float) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(vertical = 10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f)) { RowTitle(title) }
            Engraved(valueText, size = 12.sp, spacing = 1.sp)
        }
        HwFader(value, onChange, range)
    }
}

@Composable
fun ActionRow(title: String, subtitle: String?, button: String, onClick: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(vertical = 10.dp)) {
        RowTitle(title)
        if (subtitle != null) RowSub(subtitle)
        Spacer(Modifier.height(8.dp))
        HwButton(button, Modifier.fillMaxWidth(), height = 42.dp, onClick = onClick)
    }
}

@Composable
fun InfoRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalAlignment = Alignment.Top) {
        Box(Modifier.weight(0.4f)) { RowTitle(label) }
        Box(Modifier.weight(0.6f)) { Text(value, color = SpectraColors.InkSoft, fontSize = 13.sp, lineHeight = 17.sp) }
    }
}
