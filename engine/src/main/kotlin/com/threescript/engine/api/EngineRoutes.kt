package com.threescript.engine.api

import com.threescript.engine.ENGINE_VERSION
import com.threescript.engine.config.EngineConfig
import com.threescript.engine.metrics.NarrativeMetrics
import com.threescript.engine.parser.NarrativeParser
import com.threescript.engine.vector.VectorMapper
import com.threescript.shared.api.*
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.slf4j.LoggerFactory

private val log = LoggerFactory.getLogger("EngineRoutes")

/**
 * EngineRoutes — all Ktor HTTP endpoints for the narrative engine.
 *
 * ROUTES:
 *   GET  /api/health       Liveness check, returns engine version
 *   POST /api/parse        Parse raw text → List<SceneBlock>
 *   POST /api/analyze      Parse + score + filter → NarrativeTrack
 *
 * DESIGN:
 *   Routes are the ONLY entry point into the engine from the outside world.
 *   They perform HTTP concerns (request parsing, error responses) and delegate
 *   all business logic to domain classes. No domain logic lives here.
 */
fun Application.configureRoutes() {
    routing {

        // ── Health ────────────────────────────────────────────────────────────
        get("/api/health") {
            call.respond(
                HealthResponse(
                    engineVersion = ENGINE_VERSION,
                    geminiConfigured = !EngineConfig.get("GEMINI_API_KEY").isNullOrBlank(),
                )
            )
        }

        // ── Parse ─────────────────────────────────────────────────────────────
        post("/api/parse") {
            val req = runCatching { call.receive<ParseRequest>() }.getOrElse {
                call.respond(HttpStatusCode.BadRequest, ParseResponse(scenes = emptyList(), error = "Invalid JSON body"))
                return@post
            }

            if (req.text.isBlank()) {
                call.respond(HttpStatusCode.BadRequest, ParseResponse(scenes = emptyList(), error = "No script text provided"))
                return@post
            }

            val scenes = runCatching { NarrativeParser.parse(req.text) }.getOrElse { e ->
                call.respond(HttpStatusCode.InternalServerError, ParseResponse(scenes = emptyList(), error = e.message))
                return@post
            }

            log.info("Parsed ${scenes.size} scenes")
            call.respond(ParseResponse(scenes = scenes))
        }

        // ── Analyze ───────────────────────────────────────────────────────────
        post("/api/analyze") {
            val req = runCatching { call.receive<AnalyzeRequest>() }.getOrElse {
                call.respond(HttpStatusCode.BadRequest, AnalyzeResponse(error = "Invalid JSON body"))
                return@post
            }

            if (req.scenes.isEmpty()) {
                call.respond(HttpStatusCode.BadRequest, AnalyzeResponse(error = "No scenes provided"))
                return@post
            }

            val apiKey = EngineConfig.get("GEMINI_API_KEY")
            val model  = EngineConfig.get("GEMINI_MODEL") ?: "gemini-3.6-flash"
            val mapper = VectorMapper(apiKey = apiKey, model = model)
            val rawPoints = runCatching {
                mapper.map(req.scenes, provider = req.provider)
            }.getOrElse { e ->
                log.error("Vector mapping failed: ${e.message}", e)
                call.respond(HttpStatusCode.InternalServerError, AnalyzeResponse(error = "Scoring error: ${e.message}"))
                return@post
            }

            val track = runCatching {
                NarrativeMetrics.buildTrack(
                    rawPoints = rawPoints,
                    deadZoneThreshold = req.deadZoneThreshold,
                )
            }.getOrElse { e ->
                log.error("Metrics computation failed: ${e.message}", e)
                call.respond(HttpStatusCode.InternalServerError, AnalyzeResponse(error = "Metrics error: ${e.message}"))
                return@post
            }

            log.info("Analysis complete: ${track.points.size} scene points, pacing=${track.summary.pacingRating}")
            call.respond(AnalyzeResponse(track = track))
        }
    }
}
