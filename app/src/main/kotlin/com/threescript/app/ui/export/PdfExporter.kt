package com.threescript.app.ui.export

import com.threescript.shared.model.NarrativeTrack
import com.threescript.shared.model.VectorPoint
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.PDPage
import org.apache.pdfbox.pdmodel.PDPageContentStream
import org.apache.pdfbox.pdmodel.common.PDRectangle
import org.apache.pdfbox.pdmodel.font.PDType1Font
import org.apache.pdfbox.pdmodel.font.Standard14Fonts
import org.apache.pdfbox.pdmodel.graphics.color.PDColor
import org.apache.pdfbox.pdmodel.graphics.color.PDDeviceRGB
import java.io.File
import kotlin.math.*

/**
 * PdfExporter — generates a multi-page "Script Doctor Report" PDF from a
 * [NarrativeTrack] produced by the 3D Script Studio engine.
 *
 * Page layout:
 *   Page 1 — Cover: project title, pacing rating, key metrics, doctor verdict
 *   Page 2 — Axis trajectory charts: X / Y / Z scores plotted over scene time
 *   Page 3+ — Scene-by-scene beat table (paginated at ~28 rows per page)
 *
 * Dependency: Apache PDFBox 3.x (already declared in app/build.gradle.kts).
 *
 * This object has zero dependency on Compose or the UI layer. It is safe to
 * call from any coroutine context (typically Dispatchers.IO).
 */
object PdfExporter {

    // ── Page & margin constants (all in points, 1 pt = 1/72 inch) ────────────
    private val PAGE = PDRectangle.A4                   // 595 × 842 pt
    private const val ML = 48f                          // left margin
    private const val MR = 48f                          // right margin
    private const val MT = 56f                          // top margin
    private const val MB = 44f                          // bottom margin
    private val W get() = PAGE.width - ML - MR          // usable width  ≈ 499 pt
    private val H get() = PAGE.height - MT - MB         // usable height ≈ 742 pt

    // ── Fonts (Standard 14, always embedded subset-free) ─────────────────────
    private val FONT_BOLD    get() = PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD)
    private val FONT_REG     get() = PDType1Font(Standard14Fonts.FontName.HELVETICA)
    private val FONT_OBL     get() = PDType1Font(Standard14Fonts.FontName.HELVETICA_OBLIQUE)

    // ── Design palette (RGB, matching the app's Studio Obsidian Slate theme) ─
    private val C_BG_PAGE  = rgb(0.04f, 0.04f, 0.06f)   // near-black page
    private val C_SURFACE  = rgb(0.07f, 0.07f, 0.09f)   // card surface
    private val C_BORDER   = rgb(0.13f, 0.16f, 0.22f)   // subtle border
    private val C_TEXT_HI  = rgb(0.97f, 0.98f, 0.99f)   // TextHigh
    private val C_TEXT_MID = rgb(0.58f, 0.64f, 0.72f)   // TextMid
    private val C_TEXT_MUT = rgb(0.39f, 0.45f, 0.54f)   // TextMuted
    private val C_BRAND    = rgb(0.39f, 0.40f, 0.95f)   // Brand indigo
    private val C_PLOT     = rgb(0.96f, 0.25f, 0.37f)   // AxisPlot (X)
    private val C_CHAR     = rgb(0.06f, 0.72f, 0.51f)   // AxisChar (Y)
    private val C_EMOT     = rgb(0.02f, 0.71f, 0.83f)   // AxisEmot (Z)
    private val C_DEAD     = rgb(0.94f, 0.27f, 0.27f)   // FlagDead
    private val C_PHASE    = mapOf(
        "Opening"                  to rgb(0.22f, 0.75f, 0.95f),
        "Rising Action"            to rgb(0.39f, 0.40f, 0.95f),
        "Complication"             to rgb(0.96f, 0.63f, 0.25f),
        "High-Stakes Turning Point" to rgb(0.96f, 0.25f, 0.37f),
        "Character Revelation"     to rgb(0.06f, 0.72f, 0.51f),
        "Climax"                   to rgb(0.94f, 0.27f, 0.27f),
        "Resolution"               to rgb(0.22f, 0.75f, 0.95f),
    )

    // ─────────────────────────────────────────────────────────────────────────
    // Public API
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Export [track] to [outputFile] as a PDF Script Doctor Report.
     *
     * @param track         The narrative track returned by the engine
     * @param projectTitle  User's project name (shown on cover page)
     * @param outputFile    Destination .pdf file (will be created or overwritten)
     */
    fun export(track: NarrativeTrack, projectTitle: String, outputFile: File) {
        PDDocument().use { doc ->
            buildCoverPage(doc, track, projectTitle)
            buildChartPage(doc, track)
            buildSceneTablePages(doc, track)
            doc.save(outputFile)
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Page 1 — Cover
    // ─────────────────────────────────────────────────────────────────────────

    private fun buildCoverPage(doc: PDDocument, track: NarrativeTrack, projectTitle: String) {
        val page = newPage(doc)
        val cs = PDPageContentStream(doc, page)
        val s = track.summary

        // Full-bleed dark background
        fillRect(cs, C_BG_PAGE, 0f, 0f, PAGE.width, PAGE.height)

        var y = PAGE.height - MT

        // ── Logo line ──────────────────────────────────────────────────────
        text(cs, FONT_BOLD, 9f, C_BRAND, ML, y, "3D SCRIPT STUDIO")
        y -= 10f
        hRule(cs, C_BORDER, ML, y, W)
        y -= 32f

        // ── Project title ──────────────────────────────────────────────────
        textWrapped(cs, FONT_BOLD, 26f, C_TEXT_HI, ML, y, W, projectTitle.uppercase(), lineGap = 30f)
        y -= 36f
        text(cs, FONT_REG, 11f, C_TEXT_MID, ML, y, "Script Doctor Report  ·  3D Narrative Vector Analysis")
        y -= 38f
        hRule(cs, C_BORDER, ML, y, W)
        y -= 28f

        // ── Key metric row (4 tiles) ───────────────────────────────────────
        val tileW = W / 4f
        val tileH = 66f
        val tiles = listOf(
            Triple("SCENES",            s.sceneCount.toString(),            "one point per scene"),
            Triple("SCENE MOVEMENT",    String.format("%.1f", s.totalDistance), "3D score distance"),
            Triple("DEAD ZONES",        s.deadZoneCount.toString(),         "low-movement scenes"),
            Triple("CLIMAX",            "Scene ${s.climaxSceneIndex}",      "highest plot + emotion"),
        )
        tiles.forEachIndexed { i, (label, value, sub) ->
            val tx = ML + i * tileW
            fillRect(cs, C_SURFACE, tx, y - tileH, tileW - 2f, tileH)
            strokeRect(cs, C_BORDER, tx, y - tileH, tileW - 2f, tileH, 0.5f)
            text(cs, FONT_BOLD, 7f, C_TEXT_MUT, tx + 10f, y - 13f, label)
            text(cs, FONT_BOLD, 20f, C_TEXT_HI,  tx + 10f, y - 36f, value)
            text(cs, FONT_REG,  8f, C_TEXT_MUT, tx + 10f, y - 48f, sub)
        }
        y -= tileH + 26f

        // ── Pacing rating pill ─────────────────────────────────────────────
        val ratingLabel = "PACING: ${s.pacingRating.uppercase()}"
        val pillW = stringWidth(FONT_BOLD, 9f, ratingLabel) + 20f
        fillRoundRect(cs, C_BRAND.copy(alpha = 0.18f), ML, y - 18f, pillW, 20f, 4f)
        text(cs, FONT_BOLD, 9f, C_BRAND, ML + 10f, y - 12f, ratingLabel)
        y -= 36f

        // ── Axis regression summary ────────────────────────────────────────
        hRule(cs, C_BORDER, ML, y, W)
        y -= 22f
        text(cs, FONT_BOLD, 8f, C_TEXT_MUT, ML, y, "AXIS TREND ANALYSIS")
        y -= 16f

        val axes = listOf(
            Triple("X  Plot Progression",    s.xStats, C_PLOT),
            Triple("Y  Character Arc",        s.yStats, C_CHAR),
            Triple("Z  Emotional Flux",       s.zStats, C_EMOT),
        )
        axes.forEach { (label, stats, color) ->
            val slope = stats.regressionSlope
            val trend = when {
                slope > 0.15  -> "↑ Rising  (${String.format("%+.2f", slope)}/scene)"
                slope < -0.15 -> "↓ Falling (${String.format("%+.2f", slope)}/scene)"
                else          -> "→ Flat    (${String.format("%+.2f", slope)}/scene)"
            }
            val rowText = "$label   mean ${String.format("%.1f", stats.mean)}  ·  $trend"
            text(cs, FONT_REG, 10f, color, ML, y, rowText)
            y -= 16f
        }
        y -= 14f

        // ── Doctor Verdict box ─────────────────────────────────────────────
        hRule(cs, C_BORDER, ML, y, W)
        y -= 22f
        text(cs, FONT_BOLD, 8f, C_TEXT_MUT, ML, y, "SCRIPT DOCTOR VERDICT")
        y -= 16f

        val verdictLines = wrapText(s.doctorVerdict, FONT_REG, 11f, W - 24f)
        val boxH = verdictLines.size * 15f + 24f
        fillRect(cs, C_SURFACE, ML, y - boxH, W, boxH)
        strokeRect(cs, C_BORDER, ML, y - boxH, W, boxH, 0.5f)
        var vy = y - 14f
        verdictLines.forEach { line ->
            text(cs, FONT_REG, 11f, C_TEXT_HI, ML + 12f, vy, line)
            vy -= 15f
        }
        y -= boxH + 22f

        // ── Dead zone call-outs (if any) ──────────────────────────────────
        if (s.deadZoneCount > 0) {
            hRule(cs, C_BORDER, ML, y, W)
            y -= 22f
            text(cs, FONT_BOLD, 8f, C_TEXT_MUT, ML, y, "DEAD ZONE CALL-OUTS")
            y -= 16f
            val dzList = s.deadZoneIndices.joinToString("  ·  ") { "Scene $it" }
            text(cs, FONT_REG, 10f, C_DEAD, ML, y, dzList)
            y -= 16f
            text(cs, FONT_OBL, 9f, C_TEXT_MUT, ML, y,
                "Review the above scenes for stalled plot, character, or emotional progression.")
        }

        // ── Footer ─────────────────────────────────────────────────────────
        text(cs, FONT_REG, 8f, C_TEXT_MUT, ML, MB - 8f,
            "Generated by 3D Script Studio")
        text(cs, FONT_REG, 8f, C_TEXT_MUT, PAGE.width - MR - 60f, MB - 8f, "Page 1")

        cs.close()
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Page 2 — Axis Trajectory Charts (X, Y, Z sparklines)
    // ─────────────────────────────────────────────────────────────────────────

    private fun buildChartPage(doc: PDDocument, track: NarrativeTrack) {
        val page = newPage(doc)
        val cs = PDPageContentStream(doc, page)
        val points = track.points

        fillRect(cs, C_BG_PAGE, 0f, 0f, PAGE.width, PAGE.height)

        var y = PAGE.height - MT
        text(cs, FONT_BOLD, 9f, C_BRAND, ML, y, "3D SCRIPT STUDIO")
        y -= 10f
        hRule(cs, C_BORDER, ML, y, W)
        y -= 26f
        text(cs, FONT_BOLD, 16f, C_TEXT_HI, ML, y, "Axis Trajectory Charts")
        y -= 14f
        text(cs, FONT_REG, 10f, C_TEXT_MID, ML, y,
            "Each chart plots the raw AI score for one narrative dimension across all scenes.")
        y -= 28f

        val chartH = 110f
        val chartGap = 30f

        data class AxisDef(val label: String, val color: PDColor, val values: List<Double>)

        val axisDefs = listOf(
            AxisDef("X  —  PLOT PROGRESSION",   C_PLOT, points.map { it.x }),
            AxisDef("Y  —  CHARACTER ARC",       C_CHAR, points.map { it.y }),
            AxisDef("Z  —  EMOTIONAL FLUX",      C_EMOT, points.map { it.z }),
        )

        axisDefs.forEach { (label, color, values) ->
            y -= 2f
            drawSparklineChart(cs, label, color, values, ML, y, W, chartH, points)
            y -= chartH + chartGap
        }

        // ── Legend ─────────────────────────────────────────────────────────
        y -= 4f
        hRule(cs, C_BORDER, ML, y, W)
        y -= 18f
        text(cs, FONT_BOLD, 8f, C_TEXT_MUT, ML, y, "LEGEND")
        y -= 14f
        val legendItems = listOf(
            C_PLOT to "Plot Progression (X)",
            C_CHAR to "Character Arc (Y)",
            C_EMOT to "Emotional Flux (Z)",
            C_DEAD to "Dead zone segment",
        )
        var lx = ML
        legendItems.forEach { (c, label) ->
            fillRect(cs, c, lx, y - 4f, 10f, 6f)
            text(cs, FONT_REG, 8.5f, C_TEXT_MID, lx + 14f, y, label)
            lx += stringWidth(FONT_REG, 8.5f, label) + 30f
        }

        // Footer
        text(cs, FONT_REG, 8f, C_TEXT_MUT, ML, MB - 8f,
            "Generated by 3D Script Studio")
        text(cs, FONT_REG, 8f, C_TEXT_MUT, PAGE.width - MR - 60f, MB - 8f, "Page 2")

        cs.close()
    }

    /**
     * Draw a labelled sparkline chart for one axis.
     * Y-axis range is always 0–10 (matching the engine's scoring range).
     */
    private fun drawSparklineChart(
        cs: PDPageContentStream,
        label: String,
        color: PDColor,
        values: List<Double>,
        x: Float, topY: Float,
        chartW: Float, chartH: Float,
        points: List<VectorPoint>,
    ) {
        val bodyH = chartH - 22f   // reserve 22pt for header label
        val bodyY = topY - chartH  // bottom-left of chart body

        // Background + border
        fillRect(cs, C_SURFACE, x, bodyY, chartW, bodyH + 2f)
        strokeRect(cs, C_BORDER, x, bodyY, chartW, bodyH + 2f, 0.5f)

        // Label
        text(cs, FONT_BOLD, 8f, color, x, topY - 14f, label)

        // Y-axis ticks (0, 5, 10)
        listOf(0.0, 5.0, 10.0).forEach { v ->
            val fy = bodyY + (v / 10.0 * bodyH).toFloat()
            cs.setStrokingColor(C_BORDER)
            cs.setLineWidth(0.4f)
            cs.moveTo(x, fy)
            cs.lineTo(x + chartW, fy)
            cs.stroke()
            text(cs, FONT_REG, 7f, C_TEXT_MUT, x - 14f, fy - 3f, v.toInt().toString())
        }

        if (values.size < 2) return

        val stepX = chartW / (values.size - 1).toFloat()

        // Fill area under curve (very subtle)
        cs.setNonStrokingColor(color.copy(alpha = 0.08f))
        cs.moveTo(x, bodyY)
        values.forEachIndexed { i, v ->
            val px = x + i * stepX
            val py = bodyY + (v / 10.0 * bodyH).toFloat()
            cs.lineTo(px, py)
        }
        cs.lineTo(x + (values.size - 1) * stepX, bodyY)
        cs.closePath()
        cs.fill()

        // Line
        values.forEachIndexed { i, v ->
            val px = x + i * stepX
            val py = bodyY + (v / 10.0 * bodyH).toFloat()
            val isDz = points.getOrNull(i)?.isDeadZone == true
            val lineColor = if (isDz) C_DEAD else color
            cs.setStrokingColor(lineColor)
            cs.setLineWidth(1.6f)
            if (i == 0) cs.moveTo(px, py) else cs.lineTo(px, py)
        }
        cs.stroke()

        // Dots
        values.forEachIndexed { i, v ->
            val px = x + i * stepX
            val py = bodyY + (v / 10.0 * bodyH).toFloat()
            val isDz = points.getOrNull(i)?.isDeadZone == true
            val dotColor = if (isDz) C_DEAD else color
            cs.setNonStrokingColor(dotColor)
            cs.addRect(px - 2f, py - 2f, 4f, 4f)
            cs.fill()
            // Scene number label every ~10 scenes or for small counts
            if (values.size <= 20 || i % (values.size / 10).coerceAtLeast(1) == 0) {
                text(cs, FONT_REG, 6f, C_TEXT_MUT, px - 3f, bodyY - 9f, "${i + 1}")
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Page 3+ — Scene Beat Table
    // ─────────────────────────────────────────────────────────────────────────

    private fun buildSceneTablePages(doc: PDDocument, track: NarrativeTrack) {
        val points = track.points
        val rowH = 22f
        val headerH = 24f
        val availH = H - 60f   // subtract page header from usable height
        val rowsPerPage = ((availH - headerH) / rowH).toInt().coerceAtLeast(1)

        val chunks = points.chunked(rowsPerPage)
        chunks.forEachIndexed { pageIdx, chunk ->
            val page = newPage(doc)
            val cs = PDPageContentStream(doc, page)
            fillRect(cs, C_BG_PAGE, 0f, 0f, PAGE.width, PAGE.height)

            var y = PAGE.height - MT
            text(cs, FONT_BOLD, 9f, C_BRAND, ML, y, "3D SCRIPT STUDIO")
            y -= 10f
            hRule(cs, C_BORDER, ML, y, W)
            y -= 22f

            val pageLabel = if (chunks.size == 1) "Scene Beat Table" else
                "Scene Beat Table  (${pageIdx * rowsPerPage + 1}–${pageIdx * rowsPerPage + chunk.size})"
            text(cs, FONT_BOLD, 14f, C_TEXT_HI, ML, y, pageLabel)
            y -= 22f

            // ── Column definitions ─────────────────────────────────────────
            //  #    %    Title               Phase         Δd    X    Y    Z   Beat type
            val cols = listOf(
                ColumnDef("#",          32f,  TextAlign.CENTER),
                ColumnDef("%",          34f,  TextAlign.CENTER),
                ColumnDef("Title",      130f, TextAlign.LEFT),
                ColumnDef("Phase",      78f,  TextAlign.LEFT),
                ColumnDef("Δd",         34f,  TextAlign.CENTER),
                ColumnDef("X",          28f,  TextAlign.CENTER),
                ColumnDef("Y",          28f,  TextAlign.CENTER),
                ColumnDef("Z",          28f,  TextAlign.CENTER),
                ColumnDef("Beat Type",  W - 32f - 34f - 130f - 78f - 34f - 28f - 28f - 28f, TextAlign.LEFT),
            )

            // Header row
            fillRect(cs, C_SURFACE, ML, y - headerH, W, headerH)
            strokeRect(cs, C_BORDER, ML, y - headerH, W, headerH, 0.5f)
            var cx2 = ML
            cols.forEach { col ->
                val tx = when (col.align) {
                    TextAlign.CENTER -> cx2 + col.width / 2f - stringWidth(FONT_BOLD, 7f, col.header) / 2f
                    TextAlign.LEFT   -> cx2 + 5f
                }
                text(cs, FONT_BOLD, 7f, C_TEXT_MUT, tx, y - 9f, col.header)
                text(cs, FONT_BOLD, 7f, C_TEXT_MUT, tx, y - 18f, "")  // spacer
                cx2 += col.width
            }
            y -= headerH

            // Data rows
            chunk.forEach { pt ->
                val sceneCount = track.summary.sceneCount
                val pct = if (sceneCount <= 1) 0.0
                          else (pt.sceneIndex - 1).toDouble() / (sceneCount - 1) * 100.0
                val phaseColor = C_PHASE[pt.storyPhase] ?: C_BRAND
                val rowBg = if (pt.isDeadZone) C_DEAD.copy(alpha = 0.07f) else C_BG_PAGE

                fillRect(cs, rowBg, ML, y - rowH, W, rowH)

                val dzVal = if (pt.sceneIndex == 1) "—" else String.format("%.2f", pt.deltaD)
                val dzColor = if (pt.isDeadZone) C_DEAD else C_TEXT_MID
                val rowValues: List<Pair<String, PDColor>> = listOf(
                    pt.sceneIndex.toString()       to C_TEXT_MUT,
                    "${String.format("%.0f", pct)}%" to C_TEXT_MUT,
                    pt.sceneTitle.take(28)         to C_TEXT_HI,
                    pt.storyPhase.take(18)         to phaseColor,
                    dzVal                          to dzColor,
                    String.format("%.1f", pt.x)    to C_PLOT,
                    String.format("%.1f", pt.y)    to C_CHAR,
                    String.format("%.1f", pt.z)    to C_EMOT,
                    pt.beatType.take(34)           to if (pt.isDeadZone) C_DEAD else C_TEXT_MID,
                )

                var rx = ML
                rowValues.zip(cols).forEach { (pair, col) ->
                    val (value, color) = pair
                    val tx = when (col.align) {
                        TextAlign.CENTER -> rx + col.width / 2f - stringWidth(FONT_REG, 8f, value) / 2f
                        TextAlign.LEFT   -> rx + 5f
                    }
                    text(cs, FONT_REG, 8f, color, tx, y - 14f, value)
                    rx += col.width

                }

                // row separator
                cs.setStrokingColor(C_BORDER)
                cs.setLineWidth(0.3f)
                cs.moveTo(ML, y - rowH)
                cs.lineTo(ML + W, y - rowH)
                cs.stroke()

                y -= rowH
            }

            // Footer
            text(cs, FONT_REG, 8f, C_TEXT_MUT, ML, MB - 8f,
                "Generated by 3D Script Studio")
            text(cs, FONT_REG, 8f, C_TEXT_MUT, PAGE.width - MR - 60f, MB - 8f,
                "Page ${pageIdx + 3}")

            cs.close()
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // PDFBox helpers
    // ─────────────────────────────────────────────────────────────────────────

    private fun newPage(doc: PDDocument): PDPage {
        val page = PDPage(PAGE)
        doc.addPage(page)
        return page
    }

    private fun fillRect(cs: PDPageContentStream, color: PDColor, x: Float, y: Float, w: Float, h: Float) {
        cs.setNonStrokingColor(color)
        cs.addRect(x, y, w, h)
        cs.fill()
    }

    private fun strokeRect(cs: PDPageContentStream, color: PDColor, x: Float, y: Float, w: Float, h: Float, lineW: Float) {
        cs.setStrokingColor(color)
        cs.setLineWidth(lineW)
        cs.addRect(x, y, w, h)
        cs.stroke()
    }

    /** Approximate rounded-rect fill by drawing a filled rect (PDFBox 3 content streams don't expose a direct roundrect primitive). */
    private fun fillRoundRect(cs: PDPageContentStream, color: PDColor, x: Float, y: Float, w: Float, h: Float, @Suppress("UNUSED_PARAMETER") radius: Float) {
        // For the pill shape we just use a regular rect; the visual difference is negligible in print.
        fillRect(cs, color, x, y, w, h)
    }

    private fun hRule(cs: PDPageContentStream, color: PDColor, x: Float, y: Float, w: Float) {
        cs.setStrokingColor(color)
        cs.setLineWidth(0.5f)
        cs.moveTo(x, y)
        cs.lineTo(x + w, y)
        cs.stroke()
    }

    private fun text(cs: PDPageContentStream, font: PDType1Font, size: Float, color: PDColor, x: Float, y: Float, str: String) {
        if (str.isEmpty()) return
        val safe = str.map { if (it.code in 32..126) it else '?' }.joinToString("")
        cs.beginText()
        cs.setFont(font, size)
        cs.setNonStrokingColor(color)
        cs.newLineAtOffset(x, y)
        cs.showText(safe)
        cs.endText()
    }

    /** Wrap and draw text, returning the number of lines drawn. */
    private fun textWrapped(
        cs: PDPageContentStream,
        font: PDType1Font,
        size: Float,
        color: PDColor,
        x: Float, topY: Float,
        maxW: Float,
        str: String,
        lineGap: Float = size + 4f,
    ) {
        var y = topY
        wrapText(str, font, size, maxW).forEach { line ->
            text(cs, font, size, color, x, y, line)
            y -= lineGap
        }
    }

    private fun stringWidth(font: PDType1Font, size: Float, str: String): Float {
        val safe = str.map { if (it.code in 32..126) it else ' ' }.joinToString("")
        return try { font.getStringWidth(safe) / 1000f * size } catch (_: Exception) { safe.length * size * 0.55f }
    }

    private fun wrapText(str: String, font: PDType1Font, size: Float, maxW: Float): List<String> {
        val words = str.split(" ")
        val lines = mutableListOf<String>()
        var current = StringBuilder()
        words.forEach { word ->
            val probe = if (current.isEmpty()) word else "$current $word"
            if (stringWidth(font, size, probe) <= maxW) {
                current = StringBuilder(probe)
            } else {
                if (current.isNotEmpty()) lines += current.toString()
                current = StringBuilder(word)
            }
        }
        if (current.isNotEmpty()) lines += current.toString()
        return lines
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Color factory helpers
    // ─────────────────────────────────────────────────────────────────────────

    private fun rgb(r: Float, g: Float, b: Float): PDColor =
        PDColor(floatArrayOf(r, g, b), PDDeviceRGB.INSTANCE)

    private fun PDColor.copy(alpha: Float): PDColor {
        // PDFBox's PDDeviceRGB doesn't carry alpha; we blend toward page BG manually.
        val bg = floatArrayOf(0.04f, 0.04f, 0.06f)
        val c = components
        return PDColor(
            floatArrayOf(
                c[0] * alpha + bg[0] * (1f - alpha),
                c[1] * alpha + bg[1] * (1f - alpha),
                c[2] * alpha + bg[2] * (1f - alpha),
            ),
            PDDeviceRGB.INSTANCE,
        )
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Internal data
    // ─────────────────────────────────────────────────────────────────────────

    private enum class TextAlign { LEFT, CENTER }

    private data class ColumnDef(val header: String, val width: Float, val align: TextAlign)
}
