package com.starrow.epgtimer.data.repository

import com.starrow.epgtimer.data.model.CustomProgramGuide
import com.starrow.epgtimer.data.model.EdcbDateTime
import com.starrow.epgtimer.data.model.ReserveData
import com.starrow.epgtimer.data.model.SearchCondition
import com.starrow.epgtimer.util.EpgClock
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.time.LocalDateTime

class ReserveRoundTripTest {
    @Test
    fun defaultSettingIsAcceptedByTheServer(): Unit = runBlocking {
        val host = System.getenv("EDCB_TEST_HOST")
        assumeTrue(host != null)
        val repository = EpgRepositoryImpl(InMemorySettingsStore())
        repository.updateServerConfig(ServerConfig(host = requireNotNull(host), port = 4510))

        val default = repository.getDefaultRecSetting().getOrThrow()
        println("PROBE recMode=${default.recMode} isNoRec=${default.isNoRec}")
        println("PROBE priority=${default.priority} tuijyuu=${default.tuijyuuFlag}")
        println("PROBE bat='${default.batFilePath}' folders=${default.recFolderList.size}")
        println("PROBE folder0='${default.recFolderList.firstOrNull()?.recFolder}'")
        println("PROBE margins=${default.startMargine}/${default.endMargine} useMargin=${default.useMargineFlag}")
        assertTrue("既定設定が無効のまま", !default.isNoRec)

        val services = repository.getServices().getOrThrow()
        val today = EpgClock.now().toLocalDate()
        val events = repository.searchEvents(
            SearchCondition.empty().copy(andKey = "ニュース", serviceList = services.map { it.key }),
            today,
            today.plusDays(1),
        ).getOrThrow()
        assumeTrue(events.isNotEmpty())
        val event = events.first()
        val service = services.firstOrNull {
            it.onid == event.onid && it.tsid == event.tsid && it.sid == event.sid
        }
        val start = requireNotNull(event.startDateTime)
        val startTime = EdcbDateTime.from(start)
        val reserve = ReserveData(
            title = event.title,
            startTime = startTime,
            durationSecond = if (event.durationFlag == 0) 600 else event.durationSeconds,
            stationName = service?.serviceName.orEmpty(),
            onid = event.onid,
            tsid = event.tsid,
            sid = event.sid,
            eventId = event.eventId,
            comment = "",
            reserveId = 0,
            presentFlag = 0,
            overlapMode = 0,
            startTimeEpg = startTime,
            recSetting = default,
            reserveStatus = 0,
            recFileNameList = emptyList(),
        )

        val before = repository.getReserves().getOrThrow().map { it.reserveId }.toSet()
        val added = repository.addReserve(reserve)
        println("PROBE addReserve=$added")
        assertTrue("予約の登録に失敗した", added.isSuccess)
        val created = repository.getReserves().getOrThrow()
            .firstOrNull { it.reserveId !in before }
        assertTrue("予約が登録されていない", created != null)
        println("PROBE created recMode=${created?.recSetting?.recMode} bat='${created?.recSetting?.batFilePath}'")
        println("PROBE created folders=${created?.recSetting?.recFolderList?.size}")
        created?.let { repository.deleteReserve(listOf(it.reserveId)) }
        println("PROBE remaining=${repository.getReserves().getOrThrow().size}")
    }
}
