package ru.tusur.timetable.ui.map

import android.graphics.Paint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import ru.tusur.timetable.R
import ru.tusur.timetable.domain.model.FloorOutline
import ru.tusur.timetable.domain.model.LessonType
import ru.tusur.timetable.domain.model.MapRoom
import ru.tusur.timetable.domain.model.PointF
import ru.tusur.timetable.domain.model.RoomStatus
import ru.tusur.timetable.ui.theme.LocalLessonColors
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

private const val PLAN_WIDTH = 920f
private const val PLAN_HEIGHT = 640f
private const val PLAN_MIN_X = -15f
private const val PLAN_MIN_Y = -15f

@Composable
fun FloorPlan(
    floor: Int,
    rooms: List<MapRoom>,
    outline: FloorOutline?,
    statuses: Map<String, RoomStatus>,
    selectedId: String?,
    onSelect: (MapRoom) -> Unit,
    modifier: Modifier = Modifier,
) {
    var zoom by remember(floor) { mutableFloatStateOf(1f) }
    var pan by remember(floor) { mutableStateOf(Offset.Zero) }
    var viewport by remember { mutableStateOf(IntSize.Zero) }

    val background = MaterialTheme.colorScheme.background
    val buildingFill = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
    val roomFill = MaterialTheme.colorScheme.surface
    val roomStroke = MaterialTheme.colorScheme.outlineVariant
    val selectedColor = MaterialTheme.colorScheme.primary
    val busyColor = MaterialTheme.colorScheme.error
    val freeColor = LocalLessonColors.current.color(LessonType.PRACTICE)
    val textColor = MaterialTheme.colorScheme.onSurface
    val mutedText = MaterialTheme.colorScheme.onSurfaceVariant

    fun baseScaleFor(size: IntSize): Float {
        if (size.width == 0 || size.height == 0) return 1f
        return min(size.width / PLAN_WIDTH, size.height / PLAN_HEIGHT)
    }

    val tapLabel = stringResource(R.string.map_tap_hint)
    val zoomOutLabel = stringResource(R.string.map_zoom_out)

    Box(modifier = modifier) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .onSizeChanged { viewport = it }
                .pointerInput(floor) {
                    detectTransformGestures { _, panChange, zoomChange, _ ->
                        val nextZoom = (zoom * zoomChange).coerceIn(1f, 5f)
                        zoom = nextZoom
                        pan += panChange
                    }
                }
                .pointerInput(floor, rooms, zoom, pan, viewport) {
                    detectTapGestures { tap ->
                        val base = baseScaleFor(viewport)
                        val scale = base * zoom
                        val originX = (viewport.width - PLAN_WIDTH * scale) / 2f + pan.x
                        val originY = (viewport.height - PLAN_HEIGHT * scale) / 2f + pan.y
                        val point = PointF(
                            (tap.x - originX) / scale + PLAN_MIN_X,
                            (tap.y - originY) / scale + PLAN_MIN_Y,
                        )
                        val hit = rooms.lastOrNull { it.contains(point) }
                        if (hit != null) onSelect(hit)
                    }
                }
                .semantics { contentDescription = tapLabel },
        ) {
            val base = baseScaleFor(viewport)
            val scale = base * zoom
            val originX = (size.width - PLAN_WIDTH * scale) / 2f + pan.x
            val originY = (size.height - PLAN_HEIGHT * scale) / 2f + pan.y

            withTransform({
                translate(originX, originY)
                scale(scale, scale, pivot = Offset.Zero)
                translate(-PLAN_MIN_X, -PLAN_MIN_Y)
            }) {
                outline?.let { floorOutline ->
                    drawPolygon(floorOutline.building, buildingFill, null, 0f)
                    floorOutline.annex.forEach { polygon ->
                        drawPolygon(polygon, buildingFill, null, 0f)
                    }
                    floorOutline.holes.forEach { hole ->
                        drawPolygon(hole, background, null, 0f)
                    }
                }

                rooms.forEach { room ->
                    val status = statuses[room.id]
                    val fill = when {
                        room.id == selectedId -> selectedColor.copy(alpha = 0.38f)
                        status is RoomStatus.Busy -> busyColor.copy(alpha = 0.20f)
                        status is RoomStatus.Free -> freeColor.copy(alpha = 0.14f)
                        else -> roomFill
                    }
                    val stroke = if (room.id == selectedId) selectedColor else roomStroke
                    val strokeWidth = if (room.id == selectedId) 2.6f else 0.7f
                    drawPolygon(room.polygon, fill, stroke, strokeWidth)
                }

                val paint = Paint().apply {
                    isAntiAlias = true
                    textAlign = Paint.Align.CENTER
                    textSize = 24f
                    color = textColor.toArgb()
                }
                val mutedPaint = Paint().apply {
                    isAntiAlias = true
                    textAlign = Paint.Align.CENTER
                    textSize = 19f
                    color = mutedText.toArgb()
                }

                rooms.forEach { room ->
                    val label = room.displayLabel
                    if (label.isBlank()) return@forEach
                    val bounds = roomBounds(room)
                    val width = bounds[2] - bounds[0]
                    val height = bounds[3] - bounds[1]
                    if (room.id != selectedId && (width < 34f || height < 26f)) return@forEach
                    val isRoom = room.searchable
                    val currentPaint = if (isRoom) paint else mutedPaint
                    drawContext.canvas.nativeCanvas.drawText(
                        label,
                        room.center.x,
                        room.center.y + 8f,
                        currentPaint,
                    )
                }
            }
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            FilledTonalIconButton(
                onClick = { zoom = (zoom * 1.35f).coerceAtMost(5f) },
                modifier = Modifier.size(44.dp),
            ) {
                Icon(
                    Icons.Default.Add,
                    contentDescription = stringResource(R.string.map_zoom_in),
                )
            }
            Text(
                text = "${zoom.roundToInt()}×",
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.padding(vertical = 2.dp),
            )
            FilledTonalIconButton(
                onClick = { zoom = (zoom / 1.35f).coerceAtLeast(1f) },
                modifier = Modifier
                    .size(44.dp)
                    .semantics { contentDescription = zoomOutLabel },
            ) {
                Text(
                    text = "−",
                    style = MaterialTheme.typography.titleLarge,
                )
            }
            FilledTonalIconButton(
                onClick = {
                    zoom = 1f
                    pan = Offset.Zero
                },
                modifier = Modifier
                    .padding(top = 4.dp)
                    .size(44.dp),
            ) {
                Icon(
                    Icons.Default.Refresh,
                    contentDescription = stringResource(R.string.map_zoom_reset),
                )
            }
        }
    }
}

private fun DrawScope.drawPolygon(
    points: List<PointF>,
    fill: Color,
    stroke: Color?,
    strokeWidth: Float,
) {
    if (points.size < 3) return
    val path = Path().apply {
        moveTo(points.first().x, points.first().y)
        points.drop(1).forEach { lineTo(it.x, it.y) }
        close()
    }
    drawPath(path, fill)
    if (stroke != null && strokeWidth > 0f) {
        drawPath(path, stroke, style = androidx.compose.ui.graphics.drawscope.Stroke(strokeWidth))
    }
}

private fun roomBounds(room: MapRoom): FloatArray {
    var minX = Float.MAX_VALUE
    var minY = Float.MAX_VALUE
    var maxX = -Float.MAX_VALUE
    var maxY = -Float.MAX_VALUE
    room.polygon.forEach {
        minX = min(minX, it.x)
        minY = min(minY, it.y)
        maxX = max(maxX, it.x)
        maxY = max(maxY, it.y)
    }
    return floatArrayOf(minX, minY, maxX, maxY)
}
