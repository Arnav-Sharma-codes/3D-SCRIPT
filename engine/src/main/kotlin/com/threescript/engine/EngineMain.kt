package com.threescript.engine

import com.threescript.engine.api.configureRoutes
import io.ktor.server.application.*
import io.ktor.server.cio.*
import io.ktor.server.engine.*
import io.ktor.server.plugins.calllogging.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.plugins.cors.routing.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.json.Json
import org.slf4j.event.Level
import java.net.ServerSocket

/** Engine version — bump on breaking API changes. */
const val ENGINE_VERSION = "1.0.0"

/**
 * Entry point for the 3DSCRIPT narrative engine.
 *
 * Finds a free local port, starts a Ktor CIO server bound exclusively to
 * 127.0.0.1, and prints the port to stdout so the Compose app can discover it.
 *
 * PRIVACY: The server is NEVER bound to 0.0.0.0. Script text never leaves
 * the process boundary except via explicit Gemini API calls initiated by
 * the user with their own API key.
 */
fun main() {
    val port = findFreePort()
    println("ENGINE_PORT=$port")   // Compose app reads this line

    embeddedServer(CIO, host = "127.0.0.1", port = port, module = Application::narrativeEngineModule)
        .start(wait = true)
}

fun Application.narrativeEngineModule() {
    install(ContentNegotiation) {
        json(Json {
            prettyPrint = false
            ignoreUnknownKeys = true
            encodeDefaults = true
        })
    }
    install(CORS) {
        anyHost()   // Only 127.0.0.1 is ever bound, so this is safe
    }
    install(CallLogging) {
        level = Level.INFO
    }
    configureRoutes()
}

private fun findFreePort(): Int =
    ServerSocket(0).use { it.localPort }
