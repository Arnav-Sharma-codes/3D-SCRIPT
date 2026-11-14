package com.threescript.engine.shortform

import com.threescript.engine.ai.GeminiClient
import com.threescript.engine.ai.RawDimensionScore
import com.threescript.engine.ai.ShortFormGeminiResult
import com.threescript.engine.metrics.NarrativeMetrics
import com.threescript.shared.api.ShortFormPlatform
import com.threescript.shared.api.ShortFormReview
import com.threescript.shared.model.NarrativeTrack
import com.threescript.shared.model.SceneBlock
import com.threescript.shared.model.VectorPoint
import kotlin.math.roundToInt
import kotlin.math.ceil

object ShortFormAnalyzer {

    suspend fun analyze(
        text: String,
        platform: ShortFormPlatform,
        provider: String,
        apiKey: String?,
        model: String,
        deadZoneThreshold: Double,
    ): Pair<NarrativeTrack, ShortFormReview> {
        val beats = splitIntoBeats(text)
        val result = if (provider == "gemini" && !apiKey.isNullOrBlank()) {
            runCatching { GeminiClient(apiKey, model).analyzeShortForm(beats, text, platform) }
                .getOrElse { offlineResult(beats, text, platform) }
        } else {
            offlineResult(beats, text, platform)
        }
        val points = beats.mapIndexed { index, beat ->
            val score = result.scores.firstOrNull { it.sceneIndex == beat.index }
                ?: offlineScore(beat, index, beats.size)
            VectorPoint(
                sceneIndex = beat.index,
                sceneTitle = score.title.ifBlank { beat.title },
                sceneSummary = score.summary,
                xRaw = score.x,
                yRaw = score.y,
                zRaw = score.z,
                x = score.x,
                y = score.y,
                z = score.z,
            )
        }
        return NarrativeMetrics.buildTrack(points, deadZoneThreshold, beats) to result.review
    }

    /** Breaks informal creator copy into up to eight graphable viewing beats. */
    fun splitIntoBeats(text: String): List<SceneBlock> {
        val cleaned = text.trim()
        if (cleaned.isEmpty()) return emptyList()
        val lineBeats = cleaned.lines().map(String::trim).filter { it.length >= 3 }
        val fragments = if (lineBeats.size in 2..12) lineBeats else {
            cleaned.split(Regex("(?<=[.!?])\\s+|\\n{2,}")).map(String::trim).filter(String::isNotBlank)
        }
        val grouped = when {
            fragments.size <= 1 -> listOf(cleaned)
            fragments.size <= 8 -> fragments
            else -> fragments.chunked(ceil(fragments.size / 8.0).toInt().coerceAtLeast(1))
                .map { it.joinToString(" ") }
        }
        return grouped.mapIndexed { index, fragment ->
            val title = when (index) {
                0 -> "Hook"
                grouped.lastIndex -> "Payoff / CTA"
                else -> "Beat ${index + 1}"
            }
            SceneBlock(index + 1, title, fragment)
        }
    }

    private fun offlineResult(beats: List<SceneBlock>, text: String, platform: ShortFormPlatform): ShortFormGeminiResult {
        val scores = beats.mapIndexed { index, beat -> offlineScore(beat, index, beats.size) }
        val wordCount = Regex("[A-Za-z0-9']+").findAll(text).count()
        val all = text.lowercase()
        val hook = scores.firstOrNull()?.x?.times(10)?.roundToInt()?.coerceIn(0, 100) ?: 40
        val relevance = scores.map { it.y }.average().takeIf { it.isFinite() }?.times(10)?.roundToInt()?.coerceIn(0, 100) ?: 40
        val retention = scores.map { it.z }.average().takeIf { it.isFinite() }?.times(10)?.roundToInt()?.coerceIn(0, 100) ?: 40
        val trendFit = ((hook + relevance + retention) / 3).coerceIn(0, 100)
        val potential = ((hook * .35) + (retention * .4) + (relevance * .25)).roundToInt()
        val recommendations = buildList {
            if (hook < 65) add("Start with a specific tension, outcome, or surprising claim.")
            if (retention < 65) add("Add an open loop or clear turn, then resolve it with a concrete payoff.")
            if (wordCount !in 45..190) add("Tighten the spoken copy toward a focused 20–60 second delivery.")
            if (all.contains("follow") || all.contains("comment") || all.contains("save")) return@buildList
            add("End with one clear payoff or low-friction call to action.")
        }.take(3)
        val review = ShortFormReview(
            platform = platform,
            audiencePotential = potential,
            hookScore = hook,
            relatabilityScore = relevance,
            retentionScore = retention,
            trendAlignmentScore = trendFit,
            headline = if (potential >= 75) "Strong short-form foundation" else "Sharpen the opening and payoff",
            diagnosis = "Offline craft estimate for ${platform.displayName}; live trend context was unavailable.",
            recommendations = recommendations,
            benchmarkLabel = "Offline heuristic · no live trend check",
        )
        return ShortFormGeminiResult(scores, review)
    }

    private fun offlineScore(beat: SceneBlock, index: Int, total: Int): RawDimensionScore {
        val lower = beat.rawText.lowercase()
        val hookSignals = listOf("how", "why", "stop", "secret", "mistake", "don't", "here's", "watch", "never")
        val relevanceSignals = listOf("you", "your", "i", "we", "creator", "client", "everyone", "today")
        val retentionSignals = listOf("but", "then", "because", "instead", "finally", "before", "after", "until", "fix")
        val x = (3.0 + hookSignals.count(lower::contains) * 1.15 + if (index == 0) 1.3 else 0.0).coerceIn(1.5, 9.8)
        val y = (2.8 + relevanceSignals.count(lower::contains) * .85).coerceIn(1.5, 9.6)
        val payoffBoost = if (index == total - 1) 1.7 else index.toDouble() / total.coerceAtLeast(1) * 1.2
        val z = (3.0 + retentionSignals.count(lower::contains) * .85 + payoffBoost +
            if ('?' in beat.rawText || '!' in beat.rawText) .6 else 0.0).coerceIn(1.5, 9.8)
        return RawDimensionScore(
            sceneIndex = beat.index,
            x = x,
            y = y,
            z = z,
            title = beat.title,
            summary = "Offline estimate of hook clarity, viewer relevance, and retention/payoff.",
        )
    }
}
