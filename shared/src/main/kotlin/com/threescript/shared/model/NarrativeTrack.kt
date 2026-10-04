package com.threescript.shared.model

import kotlinx.serialization.Serializable

/**
 * The complete spatial-temporal rendering array output from the engine.
 *
 * This is the canonical data structure passed from the engine to the front-end.
 * The front-end treats this as read-only and renders it directly; no business
 * logic belongs in the front-end.
 *
 * @property points         Ordered list of 3D vector points, one per scene
 * @property summary        Aggregate narrative statistics
 * @property engineVersion  Semantic version of the engine that produced this track
 */
@Serializable
data class NarrativeTrack(
    val points: List<VectorPoint>,
    val summary: TrackSummary,
    val engineVersion: String = "1.0.0",
)

/**
 * Aggregate statistics computed across the scene trajectory.
 */
@Serializable
data class TrackSummary(
    val sceneCount: Int,                  // Parsed screenplay scenes
    val totalDistance: Double,           // Sum of 3D score movement between scenes
    val meanVelocity: Double,            // totalDistance / (sceneCount - 1)
    val deadZoneCount: Int,
    val deadZoneIndices: List<Int>,      // 1-based scene indices
    val climaxSceneIndex: Int,           // Scene with highest (z + x) combined
    val pacingRating: String,            // "High-Octane" | "Dynamic" | "Measured"
    val doctorVerdict: String,           // Plain-English script doctor diagnosis
    val xStats: AxisStats,
    val yStats: AxisStats,
    val zStats: AxisStats,
)

/**
 * Statistical summary for a single axis across all scenes.
 */
@Serializable
data class AxisStats(
    val min: Double,
    val max: Double,
    val mean: Double,
    val regressionSlope: Double,         // β₁ from OLS — positive = rising arc
    val regressionIntercept: Double,     // β₀ from OLS
)
