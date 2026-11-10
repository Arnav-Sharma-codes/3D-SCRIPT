package com.threescript.app.ui.ml

import androidx.compose.runtime.*
import com.threescript.ml.data.CorpusStats
import com.threescript.ml.data.ModelVersion
import com.threescript.ml.data.ScriptEntry
import com.threescript.ml.fusion.FusionWeights
import com.threescript.ml.pipeline.TrainingProgress

/**
 * MLTrainingState — observable state for the ML Training Studio window.
 *
 * All mutableStateOf fields are observed by Compose. Mutations happen only
 * through the ViewModel/Orchestrator layer, never directly from the UI.
 */
class MLTrainingState {

    // ── Corpus ────────────────────────────────────────────────────────────────
    var scripts: List<ScriptEntry> by mutableStateOf(emptyList())
    var corpusStats: CorpusStats? by mutableStateOf(null)

    // ── Script Import ─────────────────────────────────────────────────────────
    var importInProgress: Boolean by mutableStateOf(false)
    var importError: String? by mutableStateOf(null)
    var selectedScriptId: Long? by mutableStateOf(null)

    // ── Training ──────────────────────────────────────────────────────────────
    var trainingPhase: TrainingPhase by mutableStateOf(TrainingPhase.IDLE)
    var currentProgress: TrainingProgress? by mutableStateOf(null)
    var lossHistory: List<LossPoint> by mutableStateOf(emptyList())
    var trainingError: String? by mutableStateOf(null)

    // ── Hyperparameters (editable before training) ────────────────────────────
    var epochs: Int by mutableStateOf(50)
    var learningRate: Double by mutableStateOf(1e-4)
    var batchSize: Int by mutableStateOf(8)
    var hiddenDim: Int by mutableStateOf(128)
    var numLayers: Int by mutableStateOf(3)
    var numHeads: Int by mutableStateOf(4)

    // ── Model Versions ────────────────────────────────────────────────────────
    var modelVersions: List<ModelVersion> by mutableStateOf(emptyList())
    var activeModelVersion: ModelVersion? by mutableStateOf(null)

    // ── Score Fusion Weights ──────────────────────────────────────────────────
    var fusionWeights: FusionWeights by mutableStateOf(FusionWeights())

    // ── Derived state ─────────────────────────────────────────────────────────
    val hasCorpus: Boolean get() = scripts.isNotEmpty()
    val canStartTraining: Boolean get() =
        trainingPhase == TrainingPhase.IDLE && scripts.size >= 1
    val progressFraction: Float get() = currentProgress?.progressFraction ?: 0f
    val etaFormatted: String get() = currentProgress?.etaFormatted ?: "—"

    fun appendLossPoint(progress: TrainingProgress) {
        lossHistory = lossHistory + LossPoint(
            epoch = progress.epoch,
            trainLoss = progress.trainLoss.toFloat(),
            valLoss = progress.valLoss.toFloat(),
        )
    }
}

enum class TrainingPhase {
    IDLE,       // No training running
    RUNNING,    // Training subprocess active
    PAUSED,     // Not yet implemented — planned for Phase 4
    COMPLETE,   // Last run finished successfully
    ERROR,      // Last run failed
}

/** One epoch's loss values for the chart. */
data class LossPoint(
    val epoch: Int,
    val trainLoss: Float,
    val valLoss: Float,
)
