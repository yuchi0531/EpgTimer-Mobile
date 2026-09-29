package com.starrow.epgtimer.ui.autoadd

import com.starrow.epgtimer.data.model.ContentData
import com.starrow.epgtimer.data.model.EpgAutoAddData
import com.starrow.epgtimer.data.model.SearchCondition
import com.starrow.epgtimer.data.guide.buildAndKey
import com.starrow.epgtimer.data.guide.buildNotKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AutoAddFormBridgeTest {

    @Test
    fun `search condition becomes an auto add form`() {
        val condition = SearchCondition.empty().copy(
            andKey = "アニメ",
            contentList = listOf(ContentData(0x0B, 0xFF, 0, 0)),
            serviceList = listOf(0x0000_0001_0000_0002L),
        )
        val form = autoAddFormOfSearchCondition(condition)
        assertEquals("アニメ", form.andKey)
        assertEquals(setOf(0x0B), form.genreLevel1)
        assertEquals(setOf(0x0000_0001_0000_0002L), form.serviceKeys)
        assertTrue(form.isNew)
    }

    @Test
    fun `round trip of an existing auto add keeps every field`() {
        val original = EpgAutoAddData(
            dataId = 42,
            searchKey = SearchCondition.empty().copy(
                andKey = buildAndKey("ドラマ", disabled = false, caseSensitive = true, durationMin = 0, durationMax = 0),
                notKey = buildNotKey("メモ", "workflow"),
                regExpFlag = 0,
                titleOnlyFlag = 1,
                chkRecEnd = 1,
                chkRecDay = 14,
            ),
            recSetting = com.starrow.epgtimer.data.model.RecSettingData.empty().copy(priority = 3),
            addCount = 128,
        )
        val form = autoAddFormOf(original)
        assertEquals(42, form.dataId)
        assertEquals("ドラマ", form.andKey)
        assertTrue(form.caseSensitive)
        assertEquals("メモ", form.note)
        assertEquals("workflow", form.notKey)
        assertTrue(form.titleOnlyFlag)
        assertTrue(form.chkRecEnd)
        assertEquals("14", form.chkRecDay)
        assertEquals(3, form.recSetting.priority)

        val rebuilt = form.toAutoAddData()
        assertEquals(original.dataId, rebuilt.dataId)
        assertEquals(original.searchKey.andKey, rebuilt.searchKey.andKey)
        assertEquals(original.searchKey.notKey, rebuilt.searchKey.notKey)
        assertEquals(original.searchKey.titleOnlyFlag, rebuilt.searchKey.titleOnlyFlag)
        assertEquals(original.searchKey.chkRecEnd, rebuilt.searchKey.chkRecEnd)
        assertEquals(original.searchKey.chkRecDay, rebuilt.searchKey.chkRecDay)
        assertEquals(original.recSetting.priority, rebuilt.recSetting.priority)
    }

    @Test
    fun `no service flag survives the round trip`() {
        val item = EpgAutoAddData(
            dataId = 7,
            searchKey = SearchCondition.empty().copy(chkRecDay = 5 + REC_NO_SERVICE_OFFSET),
            recSetting = com.starrow.epgtimer.data.model.RecSettingData.empty(),
            addCount = 0,
        )
        val form = autoAddFormOf(item)
        assertTrue(form.chkRecNoService)
        assertEquals("5", form.chkRecDay)
        val rebuilt = form.toAutoAddData()
        assertEquals(REC_NO_SERVICE_OFFSET + 5, rebuilt.searchKey.chkRecDay)
    }

    @Test
    fun `new form is marked as new and uses the enabled recording default`() {
        val form = AutoAddForm()
        assertTrue(form.isNew)
        val item = form.copy(andKey = "=test").toAutoAddData()
        assertEquals(EpgAutoAddData.NEW_DATA_ID, item.dataId)
        assertEquals(0, item.addCount)
        assertFalse("既定の録画設定が無効 shouldn't be", item.recSetting.isNoRec)
    }

    @Test
    fun `day of week and time band survive the round trip`() {
        val form = AutoAddForm(
            andKey = "=test",
            dayOfWeek = setOf(0, 6),
            startHour = 20,
            startMin = 30,
            endHour = 23,
            endMin = 59,
        )
        val item = form.toAutoAddData()
        assertEquals(2, item.searchKey.dateList.size)
        val rebuilt = autoAddFormOf(item)
        assertEquals(setOf(0, 6), rebuilt.dayOfWeek)
        assertEquals(20, rebuilt.startHour)
        assertEquals(30, rebuilt.startMin)
    }

    @Test
    fun `services list stays empty when no service is selected`() {
        val item = AutoAddForm(andKey = "=test").toAutoAddData()
        assertTrue(item.searchKey.serviceList.isEmpty())
    }
}
