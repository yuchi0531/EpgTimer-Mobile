package com.starrow.epgtimer.ui.guide

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GuideCellLayoutTest {

    @Test
    fun `a group whose members no longer fit is drawn as a single lane`() {
        val groups = intArrayOf(3)
        val cellWidthPx = 300f
        val minMemberWidthPx = 200f

        val spans = effectiveSpans(groups, cellWidthPx, minMemberWidthPx)

        assertEquals(1, spans[0])
    }

    @Test
    fun `a group that fits keeps its member lanes`() {
        val spans = effectiveSpans(intArrayOf(3), 900f, 200f)

        assertEquals("900/3=300 は 200 以上なので分割を維持", 3, spans[0])
    }

    @Test
    fun `group widths follow the effective spans`() {
        val widths = guideGroupWidths(300f, intArrayOf(1, 3, 1))

        assertEquals(3, widths.size)
        assertEquals(300f, widths[0], 0.01f)
        assertEquals(900f, widths[1], 0.01f)
        assertEquals(300f, widths[2], 0.01f)
    }

    @Test
    fun `group starts accumulate from the axis`() {
        val starts = guideGroupColumnStarts(120f, floatArrayOf(300f, 900f, 300f))

        assertEquals(120f, starts[0], 0.01f)
        assertEquals(420f, starts[1], 0.01f)
        assertEquals(1320f, starts[2], 0.01f)
    }

    @Test
    fun `a narrow lane still gets a usable measure width`() {
        assertTrue(GUIDE_MEASURE_MIN_WIDTH_PX > 0)
        assertEquals(GUIDE_MEASURE_MIN_WIDTH_PX, maxOf(0, GUIDE_MEASURE_MIN_WIDTH_PX))
    }

    @Test
    fun `the font ladder always offers a size`() {
        val sizes = guideFittedTextFontSizes(0f, 16f, GUIDE_CELL_FONT_SIZES_SP)

        assertEquals(1, sizes.size)
        assertEquals(GUIDE_CELL_MIN_FONT_SIZE_SP, sizes.first(), 0.01f)
    }

    @Test
    fun `a tall cell keeps the largest font`() {
        val sizes = guideFittedTextFontSizes(200f, 16f, GUIDE_CELL_FONT_SIZES_SP)

        assertEquals(GUIDE_CELL_FONT_SIZES_SP.first(), sizes.first(), 0.01f)
    }

    @Test
    fun `a short cell shrinks the font but never returns nothing`() {
        val sizes = guideFittedTextFontSizes(9f, 16f, GUIDE_CELL_FONT_SIZES_SP)

        assertTrue(sizes.isNotEmpty())
        assertTrue(sizes.all { it <= GUIDE_CELL_FONT_SIZES_SP.first() })
    }
}
