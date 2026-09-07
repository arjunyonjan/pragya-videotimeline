package com.videotimeline

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = Color(0xFF0F1419)) {
                    TimelineScreen()
                }
            }
        }
    }
}

private const val DP_PER_SECOND = 50f
private const val SNAP_DP = 50f
private const val ROW_HEIGHT_DP = 80
private const val ROW_GAP_DP = 4
private const val CLIP_HEIGHT_DP = 48
private const val MIN_ROW = 0
private const val MAX_ROW = 1
private const val TIMELINE_WIDTH_DP = 600

private fun dpToTimecode(dp: Float): String {
    val totalSeconds = dp / DP_PER_SECOND
    val minutes = (totalSeconds / 60).toInt()
    val seconds = totalSeconds.toInt() % 60
    val centi = ((totalSeconds - totalSeconds.toInt()) * 100).toInt()
    return "%02d:%02d.%02d".format(minutes, seconds, centi)
}

class ClipState(
    val label: String,
    val color: Color,
    widthDp: Float,
    xDp: Float,
    row: Int
) {
    var widthDp by mutableFloatStateOf(widthDp)
    var xDp by mutableFloatStateOf(xDp)
    var row by mutableIntStateOf(row)
}

private val ClipsSaver: Saver<MutableList<ClipState>, Any> = listSaver(
    save = { list -> list.map { listOf(it.label, it.color.toArgb(), it.widthDp, it.xDp, it.row) } },
    restore = { saved ->
        saved.map {
            @Suppress("UNCHECKED_CAST")
            val l = it as List<Any>
            ClipState(
                label = l[0] as String,
                color = Color(l[1] as Int),
                widthDp = (l[2] as Number).toFloat(),
                xDp = (l[3] as Number).toFloat(),
                row = (l[4] as Number).toInt()
            )
        }.toMutableList()
    }
)

@Composable
fun TimelineScreen() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text(
            "VideoTimeline — Build 18 (split + delete)",
            color = Color(0xFF00BFA5),
            fontWeight = FontWeight.Bold,
            fontSize = 18.sp
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "Drag clips. Drag playhead. Pinch ruler to zoom. Scroll horizontally.",
            color = Color(0xFF6B7280),
            fontSize = 12.sp
        )
        Spacer(Modifier.height(16.dp))

        TimelineBox(
            modifier = Modifier
                .fillMaxWidth()
                .height(320.dp)
        )
    }
}

@Composable
fun TimelineBox(modifier: Modifier = Modifier) {
    val density = LocalDensity.current
    val trackHeightPx = with(density) { ROW_HEIGHT_DP.dp.roundToPx() }
    val trackGapPx = with(density) { ROW_GAP_DP.dp.roundToPx() }
    val rowTotalPx = trackHeightPx + trackGapPx
    val rulerHeightPx = with(density) { 32.dp.roundToPx() }
    val clipHeightPx = with(density) { CLIP_HEIGHT_DP.dp.roundToPx() }
    val scrollState = rememberScrollState()

    var scale by rememberSaveable { mutableFloatStateOf(1f) }
    var dragActive by remember { mutableStateOf(false) }
    var selectedLabel by remember { mutableStateOf<String?>(null) }
    val maxXDp = TIMELINE_WIDTH_DP * scale

    val clips = rememberSaveable(saver = ClipsSaver) {
        mutableListOf(
            ClipState("A", Color(0xFF7C3AED), 100f, 12f, 0),
            ClipState("B", Color(0xFFEC4899), 120f, 152f, 0),
            ClipState("C", Color(0xFF10B981), 90f, 24f, 1),
            ClipState("D", Color(0xFFF59E0B), 110f, 200f, 1)
        )
    }

    var playheadXDp by rememberSaveable { mutableFloatStateOf(60f) }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFF1A1F26))
    ) {
        Column(modifier = Modifier.fillMaxSize().padding(8.dp)) {
            // Zoom buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Zoom:", color = Color(0xFF6B7280), fontSize = 12.sp)
                Spacer(Modifier.width(8.dp))
                Text(
                    "-",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    modifier = Modifier
                        .size(32.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color(0xFF374151))
                        .padding(4.dp)
                        .pointerInput(Unit) {
                            detectTapGestures { scale = (scale * 0.8f).coerceIn(0.5f, 10f) }
                        },
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    "${String.format("%.1f", scale)}x",
                    color = Color(0xFF00BFA5),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    "+",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    modifier = Modifier
                        .size(32.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color(0xFF374151))
                        .padding(4.dp)
                        .pointerInput(Unit) {
                            detectTapGestures { scale = (scale * 1.25f).coerceIn(0.5f, 10f) }
                        },
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    "Reset",
                    color = Color(0xFFEF4444),
                    fontSize = 11.sp,
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color(0xFF374151))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                        .pointerInput(Unit) {
                            detectTapGestures { scale = 1f }
                        }
                )
            }
            Spacer(Modifier.height(4.dp))

            // Split / Delete toolbar
            val selClip = clips.find { it.label == selectedLabel }
            if (selClip != null) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Clip ${selClip.label}", color = Color(0xFF9CA3AF), fontSize = 11.sp)
                    Spacer(Modifier.width(12.dp))

                    // Split
                    val canSplit = playheadXDp > selClip.xDp && playheadXDp < selClip.xDp + selClip.widthDp
                    Text(
                        "Split",
                        color = if (canSplit) Color(0xFF00BFA5) else Color(0xFF4B5563),
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(if (canSplit) Color(0xFF1A3A36) else Color(0xFF1F2937))
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                            .pointerInput(Unit) {
                                detectTapGestures {
                                    if (canSplit) {
                                        val splitPoint = playheadXDp
                                        val newLabel = ('A'..'Z').first { c -> clips.none { it.label == "$c" } }.toString()
                                        val rightWidth = (selClip.xDp + selClip.widthDp) - splitPoint
                                        val newClip = ClipState(
                                            label = newLabel,
                                            color = selClip.color.copy(alpha = 0.8f),
                                            widthDp = rightWidth,
                                            xDp = splitPoint,
                                            row = selClip.row
                                        )
                                        selClip.widthDp = splitPoint - selClip.xDp
                                        clips.add(newClip)
                                        selectedLabel = null
                                    }
                                }
                            }
                    )
                    Spacer(Modifier.width(8.dp))

                    // Delete
                    Text(
                        "Delete",
                        color = Color(0xFFEF4444),
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(Color(0xFF3B1A1A))
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                            .pointerInput(Unit) {
                                detectTapGestures {
                                    clips.remove(selClip)
                                    selectedLabel = null
                                }
                            }
                    )
                }
                Spacer(Modifier.height(4.dp))
            }

            RulerRow(
                scale = scale,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(24.dp)
                    .horizontalScroll(scrollState, enabled = !dragActive)
            )
            Spacer(Modifier.height(8.dp))

            val transformState = rememberTransformableState { zoomChange, _, _ ->
                scale = (scale * zoomChange).coerceIn(0.5f, 10f)
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(240.dp)
                    .horizontalScroll(scrollState, enabled = !dragActive)
                    .transformable(state = transformState)
            ) {
                TrackLanes(
                    scale = scale,
                    trackHeightPx = trackHeightPx,
                    rowTotalPx = rowTotalPx,
                    rulerHeightPx = rulerHeightPx
                )

                // Background tap to deselect
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(240.dp)
                        .pointerInput(Unit) { detectTapGestures { selectedLabel = null } }
                )

                clips.forEach { clip ->
                    DraggableClip(
                        clip = clip,
                        isSelected = clip.label == selectedLabel,
                        scale = scale,
                        trackHeightPx = trackHeightPx,
                        rowTotalPx = rowTotalPx,
                        rulerHeightPx = rulerHeightPx,
                        maxXDp = maxXDp,
                        onActiveChange = { dragActive = it },
                        onSelect = { selectedLabel = clip.label },
                        onDeselect = { selectedLabel = null },
                        onCommit = { newXDp, newWidth, newRow ->
                            clip.xDp = newXDp
                            clip.widthDp = newWidth
                            clip.row = newRow
                            repeat(3) { pushNeighbors(clip, clips, maxXDp) }
                        }
                    )
                }

                Playhead(
                    xDp = playheadXDp,
                    scale = scale,
                    heightPx = with(density) { 240.dp.roundToPx() },
                    maxXDp = maxXDp,
                    onActiveChange = { dragActive = it },
                    onCommit = { newXDp -> playheadXDp = newXDp }
                )
            }
        }
    }
}

private fun pushNeighbors(
    moving: ClipState,
    clips: MutableList<ClipState>,
    maxXDp: Float
) {
    val movingStart = moving.xDp
    val movingEnd = moving.xDp + moving.widthDp

    clips.filter { it.label != moving.label && it.row == moving.row }.forEach { other ->
        val otherStart = other.xDp
        val otherEnd = other.xDp + other.widthDp
        val overlap = movingStart < otherEnd && movingEnd > otherStart
        if (overlap) {
            other.xDp = when {
                movingStart < otherStart -> movingEnd.coerceAtMost(maxXDp - other.widthDp)
                else -> (movingStart - other.widthDp).coerceAtLeast(0f)
            }
            other.xDp = (other.xDp / SNAP_DP).roundToInt() * SNAP_DP
        }
    }
}

@Composable
fun TrackLanes(
    scale: Float,
    trackHeightPx: Int,
    rowTotalPx: Int,
    rulerHeightPx: Int
) {
    val laneWidthDp = (TIMELINE_WIDTH_DP * scale).dp
    repeat(MAX_ROW + 1) { row ->
        val yPx = rulerHeightPx + row * rowTotalPx
        Box(
            modifier = Modifier
                .offset { IntOffset(0, yPx) }
                .size(width = laneWidthDp, height = ROW_HEIGHT_DP.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(Color(0xFF252B34))
        )
    }
}

@Composable
fun RulerRow(
    scale: Float,
    modifier: Modifier = Modifier
) {
    val count = ((TIMELINE_WIDTH_DP * scale) / DP_PER_SECOND).toInt() + 1
    val markWidthDp = (DP_PER_SECOND * scale).dp
    Row(
        modifier = modifier
            .width((TIMELINE_WIDTH_DP * scale).dp)
            .background(Color(0xFF252B34))
    ) {
        repeat(count) { i ->
            Text(
                "${i}s",
                color = Color(0xFF6B7280),
                fontSize = 10.sp,
                modifier = Modifier
                    .width(markWidthDp)
                    .height(24.dp)
                    .padding(start = 2.dp, top = 4.dp)
            )
        }
    }
}

private const val TRIM_HANDLE_DP = 20
private const val MIN_CLIP_WIDTH_DP = 30

@Composable
fun DraggableClip(
    clip: ClipState,
    isSelected: Boolean,
    scale: Float,
    trackHeightPx: Int,
    rowTotalPx: Int,
    rulerHeightPx: Int,
    maxXDp: Float,
    onActiveChange: (Boolean) -> Unit,
    onSelect: () -> Unit,
    onDeselect: () -> Unit,
    onCommit: (Float, Float, Int) -> Unit
) {
    val density = LocalDensity.current
    val densityF = density.density
    val minW = MIN_CLIP_WIDTH_DP.toFloat()
    var localXDp by remember { mutableFloatStateOf(clip.xDp) }
    var localRow by remember { mutableIntStateOf(clip.row) }
    var localWidth by remember { mutableFloatStateOf(clip.widthDp.toFloat()) }
    var dragging by remember { mutableStateOf(false) }
    var dragMode by remember { mutableIntStateOf(0) } // 0=move, -1=trimLeft, 1=trimRight

    val dispXDp = if (dragging) localXDp else clip.xDp
    val dispRow = if (dragging) localRow else clip.row
    val dispW = if (dragging) localWidth else clip.widthDp.toFloat()
    val clipH = with(density) { CLIP_HEIGHT_DP.dp.roundToPx() }
    val dispY = rulerHeightPx + (dispRow * rowTotalPx) + (trackHeightPx - clipH) / 2f
    val clipWPx = dispW * scale * densityF
    val handlePx = with(density) { TRIM_HANDLE_DP.dp.roundToPx() }

    Box(
        modifier = Modifier
            .offset { IntOffset((dispXDp * scale * densityF).roundToInt(), dispY.roundToInt()) }
            .size(width = (dispW * scale).dp, height = CLIP_HEIGHT_DP.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(clip.color)
            .pointerInput(isSelected) {
                if (isSelected) {
                    detectDragGestures(
                        onDragStart = { offset ->
                            localXDp = clip.xDp
                            localRow = clip.row
                            localWidth = clip.widthDp.toFloat()
                            dragMode = when {
                                offset.x < handlePx -> -1
                                offset.x > clipWPx - handlePx -> 1
                                else -> 0
                            }
                            dragging = true
                            onActiveChange(true)
                        },
                        onDragEnd = {
                            dragging = false
                            onActiveChange(false)
                            onCommit(
                                (localXDp / SNAP_DP).roundToInt() * SNAP_DP,
                                (localWidth / SNAP_DP).roundToInt() * SNAP_DP,
                                localRow
                            )
                        },
                        onDragCancel = {
                            dragging = false
                            onActiveChange(false)
                        },
                        onDrag = { change, dragAmount ->
                            change.consume()
                            val dxDp = dragAmount.x / densityF / scale
                            when (dragMode) {
                                -1 -> {
                                    val newW = (localWidth - dxDp).coerceAtLeast(minW)
                                    localXDp = (localXDp + localWidth - newW).coerceIn(0f, maxXDp)
                                    localWidth = newW
                                }
                                1 -> {
                                    localWidth = (localWidth + dxDp).coerceAtLeast(minW)
                                }
                                else -> {
                                    localXDp = (localXDp + dxDp).coerceIn(0f, maxXDp - localWidth)
                                    localRow = (localRow + (dragAmount.y / rowTotalPx).roundToInt())
                                        .coerceIn(MIN_ROW, MAX_ROW)
                                }
                            }
                        }
                    )
                } else {
                    detectTapGestures { onSelect() }
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Text(
            "Clip ${clip.label}",
            color = Color.White,
            fontWeight = FontWeight.Bold,
            fontSize = 12.sp
        )
        if (isSelected) {
            Box(Modifier.align(Alignment.CenterStart).width(4.dp).fillMaxHeight().background(Color.White.copy(alpha = 0.6f)))
            Box(Modifier.align(Alignment.CenterEnd).width(4.dp).fillMaxHeight().background(Color.White.copy(alpha = 0.6f)))
        }
    }
}

@Composable
fun Playhead(
    xDp: Float,
    scale: Float,
    heightPx: Int,
    maxXDp: Float,
    onActiveChange: (Boolean) -> Unit,
    onCommit: (Float) -> Unit
) {
    var localXDp by remember { mutableFloatStateOf(xDp) }
    var dragging by remember { mutableStateOf(false) }
    val dispXDp = if (dragging) localXDp else xDp
    val density = LocalDensity.current
    val densityF = density.density

    val dragState = rememberDraggableState { delta ->
        localXDp = (localXDp + delta / densityF / scale).coerceIn(0f, maxXDp)
    }

    val touchWidthPx = with(density) { 24.dp.roundToPx().toFloat() }
    Box(
        modifier = Modifier
            .offset { IntOffset(((dispXDp * scale * densityF) - (touchWidthPx / 2f)).roundToInt(), 0) }
            .size(width = 24.dp, height = with(density) { heightPx.toDp() })
            .draggable(
                state = dragState,
                orientation = androidx.compose.foundation.gestures.Orientation.Horizontal,
                onDragStarted = {
                    localXDp = xDp
                    dragging = true
                    onActiveChange(true)
                },
                onDragStopped = {
                    dragging = false
                    onActiveChange(false)
                    onCommit((localXDp / SNAP_DP).roundToInt() * SNAP_DP)
                }
            ),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                modifier = Modifier
                    .size(width = 70.dp, height = 18.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(Color(0xFFEF4444)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    dpToTimecode(if (dragging) localXDp else xDp),
                    color = Color.White,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            Box(
                modifier = Modifier
                    .width(2.dp)
                    .fillMaxHeight()
                    .background(Color(0xFFEF4444))
            )
        }
    }
}
