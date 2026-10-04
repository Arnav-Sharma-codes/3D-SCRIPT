package com.threescript.engine.metrics

import com.threescript.engine.regression.LinearRegression
import com.threescript.shared.model.*
import kotlin.math.sqrt

/** Computes scene-to-scene movement while preserving each scored 3D coordinate. */
object NarrativeMetrics {

    fun buildTrack(
        rawPoints: List<VectorPoint>,
        deadZoneThreshold: Double = 1.5,
    ): NarrativeTrack {
        if (rawPoints.isEmpty()) return NarrativeTrack(emptyList(), emptyTrackSummary())

        val exactScores = rawPoints.map { it.copy(x = it.xRaw, y = it.yRaw, z = it.zRaw) }
        val withDelta = computeDeltas(exactScores, deadZoneThreshold)
        val enriched = enrichDramaticMetadata(withDelta)
        return NarrativeTrack(points = enriched, summary = buildSummary(enriched))
    }

    private fun computeDeltas(points: List<VectorPoint>, threshold: Double): List<VectorPoint> =
        points.mapIndexed { index, point ->
            if (index == 0) point.copy(deltaD = 0.0, isDeadZone = false)
            else {
                val previous = points[index - 1]
                val dx = point.x - previous.x
                val dy = point.y - previous.y
                val dz = point.z - previous.z
                val distance = sqrt(dx * dx + dy * dy + dz * dz)
                point.copy(deltaD = distance.round4(), isDeadZone = distance < threshold)
            }
        }

    private fun enrichDramaticMetadata(points: List<VectorPoint>): List<VectorPoint> =
        points.mapIndexed { index, point ->
            val progress = if (points.size <= 1) 0.0 else index.toDouble() / (points.size - 1)
            val (phase, color) = when {
                progress < 0.2 -> "Opening" to "#38bdf8"
                progress < 0.5 -> "Rising" to "#10b981"
                progress < 0.8 -> "Turning" to "#f59e0b"
                else -> "Closing" to "#f43f5e"
            }

            val (beatType, pacingNote) = when {
                point.isDeadZone -> "Low Scene Movement" to
                    "Plot, character, and emotion change little from the previous scene. Consider whether this beat earns its place or needs a sharper turn."
                point.x >= 7.0 && point.z >= 7.0 -> "High-Stakes Turning Point" to
                    "A major plot movement lands at high emotional pressure."
                point.y >= 7.0 && point.z >= 6.0 -> "Character Revelation" to
                    "A meaningful character shift coincides with heightened emotional pressure."
                point.x >= 7.0 -> "Plot Momentum" to
                    "This scene makes a significant external story move."
                point.z >= 7.5 -> "High Tension / Suspense" to
                    "Emotional pressure is especially high in this scene."
                point.deltaD >= 3.5 -> "Dynamic Narrative Shift" to
                    "The story moves sharply across plot, character, or emotion from the previous scene."
                else -> "Transitional Story Beat" to
                    "A measured beat between stronger narrative movements."
            }

            point.copy(storyPhase = phase, storyColor = color, beatType = beatType, pacingNote = pacingNote)
        }

    private fun buildSummary(points: List<VectorPoint>): TrackSummary {
        val deltas = points.drop(1).map { it.deltaD }
        val totalDistance = deltas.sum()
        val meanVelocity = if (deltas.isEmpty()) 0.0 else totalDistance / deltas.size
        val deadZones = points.filter { it.isDeadZone }

        val pacingRating = when {
            meanVelocity >= 3.8 -> "High-Octane / Relentless"
            meanVelocity >= 2.3 -> "Dynamic Cinematic Flow"
            else -> "Measured / Deliberate"
        }
        val verdict = when {
            deadZones.isEmpty() -> "Strong scene-to-scene movement across plot, character, and emotion."
            deadZones.size <= 2 ->
                "The trajectory has ${deadZones.size} low-movement scene transition(s). Review those beats for stalled plot, character, or emotional change."
            else ->
                "Pacing warning: ${deadZones.size} scene transitions show little movement. Review the plot, character arc, and emotional progression around those scenes."
        }

        return TrackSummary(
            sceneCount = points.size,
            totalDistance = totalDistance.round4(),
            meanVelocity = meanVelocity.round4(),
            deadZoneCount = deadZones.size,
            deadZoneIndices = deadZones.map { it.sceneIndex },
            climaxSceneIndex = points.maxByOrNull { it.z + it.x }?.sceneIndex ?: 1,
            pacingRating = pacingRating,
            doctorVerdict = verdict,
            xStats = LinearRegression.axisStats(points.map { it.x }),
            yStats = LinearRegression.axisStats(points.map { it.y }),
            zStats = LinearRegression.axisStats(points.map { it.z }),
        )
    }

    private fun emptyTrackSummary() = TrackSummary(
        sceneCount = 0,
        totalDistance = 0.0,
        meanVelocity = 0.0,
        deadZoneCount = 0,
        deadZoneIndices = emptyList(),
        climaxSceneIndex = 0,
        pacingRating = "—",
        doctorVerdict = "No scenes to analyze.",
        xStats = AxisStats(0.0, 0.0, 0.0, 0.0, 0.0),
        yStats = AxisStats(0.0, 0.0, 0.0, 0.0, 0.0),
        zStats = AxisStats(0.0, 0.0, 0.0, 0.0, 0.0),
    )

    private fun Double.round4(): Double = (this * 10000).toLong() / 10000.0
}
