package com.threescript.engine.metrics

import com.threescript.shared.model.VectorPoint
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class NarrativeMetricsTest {

    private fun makePoint(index: Int, x: Double, y: Double, z: Double): VectorPoint =
        VectorPoint(
            sceneIndex = index,
            sceneTitle = "Scene $index",
            sceneSummary = "Summary $index",
            x = x,
            y = y,
            z = z,
            xRaw = x,
            yRaw = y,
            zRaw = z,
        )

    @Test
    fun testAdaptiveLowMovementThresholdFlagsAroundTenToFifteenPercent() {
        // Simulate a 100-scene script where scenes move on average by 1.0 to 1.5
        // but 12 scenes move very little (0.1 to 0.3)
        val points = mutableListOf<VectorPoint>()
        var curX = 5.0
        var curY = 5.0
        var curZ = 5.0
        for (i in 1..100) {
            val step = if (i % 8 == 0) 0.15 else 1.25 // every 8th scene drags
            curX += step
            curY += step * 0.8
            curZ += step * 0.9
            points += makePoint(i, curX, curY, curZ)
        }

        val track = NarrativeMetrics.buildTrack(points)
        val deadZoneCount = track.summary.deadZoneCount
        val deadZoneRatio = deadZoneCount.toDouble() / 100.0

        assertTrue(
            deadZoneRatio in 0.08..0.22,
            "Expected dead zone ratio to be around 10-20%, but got ${deadZoneRatio * 100}% ($deadZoneCount of 100 scenes)",
        )
    }

    @Test
    fun testClimaxDetectionFavorsActThreeOverMidpointActionSpike() {
        // Simulate a 100-scene script:
        // Midpoint at Scene 60 has high tension: Z=9.0, X=9.0 (like Tenet turnstile or Freeport)
        // Act III climax at Scene 85 has high tension: Z=9.0, X=8.5 (final battle)
        val points = (1..100).map { i ->
            when (i) {
                60 -> makePoint(i, x = 9.0, y = 6.0, z = 9.0) // Midpoint action spike
                85 -> makePoint(i, x = 8.5, y = 7.5, z = 9.0) // True Act 3 Climax
                else -> makePoint(i, x = 4.0 + (i * 0.03), y = 4.0, z = 4.0 + (i * 0.03))
            }
        }

        val climaxScene = NarrativeMetrics.detectClimaxScene(points)
        assertEquals(85, climaxScene, "Expected Act III Scene 85 to be detected as climax, not Midpoint Scene 60")
    }
}
