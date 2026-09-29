package com.starrow.epgtimer.data.guide

import com.starrow.epgtimer.data.model.ContentData
import com.starrow.epgtimer.data.model.CustomProgramGuide
import com.starrow.epgtimer.data.model.SearchCondition
import com.starrow.epgtimer.data.model.SearchDateInfo
import org.w3c.dom.Element
import org.xml.sax.InputSource
import java.io.StringReader
import javax.xml.XMLConstants
import javax.xml.parsers.DocumentBuilderFactory

data class ParsedGuides(
    val useCustomEpgView: Boolean,
    val guides: List<CustomProgramGuide>,
)

object GuideXmlParser {

    fun parse(xml: String): ParsedGuides = try {
        parseDocument(xml.removePrefix("﻿"))
    } catch (ignored: Exception) {
        ParsedGuides(useCustomEpgView = false, guides = emptyList())
    }

    private fun parseDocument(xml: String): ParsedGuides {
        val factory = DocumentBuilderFactory.newInstance()
        runCatching { factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true) }
        runCatching { factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true) }
        val document = factory.newDocumentBuilder().parse(InputSource(StringReader(xml)))
        val root = document.documentElement
        if (root == null || root.tagName != "Settings") {
            return ParsedGuides(useCustomEpgView = false, guides = emptyList())
        }
        val useCustomEpgView = boolValue(root, "UseCustomEpgView", false)
        val guides = root.child("CustomEpgTabList")
            ?.children("CustomEpgTabInfo")
            ?.map { parseTab(it) }
            ?: emptyList()
        return ParsedGuides(useCustomEpgView = useCustomEpgView, guides = guides)
    }

    private fun parseTab(x: Element): CustomProgramGuide = CustomProgramGuide(
        tabName = stringValue(x, "TabName", ""),
        epgSettingIndex = intValue(x, "EpgSettingIndex", 0),
        viewMode = intValue(x, "ViewMode", 0),
        needTimeOnlyBasic = boolValue(x, "NeedTimeOnlyBasic", false),
        needTimeOnlyWeek = boolValue(x, "NeedTimeOnlyWeek", false),
        startTimeWeek = intValue(x, "StartTimeWeek", 0),
        viewServiceList = longList(x.child("ViewServiceList"), "unsignedLong"),
        viewContentKindList = intList(x.child("ViewContentKindList"), "unsignedShort"),
        highlightContentKind = boolValue(x, "HighlightContentKind", false),
        searchMode = boolValue(x, "SearchMode", false),
        searchKey = parseSearchKey(x.child("SearchKey")),
        filterEnded = boolValue(x, "FilterEnded", false),
    )

    private fun parseSearchKey(x: Element?): SearchCondition {
        if (x == null) {
            return SearchCondition.empty()
        }
        return SearchCondition(
            andKey = stringValue(x, "andKey", ""),
            notKey = stringValue(x, "notKey", ""),
            regExpFlag = intValue(x, "regExpFlag", 0),
            titleOnlyFlag = intValue(x, "titleOnlyFlag", 0),
            contentList = x.child("contentList")
                ?.children("EpgContentData")
                ?.map {
                    ContentData(
                        nibbleLevel1 = intValue(it, "content_nibble_level_1", 0),
                        nibbleLevel2 = intValue(it, "content_nibble_level_2", 0),
                        userNibble1 = intValue(it, "user_nibble_1", 0),
                        userNibble2 = intValue(it, "user_nibble_2", 0),
                    )
                }
                ?: emptyList(),
            dateList = x.child("dateList")
                ?.children("EpgSearchDateInfo")
                ?.map {
                    SearchDateInfo(
                        startDayOfWeek = intValue(it, "startDayOfWeek", 0),
                        startHour = intValue(it, "startHour", 0),
                        startMin = intValue(it, "startMin", 0),
                        endDayOfWeek = intValue(it, "endDayOfWeek", 0),
                        endHour = intValue(it, "endHour", 0),
                        endMin = intValue(it, "endMin", 0),
                    )
                }
                ?: emptyList(),
            serviceList = longList(x.child("serviceList"), "long"),
            videoList = intList(x.child("videoList"), "unsignedShort"),
            audioList = intList(x.child("audioList"), "unsignedShort"),
            aimaiFlag = intValue(x, "aimaiFlag", 0),
            notContetFlag = intValue(x, "notContetFlag", 0),
            notDateFlag = intValue(x, "notDateFlag", 0),
            freeCAFlag = intValue(x, "freeCAFlag", 0),
            chkRecEnd = intValue(x, "chkRecEnd", 0),
            chkRecDay = intValue(x, "chkRecDay", 0),
        )
    }

    private fun stringValue(x: Element?, key: String, def: String): String = x?.child(key)?.textContent ?: def

    private fun boolValue(x: Element?, key: String, def: Boolean): Boolean =
        stringValue(x, key, if (def) "true" else "false") == "true"

    private fun intValue(x: Element?, key: String, def: Int): Int =
        numberValue(x?.child(key), def.toDouble()).toInt()

    private fun numberValue(x: Element?, def: Double): Double {
        val text = x?.textContent ?: return def
        val value = text.trim().toDoubleOrNull() ?: return def
        return if (value.isFinite()) value else def
    }

    private fun longList(x: Element?, type: String): List<Long> =
        x?.children(type)?.map { numberValue(it, 0.0).toLong() } ?: emptyList()

    private fun intList(x: Element?, type: String): List<Int> =
        x?.children(type)?.map { numberValue(it, 0.0).toInt() } ?: emptyList()

    private fun Element.child(name: String): Element? {
        val nodes = childNodes
        for (i in 0 until nodes.length) {
            val node = nodes.item(i)
            if (node is Element && node.tagName == name) {
                return node
            }
        }
        return null
    }

    private fun Element.children(name: String): List<Element> {
        val result = mutableListOf<Element>()
        val nodes = childNodes
        for (i in 0 until nodes.length) {
            val node = nodes.item(i)
            if (node is Element && node.tagName == name) {
                result.add(node)
            }
        }
        return result
    }
}
