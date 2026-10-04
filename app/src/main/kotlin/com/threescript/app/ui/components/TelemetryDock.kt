package com.threescript.app.ui.components

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import com.threescript.app.AppState
import com.threescript.app.ui.*

/**
 * TelemetryDock — bottom strip showing aggregate narrative statistics.
 */
@Composable
fun TelemetryDock(state: AppState) {
    val track = state.track
    Surface(color = BgSurface, tonalElevation = 0.dp) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(44.dp)
                .padding(horizontal = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            TelemetryItem(
                label = "TRAJECTORY",
                value = track?.let { String.format("%.2f", it.summary.totalDistance) } ?: "—"
            )
            TelemetryItem(
                label = "MEAN VELOCITY",
                value = track?.let { String.format("%.2f", it.summary.meanVelocity) } ?: "—"
            )
            TelemetryItem(
                label = "DEAD ZONES",
                value = track?.summary?.deadZoneCount?.toString() ?: "—",
                valueColor = if ((track?.summary?.deadZoneCount ?: 0) > 0) FlagDead else null,
            )
            TelemetryItem(
                label = "SCENES",
                value = track?.summary?.sceneCount?.toString() ?: "—"
            )
            TelemetryItem(
                label = "PACING",
                value = track?.summary?.pacingRating ?: "—"
            )

            // Axis regression slope indicators
            track?.let { t ->
                Spacer(Modifier.weight(1f))
                SlopeIndicator("X", t.summary.xStats.regressionSlope, AxisPlot)
                SlopeIndicator("Y", t.summary.yStats.regressionSlope, AxisChar)
                SlopeIndicator("Z", t.summary.zStats.regressionSlope, AxisEmot)
            }
        }
    }
}

@Composable
private fun TelemetryItem(label: String, value: String, valueColor: Color? = null) {
    Column {
        Text(label, fontSize = 8.sp, color = TextMuted, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
        Text(value, fontSize = 12.sp, color = valueColor ?: TextHigh, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun SlopeIndicator(axis: String, slope: Double, color: Color) {
    val arrow = when {
        slope > 0.3  -> "↑"
        slope < -0.3 -> "↓"
        else          -> "→"
    }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        Box(Modifier.size(6.dp).clip(RoundedCornerShape(3.dp)).background(color))
        Text("$axis $arrow", fontSize = 10.sp, color = color)
    }
}
