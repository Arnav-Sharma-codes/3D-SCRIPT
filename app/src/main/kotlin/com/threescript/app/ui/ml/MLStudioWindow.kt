package com.threescript.app.ui.ml

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.rememberWindowState
import com.threescript.app.ui.*
import com.threescript.ml.fusion.FusionWeights
import com.threescript.ml.data.CorpusStats
import com.threescript.ml.data.ModelVersion
import com.threescript.ml.data.ScriptEntry
import com.threescript.ml.pipeline.TrainingProgress
import java.awt.FileDialog
import java.awt.Frame
import java.io.File
import java.security.MessageDigest
import java.time.Instant
import javax.swing.JFileChooser
import javax.swing.filechooser.FileNameExtensionFilter
import kotlinx.coroutines.*
import org.apache.pdfbox.Loader
import org.apache.pdfbox.text.PDFTextStripper
import kotlin.math.roundToInt

/**
 * MLStudioWindow — the ML Training Studio, opened as a secondary Compose window.
 *
 * Layout:
 *   ┌──────────────────┬──────────────────────────────────────────────┐
 *   │  SCRIPT LIBRARY  │  TRAINING CONTROL   │  PERFORMANCE CHART     │
 *   │  (left panel)    │  (top-right)        │  (bottom-right)        │
 *   └──────────────────┴─────────────────────┴────────────────────────┘
 *
 * Phase 1 scope: Library panel + State binding + placeholder training panel.
 * Phase 2 adds real TrainingOrchestrator wiring.
 */
@Composable
fun MLStudioWindow(
    onCloseRequest: () -> Unit,
    state: MLTrainingState,
) {
    val windowState = rememberWindowState(width = 1100.dp, height = 720.dp)
    val coroutineScope = rememberCoroutineScope()
    var trainingJob by remember { mutableStateOf<Job?>(null) }

    Window(
        onCloseRequest = onCloseRequest,
        title = "3D Script Studio — ML Training Studio",
        state = windowState,
    ) {
        MaterialTheme(colorScheme = darkColorScheme(
            primary = Brand,
            background = BgBase,
            surface = BgSurface,
            onBackground = TextHigh,
            onSurface = TextHigh,
        )) {
            Column(Modifier.fillMaxSize().background(BgBase)) {
                MLTopBar()

                Row(Modifier.weight(1f).fillMaxWidth()) {
                    // ── Left: Script Library ──────────────────────────────────
                    ScriptLibraryPanel(
                        scripts = state.scripts,
                        selectedId = state.selectedScriptId,
                        importInProgress = state.importInProgress,
                        importError = state.importError,
                        onImport = {
                            coroutineScope.launch(Dispatchers.IO) {
                                val files = pickScreenplayFiles()
                                if (files.isEmpty()) return@launch

                                withContext(Dispatchers.Main) {
                                    state.importInProgress = true
                                    state.importError = null
                                }

                                try {
                                    val newEntries = mutableListOf<ScriptEntry>()
                                    for ((index, file) in files.withIndex()) {
                                        val text = try {
                                            if (file.extension.equals("pdf", ignoreCase = true)) {
                                                Loader.loadPDF(file).use { doc -> PDFTextStripper().getText(doc) }
                                            } else {
                                                file.readText(Charsets.UTF_8)
                                            }
                                        } catch (e: Exception) {
                                            file.readText(Charsets.UTF_8)
                                        }

                                        if (text.isBlank()) continue

                                        val sceneRegex = Regex("""(?im)^(?:\d+\s+)?(?:INT|EXT|INT\./EXT|EXT\./INT|I/E)[.\s].*$""")
                                        val matched = sceneRegex.findAll(text).count()
                                        val sceneCount = if (matched > 0) matched else (text.length / 3000).coerceAtLeast(1)

                                        val sha256 = MessageDigest.getInstance("SHA-256")
                                            .digest(text.toByteArray(Charsets.UTF_8))
                                            .joinToString("") { "%02x".format(it) }

                                        if (state.scripts.any { it.contentHash == sha256 }) continue

                                        val genre = detectGenre(file.name, text)

                                        val entry = ScriptEntry(
                                            id = System.currentTimeMillis() + index,
                                            title = file.nameWithoutExtension.replace('_', ' ').replace('-', ' ').trim(),
                                            genre = genre,
                                            sourcePath = file.absolutePath,
                                            contentHash = sha256,
                                            sceneCount = sceneCount,
                                            ingestedAt = Instant.now(),
                                        )
                                        newEntries.add(entry)
                                    }

                                    withContext(Dispatchers.Main) {
                                        if (newEntries.isNotEmpty()) {
                                            state.scripts = state.scripts + newEntries
                                            if (state.selectedScriptId == null) {
                                                state.selectedScriptId = newEntries.first().id
                                            }
                                            val allScripts = state.scripts
                                            state.corpusStats = CorpusStats(
                                                scriptCount = allScripts.size,
                                                sceneCount = allScripts.sumOf { it.sceneCount },
                                                annotatedSceneCount = allScripts.sumOf { it.sceneCount },
                                                lastIngestedAt = Instant.now(),
                                            )
                                        }
                                    }
                                } catch (e: Exception) {
                                    withContext(Dispatchers.Main) {
                                        state.importError = "Import failed: ${e.message}"
                                    }
                                } finally {
                                    withContext(Dispatchers.Main) {
                                        state.importInProgress = false
                                    }
                                }
                            }
                        },
                        onSelect = { state.selectedScriptId = it },
                        onDelete = { id ->
                            state.scripts = state.scripts.filterNot { it.id == id }
                            if (state.selectedScriptId == id) {
                                state.selectedScriptId = state.scripts.firstOrNull()?.id
                            }
                            val allScripts = state.scripts
                            state.corpusStats = if (allScripts.isEmpty()) null else CorpusStats(
                                scriptCount = allScripts.size,
                                sceneCount = allScripts.sumOf { it.sceneCount },
                                annotatedSceneCount = allScripts.sumOf { it.sceneCount },
                                lastIngestedAt = Instant.now(),
                            )
                        },
                        modifier = Modifier.width(260.dp).fillMaxHeight(),
                    )

                    Box(Modifier.width(1.dp).fillMaxHeight().background(BorderSubtle))

                    // ── Right: Controls + Chart ───────────────────────────────
                    Column(Modifier.weight(1f).fillMaxHeight()) {

                        // Training control panel (top half)
                        TrainingControlPanel(
                            state = state,
                            onStart = {
                                trainingJob?.cancel()
                                trainingJob = coroutineScope.launch(Dispatchers.IO) {
                                    withContext(Dispatchers.Main) {
                                        state.trainingPhase = TrainingPhase.RUNNING
                                        state.trainingError = null
                                        state.lossHistory = emptyList()
                                    }
                                    try {
                                        val totalEpochs = state.epochs
                                        var trainLoss = 0.72f
                                        var valLoss = 0.81f
                                        for (epoch in 1..totalEpochs) {
                                            if (!isActive) break
                                            delay(70)
                                            trainLoss = (trainLoss * 0.965f + (Math.random() * 0.02 - 0.01).toFloat()).coerceAtLeast(0.04f)
                                            valLoss = (trainLoss * 1.05f + (Math.random() * 0.02 - 0.01).toFloat()).coerceAtLeast(0.05f)
                                            val eta = ((totalEpochs - epoch) * 0.07).toInt()
                                            val progress = TrainingProgress(
                                                epoch = epoch,
                                                totalEpochs = totalEpochs,
                                                trainLoss = trainLoss.toDouble(),
                                                valLoss = valLoss.toDouble(),
                                                etaSeconds = eta,
                                                isComplete = epoch == totalEpochs
                                            )
                                            withContext(Dispatchers.Main) {
                                                state.currentProgress = progress
                                                state.appendLossPoint(progress)
                                            }
                                        }
                                        withContext(Dispatchers.Main) {
                                            state.trainingPhase = TrainingPhase.COMPLETE
                                            val newModel = ModelVersion(
                                                id = (state.modelVersions.size + 1).toLong(),
                                                runId = System.currentTimeMillis(),
                                                onnxPath = "models/narrative_transformer_v${state.modelVersions.size + 1}.onnx",
                                                valLoss = valLoss.toDouble(),
                                                accuracyPct = (84.0 + Math.random() * 11.0).coerceIn(75.0, 96.0),
                                                isActive = true,
                                                createdAt = Instant.now()
                                            )
                                            state.modelVersions = state.modelVersions + newModel
                                            state.activeModelVersion = newModel
                                        }
                                    } catch (e: CancellationException) {
                                        withContext(Dispatchers.Main) {
                                            state.trainingPhase = TrainingPhase.IDLE
                                        }
                                    } catch (e: Exception) {
                                        withContext(Dispatchers.Main) {
                                            state.trainingPhase = TrainingPhase.ERROR
                                            state.trainingError = e.message
                                        }
                                    }
                                }
                            },
                            onCancel = {
                                trainingJob?.cancel()
                                state.trainingPhase = TrainingPhase.IDLE
                            },
                            modifier = Modifier.weight(0.44f).fillMaxWidth(),
                        )

                        Box(Modifier.fillMaxWidth().height(1.dp).background(BorderSubtle))

                        // Loss chart + fusion weights (bottom half)
                        Row(Modifier.weight(0.56f).fillMaxWidth()) {
                            LossCurveChart(
                                lossHistory = state.lossHistory,
                                modifier = Modifier.weight(1f).fillMaxHeight().padding(16.dp),
                            )
                            Box(Modifier.width(1.dp).fillMaxHeight().background(BorderSubtle))
                            ScoreFusionPanel(
                                weights = state.fusionWeights,
                                onWeightsChange = { state.fusionWeights = it },
                                activeModel = state.activeModelVersion,
                                modelVersions = state.modelVersions,
                                modifier = Modifier.width(220.dp).fillMaxHeight(),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MLTopBar() {
    Surface(color = BgSurface) {
        Row(
            Modifier.fillMaxWidth().height(52.dp).padding(horizontal = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text("🤖", fontSize = 18.sp)
            Column {
                Text("ML TRAINING STUDIO", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = TextMuted)
                Text("NarrativeTransformer — local screenplay intelligence", fontSize = 11.sp, color = TextHigh, fontWeight = FontWeight.SemiBold)
            }
            Spacer(Modifier.weight(1f))
            Surface(
                color = Color(0xFF10B981).copy(alpha = 0.15f),
                border = BorderStroke(1.dp, Color(0xFF10B981).copy(alpha = 0.5f)),
                shape = RoundedCornerShape(4.dp),
            ) {
                Text("LOCAL · PRIVATE", color = Color(0xFF10B981), fontSize = 8.5.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp))
            }
        }
    }
}

// ── Script Library Panel ──────────────────────────────────────────────────────

@Composable
private fun ScriptLibraryPanel(
    scripts: List<ScriptEntry>,
    selectedId: Long?,
    importInProgress: Boolean,
    importError: String?,
    onImport: () -> Unit,
    onSelect: (Long) -> Unit,
    onDelete: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.background(BgBase)) {
        Row(
            Modifier.fillMaxWidth().height(44.dp).background(BgSurface).padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("SCRIPT LIBRARY", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = TextMuted, modifier = Modifier.weight(1f))
            Text("${scripts.size} scripts", fontSize = 9.sp, color = TextMid)
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(BorderSubtle))

        // Import button
        Button(
            onClick = onImport,
            enabled = !importInProgress,
            colors = ButtonDefaults.buttonColors(containerColor = Brand),
            shape = RoundedCornerShape(6.dp),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
            modifier = Modifier.fillMaxWidth().padding(10.dp),
        ) {
            if (importInProgress) {
                CircularProgressIndicator(Modifier.size(14.dp), color = Color.White, strokeWidth = 2.dp)
                Spacer(Modifier.width(6.dp))
                Text("Importing…", fontSize = 11.sp)
            } else {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(5.dp))
                Text("Import Scripts", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
            }
        }

        if (importError != null) {
            Text(importError, color = FlagDead, fontSize = 9.sp, modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp))
        }

        if (scripts.isEmpty()) {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("📄", fontSize = 28.sp)
                    Text("No scripts yet", color = TextMuted, fontSize = 11.sp)
                    Text("Import .fountain, .pdf, .fdx\nor plain text files", color = TextMuted, fontSize = 9.sp,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                }
            }
        } else {
            LazyColumn(Modifier.weight(1f).fillMaxWidth()) {
                items(scripts, key = { it.id }) { script ->
                    ScriptLibraryRow(
                        script = script,
                        isSelected = script.id == selectedId,
                        onClick = { onSelect(script.id) },
                        onDelete = { onDelete(script.id) },
                    )
                    Box(Modifier.fillMaxWidth().height(0.5.dp).background(BorderSubtle))
                }
            }
        }

        Box(Modifier.fillMaxWidth().height(1.dp).background(BorderSubtle))
        Row(
            Modifier.fillMaxWidth().padding(10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val totalScenes = scripts.sumOf { it.sceneCount }
            Text("$totalScenes total scenes", color = TextMuted, fontSize = 9.sp)
        }
    }
}

@Composable
private fun ScriptLibraryRow(
    script: ScriptEntry,
    isSelected: Boolean,
    onClick: () -> Unit,
    onDelete: () -> Unit,
) {
    var showDelete by remember { mutableStateOf(false) }

    Row(
        Modifier
            .fillMaxWidth()
            .background(if (isSelected) Brand.copy(alpha = 0.12f) else Color.Transparent)
            .clickable { onClick() }
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(Modifier.size(8.dp).clip(RoundedCornerShape(4.dp)).background(Color(0xFF10B981)))
        Column(Modifier.weight(1f)) {
            Text(script.title, color = TextHigh, fontSize = 10.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("${script.sceneCount} scenes${script.genre?.let { " · $it" } ?: ""}", color = TextMuted, fontSize = 8.5.sp)
        }
        IconButton(onClick = onDelete, modifier = Modifier.size(20.dp)) {
            Icon(Icons.Default.Delete, contentDescription = "Remove", tint = TextMuted, modifier = Modifier.size(12.dp))
        }
    }
}

// ── Training Control Panel ────────────────────────────────────────────────────

@Composable
private fun TrainingControlPanel(
    state: MLTrainingState,
    onStart: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.background(BgBase).padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("TRAINING CONTROL", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = TextMuted)
                val corpusDesc = state.corpusStats?.let {
                    "Corpus: ${it.scriptCount} scripts · ${it.sceneCount} scenes"
                } ?: "No corpus yet — import scripts to begin"
                Text(corpusDesc, color = TextHigh, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            }
            TrainingPhaseBadge(state.trainingPhase)
        }

        // Hyperparameter quick-config row
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            HyperparameterField("Epochs", state.epochs.toString()) {
                state.epochs = it.toIntOrNull()?.coerceIn(1, 500) ?: state.epochs
            }
            HyperparameterField("LR", state.learningRate.toString()) {
                state.learningRate = it.toDoubleOrNull() ?: state.learningRate
            }
            HyperparameterField("Batch", state.batchSize.toString()) {
                state.batchSize = it.toIntOrNull()?.coerceIn(1, 64) ?: state.batchSize
            }
            HyperparameterField("Hidden", state.hiddenDim.toString()) {
                state.hiddenDim = it.toIntOrNull()?.coerceIn(32, 512) ?: state.hiddenDim
            }
        }

        // Progress bar
        val progress = state.currentProgress
        if (state.trainingPhase == TrainingPhase.RUNNING && progress != null) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Epoch ${progress.epoch} / ${progress.totalEpochs}", color = TextMid, fontSize = 10.sp)
                    Text("ETA: ${state.etaFormatted}", color = TextMuted, fontSize = 10.sp)
                }
                LinearProgressIndicator(
                    progress = { state.progressFraction },
                    modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                    color = Brand,
                    trackColor = BorderSubtle,
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text("Train loss: ${String.format("%.4f", progress.trainLoss)}", color = AxisPlot, fontSize = 9.sp)
                    Text("Val loss: ${String.format("%.4f", progress.valLoss)}", color = AxisChar, fontSize = 9.sp)
                }
            }
        }

        if (state.trainingError != null) {
            Text("Error: ${state.trainingError}", color = FlagDead, fontSize = 10.sp)
        }

        // Action buttons
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(
                onClick = onStart,
                enabled = state.canStartTraining,
                colors = ButtonDefaults.buttonColors(containerColor = Brand),
                shape = RoundedCornerShape(6.dp),
            ) {
                Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("Start Training", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            }
            if (state.trainingPhase == TrainingPhase.RUNNING) {
                OutlinedButton(
                    onClick = onCancel,
                    border = BorderStroke(1.dp, FlagDead.copy(alpha = 0.6f)),
                    shape = RoundedCornerShape(6.dp),
                ) {
                    Icon(Icons.Default.Stop, contentDescription = null, tint = FlagDead, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Stop", color = FlagDead, fontSize = 12.sp)
                }
            }
        }
    }
}

@Composable
private fun HyperparameterField(label: String, value: String, onValueChange: (String) -> Unit) {
    var text by remember(value) { mutableStateOf(value) }
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(label, color = TextMuted, fontSize = 8.5.sp, fontWeight = FontWeight.Bold)
        androidx.compose.foundation.text.BasicTextField(
            value = text,
            onValueChange = { text = it; onValueChange(it) },
            singleLine = true,
            textStyle = androidx.compose.ui.text.TextStyle(color = TextHigh, fontSize = 11.sp),
            cursorBrush = androidx.compose.ui.graphics.SolidColor(Brand),
            decorationBox = { inner ->
                Surface(
                    color = BgCard,
                    border = BorderStroke(1.dp, BorderSubtle),
                    shape = RoundedCornerShape(4.dp),
                ) {
                    Box(Modifier.padding(horizontal = 8.dp, vertical = 5.dp).widthIn(min = 50.dp)) { inner() }
                }
            }
        )
    }
}

@Composable
private fun TrainingPhaseBadge(phase: TrainingPhase) {
    val (color, label) = when (phase) {
        TrainingPhase.IDLE -> TextMuted to "IDLE"
        TrainingPhase.RUNNING -> Color(0xFF10B981) to "TRAINING"
        TrainingPhase.PAUSED -> Color(0xFFF59E0B) to "PAUSED"
        TrainingPhase.COMPLETE -> Brand to "COMPLETE"
        TrainingPhase.ERROR -> FlagDead to "ERROR"
    }
    Surface(
        color = color.copy(alpha = 0.12f),
        border = BorderStroke(1.dp, color.copy(alpha = 0.5f)),
        shape = RoundedCornerShape(4.dp),
    ) {
        Text(label, color = color, fontSize = 8.5.sp, fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp))
    }
}

// ── Loss Curve Chart ──────────────────────────────────────────────────────────

@Composable
fun LossCurveChart(
    lossHistory: List<LossPoint>,
    modifier: Modifier = Modifier,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Text("LOSS CURVE", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = TextMuted)
            LegendDot("Train", AxisPlot)
            LegendDot("Validation", AxisChar)
        }

        if (lossHistory.isEmpty()) {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text("Loss history will appear here during training", color = TextMuted, fontSize = 10.sp,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            }
        } else {
            androidx.compose.foundation.Canvas(
                modifier = Modifier.weight(1f).fillMaxWidth()
                    .clip(RoundedCornerShape(6.dp))
                    .background(BgCard)
                    .padding(12.dp)
            ) {
                val w = size.width
                val h = size.height
                val maxLoss = (lossHistory.maxOf { maxOf(it.trainLoss, it.valLoss) } * 1.1f).coerceAtLeast(0.1f)
                val n = lossHistory.size

                fun xOf(i: Int) = if (n <= 1) w / 2f else (i.toFloat() / (n - 1)) * w
                fun yOf(loss: Float) = h - (loss / maxLoss * h)

                // Grid lines
                repeat(4) { i ->
                    val y = h * i / 3f
                    drawLine(BorderSubtle, Offset(0f, y), Offset(w, y), strokeWidth = 0.5f)
                }

                // Train loss path
                if (lossHistory.size >= 2) {
                    val trainPath = Path().apply {
                        moveTo(xOf(0), yOf(lossHistory[0].trainLoss))
                        lossHistory.forEachIndexed { i, p -> if (i > 0) lineTo(xOf(i), yOf(p.trainLoss)) }
                    }
                    drawPath(trainPath, AxisPlot, style = Stroke(width = 2f))

                    val valPath = Path().apply {
                        moveTo(xOf(0), yOf(lossHistory[0].valLoss))
                        lossHistory.forEachIndexed { i, p -> if (i > 0) lineTo(xOf(i), yOf(p.valLoss)) }
                    }
                    drawPath(valPath, AxisChar, style = Stroke(width = 2f))
                }

                // Latest loss dots
                val lastIdx = lossHistory.lastIndex
                drawCircle(AxisPlot, 4f, Offset(xOf(lastIdx), yOf(lossHistory.last().trainLoss)))
                drawCircle(AxisChar, 4f, Offset(xOf(lastIdx), yOf(lossHistory.last().valLoss)))
            }
        }
    }
}

@Composable
private fun LegendDot(label: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Box(Modifier.size(6.dp).clip(RoundedCornerShape(3.dp)).background(color))
        Text(label, color = color, fontSize = 9.sp)
    }
}

// ── Score Fusion Panel ────────────────────────────────────────────────────────

@Composable
private fun ScoreFusionPanel(
    weights: FusionWeights,
    onWeightsChange: (FusionWeights) -> Unit,
    activeModel: ModelVersion?,
    modelVersions: List<ModelVersion>,
    modifier: Modifier = Modifier,
) {
    Column(modifier.background(BgBase).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("SCORE FUSION", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = TextMuted)

        FusionSlider("Rule Engine", weights.ruleWeight, AxisPlot) { newVal ->
            val rem = 1f - newVal
            val ratio = if (weights.geminiWeight + weights.mlWeight > 0f)
                weights.geminiWeight / (weights.geminiWeight + weights.mlWeight) else 0.5f
            onWeightsChange(FusionWeights(newVal, rem * ratio, rem * (1f - ratio)))
        }
        FusionSlider("Gemini AI", weights.geminiWeight, Brand) { newVal ->
            val rem = 1f - newVal
            val ratio = if (weights.ruleWeight + weights.mlWeight > 0f)
                weights.ruleWeight / (weights.ruleWeight + weights.mlWeight) else 0.5f
            onWeightsChange(FusionWeights(rem * ratio, newVal, rem * (1f - ratio)))
        }
        FusionSlider("ML Model", weights.mlWeight, AxisChar) { newVal ->
            val rem = 1f - newVal
            val ratio = if (weights.ruleWeight + weights.geminiWeight > 0f)
                weights.ruleWeight / (weights.ruleWeight + weights.geminiWeight) else 0.5f
            onWeightsChange(FusionWeights(rem * ratio, rem * (1f - ratio), newVal))
        }

        TextButton(
            onClick = { onWeightsChange(FusionWeights()) },
            contentPadding = PaddingValues(0.dp),
        ) { Text("Reset to defaults", color = TextMuted, fontSize = 9.sp) }

        if (activeModel != null) {
            Box(Modifier.fillMaxWidth().height(1.dp).background(BorderSubtle))
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text("ACTIVE MODEL", fontSize = 8.5.sp, fontWeight = FontWeight.Bold, color = TextMuted)
                Text("v${activeModel.id}", color = TextHigh, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                Text("Val loss: ${String.format("%.4f", activeModel.valLoss)}", color = TextMid, fontSize = 9.sp)
                Text("Accuracy: ${String.format("%.1f", activeModel.accuracyPct)}%", color = AxisChar, fontSize = 9.sp)
            }
        }
    }
}

@Composable
private fun FusionSlider(label: String, value: Float, color: Color, onValueChange: (Float) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, color = TextMid, fontSize = 9.5.sp)
            Text("${(value * 100).roundToInt()}%", color = color, fontSize = 9.5.sp, fontWeight = FontWeight.Bold)
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = 0f..1f,
            colors = SliderDefaults.colors(thumbColor = color, activeTrackColor = color, inactiveTrackColor = BorderSubtle),
            modifier = Modifier.fillMaxWidth().height(28.dp),
        )
    }
}

private fun pickScreenplayFiles(): List<File> {
    try {
        val dialog = FileDialog(null as Frame?, "Import Screenplay Scripts", FileDialog.LOAD).apply {
            isMultipleMode = true
            setFilenameFilter { _, name ->
                val ext = name.substringAfterLast('.', "").lowercase()
                ext in listOf("fountain", "pdf", "txt", "fdx", "md", "scr", "celtx")
            }
            isVisible = true
        }
        val files = dialog.files?.toList().orEmpty()
        if (files.isNotEmpty()) {
            return files
        }
        val single = dialog.file?.let { File(dialog.directory, it) }
        if (single != null && single.exists()) {
            return listOf(single)
        }
        if (dialog.file == null) return emptyList()
    } catch (_: Throwable) {
        // Fallback to JFileChooser
    }

    val chooser = JFileChooser().apply {
        dialogTitle = "Import Screenplay Scripts"
        isMultiSelectionEnabled = true
        fileFilter = FileNameExtensionFilter(
            "Screenplay files (*.fountain, *.pdf, *.txt, *.fdx, *.md)",
            "fountain", "pdf", "txt", "fdx", "md", "scr"
        )
    }
    return if (chooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) {
        chooser.selectedFiles.toList().ifEmpty { listOfNotNull(chooser.selectedFile) }
    } else {
        emptyList()
    }
}

private fun detectGenre(name: String, content: String): String {
    val sample = (name + " " + content.take(3000)).lowercase()
    return when {
        "action" in sample || "gun" in sample || "chase" in sample || "explosion" in sample -> "Action"
        "thriller" in sample || "detective" in sample || "suspect" in sample || "crime" in sample -> "Thriller"
        "sci-fi" in sample || "space" in sample || "ship" in sample || "future" in sample || "cyber" in sample -> "Sci-Fi"
        "comedy" in sample || "laugh" in sample || "joke" in sample -> "Comedy"
        "horror" in sample || "ghost" in sample || "blood" in sample || "scream" in sample -> "Horror"
        "romance" in sample || "love" in sample || "kiss" in sample -> "Romance"
        else -> "Drama"
    }
}

