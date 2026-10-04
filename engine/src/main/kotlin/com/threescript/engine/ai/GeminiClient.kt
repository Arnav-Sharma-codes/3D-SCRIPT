package com.threescript.engine.ai

import com.threescript.shared.model.SceneBlock
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.client.statement.bodyAsText
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.coroutines.delay
import kotlinx.serialization.json.*
import org.slf4j.LoggerFactory

/**
 * GeminiClient — isolated HTTP adapter for the Google Gemini REST API.
 *
 * PRIVACY:
 *   - Uses pure REST (no Google SDK), minimizing third-party package footprint
 *   - API key is passed per-call from env; never logged or persisted here
 *   - Scene text is sent only when the user explicitly triggers analysis
 *
 * The client is decoupled from the vector mapping logic. It returns raw
 * dimension scores that [com.threescript.engine.vector.VectorMapper] then
 * transforms into [VectorPoint]s.
 */
class GeminiClient(
    private val apiKey: String,
    private val model: String = "gemini-3.6-flash",
) {
    private val log = LoggerFactory.getLogger(GeminiClient::class.java)
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    private val httpClient = HttpClient(CIO) {
        install(ContentNegotiation) { json(json) }
    }

    private val endpoint
        get() = "https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent?key=$apiKey"

    /**
     * Score every parsed scene in one ordered batch request.
     *
     * @param scenes Ordered screenplay scenes
     * @return List of [RawDimensionScore] in scene order
     * @throws GeminiException on API or parsing failure
     */
    suspend fun scoreBatch(scenes: List<SceneBlock>): List<RawDimensionScore> {
        val sceneText = scenes.joinToString("\n\n") { scene ->
            """SCENE ${scene.index}: ${scene.title}
${sceneTextForScoring(scene.rawText)}"""
        }

        val prompt = buildBatchPrompt(sceneText, scenes.size)

        log.info("Sending ${scenes.size} scenes to Gemini ($model) in batch…")

        var lastErr: Exception? = null
        repeat(3) { attempt ->
            try {
                val raw = callGemini(prompt)
                val parsed = parseBatchResponse(raw, scenes)
                log.info("Gemini batch scored ${parsed.size} scenes successfully")
                return parsed
            } catch (e: RateLimitException) {
                val waitMs = (2000L * (attempt + 1))
                log.warn("Rate limit on attempt ${attempt + 1}, waiting ${waitMs}ms")
                delay(waitMs)
                lastErr = e
            } catch (e: Exception) {
                log.error("Gemini error on attempt ${attempt + 1}: ${e.message}")
                lastErr = e
            }
        }
        throw GeminiException("Gemini batch failed after 3 attempts: ${lastErr?.message}", lastErr)
    }

    private suspend fun callGemini(prompt: String): String {
        val body = buildJsonObject {
            put("contents", buildJsonArray {
                add(buildJsonObject {
                    put("parts", buildJsonArray {
                        add(buildJsonObject { put("text", prompt) })
                    })
                })
            })
            put("generationConfig", buildJsonObject {
                put("temperature", 0.3)
                put("maxOutputTokens", 4096)
            })
        }

        val response = httpClient.post(endpoint) {
            contentType(ContentType.Application.Json)
            setBody(body)
        }

        if (response.status.value == 429) throw RateLimitException("Rate limit hit")
        if (!response.status.isSuccess())
            throw GeminiException("HTTP ${response.status.value}: ${response.bodyAsText()}")

        val responseBody = response.body<JsonObject>()
        return responseBody["candidates"]
            ?.jsonArray?.firstOrNull()?.jsonObject
            ?.get("content")?.jsonObject
            ?.get("parts")?.jsonArray?.firstOrNull()?.jsonObject
            ?.get("text")?.jsonPrimitive?.content
            ?: throw GeminiException("Could not extract text from Gemini response")
    }

    private fun parseBatchResponse(raw: String, scenes: List<SceneBlock>): List<RawDimensionScore> {
        // Strip markdown fences
        val cleaned = raw.trim()
            .removePrefix("```json").removePrefix("```")
            .removeSuffix("```").trim()

        val parsed = runCatching { json.parseToJsonElement(cleaned).jsonObject }.getOrNull()
            ?: runCatching {
                val start = cleaned.indexOf('{')
                val end = cleaned.lastIndexOf('}')
                if (start >= 0 && end > start)
                    json.parseToJsonElement(cleaned.substring(start, end + 1)).jsonObject
                else null
            }.getOrNull()
            ?: throw GeminiException("Cannot parse JSON from Gemini response: ${raw.take(200)}")

        val sceneArray = parsed["scenes"]?.jsonArray
            ?: throw GeminiException("Missing 'scenes' key in Gemini response")

        val resultMap = mutableMapOf<Int, RawDimensionScore>()
        for (el in sceneArray) {
            val obj = el.jsonObject
            val idx = obj["index"]?.jsonPrimitive?.intOrNull ?: continue
            resultMap[idx] = RawDimensionScore(
                sceneIndex = idx,
                x = obj["x"]?.jsonPrimitive?.doubleOrNull?.coerceIn(0.0, 10.0) ?: 5.0,
                y = obj["y"]?.jsonPrimitive?.doubleOrNull?.coerceIn(0.0, 10.0) ?: 5.0,
                z = obj["z"]?.jsonPrimitive?.doubleOrNull?.coerceIn(0.0, 10.0) ?: 5.0,
                title = obj["title"]?.jsonPrimitive?.contentOrNull ?: scenes.firstOrNull { it.index == idx }?.title.orEmpty(),
                summary = obj["summary"]?.jsonPrimitive?.contentOrNull ?: "",
            )
        }

        return scenes.mapNotNull { resultMap[it.index] }
    }

    private fun buildBatchPrompt(sceneText: String, count: Int): String = """
You are a professional screenplay analyst and narrative structure expert.

Analyze the following $count ordered scenes from one screenplay. Every scene must receive its own score point.
Score THREE narrative dimensions on a scale of 0.0 to 10.0:

  x — PLOT PROGRESSION (0–10)
      The story's plot state by the end of this scene: goal progress, new information, conflict,
      reversals, and changes to the story world. Judge this scene in context of prior scenes.

  y — CHARACTER DEVELOPMENT (0–10)
      The central character's arc state by the end of this scene, compared with earlier scenes.
      Score actual changes in agency, beliefs, moral stance, identity, self-understanding, or key
      relationships. Do not count character names or character-related words. A scene can advance,
      reverse, complicate, or leave the arc unchanged; let y rise, fall, or hold accordingly.

  z — EMOTIONAL FLUX (0–10)
      The audience's emotional pressure in this scene: tension, dread, joy, grief,
      catharsis, and tonal contrast.
      0 = flat, emotionally neutral. 10 = peak suspense / catharsis / visceral impact.

GUIDELINES:
  - Return exactly one point per input scene, in the same order and with the same scene index.
  - Score each scene separately; never combine scenes into acts or omit transitional scenes.
  - Treat y as the character's state along the arc at that scene, not a fixed screenplay-wide default.
  - Re-evaluate x, y, and z at every scene; reuse a value only when the story genuinely holds that dimension steady.
  - Keep the plotted coordinates as direct 0–10 scores; do not smooth or normalize them across scenes.
  - Be precise and discriminating. Most scene scores should be 2–8; reserve extremes for clear evidence.

SCENES TO ANALYZE:
$sceneText

Return ONLY a valid JSON object exactly matching this schema (no markdown, no commentary):
{
  "scenes": [
    {
      "index": <integer, matches scene number above>,
      "x": <float 0.0–10.0>,
      "y": <float 0.0–10.0>,
      "z": <float 0.0–10.0>,
      "title": "<concise scene title>",
      "summary": "<single sentence: plot, character, and emotional movement in this scene>"
    }
  ]
}
""".trimIndent()

    private fun sceneTextForScoring(text: String, limit: Int = 1800): String =
        if (text.length <= limit) text
        else {
            val head = text.take(limit * 2 / 3)
            val tail = text.takeLast(limit / 3)
            "$head\n\n[Middle portion omitted for length; scene ending follows]\n\n$tail"
        }
}

/** Raw AI-scored dimensions for one scene. */
data class RawDimensionScore(
    val sceneIndex: Int,
    val x: Double,
    val y: Double,
    val z: Double,
    val title: String,
    val summary: String,
)

class GeminiException(message: String, cause: Throwable? = null) : Exception(message, cause)
class RateLimitException(message: String) : Exception(message)
