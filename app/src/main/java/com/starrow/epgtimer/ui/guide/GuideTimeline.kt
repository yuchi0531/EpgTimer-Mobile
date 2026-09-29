package com.starrow.epgtimer.ui.guide

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import java.time.Duration
import java.time.LocalDateTime

internal const val GUIDE_MIN_TEXT_HEIGHT_PX = 14f
internal const val GUIDE_BAND_WIDTH_DP = 4
internal const val GUIDE_TEXT_PAD_DP = 4
internal const val GUIDE_AXIS_WIDTH_DP = 56
internal const val GUIDE_CELL_MIN_HEIGHT_PX = 2f

class TimeSegment(val start: Float, val end: Float)

class TimelineMapper(
    val origin: Float,
    val end: Float,
    private val segments: List<TimeSegment>,
    private val pxPerMinute: Float,
) {
    val totalPx: Float = segments.fold(0f) { acc, segment -> acc + (segment.end - segment.start) * pxPerMinute }

    fun y(minutes: Float): Float {
        var acc = 0f
        for (segment in segments) {
            if (minutes <= segment.start) return acc
            if (minutes <= segment.end) return acc + (minutes - segment.start) * pxPerMinute
            acc += (segment.end - segment.start) * pxPerMinute
        }
        return acc
    }

    fun minutesAt(y: Float): Float {
        var acc = 0f
        for (segment in segments) {
            val segmentPx = (segment.end - segment.start) * pxPerMinute
            if (y <= acc + segmentPx) {
                return segment.start + (y - acc) / pxPerMinute
            }
            acc += segmentPx
        }
        return end
    }

    fun isKept(minutes: Float): Boolean = segments.any { minutes >= it.start && minutes < it.end }
}

fun buildTimeline(
    origin: Float,
    end: Float,
    busy: List<Pair<Float, Float>>,
    collapse: Boolean,
): List<TimeSegment> {
    if (!collapse) return listOf(TimeSegment(origin, end))
    val clipped = busy
        .map { Pair(it.first.coerceAtLeast(origin), it.second.coerceAtMost(end)) }
        .filter { it.second > it.first }
        .sortedBy { it.first }
    if (clipped.isEmpty()) return listOf(TimeSegment(origin, end))
    val merged = mutableListOf<TimeSegment>()
    for ((start, finish) in clipped) {
        val last = merged.lastOrNull()
        if (last != null && start <= last.end) {
            if (finish > last.end) {
                merged[merged.size - 1] = TimeSegment(last.start, finish)
            }
        } else {
            merged.add(TimeSegment(start, finish))
        }
    }
    return merged
}

fun minutesBetween(from: LocalDateTime, to: LocalDateTime): Float =
    Duration.between(from, to).toMillis() / 60000f

class ViewRect(val left: Float, val top: Float, val right: Float, val bottom: Float) {
    val width: Float get() = right - left
    val height: Float get() = bottom - top
}

class ViewSurface(
    val rect: ViewRect,
    val originX: Float,
    val originY: Float,
    val width: Float,
    val height: Float,
)

fun visibleColumns(
    scrollX: Float,
    viewportWidth: Float,
    originPx: Float,
    cellWidthPx: Float,
    count: Int,
): IntRange {
    if (count <= 0 || cellWidthPx <= 0f) return 0..-1
    val first = ((scrollX - originPx) / cellWidthPx).toInt().coerceIn(0, count - 1)
    val last = ((scrollX + viewportWidth - originPx) / cellWidthPx).toInt().coerceIn(0, count - 1)
    return first..last
}

fun visibleRows(
    scrollY: Float,
    viewportHeight: Float,
    originPx: Float,
    rowHeightPx: Float,
    count: Int,
): IntRange {
    if (count <= 0 || rowHeightPx <= 0f) return 0..-1
    val first = ((scrollY - originPx) / rowHeightPx).toInt().coerceIn(0, count - 1)
    val last = ((scrollY + viewportHeight - originPx) / rowHeightPx).toInt().coerceIn(0, count - 1)
    return first..last
}

fun viewOrigin(scrollX: Float, scrollY: Float, headerPx: Float): Pair<Float, Float> =
    headerPx - scrollX to headerPx - scrollY

fun viewRect(
    scrollX: Float,
    scrollY: Float,
    viewportWidth: Float,
    viewportHeight: Float,
    contentWidth: Float,
    contentHeight: Float,
): ViewRect = ViewRect(
    left = scrollX.coerceIn(0f, contentWidth),
    top = scrollY.coerceIn(0f, contentHeight),
    right = (scrollX + viewportWidth).coerceIn(0f, contentWidth),
    bottom = (scrollY + viewportHeight).coerceIn(0f, contentHeight),
)

fun guideSurface(
    scrollX: Float,
    scrollY: Float,
    viewportWidth: Float,
    viewportHeight: Float,
    headerPx: Float,
    contentWidth: Float,
    contentHeight: Float,
): ViewSurface {
    val rect = viewRect(
        scrollX = scrollX,
        scrollY = scrollY,
        viewportWidth = viewportWidth,
        viewportHeight = viewportHeight,
        contentWidth = contentWidth,
        contentHeight = contentHeight,
    )
    val origin = viewOrigin(scrollX, scrollY, headerPx)
    return ViewSurface(
        rect = rect,
        originX = origin.first,
        originY = origin.second,
        width = viewportWidth.coerceAtLeast(1f),
        height = viewportHeight.coerceAtLeast(1f),
    )
}

fun gridColumnAt(xInView: Float, originPx: Float, cellWidthPx: Float, count: Int): Int {
    if (xInView < originPx) return -1
    val column = ((xInView - originPx) / cellWidthPx).toInt()
    return if (column in 0 until count) column else -1
}

fun drawFittedCellText(
    textMeasurer: TextMeasurer,
    title: String,
    maxWidth: Int,
    maxHeight: Float,
    style: TextStyle,
    topLeft: Offset,
    draw: (TextLayoutResult, Offset) -> Unit,
) {
    if (title.isBlank() || maxWidth <= 0 || maxHeight < GUIDE_MIN_TEXT_HEIGHT_PX) return
    for (maxLines in intArrayOf(2, 1)) {
        val layout = textMeasurer.measure(
            text = AnnotatedString(title),
            style = style,
            maxLines = maxLines,
            overflow = TextOverflow.Ellipsis,
            constraints = Constraints(maxWidth = maxWidth),
        )
        if (layout.size.height <= maxHeight) {
            draw(layout, topLeft)
            return
        }
    }
}

@Composable
fun GuideEmptyMessage(text: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(text, style = MaterialTheme.typography.bodyLarge)
    }
}

private val GENRE_COLORS = mapOf(
    0x00 to Color(0xFFB3E5FC),
    0x01 to Color(0xFFC8E6C9),
    0x02 to Color(0xFFFFF9C4),
    0x03 to Color(0xFFFFCDD2),
    0x04 to Color(0xFFE1BEE7),
    0x05 to Color(0xFFFFE0B2),
    0x06 to Color(0xFFD1C4E9),
    0x07 to Color(0xFFFFE082),
    0x08 to Color(0xFFBCAAA4),
    0x09 to Color(0xFFF48FB1),
    0x0A to Color(0xFFB2DFDB),
    0x0B to Color(0xFFCFD8DC),
)

fun genreColor(nibbleLevel1: Int): Color = GENRE_COLORS[nibbleLevel1] ?: Color(0xFFEEEEEE)

const val GUIDE_MAX_CONTENT_PX = 4_000_000f
const val GUIDE_MAX_PX_PER_MINUTE = 4000f
const val GUIDE_MINUTES_PER_DAY = 1440f

fun guidePpxPerMinute(hourHeightPx: Float): Float =
    (hourHeightPx / 60f).coerceIn(0.01f, GUIDE_MAX_PX_PER_MINUTE)

fun guideContentSizes(
    axisPx: Float,
    headerPx: Float,
    columns: Int,
    rows: Float,
    cellWidthPx: Float,
    pxPerMinute: Float,
): Pair<Float, Float> {
    val width = (axisPx + columns.coerceAtLeast(0) * cellWidthPx.coerceAtLeast(0f))
        .coerceIn(0f, GUIDE_MAX_CONTENT_PX)
    val height = (headerPx + rows.coerceAtLeast(0f) * pxPerMinute.coerceAtLeast(0f))
        .coerceIn(0f, GUIDE_MAX_CONTENT_PX)
    return width to height
}

fun maxScrollOffset(contentPx: Float, viewportPx: Float): Int =
    (contentPx - viewportPx).toInt().coerceIn(0, GUIDE_MAX_CONTENT_PX.toInt())

fun clampScrollOffset(target: Float, contentPx: Float, viewportPx: Float): Int {
    val limit = maxScrollOffset(contentPx, viewportPx)
    if (target.isNaN()) return 0
    if (target <= 0f) return 0
    if (target >= limit.toFloat()) return limit
    return target.toInt()
}

fun scrollOffsetToView(contentPx: Float, scrollPx: Float): Float =
    contentPx - scrollPx

fun gridLineX(axisPx: Float, column: Int, cellWidthPx: Float, scrollPx: Float): Float =
    axisPx + column * cellWidthPx - scrollPx

fun rowLineY(headerPx: Float, minutes: Float, mapper: TimelineMapper, scrollPx: Float): Float =
    headerPx + mapper.y(minutes) - scrollPx

fun cellTopY(headerPx: Float, mapper: TimelineMapper, startMin: Float, scrollPx: Float): Float =
    headerPx + mapper.y(startMin) - scrollPx

fun cellIntersectsViewport(
    topY: Float,
    heightPx: Float,
    headerPx: Float,
    viewportHeightPx: Float,
): Boolean = topY + heightPx >= headerPx && topY <= viewportHeightPx
