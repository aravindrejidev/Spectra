package com.aravind.spectra.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aravind.spectra.ui.theme.SpectraColors

@Composable
fun SectionCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 12.dp),
        colors = CardDefaults.cardColors(containerColor = SpectraColors.Card),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(
                text = title.uppercase(),
                color = SpectraColors.Text2,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 0.5.sp
            )
            Spacer(Modifier.height(12.dp))
            content()
        }
    }
}

@Composable
fun KeyValueRow(label: String, value: String) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = label, color = SpectraColors.Text3, fontSize = 13.sp)
        Text(
            text = value,
            color = SpectraColors.Text1,
            fontSize = 12.sp,
            fontFamily = FontFamily.Monospace,
            textAlign = TextAlign.End
        )
    }
}

@Composable
private fun StatCell(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier.padding(vertical = 6.dp)) {
        Text(text = label.uppercase(), color = SpectraColors.Text3, fontSize = 10.5.sp, letterSpacing = 0.4.sp)
        Spacer(Modifier.height(2.dp))
        Text(text = value, color = SpectraColors.Text1, fontSize = 14.sp, fontFamily = FontFamily.Monospace)
    }
}

@Composable
fun StatGrid(pairs: List<Pair<String, String>>) {
    Column {
        for (row in pairs.chunked(2)) {
            Row(Modifier.fillMaxWidth()) {
                StatCell(row[0].first, row[0].second, Modifier.weight(1f))
                if (row.size > 1) {
                    StatCell(row[1].first, row[1].second, Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
fun FlagBanner(text: String, isWarning: Boolean) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isWarning) SpectraColors.WarnSoft else SpectraColors.GoodSoft
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(12.dp),
            color = if (isWarning) Color(0xFFFFC4C4) else Color(0xFFBDF5D6),
            fontSize = 13.sp,
            lineHeight = 18.sp
        )
    }
}
