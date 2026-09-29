package com.starrow.epgtimer.data.guide

import com.starrow.epgtimer.data.model.CustomProgramGuide

object DefaultGuides {

    fun create(): List<CustomProgramGuide> = listOf(
        CustomProgramGuide.default(
            tabName = "地デジ",
            viewServiceList = listOf(CustomProgramGuide.VIEW_SERVICE_DTTV),
        ),
        CustomProgramGuide.default(
            tabName = "BS",
            viewServiceList = listOf(CustomProgramGuide.VIEW_SERVICE_BS),
        ),
        CustomProgramGuide.default(
            tabName = "CS",
            viewServiceList = listOf(CustomProgramGuide.VIEW_SERVICE_CS),
        ),
        CustomProgramGuide.default(
            tabName = "CS3",
            viewServiceList = listOf(CustomProgramGuide.VIEW_SERVICE_CS3),
        ),
        CustomProgramGuide.default(
            tabName = "その他",
            viewServiceList = listOf(CustomProgramGuide.VIEW_SERVICE_OTHER),
        ),
    )
}
