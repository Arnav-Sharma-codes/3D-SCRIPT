package com.threescript.engine.metrics

import com.threescript.engine.regression.LinearRegression
import com.threescript.shared.model.*
import kotlin.math.*

/** Computes scene-to-scene movement while preserving each scored 3D coordinate. */
object NarrativeMetrics {

    fun buildTrack(
        rawPoints: List<VectorPoint>,
        deadZoneThreshold: Double? = null,
    ): NarrativeTrack {
        if (rawPoints.isEmpty()) return NarrativeTrack(emptyList(), emptyTrackSummary())

        val exactScores = rawPoints.map { it.copy(x = it.xRaw, y = it.yRaw, z = it.zRaw) }
        val withDelta = computeDeltasWithAdaptiveThreshold(exactScores, deadZoneThreshold)
        val enriched = enrichDramaticMetadata(withDelta)
        return NarrativeTrack(points = enriched, summary = buildSummary(enriched))
    }

    /**
     * Computes Euclidean deltas between consecutive scenes and uses an adaptive
     * percentile threshold so only the slowest ~10–15% of scenes that genuinely
     * drag are flagged as dead zones (rather than 80%+ of the screenplay).
     */
    private fun computeDeltasWithAdaptiveThreshold(
        points: List<VectorPoint>,
        explicitThreshold: Double?,
    ): List<VectorPoint> {
        if (points.size <= 1) return points.map { it.copy(deltaD = 0.0, isDeadZone = false) }

        // Compute raw Euclidean distances
        val rawDeltas = mutableListOf<Double>()
        for (i in 1 until points.size) {
            val prev = points[i - 1]
            val curr = points[i]
            val dx = curr.x - prev.x
            val dy = curr.y - prev.y
            val dz = curr.z - prev.z
            rawDeltas += sqrt(dx * dx + dy * dy + dz * dz)
        }

        // Calculate adaptive threshold targeting the slowest 12-15% of scene transitions
        val effectiveThreshold = calculateAdaptiveLowMovementThreshold(rawDeltas, explicitThreshold)

        return points.mapIndexed { index, point ->
            if (index == 0) {
                point.copy(deltaD = 0.0, isDeadZone = false)
            } else {
                val distance = rawDeltas[index - 1]
                point.copy(
                    deltaD = distance.round4(),
                    isDeadZone = distance < effectiveThreshold,
                )
            }
        }
    }

    /**
     * Determines the low-movement threshold.
     * Uses the 15th percentile of non-zero deltas, bounded between 0.35 and 1.25.
     * This ensures that only ~10-15% of scenes (the ones with true stagnation)
     * get flagged, even on dialogue-heavy or subtle scripts like Tenet.
     */
    fun calculateAdaptiveLowMovementThreshold(deltas: List<Double>, explicitThreshold: Double?): Double {
        if (deltas.isEmpty()) return 0.5
        val nonZero = deltas.filter { it > 0.001 }.sorted()
        if (nonZero.isEmpty()) return 0.5

        // If an explicit custom threshold was configured by the user, respect it but cap it
        if (explicitThreshold != null && explicitThreshold > 0.0) {
            return explicitThreshold
        }

        // 14th percentile index: represents the bottom ~12-15% slowest movements
        val p14Index = ((nonZero.size - 1) * 0.14).roundToInt().coerceIn(0, nonZero.size - 1)
        val p14Value = nonZero[p14Index]

        // Bound to prevent flagging too many scenes or none at all
        return p14Value.coerceIn(0.35, 1.25)
    }

    private fun enrichDramaticMetadata(points: List<VectorPoint>): List<VectorPoint> =
        points.mapIndexed { index, point ->
            val progress = if (points.size <= 1) 0.0 else index.toDouble() / (points.size - 1)
            val (phase, color) = when {
                progress < 0.25 -> "Act I · Setup" to "#38bdf8"
                progress < 0.50 -> "Act II-A · Rising" to "#10b981"
                progress < 0.75 -> "Act II-B · Turning" to "#f59e0b"
                else -> "Act III · Climax & Resolution" to "#f43f5e"
            }

            val (beatType, pacingNote) = when {
                point.isDeadZone -> "Low Narrative Movement" to
                    "Plot, character, and emotion change minimally from the previous scene. Check if this beat can be sharpened or trimmed."
                point.x >= 7.0 && point.z >= 7.0 -> "High-Stakes Turning Point" to
                    "A major external plot move lands at peak emotional tension."
                point.y >= 7.0 && point.z >= 6.0 -> "Character Revelation" to
                    "A key character choice or vulnerability emerges under pressure."
                point.x >= 7.0 -> "Plot Acceleration" to
                    "External story stakes advance substantially."
                point.z >= 7.5 -> "High Tension" to
                    "Dramatic conflict and pressure are sustained at peak levels."
                point.deltaD >= 3.0 -> "Dynamic Narrative Turn" to
                    "Sharp story movement across plot, character, and emotion."
                else -> "Progressive Story Beat" to
                    "Steady dramatic progression supporting narrative momentum."
            }

            point.copy(storyPhase = phase, storyColor = color, beatType = beatType, pacingNote = pacingNote)
        }

    /**
     * Identifies the true dramatic climax of the screenplay.
     * Combines emotional flux (Z), plot progression (X), and character arc (Y)
     * with standard dramatic three-act structural weighting:
     * Screenplay climaxes structurally culminate in Act III (typically 75%–92% runtime),
     * avoiding falsely tagging a mid-movie action set-piece (e.g. at 60%) as the climax.
     */
    fun detectClimaxScene(points: List<VectorPoint>): Int {
        if (points.isEmpty()) return 1
        if (points.size <= 3) return points.maxByOrNull { it.z + it.x }?.sceneIndex ?: 1

        val n = points.size
        var bestIndex = points.last().sceneIndex
        var maxScore = Double.NEGATIVE_INFINITY

        points.forEachIndexed { i, pt ->
            val progress = i.toDouble() / (n - 1) // 0.0 to 1.0

            // Base dramatic intensity: emotional tension (Z) has highest weight, followed by plot (X) and character (Y)
            val intensity = pt.z * 1.35 + pt.x * 1.0 + pt.y * 0.65

            // Dramatic structural multiplier centered on Act III (75% to 92%, peak at ~84%)
            val structuralWeight = when {
                // Prime Climax window: Act III showdown (75% to 92%)
                progress in 0.74..0.92 -> {
                    // Gaussian-like peak around 84%
                    val distFromPeak = abs(progress - 0.84)
                    1.65 - (distFromPeak * 2.2) // 1.45 to 1.65
                }
                // Pre-climax / Dark night of the soul turning into Act III (68% to 74%)
                progress in 0.68..<0.74 -> 1.15
                // Denouement / Resolution (after 92%)
                progress > 0.92 -> 0.90
                // Midpoint / Act II complications (45% to 68%): major turns happen here, but not the final climax
                progress in 0.45..<0.68 -> 0.75
                // Opening & Act I (0% to 45%): setup and initial catalyst
                else -> 0.50
            }

            val totalScore = intensity * structuralWeight
            if (totalScore > maxScore) {
                maxScore = totalScore
                bestIndex = pt.sceneIndex
            }
        }

        return bestIndex
    }

    private fun buildSummary(points: List<VectorPoint>): TrackSummary {
        val deltas = points.drop(1).map { it.deltaD }
        val totalDistance = deltas.sum()
        val meanVelocity = if (deltas.isEmpty()) 0.0 else totalDistance / deltas.size
        val deadZones = points.filter { it.isDeadZone }

        val pacingRating = when {
            meanVelocity >= 3.2 -> "High-Velocity Dramatic Momentum"
            meanVelocity >= 1.8 -> "Balanced Cinematic Flow"
            else -> "Deliberate / Character-Driven Rhythm"
        }
        val verdict = when {
            deadZones.isEmpty() -> "Excellent narrative momentum — every scene advances the plot or character arc."
            deadZones.size <= maxOf(3, (points.size * 0.08).toInt()) ->
                "Well-paced structure with ${deadZones.size} quiet transitional beat(s). Minimal drag detected."
            else ->
                "Pacing diagnostic: ${deadZones.size} scenes (${String.format("%.0f", deadZones.size.toDouble() / points.size * 100)}%) show minimal movement across plot and character. Review these beats for potential streamlining."
        }

        return TrackSummary(
            sceneCount = points.size,
            totalDistance = totalDistance.round4(),
            meanVelocity = meanVelocity.round4(),
            deadZoneCount = deadZones.size,
            deadZoneIndices = deadZones.map { it.sceneIndex },
            climaxSceneIndex = detectClimaxScene(points),
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
