package com.threescript.ml.pipeline

import com.threescript.ml.data.*
import com.threescript.shared.model.NarrativeTrack
import com.threescript.shared.model.VectorPoint
import com.threescript.shared.model.cognitive.CognitiveTriadState
import com.threescript.shared.model.cognitive.CognitiveTokenType
import kotlin.math.sqrt

/**
 * FeatureExtractor — converts a fully-analyzed NarrativeTrack into a list of
 * SceneFeatureVector (32 dims each) ready for ML training or inference.
 *
 * This runs entirely on existing engine output — no new API calls needed.
 * The extractor is deterministic: same track → same features, every time.
 */
class FeatureExtractor {

    /**
     * Extract one SceneFeatureVector per scene from an analyzed track.
     *
     * @param scriptId  The SQLite row ID for the parent script
     * @param track     Fully analyzed NarrativeTrack (rule engine + cognitive overlay)
     * @return          Ordered list of feature vectors, one per scene
     */
    fun extract(scriptId: Long, track: NarrativeTrack): List<SceneFeatureVector> {
        val points = track.points
        if (points.isEmpty()) return emptyList()

        val maxX = points.maxOf { it.x }.coerceAtLeast(1.0)
        val maxY = points.maxOf { it.y }.coerceAtLeast(1.0)
        val maxZ = points.maxOf { it.z }.coerceAtLeast(1.0)
        val maxDelta = points.maxOf { it.deltaD }.coerceAtLeast(1.0)
        val sceneCount = points.size

        val cognitiveByScene: Map<Int, CognitiveTriadState> =
            track.cognitiveOverlay.associateBy { it.sceneIndex }

        return points.mapIndexed { idx, point ->
            val f = FloatArray(SceneFeatureVector.FEATURE_DIM)
            val prev = if (idx > 0) points[idx - 1] else null
            val cognitive = cognitiveByScene[point.sceneIndex]

            // ── Dims 0–2: Normalised 3D position ────────────────────────────
            f[0] = (point.x / maxX).toFloat()
            f[1] = (point.y / maxY).toFloat()
            f[2] = (point.z / maxZ).toFloat()

            // ── Dims 3–5: Velocity from previous scene ───────────────────────
            f[3] = if (prev != null) ((point.x - prev.x) / maxX).toFloat() else 0f
            f[4] = if (prev != null) ((point.y - prev.y) / maxY).toFloat() else 0f
            f[5] = if (prev != null) ((point.z - prev.z) / maxZ).toFloat() else 0f

            // ── Dim 6: Pacing delta (Euclidean movement) ────────────────────
            f[6] = (point.deltaD / maxDelta).toFloat()

            // ── Dim 7: Narrative time (position in story) ───────────────────
            f[7] = (idx.toFloat() / sceneCount.coerceAtLeast(1))

            // ── Dims 8–11: Cognitive overlay metrics ─────────────────────────
            f[8]  = (cognitive?.recollection?.recallRetentionPct?.toFloat() ?: 70f) / 100f
            f[9]  = (cognitive?.perception?.emotionalFluxPct?.toFloat() ?: 50f) / 100f
            f[10] = (cognitive?.anticipation?.divergenceGap?.toFloat() ?: 0f) / 10f
            f[11] = (cognitive?.anticipation?.subversionScore?.toFloat() ?: 0f) / 10f

            // ── Dims 12–14: One-hot act encoding ─────────────────────────────
            val actFraction = idx.toFloat() / sceneCount
            f[12] = if (actFraction < 0.25f) 1f else 0f   // Act I
            f[13] = if (actFraction in 0.25f..0.75f) 1f else 0f // Act II
            f[14] = if (actFraction > 0.75f) 1f else 0f   // Act III

            // ── Dims 15–17: Dominant cognitive token type ────────────────────
            val tokenTypes = track.cognitiveTokens
                .filter { it.sceneIndex == point.sceneIndex }
                .map { it.type }
            f[15] = if (tokenTypes.contains(CognitiveTokenType.SETUP_CYAN)) 1f else 0f
            f[16] = if (tokenTypes.contains(CognitiveTokenType.SUBVERSION_AMBER)) 1f else 0f
            f[17] = if (tokenTypes.contains(CognitiveTokenType.OVERLOAD_PURPLE)) 1f else 0f

            // ── Dim 18: Dead zone flag ───────────────────────────────────────
            f[18] = if (point.isDeadZone) 1f else 0f

            // ── Dim 19: Dialogue density (estimated) ─────────────────────────
            // Approximate: dialogue lines tend to be short (< 60 chars) and
            // character names are ALL CAPS. We estimate from sceneSummary length.
            f[19] = estimateDialogueDensity(point.sceneSummary)

            // ── Dims 20–31: TF-IDF slugline bag-of-words ────────────────────
            SceneFeatureVector.SLUGLINE_VOCAB.forEachIndexed { vocabIdx, term ->
                f[20 + vocabIdx] = if (point.sceneTitle.uppercase().contains(term)) 1f else 0f
            }

            SceneFeatureVector(scriptId, point.sceneIndex, f)
        }
    }

    /**
     * Bootstrap labels from existing rule engine + Gemini scores.
     *
     * These serve as the initial training signal. The user can override them
     * with manual annotations in the ML Training Studio.
     */
    fun bootstrapLabels(scriptId: Long, track: NarrativeTrack): List<SceneLabel> {
        val points = track.points
        val sceneCount = points.size

        return points.mapIndexed { idx, point ->
            val actFraction = idx.toDouble() / sceneCount

            val pacingClass = when {
                point.isDeadZone -> PacingClass.DEAD
                point.deltaD >= 3.0 -> PacingClass.DYNAMIC
                else -> PacingClass.STEADY
            }

            val structuralRole = when {
                actFraction < 0.12 -> StructuralRole.SETUP
                actFraction < 0.60 -> StructuralRole.RISING_ACTION
                actFraction < 0.75 -> StructuralRole.MIDPOINT
                point.sceneIndex == track.summary.climaxSceneIndex -> StructuralRole.CLIMAX
                else -> StructuralRole.RESOLUTION
            }

            SceneLabel(
                scriptId = scriptId,
                sceneIndex = point.sceneIndex,
                plotScore = point.x.toFloat().coerceIn(0f, 10f),
                characterScore = point.y.toFloat().coerceIn(0f, 10f),
                emotionScore = point.z.toFloat().coerceIn(0f, 10f),
                pacingClass = pacingClass,
                structuralRole = structuralRole,
                isManuallyAnnotated = false,
            )
        }
    }

    private fun estimateDialogueDensity(sceneSummary: String): Float {
        if (sceneSummary.isBlank()) return 0.3f
        val words = sceneSummary.trim().split(Regex("\\s+"))
        val shortWords = words.count { it.length <= 4 }
        return (shortWords.toFloat() / words.size.coerceAtLeast(1)).coerceIn(0f, 1f)
    }
}
