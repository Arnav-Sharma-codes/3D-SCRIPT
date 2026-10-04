package com.threescript.shared.api

import com.threescript.shared.model.SceneBlock
import kotlinx.serialization.Serializable

// ── /api/parse ───────────────────────────────────────────────────────────────

@Serializable
data class ParseRequest(
    val text: String,
)

@Serializable
data class ParseResponse(
    val scenes: List<SceneBlock>,
    val error: String? = null,
)

// ── /api/analyze ─────────────────────────────────────────────────────────────

@Serializable
data class AnalyzeRequest(
    val scenes: List<SceneBlock>,
    val provider: String = "gemini",        // "gemini" | "mock"
    val deadZoneThreshold: Double = 1.5,
)

@Serializable
data class AnalyzeResponse(
    val track: com.threescript.shared.model.NarrativeTrack? = null,
    val error: String? = null,
)

// ── /api/health ───────────────────────────────────────────────────────────────

@Serializable
data class HealthResponse(
    val status: String = "ok",
    val engineVersion: String,
    val geminiConfigured: Boolean,
)
