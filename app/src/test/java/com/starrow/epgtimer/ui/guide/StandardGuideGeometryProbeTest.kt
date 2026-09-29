package com.starrow.epgtimer.ui.guide

import com.starrow.epgtimer.data.edcb.EpgTimerTcpClient
import com.starrow.epgtimer.data.guide.DefaultGuides
import com.starrow.epgtimer.data.guide.GuideEngine
import com.starrow.epgtimer.data.model.ServiceInfo
import com.starrow.epgtimer.data.repository.GuideData
import com.starrow.epgtimer.data.repository.GuideEvent
import com.starrow.epgtimer.data.repository.ServerConfig
import com.starrow.epgtimer.data.repository.guideWindow
import com.starrow.epgtimer.util.EpgClock
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.time.LocalDateTime

class StandardGuideGeometryProbeTest {

    @Test
    fun cellsRenderAcrossTheWholeWeek() = runBlocking {
        val host = System.getenv("EDCB_TEST_HOST")
        assumeTrue(host != null)
        val client = EpgTimerTcpClient(requireNotNull(host), 4510, readTimeoutMs = 120000)
        val engine = GuideEngine()
        val guide = DefaultGuides.create().first { it.tabName == "地デジ" }
        val services = client.enumService()
        val expanded = engine.expandViewServices(guide.viewServiceList, services)
        val now = EpgClock.now()
        val weekStart = engine.eventBaseTime(now, true).toLocalDate()
        val window = guideWindow(weekStart, weekStart)
        val events = client.enumPgInfoEx(expanded.map { it.key }, window.start, window.end)
            .flatMap { it.eventList }
        val data = engine.buildGuideData(guide, weekStart, expanded, events)
        println("PROBE services=" + data.services.size + " events=" + data.eventsByService.values.sumOf { it.size })

        val density = 3f
        val hourHeightDp = 120
        val viewportW = 1080f
        val viewportH = 1920f
        val cellWidth = maxOf(96f * density, viewportW / data.services.size)
        val axisPx = GUIDE_AXIS_WIDTH_DP * density
        val headerPx = 52f * density
        val pxPerMinute = guidePpxPerMinute(hourHeightDp * density)
        val origin = weekStart.atStartOfDay()
        val windowEndMin = 7f * 1440f

        val mapper = TimelineMapper(0f, windowEndMin, buildTimeline(0f, windowEndMin, emptyList(), false), pxPerMinute)
        val (contentW, contentH) = guideContentSizes(axisPx, headerPx, data.services.size, windowEndMin, cellWidth, pxPerMinute)
        println("PROBE contentW=$contentW contentH=$contentH totalPx=" + mapper.totalPx)
        println("PROBE maxScrollY=" + maxScrollOffset(contentH, viewportH))

        val nowMin = minutesBetween(origin, now)
        val autoScroll = clampScrollOffset(headerPx + mapper.y(nowMin) - viewportH / 2f, contentH, viewportH).toFloat()
        println("PROBE nowMin=$nowMin autoScrollY=$autoScroll")

        val cols = visibleColumns(0f, viewportW, axisPx, cellWidth, data.services.size)
        println("PROBE visibleColumns=$cols (count=${cols.count()})")
        val gridCells = data.eventsByService[expanded[0].key].orEmpty()
        println("PROBE col0 cells=" + gridCells.size + " firstStart=" +
            gridCells.firstOrNull()?.event?.startDateTime)

        var drawn = 0
        var textDrawn = 0
        var minTop = Float.MAX_VALUE
        var maxTop = -Float.MAX_VALUE
        for (column in cols) {
            for (ge in data.eventsByService[data.services[column].key].orEmpty()) {
                val start = ge.event.startDateTime ?: continue
                val startMin = minutesBetween(origin, start)
                if (startMin < 0f || startMin >= windowEndMin) continue
                val endMin = (startMin + eventDurationMinutes(ge.event)).coerceAtMost(windowEndMin)
                val y0 = mapper.y(startMin)
                val y1 = mapper.y(endMin)
                val top = cellTopY(headerPx, mapper, startMin, autoScroll)
                val height = (y1 - y0).coerceAtLeast(GUIDE_CELL_MIN_HEIGHT_PX)
                if (!cellIntersectsViewport(top, height, headerPx, viewportH)) continue
                drawn++
                if (height >= GUIDE_MIN_TEXT_HEIGHT_PX) textDrawn++
                minTop = minOf(minTop, top)
                maxTop = maxOf(maxTop, top)
            }
        }
        println("PROBE drawn=$drawn textDrawn=$textDrawn topRange=[$minTop,$maxTop]")
        assertTrue("自動スクロール位置で番組セルが1つも描画されない", drawn > 0)
        assertTrue("番組タイトルが描画可能なセルが無い", textDrawn > 0)
    }

    @Test
    fun fixedOverlaysStayInsideTheirOwnBounds() {
        val viewportW = 1080f
        val viewportH = 1920f
        val axisPx = 56f * 3f
        val headerPx = 52f * 3f
        val axisHeight = viewportH - headerPx
        println("PROBE headerH=$headerPx axisW=$axisPx axisH=$axisHeight viewport=${viewportW}x$viewportH")
        assertTrue("ヘッダー高さが画面高を超える: $headerPx >= $viewportH", headerPx < viewportH)
        assertTrue("時刻軸幅が画面幅を超える: $axisPx >= $viewportW", axisPx < viewportW)
        assertTrue("時刻軸の高さが0以下: $axisHeight <= 0", axisHeight > 0f)
    }
}
