package com.starrow.epgtimer.data.model

data class EpgAutoAddData(
    val dataId: Int,
    val searchKey: SearchCondition,
    val recSetting: RecSettingData,
    val addCount: Int,
) {
    companion object {
        const val NEW_DATA_ID = 0
    }
}
