package com.starrow.epgtimer.data.edcb

import com.starrow.epgtimer.data.model.AudioComponentData
import com.starrow.epgtimer.data.model.AudioInfo
import com.starrow.epgtimer.data.model.ComponentInfo
import com.starrow.epgtimer.data.model.ContentData
import com.starrow.epgtimer.data.model.ContentInfo
import com.starrow.epgtimer.data.model.EdcbDateTime
import com.starrow.epgtimer.data.model.EpgEvent
import com.starrow.epgtimer.data.model.EventDataRef
import com.starrow.epgtimer.data.model.EventGroupInfo
import com.starrow.epgtimer.data.model.ExtInfo
import com.starrow.epgtimer.data.model.RecFileInfo
import com.starrow.epgtimer.data.model.RecFileSetInfo
import com.starrow.epgtimer.data.model.RecSettingData
import com.starrow.epgtimer.data.model.ReserveData
import com.starrow.epgtimer.data.model.SearchCondition
import com.starrow.epgtimer.data.model.SearchDateInfo
import com.starrow.epgtimer.data.model.ServiceEventInfo
import com.starrow.epgtimer.data.model.ServiceInfo
import com.starrow.epgtimer.data.model.ShortInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CtrlCmdStructTest {

    private fun <T> roundTrip(
        value: T,
        version: Int = CtrlCmd.CMD_VER,
        write: CtrlCmdSerializer.(T) -> Unit,
        read: CtrlCmdDeserializer.() -> T,
    ): T {
        val writer = CtrlCmdSerializer(version)
        writer.write(value)
        val reader = CtrlCmdDeserializer(writer.toByteArray(), version)
        return reader.read()
    }

    private fun sampleServiceInfo() = ServiceInfo(
        onid = 4,
        tsid = 32736,
        sid = 1024,
        serviceType = 1,
        partialReceptionFlag = 0,
        serviceProviderName = "ＮＨＫ",
        serviceName = "ＮＨＫ総合１",
        networkName = "東京",
        tsName = "東京",
        remoteControlKeyId = 1,
    )

    private fun sampleRecSetting() = RecSettingData(
        recMode = 2,
        priority = 3,
        tuijyuuFlag = 1,
        serviceMode = 1,
        pittariFlag = 0,
        batFilePath = "C:\\rec\\bat.bat",
        recFolderList = listOf(
            RecFileSetInfo("D:\\rec", "Write_Default.dll", "RecName_Macro.dll", "file.ts"),
            RecFileSetInfo("E:\\rec", "Write_OneService.dll", "", ""),
        ),
        suspendMode = 0,
        rebootFlag = 1,
        useMargineFlag = 1,
        startMargine = -10,
        endMargine = 5,
        continueRecFlag = 1,
        partialRecFlag = 0,
        tunerID = 0,
        partialRecFolder = listOf(RecFileSetInfo("F:\\seg", "Write_Default.dll", "", "")),
    )

    private fun sampleReserve() = ReserveData(
        title = "ニュース",
        startTime = EdcbDateTime(year = 2024, month = 6, dayOfWeek = 6, day = 6, hour = 19, minute = 0, second = 0, millisecond = 0),
        durationSecond = 1800,
        stationName = "ＮＨＫ総合１",
        onid = 4,
        tsid = 32736,
        sid = 1024,
        eventId = 4123,
        comment = "メモ",
        reserveId = 77,
        presentFlag = 1,
        overlapMode = 2,
        startTimeEpg = EdcbDateTime(year = 2024, month = 6, dayOfWeek = 6, day = 1, hour = 12, minute = 34, second = 56, millisecond = 789),
        recSetting = sampleRecSetting(),
        reserveStatus = 1,
        recFileNameList = listOf("program.ts", "program2.ts"),
    )

    private fun sampleRecFileInfo() = RecFileInfo(
        id = 9,
        recFilePath = "D:\\rec\\program.ts",
        title = "ドラマ",
        startTime = EdcbDateTime(year = 2024, month = 5, dayOfWeek = 3, day = 3, hour = 22, minute = 0, second = 0, millisecond = 0),
        durationSecond = 5400,
        serviceName = "ＮＨＫＥテレ１",
        onid = 4,
        tsid = 32736,
        sid = 1025,
        eventId = 100,
        drops = 1234567890123L,
        scrambles = 42,
        recStatus = 1,
        startTimeEpg = EdcbDateTime(year = 2024, month = 5, dayOfWeek = 3, day = 1, hour = 8, minute = 0, second = 0, millisecond = 0),
        comment = "録画終了",
        programInfo = "content data",
        errInfo = "",
        protectFlag = 1,
    )

    private fun sampleEpgEvent() = EpgEvent(
        onid = 4,
        tsid = 32736,
        sid = 1024,
        eventId = 4123,
        startTimeFlag = 1,
        startTime = EdcbDateTime(year = 2024, month = 6, dayOfWeek = 6, day = 6, hour = 19, minute = 0, second = 0, millisecond = 0),
        durationFlag = 1,
        durationSeconds = 1800,
        freeCaFlag = 0,
        shortInfo = ShortInfo("番組名", "あらすじ"),
        extInfo = ExtInfo("詳細情報"),
        contentInfo = ContentInfo(
            listOf(ContentData(0x05, 0xFF, 0x01, 0x02), ContentData(0x07, 0x01, 0, 0)),
        ),
        componentInfo = ComponentInfo(0x01, 0x71, 0x05, "映像情報"),
        audioInfo = AudioInfo(
            listOf(
                AudioComponentData(1, 3, 15, 3, 0, 0, 1, 2, 48000 / 1000, "音声情報"),
                AudioComponentData(1, 10, 0, 4, 1, 1, 0, 1, 44100 / 1000, "ステレオ"),
            ),
        ),
        eventGroupInfo = EventGroupInfo(
            1,
            listOf(EventDataRef(4, 32736, 1024, 4124), EventDataRef(4, 32736, 1025, 100)),
        ),
        eventRelayInfo = EventGroupInfo(4, listOf(EventDataRef(6, 240, 101, 300))),
    )

    private fun sampleBareEvent() = EpgEvent(
        onid = 1,
        tsid = 2,
        sid = 3,
        eventId = 4,
        startTimeFlag = 0,
        startTime = EdcbDateTime.invalid(),
        durationFlag = 0,
        durationSeconds = 0,
        freeCaFlag = 0,
        shortInfo = null,
        extInfo = null,
        contentInfo = null,
        componentInfo = null,
        audioInfo = null,
        eventGroupInfo = null,
        eventRelayInfo = null,
    )

    private fun sampleSearchCondition() = SearchCondition(
        andKey="ニュース",
        notKey = "単発",
        regExpFlag = 0,
        titleOnlyFlag = 1,
        contentList = listOf(ContentData(0x05, 0xFF, 0, 0)),
        dateList = listOf(SearchDateInfo(1, 6, 0, 1, 12, 30)),
        serviceList = listOf(0x00047E8000000400L, 0x0001000000000001L),
        videoList = listOf(0x0171, 0x0102),
        audioList = listOf(0x0003, 0x0010),
        aimaiFlag = 1,
        notContetFlag = 0,
        notDateFlag = 1,
        freeCAFlag = 2,
        chkRecEnd = 1,
        chkRecDay = 7,
    )

    private fun sampleServerStatus() = ServerStatus(
        notifyId = 100,
        time = EdcbDateTime(year = 2024, month = 6, dayOfWeek = 6, day = 6, hour = 12, minute = 30, second = 45, millisecond = 123),
        srvStatus = 0,
        param2 = 3,
        notifyCount = -6,
        param4 = "param4",
        param5 = "",
        param6 = "パラメータ6",
    )

    @Test
    fun serviceInfoRoundTrip() {
        val sample = sampleServiceInfo()
        assertEquals(sample, roundTrip(sample, write = { writeServiceInfo(it) }, read = { readServiceInfo() }))
    }

    @Test
    fun serviceEventInfoRoundTrip() {
        val sample = ServiceEventInfo(sampleServiceInfo(), listOf(sampleEpgEvent(), sampleBareEvent()))
        assertEquals(sample, roundTrip(sample, write = { writeServiceEventInfo(it) }, read = { readServiceEventInfo() }))
    }

    @Test
    fun epgEventRoundTrip() {
        val full = sampleEpgEvent()
        assertEquals(full, roundTrip(full, write = { writeEpgEvent(it) }, read = { readEpgEvent() }))
        val bare = sampleBareEvent()
        assertEquals(bare, roundTrip(bare, write = { writeEpgEvent(it) }, read = { readEpgEvent() }))
    }

    @Test
    fun epgEventOptionalStructsWriteFourByteSizeWhenAbsent() {
        val writer = CtrlCmdSerializer()
        writer.writeEpgEvent(sampleBareEvent())
        val bytes = writer.toByteArray()
        assertEquals(63, bytes.size)
        for (offset in 34 until 62 step 4) {
            assertEquals(4, bytes[offset].toInt())
            assertEquals(0, bytes[offset + 1].toInt())
            assertEquals(0, bytes[offset + 2].toInt())
            assertEquals(0, bytes[offset + 3].toInt())
        }
        assertEquals(0, bytes[62].toInt())
    }

    @Test
    fun reserveDataRoundTrip() {
        val sample = sampleReserve()
        assertEquals(sample, roundTrip(sample, write = { writeReserveData(it) }, read = { readReserveData() }))
    }

    @Test
    fun reserveDataVersionBelow5OmitsRecFileNameList() {
        val sample = sampleReserve()
        val parsed = roundTrip(sample, version = 4, write = { writeReserveData(it) }, read = { readReserveData() })
        assertEquals(sample.copy(recFileNameList = emptyList()), parsed)
    }

    @Test
    fun recSettingDataVersionBelow2OmitsPartialRecFolder() {
        val sample = sampleRecSetting()
        val parsed = roundTrip(sample, version = 1, write = { writeRecSettingData(it) }, read = { readRecSettingData() })
        assertEquals(sample.copy(partialRecFolder = emptyList()), parsed)
    }

    @Test
    fun recFileInfoRoundTrip() {
        val sample = sampleRecFileInfo()
        assertEquals(sample, roundTrip(sample, write = { writeRecFileInfo(it) }, read = { readRecFileInfo() }))
    }

    @Test
    fun recFileInfoVersionBelow4OmitsProtectFlag() {
        val sample = sampleRecFileInfo()
        val parsed = roundTrip(sample, version = 3, write = { writeRecFileInfo(it) }, read = { readRecFileInfo() })
        assertEquals(sample.copy(protectFlag = 0), parsed)
    }

    @Test
    fun searchConditionRoundTrip() {
        val sample = sampleSearchCondition()
        assertEquals(sample, roundTrip(sample, write = { writeSearchCondition(it) }, read = { readSearchCondition() }))
    }

    @Test
    fun searchConditionVersionBelow3OmitsChkRecFields() {
        val sample = sampleSearchCondition()
        val parsed = roundTrip(sample, version = 2, write = { writeSearchCondition(it) }, read = { readSearchCondition() })
        assertEquals(
            sample.copy(chkRecEnd = 0, chkRecDay = 6),
            parsed,
        )
    }

    @Test
    fun searchConditionWithoutVer5FieldsIsAccepted() {
        val sample = sampleSearchCondition()
        val writer = CtrlCmdSerializer(5)
        writer.struct {
            writer.string(sample.andKey)
            writer.string(sample.notKey)
            writer.i32(sample.regExpFlag)
            writer.i32(sample.titleOnlyFlag)
            writer.vector(sample.contentList) { writer.writeContentData(it) }
            writer.vector(sample.dateList) { writer.writeSearchDateInfo(it) }
            writer.vector(sample.serviceList) { writer.i64(it) }
            writer.vector(sample.videoList) { writer.u16(it) }
            writer.vector(sample.audioList) { writer.u16(it) }
            writer.u8(sample.aimaiFlag)
            writer.u8(sample.notContetFlag)
            writer.u8(sample.notDateFlag)
            writer.u8(sample.freeCAFlag)
            writer.u8(sample.chkRecEnd)
            writer.u16(sample.chkRecDay)
        }
        val reader = CtrlCmdDeserializer(writer.toByteArray(), 5)
        assertEquals(sample, reader.readSearchCondition())
    }

    @Test
    fun serverStatusRoundTrip() {
        val sample = sampleServerStatus()
        assertEquals(sample, roundTrip(sample, write = { writeServerStatus(it) }, read = { readServerStatus() }))
    }

    @Test
    fun observedStatusResponseParses() {
        val time = byteArrayOf(
            0xE8.toByte(), 0x07, 0x01, 0x00, 0x02, 0x00, 0x02, 0x00,
            0x03, 0x00, 0x04, 0x00, 0x05, 0x00, 0xA6.toByte(), 0x02,
        )
        val emptyString = byteArrayOf(6, 0, 0, 0, 0, 0)
        val body = byteArrayOf(0x02, 0x00) +
            byteArrayOf(0x36, 0, 0, 0) +
            byteArrayOf(0x64, 0, 0, 0) +
            time +
            byteArrayOf(0, 0, 0, 0) +
            byteArrayOf(1, 0, 0, 0) +
            byteArrayOf(0x2A, 0, 0, 0) +
            emptyString + emptyString + emptyString
        assertEquals(56, body.size)

        val reader = CtrlCmdDeserializer(body)
        assertEquals(2, reader.readVersion())
        val status = reader.readServerStatus()
        assertEquals(100, status.notifyId)
        assertEquals(EdcbDateTime(year = 2024, month = 1, dayOfWeek = 2, day = 2, hour = 3, minute = 4, second = 5, millisecond = 678), status.time)
        assertEquals(0, status.srvStatus)
        assertEquals(1, status.param2)
        assertEquals(0x2A, status.notifyCount)
        assertEquals("", status.param4)
        assertEquals("", status.param5)
        assertEquals("", status.param6)
        assertEquals(56, reader.position)
        assertTrue(status.srvStatusText == "通常")
    }
}
