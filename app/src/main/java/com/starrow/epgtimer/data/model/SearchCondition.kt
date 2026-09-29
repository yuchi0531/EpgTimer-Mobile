package com.starrow.epgtimer.data.model

data class SearchDateInfo(
    val startDayOfWeek: Int,
    val startHour: Int,
    val startMin: Int,
    val endDayOfWeek: Int,
    val endHour: Int,
    val endMin: Int,
)

data class SearchCondition(
    val andKey: String,
    val notKey: String,
    val regExpFlag: Int,
    val titleOnlyFlag: Int,
    val contentList: List<ContentData>,
    val dateList: List<SearchDateInfo>,
    val serviceList: List<Long>,
    val videoList: List<Int>,
    val audioList: List<Int>,
    val aimaiFlag: Int,
    val notContetFlag: Int,
    val notDateFlag: Int,
    val freeCAFlag: Int,
    val chkRecEnd: Int,
    val chkRecDay: Int,
) {
    val isEmpty: Boolean
        get() = andKey.isBlank() && notKey.isBlank() &&
            contentList.isEmpty() && dateList.isEmpty() && serviceList.isEmpty() &&
            videoList.isEmpty() && audioList.isEmpty() &&
            freeCAFlag == 0 && titleOnlyFlag == 0 && regExpFlag == 0 && aimaiFlag == 0

    companion object {
        fun empty(): SearchCondition = SearchCondition(
            andKey = "",
            notKey = "",
            regExpFlag = 0,
            titleOnlyFlag = 0,
            contentList = emptyList(),
            dateList = emptyList(),
            serviceList = emptyList(),
            videoList = emptyList(),
            audioList = emptyList(),
            aimaiFlag = 0,
            notContetFlag = 0,
            notDateFlag = 0,
            freeCAFlag = 0,
            chkRecEnd = 0,
            chkRecDay = 0,
        )
    }
}
