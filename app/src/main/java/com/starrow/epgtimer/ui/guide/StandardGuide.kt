package com.starrow.epgtimer.ui.guide

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.starrow.epgtimer.data.guide.ServiceGroup
import com.starrow.epgtimer.data.model.ServiceInfo
import com.starrow.epgtimer.data.repository.GuideData
import com.starrow.epgtimer.data.repository.GuideEvent
import com.starrow.epgtimer.ui.dateLabel
import com.starrow.epgtimer.util.EpgClock
import java.time.LocalDate
import java.time.LocalDateTime

private const val GUIDE_HEADER_HEIGHT_DP = 52
private const val GUIDE_MIN_CELL_WIDTH_DP = 96
private const val GUIDE_CELL_MEMBER_RATIO_DP = 72
private const val GUIDE_MIN_SLOT_HEIGHT_DP = 40
private const val GUIDE_OVERLAP_EPSILON_MIN = 0.01f
private const val GUIDE_LOGO_MIN_CELL_WIDTH_DP = 140
private const val GUIDE_LOGO_SIDE_PAD_DP = 8f
private const val GUIDE_LOGO_AREA_RATIO = 0.5f
private const val GUIDE_DAYS = 7
private const val GUIDE_HOURS_PER_DAY = 24

private data class StandardCell(
    val startMin: Float,
    val endMin: Float,
    val y0: Float,
    val y1: Float,
    val level1: Int,
    val level2: Int,
    val event: GuideEvent,
    val memberIndex: Int,
    val columnIndex: Int,
    val columnCount: Int,
)

private class StandardGrid(
    val mapper: TimelineMapper,
    val cellsByColumn: List<List<StandardCell>>,
    val groupStartsPx: FloatArray,
    val groupWidthsPx: FloatArray,
    val spans: IntArray,
    val contentWidth: Float,
    val contentHeight: Float,
)

fun eventDurationMinutes(event: com.starrow.epgtimer.data.model.EpgEvent): Float =
    (if (event.durationSeconds > 0) event.durationSeconds else 1800) / 60f

private fun serviceOf(guideEvent: GuideEvent, group: ServiceGroup): ServiceInfo =
    group.members.firstOrNull { it.key == guideEvent.event.serviceKey } ?: group.primary

private fun buildStandardGrid(
    data: GuideData,
    origin: LocalDateTime,
    originMin: Float,
    windowEndMin: Float,
    pxPerMinute: Float,
    cellWidthPx: Float,
    axisPx: Float,
    headerPx: Float,
    collapse: Boolean,
    minMemberWidthPx: Float,
    minSlotHeightPx: Float,
): StandardGrid {
    val groups = data.serviceGroups
    val busy = mutableListOf<Pair<Float, Float>>()
    for (list in data.eventsByService.values) {
        for (guideEvent in list) {
            val start = guideEvent.event.startDateTime ?: continue
            val startMin = minutesBetween(origin, start)
            busy.add(Pair(startMin, startMin + eventDurationMinutes(guideEvent.event)))
        }
    }
    val mapper = TimelineMapper(
        end = windowEndMin,
        segments = buildTimeline(originMin, windowEndMin, busy, collapse),
        pxPerMinute = pxPerMinute,
    )
    val cellsByColumn = ArrayList<List<StandardCell>>(groups.size)
    for (group in groups) {
        val raw = ArrayList<StandardCell>()
        for (memberIndex in group.members.indices) {
            val member = group.members[memberIndex]
            for (guideEvent in data.eventsByService[member.key].orEmpty()) {
                val start = guideEvent.event.startDateTime ?: continue
                val startMin = minutesBetween(origin, start)
                val endMin = startMin + eventDurationMinutes(guideEvent.event)
                if (endMin <= originMin || startMin >= windowEndMin) continue
                val nibble = guideEvent.event.contentInfo?.nibbleList?.firstOrNull()
                val level1 = nibble?.nibbleLevel1 ?: 0x0F
                val level2 = nibble?.nibbleLevel2 ?: GENRE_UNKNOWN_LEVEL2
                val clampedStart = startMin.coerceAtLeast(originMin)
                val clampedEnd = endMin.coerceAtMost(windowEndMin)
                raw.add(
                    StandardCell(
                        startMin = clampedStart,
                        endMin = clampedEnd,
                        y0 = mapper.y(clampedStart),
                        y1 = mapper.y(clampedEnd),
                        level1 = level1,
                        level2 = level2,
                        event = guideEvent,
                        memberIndex = memberIndex,
                        columnIndex = memberIndex,
                        columnCount = group.members.size,
                    ),
                )
            }
        }
        raw.sortBy { it.startMin }
        val starts = FloatArray(raw.size) { raw[it].startMin }
        val ends = FloatArray(raw.size) { raw[it].endMin }
        val lanes = cellOverlapSlots(starts, ends, GUIDE_OVERLAP_EPSILON_MIN)
        val minLaneHeight = minSlotHeightPx.coerceAtLeast(pxPerMinute)
        val cells = raw.mapIndexed { index, cell ->
            val laneCount = (lanes.count { it == cell.memberIndex } + 1).coerceAtLeast(1)
            val laneIndex = lanes.take(index + 1).count { it == cell.memberIndex }
            if (laneCount <= 1 || (cell.y1 - cell.y0) / laneCount >= minLaneHeight) {
                cell
            } else {
                cell.copy(columnIndex = laneIndex, columnCount = laneCount)
            }
        }
        cellsByColumn.add(cells)
    }
    val spans = effectiveSpans(IntArray(groups.size) { groups[it].span }, cellWidthPx, minMemberWidthPx)
    val groupWidthsPx = guideGroupWidths(cellWidthPx, spans)
    val groupStartsPx = guideGroupColumnStarts(axisPx, groupWidthsPx)
    val contentWidth = (axisPx + groupWidthsPx.sum()).coerceIn(0f, GUIDE_MAX_CONTENT_PX)
    val contentHeight = (headerPx + mapper.totalPx).coerceIn(0f, GUIDE_MAX_CONTENT_PX)
    val aligned = if (spans.contentEquals(IntArray(spans.size) { groups[it].span })) {
        cellsByColumn
    } else {
        val single = ArrayList<List<StandardCell>>(cellsByColumn.size)
        for ((index, cells) in cellsByColumn.withIndex()) {
            val lanes = spans[index].coerceAtLeast(1)
            single.add(
                if (lanes == 1) {
                    cells.map { it.copy(columnIndex = 0, columnCount = 1) }
                } else {
                    cells
                },
            )
        }
        single
    }
    return StandardGrid(mapper, aligned, groupStartsPx, groupWidthsPx, spans, contentWidth, contentHeight)
}

internal fun genreCellAlpha(background: Color): Float =
    if (background.luminance() < 0.5f) GUIDE_CELL_DARK_GENRE_ALPHA else GUIDE_CELL_LIGHT_GENRE_ALPHA

internal fun DrawScope.drawStandardCellRect(
    colors: ColorScheme,
    level1: Int,
    level2: Int,
    dimmed: Boolean,
    x: Float,
    top: Float,
    cellW: Float,
    height: Float,
    bandPx: Float,
) {
    val alpha = if (dimmed) 0.45f else 1f
    val rectSize = Size((cellW - 2f).coerceAtLeast(1f), height)
    val topLeft = Offset(x + 1f, top)
    val genre = genreColor(level1, level2)
    drawRect(color = colors.surface.copy(alpha = alpha), topLeft = topLeft, size = rectSize)
    drawRect(
        color = genre.copy(alpha = genreCellAlpha(colors.background) * alpha),
        topLeft = topLeft,
        size = rectSize,
    )
    drawRect(
        color = genre.copy(alpha = alpha),
        topLeft = topLeft,
        size = Size(bandPx.coerceAtMost(rectSize.width), height),
    )
    drawRect(
        color = colors.outlineVariant.copy(alpha = alpha),
        topLeft = topLeft,
        size = rectSize,
        style = Stroke(width = 1f),
    )
}

private fun DrawScope.drawLogoFitted(
    image: ImageBitmap,
    cellX: Float,
    cellW: Float,
    areaTop: Float,
    areaH: Float,
) {
    val imageW = image.width
    val imageH = image.height
    if (imageW <= 0 || imageH <= 0 || areaH <= 0f) return
    val maxW = (cellW - GUIDE_LOGO_SIDE_PAD_DP * density).coerceAtLeast(1f)
    val scale = minOf(maxW / imageW, areaH / imageH)
    val drawW = imageW * scale
    val drawH = imageH * scale
    drawImage(
        image = image,
        srcOffset = IntOffset.Zero,
        srcSize = IntSize(imageW, imageH),
        dstOffset = IntOffset(
            (cellX + (cellW - drawW) / 2f).toInt(),
            (areaTop + (areaH - drawH) / 2f).toInt(),
        ),
        dstSize = IntSize(drawW.toInt().coerceAtLeast(1), drawH.toInt().coerceAtLeast(1)),
    )
}

@Composable
fun StandardGuide(
    data: GuideData,
    weekStart: LocalDate,
    hourHeightDp: Int,
    collapse: Boolean,
    logos: Map<String, ImageBitmap> = emptyMap(),
    onEventClick: (GuideEvent, ServiceInfo) -> Unit,
) {
    if (data.serviceGroups.isEmpty()) {
        GuideEmptyMessage("サービスがありません")
        return
    }
    val groups = data.serviceGroups
    val density = LocalDensity.current
    val colors = MaterialTheme.colorScheme
    val textMeasurer = rememberTextMeasurer()
    var scrollX by remember { mutableFloatStateOf(0f) }
    var scrollY by remember { mutableFloatStateOf(0f) }
    val origin = weekStart.atStartOfDay()
    val originMin = 0f
    val windowEndMin = GUIDE_DAYS * GUIDE_MINUTES_PER_DAY
    val axisWidth = GUIDE_AXIS_WIDTH_DP.dp
    val headerHeight = GUIDE_HEADER_HEIGHT_DP.dp
    val textPad = with(density) { GUIDE_TEXT_PAD_DP.dp.toPx() }
    val bandPx = with(density) { GUIDE_BAND_WIDTH_DP.dp.toPx() }
    val headerLabelPadPx = with(density) { 4.dp.toPx() }
    val headerTextInsetPx = with(density) { 12.dp.toPx() }
    val cellTitleStyle = TextStyle(fontSize = 12.sp, color = colors.onSurface)
    val cellTitleStyleDimmed = cellTitleStyle.copy(color = colors.onSurface.copy(alpha = 0.45f))
    val now = remember(data) { EpgClock.now() }
    val nowMin = minutesBetween(origin, now)
    val nowInWindow = nowMin in originMin..windowEndMin

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val viewportW = maxWidth
        val viewportH = maxHeight
        val cellWidth = maxOf(GUIDE_MIN_CELL_WIDTH_DP.dp, viewportW / groups.size)
        val axisPx = with(density) { axisWidth.toPx() }
        val headerPx = with(density) { headerHeight.toPx() }
        val cellW = with(density) { cellWidth.toPx() }
        val viewportWPx = with(density) { viewportW.toPx() }
        val viewportHPx = with(density) { viewportH.toPx() }
        val pxPerMinute = guidePpxPerMinute(with(density) { hourHeightDp.dp.toPx() })
        val minMemberWidthPx = with(density) { (GUIDE_CELL_MEMBER_RATIO_DP.dp).toPx() }
        val minSlotHeightPx = with(density) { GUIDE_MIN_SLOT_HEIGHT_DP.dp.toPx() }
        val grid = remember(data, weekStart, hourHeightDp, collapse, cellWidth, density) {
            buildStandardGrid(
                data = data,
                origin = origin,
                originMin = originMin,
                windowEndMin = windowEndMin,
                pxPerMinute = pxPerMinute,
                cellWidthPx = cellW,
                axisPx = axisPx,
                headerPx = headerPx,
                collapse = collapse,
                minMemberWidthPx = minMemberWidthPx,
                minSlotHeightPx = minSlotHeightPx,
            )
        }
        val maxScrollX = maxScrollOffset(grid.contentWidth, viewportWPx)
        val maxScrollY = maxScrollOffset(grid.contentHeight, viewportHPx)

        val currentGridState = rememberUpdatedState(grid)
        val currentGroupsState = rememberUpdatedState(groups)
        val currentMaxScrollXState = rememberUpdatedState(maxScrollX.toFloat())
        val currentMaxScrollYState = rememberUpdatedState(maxScrollY.toFloat())

        LaunchedEffect(grid, viewportHPx) {
            val target = if (nowInWindow && grid.mapper.isKept(nowMin)) {
                headerPx + grid.mapper.y(nowMin) - viewportHPx / 2f
            } else {
                0f
            }
            scrollY = clampScrollOffset(target, grid.contentHeight, viewportHPx).toFloat()
        }

        Box(Modifier.fillMaxSize()) {
            Canvas(
                Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        detectTapGestures { offset ->
                            val currentGrid = currentGridState.value
                            val currentGroups = currentGroupsState.value
                            val column = groupColumnAt(
                                currentGrid.groupStartsPx,
                                currentGrid.groupWidthsPx,
                                scrollX + offset.x,
                            )
                            if (column < 0) return@detectTapGestures
                            val group = currentGroups[column]
                            val span = currentGrid.spans.getOrElse(column) { 1 }
                            val laneWidth = currentGrid.groupWidthsPx[column] / span
                            val memberIndex = (
                                ((scrollX + offset.x - currentGrid.groupStartsPx[column]) / laneWidth).toInt()
                            ).coerceIn(0, span - 1)
                            val minutes = currentGrid.mapper.minutesAt(scrollY + offset.y - headerPx)
                            currentGrid.cellsByColumn[column]
                                .firstOrNull { cell ->
                                    cell.columnIndex == memberIndex &&
                                        minutes >= cell.startMin && minutes < cell.endMin
                                }
                                ?.let { cell ->
                                    val service = serviceOf(cell.event, group)
                                    onEventClick(cell.event, service)
                                }
                        }
                    }
                    .pointerInput(Unit) {
                        val currentMaxX = currentMaxScrollXState.value
                        val currentMaxY = currentMaxScrollYState.value
                        detectTransformGestures { _, pan, _, _ ->
                            scrollX = (scrollX - pan.x).coerceIn(0f, currentMaxX)
                            scrollY = (scrollY - pan.y).coerceIn(0f, currentMaxY)
                        }
                    },
            ) {
                val drawScrollX = scrollX
                val drawScrollY = scrollY
                clipRect(left = 0f, top = headerPx, right = size.width, bottom = size.height) {
                drawRect(color = colors.surface, topLeft = Offset(0f, headerPx), size = Size(size.width, size.height - headerPx))
                val columns = visibleGroupColumns(grid.groupStartsPx, grid.groupWidthsPx, drawScrollX, size.width)

                for (column in columns) {
                    val x = grid.groupStartsPx[column] - drawScrollX
                    drawLine(
                        color = colors.outlineVariant,
                        start = Offset(x, headerPx),
                        end = Offset(x, size.height),
                        strokeWidth = 1f,
                    )
                }

                for (day in 0 until GUIDE_DAYS) {
                    for (hour in 0 until GUIDE_HOURS_PER_DAY) {
                        val minutes = day * GUIDE_MINUTES_PER_DAY + hour * 60f
                        if (!grid.mapper.isKept(minutes)) continue
                        val y = rowLineY(headerPx, minutes, grid.mapper, drawScrollY)
                        if (y < headerPx - 1f || y > size.height) continue
                        val midnight = hour == 0
                        drawLine(
                            color = if (midnight) colors.outline else colors.outlineVariant,
                            start = Offset(axisPx, y),
                            end = Offset(size.width, y),
                            strokeWidth = if (midnight) 2f else 1f,
                            cap = StrokeCap.Butt,
                        )
                    }
                }

                for (column in columns) {
                    val groupX = grid.groupStartsPx[column] - drawScrollX
                    val groupWidth = grid.groupWidthsPx[column]
                    val span = grid.spans[column].coerceAtLeast(1)
                    val laneWidth = groupWidth / span
                    for (cell in grid.cellsByColumn[column]) {
                        val count = cell.columnCount.coerceAtLeast(1)
                        val slotWidth = laneWidth / count
                        val cellX = groupX + cell.columnIndex * slotWidth
                        val top = cellTopY(headerPx, grid.mapper, cell.startMin, drawScrollY)
                        val height = (cell.y1 - cell.y0).coerceAtLeast(GUIDE_CELL_MIN_HEIGHT_PX)
                        if (!cellIntersectsViewport(top, height, headerPx, size.height)) continue
                        clipRect(
                            left = 0f,
                            top = headerPx,
                            right = size.width,
                            bottom = size.height,
                        ) {
                            drawStandardCellRect(
                                colors = colors,
                                level1 = cell.level1,
                                level2 = cell.level2,
                                dimmed = cell.event.dimmed,
                                x = cellX,
                                top = top,
                                cellW = slotWidth,
                                height = height,
                                bandPx = bandPx,
                            )
                            drawFittedCellText(
                                textMeasurer = textMeasurer,
                                title = cell.event.event.title,
                                maxWidth = (slotWidth - 2f - bandPx - textPad * 2).toInt(),
                                maxHeight = height - textPad,
                                style = if (cell.event.dimmed) cellTitleStyleDimmed else cellTitleStyle,
                                topLeft = Offset(cellX + 1f + bandPx + textPad, top + textPad / 2f),
                            ) { layout, position ->
                                drawText(layout, topLeft = position)
                            }
                        }
                    }
                }

                if (nowInWindow && grid.mapper.isKept(nowMin)) {
                    val y = rowLineY(headerPx, nowMin, grid.mapper, drawScrollY)
                    if (y in headerPx..size.height) {
                        drawLine(
                            color = Color(0xFFF44336),
                            start = Offset(axisPx, y),
                            end = Offset(size.width, y),
                            strokeWidth = 2f,
                        )
                    }
                }

                drawLine(
                    color = colors.outlineVariant,
                    start = Offset(axisPx, headerPx),
                    end = Offset(axisPx, size.height),
                    strokeWidth = 1f,
                )
                }
            }
            Canvas(
                Modifier
                    .align(Alignment.TopStart)
                    .offset(y = headerHeight)
                    .requiredSize(axisWidth, viewportH - headerHeight)
                    .clipToBounds(),
            ) {
                val drawScrollY = scrollY
                drawRect(color = colors.surface, topLeft = Offset.Zero, size = Size(size.width, size.height))
                drawLine(
                    color = colors.outlineVariant,
                    start = Offset(size.width - 1f, 0f),
                    end = Offset(size.width - 1f, size.height),
                    strokeWidth = 1f,
                )
                for (day in 0 until GUIDE_DAYS) {
                    for (hour in 0 until GUIDE_HOURS_PER_DAY) {
                        val minutes = day * GUIDE_MINUTES_PER_DAY + hour * 60f
                        if (!grid.mapper.isKept(minutes)) continue
                        val y = rowLineY(0f, minutes, grid.mapper, drawScrollY)
                        if (y < -24f || y > size.height) continue
                        val midnight = hour == 0
                        val label = if (midnight) dateLabel(weekStart.plusDays(day.toLong())) else hourLabel(hour)
                        val layout = textMeasurer.measure(
                            text = AnnotatedString(label),
                            style = TextStyle(
                                fontSize = if (midnight) GUIDE_AXIS_DATE_FONT_SIZE_SP.sp else 11.sp,
                                color = if (midnight) colors.primary else colors.onSurfaceVariant,
                                fontWeight = if (midnight) FontWeight.Bold else FontWeight.Normal,
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            constraints = Constraints(maxWidth = (axisPx - headerLabelPadPx * 2f).toInt().coerceAtLeast(1)),
                        )
                        drawText(layout, topLeft = Offset(headerLabelPadPx, y + 2f))
                    }
                }
            }
            Canvas(
                Modifier
                    .align(Alignment.TopStart)
                    .requiredSize(viewportW, headerHeight)
                    .clipToBounds(),
            ) {
                val drawScrollX = scrollX
                drawRect(color = colors.surface, topLeft = Offset.Zero, size = Size(size.width, size.height))
                drawRect(
                    color = colors.surfaceVariant,
                    topLeft = Offset.Zero,
                    size = Size(axisPx, size.height),
                )
                drawLine(
                    color = colors.outlineVariant,
                    start = Offset(0f, size.height - 1f),
                    end = Offset(size.width, size.height - 1f),
                    strokeWidth = 1f,
                )
                val columns = visibleGroupColumns(grid.groupStartsPx, grid.groupWidthsPx, drawScrollX, size.width)
                val logoMinWidthPx = with(density) { GUIDE_LOGO_MIN_CELL_WIDTH_DP.dp.toPx() }
                val logoAreaH = size.height * GUIDE_LOGO_AREA_RATIO
                for (column in columns) {
                    val x = grid.groupStartsPx[column] - drawScrollX
                    val columnWidth = grid.groupWidthsPx[column]
                    if (x + columnWidth < 0f || x > size.width) continue
                    drawLine(
                        color = colors.outlineVariant,
                        start = Offset(x, 0f),
                        end = Offset(x, size.height),
                        strokeWidth = 1f,
                    )
                    val service = groups[column].primary
                    val span = grid.spans[column].coerceAtLeast(1)
                    val logo = if (columnWidth >= logoMinWidthPx) logos[service.key.toString(16)] else null
                    val maxLines = if (logo == null) 2 else 1
                    val textCenterY = if (logo == null) {
                        size.height / 2f
                    } else {
                        size.height - logoAreaH / 2f
                    }
                    if (logo != null) {
                        drawLogoFitted(
                            image = logo,
                            cellX = x,
                            cellW = columnWidth,
                            areaTop = (size.height - logoAreaH) / 2f,
                            areaH = logoAreaH,
                        )
                    }
                    val layout = textMeasurer.measure(
                        text = AnnotatedString(service.serviceName),
                        style = TextStyle(
                            fontSize = 12.sp,
                            color = colors.onSurface,
                            textAlign = TextAlign.Center,
                        ),
                        maxLines = maxLines,
                        overflow = TextOverflow.Ellipsis,
                        constraints = Constraints(maxWidth = (columnWidth - headerTextInsetPx).toInt().coerceAtLeast(1)),
                    )
                    drawText(
                        layout,
                        topLeft = Offset(
                            x + (columnWidth - layout.size.width) / 2f,
                            textCenterY - layout.size.height / 2f,
                        ),
                    )
                    if (span > 1) {
                        val spanLabel = textMeasurer.measure(
                            text = AnnotatedString("+${span - 1}"),
                            style = TextStyle(fontSize = 10.sp, color = colors.onSurfaceVariant),
                            maxLines = 1,
                            constraints = Constraints(maxWidth = (columnWidth - headerTextInsetPx).toInt().coerceAtLeast(1)),
                        )
                        drawText(
                            spanLabel,
                            topLeft = Offset(
                                x + (columnWidth - spanLabel.size.width) / 2f,
                                textCenterY + layout.size.height / 2f,
                            ),
                        )
                    }
                }
            }
        }
    }
}
