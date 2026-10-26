package com.threescript.engine.cognitive

import com.threescript.shared.model.SceneBlock
import com.threescript.shared.model.VectorPoint
import com.threescript.shared.model.cognitive.CognitiveTokenType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class AudienceCognitiveEngineTest {

    @Test
    fun testCognitiveTokenMatching() {
        val scenes = listOf(
            SceneBlock(
                index = 1,
                title = "INT. SAFE HOUSE - NIGHT",
                rawText = "The Protagonist promises he will return before midnight. He tucks away the hidden key in his pocket.",
            ),
            SceneBlock(
                index = 2,
                title = "INT. LAB - DAY",
                rawText = "The scientist gives a quantum temporal algorithm briefing regarding inverted entropy protocols.",
            ),
            SceneBlock(
                index = 3,
                title = "EXT. ALLEY - NIGHT",
                rawText = "Suddenly his partner betrays him and pulls a gun. The vault is empty!",
            ),
        )

        val tokens = CognitiveTokenMatcher.matchTokens(scenes)
        assertTrue(tokens.isNotEmpty(), "Should identify cognitive tokens")

        val setupTokens = tokens.filter { it.type == CognitiveTokenType.SETUP_CYAN }
        assertTrue(setupTokens.isNotEmpty(), "Should find setup tokens in scene 1")
        assertEquals(1, setupTokens.first().sceneIndex)

        val overloadTokens = tokens.filter { it.type == CognitiveTokenType.OVERLOAD_PURPLE }
        assertTrue(overloadTokens.isNotEmpty(), "Should identify exposition overload in scene 2")
        assertEquals(2, overloadTokens.first().sceneIndex)

        val subversionTokens = tokens.filter { it.type == CognitiveTokenType.SUBVERSION_AMBER }
        assertTrue(subversionTokens.isNotEmpty(), "Should find subversion token in scene 3")
        assertEquals(3, subversionTokens.first().sceneIndex)
    }

    @Test
    fun testAudienceCognitiveTriadGeneration() {
        val points = listOf(
            VectorPoint(1, "Scene 1", "Summary 1", 3.0, 4.0, 5.0, 3.0, 4.0, 5.0),
            VectorPoint(2, "Scene 2", "Summary 2", 5.0, 5.0, 6.0, 5.0, 5.0, 6.0),
            VectorPoint(3, "Scene 3", "Summary 3", 8.5, 7.0, 9.0, 8.5, 7.0, 9.0),
        )
        val scenes = listOf(
            SceneBlock(1, "Scene 1", "He hides the gun."),
            SceneBlock(2, "Scene 2", "They discuss the treaty protocol."),
            SceneBlock(3, "Scene 3", "He discovers the body is gone!"),
        )

        val tokens = CognitiveTokenMatcher.matchTokens(scenes)
        val overlay = AudienceCognitiveEngine.generateOverlay(points, scenes, tokens)

        assertEquals(3, overlay.size)

        // Check Scene 1 (Recollection active)
        val s1 = overlay[0]
        assertEquals(1, s1.sceneIndex)
        assertTrue(s1.recollection.recallRetentionPct in 80..100)

        // Check Scene 3 (Divergence & anticipation)
        val s3 = overlay[2]
        assertEquals(3, s3.sceneIndex)
        assertTrue(s3.anticipation.expectationVector.x > 0.0)
        assertTrue(s3.anticipation.divergenceGap >= 0.0)
        assertTrue(s3.perception.emotionalFluxPct >= 50)
        assertNotNull(s3.anticipation.topHypotheses)
    }
}
