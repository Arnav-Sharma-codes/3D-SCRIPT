package com.threescript.shared.api

import com.threescript.shared.model.SceneBlock
import com.threescript.shared.model.NarrativeTrack
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

// ── Short-form creator analysis ─────────────────────────────────────────────

@Serializable
enum class ShortFormPlatform(val displayName: String) {
    TIKTOK("TikTok"),
    INSTAGRAM_REELS("Instagram Reels"),
    YOUTUBE_SHORTS("YouTube Shorts"),
}

@Serializable
data class ShortFormAnalyzeRequest(
    val text: String,
    val platform: ShortFormPlatform = ShortFormPlatform.TIKTOK,
    val provider: String = "gemini",
    val deadZoneThreshold: Double = 1.5,
)

@Serializable
data class TrendSource(val title: String, val url: String)

@Serializable
data class ShortFormReview(
    val platform: ShortFormPlatform,
    val audiencePotential: Int,
    val hookScore: Int,
    val relatabilityScore: Int,
    val retentionScore: Int,
    val trendAlignmentScore: Int,
    val headline: String,
    val diagnosis: String,
    val recommendations: List<String>,
    val trendSources: List<TrendSource> = emptyList(),
    val searchSuggestionHtml: String? = null,
    val benchmarkLabel: String = "AI audience estimate · live web trend context",
)

@Serializable
data class ShortFormAnalyzeResponse(
    val track: NarrativeTrack? = null,
    val review: ShortFormReview? = null,
    val error: String? = null,
)

// ── /api/health ───────────────────────────────────────────────────────────────

@Serializable
data class HealthResponse(
    val status: String = "ok",
    val engineVersion: String,
    val geminiConfigured: Boolean,
)
