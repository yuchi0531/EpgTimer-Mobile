package com.starrow.epgtimer.data.repository

import com.starrow.epgtimer.data.guide.GuideEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime

class GuideWindowTest {

    private val engine = GuideEngine()

    private fun base(now: LocalDateTime): LocalDate =
        engine.eventBaseTime(now, pastAvailable = true).toLocalDate()

    @Test
    fun `current week has no upper bound`() {
        val base = base(LocalDateTime.of(2024, 1, 10, 15, 30))
        val weekStart = LocalDate.of(2024, 1, 7)
        val window = guideWindow(weekStart, base)
        assertEquals(weekStart.atStartOfDay(), window.start)
        assertNull(window.end)
    }

    @Test
    fun `sunday keeps today inside the current week window`() {
        val today = LocalDate.of(2024, 1, 7)
        val base = base(LocalDateTime.of(2024, 1, 7, 10, 0))
        assertEquals("eventBaseTime(now, pastAvailable = true)", LocalDate.of(2023, 12, 31), base)
        assertNull(guideWindow(today, base).end)
    }

    @Test
    fun `past week is clamped to seven days`() {
        val base = base(LocalDateTime.of(2024, 1, 10, 15, 30))
        val weekStart = LocalDate.of(2023, 12, 31)
        val window = guideWindow(weekStart, base)
        assertEquals(weekStart.atStartOfDay(), window.start)
        assertEquals(weekStart.plusDays(7).atStartOfDay(), window.end)
    }

    @Test
    fun `future week has no upper bound`() {
        val base = base(LocalDateTime.of(2024, 1, 10, 15, 30))
        assertNull(guideWindow(LocalDate.of(2024, 1, 14), base).end)
    }
}
