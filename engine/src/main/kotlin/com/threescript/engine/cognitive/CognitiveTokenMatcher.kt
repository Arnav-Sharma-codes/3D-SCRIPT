package com.threescript.engine.cognitive

import com.threescript.shared.model.SceneBlock
import com.threescript.shared.model.cognitive.CognitiveHighlightToken
import com.threescript.shared.model.cognitive.CognitiveTokenType

/**
 * Scans screenplay scene text for cognitive markers:
 *   - Cyan/Blue: Setup tokens (narrative planting, Chekhov's guns, promises, mysteries)
 *   - Red/Amber: Subversion/Twist beats (breaking audience predictions, sudden betrayals, reversals)
 *   - Purple: Cognitive Overload beats (dense exposition dumps, rapid technical jargon)
 */
object CognitiveTokenMatcher {

    // ── 1. Setup / Planting Tokens (Cyan) ────────────────────────────────────
    private val SETUP_PATTERNS = listOf(
        Regex("""(?i)\b(?:promises?|swears?|vows?|agreed to|deal is|blood oath)\b""") to "Dramatic Promise / Vow: Audience registers a binding expectation.",
        Regex("""(?i)\b(?:tucks away|slips into (?:his|her|their) pocket|hides the|locks the|gun in the|safe contains|hidden key|secret drawer|envelope)\b""") to "Chekhov's Gun: Physical seed planted for future payoff.",
        Regex("""(?i)\b(?:who (?:is|was) (?:he|she|they)|what happened (?:to|at)|where is the|the real identity|nobody knows)\b""") to "Mystery Question: Active cognitive question open in audience memory.",
        Regex("""(?i)\b(?:bad feeling|warn you|don't trust|trap|it's dangerous|remember this|never forget)\b""") to "Foreshadowing: Emotional forewarning priming anticipation.",
        Regex("""(?i)\b(?:the protocol|the plan is|at exactly|the rendezvous|phase one)\b""") to "Procedural Plan: Structured framework for audience expectation.",
    )

    // ── 2. Subversion / Twist Tokens (Amber / Red) ───────────────────────────
    private val SUBVERSION_PATTERNS = listOf(
        Regex("""(?i)\b(?:betrays?|shoots (?:his|her|their) (?:partner|ally)|double agent|was working for|all along|it was (?:him|her|you))\b""") to "Radical Subversion: Core allegiance reversed, shattering prediction.",
        Regex("""(?i)\b(?:the vault is empty|it's not here|the body is gone|there was never|we're too late|it was a decoy)\b""") to "Expectation Gap: Goal state negated; audience hypothesis invalid.",
        Regex("""(?i)\b(?:not (?:my|your) (?:father|mother|brother|sister|son|daughter)|still alive|faked (?:his|her|their) death)\b""") to "Character Reversal: Biological / relational foundation inverted.",
        Regex("""(?i)\b(?:they were expecting us|it's an ambush|we've been set up|surrounded)\b""") to "Tactical Subversion: Audience safety expectation collapses.",
        Regex("""(?i)\b(?:suddenly|without warning|an explosion rips|the car violently spins|gunfire erupts)\b""") to "Sensory Shock: Abrupt disruption of perceived scene tempo.",
    )

    // ── 3. Cognitive Overload Tokens (Purple) ────────────────────────────────
    private val OVERLOAD_PATTERNS = listOf(
        Regex("""(?i)\b(?:quantum|temporal|algorithm|inversion|entropy|triangulation|sub-atomic|jurisdiction|extradition|conglomerate|subsidiary)\b""") to "Technical Exposition: Elevated processing load on narrative logic.",
        Regex("""(?i)\b(?:the history of|twenty years ago the|the treaty of|the committee decided|according to the records)\b""") to "Lore Dump: Dense backstory requiring high audience working memory.",
    )

    /**
     * Finds all cognitive tokens across all parsed scenes and links callbacks to prior setups.
     */
    fun matchTokens(scenes: List<SceneBlock>): List<CognitiveHighlightToken> {
        val tokens = mutableListOf<CognitiveHighlightToken>()
        val knownSetups = mutableListOf<Pair<Int, String>>() // SceneIndex to keyword

        scenes.forEach { scene ->
            val lines = scene.rawText.lines()
            lines.forEach { rawLine ->
                val line = rawLine.trim()
                if (line.length < 5) return@forEach

                // 1. Check Setups (Cyan)
                for ((regex, explanation) in SETUP_PATTERNS) {
                    val match = regex.find(line)
                    if (match != null) {
                        tokens += CognitiveHighlightToken(
                            type = CognitiveTokenType.SETUP_CYAN,
                            sceneIndex = scene.index,
                            tokenText = match.value,
                            lineSnippet = line.take(90),
                            explanation = explanation,
                            originatingSceneIndex = null,
                        )
                        knownSetups += scene.index to match.value.lowercase()
                        break
                    }
                }

                // 2. Check Subversions (Amber)
                for ((regex, explanation) in SUBVERSION_PATTERNS) {
                    val match = regex.find(line)
                    if (match != null) {
                        // Find if this subverts a previous setup
                        val matchingSetup = knownSetups.lastOrNull { it.first < scene.index }
                        tokens += CognitiveHighlightToken(
                            type = CognitiveTokenType.SUBVERSION_AMBER,
                            sceneIndex = scene.index,
                            tokenText = match.value,
                            lineSnippet = line.take(90),
                            explanation = explanation,
                            originatingSceneIndex = matchingSetup?.first,
                        )
                        break
                    }
                }

                // 3. Check Overload (Purple)
                // Overload occurs if dense technical terms appear or if a dialogue line is excessively verbose
                val isDenseLine = line.length > 220 && !line.startsWith("INT.") && !line.startsWith("EXT.")
                for ((regex, explanation) in OVERLOAD_PATTERNS) {
                    val match = regex.find(line)
                    if (match != null || isDenseLine) {
                        tokens += CognitiveHighlightToken(
                            type = CognitiveTokenType.OVERLOAD_PURPLE,
                            sceneIndex = scene.index,
                            tokenText = match?.value ?: "Dense Exposition",
                            lineSnippet = line.take(90),
                            explanation = if (isDenseLine) "Exposition Saturation: Unbroken wall of explanatory text." else explanation,
                            originatingSceneIndex = null,
                        )
                        break
                    }
                }
            }
        }
        return tokens
    }
}
