package com.threescript.app

import androidx.compose.runtime.*
import com.threescript.shared.model.NarrativeTrack
import com.threescript.shared.model.VectorPoint
import kotlinx.coroutines.*
import java.nio.file.Paths

/**
 * AppState — single source of truth for the entire UI (MVI pattern).
 *
 * All state mutations flow through this class. Compose panels observe
 * these State values and re-render automatically on change.
 *
 * The front-end NEVER calls engine business logic directly. Every user
 * action dispatches to [EngineClient], which updates this state.
 */
class AppState {
    private val envFile = loadDotEnv()

    // ── Engine connection ─────────────────────────────────────────────────────
    var enginePort: Int by mutableStateOf(0)
    var engineReady: Boolean by mutableStateOf(false)

    // ── Script editor ─────────────────────────────────────────────────────────
    var scriptText: String by mutableStateOf("")
    var projectTitle: String by mutableStateOf("Untitled Project")

    // ── Analysis state ────────────────────────────────────────────────────────
    var isAnalyzing: Boolean by mutableStateOf(false)
    var analysisError: String? by mutableStateOf(null)
    var track: NarrativeTrack? by mutableStateOf(null)

    // ── Inspector state ───────────────────────────────────────────────────────
    var selectedPoint: VectorPoint? by mutableStateOf(null)
    var selectedTab: Int by mutableStateOf(0)  // 0=3D viewport, 1=beat cards

    // ── Settings ──────────────────────────────────────────────────────────────
    var geminiApiKey: String by mutableStateOf(System.getenv("GEMINI_API_KEY") ?: envFile["GEMINI_API_KEY"].orEmpty())
    var geminiModel: String by mutableStateOf(System.getenv("GEMINI_MODEL") ?: envFile["GEMINI_MODEL"] ?: "gemini-3.6-flash")
    var provider: String by mutableStateOf(System.getenv("SCORER_PROVIDER") ?: envFile["SCORER_PROVIDER"] ?: "gemini")
    var deadZoneThreshold: Double by mutableStateOf(
        (System.getenv("DEAD_ZONE_THRESHOLD") ?: envFile["DEAD_ZONE_THRESHOLD"])?.toDoubleOrNull() ?: 1.5
    )
    // ── Derived ───────────────────────────────────────────────────────────────
    val hasResults: Boolean get() = track != null
    val sceneCount: Int get() = track?.summary?.sceneCount ?: 0
}

private fun loadDotEnv(): Map<String, String> {
    val candidates = listOf(".env", "../.env")
    val file = candidates
        .map { Paths.get(it).toFile() }
        .firstOrNull { it.exists() && it.isFile }
        ?: return emptyMap()

    return file.readLines()
        .asSequence()
        .map { it.trim() }
        .filter { it.isNotBlank() && !it.startsWith("#") && "=" in it }
        .mapNotNull { line ->
            val key = line.substringBefore("=").trim()
            val value = line.substringAfter("=").trim().trim('"', '\'')
            key.takeIf { it.isNotBlank() }?.let { it to value }
        }
        .toMap()
}
