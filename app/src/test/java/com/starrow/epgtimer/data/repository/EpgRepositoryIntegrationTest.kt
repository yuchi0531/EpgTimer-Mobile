package com.starrow.epgtimer.data.repository

import com.starrow.epgtimer.data.guide.DefaultGuides
import com.starrow.epgtimer.data.guide.GuideEngine
import com.starrow.epgtimer.data.model.CustomProgramGuide
import com.starrow.epgtimer.data.model.ContentData
import com.starrow.epgtimer.data.model.ReserveData
import com.starrow.epgtimer.data.model.SearchCondition
import com.starrow.epgtimer.util.EpgClock
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import java.time.LocalDate

class EpgRepositoryIntegrationTest {

    private lateinit var repository: EpgRepository

    @Before
    fun setUp() {
        val host = System.getenv("EDCB_TEST_HOST")
        assumeTrue("EDCB_TEST_HOST 未設定のため実サーバテストをスキップ", host != null)
        val port = System.getenv("EDCB_TEST_PORT")?.toIntOrNull() ?: 4510
        repository = com.starrow.epgtimer.data.repository.EpgRepositoryImpl(
            store = InMemorySettingsStore(),
        )
        repository.updateServerConfig(ServerConfig(host = requireNotNull(host), port = port))
    }

    @Test
    fun `guide tabs expand services and return events from server`() = runBlocking {
        val services = repository.getServices().getOrThrow()
        assertTrue("サービスが1件も取得できていない", services.isNotEmpty())

        val guides = DefaultGuides.create()
        assertEquals(5, guides.size)

        val guide = guides.first { it.tabName == "地デジ" }
        val weekStart = EpgClock.now().toLocalDate().let {
            val base = GuideEngine().eventBaseTime(EpgClock.now(), pastAvailable = true).toLocalDate()
            if (it >= base) it else base
        }
        val data = repository.loadGuideWeek(guide, weekStart).getOrThrow()
        assertEquals(guide.viewServiceList, data.guide.viewServiceList)
        assertTrue("地デジタブでサービスが展開されていない", data.services.isNotEmpty())
        assertTrue("イベントデータが1件も取得できていない", data.eventsByService.values.any { it.isNotEmpty() })
        val events = data.eventsByService.values.flatten()
        assertTrue("開始時刻を持つイベントが1件も無い", events.all { it.event.startDateTime != null })
    }

    @Test
    fun `list mode filters ended programs`() = runBlocking {
        val guides = DefaultGuides.create()
        val guide = guides.first { it.tabName == "地デジ" }.copy(
            viewMode = CustomProgramGuide.VIEW_MODE_LIST,
            filterEnded = true,
        )
        val base = GuideEngine().eventBaseTime(EpgClock.now(), pastAvailable = true).toLocalDate()
        val data = repository.loadGuideWeek(guide, base).getOrThrow()
        val now = EpgClock.now()
        assertTrue(
            "終了済み番組が残っている",
            data.eventsByService.values.flatten().none { it.event.endDateTime?.isBefore(now) == true },
        )
    }

    @Test
    fun `search by keyword returns events within range`() = runBlocking {
        val today = EpgClock.now().toLocalDate()
        val result = repository.searchEvents(
            SearchCondition.empty().copy(andKey = "ニュース"),
            today,
            today.plusDays(1),
        ).getOrThrow()
        assertTrue("キーワード検索が1件もヒットしていない", result.isNotEmpty())
        result.forEach { event ->
            val start = event.startDateTime
            assertTrue(start != null)
            assertTrue(start!!.toLocalDate().isBefore(today.plusDays(2)))
            val haystack = event.title + (event.shortInfo?.text.orEmpty()) + (event.extInfo?.text.orEmpty())
            assertTrue("キーワードがタイトルにも概要にも無い: ${event.title}", haystack.contains("ニュース"))
        }
    }

    @Test
    fun `search without any condition is rejected before hitting the server`() = runBlocking {
        val today = EpgClock.now().toLocalDate()
        val result = repository.searchEvents(SearchCondition.empty(), today, today.plusDays(1))
        assertTrue("条件無しの検索が拒否されていない", result.isFailure)
    }

    @Test
    fun `search without service filter still matches events`() = runBlocking {
        val today = EpgClock.now().toLocalDate()
        val result = repository.searchEvents(
            SearchCondition.empty().copy(andKey = "a"),
            today,
            today.plusDays(1),
        ).getOrThrow()
        assertTrue("放送局未指定の検索が1件もヒットしていない", result.isNotEmpty())
    }

    @Test
    fun `search narrows by genre nibble level1`() = runBlocking {
        val today = EpgClock.now().toLocalDate()
        val all = repository.searchEvents(
            SearchCondition.empty().copy(andKey = "a"),
            today,
            today.plusDays(1),
        ).getOrThrow()
        assertTrue(all.isNotEmpty())
        val anime = repository.searchEvents(
            SearchCondition.empty().copy(
                contentList = listOf(ContentData(0x07, 0xFF, 0, 0)),
            ),
            today,
            today.plusDays(1),
        ).getOrThrow()
        anime.forEach { event ->
            val level1 = event.contentInfo?.nibbleList.orEmpty().map { it.nibbleLevel1 }
            assertTrue(
                "ジャンル0x07のニブルを含まない番組が混ざっている: ${event.title} $level1",
                level1.contains(0x07),
            )
        }
    }

    @Test
    fun `getEvent resolves a single event by event key`() = runBlocking {
        val today = EpgClock.now().toLocalDate()
        val events = repository.searchEvents(
            SearchCondition.empty().copy(andKey = "a"),
            today,
            today.plusDays(1),
        ).getOrThrow()
        assumeTrue(events.isNotEmpty())
        val event = events.first()
        val fetched = repository.getEvent(event.serviceKey, event.eventId).getOrThrow()
        assertEquals(event.eventKey, fetched.eventKey)
        assertEquals(event.title, fetched.title)
    }

    @Test
    fun `getEvent with unknown key fails instead of crashing`() = runBlocking {
        val result = repository.getEvent(0x0000_0001L, 0)
        assertTrue(result.isFailure || result.getOrNull()?.eventKey == 0L)
    }

    @Test
    fun `default reserve setting is available`() = runBlocking {
        val setting = repository.getDefaultRecSetting().getOrThrow()
        assertTrue("既定の録画設定が無効", !setting.isNoRec)
    }

    @Test
    fun `recinfo list is readable`() = runBlocking {
        val recFiles = repository.getRecFiles().getOrThrow()
        assertTrue(recFiles.all { it.startTime.toLocalDateTime() != null })
    }

    @Test
    fun `reserve list is readable`() = runBlocking {
        val reserves = repository.getReserves().getOrThrow()
        assertTrue(reserves.all { it.startTime.toLocalDateTime() != null })
    }
}
