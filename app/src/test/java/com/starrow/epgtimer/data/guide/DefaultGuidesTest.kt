package com.starrow.epgtimer.data.guide

import com.starrow.epgtimer.data.model.CustomProgramGuide
import com.starrow.epgtimer.data.model.SearchCondition
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class DefaultGuidesTest {

    @Test
    fun `five default tabs reproduce the built in epg view`() {
        val guides = DefaultGuides.create()

        assertEquals(5, guides.size)
        assertEquals(
            "EpgDataView.xaml.cs:154-175",
            listOf("地デジ", "BS", "CS", "CS3", "その他"),
            guides.map { it.tabName },
        )
        assertEquals(
            listOf(
                CustomProgramGuide.VIEW_SERVICE_DTTV,
                CustomProgramGuide.VIEW_SERVICE_BS,
                CustomProgramGuide.VIEW_SERVICE_CS,
                CustomProgramGuide.VIEW_SERVICE_CS3,
                CustomProgramGuide.VIEW_SERVICE_OTHER,
            ),
            guides.map { it.viewServiceList.single() },
        )
    }

    @Test
    fun `default tabs use the constructor defaults`() {
        val guides = DefaultGuides.create()

        for (guide in guides) {
            assertEquals(0, guide.epgSettingIndex)
            assertEquals(CustomProgramGuide.VIEW_MODE_STANDARD, guide.viewMode)
            assertFalse(guide.needTimeOnlyBasic)
            assertFalse(guide.needTimeOnlyWeek)
            assertEquals("CustomEpgTabInfo.cs:23", 4, guide.startTimeWeek)
            assertEquals(emptyList<Int>(), guide.viewContentKindList)
            assertEquals("CustomEpgTabInfo.cs:24", true, guide.highlightContentKind)
            assertFalse(guide.searchMode)
            assertEquals(SearchCondition.empty(), guide.searchKey)
            assertFalse(guide.filterEnded)
        }
    }
}
