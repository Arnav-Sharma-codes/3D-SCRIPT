package com.threescript.ml.fusion

/**
 * ScoreFusionLayer — blends rule-engine, Gemini AI, and ML model scores
 * into a single authoritative scene score for each narrative dimension.
 *
 * The three weights (α, β, γ) always sum to 1.0. As the user's ML corpus grows,
 * γ can be increased in Settings to trust the local model more.
 *
 * Default weights:
 *   α = 0.25  (rule engine — fast, deterministic)
 *   β = 0.45  (Gemini AI  — contextual, semantic)
 *   γ = 0.30  (ML model   — corpus-trained, writer-personalised)
 */
data class FusionWeights(
    val ruleWeight: Float = 0.25f,
    val geminiWeight: Float = 0.45f,
    val mlWeight: Float = 0.30f,
) {
    init {
        val sum = ruleWeight + geminiWeight + mlWeight
        require(sum in 0.98f..1.02f) {
            "FusionWeights must sum to 1.0, got $sum"
        }
    }

    companion object {
        /** Weights before any ML training has happened (ML not yet trusted). */
        val RULE_GEMINI_ONLY = FusionWeights(ruleWeight = 0.35f, geminiWeight = 0.65f, mlWeight = 0.0f)

        /** Balanced weights once a reasonable corpus (≥ 20 scripts) has been trained. */
        val BALANCED = FusionWeights(ruleWeight = 0.25f, geminiWeight = 0.45f, mlWeight = 0.30f)

        /** Maximum ML trust — for writers with large annotated corpora. */
        val ML_DOMINANT = FusionWeights(ruleWeight = 0.15f, geminiWeight = 0.30f, mlWeight = 0.55f)
    }
}

/**
 * Raw per-scene scores from each intelligence source.
 * Any source can be null if unavailable (e.g., Gemini offline, ML not trained).
 */
data class RawSceneScores(
    val sceneIndex: Int,
    // Rule engine scores (always present)
    val ruleX: Float, val ruleY: Float, val ruleZ: Float,
    // Gemini AI scores (may be null if offline or parse failed)
    val geminiX: Float?, val geminiY: Float?, val geminiZ: Float?,
    // ML model scores (null until first training run completes)
    val mlX: Float?, val mlY: Float?, val mlZ: Float?,
)

/**
 * Fused output for one scene across all three dimensions.
 */
data class FusedSceneScore(
    val sceneIndex: Int,
    val x: Float,  // Plot progression
    val y: Float,  // Character arc
    val z: Float,  // Emotional tension
    val sourceSummary: String, // e.g. "Rule 25% · Gemini 45% · ML 30%"
)

class ScoreFusionLayer(private val weights: FusionWeights = FusionWeights()) {

    /**
     * Fuse scores for a single scene. Falls back gracefully when any source
     * is unavailable by redistributing its weight proportionally.
     */
    fun fuse(raw: RawSceneScores): FusedSceneScore {
        val (wR, wG, wM) = effectiveWeights(raw)

        return FusedSceneScore(
            sceneIndex = raw.sceneIndex,
            x = (raw.ruleX * wR + (raw.geminiX ?: raw.ruleX) * wG + (raw.mlX ?: raw.ruleX) * wM).coerceIn(0f, 10f),
            y = (raw.ruleY * wR + (raw.geminiY ?: raw.ruleY) * wG + (raw.mlY ?: raw.ruleY) * wM).coerceIn(0f, 10f),
            z = (raw.ruleZ * wR + (raw.geminiZ ?: raw.ruleZ) * wG + (raw.mlZ ?: raw.ruleZ) * wM).coerceIn(0f, 10f),
            sourceSummary = "Rule ${(wR * 100).toInt()}% · Gemini ${(wG * 100).toInt()}% · ML ${(wM * 100).toInt()}%",
        )
    }

    /** Fuse a full list of scene scores (one pass for the whole track). */
    fun fuseAll(rawScores: List<RawSceneScores>): List<FusedSceneScore> =
        rawScores.map { fuse(it) }

    /**
     * Re-distribute weights when a source is unavailable.
     * E.g. if ML not trained (mlX == null), split its weight evenly to rule + Gemini.
     */
    private fun effectiveWeights(raw: RawSceneScores): Triple<Float, Float, Float> {
        val hasGemini = raw.geminiX != null
        val hasMl = raw.mlX != null

        return when {
            hasGemini && hasMl -> Triple(weights.ruleWeight, weights.geminiWeight, weights.mlWeight)
            hasGemini && !hasMl -> {
                val extra = weights.mlWeight / 2f
                Triple(weights.ruleWeight + extra, weights.geminiWeight + extra, 0f)
            }
            !hasGemini && hasMl -> {
                val extra = weights.geminiWeight / 2f
                Triple(weights.ruleWeight + extra, 0f, weights.mlWeight + extra)
            }
            else -> Triple(1f, 0f, 0f)  // Rule engine only
        }
    }
}
