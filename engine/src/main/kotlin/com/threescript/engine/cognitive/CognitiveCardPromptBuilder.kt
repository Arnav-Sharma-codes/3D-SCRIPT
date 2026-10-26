package com.threescript.engine.cognitive

import com.threescript.shared.model.SceneBlock
import com.threescript.shared.model.VectorPoint
import com.threescript.shared.model.cognitive.CognitiveTriadState

/**
 * AI Cognitive Card Generator & Prompt Orchestrator.
 *
 * Orchestrates LLM prompt generation for simulating audience cognitive state
 * across three temporal dimensions (Recollection, Perception, Anticipation),
 * and parses response into the structured Cognitive Triad card.
 */
object CognitiveCardPromptBuilder {

    /**
     * Builds the structured prompt for passing scene context to Gemini or another LLM
     * to perform audience cognitive modeling.
     */
    fun buildCognitivePrompt(
        currentScene: SceneBlock,
        priorScenes: List<SceneBlock>,
        subsequentSceneHeadings: List<String>,
        point: VectorPoint,
    ): String {
        val priorContextSnippet = priorScenes.takeLast(3).joinToString("\n") {
            "Scene ${it.index}: ${it.title} -> ${it.rawText.take(150).replace("\n", " ")}"
        }

        return """
YOU ARE AN AUDIENCE COGNITIVE NEUROSCIENCE & SCREENPLAY PACING ANALYZER.
Analyze the viewer's psychological processing of Scene ${currentScene.index} in real time across the Cognitive Triad:

--- 1. PAST RECOLLECTION CONTEXT ---
Recent scenes in viewer memory:
$priorContextSnippet

--- 2. CURRENT SCENE PERCEPTION ---
Scene ${currentScene.index}: ${currentScene.title}
Text:
${currentScene.rawText.take(1200)}

Plot: ${point.x}/10, Character: ${point.y}/10, Emotion: ${point.z}/10

--- 3. FUTURE ANTICIPATION CONTEXT ---
Subsequent scene outlines:
${subsequentSceneHeadings.take(3).joinToString("\n") { "• $it" }}

TASK:
Simulate human viewer cognitive reception and output a strict JSON object with:
{
  "pastContextLink": "How this scene anchors back to earlier setups, Chekhov's guns, or mysteries",
  "recallRetentionPct": 0-100 integer (audience memory retention of earlier setups),
  "currentMentalState": "Short 3-5 word label of audience psychological immersion (e.g. Visceral Suspense, Lore Saturation, Cathartic Relief)",
  "emotionalFluxPct": 0-100 integer (viewer physiological and emotional arousal),
  "cognitiveStrainPct": 0-100 integer (processing difficulty / exposition burden),
  "futurePredictions": [
    {"hypothesis": "Predicted event 1", "probabilityPct": 75},
    {"hypothesis": "Predicted event 2", "probabilityPct": 45}
  ],
  "divergenceExpectationGap": 0.0 to 5.0 (gap between expected convention vs actual scene execution),
  "structuralWarnings": [
    "Flag if recall risk drops below 50% or exposition causes overload"
  ]
}
        """.trimIndent()
    }
}
