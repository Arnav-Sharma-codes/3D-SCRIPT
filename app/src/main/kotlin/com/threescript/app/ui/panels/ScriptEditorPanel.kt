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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import com.threescript.shared.model.cognitive.CognitiveHighlightToken
import com.threescript.shared.model.cognitive.CognitiveTokenType

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

        // Sub-bar / Mode Switcher
        Row(
            Modifier.fillMaxWidth().height(40.dp).background(BgBase).padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            val tokens = state.track?.cognitiveTokens.orEmpty()
            if (state.hasResults && tokens.isNotEmpty()) {
                Surface(
                    color = if (state.scriptViewMode == 0) Brand.copy(alpha = 0.2f) else Color.Transparent,
                    shape = RoundedCornerShape(4.dp),
                    modifier = Modifier.clickable { state.scriptViewMode = 0 }
                ) {
                    Text("Editor", color = if (state.scriptViewMode == 0) Brand else TextMuted, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
                }
                Surface(
                    color = if (state.scriptViewMode == 1) Brand.copy(alpha = 0.2f) else Color.Transparent,
                    shape = RoundedCornerShape(4.dp),
                    modifier = Modifier.clickable { state.scriptViewMode = 1 }
                ) {
                    Row(Modifier.padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("🧠 Cognitive Overlay", color = if (state.scriptViewMode == 1) Brand else TextMuted, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.width(4.dp))
                        Box(Modifier.clip(RoundedCornerShape(8.dp)).background(Brand).padding(horizontal = 4.dp, vertical = 1.dp)) {
                            Text("${tokens.size}", color = Color.White, fontSize = 8.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            } else {
                Text("Paste Fountain, plain text, or scene headings", fontSize = 10.sp, color = TextMuted, modifier = Modifier.weight(1f))
            }

            Spacer(Modifier.weight(1f))

            if (state.scriptText.isNotEmpty()) {
                TextButton(onClick = {
                    state.scriptText = ""
                    state.track = null
                    state.selectedPoint = null
                    state.activeSplineTargetScene = null
                }) {
                    Text("Clear", color = TextMid, fontSize = 11.sp)
                }
            }
        }

        // Script content body: Editor or Cognitive Highlight Overlay
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp)
        ) {
            if (state.scriptViewMode == 1 && state.hasResults) {
                CognitiveScriptOverlayView(state)
            } else {
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

@Composable
private fun CognitiveScriptOverlayView(state: AppState) {
    val tokens = state.track?.cognitiveTokens.orEmpty()
    val points = state.track?.points.orEmpty()
    val tokensByScene = remember(tokens) { tokens.groupBy { it.sceneIndex } }

    Column(Modifier.fillMaxSize()) {
        // Legend
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(4.dp)).background(BgSurface).padding(horizontal = 8.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            LegendPill("Setup / Seed", TokenSetupCyan)
            LegendPill("Subversion / Twist", TokenSubversionAmber)
            LegendPill("Cognitive Overload", TokenOverloadPurple)
        }

        Spacer(Modifier.height(8.dp))

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(points, key = { it.sceneIndex }) { pt ->
                val sceneTokens = tokensByScene[pt.sceneIndex].orEmpty()
                val isSelected = pt.sceneIndex == state.selectedPoint?.sceneIndex
                val isSplineTarget = pt.sceneIndex == state.activeSplineTargetScene

                Surface(
                    color = if (isSplineTarget) TokenSetupCyan.copy(alpha = 0.15f) else if (isSelected) Brand.copy(alpha = 0.12f) else BgCard,
                    shape = RoundedCornerShape(6.dp),
                    border = BorderStroke(
                        width = if (isSplineTarget) 1.5.dp else if (isSelected) 1.dp else 0.5.dp,
                        color = if (isSplineTarget) TokenSetupCyan else if (isSelected) Brand else BorderSubtle,
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { state.selectedPoint = pt },
                ) {
                    Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("SCENE ${pt.sceneIndex}: ${pt.sceneTitle}", color = TextHigh, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                            if (isSplineTarget) {
                                Surface(
                                    color = TokenSetupCyan.copy(alpha = 0.2f),
                                    shape = RoundedCornerShape(3.dp),
                                    border = BorderStroke(1.dp, TokenSetupCyan),
                                ) {
                                    Text("ORIGINATING SETUP", color = TokenSetupCyan, fontSize = 7.5.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp))
                                }
                            }
                        }

                        Text(pt.sceneSummary, color = TextMid, fontSize = 10.sp, lineHeight = 14.sp)

                        // Tokens in this scene
                        if (sceneTokens.isNotEmpty()) {
                            HorizontalRule()
                            sceneTokens.forEach { token ->
                                val (tokenColor, badgeLabel) = when (token.type) {
                                    CognitiveTokenType.SETUP_CYAN -> TokenSetupCyan to "SETUP"
                                    CognitiveTokenType.SUBVERSION_AMBER -> TokenSubversionAmber to "TWIST"
                                    CognitiveTokenType.OVERLOAD_PURPLE -> TokenOverloadPurple to "OVERLOAD"
                                }

                                Surface(
                                    color = tokenColor.copy(alpha = 0.10f),
                                    border = BorderStroke(1.dp, tokenColor.copy(alpha = 0.45f)),
                                    shape = RoundedCornerShape(4.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .pointerInput(token) {
                                            awaitPointerEventScope {
                                                while (true) {
                                                    val event = awaitPointerEvent()
                                                    when (event.type) {
                                                        PointerEventType.Enter -> {
                                                            state.hoveredTokenText = token.tokenText
                                                            if (token.originatingSceneIndex != null) {
                                                                state.activeSplineTargetScene = token.originatingSceneIndex
                                                            }
                                                        }
                                                        PointerEventType.Exit -> {
                                                            state.hoveredTokenText = null
                                                            state.activeSplineTargetScene = null
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                        .clickable {
                                            if (token.originatingSceneIndex != null) {
                                                val originPoint = points.find { it.sceneIndex == token.originatingSceneIndex }
                                                if (originPoint != null) {
                                                    state.selectedPoint = originPoint
                                                    state.activeSplineTargetScene = token.originatingSceneIndex
                                                }
                                            }
                                        },
                                ) {
                                    Column(Modifier.padding(6.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Surface(
                                                color = tokenColor.copy(alpha = 0.25f),
                                                shape = RoundedCornerShape(3.dp),
                                            ) {
                                                Text(badgeLabel, color = tokenColor, fontSize = 8.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp))
                                            }
                                            Spacer(Modifier.width(6.dp))
                                            Text("\"${token.tokenText}\"", color = tokenColor, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                            Spacer(Modifier.weight(1f))
                                            if (token.originatingSceneIndex != null) {
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    Text("Links to Scene ${token.originatingSceneIndex}", color = TokenSetupCyan, fontSize = 9.sp, fontWeight = FontWeight.SemiBold)
                                                    Spacer(Modifier.width(2.dp))
                                                    Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, tint = TokenSetupCyan, modifier = Modifier.size(10.dp))
                                                }
                                            }
                                        }
                                        Text(token.explanation, color = TextMid, fontSize = 9.sp, lineHeight = 12.sp)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LegendPill(label: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Box(Modifier.size(7.dp).clip(RoundedCornerShape(3.5.dp)).background(color))
        Text(label, color = color, fontSize = 9.sp, fontWeight = FontWeight.Medium)
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
