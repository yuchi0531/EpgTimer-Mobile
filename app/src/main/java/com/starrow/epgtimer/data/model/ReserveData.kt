package com.starrow.epgtimer.data.model

data class RecFileSetInfo(
    val recFolder: String,
    val writePlugIn: String,
    val recNamePlugIn: String,
    val recFileName: String,
)

data class RecSettingData(
    val recMode: Int,
    val priority: Int,
    val tuijyuuFlag: Int,
    val serviceMode: Int,
    val pittariFlag: Int,
    val batFilePath: String,
    val recFolderList: List<RecFileSetInfo>,
    val suspendMode: Int,
    val rebootFlag: Int,
    val useMargineFlag: Int,
    val startMargine: Int,
    val endMargine: Int,
    val continueRecFlag: Int,
    val partialRecFlag: Int,
    val tunerID: Int,
    val partialRecFolder: List<RecFileSetInfo>,
) {
    val isNoRec: Boolean
        get() = recMode / 5 % 2 != 0

    val effectiveRecMode: Int
        get() = (recMode + recMode / 5 % 2) % 5

    companion object {
        fun empty(): RecSettingData = RecSettingData(
            recMode = 1,
            priority = 1,
            tuijyuuFlag = 1,
            serviceMode = 0,
            pittariFlag = 0,
            batFilePath = "",
            recFolderList = emptyList(),
            suspendMode = 0,
            rebootFlag = 0,
            useMargineFlag = 0,
            startMargine = 0,
            endMargine = 0,
            continueRecFlag = 0,
            partialRecFlag = 0,
            tunerID = 0,
            partialRecFolder = emptyList(),
        )
    }
}

data class ReserveData(
    val title: String,
    val startTime: EdcbDateTime,
    val durationSecond: Int,
    val stationName: String,
    val onid: Int,
    val tsid: Int,
    val sid: Int,
    val eventId: Int,
    val comment: String,
    val reserveId: Int,
    val presentFlag: Int,
    val overlapMode: Int,
    val startTimeEpg: EdcbDateTime,
    val recSetting: RecSettingData,
    val reserveStatus: Int,
    val recFileNameList: List<String>,
) {
    companion object {
        const val DEFAULT_RESERVE_ID = 0x7FFFFFFF
    }
}
