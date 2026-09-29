package com.starrow.epgtimer.data.model

data class CustomProgramGuide(
    val tabName: String,
    val epgSettingIndex: Int,
    val viewMode: Int,
    val needTimeOnlyBasic: Boolean,
    val needTimeOnlyWeek: Boolean,
    val startTimeWeek: Int,
    val viewServiceList: List<Long>,
    val viewContentKindList: List<Int>,
    val highlightContentKind: Boolean,
    val searchMode: Boolean,
    val searchKey: SearchCondition,
    val filterEnded: Boolean,
) {
    companion object {
        const val VIEW_MODE_STANDARD = 0
        const val VIEW_MODE_WEEK = 1
        const val VIEW_MODE_LIST = 2

        const val VIEW_SERVICE_DTTV = 0x1000000000000L
        const val VIEW_SERVICE_BS = 0x1000000000001L
        const val VIEW_SERVICE_CS = 0x1000000000002L
        const val VIEW_SERVICE_CS3 = 0x1000000000003L
        const val VIEW_SERVICE_OTHER = 0x1000000000004L

        fun default(
            tabName: String,
            viewServiceList: List<Long>,
        ): CustomProgramGuide = CustomProgramGuide(
            tabName = tabName,
            epgSettingIndex = 0,
            viewMode = VIEW_MODE_STANDARD,
            needTimeOnlyBasic = false,
            needTimeOnlyWeek = false,
            startTimeWeek = 4,
            viewServiceList = viewServiceList,
            viewContentKindList = emptyList(),
            highlightContentKind = true,
            searchMode = false,
            searchKey = SearchCondition.empty(),
            filterEnded = false,
        )
    }
}
