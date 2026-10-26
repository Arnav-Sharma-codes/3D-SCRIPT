package com.threescript.shared.model.cognitive

import kotlinx.serialization.Serializable

/**
 * The Cognitive Triad simulates human cognitive processing across three temporal dimensions
 * as the audience experiences the narrative:
 *   - Recollection: Past context accumulation, active memory load, and payoff tracking.
 *   - Perception: Real-time emotional immersion, attentional focus, and cognitive load.
 *   - Anticipation: Predictive hypothesis generation, expectation vectors, and subversion gaps.
 */
@Serializable
data class CognitiveTriadState(
    val sceneIndex: Int,
    val recollection: RecollectionState,
    val perception: PerceptionState,
    val anticipation: AnticipationState,
    val cognitiveWarnings: List<CognitiveWarning> = emptyList(),
)

@Serializable
data class RecollectionState(
    val activeSetupsCount: Int,
    val resolvedCallbacksCount: Int,
    val activeSetups: List<SetupItem> = emptyList(),
    val resolvedCallbacks: List<CallbackLink> = emptyList(),
    val recallRetentionPct: Int,          // 0-100: Estimated audience recall retention of prior setups
    val memoryLoadScore: Double,          // 0.0 to 10.0: Cognitive strain from unresolved threads
    val contextSummary: String = "",       // Plain-English past context summary
)

@Serializable
data class PerceptionState(
    val emotionalFluxPct: Int,            // 0-100: Visceral emotional arousal in current scene
    val attentionalFocusPct: Int,         // 0-100: Audience immersion vs distraction
    val cognitiveStrainPct: Int,          // 0-100: Exposition load / processing difficulty
    val audienceMentalState: String,      // e.g. "High-Tension Immersion", "Absorbing Setup", "Cognitive Saturation"
)

@Serializable
data class AnticipationState(
    val expectationVector: CognitiveVector, // Audience projected (x, y, z) trajectory
    val divergenceGap: Double,              // Euclidean distance between actual scene & expectation vector
    val subversionScore: Double,           // 0.0 to 10.0: Degree of expectation subversion / twist
    val isSubversiveTwist: Boolean,        // True if divergence exceeds dramatic surprise threshold
    val topHypotheses: List<AudienceHypothesis> = emptyList(), // What audience expects next
)

@Serializable
data class CognitiveVector(
    val x: Double, // Projected plot momentum
    val y: Double, // Projected character shift
    val z: Double, // Projected emotional intensity
)

@Serializable
data class AudienceHypothesis(
    val statement: String,       // e.g. "Protagonist will reveal identity to Neil"
    val probabilityPct: Int,     // 0-100
    val dramaticArchetype: String = "Logical Progression",
)

@Serializable
data class SetupItem(
    val id: String,
    val setupSceneIndex: Int,
    val headline: String,
    val tokenText: String,
    val setupType: String = "Narrative Seed", // "Chekhov's Gun", "Mystery Question", "Foreshadowing"
)

@Serializable
data class CallbackLink(
    val id: String,
    val setupSceneIndex: Int,
    val callbackSceneIndex: Int,
    val description: String,
    val payoffQuality: String = "Earned Payoff", // "Earned Payoff", "Delayed Callback", "Subverted Payoff"
)

@Serializable
data class CognitiveWarning(
    val level: String, // "INFO", "WARNING", "CRITICAL"
    val message: String,
    val affectedSceneIndex: Int? = null,
    val recommendation: String = "",
)

@Serializable
enum class CognitiveTokenType {
    SETUP_CYAN,          // Cyan/Blue: Planting future expectations / Chekhov's guns
    SUBVERSION_AMBER,    // Red/Amber: Breaking audience expectations / Twists / Betrayals
    OVERLOAD_PURPLE,     // Purple: Excessive exposition / Information saturation
}

@Serializable
data class CognitiveHighlightToken(
    val type: CognitiveTokenType,
    val sceneIndex: Int,
    val tokenText: String,
    val lineSnippet: String,
    val explanation: String,
    val originatingSceneIndex: Int? = null, // Set for callbacks to link back to originating setup
)
