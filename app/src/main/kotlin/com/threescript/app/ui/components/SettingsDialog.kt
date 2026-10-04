package com.threescript.app.ui.components

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import androidx.compose.ui.window.Dialog
import com.threescript.app.AppState
import com.threescript.app.ui.*

/**
 * SettingsDialog — floating modal for API key and engine configuration.
 * Edits are written to AppState only; the EngineClient reads from there.
 */
@Composable
fun SettingsDialog(state: AppState, onDismiss: () -> Unit) {
    var geminiKey by remember { mutableStateOf(state.geminiApiKey) }
    var model by remember { mutableStateOf(state.geminiModel) }
    var threshold by remember { mutableStateOf(state.deadZoneThreshold.toString()) }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            color = BgCard,
            shape = RoundedCornerShape(14.dp),
            border = BorderStroke(1.dp, BorderStrong),
            modifier = Modifier.width(480.dp),
        ) {
            Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                // Header
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Settings, contentDescription = null, tint = Brand, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Engine Settings", color = TextHigh, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.weight(1f))
                    IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = TextMuted, modifier = Modifier.size(16.dp))
                    }
                }

                Divider(color = BorderSubtle)

                // AI Provider section
                SettingSection("AI ENGINE") {
                    SettingField(
                        label = "Gemini API Key",
                        value = geminiKey,
                        onValueChange = { geminiKey = it },
                        placeholder = "AQ.Ab8R...",
                        isPassword = true,
                    )
                    SettingField(
                        label = "Gemini Model",
                        value = model,
                        onValueChange = { model = it },
                        placeholder = "gemini-3.8-flash",
                    )
                }

                SettingSection("STORY METRICS") {
                    SettingField(
                        label = "Dead Zone Threshold (Δd)",
                        value = threshold,
                        onValueChange = { threshold = it },
                        placeholder = "1.5",
                        hint = "Scene transitions with 3D score movement below this value are flagged",
                    )
                }

                Divider(color = BorderSubtle)

                // Buttons
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    Spacer(Modifier.weight(1f))
                    OutlinedButton(
                        onClick = onDismiss,
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = TextMid),
                        border = BorderStroke(1.dp, BorderStrong),
                    ) { Text("Cancel", fontSize = 13.sp) }

                    Button(
                        onClick = {
                            state.geminiApiKey = geminiKey.trim()
                            state.geminiModel = model.trim()
                            state.deadZoneThreshold = threshold.toDoubleOrNull()?.coerceAtLeast(0.1) ?: 1.5
                            onDismiss()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Brand),
                        shape = RoundedCornerShape(8.dp),
                    ) { Text("Save", fontSize = 13.sp) }
                }
            }
        }
    }
}

@Composable
private fun SettingSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(title, fontSize = 9.sp, fontWeight = FontWeight.Bold, color = TextMuted, letterSpacing = 2.sp)
        content()
    }
}

@Composable
private fun SettingField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String = "",
    isPassword: Boolean = false,
    hint: String = "",
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, fontSize = 11.sp, color = TextMid, fontWeight = FontWeight.Medium)
        Surface(
            color = BgSurface,
            shape = RoundedCornerShape(6.dp),
            border = BorderStroke(1.dp, BorderStrong),
            modifier = Modifier.fillMaxWidth(),
        ) {
            BasicTextField(
                value = if (isPassword && value.isNotEmpty()) "•".repeat(minOf(value.length, 32)) else value,
                onValueChange = { if (!isPassword || !it.contains("•")) onValueChange(it) },
                textStyle = TextStyle(color = TextHigh, fontSize = 12.sp),
                cursorBrush = SolidColor(Brand),
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(10.dp, 8.dp),
                decorationBox = { inner ->
                    if (value.isEmpty()) Text(placeholder, color = TextMuted, fontSize = 12.sp)
                    inner()
                }
            )
        }
        if (hint.isNotEmpty()) Text(hint, fontSize = 9.sp, color = TextMuted)
    }
}
