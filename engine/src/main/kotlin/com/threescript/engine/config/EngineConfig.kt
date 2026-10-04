package com.threescript.engine.config

import java.nio.file.Paths

object EngineConfig {
    private val dotEnv: Map<String, String> by lazy { loadDotEnv() }

    fun get(name: String): String? =
        System.getenv(name) ?: dotEnv[name]

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
}
