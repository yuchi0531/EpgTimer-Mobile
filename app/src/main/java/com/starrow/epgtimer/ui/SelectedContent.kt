package com.starrow.epgtimer.ui

import com.starrow.epgtimer.data.model.EpgAutoAddData
import com.starrow.epgtimer.data.model.EpgEvent
import com.starrow.epgtimer.data.model.ReserveData
import com.starrow.epgtimer.data.model.SearchCondition
import com.starrow.epgtimer.data.model.ServiceInfo

object SelectedContent {
    var event: EpgEvent? = null
    var service: ServiceInfo? = null
    var reserve: ReserveData? = null
    var autoAdd: EpgAutoAddData? = null
    var autoAddDraft: SearchCondition? = null
}
