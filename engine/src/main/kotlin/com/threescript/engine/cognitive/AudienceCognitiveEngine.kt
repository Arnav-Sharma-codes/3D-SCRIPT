package com.threescript.engine.cognitive

import com.threescript.shared.model.SceneBlock
import com.threescript.shared.model.VectorPoint
import com.threescript.shared.model.cognitive.*
import kotlin.math.*

/**
 * AudiencePredictiveCognitiveOverlay Engine
 *
 * Simulates real-time human cognitive processing across three temporal dimensions:
 *   1. Recollection (Past Context & Recall Tracking)
 *   2. Perception (Present Emotional Flux & Cognitive Strain)
 *   3. Anticipation (Future Expectation Vector & Divergence Gap)
 */
object AudienceCognitiveEngine {

    fun generateOverlay(
        points: List<VectorPoint>,
        scenes: List<SceneBlock>,
        tokens: List<CognitiveHighlightToken>,
    ): List<CognitiveTriadState> {
        if (points.isEmpty()) return emptyList()

        val tokensByScene = tokens.groupBy { it.sceneIndex }
        val cumulativeSetups = mutableListOf<SetupItem>()
        val resolvedCallbacks = mutableListOf<CallbackLink>()

        return points.mapIndexed { index, pt ->
            val sceneTokens = tokensByScene[pt.sceneIndex].orEmpty()

            // ── 1. Update Recollection (Past) ────────────────────────────────
            val newSetups = sceneTokens
                .filter { it.type == CognitiveTokenType.SETUP_CYAN }
                .map { token ->
                    SetupItem(
                        id = "setup_${pt.sceneIndex}_${token.tokenText.take(12)}",
                        setupSceneIndex = pt.sceneIndex,
                        headline = token.explanation.substringBefore(":"),
                        tokenText = token.tokenText,
                        setupType = if ("Chekhov" in token.explanation) "Chekhov's Gun" else "Narrative Seed"
                    )
                }
            cumulativeSetups.addAll(newSetups)

            // Check if any subversion/callback resolves earlier setups
            val callbacksThisScene = sceneTokens
                .filter { it.type == CognitiveTokenType.SUBVERSION_AMBER && it.originatingSceneIndex != null }
                .map { token ->
                    CallbackLink(
                        id = "cb_${pt.sceneIndex}_${token.originatingSceneIndex}",
                        setupSceneIndex = token.originatingSceneIndex!!,
                        callbackSceneIndex = pt.sceneIndex,
                        description = token.explanation,
                        payoffQuality = if (pt.z >= 7.0) "High-Stakes Payoff" else "Subverted Payoff"
                    )
                }
            resolvedCallbacks.addAll(callbacksThisScene)

            // Working memory decay model: audience recall drops as scene distance increases
            val oldestUnresolvedSetup = cumulativeSetups.firstOrNull()
            val recallRetentionPct = if (oldestUnresolvedSetup == null) {
                95
            } else {
                val distance = pt.sceneIndex - oldestUnresolvedSetup.setupSceneIndex
                // Exponential decay: drops toward ~35% after 25 scenes without resolution
                (100.0 * exp(-0.042 * max(0, distance))).roundToInt().coerceIn(20, 100)
            }

            val memoryLoadScore = (cumulativeSetups.size * 0.75 + sceneTokens.size * 0.4).coerceIn(0.5, 9.8)

            val pastSummary = when {
                cumulativeSetups.isEmpty() -> "Initial exposition: establishing baseline world rules and character introductions."
                cumulativeSetups.size > 5 -> "${cumulativeSetups.size} active narrative threads open in audience working memory."
                else -> "${cumulativeSetups.size} narrative setup(s) planted; audience anticipating payoff."
            }

            val recollection = RecollectionState(
                activeSetupsCount = cumulativeSetups.size,
                resolvedCallbacksCount = resolvedCallbacks.size,
                activeSetups = cumulativeSetups.takeLast(4),
                resolvedCallbacks = callbacksThisScene,
                recallRetentionPct = recallRetentionPct,
                memoryLoadScore = (memoryLoadScore * 10).roundToInt() / 10.0,
                contextSummary = pastSummary,
            )

            // ── 2. Compute Perception (Present) ──────────────────────────────
            val emotionalFluxPct = ((pt.z / 10.0) * 100).roundToInt().coerceIn(12, 99)
            val hasOverload = sceneTokens.any { it.type == CognitiveTokenType.OVERLOAD_PURPLE }
            val attentionalFocusPct = ((0.5 * pt.x + 0.5 * pt.deltaD).coerceIn(1.0, 10.0) / 10.0 * 100).roundToInt().coerceIn(30, 98)
            val cognitiveStrainPct = if (hasOverload) {
                ((memoryLoadScore / 10.0 * 45) + 50).roundToInt().coerceIn(55, 95)
            } else {
                ((memoryLoadScore / 10.0 * 40) + 15).roundToInt().coerceIn(15, 80)
            }

            val mentalState = when {
                hasOverload -> "Cognitive Saturation / Lore Heavy"
                emotionalFluxPct >= 80 -> "High-Tension Visceral Immersion"
                pt.x >= 7.5 -> "Active Narrative Alignment"
                pt.deltaD < 0.6 -> "Reflective Baseline"
                else -> "Steady Narrative Processing"
            }

            val perception = PerceptionState(
                emotionalFluxPct = emotionalFluxPct,
                attentionalFocusPct = attentionalFocusPct,
                cognitiveStrainPct = cognitiveStrainPct,
                audienceMentalState = mentalState,
            )

            // ── 3. Compute Anticipation (Future) ─────────────────────────────
            // Project the audience expectation vector based on prior narrative inertia
            val expectationVector = computeExpectationVector(points, index)

            val dx = pt.x - expectationVector.x
            val dy = pt.y - expectationVector.y
            val dz = pt.z - expectationVector.z
            val divergence = sqrt(dx * dx + dy * dy + dz * dz)

            val subversionScore = (divergence * 2.2).coerceIn(0.0, 10.0)
            val isTwist = divergence >= 2.0 || sceneTokens.any { it.type == CognitiveTokenType.SUBVERSION_AMBER }

            val hypotheses = generateHypotheses(pt, expectationVector, index, points.size)

            val anticipation = AnticipationState(
                expectationVector = expectationVector,
                divergenceGap = (divergence * 100).roundToInt() / 100.0,
                subversionScore = (subversionScore * 10).roundToInt() / 10.0,
                isSubversiveTwist = isTwist,
                topHypotheses = hypotheses,
            )

            // ── 4. Generate Cognitive Warnings ───────────────────────────────
            val warnings = mutableListOf<CognitiveWarning>()
            if (recallRetentionPct < 42 && cumulativeSetups.isNotEmpty()) {
                val oldest = cumulativeSetups.first()
                warnings += CognitiveWarning(
                    level = "WARNING",
                    message = "Recall Fade: Setup '${oldest.headline}' from Scene ${oldest.setupSceneIndex} has been dormant for ${pt.sceneIndex - oldest.setupSceneIndex} scenes.",
                    affectedSceneIndex = oldest.setupSceneIndex,
                    recommendation = "Provide an echo or dialogue reminder to reactivate audience working memory."
                )
            }
            if (hasOverload) {
                warnings += CognitiveWarning(
                    level = "CRITICAL",
                    message = "Cognitive Saturation: Exposition density exceeds optimal working memory threshold.",
                    affectedSceneIndex = pt.sceneIndex,
                    recommendation = "Break up explanatory dialogue with visual action beats to prevent audience disengagement."
                )
            }
            if (isTwist && cumulativeSetups.isEmpty() && pt.sceneIndex > 5) {
                warnings += CognitiveWarning(
                    level = "INFO",
                    message = "Unanchored Subversion: Sharp trajectory break occurs without prior planted setups.",
                    affectedSceneIndex = pt.sceneIndex,
                    recommendation = "Consider foreshadowing this reversal earlier so the surprise feels earned."
                )
            }

            CognitiveTriadState(
                sceneIndex = pt.sceneIndex,
                recollection = recollection,
                perception = perception,
                anticipation = anticipation,
                cognitiveWarnings = warnings,
            )
        }
    }

    /**
     * Audience projects next scene coordinates based on narrative trajectory inertia.
     */
    private fun computeExpectationVector(points: List<VectorPoint>, currentIndex: Int): CognitiveVector {
        if (currentIndex == 0) {
            val pt = points[0]
            return CognitiveVector(pt.x, pt.y, pt.z)
        }
        val prev = points[currentIndex - 1]
        val prev2 = if (currentIndex >= 2) points[currentIndex - 2] else prev

        // Velocity inertia from prior 2 scenes
        val velX = (prev.x - prev2.x) * 0.70
        val velY = (prev.y - prev2.y) * 0.60
        val velZ = (prev.z - prev2.z) * 0.65

        val expX = (prev.x + velX).coerceIn(0.5, 9.8)
        val expY = (prev.y + velY).coerceIn(0.5, 9.8)
        val expZ = (prev.z + velZ).coerceIn(0.5, 9.8)

        return CognitiveVector(
            x = (expX * 100).roundToInt() / 100.0,
            y = (expY * 100).roundToInt() / 100.0,
            z = (expZ * 100).roundToInt() / 100.0,
        )
    }

    private fun generateHypotheses(
        current: VectorPoint,
        expectation: CognitiveVector,
        index: Int,
        totalScenes: Int,
    ): List<AudienceHypothesis> {
        val progress = if (totalScenes <= 1) 0.5 else index.toDouble() / (totalScenes - 1)
        val list = mutableListOf<AudienceHypothesis>()

        when {
            progress < 0.25 -> {
                list += AudienceHypothesis("Inciting catalyst forces protagonist out of comfort zone", 78, "Structural Catalyst")
                list += AudienceHypothesis("Allies establish primary external mission stakes", 62, "World-Building")
            }
            progress in 0.25..0.50 -> {
                list += AudienceHypothesis("Protagonist faces escalating obstacles pursuing initial plan", 74, "Rising Complication")
                list += AudienceHypothesis("Hidden vulnerability in antagonist defenses discovered", 55, "Tactical Advance")
            }
            progress in 0.50..0.75 -> {
                list += AudienceHypothesis("Major reversal collapses initial strategy ('All Is Lost')", 82, "Midpoint Fallout")
                list += AudienceHypothesis("Key ally reveals dual motives or deeper secret", 64, "Relational Friction")
            }
            else -> {
                list += AudienceHypothesis("Decisive confrontation resolves core thematic conflict", 86, "Climactic Payoff")
                list += AudienceHypothesis("Sacrifice required to secure permanent resolution", 71, "Thematic Catharsis")
            }
        }
        return list
    }
}
