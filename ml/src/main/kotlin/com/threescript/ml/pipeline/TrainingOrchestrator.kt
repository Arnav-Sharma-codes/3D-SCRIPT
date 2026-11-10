package com.threescript.ml.pipeline

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader

/**
 * TrainingOrchestrator — spawns and manages the Python training subprocess.
 *
 * The Python process (`python3 -m threescript.trainer`) handles all gradient
 * computation via PyTorch. It streams JSON progress lines to stdout which this
 * class parses and emits as [TrainingProgress] events.
 *
 * Usage:
 *   val orchestrator = TrainingOrchestrator(dbPath, modelOutputDir)
 *   orchestrator.train(epochs = 50, learningRate = 1e-4)
 *       .collect { progress -> updateUI(progress) }
 */
class TrainingOrchestrator(
    private val dbPath: String,
    private val modelOutputDir: String,
    private val pythonExecutable: String = "python3",
) {
    private var trainingProcess: Process? = null

    /**
     * Start training and return a cold Flow of [TrainingProgress] events.
     *
     * The flow completes when training finishes or throws on error/cancellation.
     * Cancelling the flow kills the subprocess cleanly.
     */
    fun train(
        epochs: Int = 50,
        learningRate: Double = 1e-4,
        batchSize: Int = 8,
        hiddenDim: Int = 128,
        numLayers: Int = 3,
        numHeads: Int = 4,
    ): Flow<TrainingProgress> = flow {
        val outputDir = File(modelOutputDir).also { it.mkdirs() }
        val modelPath = File(outputDir, "narrative_transformer_${System.currentTimeMillis()}.onnx").absolutePath

        val cmd = listOf(
            pythonExecutable, "-m", "threescript.trainer",
            "--db", dbPath,
            "--output", modelPath,
            "--epochs", epochs.toString(),
            "--lr", learningRate.toString(),
            "--batch-size", batchSize.toString(),
            "--hidden-dim", hiddenDim.toString(),
            "--num-layers", numLayers.toString(),
            "--num-heads", numHeads.toString(),
        )

        val process = ProcessBuilder(cmd)
            .redirectErrorStream(true)
            .start()
            .also { trainingProcess = it }

        try {
            val reader = BufferedReader(InputStreamReader(process.inputStream))
            var line: String?

            while (reader.readLine().also { line = it } != null) {
                val progress = parseProgressLine(line!!) ?: continue
                emit(progress)

                // If the training process signals completion, break
                if (progress.isComplete) break
            }

            val exitCode = process.waitFor()
            if (exitCode != 0) {
                throw RuntimeException("Training process exited with code $exitCode")
            }

            // Emit final completion event with model path
            emit(TrainingProgress(
                epoch = epochs,
                totalEpochs = epochs,
                trainLoss = 0.0,
                valLoss = 0.0,
                etaSeconds = 0,
                isComplete = true,
                modelPath = modelPath,
            ))
        } finally {
            trainingProcess?.destroy()
            trainingProcess = null
        }
    }.flowOn(Dispatchers.IO)

    /** Stop the training subprocess if running. */
    fun cancel() {
        trainingProcess?.destroy()
        trainingProcess = null
    }

    /**
     * Parse a newline-delimited JSON progress line from the Python trainer.
     *
     * Expected format:
     *   {"epoch": 1, "train_loss": 0.42, "val_loss": 0.51, "eta_seconds": 340}
     */
    private fun parseProgressLine(line: String): TrainingProgress? {
        // Lightweight JSON parsing without a full library dependency
        return try {
            if (!line.trimStart().startsWith("{")) return null
            val epoch = extractInt(line, "epoch") ?: return null
            val totalEpochs = extractInt(line, "total_epochs") ?: epoch
            val trainLoss = extractDouble(line, "train_loss") ?: return null
            val valLoss = extractDouble(line, "val_loss") ?: trainLoss
            val eta = extractInt(line, "eta_seconds") ?: 0
            val complete = line.contains("\"complete\": true") || line.contains("\"complete\":true")
            TrainingProgress(epoch, totalEpochs, trainLoss, valLoss, eta, complete)
        } catch (e: Exception) {
            null
        }
    }

    private fun extractInt(json: String, key: String): Int? =
        Regex(""""$key"\s*:\s*(\d+)""").find(json)?.groupValues?.get(1)?.toIntOrNull()

    private fun extractDouble(json: String, key: String): Double? =
        Regex(""""$key"\s*:\s*([\d.]+)""").find(json)?.groupValues?.get(1)?.toDoubleOrNull()
}

/**
 * A single progress snapshot emitted by the training subprocess.
 */
data class TrainingProgress(
    val epoch: Int,
    val totalEpochs: Int,
    val trainLoss: Double,
    val valLoss: Double,
    val etaSeconds: Int,
    val isComplete: Boolean = false,
    val modelPath: String? = null,
) {
    val progressFraction: Float get() = (epoch.toFloat() / totalEpochs.coerceAtLeast(1)).coerceIn(0f, 1f)
    val etaFormatted: String get() = when {
        etaSeconds < 60 -> "${etaSeconds}s"
        etaSeconds < 3600 -> "${etaSeconds / 60}m ${etaSeconds % 60}s"
        else -> "${etaSeconds / 3600}h ${(etaSeconds % 3600) / 60}m"
    }
}
