package com.starrow.epgtimer.data.edcb

import com.starrow.epgtimer.data.model.AudioComponentData
import com.starrow.epgtimer.data.model.AudioInfo
import com.starrow.epgtimer.data.model.ComponentInfo
import com.starrow.epgtimer.data.model.ContentData
import com.starrow.epgtimer.data.model.ContentInfo
import com.starrow.epgtimer.data.model.EdcbDateTime
import com.starrow.epgtimer.data.model.EpgAutoAddData
import com.starrow.epgtimer.data.model.EpgEvent
import com.starrow.epgtimer.data.model.EventDataRef
import com.starrow.epgtimer.data.model.EventGroupInfo
import com.starrow.epgtimer.data.model.ExtInfo
import com.starrow.epgtimer.data.model.FileData
import com.starrow.epgtimer.data.model.RecFileInfo
import com.starrow.epgtimer.data.model.RecFileSetInfo
import com.starrow.epgtimer.data.model.RecSettingData
import com.starrow.epgtimer.data.model.ReserveData
import com.starrow.epgtimer.data.model.SearchCondition
import com.starrow.epgtimer.data.model.SearchDateInfo
import com.starrow.epgtimer.data.model.ServiceEventInfo
import com.starrow.epgtimer.data.model.ServiceInfo
import com.starrow.epgtimer.data.model.ShortInfo
import java.time.LocalDateTime

class CtrlCmdSerializer(var version: Int = 0) {
    private var buffer = ByteArray(256)
    private var length = 0

    val position: Int
        get() = length

    fun toByteArray(): ByteArray = buffer.copyOf(length)

    private fun reserve(size: Int) {
        if (length + size > buffer.size) {
            var capacity = buffer.size
            while (capacity < length + size) {
                capacity *= 2
            }
            buffer = buffer.copyOf(capacity)
        }
    }

    private fun patchU32(at: Int, value: Int) {
        buffer[at] = value.toByte()
        buffer[at + 1] = (value ushr 8).toByte()
        buffer[at + 2] = (value ushr 16).toByte()
        buffer[at + 3] = (value ushr 24).toByte()
    }

    fun u8(value: Int) {
        reserve(1)
        buffer[length++] = value.toByte()
    }

    fun u16(value: Int) {
        reserve(2)
        buffer[length++] = value.toByte()
        buffer[length++] = (value ushr 8).toByte()
    }

    fun u32(value: Int) {
        reserve(4)
        buffer[length++] = value.toByte()
        buffer[length++] = (value ushr 8).toByte()
        buffer[length++] = (value ushr 16).toByte()
        buffer[length++] = (value ushr 24).toByte()
    }

    fun u32(value: Long) = u32(value.toInt())

    fun i32(value: Int) = u32(value)

    fun i64(value: Long) {
        u32((value and 0xFFFFFFFFL).toInt())
        u32((value ushr 32).toInt())
    }

    fun bytes(value: ByteArray) {
        reserve(value.size)
        value.copyInto(buffer, length)
        length += value.size
    }

    fun f32(value: Float) = u32(value.toBits())

    fun string(value: String) {
        val bytes = value.toByteArray(Charsets.UTF_16LE)
        u32(bytes.size + 6)
        reserve(bytes.size)
        bytes.copyInto(buffer, length)
        length += bytes.size
        u16(0)
    }

    fun systemTime(value: EdcbDateTime) {
        u16(value.year)
        u16(value.month)
        u16(value.dayOfWeek)
        u16(value.day)
        u16(value.hour)
        u16(value.minute)
        u16(value.second)
        u16(value.millisecond)
    }

    fun <T> vector(items: List<T>, writeItem: (T) -> Unit) {
        val start = position
        u32(0)
        u32(items.size)
        for (item in items) {
            writeItem(item)
        }
        patchU32(start, position - start)
    }

    fun struct(body: () -> Unit) {
        val start = position
        u32(0)
        body()
        patchU32(start, position - start)
    }

    fun <T> optionalStruct(value: T?, writeItem: (T) -> Unit) {
        if (value == null) {
            u32(4)
        } else {
            writeItem(value)
        }
    }
}

fun CtrlCmdSerializer.writeVectorU32(items: List<Int>) = vector(items) { u32(it) }

fun CtrlCmdSerializer.writeVectorI64(items: List<Long>) = vector(items) { i64(it) }

fun CtrlCmdSerializer.writeVectorString(items: List<String>) = vector(items) { string(it) }

fun CtrlCmdSerializer.writeRecFileSetInfo(value: RecFileSetInfo) = struct {
    string(value.recFolder)
    string(value.writePlugIn)
    string(value.recNamePlugIn)
    string(value.recFileName)
}

fun CtrlCmdSerializer.writeRecSettingData(value: RecSettingData) = struct {
    u8(value.recMode)
    u8(value.priority)
    u8(value.tuijyuuFlag)
    u32(value.serviceMode)
    u8(value.pittariFlag)
    string(value.batFilePath)
    vector(value.recFolderList) { writeRecFileSetInfo(it) }
    u8(value.suspendMode)
    u8(value.rebootFlag)
    u8(value.useMargineFlag)
    i32(value.startMargine)
    i32(value.endMargine)
    u8(value.continueRecFlag)
    u8(value.partialRecFlag)
    u32(value.tunerID)
    if (version >= 2) {
        vector(value.partialRecFolder) { writeRecFileSetInfo(it) }
    }
}

fun CtrlCmdSerializer.writeReserveData(value: ReserveData) = struct {
    string(value.title)
    systemTime(value.startTime)
    u32(value.durationSecond)
    string(value.stationName)
    u16(value.onid)
    u16(value.tsid)
    u16(value.sid)
    u16(value.eventId)
    string(value.comment)
    u32(value.reserveId)
    u8(value.presentFlag)
    u8(value.overlapMode)
    string("")
    systemTime(value.startTimeEpg)
    writeRecSettingData(value.recSetting)
    u32(value.reserveStatus)
    if (version >= 5) {
        writeVectorString(value.recFileNameList)
        u32(0)
    }
}

fun CtrlCmdSerializer.writeRecFileInfo(value: RecFileInfo) = struct {
    u32(value.id)
    string(value.recFilePath)
    string(value.title)
    systemTime(value.startTime)
    u32(value.durationSecond)
    string(value.serviceName)
    u16(value.onid)
    u16(value.tsid)
    u16(value.sid)
    u16(value.eventId)
    i64(value.drops)
    i64(value.scrambles)
    u32(value.recStatus)
    systemTime(value.startTimeEpg)
    string(value.comment)
    string(value.programInfo)
    string(value.errInfo)
    if (version >= 4) {
        u8(value.protectFlag)
    }
}

fun CtrlCmdSerializer.writeServiceInfo(value: ServiceInfo) = struct {
    u16(value.onid)
    u16(value.tsid)
    u16(value.sid)
    u8(value.serviceType)
    u8(value.partialReceptionFlag)
    string(value.serviceProviderName)
    string(value.serviceName)
    string(value.networkName)
    string(value.tsName)
    u8(value.remoteControlKeyId)
}

fun CtrlCmdSerializer.writeServiceEventInfo(value: ServiceEventInfo) = struct {
    writeServiceInfo(value.serviceInfo)
    vector(value.eventList) { writeEpgEvent(it) }
}

fun CtrlCmdSerializer.writeShortInfo(value: ShortInfo) = struct {
    string(value.eventName)
    string(value.text)
}

fun CtrlCmdSerializer.writeExtInfo(value: ExtInfo) = struct {
    string(value.text)
}

fun CtrlCmdSerializer.writeContentData(value: ContentData) = struct {
    u8(value.nibbleLevel1)
    u8(value.nibbleLevel2)
    u8(value.userNibble1)
    u8(value.userNibble2)
}

fun CtrlCmdSerializer.writeContentInfo(value: ContentInfo) = struct {
    vector(value.nibbleList) { writeContentData(it) }
}

fun CtrlCmdSerializer.writeComponentInfo(value: ComponentInfo) = struct {
    u8(value.streamContent)
    u8(value.componentType)
    u8(value.componentTag)
    string(value.text)
}

fun CtrlCmdSerializer.writeAudioComponentData(value: AudioComponentData) = struct {
    u8(value.streamContent)
    u8(value.componentType)
    u8(value.componentTag)
    u8(value.streamType)
    u8(value.simulcastGroupTag)
    u8(value.esMultiLingualFlag)
    u8(value.mainComponentFlag)
    u8(value.qualityIndicator)
    u8(value.samplingRate)
    string(value.text)
}

fun CtrlCmdSerializer.writeAudioInfo(value: AudioInfo) = struct {
    vector(value.componentList) { writeAudioComponentData(it) }
}

fun CtrlCmdSerializer.writeEventDataRef(value: EventDataRef) = struct {
    u16(value.onid)
    u16(value.tsid)
    u16(value.sid)
    u16(value.eventId)
}

fun CtrlCmdSerializer.writeEventGroupInfo(value: EventGroupInfo) = struct {
    u8(value.groupType)
    vector(value.eventDataList) { writeEventDataRef(it) }
}

fun CtrlCmdSerializer.writeEpgEvent(value: EpgEvent) = struct {
    u16(value.onid)
    u16(value.tsid)
    u16(value.sid)
    u16(value.eventId)
    u8(value.startTimeFlag)
    systemTime(value.startTime)
    u8(value.durationFlag)
    u32(value.durationSeconds)
    optionalStruct(value.shortInfo) { writeShortInfo(it) }
    optionalStruct(value.extInfo) { writeExtInfo(it) }
    optionalStruct(value.contentInfo) { writeContentInfo(it) }
    optionalStruct(value.componentInfo) { writeComponentInfo(it) }
    optionalStruct(value.audioInfo) { writeAudioInfo(it) }
    optionalStruct(value.eventGroupInfo) { writeEventGroupInfo(it) }
    optionalStruct(value.eventRelayInfo) { writeEventGroupInfo(it) }
    u8(value.freeCaFlag)
}

fun CtrlCmdSerializer.writeSearchDateInfo(value: SearchDateInfo) = struct {
    u8(value.startDayOfWeek)
    u16(value.startHour)
    u16(value.startMin)
    u8(value.endDayOfWeek)
    u16(value.endHour)
    u16(value.endMin)
}

fun CtrlCmdSerializer.writeSearchCondition(value: SearchCondition) = struct {
    string(value.andKey)
    string(value.notKey)
    i32(value.regExpFlag)
    i32(value.titleOnlyFlag)
    vector(value.contentList) { writeContentData(it) }
    vector(value.dateList) { writeSearchDateInfo(it) }
    vector(value.serviceList) { i64(it) }
    vector(value.videoList) { u16(it) }
    vector(value.audioList) { u16(it) }
    u8(value.aimaiFlag)
    u8(value.notContetFlag)
    u8(value.notDateFlag)
    u8(value.freeCAFlag)
    if (version >= 3) {
        u8(value.chkRecEnd)
        u16(value.chkRecDay)
    }
}

fun CtrlCmdSerializer.writeEpgAutoAddData(value: EpgAutoAddData) = struct {
    u32(value.dataId)
    writeSearchCondition(value.searchKey)
    writeRecSettingData(value.recSetting)
    if (version >= 5) {
        u32(value.addCount)
    }
}

fun CtrlCmdSerializer.writeSearchPgParam(
    keys: List<SearchCondition>,
    start: LocalDateTime?,
    end: LocalDateTime?,
) = struct {
    vector(keys) { writeSearchCondition(it) }
    i64(start?.let { FileTime.toFileTime(it) } ?: 0L)
    i64(end?.let { FileTime.toFileTime(it) } ?: Long.MAX_VALUE)
}

fun CtrlCmdSerializer.writeServerStatus(value: ServerStatus) = struct {
    u32(value.notifyId)
    systemTime(value.time)
    u32(value.srvStatus)
    u32(value.param2)
    u32(value.notifyCount)
    string(value.param4)
    string(value.param5)
    string(value.param6)
}

fun CtrlCmdSerializer.writeFileData(value: FileData) = struct {
    string(value.name)
    u32(value.data.size)
    u32(value.status)
    bytes(value.data)
}
