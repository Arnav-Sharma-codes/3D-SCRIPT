package com.threescript.engine.parser

import com.threescript.shared.model.SceneBlock

/**
 * NarrativeParser — converts raw screenplay or prose text into an ordered
 * list of [SceneBlock] tokens.
 *
 * Parse strategy (in priority order):
 *  1. Fountain sluglines: INT./EXT./I/E + INTERIOR/EXTERIOR variants
 *  2. Numbered scene headings: "1. TITLE" / "#1 TITLE"
 *  3. Paragraph-segmentation fallback for plain prose
 *
 * This module has zero dependencies on the AI layer, the vector mapper,
 * or the front-end. It operates solely on raw text strings.
 */
object NarrativeParser {

    // Fountain + extended slugline regex
    private val SLUGLINE = Regex(
        pattern = """(?m)^[ \t]*(?:INT\./EXT\.|I/E\.|INT\.|EXT\.|INTERIOR|EXTERIOR|SCENE\s+\d+|#\d+|\d+\.\s+[A-Z]).*$""",
        options = setOf(RegexOption.IGNORE_CASE),
    )
    private val ACT_HEADING = Regex(
        """(?im)^[ \t]*ACT[ \t]+(ONE|TWO|THREE|VIII|VII|VI|IV|V|III|II|I|IX|X|[0-9]+)(?:[ \t]*[:—-].*)?[ \t]*$"""
    )
    private val NUMBERED_HEADING = Regex("""(?m)^\s*(\d+)[.\)]\s+(.+)$""")

    /**
     * Parse raw text into an ordered list of [SceneBlock]s.
     *
     * @param text Raw screenplay or narrative text
     * @return Non-empty list of scene blocks, guaranteed to have ≥1 element
     */
    fun parse(text: String): List<SceneBlock> {
        val trimmed = text.trim()
        if (trimmed.isBlank()) return listOf(SceneBlock(1, "Untitled Scene", trimmed))

        return when {
            SLUGLINE.containsMatchIn(trimmed)         -> parseBySluglines(trimmed)
            NUMBERED_HEADING.containsMatchIn(trimmed) -> parseByNumberedHeadings(trimmed)
            else                                       -> parseByParagraphs(trimmed)
        }
    }

    // ── Strategy 1: Fountain sluglines ────────────────────────────────────────

    private fun parseBySluglines(text: String): List<SceneBlock> {
        val matches = SLUGLINE.findAll(text).toList()
        if (matches.isEmpty()) return parseByParagraphs(text)
        val blocks = mutableListOf<SceneBlock>()
        matches.forEachIndexed { i, match ->
            val start = match.range.first
            val nextSceneStart = if (i + 1 < matches.size) matches[i + 1].range.first else text.length
            val body = ACT_HEADING.replace(text.substring(start, nextSceneStart), "").trim()
            val title = match.value.trim()
                .replace(Regex("^(INT\\.|EXT\\.|INT\\./EXT\\.|I/E\\.)\\s*", RegexOption.IGNORE_CASE), "")
                .take(80)
            blocks += SceneBlock(
                index = i + 1,
                title = title.ifBlank { "Scene ${i + 1}" },
                rawText = body,
            )
        }
        return blocks
    }

    // ── Strategy 2: Numbered headings ─────────────────────────────────────────

    private fun parseByNumberedHeadings(text: String): List<SceneBlock> {
        val matches = NUMBERED_HEADING.findAll(text).toList()
        val blocks = mutableListOf<SceneBlock>()
        matches.forEachIndexed { i, match ->
            val start = match.range.first
            val end = if (i + 1 < matches.size) matches[i + 1].range.first else text.length
            val body = text.substring(start, end).trim()
            blocks += SceneBlock(
                index = i + 1,
                title = match.groupValues[2].trim().take(80),
                rawText = body,
            )
        }
        return blocks.ifEmpty { parseByParagraphs(text) }
    }

    // ── Strategy 3: Paragraph / sentence fallback ─────────────────────────────

    private fun parseByParagraphs(text: String): List<SceneBlock> {
        val paragraphs = text.split(Regex("\n{2,}")).map { it.trim() }.filter { it.isNotBlank() }

        // If only 1 paragraph, split into sentence chunks (4 groups)
        val chunks = if (paragraphs.size <= 1) {
            val sentences = text.split(Regex("(?<=[.!?])\\s+")).filter { it.isNotBlank() }
            if (sentences.size <= 1) return listOf(SceneBlock(1, "Scene 1", text))
            val groupSize = maxOf(1, sentences.size / 4)
            sentences.chunked(groupSize).take(4).map { it.joinToString(" ") }
        } else paragraphs

        return chunks.mapIndexed { i, chunk ->
            val title = chunk.take(60).replace(Regex("[\n\r]+"), " ").trim()
            SceneBlock(index = i + 1, title = "Scene ${i + 1}: $title…", rawText = chunk)
        }
    }
}
