package com.starrow.epgtimer.data.model

import java.time.LocalDateTime

data class ShortInfo(
    val eventName: String,
    val text: String,
)

data class ExtInfo(
    val text: String,
)

data class ContentData(
    val nibbleLevel1: Int,
    val nibbleLevel2: Int,
    val userNibble1: Int,
    val userNibble2: Int,
) {
    val contentKind: Int
        get() = ((nibbleLevel1 and 0xFF) shl 8) or (nibbleLevel2 and 0xFF)
}

data class ContentInfo(
    val nibbleList: List<ContentData>,
)

data class ComponentInfo(
    val streamContent: Int,
    val componentType: Int,
    val componentTag: Int,
    val text: String,
)

data class AudioComponentData(
    val streamContent: Int,
    val componentType: Int,
    val componentTag: Int,
    val streamType: Int,
    val simulcastGroupTag: Int,
    val esMultiLingualFlag: Int,
    val mainComponentFlag: Int,
    val qualityIndicator: Int,
    val samplingRate: Int,
    val text: String,
)

data class AudioInfo(
    val componentList: List<AudioComponentData>,
)

data class EventDataRef(
    val onid: Int,
    val tsid: Int,
    val sid: Int,
    val eventId: Int,
)

data class EventGroupInfo(
    val groupType: Int,
    val eventDataList: List<EventDataRef>,
)

data class EpgEvent(
    val onid: Int,
    val tsid: Int,
    val sid: Int,
    val eventId: Int,
    val startTimeFlag: Int,
    val startTime: EdcbDateTime,
    val durationFlag: Int,
    val durationSeconds: Int,
    val freeCaFlag: Int,
    val shortInfo: ShortInfo?,
    val extInfo: ExtInfo?,
    val contentInfo: ContentInfo?,
    val componentInfo: ComponentInfo?,
    val audioInfo: AudioInfo?,
    val eventGroupInfo: EventGroupInfo?,
    val eventRelayInfo: EventGroupInfo?,
) {
    val serviceKey: Long
        get() = ServiceKey.create(onid, tsid, sid)

    val eventKey: Long
        get() = ServiceKey.event(onid, tsid, sid, eventId)

    val startDateTime: LocalDateTime?
        get() = if (startTimeFlag != 0) startTime.toLocalDateTime() else null

    val endDateTime: LocalDateTime?
        get() = startDateTime?.plusSeconds(durationSeconds.toLong())

    val title: String
        get() = shortInfo?.eventName.orEmpty()
}
