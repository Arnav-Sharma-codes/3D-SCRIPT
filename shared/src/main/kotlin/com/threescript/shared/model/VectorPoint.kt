package com.threescript.shared.model

import kotlinx.serialization.Serializable

/**
 * A single point in 3D narrative vector space for one screenplay scene.
 *
 * AXIS DEFINITIONS:
 *   x — Plot Progression (0–10)
 *       External world-state change: goal advancement, structural pivots,
 *       event density, macro story beat presence.
 *
 *   y — Character Development (0–10)
 *       Internal arc: psychological agency shifts, relationship dynamics,
 *       character revelation depth, want-vs-need tension.
 *
 *   z — Emotional Flux (0–10)
 *       Affective stakes: psychological tension, dread/joy contrast,
 *       tonal volatility, audience-affect pressure in the scene.
 *
 * Additional fields supply the dramatic interpretation layer and are
 * populated by [com.threescript.engine.metrics.NarrativeMetrics].
 */
@Serializable
data class VectorPoint(
    val sceneIndex: Int,
    val sceneTitle: String,
    val sceneSummary: String,

    // ── Raw AI scores (pre-filter) ───────────────────────────────────────────
    val xRaw: Double,
    val yRaw: Double,
    val zRaw: Double,

    // ── Rendered coordinates (identical to the scored values) ───────────────
    val x: Double,
    val y: Double,
    val z: Double,

    // ── Derived spatial metrics ──────────────────────────────────────────────
    val deltaD: Double = 0.0,           // Euclidean distance from previous point
    val isDeadZone: Boolean = false,    // True if deltaD < threshold

    // ── Dramatic interpretation ──────────────────────────────────────────────
    val storyPhase: String = "Opening",
    val storyColor: String = "#38bdf8",
    val beatType: String = "Story Beat",
    val pacingNote: String = "",
)
