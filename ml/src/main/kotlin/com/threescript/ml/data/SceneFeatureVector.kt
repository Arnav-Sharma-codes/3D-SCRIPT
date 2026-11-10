package com.threescript.ml.data

/**
 * SceneFeatureVector — fixed-length 32-dimensional float encoding of a single scene.
 *
 * This is the primary input unit to the NarrativeTransformer model. All fields
 * are normalised to [0, 1] before packing into the flat float array for ONNX
 * inference.
 *
 * Layout:
 *   [0–2]   : (x, y, z) normalised 3D position
 *   [3–5]   : (Δx, Δy, Δz) velocity from previous scene
 *   [6]     : deltaD (Euclidean movement, normalised by max observed)
 *   [7]     : narrativeTime  (scene_index / scene_count)
 *   [8]     : recallRetentionPct / 100
 *   [9]     : emotionalFluxPct / 100
 *   [10]    : divergenceGap / 10
 *   [11]    : subversionScore / 10
 *   [12–14] : one-hot act encoding (I, II, III)
 *   [15–17] : one-hot token type (Setup=cyan, Twist=amber, Overload=purple)
 *   [18]    : isDeadZone (0.0 or 1.0)
 *   [19]    : dialogueDensity (estimated dialogue ratio 0–1)
 *   [20–31] : TF-IDF bag-of-slugline-words (top-12 screenplay action verbs)
 */
data class SceneFeatureVector(
    val scriptId: Long,
    val sceneIndex: Int,
    val features: FloatArray,           // Always length FEATURE_DIM
) {
    init {
        require(features.size == FEATURE_DIM) {
            "SceneFeatureVector requires exactly $FEATURE_DIM dims, got ${features.size}"
        }
    }

    companion object {
        const val FEATURE_DIM = 32

        /**
         * Top-12 high-signal action verbs used for the TF-IDF bag-of-words
         * sub-encoding (dims 20–31). These were chosen for maximum screenplay
         * discriminative power across genre and act structure.
         */
        val SLUGLINE_VOCAB = listOf(
            "INT", "EXT", "CONTINUOUS", "LATER", "NIGHT", "DAY",
            "FLASHBACK", "FLASHFORWARD", "INTERCUT", "DREAM", "CLOSE", "WIDE"
        )
    }
}

/**
 * Labels associated with one scene for supervised training.
 *
 * Initial labels are bootstrapped from the rule engine + Gemini AI scores.
 * Writers can manually override them in the ML Training Studio.
 */
data class SceneLabel(
    val scriptId: Long,
    val sceneIndex: Int,

    // Regression targets [0.0, 10.0]
    val plotScore: Float,
    val characterScore: Float,
    val emotionScore: Float,

    // Classification targets
    val pacingClass: PacingClass,       // Dead / Steady / Dynamic
    val structuralRole: StructuralRole, // Setup / Rising / Midpoint / Climax / Resolution

    // Whether this label was manually verified by the user
    val isManuallyAnnotated: Boolean = false,
)

enum class PacingClass(val id: Int, val label: String) {
    DEAD(0, "Dead Zone"),
    STEADY(1, "Steady Progression"),
    DYNAMIC(2, "Dynamic Turn"),
    ;
    companion object {
        fun fromId(id: Int) = entries.first { it.id == id }
    }
}

enum class StructuralRole(val id: Int, val label: String) {
    SETUP(0, "Setup"),
    RISING_ACTION(1, "Rising Action"),
    MIDPOINT(2, "Midpoint Turn"),
    CLIMAX(3, "Climax"),
    RESOLUTION(4, "Resolution"),
    ;
    companion object {
        fun fromId(id: Int) = entries.first { it.id == id }
    }
}
