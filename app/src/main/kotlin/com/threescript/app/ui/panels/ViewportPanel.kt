package com.threescript.app.ui.panels

import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material.icons.filled.ZoomOut
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import com.threescript.app.AppState
import com.threescript.app.ui.*
import com.threescript.shared.model.NarrativeTrack
import com.threescript.shared.model.VectorPoint
import kotlin.math.*

@Composable
internal fun ViewportPanel(
    modifier: Modifier = Modifier,
    state: AppState,
    camera: GraphCamera,
    onEnterFullscreen: () -> Unit,
) {
    val track = state.track
    val points = track?.points.orEmpty()
    val selectPoint: (VectorPoint) -> Unit = { point -> state.selectedPoint = point }

    Column(modifier.background(BgBase)) {
        TrajectoryHeader()

        Row(Modifier.weight(0.58f).fillMaxWidth()) {
            Column(Modifier.weight(1f).fillMaxHeight()) {
                GraphToolbar(camera, onEnterFullscreen = onEnterFullscreen.takeIf { track != null })
                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    if (track == null || points.isEmpty()) {
                        EmptyGraphState()
                    } else {
                        Viewport3D(
                            track = track,
                            selectedPoint = state.selectedPoint,
                            onPointSelected = selectPoint,
                            camera = camera,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                }
            }
            VerticalRule()
            SceneInspector(
                point = state.selectedPoint,
                track = track,
                modifier = Modifier.width(248.dp).fillMaxHeight(),
            )
        }

        TrackMetrics(track)
        SceneMapHeader(track)
        SceneMap(
            points = points,
            selectedIndex = state.selectedPoint?.sceneIndex,
            onSelect = selectPoint,
            modifier = Modifier.weight(0.42f).fillMaxWidth(),
        )
    }
}

@Composable
internal fun FullscreenGraphView(
    track: NarrativeTrack,
    selectedPoint: VectorPoint?,
    camera: GraphCamera,
    onPointSelected: (VectorPoint) -> Unit,
    onExitFullscreen: () -> Unit,
) {
    Column(Modifier.fillMaxSize().background(BgBase)) {
        GraphToolbar(camera, onExitFullscreen = onExitFullscreen)
        Row(Modifier.weight(1f).fillMaxWidth()) {
            Viewport3D(
                track = track,
                selectedPoint = selectedPoint,
                onPointSelected = onPointSelected,
                camera = camera,
                modifier = Modifier.weight(1f).fillMaxHeight(),
            )
            VerticalRule()
            SceneInspector(point = selectedPoint, track = track, modifier = Modifier.width(300.dp).fillMaxHeight())
        }
    }
}

@Composable
private fun TrajectoryHeader() {
    Row(
        Modifier.fillMaxWidth().height(82.dp).background(BgBase)
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Eyebrow("STORY TRAJECTORY")
            Text("The screenplay in vector space", fontSize = 19.sp, fontWeight = FontWeight.Bold, color = TextHigh)
            Text(
                "One point per scene: plot, character, and emotion plotted in story time.",
                fontSize = 10.sp, color = TextMid, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
            AxisLegend("Plot", AxisPlot)
            AxisLegend("Character Arc", AxisChar)
            AxisLegend("Emotion", AxisEmot)
            AxisLegend("Time", AxisTime)
        }
    }
}

@Composable
private fun GraphToolbar(
    camera: GraphCamera,
    onEnterFullscreen: (() -> Unit)? = null,
    onExitFullscreen: (() -> Unit)? = null,
) {
    Row(
        Modifier.fillMaxWidth().height(36.dp).background(BgSurface)
            .padding(start = 14.dp, end = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("3D SCENE TRAJECTORY", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = TextMuted)
        Spacer(Modifier.weight(1f))
        Text("Scenes advance along time · Drag to orbit · Click a point", fontSize = 9.sp, color = TextMuted)
        IconButton(onClick = { camera.zoom = (camera.zoom * 0.82f).coerceIn(0.45f, 3.2f) }, modifier = Modifier.size(30.dp)) {
            Icon(Icons.Default.ZoomOut, contentDescription = "Zoom out", tint = TextMid, modifier = Modifier.size(16.dp))
        }
        IconButton(onClick = { camera.zoom = (camera.zoom * 1.22f).coerceIn(0.45f, 3.2f) }, modifier = Modifier.size(30.dp)) {
            Icon(Icons.Default.ZoomIn, contentDescription = "Zoom in", tint = TextMid, modifier = Modifier.size(16.dp))
        }
        IconButton(onClick = camera::reset, modifier = Modifier.size(30.dp)) {
            Icon(Icons.Default.Refresh, contentDescription = "Reset graph view", tint = TextMid, modifier = Modifier.size(15.dp))
        }
        if (onEnterFullscreen != null) {
            IconButton(onClick = onEnterFullscreen, modifier = Modifier.size(30.dp)) {
                Icon(Icons.Default.Fullscreen, contentDescription = "View graph fullscreen", tint = TextMid, modifier = Modifier.size(16.dp))
            }
        }
        if (onExitFullscreen != null) {
            IconButton(onClick = onExitFullscreen, modifier = Modifier.size(30.dp)) {
                Icon(Icons.Default.FullscreenExit, contentDescription = "Exit fullscreen", tint = TextMid, modifier = Modifier.size(16.dp))
            }
        }
    }
}

@Composable
private fun Viewport3D(
    track: NarrativeTrack,
    selectedPoint: VectorPoint?,
    onPointSelected: (VectorPoint) -> Unit,
    camera: GraphCamera,
    modifier: Modifier = Modifier,
) {
    val points = track.points
    var canvasSize by remember { mutableStateOf(IntSize.Zero) }
    val density = LocalDensity.current
    val hitRadius = with(density) { 22.dp.toPx() }
    val yaw = camera.yaw
    val pitch = camera.pitch
    val zoom = camera.zoom
    val graphModifier = Modifier
        .onSizeChanged { canvasSize = it }
        .pointerInput(track) {
            detectDragGestures { change, drag ->
                change.consume()
                camera.yaw = (camera.yaw + drag.x * 0.35f) % 360f
                camera.pitch = (camera.pitch + drag.y * 0.25f).coerceIn(-65f, 65f)
            }
        }
        .pointerInput(Unit) {
            awaitPointerEventScope {
                while (true) {
                    val event = awaitPointerEvent()
                    if (event.type == PointerEventType.Scroll) {
                        val delta = event.changes.firstOrNull()?.scrollDelta?.y ?: 0f
                        camera.zoom = (camera.zoom * if (delta < 0f) 1.12f else 0.89f).coerceIn(0.45f, 3.2f)
                        event.changes.forEach { it.consume() }
                    }
                }
            }
        }
        .pointerInput(points, camera.yaw, camera.pitch, camera.zoom, canvasSize) {
            detectTapGestures { tap ->
                val nearest = points.map { point ->
                    val position = projectPoint(
                        point.x, point.y, point.z,
                        canvasSize.width / 2f, canvasSize.height / 2f,
                        min(canvasSize.width, canvasSize.height) * 0.045f * camera.zoom,
                        camera.yaw, camera.pitch,
                        narrativeTime(point, points.size),
                    )
                    point to (position - tap).getDistance()
                }.minByOrNull { it.second }
                if (nearest != null && nearest.second <= hitRadius) onPointSelected(nearest.first)
            }
        }

    Box(modifier) {
    Canvas(graphModifier.fillMaxSize()) {
        val scale = min(size.width, size.height) * 0.045f * camera.zoom
        val cx = size.width / 2f
        val cy = size.height / 2f

        fun line(x1: Double, y1: Double, z1: Double, x2: Double, y2: Double, z2: Double, color: Color) {
            drawLine(
                color = color,
                start = projectPoint(x1, y1, z1, cx, cy, scale, camera.yaw, camera.pitch),
                end = projectPoint(x2, y2, z2, cx, cy, scale, camera.yaw, camera.pitch),
                strokeWidth = 0.8f,
            )
        }

        // Three faint coordinate planes give the trajectory a stable spatial frame.
        for (tick in 0..10 step 2) {
            val value = tick.toDouble()
            val grid = BorderSubtle.copy(alpha = 0.9f)
            line(0.0, 0.0, value, 10.0, 0.0, value, grid)
            line(value, 0.0, 0.0, value, 0.0, 10.0, grid)
            line(0.0, value, 0.0, 10.0, value, 0.0, grid)
            line(value, 0.0, 0.0, value, 10.0, 0.0, grid)
            line(0.0, value, 0.0, 0.0, value, 10.0, grid)
            line(0.0, 0.0, value, 0.0, 10.0, value, grid)
        }

        drawAxisLine(this, cx, cy, scale, camera, 10.0, 0.0, 0.0, AxisPlot)
        drawAxisLine(this, cx, cy, scale, camera, 0.0, 10.0, 0.0, AxisChar)
        drawAxisLine(this, cx, cy, scale, camera, 0.0, 0.0, 10.0, AxisEmot)

        // The time rail and every scene use the same fourth-coordinate projection.
        val timeOrigin = projectPoint(0.0, 0.0, 0.0, cx, cy, scale, camera.yaw, camera.pitch)
        val timeEnd = projectPoint(0.0, 0.0, 0.0, cx, cy, scale, camera.yaw, camera.pitch, TIME_AXIS_MAX)
        drawLine(AxisTime.copy(alpha = 0.9f), timeOrigin, timeEnd, strokeWidth = 1.8f)
        for (tick in 0 until 10 step 2) {
            val start = projectPoint(0.0, 0.0, 0.0, cx, cy, scale, camera.yaw, camera.pitch, tick.toDouble())
            val after = projectPoint(0.0, 0.0, 0.0, cx, cy, scale, camera.yaw, camera.pitch, (tick + 0.25).toDouble())
            val direction = after - start
            val normal = Offset(-direction.y, direction.x).let { vector ->
                val length = vector.getDistance().coerceAtLeast(1f)
                vector / length * 4f
            }
            drawLine(AxisTime.copy(alpha = 0.7f), start - normal, start + normal, strokeWidth = 1f)
        }
        for (index in 1 until points.size) {
            val previous = points[index - 1]
            val current = points[index]
            val startPosition = projectPoint(previous.x, previous.y, previous.z, cx, cy, scale, camera.yaw, camera.pitch, narrativeTime(previous, points.size))
            val endPosition = projectPoint(current.x, current.y, current.z, cx, cy, scale, camera.yaw, camera.pitch, narrativeTime(current, points.size))
            drawLine(
                color = if (current.isDeadZone) FlagDead else parseHexColor(current.storyColor),
                start = startPosition,
                end = endPosition,
                strokeWidth = 3f,
            )
        }

        points.forEach { point ->
            val position = projectPoint(point.x, point.y, point.z, cx, cy, scale, camera.yaw, camera.pitch, narrativeTime(point, points.size))
            val selected = point.sceneIndex == selectedPoint?.sceneIndex
            val pointColor = if (point.isDeadZone) FlagDead else parseHexColor(point.storyColor)
            val depth = (narrativeTime(point, points.size) / TIME_AXIS_MAX).toFloat()
            val radius = (if (selected) 6.5f else 4.8f) + depth * 2.2f
            drawCircle(pointColor.copy(alpha = 0.55f + 0.45f * depth), radius, position)
            drawCircle(BgBase, radius * 0.42f, position)
        }
    }
        if (canvasSize != IntSize.Zero) {
            val cx = canvasSize.width / 2f
            val cy = canvasSize.height / 2f
            val scale = min(canvasSize.width, canvasSize.height) * 0.045f * zoom
            AxisEndpointLabel("PLOT", AxisPlot, projectPoint(10.0, 0.0, 0.0, cx, cy, scale, yaw, pitch), canvasSize, 48.dp)
            AxisEndpointLabel("CHARACTER ARC", AxisChar, projectPoint(0.0, 10.0, 0.0, cx, cy, scale, yaw, pitch), canvasSize, 92.dp)
            AxisEndpointLabel("EMOTION", AxisEmot, projectPoint(0.0, 0.0, 10.0, cx, cy, scale, yaw, pitch), canvasSize, 64.dp)
            AxisEndpointLabel("TIME", AxisTime, projectPoint(0.0, 0.0, 0.0, cx, cy, scale, yaw, pitch, TIME_AXIS_MAX), canvasSize, 42.dp)
        }
    }
}

@Composable
private fun AxisEndpointLabel(label: String, color: Color, anchor: Offset, canvasSize: IntSize, width: Dp) {
    val density = LocalDensity.current
    Text(
        text = label,
        color = color,
        fontSize = 9.sp,
        fontWeight = FontWeight.Bold,
        maxLines = 1,
        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        modifier = Modifier
            .width(width)
            .height(16.dp)
            .offset {
                val labelWidth = with(density) { width.roundToPx() }
                val labelHeight = with(density) { 16.dp.roundToPx() }
                val left = (anchor.x - labelWidth / 2f).roundToInt()
                    .coerceIn(2, (canvasSize.width - labelWidth - 2).coerceAtLeast(2))
                val top = (anchor.y - labelHeight / 2f).roundToInt()
                    .coerceIn(2, (canvasSize.height - labelHeight - 2).coerceAtLeast(2))
                IntOffset(left, top)
            },
    )
}

private fun drawAxisLine(
    scope: DrawScope,
    cx: Float,
    cy: Float,
    scale: Float,
    camera: GraphCamera,
    x: Double,
    y: Double,
    z: Double,
    color: Color,
) {
    scope.drawLine(
        color = color.copy(alpha = 0.8f),
        start = projectPoint(0.0, 0.0, 0.0, cx, cy, scale, camera.yaw, camera.pitch),
        end = projectPoint(x, y, z, cx, cy, scale, camera.yaw, camera.pitch),
        strokeWidth = 1.8f,
    )
}

@Composable
private fun SceneInspector(point: VectorPoint?, track: NarrativeTrack?, modifier: Modifier = Modifier) {
    Column(
        modifier.background(BgBase).verticalScroll(rememberScrollState()).padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (point == null) {
            Eyebrow(if (track == null) "SCENE INSPECTOR" else "NO SCENE SELECTED")
            Text(
                if (track == null) "Analyze a screenplay to explore its scene trajectory." else "Select a scene point to inspect its narrative scores.",
                color = TextMid, fontSize = 12.sp, lineHeight = 18.sp,
            )
            return@Column
        }

        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Eyebrow("SCENE ${point.sceneIndex}")
            Spacer(Modifier.weight(1f))
            SmallTag(point.storyPhase, parseHexColor(point.storyColor))
        }
        Text(point.sceneTitle, color = TextHigh, fontSize = 17.sp, fontWeight = FontWeight.Bold, lineHeight = 21.sp)
        Text(point.sceneSummary, color = TextMid, fontSize = 11.sp, lineHeight = 16.sp)
        if (point.isDeadZone) SmallTag("DEAD ZONE", FlagDead)

        HorizontalRule()
        ScoreBar("Plot progression", point.x, AxisPlot)
        ScoreBar("Character arc", point.y, AxisChar)
        ScoreBar("Emotional flux", point.z, AxisEmot)
        val sceneCount = track?.summary?.sceneCount ?: point.sceneIndex
        val narrativeTime = narrativeTime(point, sceneCount)
        ScoreBar("Narrative time", narrativeTime, AxisTime)
        Text(
            "Scene ${point.sceneIndex} of $sceneCount · ${String.format("%.0f", narrativeTime * 10)}% through the screenplay",
            color = TextMuted,
            fontSize = 9.sp,
        )

        if (point.deltaD > 0.0) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Movement from previous scene", color = TextMuted, fontSize = 10.sp)
                Text(String.format("%.2f", point.deltaD), color = TextHigh, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
            }
        }
        if (point.pacingNote.isNotBlank()) {
            HorizontalRule()
            Eyebrow("STORY DOCTOR")
            Text(point.beatType, color = TextHigh, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            Text(point.pacingNote, color = TextMid, fontSize = 10.sp, lineHeight = 15.sp)
        }
    }
}

@Composable
private fun TrackMetrics(track: NarrativeTrack?) {
    Row(Modifier.fillMaxWidth().height(74.dp).background(BgSurface)) {
        MetricCell("SCENES", track?.summary?.sceneCount?.toString() ?: "—", "one point per scene", Modifier.weight(1f))
        MetricCell("SCENE MOVEMENT", track?.let { String.format("%.1f", it.summary.totalDistance) } ?: "—", "3D score distance", Modifier.weight(1f))
        MetricCell("LOW-MOVEMENT SCENES", track?.summary?.deadZoneCount?.toString() ?: "—", "below threshold", Modifier.weight(1f))
        MetricCell("CLIMAX", track?.summary?.climaxSceneIndex?.let { "Scene $it" } ?: "—", "highest plot + emotion", Modifier.weight(1f))
    }
}

@Composable
private fun MetricCell(label: String, value: String, detail: String, modifier: Modifier = Modifier) {
    Column(
        modifier.height(74.dp).border(BorderStroke(0.5.dp, BorderSubtle)).padding(horizontal = 16.dp, vertical = 9.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Eyebrow(label)
        Text(value, color = TextHigh, fontSize = 20.sp, fontWeight = FontWeight.Bold, maxLines = 1)
        Text(detail, color = TextMuted, fontSize = 9.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun SceneMapHeader(track: NarrativeTrack?) {
    Row(
        Modifier.fillMaxWidth().height(38.dp).background(BgBase).padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Eyebrow("SCENE MAP")
        Spacer(Modifier.weight(1f))
        Text(track?.summary?.pacingRating ?: "Awaiting analysis", color = if (track == null) TextMuted else Brand, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun SceneMap(
    points: List<VectorPoint>,
    selectedIndex: Int?,
    onSelect: (VectorPoint) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (points.isEmpty()) {
        Box(modifier, contentAlignment = Alignment.Center) {
            Text("Scene points will appear here after analysis.", color = TextMuted, fontSize = 11.sp)
        }
        return
    }

    LazyColumn(modifier) {
        items(points, key = { it.sceneIndex }) { point ->
            Row(
                Modifier.fillMaxWidth().height(34.dp)
                    .background(if (point.sceneIndex == selectedIndex) Brand.copy(alpha = 0.12f) else BgBase)
                    .clickable { onSelect(point) }
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("${point.sceneIndex}", color = TextMuted, fontSize = 10.sp, modifier = Modifier.width(48.dp))
                Text("${String.format("%.0f", narrativeTime(point, points.size) * 10)}%", color = AxisTime, fontSize = 9.sp, modifier = Modifier.width(38.dp))
                Text(point.sceneTitle, color = TextHigh, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                Text(point.storyPhase, color = parseHexColor(point.storyColor), fontSize = 9.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.width(88.dp))
                Text(if (point.sceneIndex == 1) "—" else String.format("%.2f", point.deltaD), color = TextMid, fontSize = 9.sp, modifier = Modifier.width(55.dp))
                Text(point.beatType, color = if (point.isDeadZone) FlagDead else TextMid, fontSize = 9.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.width(142.dp))
            }
            HorizontalRule()
        }
    }
}

@Composable
private fun ScoreBar(label: String, value: Double, color: Color) {
    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(label, color = TextMid, fontSize = 10.sp)
            Text(String.format("%.1f", value), color = TextHigh, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
        }
        Box(Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)).background(BorderSubtle)) {
            Box(Modifier.fillMaxWidth((value / 10.0).toFloat().coerceIn(0f, 1f)).fillMaxHeight().background(color).clip(RoundedCornerShape(2.dp)))
        }
    }
}

@Composable
private fun AxisLegend(label: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        Box(Modifier.size(7.dp).clip(RoundedCornerShape(4.dp)).background(color))
        Text(label, color = color, fontSize = 10.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun SmallTag(label: String, color: Color) {
    Surface(color = color.copy(alpha = 0.12f), shape = RoundedCornerShape(4.dp), border = BorderStroke(1.dp, color.copy(alpha = 0.5f))) {
        Text(label, color = color, fontSize = 9.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp))
    }
}

@Composable
private fun Eyebrow(text: String) {
    Text(text, color = TextMuted, fontSize = 9.sp, fontWeight = FontWeight.Bold)
}

@Composable
private fun HorizontalRule() {
    Box(Modifier.fillMaxWidth().height(1.dp).background(BorderSubtle))
}

@Composable
private fun VerticalRule() {
    Box(Modifier.width(1.dp).fillMaxHeight().background(BorderSubtle))
}

@Composable
private fun EmptyGraphState() {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("3D", color = BorderStrong, fontSize = 32.sp, fontWeight = FontWeight.Bold)
        Text("Analyze a screenplay to build its trajectory", color = TextMid, fontSize = 12.sp)
    }
}

@Stable
internal class GraphCamera {
    var yaw by mutableFloatStateOf(45f)
    var pitch by mutableFloatStateOf(30f)
    var zoom by mutableFloatStateOf(1f)

    fun reset() {
        yaw = 45f
        pitch = 30f
        zoom = 1f
    }
}

private fun projectPoint(
    x: Double,
    y: Double,
    z: Double,
    cx: Float,
    cy: Float,
    scale: Float,
    yaw: Float,
    pitch: Float,
    time: Double = 0.0,
): Offset {
    val yawRadians = Math.toRadians(yaw.toDouble())
    val pitchRadians = Math.toRadians(pitch.toDouble())
    val centeredX = x - 5.0
    val centeredY = y - 5.0
    val centeredZ = z - 5.0
    val rotatedX = centeredX * cos(yawRadians) - centeredZ * sin(yawRadians)
    val rotatedZ = centeredX * sin(yawRadians) + centeredZ * cos(yawRadians)
    val timeProgress = time.coerceIn(0.0, TIME_AXIS_MAX) / TIME_AXIS_MAX
    val timeDistance = timeProgress * TIME_AXIS_LENGTH
    val timeX = timeDistance * TIME_AXIS_X
    val timeZ = timeDistance * TIME_AXIS_Z
    return Offset(
        cx + ((rotatedX + timeX * cos(yawRadians) - timeZ * sin(yawRadians)) * scale).toFloat(),
        cy + ((-centeredY * cos(pitchRadians) + (rotatedZ + timeX * sin(yawRadians) + timeZ * cos(yawRadians)) * sin(pitchRadians)) * scale).toFloat(),
    )
}

private const val TIME_AXIS_MAX = 10.0
private const val TIME_AXIS_LENGTH = 10.0
private const val TIME_AXIS_X = 1.0
private const val TIME_AXIS_Z = 1.0

private fun narrativeTime(point: VectorPoint, sceneCount: Int): Double =
    if (sceneCount <= 1) 0.0 else ((point.sceneIndex - 1).toDouble() / (sceneCount - 1)) * TIME_AXIS_MAX

private fun parseHexColor(value: String): Color {
    val hex = value.removePrefix("#")
    val rgb = hex.toLongOrNull(16) ?: return Brand
    return when (hex.length) {
        6 -> Color(0xFF000000 or rgb)
        8 -> Color(rgb)
        else -> Brand
    }
}
