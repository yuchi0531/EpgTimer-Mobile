package com.starrow.epgtimer.data.guide

import com.starrow.epgtimer.data.edcb.CtrlCmd
import com.starrow.epgtimer.data.edcb.CtrlCmdDeserializer
import com.starrow.epgtimer.data.edcb.CtrlCmdSerializer
import com.starrow.epgtimer.data.edcb.readEpgAutoAddData
import com.starrow.epgtimer.data.edcb.writeEpgAutoAddData
import com.starrow.epgtimer.data.model.ContentData
import com.starrow.epgtimer.data.model.EpgAutoAddData
import com.starrow.epgtimer.data.model.RecFileSetInfo
import com.starrow.epgtimer.data.model.RecSettingData
import com.starrow.epgtimer.data.model.SearchCondition
import com.starrow.epgtimer.data.model.SearchDateInfo
import org.junit.Assert.assertEquals
import org.junit.Test

class EpgAutoAddDataStructTest {

    private fun recSetting() = RecSettingData(
        recMode = 1,
        priority = 5,
        tuijyuuFlag = 1,
        serviceMode = 0,
        pittariFlag = 0,
        batFilePath = "",
        recFolderList = listOf(RecFileSetInfo("D:\\rec", "Write_Default.dll", "", "")),
        suspendMode = 0,
        rebootFlag = 0,
        useMargineFlag = 1,
        startMargine = -300,
        endMargine = 600,
        continueRecFlag = 0,
        partialRecFlag = 0,
        tunerID = 0,
        partialRecFolder = emptyList(),
    )

    private fun searchCondition(andKey: String) = SearchCondition(
        andKey = andKey,
        notKey = ":note:メモ 単発",
        regExpFlag = 0,
        titleOnlyFlag = 1,
        contentList = listOf(ContentData(0x07, 0xFF, 0, 0)),
        dateList = listOf(SearchDateInfo(1, 6, 0, 5, 23, 59)),
        serviceList = listOf(0x00047E8000000400L),
        videoList = emptyList(),
        audioList = emptyList(),
        aimaiFlag = 0,
        notContetFlag = 0,
        notDateFlag = 0,
        freeCAFlag = 0,
        chkRecEnd = 1,
        chkRecDay = 6,
    )

    private fun roundTrip(value: EpgAutoAddData, version: Int = CtrlCmd.CMD_VER): EpgAutoAddData {
        val writer = CtrlCmdSerializer(version)
        writer.writeEpgAutoAddData(value)
        val reader = CtrlCmdDeserializer(writer.toByteArray(), version)
        return reader.readEpgAutoAddData()
    }

    @Test
    fun epgAutoAddDataRoundTrip() {
        val sample = EpgAutoAddData(
            dataId = 42,
            searchKey = searchCondition("^!{999}C!{999}D!{100300050}アニメ"),
            recSetting = recSetting(),
            addCount = 7,
        )
        assertEquals(sample, roundTrip(sample))
    }

    @Test
    fun newDataIdIsAlwaysSerialized() {
        val sample = EpgAutoAddData(
            dataId = EpgAutoAddData.NEW_DATA_ID,
            searchKey = searchCondition(""),
            recSetting = recSetting(),
            addCount = 0,
        )
        val parsed = roundTrip(sample)
        assertEquals(0, parsed.dataId)
        assertEquals(0, parsed.addCount)
    }

    @Test
    fun versionBelow5OmitsAddCount() {
        val sample = EpgAutoAddData(
            dataId = 5,
            searchKey = searchCondition(""),
            recSetting = recSetting(),
            addCount = 99,
        )
        val parsed = roundTrip(sample, version = 4)
        assertEquals(0, parsed.addCount)
        assertEquals(sample.dataId, parsed.dataId)
    }

    @Test
    fun trailingFiveBytesRebuildDurationPrefix() {
        val parsed = readWithTrailer(
            andKey = "ニュース",
            recNoService = 1,
            durMin = 30,
            durMax = 50,
        )
        assertEquals("D!{100300050}ニュース", parsed.searchKey.andKey)
        assertEquals(40006, parsed.searchKey.chkRecDay)
    }

    @Test
    fun trailingFiveBytesKeepExistingPrefixOrder() {
        val parsed = readWithTrailer(
            andKey = "^!{999}C!{999}ニュース",
            recNoService = 0,
            durMin = 30,
            durMax = 50,
        )
        assertEquals("^!{999}C!{999}D!{100300050}ニュース", parsed.searchKey.andKey)
    }

    private fun readWithTrailer(
        andKey: String,
        recNoService: Int,
        durMin: Int,
        durMax: Int,
    ): EpgAutoAddData {
        val writer = CtrlCmdSerializer(5)
        writer.writeEpgAutoAddData(
            EpgAutoAddData(
                dataId = 3,
                searchKey = searchCondition(andKey),
                recSetting = recSetting(),
                addCount = 0,
            ),
        )
        val base = writer.toByteArray()
        val searchSizeOffset = 8
        val searchSize = readInt(base, searchSizeOffset)
        val searchEnd = searchSizeOffset + searchSize
        val patched = base.copyOf(base.size + 5)
        base.copyInto(patched, searchEnd + 5, searchEnd, base.size)
        patched[searchEnd] = recNoService.toByte()
        patched[searchEnd + 1] = (durMin and 0xFF).toByte()
        patched[searchEnd + 2] = (durMin ushr 8).toByte()
        patched[searchEnd + 3] = (durMax and 0xFF).toByte()
        patched[searchEnd + 4] = (durMax ushr 8).toByte()
        writeInt(patched, searchSizeOffset, searchSize + 5)
        writeInt(patched, 0, readInt(base, 0) + 5)
        return CtrlCmdDeserializer(patched, 5).readEpgAutoAddData()
    }

    private fun readInt(data: ByteArray, offset: Int): Int =
        (data[offset].toInt() and 0xFF) or
            ((data[offset + 1].toInt() and 0xFF) shl 8) or
            ((data[offset + 2].toInt() and 0xFF) shl 16) or
            ((data[offset + 3].toInt() and 0xFF) shl 24)

    private fun writeInt(data: ByteArray, offset: Int, value: Int) {
        data[offset] = value.toByte()
        data[offset + 1] = (value ushr 8).toByte()
        data[offset + 2] = (value ushr 16).toByte()
        data[offset + 3] = (value ushr 24).toByte()
    }

    @Test
    fun trailingFiveBytesWithNoDurationLeaveAndKeyUntouched() {
        val writer = CtrlCmdSerializer(5)
        writer.writeEpgAutoAddData(
            EpgAutoAddData(
                dataId = 3,
                searchKey = searchCondition("^!{999}ニュース"),
                recSetting = recSetting(),
                addCount = 0,
            ),
        )
        val base = writer.toByteArray()
        val withTrailer = base.copyOf(base.size + 5)

        val reader = CtrlCmdDeserializer(withTrailer, 5)
        val parsed = reader.readEpgAutoAddData()
        assertEquals("^!{999}ニュース", parsed.searchKey.andKey)
        assertEquals(6, parsed.searchKey.chkRecDay)
    }

    @Test
    fun structureWithoutTrailingBytesIsAccepted() {
        val writer = CtrlCmdSerializer(5)
        writer.writeEpgAutoAddData(
            EpgAutoAddData(
                dataId = 9,
                searchKey = searchCondition(" documentary "),
                recSetting = recSetting(),
                addCount = 0,
            ),
        )
        val reader = CtrlCmdDeserializer(writer.toByteArray(), 5)
        val parsed = reader.readEpgAutoAddData()
        assertEquals(" documentary ", parsed.searchKey.andKey)
    }

    @Test
    fun fieldOrderMatchesTheStructDefinition() {
        val writer = CtrlCmdSerializer(5)
        writer.writeEpgAutoAddData(
            EpgAutoAddData(
                dataId = 0x11223344,
                searchKey = SearchCondition.empty(),
                recSetting = RecSettingData.empty(),
                addCount = 0x55667788,
            ),
        )
        val bytes = writer.toByteArray()
        assertEquals(0x44, bytes[4].toInt() and 0xFF)
        assertEquals(0x33, bytes[5].toInt() and 0xFF)
        assertEquals(0x22, bytes[6].toInt() and 0xFF)
        assertEquals(0x11, bytes[7].toInt() and 0xFF)
        assertEquals(0x88, bytes[bytes.size - 4].toInt() and 0xFF)
        assertEquals(0x77, bytes[bytes.size - 3].toInt() and 0xFF)
        assertEquals(0x66, bytes[bytes.size - 2].toInt() and 0xFF)
        assertEquals(0x55, bytes[bytes.size - 1].toInt() and 0xFF)
    }
}
