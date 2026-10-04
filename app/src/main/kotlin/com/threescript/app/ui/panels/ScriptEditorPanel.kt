package com.threescript.app.ui.panels

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.*
import androidx.compose.ui.unit.*
import com.threescript.app.AppState
import com.threescript.app.ui.*
import org.apache.pdfbox.Loader
import org.apache.pdfbox.text.PDFTextStripper
import java.io.File
import javax.swing.JFileChooser
import javax.swing.filechooser.FileNameExtensionFilter

/**
 * ScriptEditorPanel — left-pane screenplay editor.
 *
 * Presents a dark monospaced text editor styled after professional
 * screenwriting software (Courier Prime font, slugline coloring).
 * Contains the Analyze trigger button.
 */
@Composable
fun ScriptEditorPanel(
    modifier: Modifier = Modifier,
    state: AppState,
    onAnalyze: () -> Unit,
) {
    Column(modifier.background(BgBase)) {
        Row(
            Modifier.fillMaxWidth().height(70.dp).background(BgSurface).padding(horizontal = 18.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text("PROJECT", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = TextMuted)
                BasicTextField(
                    value = state.projectTitle,
                    onValueChange = { state.projectTitle = it },
                    singleLine = true,
                    textStyle = TextStyle(color = TextHigh, fontSize = 17.sp, fontWeight = FontWeight.Bold),
                    cursorBrush = SolidColor(Brand),
                    decorationBox = { inner ->
                        if (state.projectTitle.isEmpty()) Text("Untitled screenplay", color = TextMuted, fontSize = 17.sp)
                        inner()
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            if (state.hasResults) {
                Text("${state.sceneCount} SCENES", fontSize = 9.sp, color = TextMid, fontWeight = FontWeight.Bold)
            }
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(BorderSubtle))

        Row(
            Modifier.fillMaxWidth().height(40.dp).background(BgBase).padding(horizontal = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Paste Fountain, plain text, or scene headings", fontSize = 10.sp, color = TextMuted, modifier = Modifier.weight(1f))
            if (state.scriptText.isNotEmpty()) {
                TextButton(onClick = {
                    state.scriptText = ""
                    state.track = null
                    state.selectedPoint = null
                }) {
                    Text("Clear", color = TextMid, fontSize = 11.sp)
                }
            }
        }

        // Script textarea
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp)
        ) {
            val scroll = rememberScrollState()
            BasicTextField(
                value = state.scriptText,
                onValueChange = { state.scriptText = it },
                textStyle = TextStyle(
                    color = TextHigh,
                    fontSize = 13.sp,
                    fontFamily = FontFamily.Monospace,
                    lineHeight = 20.sp,
                    letterSpacing = 0.sp,
                ),
                cursorBrush = SolidColor(Brand),
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(scroll),
                decorationBox = { innerField ->
                    if (state.scriptText.isEmpty()) {
                        Text(
                            text = "Paste your screenplay here…\n\nSupports Fountain format (INT./EXT. sluglines)\nor plain prose narrative text.",
                            color = TextMuted,
                            fontSize = 13.sp,
                            fontFamily = FontFamily.Monospace,
                            lineHeight = 20.sp,
                        )
                    }
                    innerField()
                }
            )
        }

        // Action bar
        Surface(color = BgSurface, tonalElevation = 0.dp) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Spacer(Modifier.weight(1f))

                TextButton(
                    onClick = { importScreenplay(state) },
                    colors = ButtonDefaults.textButtonColors(contentColor = TextMid),
                ) {
                    Icon(Icons.Default.FolderOpen, contentDescription = null, modifier = Modifier.size(15.dp))
                    Spacer(Modifier.width(5.dp))
                    Text("Import", fontSize = 12.sp)
                }

                // Analyze button
                Button(
                    onClick = onAnalyze,
                    enabled = state.scriptText.isNotEmpty() && !state.isAnalyzing && state.engineReady,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Brand,
                        contentColor = Color.White,
                        disabledContainerColor = BorderStrong,
                        disabledContentColor = TextMuted,
                    ),
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                ) {
                    if (state.isAnalyzing) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(14.dp),
                            color = Color.White,
                            strokeWidth = 2.dp,
                        )
                        Spacer(Modifier.width(6.dp))
                        Text("Analyzing…", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    } else {
                        Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(15.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Analyze Script", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}

private fun importScreenplay(state: AppState) {
    val chooser = JFileChooser().apply {
        dialogTitle = "Import screenplay"
        isMultiSelectionEnabled = false
        fileFilter = FileNameExtensionFilter(
            "Screenplay files (*.pdf, *.fountain, *.txt, *.scr, *.md)",
            "pdf", "fountain", "txt", "scr", "md",
        )
    }

    if (chooser.showOpenDialog(null) != JFileChooser.APPROVE_OPTION) return

    val file: File = chooser.selectedFile
    try {
        val text = if (file.extension.equals("pdf", ignoreCase = true)) {
            extractPdfText(file)
        } else {
            file.readText(Charsets.UTF_8)
        }
        if (file.extension.equals("pdf", ignoreCase = true) && text.isBlank()) {
            throw IllegalArgumentException("No selectable text found. This PDF may be scanned and need OCR first.")
        }
        state.scriptText = text
        state.projectTitle = file.nameWithoutExtension
        state.track = null
        state.selectedPoint = null
        state.analysisError = null
    } catch (error: Exception) {
        state.analysisError = "Could not open ${file.name}: ${error.message ?: "read failed"}"
    }
}

private fun extractPdfText(file: File): String =
    Loader.loadPDF(file).use { document -> PDFTextStripper().getText(document) }

@Composable
fun PanelHeader(title: String, subtitle: String = "") {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(36.dp)
            .background(BgSurface)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(title, fontSize = 9.sp, fontWeight = FontWeight.Bold, color = TextMuted, letterSpacing = 2.sp)
        if (subtitle.isNotEmpty()) {
            Text("·", color = BorderStrong, fontSize = 9.sp)
            Text(subtitle, fontSize = 9.sp, color = TextMuted)
        }
    }
    Box(Modifier.fillMaxWidth().height(1.dp).background(BorderSubtle))
}
