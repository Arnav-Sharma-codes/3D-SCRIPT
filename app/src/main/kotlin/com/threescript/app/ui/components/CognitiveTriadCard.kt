package com.threescript.app.ui.components

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.threescript.app.ui.*
import com.threescript.shared.model.cognitive.*

/**
 * Audience Predictive Cognitive Overlay Card
 *
 * Simulates human cognitive processing across three temporal dimensions:
 *   - Recollection: Past context, working memory load, recall retention %
 *   - Perception: Present emotional flux %, attentional focus %, cognitive strain %
 *   - Anticipation: Future expectation vector, divergence gap, predictive hypotheses %
 */
@Composable
fun CognitiveTriadCard(
    triadState: CognitiveTriadState?,
    onJumpToScene: (Int) -> Unit,
    onHoverCallbackScene: (Int?) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (triadState == null) {
        Surface(
            color = BgCard,
            shape = RoundedCornerShape(8.dp),
            border = BorderStroke(1.dp, BorderSubtle),
            modifier = modifier.fillMaxWidth(),
        ) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("🧠 AUDIENCE COGNITIVE OVERLAY", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Brand)
                Text("Select a scene to inspect audience recollection, perception, and anticipation modeling.", color = TextMuted, fontSize = 11.sp)
            }
        }
        return
    }

    var selectedTemporalTab by remember { mutableStateOf(0) } // 0=Recollection, 1=Perception, 2=Anticipation

    Surface(
        color = BgCard,
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, BorderSubtle),
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            // Header
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("🧠 AUDIENCE COGNITIVE OVERLAY", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Brand)
                Spacer(Modifier.weight(1f))
                CognitiveStateBadge(triadState.perception.audienceMentalState)
            }

            // Warnings banner if any
            if (triadState.cognitiveWarnings.isNotEmpty()) {
                val warning = triadState.cognitiveWarnings.first()
                val warnColor = if (warning.level == "CRITICAL") FlagDead else Color(0xFFF59E0B)
                Surface(
                    color = warnColor.copy(alpha = 0.12f),
                    border = BorderStroke(1.dp, warnColor.copy(alpha = 0.45f)),
                    shape = RoundedCornerShape(6.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Warning, contentDescription = null, tint = warnColor, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(warning.message, color = TextHigh, fontSize = 10.sp, modifier = Modifier.weight(1f))
                        val affected = warning.affectedSceneIndex
                        if (affected != null) {
                            TextButton(
                                onClick = { onJumpToScene(affected) },
                                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text("Scene $affected →", color = warnColor, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }

            // 3 Temporal Dimension Tabs
            Row(
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(6.dp)).background(BgBase).padding(2.dp),
                horizontalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                TemporalTabButton("⏪ Past (Recall)", selectedTemporalTab == 0, Modifier.weight(1f)) { selectedTemporalTab = 0 }
                TemporalTabButton("👁️ Present (Sense)", selectedTemporalTab == 1, Modifier.weight(1f)) { selectedTemporalTab = 1 }
                TemporalTabButton("🔮 Future (Expect)", selectedTemporalTab == 2, Modifier.weight(1f)) { selectedTemporalTab = 2 }
            }

            // Tab Content
            when (selectedTemporalTab) {
                0 -> RecollectionSection(triadState.recollection, onJumpToScene, onHoverCallbackScene)
                1 -> PerceptionSection(triadState.perception)
                2 -> AnticipationSection(triadState.anticipation, onJumpToScene)
            }
        }
    }
}

@Composable
private fun TemporalTabButton(
    label: String,
    isSelected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(4.dp))
            .background(if (isSelected) Brand.copy(alpha = 0.25f) else Color.Transparent)
            .clickable { onClick() }
            .padding(vertical = 5.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            color = if (isSelected) Brand else TextMuted,
            fontSize = 9.5.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
        )
    }
}

// ── 1. RECOLLECTION (PAST) ──────────────────────────────────────────────────

@Composable
private fun RecollectionSection(
    recollection: RecollectionState,
    onJumpToScene: (Int) -> Unit,
    onHoverCallbackScene: (Int?) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        // Working memory retention gauge
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("Audience Recall Retention", color = TextMid, fontSize = 10.sp)
            val retColor = if (recollection.recallRetentionPct >= 70) Color(0xFF10B981) else if (recollection.recallRetentionPct >= 45) Color(0xFFF59E0B) else FlagDead
            Text("${recollection.recallRetentionPct}%", color = retColor, fontSize = 10.sp, fontWeight = FontWeight.Bold)
        }
        ProgressBar(recollection.recallRetentionPct / 100f, TokenSetupCyan)

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Memory Load: ${recollection.memoryLoadScore}/10", color = TextMuted, fontSize = 9.sp)
            Text("${recollection.activeSetupsCount} Open Setups", color = TokenSetupCyan, fontSize = 9.sp, fontWeight = FontWeight.SemiBold)
        }

        Text(recollection.contextSummary, color = TextMid, fontSize = 10.sp, lineHeight = 14.sp)

        // Active setups
        if (recollection.activeSetups.isNotEmpty()) {
            Text("ACTIVE SEEDS IN WORKING MEMORY:", color = TextMuted, fontSize = 8.5.sp, fontWeight = FontWeight.Bold)
            recollection.activeSetups.forEach { setup ->
                Surface(
                    color = BgBase,
                    shape = RoundedCornerShape(4.dp),
                    border = BorderStroke(0.5.dp, TokenSetupCyan.copy(alpha = 0.4f)),
                    modifier = Modifier.fillMaxWidth().clickable { onJumpToScene(setup.setupSceneIndex) }
                ) {
                    Row(Modifier.padding(6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(6.dp).clip(RoundedCornerShape(3.dp)).background(TokenSetupCyan))
                        Spacer(Modifier.width(6.dp))
                        Text("Scene ${setup.setupSceneIndex}: ${setup.headline}", color = TextHigh, fontSize = 9.5.sp, maxLines = 1, modifier = Modifier.weight(1f))
                        Text("Inspect →", color = TokenSetupCyan, fontSize = 8.5.sp)
                    }
                }
            }
        }

        // Callbacks resolved in this scene
        if (recollection.resolvedCallbacks.isNotEmpty()) {
            Text("PAYOFFS LINKED IN THIS SCENE:", color = TextMuted, fontSize = 8.5.sp, fontWeight = FontWeight.Bold)
            recollection.resolvedCallbacks.forEach { cb ->
                Surface(
                    color = TokenSubversionAmber.copy(alpha = 0.10f),
                    shape = RoundedCornerShape(4.dp),
                    border = BorderStroke(1.dp, TokenSubversionAmber.copy(alpha = 0.5f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            onJumpToScene(cb.setupSceneIndex)
                            onHoverCallbackScene(cb.setupSceneIndex)
                        }
                ) {
                    Row(Modifier.padding(6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Link, contentDescription = null, tint = TokenSubversionAmber, modifier = Modifier.size(12.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Callback to Scene ${cb.setupSceneIndex}: ${cb.description}", color = TextHigh, fontSize = 9.5.sp, modifier = Modifier.weight(1f))
                        Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, tint = TokenSubversionAmber, modifier = Modifier.size(10.dp))
                    }
                }
            }
        }
    }
}

// ── 2. PERCEPTION (PRESENT) ─────────────────────────────────────────────────

@Composable
private fun PerceptionSection(perception: PerceptionState) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        MetricGaugeRow("Visceral Emotional Flux", "${perception.emotionalFluxPct}%", perception.emotionalFluxPct / 100f, AxisEmot)
        MetricGaugeRow("Attentional Immersion", "${perception.attentionalFocusPct}%", perception.attentionalFocusPct / 100f, AxisChar)
        MetricGaugeRow("Cognitive Exposition Load", "${perception.cognitiveStrainPct}%", perception.cognitiveStrainPct / 100f, TokenOverloadPurple)

        Surface(
            color = BgBase,
            shape = RoundedCornerShape(4.dp),
            border = BorderStroke(0.5.dp, BorderSubtle),
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
        ) {
            Column(Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("CURRENT AUDIENCE RECEPTION STATE", fontSize = 8.5.sp, fontWeight = FontWeight.Bold, color = TextMuted)
                Text(perception.audienceMentalState, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = TextHigh)
            }
        }
    }
}

// ── 3. ANTICIPATION (FUTURE) ────────────────────────────────────────────────

@Composable
private fun AnticipationSection(
    anticipation: AnticipationState,
    onJumpToScene: (Int) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        // Divergence expectation gap
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Audience Expectation Divergence", color = TextMid, fontSize = 10.sp)
                if (anticipation.isSubversiveTwist) {
                    Spacer(Modifier.width(6.dp))
                    Surface(
                        color = FlagDead.copy(alpha = 0.15f),
                        border = BorderStroke(1.dp, FlagDead),
                        shape = RoundedCornerShape(3.dp),
                    ) {
                        Text("SUBVERSIVE TWIST", color = FlagDead, fontSize = 7.5.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp))
                    }
                }
            }
            Text("Δ ${String.format("%.2f", anticipation.divergenceGap)}", color = if (anticipation.isSubversiveTwist) FlagDead else TextHigh, fontSize = 10.sp, fontWeight = FontWeight.Bold)
        }
        ProgressBar((anticipation.divergenceGap / 4.0).toFloat().coerceIn(0f, 1f), if (anticipation.isSubversiveTwist) FlagDead else TokenSubversionAmber)

        // Vector coordinates
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Projected Vector: (${anticipation.expectationVector.x}, ${anticipation.expectationVector.y}, ${anticipation.expectationVector.z})", color = TextMuted, fontSize = 9.sp)
            Text("Subversion: ${anticipation.subversionScore}/10", color = TokenSubversionAmber, fontSize = 9.sp, fontWeight = FontWeight.SemiBold)
        }

        // Predicted Hypotheses
        Text("AUDIENCE PREDICTIVE HYPOTHESES FOR NEXT SCENES:", color = TextMuted, fontSize = 8.5.sp, fontWeight = FontWeight.Bold)
        anticipation.topHypotheses.forEach { hyp ->
            Surface(
                color = BgBase,
                shape = RoundedCornerShape(4.dp),
                border = BorderStroke(0.5.dp, BorderSubtle),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(Modifier.padding(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(hyp.statement, color = TextHigh, fontSize = 9.5.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                    Spacer(Modifier.width(8.dp))
                    Text("${hyp.probabilityPct}%", color = Brand, fontSize = 9.5.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

// ── Shared Helpers ──────────────────────────────────────────────────────────

@Composable
private fun MetricGaugeRow(label: String, valueText: String, fraction: Float, color: Color) {
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, color = TextMid, fontSize = 9.5.sp)
            Text(valueText, color = color, fontSize = 9.5.sp, fontWeight = FontWeight.Bold)
        }
        ProgressBar(fraction, color)
    }
}

@Composable
private fun ProgressBar(fraction: Float, color: Color) {
    Box(Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)).background(BorderSubtle)) {
        Box(Modifier.fillMaxWidth(fraction.coerceIn(0f, 1f)).fillMaxHeight().background(color).clip(RoundedCornerShape(2.dp)))
    }
}

@Composable
private fun CognitiveStateBadge(state: String) {
    Surface(
        color = Brand.copy(alpha = 0.15f),
        border = BorderStroke(1.dp, Brand.copy(alpha = 0.5f)),
        shape = RoundedCornerShape(4.dp),
    ) {
        Text(state, color = Brand, fontSize = 8.5.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
    }
}
