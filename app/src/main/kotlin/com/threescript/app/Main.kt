package com.threescript.app

import androidx.compose.ui.window.application
import com.threescript.app.ui.MainWindow

/**
 * Application entry point.
 *
 * Launches the Compose Desktop application which itself spawns
 * the engine process on first run and connects to it via HTTP.
 */
fun main() = application {
    MainWindow(onCloseRequest = ::exitApplication)
}
