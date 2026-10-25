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

    // Robust slugline regex supporting industry-standard screenplay formats,
    // messy PDF conversions, leading/trailing scene numbers, and split prefixes.
    private val SLUGLINE = Regex(
        pattern = """(?im)^[ \t]*(?:(?:\d+[A-Z]?|[#\[]\d+[\]]?)\s+)?(?:INT\.\s*/\s*EXT\.|EXT\.\s*/\s*INT\.|INT\s*/\s*EXT\.?|EXT\s*/\s*INT\.?|/EXT\.|/INT\.|I/E\.?|INT\.|EXT\.|INTERIOR|EXTERIOR|SCENE\s+\d+|#\d+|\d+\.\s+[A-Z]).*${'$'}""",
    )
    private val ACT_HEADING = Regex(
        """(?im)^[ \t]*ACT[ \t]+(ONE|TWO|THREE|VIII|VII|VI|IV|V|III|II|I|IX|X|[0-9]+)(?:[ \t]*[:—-].*)?[ \t]*${'$'}"""
    )
    private val NUMBERED_HEADING = Regex("""(?m)^\s*(\d+)[.\)]\s+(.+)$""")

    // Normalizes messy sluglines like "/EXT. VAN" -> "INT./EXT. VAN"
    private val LEADING_SCENE_NUM = Regex("""^\s*(?:\d+[A-Z]?|[#\[]\d+[\]]?|[0-9]+)[\.\)\s\t\-]+""")
    private val TRAILING_SCENE_NUM = Regex("""\s+(?:\d+[A-Z]?|[#\[]\d+[\]]?|[0-9]+)\s*${'$'}""")
    private val SLASH_EXT_FIX = Regex("""^/EXT\b\.?""", RegexOption.IGNORE_CASE)
    private val SLASH_INT_FIX = Regex("""^/INT\b\.?""", RegexOption.IGNORE_CASE)
    private val INT_EXT_VARIANTS = Regex("""^(?:INT\.\s*/\s*EXT\.|INT\s*/\s*EXT\.?|EXT\.\s*/\s*INT\.|EXT\s*/\s*INT\.?)\s*""", RegexOption.IGNORE_CASE)
    private val STANDARD_PREFIXES = Regex("""^(?:I/E\.?|INT\.|EXT\.|INTERIOR|EXTERIOR)\s*""", RegexOption.IGNORE_CASE)

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

    // ── Strategy 1: Fountain & production sluglines ───────────────────────────

    private fun parseBySluglines(text: String): List<SceneBlock> {
        val matches = SLUGLINE.findAll(text).toList()
        if (matches.isEmpty()) return parseByParagraphs(text)
        val blocks = mutableListOf<SceneBlock>()

        // Check if there is meaningful story / prologue text before the first slugline
        val firstStart = matches[0].range.first
        if (firstStart > 0) {
            val prologue = text.substring(0, firstStart).trim()
            // Ignore trivial title pages or author metadata, but preserve genuine opening scenes
            if (isSubstantiveSceneContent(prologue)) {
                val prologueTitle = extractPrologueTitle(prologue)
                blocks += SceneBlock(
                    index = 1,
                    title = prologueTitle,
                    rawText = prologue,
                )
            }
        }

        matches.forEachIndexed { i, match ->
            val start = match.range.first
            val nextSceneStart = if (i + 1 < matches.size) matches[i + 1].range.first else text.length
            val body = ACT_HEADING.replace(text.substring(start, nextSceneStart), "").trim()
            val rawSlug = match.value.trim()
            val title = cleanSceneTitle(rawSlug)
            val sceneIndex = blocks.size + 1
            blocks += SceneBlock(
                index = sceneIndex,
                title = title.ifBlank { "Scene $sceneIndex" },
                rawText = body,
            )
        }
        return blocks
    }

    /**
     * Cleans and normalizes a raw matched slugline into a professional scene title.
     * - Fixes messy "/EXT. VAN" -> "INT./EXT. VAN"
     * - Strips leading & trailing scene numbers (e.g. "1 EXT. CONCERT - NIGHT 1" -> "EXT. CONCERT - NIGHT")
     * - Retains slugline context (INT. / EXT. / INT./EXT.) so writers know the environment.
     */
    fun cleanSceneTitle(raw: String): String {
        var s = raw.trim()
        // Strip leading scene number: "1 INT. ..." -> "INT. ..."
        s = LEADING_SCENE_NUM.replace(s, "").trim()
        // Strip trailing scene number: "INT. ... 1" -> "INT. ..."
        s = TRAILING_SCENE_NUM.replace(s, "").trim()

        // Fix broken "/EXT. VAN" or "/INT."
        if (SLASH_EXT_FIX.containsMatchIn(s)) {
            s = SLASH_EXT_FIX.replace(s, "INT./EXT.")
        } else if (SLASH_INT_FIX.containsMatchIn(s)) {
            s = SLASH_INT_FIX.replace(s, "INT./EXT.")
        } else if (INT_EXT_VARIANTS.containsMatchIn(s)) {
            s = INT_EXT_VARIANTS.replace(s, "INT./EXT. ")
        }

        return s.take(80).trim()
    }

    private fun isSubstantiveSceneContent(text: String): Boolean {
        if (text.length < 35) return false
        val lines = text.lines().map { it.trim() }.filter { it.isNotBlank() }
        // If it's just "TITLE" and "by AUTHOR", skip title page
        if (lines.size <= 2 && lines.any { it.startsWith("by ", ignoreCase = true) || it.startsWith("written by", ignoreCase = true) }) {
            return false
        }
        return true
    }

    private fun extractPrologueTitle(prologue: String): String {
        val firstLine = prologue.lines().firstOrNull { it.isNotBlank() }?.trim().orEmpty()
        return if (firstLine.length in 4..60 && firstLine.all { it.isLetterOrDigit() || it.isWhitespace() || it in "-:–" }) {
            firstLine.uppercase()
        } else {
            "OPENING / PROLOGUE"
        }
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
