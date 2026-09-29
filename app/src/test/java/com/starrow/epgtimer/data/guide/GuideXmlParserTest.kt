package com.starrow.epgtimer.data.guide

import com.starrow.epgtimer.data.model.ContentData
import com.starrow.epgtimer.data.model.CustomProgramGuide
import com.starrow.epgtimer.data.model.SearchCondition
import com.starrow.epgtimer.data.model.SearchDateInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element
import org.xml.sax.InputSource
import java.io.StringReader
import javax.xml.parsers.DocumentBuilderFactory

class GuideXmlParserTest {

    @Test
    fun `tab element order follows the self serializer`() {
        assertEquals(
            "SettingClass.cs:1122-1158",
            listOf(
                "TabName",
                "EpgSettingIndex",
                "ViewMode",
                "NeedTimeOnlyBasic",
                "NeedTimeOnlyWeek",
                "StartTimeWeek",
                "ViewServiceList",
                "ViewContentKindList",
                "HighlightContentKind",
                "SearchMode",
                "SearchKey",
                "FilterEnded",
            ),
            childNames(tabElement(0)),
        )
    }

    @Test
    fun `search key element order follows the self serializer`() {
        assertEquals(
            "SettingClass.cs:1136-1153",
            listOf(
                "andKey",
                "notKey",
                "regExpFlag",
                "titleOnlyFlag",
                "contentList",
                "dateList",
                "serviceList",
                "videoList",
                "audioList",
                "aimaiFlag",
                "notContetFlag",
                "notDateFlag",
                "freeCAFlag",
                "chkRecEnd",
                "chkRecDay",
            ),
            childNames(child(tabElement(0), "SearchKey")),
        )
    }

    @Test
    fun `list element types follow the self serializer`() {
        assertEquals(
            "SettingClass.cs:350 + 1119",
            listOf("CustomEpgTabInfo", "CustomEpgTabInfo"),
            childNames(child(root(), "CustomEpgTabList")),
        )
        assertEquals(
            "SettingClass.cs:1128-1129",
            listOf("unsignedLong", "unsignedLong", "unsignedLong"),
            childNames(child(tabElement(0), "ViewServiceList")),
        )
        assertEquals(
            "SettingClass.cs:1130-1131",
            listOf("unsignedShort", "unsignedShort"),
            childNames(child(tabElement(0), "ViewContentKindList")),
        )
        assertEquals(
            "SettingClass.cs:1081",
            listOf("EpgContentData"),
            childNames(child(child(tabElement(0), "SearchKey"), "contentList")),
        )
        assertEquals(
            "SettingClass.cs:1094",
            listOf("EpgSearchDateInfo"),
            childNames(child(child(tabElement(0), "SearchKey"), "dateList")),
        )
        assertEquals(
            "SettingClass.cs:1142-1143",
            listOf("long"),
            childNames(child(child(tabElement(0), "SearchKey"), "serviceList")),
        )
        assertEquals(
            "SettingClass.cs:1144-1145",
            listOf("unsignedShort"),
            childNames(child(child(tabElement(0), "SearchKey"), "videoList")),
        )
        assertEquals(
            "SettingClass.cs:1084-1087",
            listOf("content_nibble_level_1", "content_nibble_level_2", "user_nibble_1", "user_nibble_2"),
            childNames(child(child(child(tabElement(0), "SearchKey"), "contentList"), "EpgContentData")),
        )
        assertEquals(
            "SettingClass.cs:1103-1108",
            listOf("startDayOfWeek", "startHour", "startMin", "endDayOfWeek", "endHour", "endMin"),
            childNames(child(child(child(tabElement(0), "SearchKey"), "dateList"), "EpgSearchDateInfo")),
        )
        assertEquals(
            "CustomEpgTabInfo.cs:27-86 + SettingClass.cs:349",
            listOf("UseCustomEpgView", "CustomEpgTabList"),
            childNames(root()),
        )
    }

    @Test
    fun `parses every field of a serialized tab`() {
        val parsed = GuideXmlParser.parse(SAMPLE)
        assertTrue(parsed.useCustomEpgView)
        assertEquals(2, parsed.guides.size)

        val first = parsed.guides[0]
        assertEquals("地デジ", first.tabName)
        assertEquals(1, first.epgSettingIndex)
        assertEquals(CustomProgramGuide.VIEW_MODE_WEEK, first.viewMode)
        assertFalse(first.needTimeOnlyBasic)
        assertTrue(first.needTimeOnlyWeek)
        assertEquals(4, first.startTimeWeek)
        assertEquals(
            listOf(0x1000000000000L, 0x1000000000001L, 132491151212801L),
            first.viewServiceList,
        )
        assertEquals(listOf(0x01FF, 0x0E07), first.viewContentKindList)
        assertTrue(first.highlightContentKind)
        assertTrue(first.searchMode)
        assertFalse(first.filterEnded)

        val key = first.searchKey
        assertEquals("ニュース", key.andKey)
        assertEquals("バラエティ", key.notKey)
        assertEquals(1, key.regExpFlag)
        assertEquals(1, key.titleOnlyFlag)
        assertEquals(1, key.contentList.size)
        assertEquals(1, key.contentList[0].nibbleLevel1)
        assertEquals(4, key.contentList[0].nibbleLevel2)
        assertEquals(3, key.contentList[0].userNibble1)
        assertEquals(7, key.contentList[0].userNibble2)
        assertEquals(1, key.dateList.size)
        assertEquals(1, key.dateList[0].startDayOfWeek)
        assertEquals(6, key.dateList[0].startHour)
        assertEquals(30, key.dateList[0].startMin)
        assertEquals(2, key.dateList[0].endDayOfWeek)
        assertEquals(7, key.dateList[0].endHour)
        assertEquals(0, key.dateList[0].endMin)
        assertEquals(listOf(132491151212801L), key.serviceList)
        assertEquals(listOf(101), key.videoList)
        assertEquals(listOf(15), key.audioList)
        assertEquals(1, key.aimaiFlag)
        assertEquals(1, key.notContetFlag)
        assertEquals(1, key.notDateFlag)
        assertEquals(2, key.freeCAFlag)
        assertEquals(1, key.chkRecEnd)
        assertEquals(6, key.chkRecDay)

        val second = parsed.guides[1]
        assertEquals("リスト", second.tabName)
        assertEquals(CustomProgramGuide.VIEW_MODE_LIST, second.viewMode)
        assertTrue(second.needTimeOnlyBasic)
        assertTrue(second.filterEnded)
        assertFalse(second.highlightContentKind)
        assertFalse(second.searchMode)
        assertEquals(emptyList<Long>(), second.viewServiceList)
        assertEquals(emptyList<Int>(), second.viewContentKindList)
        assertEquals("", second.searchKey.andKey)
        assertEquals(0, second.searchKey.chkRecDay)
        assertEquals(emptyList<ContentData>(), second.searchKey.contentList)
        assertEquals(emptyList<SearchDateInfo>(), second.searchKey.dateList)
        assertEquals(SearchCondition.empty(), second.searchKey)
    }

    @Test
    fun `bool values follow the c sharp spelling`() {
        val parsed = GuideXmlParser.parse(SAMPLE)
        assertTrue(parsed.guides[0].needTimeOnlyWeek)
        assertFalse(parsed.guides[1].needTimeOnlyWeek)
        val upper = SAMPLE.replace("<UseCustomEpgView>true</UseCustomEpgView>", "<UseCustomEpgView>True</UseCustomEpgView>")
        assertFalse(GuideXmlParser.parse(upper).useCustomEpgView)
    }

    @Test
    fun `missing tab elements fall back to the converter defaults`() {
        val xml = """
            <Settings>
              <UseCustomEpgView>false</UseCustomEpgView>
              <CustomEpgTabList>
                <CustomEpgTabInfo />
              </CustomEpgTabList>
            </Settings>
        """.trimIndent()
        val parsed = GuideXmlParser.parse(xml)
        assertFalse(parsed.useCustomEpgView)
        assertEquals(1, parsed.guides.size)
        val tab = parsed.guides[0]
        assertEquals("", tab.tabName)
        assertEquals(0, tab.epgSettingIndex)
        assertEquals(0, tab.viewMode)
        assertFalse(tab.needTimeOnlyBasic)
        assertFalse(tab.needTimeOnlyWeek)
        assertEquals("SettingClass.cs:1127 def=0 (ctor は CustomEpgTabInfo.cs:23)", 0, tab.startTimeWeek)
        assertEquals(emptyList<Long>(), tab.viewServiceList)
        assertEquals(emptyList<Int>(), tab.viewContentKindList)
        assertEquals(
            "SettingClass.cs:1132 def=false (ctor は CustomEpgTabInfo.cs:24)",
            false,
            tab.highlightContentKind,
        )
        assertFalse(tab.searchMode)
        assertFalse(tab.filterEnded)
        assertEquals("", tab.searchKey.andKey)
        assertEquals("", tab.searchKey.notKey)
        assertEquals("SettingClass.cs:1153 def=0 (field は CtrlCmdDef.cs:980)", 0, tab.searchKey.chkRecDay)
        assertEquals(0, tab.searchKey.chkRecEnd)
        assertEquals(0, tab.searchKey.freeCAFlag)
        assertEquals(emptyList<Long>(), tab.searchKey.serviceList)
        assertEquals(SearchCondition.empty(), tab.searchKey)
    }

    @Test
    fun `missing search key falls back to the field defaults`() {
        val xml = """
            <Settings>
              <CustomEpgTabList>
                <CustomEpgTabInfo>
                  <TabName>BS</TabName>
                  <StartTimeWeek>4</StartTimeWeek>
                  <HighlightContentKind>true</HighlightContentKind>
                </CustomEpgTabInfo>
              </CustomEpgTabList>
            </Settings>
        """.trimIndent()
        val tab = GuideXmlParser.parse(xml).guides.single()
        assertEquals("BS", tab.tabName)
        assertEquals(4, tab.startTimeWeek)
        assertTrue(tab.highlightContentKind)
        assertEquals(SearchCondition.empty(), tab.searchKey)
    }

    @Test
    fun `numbers are read as invariant doubles and truncated`() {
        val xml = """
            <Settings>
              <CustomEpgTabList>
                <CustomEpgTabInfo>
                  <EpgSettingIndex>2.5</EpgSettingIndex>
                  <StartTimeWeek>1e1</StartTimeWeek>
                  <ViewServiceList>
                    <unsignedLong>1.32491151212801E+14</unsignedLong>
                    <unsignedLong>broken</unsignedLong>
                  </ViewServiceList>
                </CustomEpgTabInfo>
              </CustomEpgTabList>
            </Settings>
        """.trimIndent()
        val tab = GuideXmlParser.parse(xml).guides.single()
        assertEquals(2, tab.epgSettingIndex)
        assertEquals(10, tab.startTimeWeek)
        assertEquals(listOf(132491151212801L, 0L), tab.viewServiceList)
    }

    @Test
    fun `broken xml falls back to defaults`() {
        val defaults = ParsedGuides(useCustomEpgView = false, guides = emptyList())
        assertEquals(defaults, GuideXmlParser.parse("<Settings><"))
        assertEquals(defaults, GuideXmlParser.parse(""))
        assertEquals(defaults, GuideXmlParser.parse("not xml at all"))
    }

    @Test
    fun `missing settings root falls back to defaults`() {
        assertEquals(ParsedGuides(useCustomEpgView = false, guides = emptyList()), GuideXmlParser.parse("<Other />"))
        val noList = GuideXmlParser.parse("<Settings><UseCustomEpgView>true</UseCustomEpgView></Settings>")
        assertTrue(noList.useCustomEpgView)
        assertEquals(emptyList<CustomProgramGuide>(), noList.guides)
    }

    private fun root(): Element = parseDocument(SAMPLE)

    private fun tabElement(index: Int): Element = child(child(root(), "CustomEpgTabList"), "CustomEpgTabInfo", index)

    private fun parseDocument(xml: String): Element =
        DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(InputSource(StringReader(xml))).documentElement

    private fun child(x: Element, name: String, index: Int = 0): Element {
        var seen = 0
        val nodes = x.childNodes
        for (i in 0 until nodes.length) {
            val node = nodes.item(i)
            if (node is Element && node.tagName == name) {
                if (seen == index) {
                    return node
                }
                seen++
            }
        }
        throw AssertionError("$name not found")
    }

    private fun childNames(x: Element): List<String> {
        val result = mutableListOf<String>()
        val nodes = x.childNodes
        for (i in 0 until nodes.length) {
            val node = nodes.item(i)
            if (node is Element) {
                result.add(node.tagName)
            }
        }
        return result
    }

    private companion object {
        const val SAMPLE = """<?xml version="1.0" encoding="utf-8"?>
<Settings>
  <UseCustomEpgView>true</UseCustomEpgView>
  <CustomEpgTabList>
    <CustomEpgTabInfo>
      <TabName>地デジ</TabName>
      <EpgSettingIndex>1</EpgSettingIndex>
      <ViewMode>1</ViewMode>
      <NeedTimeOnlyBasic>false</NeedTimeOnlyBasic>
      <NeedTimeOnlyWeek>true</NeedTimeOnlyWeek>
      <StartTimeWeek>4</StartTimeWeek>
      <ViewServiceList>
        <unsignedLong>281474976710656</unsignedLong>
        <unsignedLong>281474976710657</unsignedLong>
        <unsignedLong>132491151212801</unsignedLong>
      </ViewServiceList>
      <ViewContentKindList>
        <unsignedShort>511</unsignedShort>
        <unsignedShort>3591</unsignedShort>
      </ViewContentKindList>
      <HighlightContentKind>true</HighlightContentKind>
      <SearchMode>true</SearchMode>
      <SearchKey>
        <andKey>ニュース</andKey>
        <notKey>バラエティ</notKey>
        <regExpFlag>1</regExpFlag>
        <titleOnlyFlag>1</titleOnlyFlag>
        <contentList>
          <EpgContentData>
            <content_nibble_level_1>1</content_nibble_level_1>
            <content_nibble_level_2>4</content_nibble_level_2>
            <user_nibble_1>3</user_nibble_1>
            <user_nibble_2>7</user_nibble_2>
          </EpgContentData>
        </contentList>
        <dateList>
          <EpgSearchDateInfo>
            <startDayOfWeek>1</startDayOfWeek>
            <startHour>6</startHour>
            <startMin>30</startMin>
            <endDayOfWeek>2</endDayOfWeek>
            <endHour>7</endHour>
            <endMin>0</endMin>
          </EpgSearchDateInfo>
        </dateList>
        <serviceList>
          <long>132491151212801</long>
        </serviceList>
        <videoList>
          <unsignedShort>101</unsignedShort>
        </videoList>
        <audioList>
          <unsignedShort>15</unsignedShort>
        </audioList>
        <aimaiFlag>1</aimaiFlag>
        <notContetFlag>1</notContetFlag>
        <notDateFlag>1</notDateFlag>
        <freeCAFlag>2</freeCAFlag>
        <chkRecEnd>1</chkRecEnd>
        <chkRecDay>6</chkRecDay>
      </SearchKey>
      <FilterEnded>false</FilterEnded>
    </CustomEpgTabInfo>
    <CustomEpgTabInfo>
      <TabName>リスト</TabName>
      <EpgSettingIndex>0</EpgSettingIndex>
      <ViewMode>2</ViewMode>
      <NeedTimeOnlyBasic>true</NeedTimeOnlyBasic>
      <NeedTimeOnlyWeek>false</NeedTimeOnlyWeek>
      <StartTimeWeek>4</StartTimeWeek>
      <ViewServiceList />
      <ViewContentKindList />
      <HighlightContentKind>false</HighlightContentKind>
      <SearchMode>false</SearchMode>
      <SearchKey>
        <andKey></andKey>
        <notKey></notKey>
        <regExpFlag>0</regExpFlag>
        <titleOnlyFlag>0</titleOnlyFlag>
        <contentList />
        <dateList />
        <serviceList />
        <videoList />
        <audioList />
        <aimaiFlag>0</aimaiFlag>
        <notContetFlag>0</notContetFlag>
        <notDateFlag>0</notDateFlag>
        <freeCAFlag>0</freeCAFlag>
        <chkRecEnd>0</chkRecEnd>
        <chkRecDay>0</chkRecDay>
      </SearchKey>
      <FilterEnded>true</FilterEnded>
    </CustomEpgTabInfo>
  </CustomEpgTabList>
</Settings>"""
    }
}
