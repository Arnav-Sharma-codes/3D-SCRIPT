package com.threescript.engine.vector

import com.threescript.engine.ai.GeminiClient
import com.threescript.engine.ai.RawDimensionScore
import com.threescript.shared.model.SceneBlock
import com.threescript.shared.model.VectorPoint
import org.slf4j.LoggerFactory
import kotlin.math.ln1p

/** Maps every parsed scene to one independently scored point in narrative space. */
class VectorMapper(private val apiKey: String?, private val model: String) {

    private val log = LoggerFactory.getLogger(VectorMapper::class.java)

    suspend fun map(scenes: List<SceneBlock>, provider: String): List<VectorPoint> {
        val scores = when {
            provider == "mock" || apiKey.isNullOrBlank() -> {
                log.info("Using heuristic mock scorer for ${scenes.size} scenes")
                scenes.mapIndexed { index, scene -> heuristicScore(scene, scenes.take(index + 1)) }
            }
            provider == "gemini" -> {
                try {
                    GeminiClient(apiKey = apiKey!!, model = model).scoreBatch(scenes)
                } catch (e: Exception) {
                    log.warn("Gemini failed (${e.message}), falling back to heuristic scorer")
                    scenes.mapIndexed { index, scene -> heuristicScore(scene, scenes.take(index + 1)) }
                }
            }
            else -> scenes.mapIndexed { index, scene -> heuristicScore(scene, scenes.take(index + 1)) }
        }.associateBy { it.sceneIndex }

        return scenes.mapIndexed { index, scene ->
            val score = scores[scene.index] ?: heuristicScore(scene, scenes.take(index + 1))
            VectorPoint(
                sceneIndex = scene.index,
                sceneTitle = score.title.ifBlank { scene.title },
                sceneSummary = score.summary,
                xRaw = score.x,
                yRaw = score.y,
                zRaw = score.z,
                x = score.x,
                y = score.y,
                z = score.z,
            )
        }
    }

    /** Offline estimate: plot and character state accumulate only when scene evidence appears. */
    private fun heuristicScore(scene: SceneBlock, scenesThroughHere: List<SceneBlock>): RawDimensionScore {
        val text = scene.rawText.lowercase()
        val tokens = Regex("[a-z']+").findAll(text).map { it.value }.toList()
        val tokenSet = tokens.toSet()
        val plotCues = setOf(
            "attacks", "attacked", "attack", "escapes", "escaped", "escape", "discovers", "discovered",
            "reveals", "revealed", "arrives", "leaves", "returns", "steals", "destroys", "defeats",
            "surrenders", "betrays", "confronts", "pursues", "captures", "rescues", "plans", "orders",
            "fights", "runs", "chases", "shoots", "crashes", "explodes", "kills", "dies", "finds",
            "learns", "escapes", "decides", "chooses", "reveals", "discovers", "changes",
        )
        val characterChangeCues = setOf(
            "realizes", "realized", "understands", "understood", "learns", "learned", "chooses", "chose",
            "decides", "decided", "accepts", "accepted", "refuses", "refused", "admits", "admitted",
            "confesses", "confessed", "regrets", "regretted", "forgives", "forgave", "trusts", "trusted",
            "doubts", "doubted", "changes", "changed", "transforms", "transformed", "becomes", "became",
            "abandons", "sacrifices", "promises", "defies", "joins", "protects", "turns", "knows", "knew",
            "recognizes", "recognized", "remembers", "remembered", "admits", "admitted", "apologizes",
            "apologized", "reconciles", "reconciled", "rejects", "rejected", "commits", "committed",
        )
        val agencyCues = setOf(
            "wants", "wanted", "needs", "needed", "fears", "feared", "believes", "believed", "fights",
            "decides", "decided", "acts", "acted", "refuses", "refused", "risks", "risked", "chooses", "chose",
            "asks", "asked", "searches", "searched", "confronts", "confronted", "protects", "protected",
        )
        val relationshipCues = setOf(
            "loves", "loved", "hates", "hated", "trusts", "trusted", "betrays", "betrayed", "forgives",
            "forgave", "reconciles", "reconciled", "married", "friend", "enemy", "ally", "apologizes", "apologized",
        )
        val emotionCues = setOf(
            "danger", "urgent", "desperate", "panic", "threat", "warning", "death", "blood", "scream",
            "terror", "afraid", "shaking", "grief", "joy", "rage", "fear", "love", "loss", "hope", "dread",
            "cries", "weeps", "laughs", "terrified", "furious", "relieved", "shocked", "trembles",
        )

        fun hasCue(block: SceneBlock, cues: Set<String>): Boolean =
            Regex("[a-z']+").findAll(block.rawText.lowercase()).any { it.value in cues }

        val plotEvents = scenesThroughHere.count { hasCue(it, plotCues) }
        val characterChanges = scenesThroughHere.count { hasCue(it, characterChangeCues) }
        val agencyShifts = scenesThroughHere.count { hasCue(it, agencyCues) }
        val relationshipShifts = scenesThroughHere.count { hasCue(it, relationshipCues) }
        val plot = (1.0 + 1.9 * ln1p(plotEvents.toDouble())).coerceIn(0.0, 10.0)
        val character = (
            1.0 + 1.5 * ln1p(characterChanges.toDouble()) +
                0.65 * ln1p(agencyShifts.toDouble()) + 0.7 * ln1p(relationshipShifts.toDouble())
            ).coerceIn(0.0, 10.0)

        val emotionHits = tokens.count { it in emotionCues }
        val wordCount = tokens.size.coerceAtLeast(1)
        val ellipses = Regex("(?:…|\\.\\.\\.)").findAll(text).count()
        val emotion = (
            1.0 + 2.0 * ln1p(emotionHits * 180.0 / wordCount) +
                text.count { it == '!' } * 0.15 + ellipses * 0.2
            ).coerceIn(0.0, 10.0)

        val summary = if (tokenSet.any { it in characterChangeCues || it in plotCues || it in emotionCues }) {
            "Heuristic estimate from this scene's narrative cues; use Gemini for deeper analysis."
        } else {
            "No strong plot, character-change, or emotional cues detected by the offline scorer."
        }
        return RawDimensionScore(
            sceneIndex = scene.index,
            x = plot,
            y = character,
            z = emotion,
            title = scene.title,
            summary = summary,
        )
    }
}
