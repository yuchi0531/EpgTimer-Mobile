package com.starrow.epgtimer.ui

import com.starrow.epgtimer.data.model.EdcbDateTime
import com.starrow.epgtimer.data.model.RecFileInfo
import com.starrow.epgtimer.data.model.ReserveData
import com.starrow.epgtimer.util.EpgClock
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime

class ListOrderingTest {

    private val today: LocalDate = EpgClock.now().toLocalDate()

    @Test
    fun `recorded items are grouped newest date first and newest time first inside a day`() {
        val files = listOf(
            recFile(today.minusDays(2), 10, 0, id = 1),
            recFile(today, 8, 0, id = 2),
            recFile(today, 20, 0, id = 3),
            recFile(today.minusDays(1), 12, 0, id = 4),
        )

        val grouped = files
            .map { it.startTime.toLocalDateTime()!! to it }
            .sortedByDescending { it.first }
            .groupBy { it.first.toLocalDate() }
            .toSortedMap(compareByDescending { it })

        assertEquals(listOf(today, today.minusDays(1), today.minusDays(2)), grouped.keys.toList())
        assertEquals(listOf(20, 8), grouped.getValue(today).map { it.second.startTime.toLocalDateTime()!!.hour })
    }

    @Test
    fun `reservations are grouped nearest date first and earliest time first inside a day`() {
        val reserves = listOf(
            reserve(today.plusDays(3), 10, 0, id = 1),
            reserve(today, 20, 0, id = 2),
            reserve(today, 6, 0, id = 3),
            reserve(today.minusDays(1), 9, 0, id = 4),
        )

        val grouped = reserves
            .map { it.startTime.toLocalDateTime()!! to it }
            .sortedByDescending { it.first }
            .groupBy { it.first.toLocalDate() }
            .toSortedMap(compareByDescending { it })

        assertEquals(listOf(today.plusDays(3), today, today.minusDays(1)), grouped.keys.toList())
        assertEquals(listOf(20, 6), grouped.getValue(today).map { it.second.startTime.toLocalDateTime()!!.hour })
    }

    @Test
    fun `day headers label today and tomorrow`() {
        assertEquals("今日", dayHeaderLabel(today))
        assertEquals("明日", dayHeaderLabel(today.plusDays(1)))
        assertTrue(dayHeaderLabel(today.plusDays(2)).contains("/"))
    }

    private fun recFile(date: LocalDate, hour: Int, minute: Int, id: Int): RecFileInfo = RecFileInfo(
        id = id,
        recFilePath = "/tmp/$id.ts",
        title = "title-$id",
        startTime = EdcbDateTime.from(LocalDateTime.of(date, java.time.LocalTime.of(hour, minute))),
        durationSecond = 1800,
        serviceName = "svc",
        onid = 1,
        tsid = 1,
        sid = 1,
        eventId = id,
        drops = 0,
        scrambles = 0,
        recStatus = 1,
        startTimeEpg = EdcbDateTime.from(LocalDateTime.of(date, java.time.LocalTime.of(hour, minute))),
        comment = "",
        programInfo = "",
        errInfo = "",
        protectFlag = 0,
    )

    private fun reserve(date: LocalDate, hour: Int, minute: Int, id: Int): ReserveData = ReserveData(
        title = "title-$id",
        startTime = EdcbDateTime.from(LocalDateTime.of(date, java.time.LocalTime.of(hour, minute))),
        durationSecond = 1800,
        stationName = "svc",
        onid = 1,
        tsid = 1,
        sid = 1,
        eventId = id,
        comment = "",
        reserveId = id,
        presentFlag = 0,
        overlapMode = 0,
        startTimeEpg = EdcbDateTime.from(LocalDateTime.of(date, java.time.LocalTime.of(hour, minute))),
        recSetting = com.starrow.epgtimer.data.model.RecSettingData.empty(),
        reserveStatus = 0,
        recFileNameList = emptyList(),
    )
}
