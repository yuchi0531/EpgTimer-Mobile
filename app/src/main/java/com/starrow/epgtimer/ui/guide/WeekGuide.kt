package com.starrow.epgtimer.ui.guide

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.starrow.epgtimer.data.guide.GuideEngine
import com.starrow.epgtimer.data.guide.WeekLayout
import com.starrow.epgtimer.data.model.ServiceInfo
import com.starrow.epgtimer.data.repository.GuideData
import com.starrow.epgtimer.data.repository.GuideEvent
import com.starrow.epgtimer.ui.dateLabel
import com.starrow.epgtimer.util.EpgClock
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

private const val WEEK_HEADER_HEIGHT_DP = 48
private const val WEEK_MIN_CELL_WIDTH_DP = 88
private const val WEEK_CYCLE_MINUTES = GUIDE_MINUTES_PER_DAY
private const val WEEK_HOURS_PER_CYCLE = 24

private class WeekCell(
    val startMin: Float,
    val endMin: Float,
    val y0: Float,
    val y1: Float,
    val level1: Int,
    val event: GuideEvent,
)

private class WeekGrid(
    val mapper: TimelineMapper,
    val cellsByColumn: List<List<WeekCell>>,
    val contentWidth: Float,
    val contentHeight: Float,
)

private fun buildWeekGrid(
    data: GuideData,
    days: List<LocalDate>,
    startHour: Int,
    engine: GuideEngine,
    serviceIndex: Int,
    originCycle: Float,
    endCycle: Float,
    pxPerMinute: Float,
    cellWidthPx: Float,
    axisPx: Float,
    headerPx: Float,
    collapse: Boolean,
): WeekGrid {
    val service = data.services.getOrNull(serviceIndex)
    val events = if (service == null) emptyList() else data.eventsByService[service.key].orEmpty()
    val dayIndex = days.withIndex().associate { (index, day) -> day to index }
    val busy = mutableListOf<Pair<Float, Float>>()
    val pending = ArrayList<List<GuideEvent>>(days.size)
    repeat(days.size) { pending.add(emptyList()) }
    for (guideEvent in events) {
        val start = guideEvent.event.startDateTime ?: continue
        val column = engine.eventDayColumn(guideEvent.event, startHour)
        val index = dayIndex[column] ?: continue
        val cycleStart = LocalDateTime.of(column, LocalTime.of(startHour, 0))
        val startMin = minutesBetween(cycleStart, start)
        if (startMin < 0f || startMin >= WEEK_CYCLE_MINUTES) continue
        val endMin = (startMin + eventDurationMinutes(guideEvent.event)).coerceAtMost(WEEK_CYCLE_MINUTES)
        busy.add(Pair(startMin, endMin))
        val list = pending[index]
        pending[index] = list + guideEvent
    }
    val mapper = TimelineMapper(
        origin = originCycle,
        end = endCycle,
        segments = buildTimeline(originCycle, endCycle, busy, collapse),
        pxPerMinute = pxPerMinute,
    )
    val columnEvents = ArrayList<List<WeekCell>>(days.size)
    for ((index, list) in pending.withIndex()) {
        val cells = ArrayList<WeekCell>(list.size)
        for (guideEvent in list) {
            val start = guideEvent.event.startDateTime ?: continue
            val column = days[index]
            val cycleStart = LocalDateTime.of(column, LocalTime.of(startHour, 0))
            val startMin = minutesBetween(cycleStart, start).coerceAtLeast(originCycle)
            val endMin = (startMin + eventDurationMinutes(guideEvent.event))
                .coerceAtMost(endCycle)
                .coerceAtLeast(startMin + 1f)
            if (endMin <= originCycle) continue
            val level1 = guideEvent.event.contentInfo?.nibbleList?.firstOrNull()?.nibbleLevel1 ?: 0x0F
            cells.add(
                WeekCell(
                    startMin = startMin,
                    endMin = endMin,
                    y0 = mapper.y(startMin),
                    y1 = mapper.y(endMin),
                    level1 = level1,
                    event = guideEvent,
                ),
            )
        }
        columnEvents.add(cells)
    }
    val (contentWidth, contentHeight) = guideContentSizes(
        axisPx = axisPx,
        headerPx = headerPx,
        columns = days.size,
        rows = endCycle - originCycle,
        cellWidthPx = cellWidthPx,
        pxPerMinute = pxPerMinute,
    )
    return WeekGrid(mapper, columnEvents, contentWidth, contentHeight)
}

@Composable
fun WeekGuide(
    data: GuideData,
    weekLayout: WeekLayout,
    hourHeightDp: Int,
    collapse: Boolean,
    serviceIndex: Int,
    onEventClick: (GuideEvent, ServiceInfo) -> Unit,
) {
    val days = weekLayout.days
    if (data.services.isEmpty() || days.isEmpty() || serviceIndex !in data.services.indices) {
        GuideEmptyMessage("サービスがありません")
        return
    }
    val service = data.services[serviceIndex]
    val density = LocalDensity.current
    val colors = MaterialTheme.colorScheme
    val textMeasurer = rememberTextMeasurer()
    var scrollX by remember { mutableFloatStateOf(0f) }
    var scrollY by remember { mutableFloatStateOf(0f) }
    val engine = remember { GuideEngine() }
    val startHour = weekLayout.startHour
    val originCycle = 0f
    val endCycle = WEEK_CYCLE_MINUTES
    val axisWidth = GUIDE_AXIS_WIDTH_DP.dp
    val headerHeight = WEEK_HEADER_HEIGHT_DP.dp
    val bandPx = with(density) { GUIDE_BAND_WIDTH_DP.dp.toPx() }
    val textPad = with(density) { GUIDE_TEXT_PAD_DP.dp.toPx() }
    val axisLabelPadPx = with(density) { 4.dp.toPx() }
    val headerTextInsetPx = with(density) { 12.dp.toPx() }
    val now = remember(data) { EpgClock.now() }
    val today = remember(data) { now.toLocalDate() }
    val nowColumn = if (now.hour < startHour) today.minusDays(1) else today
    val nowIndex = days.indexOf(nowColumn)
    val nowCycleMin = if (nowIndex >= 0) {
        minutesBetween(LocalDateTime.of(nowColumn, LocalTime.of(startHour, 0)), now)
    } else {
        -1f
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val viewportW = maxWidth
        val viewportH = maxHeight
        val cellWidth = maxOf(WEEK_MIN_CELL_WIDTH_DP.dp, viewportW / days.size)
        val axisPx = with(density) { axisWidth.toPx() }
        val headerPx = with(density) { headerHeight.toPx() }
        val cellW = with(density) { cellWidth.toPx() }
        val viewportWPx = with(density) { viewportW.toPx() }
        val viewportHPx = with(density) { viewportH.toPx() }
        val pxPerMinute = guidePpxPerMinute(with(density) { hourHeightDp.dp.toPx() })
        val grid = remember(data, weekLayout, hourHeightDp, collapse, serviceIndex, cellWidth, density) {
            buildWeekGrid(
                data = data,
                days = days,
                startHour = startHour,
                engine = engine,
                serviceIndex = serviceIndex,
                originCycle = originCycle,
                endCycle = endCycle,
                pxPerMinute = pxPerMinute,
                cellWidthPx = cellW,
                axisPx = axisPx,
                headerPx = headerPx,
                collapse = collapse,
            )
        }
        val maxScrollX = maxScrollOffset(grid.contentWidth, viewportWPx)
        val maxScrollY = maxScrollOffset(grid.contentHeight, viewportHPx)

        LaunchedEffect(grid, viewportHPx) {
            val target = if (nowIndex >= 0 && grid.mapper.isKept(nowCycleMin)) {
                headerPx + grid.mapper.y(nowCycleMin) - viewportHPx / 2f
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
                            val column = gridColumnAt(scrollX + offset.x, axisPx, cellW, grid.cellsByColumn.size)
                            if (column < 0) return@detectTapGestures
                            val minutes = grid.mapper.minutesAt(scrollY + offset.y - headerPx)
                            grid.cellsByColumn[column]
                                .firstOrNull { minutes >= it.startMin && minutes < it.endMin }
                                ?.let { cell -> onEventClick(cell.event, service) }
                        }
                    }
                    .pointerInput(grid, cellW, axisPx, viewportWPx, viewportHPx) {
                        detectTransformGestures { _, pan, _, _ ->
                            scrollX = (scrollX - pan.x).coerceIn(0f, maxScrollX.toFloat())
                            scrollY = (scrollY - pan.y).coerceIn(0f, maxScrollY.toFloat())
                        }
                    },
            ) {
                val drawScrollX = scrollX
                val drawScrollY = scrollY
                drawRect(color = colors.surface, topLeft = Offset(0f, headerPx), size = Size(size.width, size.height - headerPx))
                val columns = visibleColumns(drawScrollX, size.width, axisPx, cellW, days.size)

                for (column in columns) {
                    val x = gridLineX(axisPx, column, cellW, drawScrollX)
                    drawLine(
                        color = colors.outlineVariant,
                        start = Offset(x, headerPx),
                        end = Offset(x, size.height),
                        strokeWidth = 1f,
                    )
                }

                for (hour in 0 until WEEK_HOURS_PER_CYCLE) {
                    val minutes = hour * 60f
                    if (minutes >= WEEK_CYCLE_MINUTES) break
                    if (!grid.mapper.isKept(minutes)) continue
                    val y = rowLineY(headerPx, minutes, grid.mapper, drawScrollY)
                    if (y < headerPx - 1f || y > size.height) continue
                    drawLine(
                        color = colors.outlineVariant,
                        start = Offset(axisPx, y),
                        end = Offset(size.width, y),
                        strokeWidth = 1f,
                    )
                }

                for (column in columns) {
                    val x = gridLineX(axisPx, column, cellW, drawScrollX)
                    for (cell in grid.cellsByColumn[column]) {
                        val top = cellTopY(headerPx, grid.mapper, cell.startMin, drawScrollY)
                        val height = (cell.y1 - cell.y0).coerceAtLeast(GUIDE_CELL_MIN_HEIGHT_PX)
                        if (!cellIntersectsViewport(top, height, headerPx, size.height)) continue
                        clipRect(left = 0f, top = headerPx, right = size.width, bottom = size.height) {
                        val alpha = if (cell.event.dimmed) 0.45f else 1f
                        val rectSize = Size((cellW - 2f).coerceAtLeast(1f), height)
                        val rectTopLeft = Offset(x + 1f, top)
                        drawRect(
                            color = colors.surface.copy(alpha = alpha),
                            topLeft = rectTopLeft,
                            size = rectSize,
                        )
                        drawRect(
                            color = genreColor(cell.level1).copy(alpha = alpha),
                            topLeft = rectTopLeft,
                            size = Size(bandPx, height),
                        )
                        drawRect(
                            color = colors.outlineVariant.copy(alpha = alpha),
                            topLeft = rectTopLeft,
                            size = rectSize,
                            style = Stroke(width = 1f),
                        )
                        drawFittedCellText(
                            textMeasurer = textMeasurer,
                            title = cell.event.event.title,
                            maxWidth = (rectSize.width - bandPx - textPad * 2).toInt(),
                            maxHeight = height - textPad,
                            style = TextStyle(
                                fontSize = 12.sp,
                                color = colors.onSurface.copy(alpha = alpha),
                            ),
                            topLeft = Offset(x + 1f + bandPx + textPad, top + textPad / 2f),
                        ) { layout, position ->
                            drawText(layout, topLeft = position)
                        }
                        }
                    }
                }

                if (nowIndex >= 0 && grid.mapper.isKept(nowCycleMin)) {
                    val y = rowLineY(headerPx, nowCycleMin, grid.mapper, drawScrollY)
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
                    start = Offset(axisPx, 0f),
                    end = Offset(axisPx, size.height),
                    strokeWidth = 1f,
                )
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
                for (hour in 0 until WEEK_HOURS_PER_CYCLE) {
                    val minutes = hour * 60f
                    if (minutes >= WEEK_CYCLE_MINUTES) break
                    if (!grid.mapper.isKept(minutes)) continue
                    val y = rowLineY(0f, minutes, grid.mapper, drawScrollY)
                    if (y < -24f || y > size.height) continue
                    val layout = textMeasurer.measure(
                        text = AnnotatedString("%02d:00".format((startHour + hour) % 24)),
                        style = TextStyle(fontSize = 11.sp, color = colors.onSurfaceVariant),
                        constraints = Constraints(maxWidth = (axisPx - axisLabelPadPx * 2f).toInt().coerceAtLeast(1)),
                    )
                    drawText(layout, topLeft = Offset(axisLabelPadPx, y + 2f))
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
                val columns = visibleColumns(drawScrollX, size.width, axisPx, cellW, days.size)
                for (column in columns) {
                    val x = gridLineX(axisPx, column, cellW, drawScrollX)
                    if (x + cellW < 0f || x > size.width) continue
                    drawLine(
                        color = colors.outlineVariant,
                        start = Offset(x, 0f),
                        end = Offset(x, size.height),
                        strokeWidth = 1f,
                    )
                    val day = days[column]
                    val maxWidth = (cellW - headerTextInsetPx).toInt().coerceAtLeast(1)
                    val isToday = day == today
                    val dayColor = when {
                        isToday -> colors.primary
                        day.dayOfWeek.value == 7 -> Color(0xFFD32F2F)
                        day.dayOfWeek.value == 6 -> Color(0xFF1565C0)
                        else -> colors.onSurface
                    }
                    val primary = textMeasurer.measure(
                        text = AnnotatedString(dateLabel(day)),
                        style = TextStyle(
                            fontSize = 12.sp,
                            color = dayColor,
                            textAlign = TextAlign.Center,
                            fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal,
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        constraints = Constraints(maxWidth = maxWidth),
                    )
                    val secondary = if (isToday) {
                        textMeasurer.measure(
                            text = AnnotatedString("今日"),
                            style = TextStyle(fontSize = 11.sp, color = colors.primary),
                            maxLines = 1,
                            constraints = Constraints(maxWidth = maxWidth),
                        )
                    } else {
                        null
                    }
                    val totalHeight = primary.size.height + (secondary?.size?.height ?: 0)
                    var textTop = (size.height - totalHeight) / 2f
                    drawText(primary, topLeft = Offset(x + (cellW - primary.size.width) / 2f, textTop))
                    if (secondary != null) {
                        textTop += primary.size.height
                        drawText(secondary, topLeft = Offset(x + (cellW - secondary.size.width) / 2f, textTop))
                    }
                }
            }
        }
    }
}
