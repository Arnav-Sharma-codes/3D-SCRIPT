package com.threescript.app.network

import com.threescript.app.AppState
import com.threescript.shared.api.*
import com.threescript.shared.model.SceneBlock
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.coroutines.*
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory
import java.io.BufferedReader
import java.io.InputStreamReader
import java.nio.file.Paths

/**
 * EngineClient — HTTP client that communicates with the local Ktor engine process.
 *
 * Responsibilities:
 *   1. Spawn the engine JAR process and capture its port from stdout
 *   2. Wait for the engine to become healthy
 *   3. Provide suspend functions for each engine API endpoint
 *   4. Update [AppState] on responses
 *
 * The app never imports any engine classes directly — all communication
 * is via JSON/HTTP through the shared API contracts.
 */
class EngineClient(private val state: AppState) {

    private val log = LoggerFactory.getLogger(EngineClient::class.java)
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    private val http = HttpClient(CIO) {
        install(ContentNegotiation) { json(json) }
        engine { requestTimeout = 120_000 }
    }

    private val baseUrl get() = "http://127.0.0.1:${state.enginePort}"

    // ── Engine process lifecycle ───────────────────────────────────────────────

    /**
     * Start the engine JAR in a background process, capture the port it binds to,
     * then poll /api/health until it responds.
     */
    suspend fun startEngine() = withContext(Dispatchers.IO) {
        val engineJar = findEngineJar()
        log.info("Starting engine from: $engineJar")

        val process = ProcessBuilder("java", "-jar", engineJar)
            .apply {
                environment()["GEMINI_API_KEY"] = state.geminiApiKey
                environment()["GEMINI_MODEL"]   = state.geminiModel
                redirectErrorStream(true)
            }
            .start()

        // Read ENGINE_PORT=NNNN from first line of stdout
        val reader = BufferedReader(InputStreamReader(process.inputStream))
        val portLine = reader.readLine() ?: ""
        val port = Regex("ENGINE_PORT=(\\d+)").find(portLine)?.groupValues?.get(1)?.toIntOrNull()
            ?: throw IllegalStateException("Engine did not report port. Got: $portLine")

        state.enginePort = port
        log.info("Engine bound to port $port")

        // Continue draining stdout in background
        GlobalScope.launch(Dispatchers.IO) {
            reader.forEachLine { log.debug("[engine] $it") }
        }

        // Poll health
        waitForEngine()
        state.engineReady = true
        log.info("Engine ready on port $port")
    }

    private suspend fun waitForEngine(maxAttempts: Int = 30) {
        repeat(maxAttempts) { i ->
            try {
                val health = http.get("$baseUrl/api/health").body<HealthResponse>()
                if (health.status == "ok") return
            } catch (_: Exception) {}
            delay(500)
            if (i == maxAttempts - 1) throw IllegalStateException("Engine did not start within ${maxAttempts * 500}ms")
        }
    }

    private fun findEngineJar(): String {
        // Look for engine JAR relative to this process's working directory
        val candidates = listOf(
            "engine/build/libs/engine-all.jar",
            "engine/build/libs/engine.jar",
            "../engine/build/libs/engine-all.jar",
            "../engine/build/libs/engine.jar",
            "engine.jar",
        )
        return candidates.firstOrNull { Paths.get(it).toFile().exists() }
            ?: throw IllegalStateException(
                "Engine JAR not found. Run: gradle :engine:jar\nSearched: $candidates"
            )
    }

    // ── API calls ─────────────────────────────────────────────────────────────

    /** Parse raw script text into scenes. */
    suspend fun parse(text: String): ParseResponse =
        http.post("$baseUrl/api/parse") {
            contentType(ContentType.Application.Json)
            setBody(ParseRequest(text = text))
        }.body()

    /**
     * Score every scene independently and return the time-ordered scene trajectory.
     * Updates [AppState] on success or failure.
     */
    suspend fun analyze(scenes: List<SceneBlock>) {
        state.isAnalyzing = true
        state.analysisError = null
        try {
            val resp: AnalyzeResponse = http.post("$baseUrl/api/analyze") {
                contentType(ContentType.Application.Json)
                setBody(AnalyzeRequest(
                    scenes = scenes,
                    provider = state.provider,
                    deadZoneThreshold = state.deadZoneThreshold,
                ))
            }.body()

            if (resp.error != null) {
                state.analysisError = resp.error
            } else {
                state.track = resp.track
                state.selectedPoint = resp.track?.points?.firstOrNull()
            }
        } catch (e: Exception) {
            log.error("Analysis request failed: ${e.message}", e)
            state.analysisError = "Network error: ${e.message}"
        } finally {
            state.isAnalyzing = false
        }
    }
}
