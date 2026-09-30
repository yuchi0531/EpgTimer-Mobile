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
import androidx.compose.ui.unit.sp
import java.time.Duration
import java.time.LocalDateTime

internal const val GUIDE_MIN_TEXT_HEIGHT_PX = 14f
internal const val GUIDE_BAND_WIDTH_DP = 4
internal const val GUIDE_TEXT_PAD_DP = 4
internal const val GUIDE_AXIS_WIDTH_DP = 40
internal const val GUIDE_AXIS_DATE_FONT_SIZE_SP = 9f
internal const val GUIDE_CELL_MIN_HEIGHT_PX = 2f
internal const val GUIDE_MEASURE_MIN_WIDTH_PX = 24
internal const val GUIDE_CELL_MAX_LINES = 2
internal const val GUIDE_CELL_LIGHT_GENRE_ALPHA = 0.18f
internal const val GUIDE_CELL_DARK_GENRE_ALPHA = 0.28f
internal const val GUIDE_GENRE_LEVEL2_DARKEN_STEP = 0.012f
internal const val GENRE_UNKNOWN_LEVEL2 = 0xFF

internal val GUIDE_CELL_FONT_SIZES_SP: List<Float> = listOf(12f, 11f, 10f, 9f, 8f)

internal val GUIDE_CELL_MIN_FONT_SIZE_SP: Float = GUIDE_CELL_FONT_SIZES_SP.last()

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

class TimeSegment(val start: Float, val end: Float)

class TimelineMapper(
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

fun gridColumnAt(xInView: Float, originPx: Float, cellWidthPx: Float, count: Int): Int {
    if (xInView < originPx) return -1
    val column = ((xInView - originPx) / cellWidthPx).toInt()
    return if (column in 0 until count) column else -1
}

fun guideGroupWidths(cellWidthPx: Float, spans: IntArray): FloatArray =
    FloatArray(spans.size) { cellWidthPx * spans[it].coerceAtLeast(1) }

fun effectiveSpans(spans: IntArray, cellWidthPx: Float, minMemberWidthPx: Float): IntArray =
    IntArray(spans.size) { index ->
        val group = spans[index].coerceAtLeast(1)
        if (group <= 1 || cellWidthPx / group >= minMemberWidthPx) group else 1
    }

fun guideGroupColumnStarts(axisPx: Float, widthsPx: FloatArray): FloatArray {
    val starts = FloatArray(widthsPx.size)
    var acc = axisPx
    for (index in widthsPx.indices) {
        starts[index] = acc
        acc += widthsPx[index]
    }
    return starts
}

fun visibleGroupColumns(
    startsPx: FloatArray,
    widthsPx: FloatArray,
    scrollX: Float,
    viewportWidth: Float,
): IntRange {
    val count = widthsPx.size
    if (count == 0) return 0..-1
    val from = scrollX
    val to = scrollX + viewportWidth
    var first = count
    var last = -1
    for (index in 0 until count) {
        val start = startsPx[index]
        val end = start + widthsPx[index]
        if (end > from && start < to) {
            if (index < first) first = index
            last = index
        }
    }
    return if (last < 0) 0..-1 else first..last
}

fun groupColumnAt(startsPx: FloatArray, widthsPx: FloatArray, contentX: Float): Int {
    for (index in widthsPx.indices) {
        if (contentX >= startsPx[index] && contentX < startsPx[index] + widthsPx[index]) return index
    }
    return -1
}

fun cellOverlapSlot(
    startsMinutes: FloatArray,
    endsMinutes: FloatArray,
    startMin: Float,
    endMin: Float,
    epsilonMin: Float,
): Int {
    var slot = 0
    for (index in startsMinutes.indices) {
        if (startsMinutes[index] < endMin - epsilonMin && endsMinutes[index] > startMin + epsilonMin) {
            slot = maxOf(slot, cellOverlapSlot(startsMinutes, endsMinutes, index, epsilonMin) + 1)
        }
    }
    return slot
}

private fun cellOverlapSlot(
    startsMinutes: FloatArray,
    endsMinutes: FloatArray,
    index: Int,
    epsilonMin: Float,
): Int {
    var slot = 0
    for (other in 0 until index) {
        if (startsMinutes[other] < endsMinutes[index] - epsilonMin &&
            endsMinutes[other] > startsMinutes[index] + epsilonMin
        ) {
            slot = maxOf(slot, cellOverlapSlot(startsMinutes, endsMinutes, other, epsilonMin) + 1)
        }
    }
    return slot
}

fun cellOverlapSlots(
    startsMinutes: FloatArray,
    endsMinutes: FloatArray,
    epsilonMin: Float,
): IntArray = IntArray(startsMinutes.size) {
    cellOverlapSlot(startsMinutes, endsMinutes, it, epsilonMin)
}

fun guideFittedTextFontSizes(
    maxHeightPx: Float,
    lineHeightPx: Float,
    fontSizesSp: List<Float>,
): List<Float> {
    val smallest = fontSizesSp.lastOrNull() ?: return fontSizesSp
    val largest = fontSizesSp.first()
    if (lineHeightPx <= 0f) return fontSizesSp
    val fitting = if (maxHeightPx <= 0f) {
        emptyList()
    } else {
        fontSizesSp.filter { it <= largest * maxHeightPx / lineHeightPx }
    }
    return fitting.ifEmpty { listOf(smallest) }
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
    if (title.isBlank() || maxHeight <= 0f) return
    val measureWidth = maxOf(maxWidth, GUIDE_MEASURE_MIN_WIDTH_PX)
    val text = AnnotatedString(title)
    val lineHeightPx = textMeasurer.measure(
        text = text,
        style = style,
        maxLines = 1,
        constraints = Constraints(maxWidth = measureWidth),
    ).size.height.toFloat()
    val fontSizes = guideFittedTextFontSizes(maxHeight, lineHeightPx, GUIDE_CELL_FONT_SIZES_SP)
    val fits = { candidate: TextLayoutResult -> candidate.size.height <= maxHeight }
    val single = { fontSizeSp: Float ->
        textMeasurer.measure(
            text = text,
            style = style.copy(fontSize = fontSizeSp.sp),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            constraints = Constraints(maxWidth = measureWidth),
        )
    }
    for (fontSizeSp in fontSizes) {
        val oneLine = single(fontSizeSp)
        if (fits(oneLine)) {
            draw(oneLine, topLeft)
            return
        }
    }
    for (fontSizeSp in fontSizes) {
        val twoLines = textMeasurer.measure(
            text = text,
            style = style.copy(fontSize = fontSizeSp.sp),
            maxLines = GUIDE_CELL_MAX_LINES,
            overflow = TextOverflow.Ellipsis,
            constraints = Constraints(maxWidth = measureWidth),
        )
        if (fits(twoLines)) {
            draw(twoLines, topLeft)
            return
        }
    }
    draw(single(fontSizes.last()), topLeft)
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

fun genreTone(base: Color, steps: Int): Color {
    val ratio = (1f - steps * GUIDE_GENRE_LEVEL2_DARKEN_STEP).coerceIn(0.5f, 1f)
    return Color(
        red = base.red * ratio,
        green = base.green * ratio,
        blue = base.blue * ratio,
        alpha = base.alpha,
    )
}

fun genreColor(nibbleLevel1: Int, nibbleLevel2: Int): Color {
    val base = GENRE_COLORS[nibbleLevel1] ?: return Color(0xFFEEEEEE)
    if (nibbleLevel2 == GENRE_UNKNOWN_LEVEL2) return base
    return genreTone(base, (nibbleLevel2 and 0x0F) + 1)
}

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

fun guideContentSizes(
    axisPx: Float,
    headerPx: Float,
    columns: Int,
    timelineHeightPx: Float,
    cellWidthPx: Float,
): Pair<Float, Float> {
    val width = (axisPx + columns.coerceAtLeast(0) * cellWidthPx.coerceAtLeast(0f))
        .coerceIn(0f, GUIDE_MAX_CONTENT_PX)
    val height = (headerPx + timelineHeightPx.coerceAtLeast(0f))
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

private val HOUR_LABELS: Array<String> = Array(24) { "%02d:00".format(it) }

fun hourLabel(hour: Int): String = HOUR_LABELS[((hour % 24) + 24) % 24]
