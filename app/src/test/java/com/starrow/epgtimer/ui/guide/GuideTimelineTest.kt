package com.starrow.epgtimer.ui.guide

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GuideTimelineTest {

    @Test
    fun `visible columns cover the scrolled viewport and clamp to bounds`() {
        assertEquals(0..4, visibleColumns(0f, 500f, 50f, 100f, 5))
        assertEquals(0..4, visibleColumns(120f, 500f, 50f, 100f, 5))
        assertEquals(2..4, visibleColumns(320f, 500f, 50f, 100f, 5))
        assertEquals(4..4, visibleColumns(9999f, 500f, 50f, 100f, 5))
        assertEquals(0..4, visibleColumns(50f, 500f, 50f, 100f, 5))
    }

    @Test
    fun `visible columns is empty when there is no column to draw`() {
        assertEquals(0..-1, visibleColumns(0f, 500f, 50f, 100f, 0))
    }

    @Test
    fun `grid column at returns minus one outside the columns`() {
        assertEquals(-1, gridColumnAt(20f, 50f, 100f, 5))
        assertEquals(0, gridColumnAt(50f, 50f, 100f, 5))
        assertEquals(3, gridColumnAt(399f, 50f, 100f, 5))
        assertEquals(-1, gridColumnAt(1000f, 50f, 100f, 5))
    }

    @Test
    fun `timeline mapper collapses gaps and maps both directions`() {
        val mapper = TimelineMapper(
            origin = 0f,
            end = 600f,
            segments = buildTimeline(0f, 600f, listOf(60f to 120f, 300f to 360f), collapse = true),
            pxPerMinute = 2f,
        )
        assertEquals(240f, mapper.totalPx, 0.01f)
        assertEquals(0f, mapper.y(60f), 0.01f)
        assertEquals(120f, mapper.y(120f), 0.01f)
        assertEquals(120f, mapper.y(300f), 0.01f)
        assertEquals(75f, mapper.minutesAt(30f), 0.01f)
        assertTrue(mapper.isKept(90f))
        assertTrue(!mapper.isKept(200f))
    }

    @Test
    fun `max scroll offset is content minus viewport and never negative`() {
        assertEquals(0, maxScrollOffset(1000f, 2000f))
        assertEquals(0, maxScrollOffset(2000f, 2000f))
        assertEquals(800, maxScrollOffset(2000f, 1200f))
        assertEquals(48960, maxScrollOffset(50400f, 1440f))
    }

    @Test
    fun `clamp scroll offset keeps autoscroll inside the scrollable range`() {
        assertEquals(0, clampScrollOffset(-500f, 20000f, 1920f))
        assertEquals(18080, clampScrollOffset(999999f, 20000f, 1920f))
        assertEquals(9040, clampScrollOffset(9040f, 20000f, 1920f))
        assertEquals(0, clampScrollOffset(5000f, 1000f, 1920f))
    }

    @Test
    fun `grid and row coordinates subtract the scroll offset exactly once`() {
        assertEquals(150f, gridLineX(50f, 2, 100f, 100f), 0.01f)
        assertEquals(-50f, gridLineX(50f, 0, 100f, 100f), 0.01f)

        val mapper = TimelineMapper(0f, 600f, listOf(TimeSegment(0f, 600f)), pxPerMinute = 2f)
        assertEquals(550f, rowLineY(50f, 300f, mapper, 100f), 0.01f)
        assertEquals(50f, rowLineY(50f, 0f, mapper, 0f), 0.01f)
        assertEquals(50f, cellTopY(50f, mapper, 0f, 0f), 0.01f)
        assertEquals(-50f, cellTopY(50f, mapper, 0f, 100f), 0.01f)
    }

    @Test
    fun `cell visibility keeps only cells touching the grid area below the header`() {
        assertTrue(cellIntersectsViewport(topY = 200f, heightPx = 40f, headerPx = 100f, viewportHeightPx = 800f))
        assertTrue(cellIntersectsViewport(topY = 50f, heightPx = 60f, headerPx = 100f, viewportHeightPx = 800f))
        assertTrue(cellIntersectsViewport(topY = 790f, heightPx = 40f, headerPx = 100f, viewportHeightPx = 800f))
        assertTrue(!cellIntersectsViewport(topY = 10f, heightPx = 40f, headerPx = 100f, viewportHeightPx = 800f))
        assertTrue(!cellIntersectsViewport(topY = 860f, heightPx = 40f, headerPx = 100f, viewportHeightPx = 800f))
    }

    @Test
    fun `a scrolled seven day timeline keeps every visible cell inside the viewport`() {
        val density = 3f
        val hourHeightDp = 300
        val pxPerMinute = hourHeightDp * density / 60f
        val mapper = TimelineMapper(0f, 10080f, listOf(TimeSegment(0f, 10080f)), pxPerMinute)
        val headerPx = 52f * density
        val viewportHeightPx = 1920f * density

        val scrollY = clampScrollOffset(headerPx + mapper.y(5000f) - viewportHeightPx / 2f, headerPx + mapper.totalPx, viewportHeightPx)
        assertTrue(scrollY > 0)

        var drawn = 0
        for (day in 0 until 7) {
            for (hour in 0 until 24) {
                val minutes = day * 1440f + hour * 60f
                val y = rowLineY(headerPx, minutes, mapper, scrollY.toFloat())
                if (y < headerPx - 1f || y > viewportHeightPx) continue
                drawn++
            }
        }
        assertTrue("可視範囲の時刻行が1本も描画されない", drawn > 0)
        assertTrue("7日分の時刻行がすべて可視になることはない", drawn < 168)
    }

    @Test
    fun `max scroll offset is zero when the content is smaller than the viewport`() {
        assertEquals(0, maxScrollOffset(1000f, 1920f))
        assertEquals(0, maxScrollOffset(0f, 1920f))
        assertEquals(0, maxScrollOffset(152100f, 152100f))
    }

    @Test
    fun `clamp scroll offset pins negative targets to zero and far targets to the limit`() {
        assertEquals(0, clampScrollOffset(-1f, 151200f, 2400f))
        assertEquals(0, clampScrollOffset(-999999f, 151200f, 2400f))
        assertEquals(148800, clampScrollOffset(999999f, 151200f, 2400f))
        assertEquals(0, clampScrollOffset(Float.NaN, 151200f, 2400f))
        assertEquals(0, clampScrollOffset(Float.POSITIVE_INFINITY, 1000f, 1920f))
    }

    @Test
    fun `clamp scroll offset truncates instead of rounding up past the limit`() {
        val limit = maxScrollOffset(2000.6f, 1200f)
        assertEquals(800, limit)
        assertEquals(800, clampScrollOffset(2000.5f, 2000.6f, 1200f))
    }

    @Test
    fun `visible columns never return a negative range`() {
        val columns = visibleColumns(0f, 1080f, 168f, 288f, 48)
        assertTrue(columns.first >= 0)
        assertTrue(columns.last >= columns.first)
        assertTrue(columns.last < 48)

        assertEquals(0..-1, visibleColumns(0f, 1080f, 168f, 0f, 48))
        assertEquals(0..-1, visibleColumns(0f, 1080f, 168f, 288f, 0))
    }

    @Test
    fun `collapsing empty hours shortens the content so no blank area is scrollable`() {
        val pxPerMinute = 2f
        val collapsed = buildTimeline(0f, 600f, listOf(60f to 120f, 300f to 360f), collapse = true)
        val mapper = TimelineMapper(0f, 600f, collapsed, pxPerMinute)
        val uncollapsed = TimelineMapper(0f, 600f, listOf(TimeSegment(0f, 600f)), pxPerMinute)

        val (collapsedWidth, collapsedHeight) = guideContentSizes(
            axisPx = 50f,
            headerPx = 40f,
            columns = 10,
            timelineHeightPx = mapper.totalPx,
            cellWidthPx = 100f,
        )
        val (_, plainHeight) = guideContentSizes(
            axisPx = 50f,
            headerPx = 40f,
            columns = 10,
            timelineHeightPx = uncollapsed.totalPx,
            cellWidthPx = 100f,
        )

        assertTrue(collapsedHeight < plainHeight)
        assertEquals(40f + mapper.totalPx, collapsedHeight, 0.01f)
        assertEquals(collapsedWidth, 50f + 10 * 100f, 0.01f)
    }

    @Test
    fun `seven days at the maximum hour height still fits the int scroll range`() {
        val density = 3f
        val pxPerMinute = guidePpxPerMinute(300 * density)
        val mapper = TimelineMapper(0f, 10080f, listOf(TimeSegment(0f, 10080f)), pxPerMinute)
        val headerPx = 52f * density
        val (contentWidth, contentHeight) = guideContentSizes(
            axisPx = 56f * density,
            headerPx = headerPx,
            columns = 48,
            rows = 10080f,
            cellWidthPx = 96f * density,
            pxPerMinute = pxPerMinute,
        )
        assertTrue("contentHeight=$contentHeight", contentHeight <= Int.MAX_VALUE.toFloat())
        assertTrue(contentHeight == headerPx + mapper.totalPx)
        assertTrue(contentWidth <= Int.MAX_VALUE.toFloat())
        assertTrue(maxScrollOffset(contentHeight, 2400f) > 0)
        assertTrue(clampScrollOffset(contentHeight, contentHeight, 2400f) <= maxScrollOffset(contentHeight, 2400f))
    }

    @Test
    fun `forty eight services at the minimum cell width stay within the int scroll range`() {
        val density = 3f
        val cellW = 96f * density
        val (contentWidth, _) = guideContentSizes(
            axisPx = 56f * density,
            headerPx = 52f * density,
            columns = 48,
            rows = 10080f,
            cellWidthPx = cellW,
            pxPerMinute = guidePpxPerMinute(300 * density),
        )
        assertEquals(13992f, contentWidth, 0.01f)
        val maxX = maxScrollOffset(contentWidth, 1080f)
        assertEquals(12912, maxX)
        assertEquals(0, clampScrollOffset(-1f, contentWidth, 1080f))
        assertEquals(12912, clampScrollOffset(1_000_000f, contentWidth, 1080f))
    }

    @Test
    fun `guide content sizes clamp degenerate inputs and cap absurd totals`() {
        val (width, height) = guideContentSizes(
            axisPx = 0f,
            headerPx = 0f,
            columns = -5,
            rows = -10f,
            cellWidthPx = -1f,
            pxPerMinute = -1f,
        )
        assertEquals(0f, width, 0.01f)
        assertEquals(0f, height, 0.01f)

        val (wide, tall) = guideContentSizes(
            axisPx = 168f,
            headerPx = 156f,
            columns = Int.MAX_VALUE,
            rows = Float.MAX_VALUE,
            cellWidthPx = 288f,
            pxPerMinute = 4000f,
        )
        assertEquals(GUIDE_MAX_CONTENT_PX, wide, 0.01f)
        assertEquals(GUIDE_MAX_CONTENT_PX, tall, 0.01f)
    }

    @Test
    fun `guide px per minute is bounded so float precision survives huge timelines`() {
        assertEquals(15f, guidePpxPerMinute(900f), 0.001f)
        assertEquals(GUIDE_MAX_PX_PER_MINUTE, guidePpxPerMinute(1_000_000f), 0.001f)
        assertTrue(guidePpxPerMinute(0f) > 0f)
    }

    @Test
    fun `merged groups take as many cell widths as their span`() {
        val widths = guideGroupWidths(100f, intArrayOf(1, 3, 2, 1))

        assertEquals(listOf(100f, 300f, 200f, 100f), widths.toList())
        assertEquals(listOf(56f, 156f, 456f, 656f), guideGroupColumnStarts(56f, widths).toList())
    }

    @Test
    fun `group widths and starts ignore non positive spans and empty lists`() {
        assertEquals(listOf(100f, 100f), guideGroupWidths(100f, intArrayOf(0, 1)).toList())
        assertEquals(0, guideGroupWidths(100f, IntArray(0)).size)
        assertEquals(0, guideGroupColumnStarts(56f, FloatArray(0)).size)
    }

    @Test
    fun `visible group columns follow the cumulative widths instead of a fixed cell width`() {
        val widths = guideGroupWidths(100f, intArrayOf(1, 3, 1))
        val starts = guideGroupColumnStarts(50f, widths)

        assertEquals(0..2, visibleGroupColumns(starts, widths, 0f, 700f))
        assertEquals(0..1, visibleGroupColumns(starts, widths, 0f, 400f))
        assertEquals(1..2, visibleGroupColumns(starts, widths, 200f, 400f))
        assertEquals(0..-1, visibleGroupColumns(starts, FloatArray(0), 0f, 400f))
    }

    @Test
    fun `group column lookup maps a content x to the group that owns it`() {
        val widths = guideGroupWidths(100f, intArrayOf(1, 3, 1))
        val starts = guideGroupColumnStarts(50f, widths)

        assertEquals(-1, groupColumnAt(starts, widths, 49f))
        assertEquals(0, groupColumnAt(starts, widths, 50f))
        assertEquals(0, groupColumnAt(starts, widths, 149f))
        assertEquals(1, groupColumnAt(starts, widths, 150f))
        assertEquals(1, groupColumnAt(starts, widths, 449f))
        assertEquals(2, groupColumnAt(starts, widths, 450f))
        assertEquals(2, groupColumnAt(starts, widths, 549f))
        assertEquals(-1, groupColumnAt(starts, widths, 550f))
    }
}
