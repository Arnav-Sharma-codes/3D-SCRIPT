package com.threescript.app.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.*
import androidx.compose.ui.unit.*
import androidx.compose.ui.window.*
import com.threescript.app.AppState
import com.threescript.app.network.EngineClient
import com.threescript.app.ui.panels.*
import com.threescript.app.ui.components.SettingsDialog
import kotlinx.coroutines.*

// ── Design tokens (Studio Obsidian Slate) ─────────────────────────────────────
val BgBase     = Color(0xFF08090D)
val BgSurface  = Color(0xFF0E1118)
val BgCard     = Color(0xFF131722)
val BorderSubtle = Color(0xFF1D2333)
val BorderStrong = Color(0xFF2B344C)
val TextHigh   = Color(0xFFF8FAFC)
val TextMid    = Color(0xFF94A3B8)
val TextMuted  = Color(0xFF64748B)
val Brand      = Color(0xFF6366F1)
val AxisPlot   = Color(0xFFF43F5E)    // X — Plot Progression
val AxisChar   = Color(0xFF10B981)    // Y — Character Development
val AxisEmot   = Color(0xFF06B6D4)    // Z — Emotional Flux
val AxisTime   = Color(0xFFE2E8F0)    // T — Scene order / narrative time
val FlagDead   = Color(0xFFEF4444)

/**
 * MainWindow — root composable and application window.
 *
 * Layout:
 *   ┌─────────────────────────────────────────────────────┐
 *   │  TopBar (actions, engine status)                     │
 *   ├──────────────────────┬──────────────────────────────┤
 *   │  ScriptEditorPanel   │  ViewportPanel (graph + map)  │
 *   │  (left, 400dp)       │  (right, flex)               │
 *   └─────────────────────────────────────────────────────┘
 */
@Composable
fun ApplicationScope.MainWindow(onCloseRequest: () -> Unit) {
    val state = remember { AppState() }
    val client = remember { EngineClient(state) }
    val scope = rememberCoroutineScope()
    val windowState = rememberWindowState(
        width = 1440.dp,
        height = 900.dp,
        placement = WindowPlacement.Floating,
    )
    val graphCamera = remember { GraphCamera() }
    var graphFullscreen by remember { mutableStateOf(false) }

    // Start engine on first composition
    LaunchedEffect(Unit) {
        scope.launch(Dispatchers.IO) {
            try { client.startEngine() } catch (e: Exception) {
                state.analysisError = "Engine failed to start: ${e.message}"
            }
        }
    }

    Window(
        onCloseRequest = onCloseRequest,
        title = "3D Script Studio",
        state = windowState,
    ) {
        MaterialTheme(colorScheme = darkColorScheme(
            primary = Brand,
            background = BgBase,
            surface = BgSurface,
            onBackground = TextHigh,
            onSurface = TextHigh,
        )) {
            if (graphFullscreen && state.track != null) {
                FullscreenGraphView(
                    track = state.track!!,
                    selectedPoint = state.selectedPoint,
                    camera = graphCamera,
                    onPointSelected = {
                        state.selectedPoint = it
                    },
                    onExitFullscreen = {
                        graphFullscreen = false
                        windowState.placement = WindowPlacement.Floating
                    },
                )
            } else {
                Box(Modifier.fillMaxSize().background(BgBase)) {
                    Column(Modifier.fillMaxSize()) {
                        TopBar(state = state, client = client, scope = scope)

                        Row(Modifier.weight(1f).fillMaxWidth()) {
                            ScriptEditorPanel(
                                modifier = Modifier.width(400.dp).fillMaxHeight(),
                                state = state,
                                onAnalyze = {
                                    scope.launch {
                                        val parsed = client.parse(state.scriptText)
                                        if (!parsed.scenes.isNullOrEmpty()) {
                                            client.analyze(parsed.scenes)
                                        } else {
                                            state.analysisError = parsed.error ?: "No scenes parsed"
                                        }
                                    }
                                }
                            )

                            Box(Modifier.width(1.dp).fillMaxHeight().background(BorderSubtle))

                            ViewportPanel(
                                modifier = Modifier.weight(1f).fillMaxHeight(),
                                state = state,
                                camera = graphCamera,
                                onEnterFullscreen = {
                                    graphFullscreen = true
                                    windowState.placement = WindowPlacement.Fullscreen
                                },
                            )
                        }
                    }

                    if (state.isAnalyzing) AnalysisOverlay()

                    state.analysisError?.let { err ->
                        ErrorBanner(
                            message = err,
                            onDismiss = { state.analysisError = null },
                            modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TopBar(state: AppState, client: EngineClient, scope: CoroutineScope) {
    var showSettings by remember { mutableStateOf(false) }

    Surface(color = BgSurface, tonalElevation = 0.dp) {
        Row(
            modifier = Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // Logo
            Text("⬡", fontSize = 22.sp, color = Brand)
            Text("3D SCRIPT", fontSize = 13.sp, fontWeight = FontWeight.Bold,
                color = TextHigh, letterSpacing = 3.sp)

            Spacer(Modifier.width(8.dp))

            Spacer(Modifier.weight(1f))

            // Engine status badge
            EngineStatusBadge(state.engineReady)

            // Action buttons
            TopBarButton("Sample", Icons.Default.Article) {
                state.scriptText = SAMPLE_SCRIPT
                state.projectTitle = "The Fracture Line"
                state.track = null
                state.selectedPoint = null
            }
            TopBarButton("Settings", Icons.Default.Settings) {
                showSettings = true
            }
        }
    }

    if (showSettings) {
        SettingsDialog(state = state, onDismiss = { showSettings = false })
    }
}

@Composable
private fun EngineStatusBadge(ready: Boolean) {
    val color = if (ready) Color(0xFF10B981) else Color(0xFFF59E0B)
    val label = if (ready) "Engine Ready" else "Engine Starting…"
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(color.copy(alpha = 0.12f))
            .padding(horizontal = 10.dp, vertical = 4.dp),
    ) {
        Box(Modifier.size(6.dp).clip(RoundedCornerShape(3.dp)).background(color))
        Spacer(Modifier.width(6.dp))
        Text(label, fontSize = 11.sp, color = color, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun TopBarButton(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    TextButton(onClick = onClick, colors = ButtonDefaults.textButtonColors(contentColor = TextMid)) {
        Icon(icon, contentDescription = label, modifier = Modifier.size(15.dp))
        Spacer(Modifier.width(4.dp))
        Text(label, fontSize = 12.sp)
    }
}

@Composable
private fun AnalysisOverlay() {
    Box(
        modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.6f)),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
            CircularProgressIndicator(color = Brand, modifier = Modifier.size(48.dp))
            Text("Analyzing narrative structure…", color = TextHigh, fontSize = 14.sp)
            Text("AI scoring scenes via Gemini", color = TextMuted, fontSize = 12.sp)
        }
    }
}

@Composable
private fun ErrorBanner(message: String, onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        color = Color(0xFF1C0A0A),
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(1.dp, FlagDead.copy(alpha = 0.4f)),
    ) {
        Row(
            modifier = Modifier.padding(12.dp, 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(Icons.Default.Error, contentDescription = null, tint = FlagDead, modifier = Modifier.size(16.dp))
            Text(message, color = TextHigh, fontSize = 12.sp, modifier = Modifier.weight(1f))
            IconButton(onClick = onDismiss, modifier = Modifier.size(24.dp)) {
                Icon(Icons.Default.Close, contentDescription = "Dismiss", tint = TextMuted, modifier = Modifier.size(14.dp))
            }
        }
    }
}

// Sample script for "Load Sample" button
private val SAMPLE_SCRIPT = """
INT. SURVEILLANCE VAN - NIGHT

DETECTIVE CHEN studies a wall of monitors. Red pins trace the suspect's last known route through the city grid.

CHEN
He doesn't know we have the footage yet.

AGENT ROSS
How long before he figures it out?

Chen turns. The answer is in her eyes before she says it.

CHEN
Less time than we have.

EXT. DOWNTOWN BRIDGE - NIGHT

The suspect — MARCUS VALE — runs. Behind him: sirens. Above him: the drone Chen launched twenty seconds ago.

He knows now.

INT. COMMAND CENTER - CONTINUOUS

Chen watches the feed. Vale reaches the bridge railing.

ROSS
He's going to jump.

CHEN
He's going to let us think he's going to jump.

A beat. Then Vale turns — and walks calmly toward the officers. Hands raised. Smiling.

It's worse than if he'd jumped.
""".trimIndent()
