package com.starrow.epgtimer.data.repository

import com.starrow.epgtimer.data.edcb.EpgTimerTcpClient
import com.starrow.epgtimer.data.model.CustomProgramGuide
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

class ImportedGuideEndToEndTest {
    @Test
    fun importedGuidesRenderWithTheirOwnViewMode() = runBlocking {
        val host = System.getenv("EDCB_TEST_HOST")
        assumeTrue(host != null)
        val xmlPath = System.getenv("EPGTIMER_TEST_XML")
            ?: "src/test/resources/sample/EpgTimerNW.exe.xml"
        val xmlFile = File(xmlPath)
        assumeTrue("実ファイルが無い: $xmlPath", xmlFile.exists())

        val repository = EpgRepositoryImpl(InMemorySettingsStore())
        val importResult = repository.importGuides(xmlFile.inputStream()).getOrThrow()
        assertEquals(6, importResult.guidesCount)
        assertTrue(importResult.useCustomEpgView)
        assertEquals(GuidesSource.IMPORTED, repository.guidesSource.value)

        val guides = repository.guides.value
        assertEquals(6, guides.size)
        val byName = guides.associateBy { it.tabName }
        assertEquals(
            CustomProgramGuide.VIEW_MODE_STANDARD,
            byName.getValue("地デジ").viewMode,
        )
        assertEquals(
            CustomProgramGuide.VIEW_MODE_LIST,
            byName.getValue("アニメ").viewMode,
        )
        assertEquals(
            CustomProgramGuide.VIEW_MODE_LIST,
            byName.getValue("新アニメ").viewMode,
        )
        assertTrue(byName.getValue("新アニメ").searchMode)
        assertTrue(byName.getValue("アニメ").filterEnded)
        assertTrue(byName.getValue("新アニメ").filterEnded)

        repository.updateServerConfig(ServerConfig(host = requireNotNull(host), port = 4510))
        val services = repository.getServices().getOrThrow()
        val anime = byName.getValue("アニメ")
        val expanded = com.starrow.epgtimer.data.guide.GuideEngine()
            .expandViewServices(anime.viewServiceList, services)
        assertTrue("アニメタブのサービスが展開されない", expanded.isNotEmpty())

        val weekStart = com.starrow.epgtimer.data.guide.GuideEngine()
            .eventBaseTime(com.starrow.epgtimer.util.EpgClock.now(), true)
            .toLocalDate()
        val data = repository.loadGuideWeek(anime, weekStart).getOrThrow()
        assertEquals(CustomProgramGuide.VIEW_MODE_LIST, data.guide.viewMode)
        assertTrue("アニメタブの番組が0件", data.eventsByService.values.any { it.isNotEmpty() })
        println("PROBE anime services=" + data.services.size + " events=" + data.eventsByService.values.sumOf { it.size })
    }
}
