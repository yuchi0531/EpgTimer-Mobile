package com.starrow.epgtimer.data.guide

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class RealGuideXmlTest {

    private fun sampleFile(): File? {
        val path = System.getenv("EPGTIMER_TEST_XML")
            ?: "src/test/resources/sample/EpgTimerNW.exe.xml"
        return File(path).takeIf { it.exists() }
    }

    @Test
    fun parsesEpgTimerNwSettingsXml() {
        val file = sampleFile() ?: return
        val parsed = GuideXmlParser.parse(file.readBytes().toString(Charsets.UTF_8))
        assertTrue("UseCustomEpgView が true にならない", parsed.useCustomEpgView)
        assertEquals("タブ数が6ではない", 6, parsed.guides.size)
        parsed.guides.forEach { g ->
            println(
                "TAB name=${g.tabName} viewMode=${g.viewMode} searchMode=${g.searchMode} " +
                    "filterEnded=${g.filterEnded} startTimeWeek=${g.startTimeWeek} " +
                    "services=${g.viewServiceList.size} contentKinds=${g.viewContentKindList} " +
                    "highlight=${g.highlightContentKind}",
            )
        }
        val dttv = parsed.guides.first()
        assertEquals("地デジ", dttv.tabName)
        assertEquals(0, dttv.viewMode)
        assertEquals(4, dttv.startTimeWeek)
        assertTrue("地デジのサービスIDが1件も取れない", dttv.viewServiceList.isNotEmpty())
        assertTrue(
            "地デジが特殊キー(0x1000000000000)になっていない",
            dttv.viewServiceList.all { it > 0x100000L },
        )
        assertEquals(emptyList<Int>(), dttv.viewContentKindList)
        val bs4k = parsed.guides.first { it.tabName == "BS4K" }
        assertEquals(0, bs4k.viewMode)
        assertEquals(8, bs4k.viewServiceList.size)
        val anime = parsed.guides.first { it.tabName == "アニメ" }
        assertEquals("アニメの表示モードが2(リスト)にならない", 2, anime.viewMode)
        assertTrue("アニメの FilterEnded が true にならない", anime.filterEnded)
        assertEquals(3, anime.viewContentKindList.size)
        assertTrue("アニメの HighlightContentKind が false でない", !anime.highlightContentKind)
        val newAnime = parsed.guides.first { it.tabName == "新アニメ" }
        assertEquals(2, newAnime.viewMode)
        assertTrue(newAnime.searchMode)
        assertEquals(listOf(2047), newAnime.viewContentKindList)
        assertTrue("新アニメの検索キーワードが空", newAnime.searchKey.andKey.isNotBlank())
    }

    @Test
    fun utf8BomDoesNotBreakParsing() {
        val file = sampleFile() ?: return
        val raw = file.readBytes()
        assertTrue(
            "BOM付きUTF-8ではない",
            raw.size >= 3 && raw[0] == 0xEF.toByte() && raw[1] == 0xBB.toByte() && raw[2] == 0xBF.toByte(),
        )
        val parsed = GuideXmlParser.parse(raw.toString(Charsets.UTF_8))
        assertTrue("BOM付きでパースできない", parsed.useCustomEpgView)
    }
}
