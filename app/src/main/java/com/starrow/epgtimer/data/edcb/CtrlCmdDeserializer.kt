package com.starrow.epgtimer.data.edcb

import com.starrow.epgtimer.data.guide.buildAndKey
import com.starrow.epgtimer.data.guide.parseAndKey
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

class CtrlCmdDeserializer(private val data: ByteArray, var version: Int = 0) {
    private val limits = ArrayList<Int>()
    var position = 0
        private set

    val limit: Int
        get() = if (limits.isEmpty()) data.size else limits[limits.size - 1]

    fun remaining(): Int = limit - position

    fun hasRemaining(): Boolean = position < limit

    private fun need(size: Int) {
        if (size < 0 || position + size > limit || position + size > data.size) {
            throw EdcbException(ErrCode.CMD_ERR_DISCONNECT, "応答データが不足しています: $size バイト")
        }
    }

    private fun malformed(message: String): EdcbException =
        EdcbException(ErrCode.CMD_ERR, message)

    fun u8(): Int {
        need(1)
        return data[position++].toInt() and 0xFF
    }

    fun u16(): Int {
        need(2)
        val value = (data[position].toInt() and 0xFF) or
            ((data[position + 1].toInt() and 0xFF) shl 8)
        position += 2
        return value
    }

    fun u32(): Int {
        need(4)
        val value = (data[position].toInt() and 0xFF) or
            ((data[position + 1].toInt() and 0xFF) shl 8) or
            ((data[position + 2].toInt() and 0xFF) shl 16) or
            ((data[position + 3].toInt() and 0xFF) shl 24)
        position += 4
        return value
    }

    fun u32Long(): Long = u32().toLong() and 0xFFFFFFFFL

    fun i32(): Int = u32()

    fun i64(): Long {
        val low = u32().toLong() and 0xFFFFFFFFL
        val high = u32().toLong()
        return (high shl 32) or low
    }

    fun f32(): Float = Float.fromBits(u32())

    fun bytes(size: Int): ByteArray {
        if (size < 0 || size > limit - position || size > data.size - position) {
            throw EdcbException(ErrCode.CMD_ERR_DISCONNECT, "バイナリデータが応答データの終端を超えています: $size")
        }
        val value = data.copyOfRange(position, position + size)
        position += size
        return value
    }

    fun string(): String {
        val size = u32()
        if (size < 4) {
            throw malformed("文字列のサイズフィールドが異常です: $size")
        }
        val dataSize = size - 4
        if (dataSize > limit - position) {
            throw EdcbException(ErrCode.CMD_ERR_DISCONNECT, "文字列が応答データの終端を超えています")
        }
        if (dataSize % 2 != 0) {
            throw malformed("文字列のバイト数が奇数です: $dataSize")
        }
        if (dataSize >= 2) {
            val tail = position + dataSize - 2
            if (data[tail].toInt() != 0 || data[tail + 1].toInt() != 0) {
                throw malformed("文字列の終端が壊れています")
            }
        }
        val value = if (dataSize > 2) String(data, position, dataSize - 2, Charsets.UTF_16LE) else ""
        position += dataSize
        return value
    }

    fun systemTime(): EdcbDateTime {
        val year = u16()
        val month = u16()
        val dayOfWeek = u16()
        val day = u16()
        val hour = u16()
        val minute = u16()
        val second = u16()
        val millisecond = u16()
        return EdcbDateTime(year, month, dayOfWeek, day, hour, minute, second, millisecond)
    }

    fun <T> struct(body: () -> T): T {
        val size = u32()
        if (size < 4) {
            throw malformed("構造体のサイズフィールドが異常です: $size")
        }
        if (size - 4 > limit - position) {
            throw EdcbException(ErrCode.CMD_ERR_DISCONNECT, "構造体が応答データの終端を超えています: $size")
        }
        val end = position + size - 4
        limits.add(end)
        val value = try {
            body()
        } finally {
            limits.removeAt(limits.size - 1)
        }
        if (position > end) {
            throw malformed("構造体のサイズフィールドを超えて読み込みました")
        }
        position = end
        return value
    }

    fun <T> optionalStruct(body: () -> T): T? {
        val size = u32()
        if (size == 4) {
            return null
        }
        position -= 4
        return body()
    }

    fun <T> vector(readItem: () -> T): List<T> {
        val size = u32()
        if (size < 8) {
            throw malformed("ベクタのサイズフィールドが異常です: $size")
        }
        if (size - 8 > limit - position - 4) {
            throw EdcbException(ErrCode.CMD_ERR_DISCONNECT, "ベクタが応答データの終端を超えています: $size")
        }
        val count = u32()
        val end = position + (size - 8)
        if (count < 0 || count > end - position) {
            throw malformed("ベクタの個数が異常です: $count")
        }
        limits.add(end)
        val items = try {
            val list = ArrayList<T>(minOf(count, 1024))
            repeat(count) {
                list.add(readItem())
            }
            list
        } finally {
            limits.removeAt(limits.size - 1)
        }
        if (position > end) {
            throw malformed("ベクタのサイズフィールドを超えて読み込みました")
        }
        position = end
        return items
    }

    fun readVersion(): Int {
        version = maxOf(u16(), 2)
        return version
    }
}

fun CtrlCmdDeserializer.readVectorU32(): List<Int> = vector { u32() }

fun CtrlCmdDeserializer.readVectorI64(): List<Long> = vector { i64() }

fun CtrlCmdDeserializer.readVectorString(): List<String> = vector { string() }

fun CtrlCmdDeserializer.readRecFileSetInfo(): RecFileSetInfo = struct {
    val recFolder = string()
    val writePlugIn = string()
    val recNamePlugIn = string()
    val recFileName = string()
    RecFileSetInfo(recFolder, writePlugIn, recNamePlugIn, recFileName)
}

fun CtrlCmdDeserializer.readRecSettingData(): RecSettingData = struct {
    val recMode = u8()
    val priority = u8()
    val tuijyuuFlag = u8()
    val serviceMode = u32()
    val pittariFlag = u8()
    val batFilePath = string()
    val recFolderList = vector { readRecFileSetInfo() }
    val suspendMode = u8()
    val rebootFlag = u8()
    val useMargineFlag = u8()
    val startMargine = i32()
    val endMargine = i32()
    val continueRecFlag = u8()
    val partialRecFlag = u8()
    val tunerID = u32()
    val partialRecFolder = if (version >= 2) vector { readRecFileSetInfo() } else emptyList()
    RecSettingData(
        recMode = recMode,
        priority = priority,
        tuijyuuFlag = tuijyuuFlag,
        serviceMode = serviceMode,
        pittariFlag = pittariFlag,
        batFilePath = batFilePath,
        recFolderList = recFolderList,
        suspendMode = suspendMode,
        rebootFlag = rebootFlag,
        useMargineFlag = useMargineFlag,
        startMargine = startMargine,
        endMargine = endMargine,
        continueRecFlag = continueRecFlag,
        partialRecFlag = partialRecFlag,
        tunerID = tunerID,
        partialRecFolder = partialRecFolder,
    )
}

fun CtrlCmdDeserializer.readReserveData(): ReserveData = struct {
    val title = string()
    val startTime = systemTime()
    val durationSecond = u32()
    val stationName = string()
    val onid = u16()
    val tsid = u16()
    val sid = u16()
    val eventId = u16()
    val comment = string()
    val reserveId = u32()
    val presentFlag = u8()
    val overlapMode = u8()
    string()
    val startTimeEpg = systemTime()
    val recSetting = readRecSettingData()
    val reserveStatus = u32()
    val recFileNameList = if (version >= 5) vector { string() } else emptyList()
    if (version >= 5) {
        u32()
    }
    ReserveData(
        title = title,
        startTime = startTime,
        durationSecond = durationSecond,
        stationName = stationName,
        onid = onid,
        tsid = tsid,
        sid = sid,
        eventId = eventId,
        comment = comment,
        reserveId = reserveId,
        presentFlag = presentFlag,
        overlapMode = overlapMode,
        startTimeEpg = startTimeEpg,
        recSetting = recSetting,
        reserveStatus = reserveStatus,
        recFileNameList = recFileNameList,
    )
}

fun CtrlCmdDeserializer.readRecFileInfo(): RecFileInfo = struct {
    val id = u32()
    val recFilePath = string()
    val title = string()
    val startTime = systemTime()
    val durationSecond = u32()
    val serviceName = string()
    val onid = u16()
    val tsid = u16()
    val sid = u16()
    val eventId = u16()
    val drops = i64()
    val scrambles = i64()
    val recStatus = u32()
    val startTimeEpg = systemTime()
    val comment = string()
    val programInfo = string()
    val errInfo = string()
    val protectFlag = if (version >= 4) u8() else 0
    RecFileInfo(
        id = id,
        recFilePath = recFilePath,
        title = title,
        startTime = startTime,
        durationSecond = durationSecond,
        serviceName = serviceName,
        onid = onid,
        tsid = tsid,
        sid = sid,
        eventId = eventId,
        drops = drops,
        scrambles = scrambles,
        recStatus = recStatus,
        startTimeEpg = startTimeEpg,
        comment = comment,
        programInfo = programInfo,
        errInfo = errInfo,
        protectFlag = protectFlag,
    )
}

fun CtrlCmdDeserializer.readServiceInfo(): ServiceInfo = struct {
    val onid = u16()
    val tsid = u16()
    val sid = u16()
    val serviceType = u8()
    val partialReceptionFlag = u8()
    val serviceProviderName = string()
    val serviceName = string()
    val networkName = string()
    val tsName = string()
    val remoteControlKeyId = u8()
    ServiceInfo(
        onid = onid,
        tsid = tsid,
        sid = sid,
        serviceType = serviceType,
        partialReceptionFlag = partialReceptionFlag,
        serviceProviderName = serviceProviderName,
        serviceName = serviceName,
        networkName = networkName,
        tsName = tsName,
        remoteControlKeyId = remoteControlKeyId,
    )
}

fun CtrlCmdDeserializer.readServiceEventInfo(): ServiceEventInfo = struct {
    val serviceInfo = readServiceInfo()
    val eventList = vector { readEpgEvent() }
    ServiceEventInfo(serviceInfo, eventList)
}

fun CtrlCmdDeserializer.readShortInfo(): ShortInfo = struct {
    val eventName = string()
    val text = string()
    ShortInfo(eventName, text)
}

fun CtrlCmdDeserializer.readExtInfo(): ExtInfo = struct {
    ExtInfo(string())
}

fun CtrlCmdDeserializer.readContentData(): ContentData = struct {
    val nibbleLevel1 = u8()
    val nibbleLevel2 = u8()
    val userNibble1 = u8()
    val userNibble2 = u8()
    ContentData(nibbleLevel1, nibbleLevel2, userNibble1, userNibble2)
}

fun CtrlCmdDeserializer.readContentInfo(): ContentInfo = struct {
    ContentInfo(vector { readContentData() })
}

fun CtrlCmdDeserializer.readComponentInfo(): ComponentInfo = struct {
    val streamContent = u8()
    val componentType = u8()
    val componentTag = u8()
    val text = string()
    ComponentInfo(streamContent, componentType, componentTag, text)
}

fun CtrlCmdDeserializer.readAudioComponentData(): AudioComponentData = struct {
    val streamContent = u8()
    val componentType = u8()
    val componentTag = u8()
    val streamType = u8()
    val simulcastGroupTag = u8()
    val esMultiLingualFlag = u8()
    val mainComponentFlag = u8()
    val qualityIndicator = u8()
    val samplingRate = u8()
    val text = string()
    AudioComponentData(
        streamContent = streamContent,
        componentType = componentType,
        componentTag = componentTag,
        streamType = streamType,
        simulcastGroupTag = simulcastGroupTag,
        esMultiLingualFlag = esMultiLingualFlag,
        mainComponentFlag = mainComponentFlag,
        qualityIndicator = qualityIndicator,
        samplingRate = samplingRate,
        text = text,
    )
}

fun CtrlCmdDeserializer.readAudioInfo(): AudioInfo = struct {
    AudioInfo(vector { readAudioComponentData() })
}

fun CtrlCmdDeserializer.readEventDataRef(): EventDataRef = struct {
    val onid = u16()
    val tsid = u16()
    val sid = u16()
    val eventId = u16()
    EventDataRef(onid, tsid, sid, eventId)
}

fun CtrlCmdDeserializer.readEventGroupInfo(): EventGroupInfo = struct {
    val groupType = u8()
    val eventDataList = vector { readEventDataRef() }
    EventGroupInfo(groupType, eventDataList)
}

fun CtrlCmdDeserializer.readEpgEvent(): EpgEvent = struct {
    val onid = u16()
    val tsid = u16()
    val sid = u16()
    val eventId = u16()
    val startTimeFlag = u8()
    val startTime = systemTime()
    val durationFlag = u8()
    val durationSeconds = u32()
    val shortInfo = optionalStruct { readShortInfo() }
    val extInfo = optionalStruct { readExtInfo() }
    val contentInfo = optionalStruct { readContentInfo() }
    val componentInfo = optionalStruct { readComponentInfo() }
    val audioInfo = optionalStruct { readAudioInfo() }
    val eventGroupInfo = optionalStruct { readEventGroupInfo() }
    val eventRelayInfo = optionalStruct { readEventGroupInfo() }
    val freeCaFlag = u8()
    EpgEvent(
        onid = onid,
        tsid = tsid,
        sid = sid,
        eventId = eventId,
        startTimeFlag = startTimeFlag,
        startTime = startTime,
        durationFlag = durationFlag,
        durationSeconds = durationSeconds,
        freeCaFlag = freeCaFlag,
        shortInfo = shortInfo,
        extInfo = extInfo,
        contentInfo = contentInfo,
        componentInfo = componentInfo,
        audioInfo = audioInfo,
        eventGroupInfo = eventGroupInfo,
        eventRelayInfo = eventRelayInfo,
    )
}

fun CtrlCmdDeserializer.readSearchDateInfo(): SearchDateInfo = struct {
    val startDayOfWeek = u8()
    val startHour = u16()
    val startMin = u16()
    val endDayOfWeek = u8()
    val endHour = u16()
    val endMin = u16()
    SearchDateInfo(startDayOfWeek, startHour, startMin, endDayOfWeek, endHour, endMin)
}

fun CtrlCmdDeserializer.readSearchCondition(): SearchCondition = struct {
    var andKey = string()
    val notKey = string()
    val regExpFlag = i32()
    val titleOnlyFlag = i32()
    val contentList = vector { readContentData() }
    val dateList = vector { readSearchDateInfo() }
    val serviceList = vector { i64() }
    val videoList = vector { u16() }
    val audioList = vector { u16() }
    val aimaiFlag = u8()
    val notContetFlag = u8()
    val notDateFlag = u8()
    val freeCAFlag = u8()
    var chkRecEnd = 0
    var chkRecDay = 6
    if (version >= 3) {
        chkRecEnd = u8()
        chkRecDay = u16()
    }
    if (version >= 5 && remaining() >= 5) {
        val recNoService = u8()
        val durMin = u16()
        val durMax = u16()
        if (recNoService != 0) {
            chkRecDay = chkRecDay % 10000 + 40000
        }
        if (durMin > 0 || durMax > 0) {
            val parsed = parseAndKey(andKey)
            andKey = buildAndKey(
                plain = parsed.plain,
                disabled = parsed.disabled,
                caseSensitive = parsed.caseSensitive,
                durationMin = durMin,
                durationMax = durMax,
            )
        }
    }
    SearchCondition(
        andKey = andKey,
        notKey = notKey,
        regExpFlag = regExpFlag,
        titleOnlyFlag = titleOnlyFlag,
        contentList = contentList,
        dateList = dateList,
        serviceList = serviceList,
        videoList = videoList,
        audioList = audioList,
        aimaiFlag = aimaiFlag,
        notContetFlag = notContetFlag,
        notDateFlag = notDateFlag,
        freeCAFlag = freeCAFlag,
        chkRecEnd = chkRecEnd,
        chkRecDay = chkRecDay,
    )
}

fun CtrlCmdDeserializer.readEpgAutoAddData(): EpgAutoAddData = struct {
    val dataId = u32()
    val searchKey = readSearchCondition()
    val recSetting = readRecSettingData()
    val addCount = if (version >= 5) u32() else 0
    EpgAutoAddData(dataId, searchKey, recSetting, addCount)
}

fun CtrlCmdDeserializer.readServerStatus(): ServerStatus = struct {
    val notifyId = u32()
    val time = systemTime()
    val srvStatus = u32()
    val param2 = u32()
    val notifyCount = u32()
    val param4 = string()
    val param5 = string()
    val param6 = string()
    ServerStatus(
        notifyId = notifyId,
        time = time,
        srvStatus = srvStatus,
        param2 = param2,
        notifyCount = notifyCount,
        param4 = param4,
        param5 = param5,
        param6 = param6,
    )
}

fun CtrlCmdDeserializer.readFileData(): FileData = struct {
    val name = string()
    val size = u32()
    val status = u32()
    val data = bytes(size)
    FileData(name, status, data)
}
