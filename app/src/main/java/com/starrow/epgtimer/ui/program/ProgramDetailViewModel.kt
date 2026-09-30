package com.starrow.epgtimer.ui.program

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.starrow.epgtimer.data.model.EpgEvent
import com.starrow.epgtimer.data.model.EdcbDateTime
import com.starrow.epgtimer.data.model.RecSettingData
import com.starrow.epgtimer.data.model.ReserveData
import com.starrow.epgtimer.data.model.ServiceInfo
import com.starrow.epgtimer.data.repository.EpgRepository
import com.starrow.epgtimer.ui.SelectedContent
import com.starrow.epgtimer.ui.errorText
import kotlinx.coroutines.launch

class ProgramDetailViewModel(private val repository: EpgRepository) : ViewModel() {

    var event by mutableStateOf<EpgEvent?>(null)
        private set

    var service by mutableStateOf<ServiceInfo?>(null)
        private set

    var eventLoading by mutableStateOf(false)
        private set

    var eventError by mutableStateOf<String?>(null)
        private set

    var defaultRecSetting by mutableStateOf<RecSettingData?>(null)
        private set

    var defaultLoaded by mutableStateOf(false)
        private set

    var defaultError by mutableStateOf<String?>(null)
        private set

    var submitting by mutableStateOf(false)
        private set

    init {
        loadDefault()
    }

    fun load(eventKey: Long) {
        val holder = SelectedContent.event
        if (holder != null && holder.eventKey == eventKey) {
            event = holder
            service = SelectedContent.service
            eventError = null
            eventLoading = false
            return
        }
        if (eventKey == MISSING_EVENT_KEY) {
            event = null
            service = null
            eventError = "表示する番組が指定されていません"
            eventLoading = false
            return
        }
        event = null
        service = null
        eventError = null
        eventLoading = true
        viewModelScope.launch {
            repository.getEvent(eventKey ushr 16, (eventKey and 0xFFFF).toInt())
                .onSuccess { fetched ->
                    event = fetched
                    eventError = null
                    repository.getServices()
                        .onSuccess { list -> service = list.firstOrNull { it.key == fetched.serviceKey } }
                        .onFailure { service = null }
                }
                .onFailure {
                    eventError = errorText(it)
                }
            eventLoading = false
        }
    }

    fun loadDefault() {
        viewModelScope.launch {
            defaultLoaded = false
            repository.getDefaultRecSetting()
                .onSuccess {
                    defaultRecSetting = it
                    defaultError = null
                }
                .onFailure {
                    defaultError = errorText(it)
                }
            defaultLoaded = true
        }
    }

    fun reserve(
        event: EpgEvent,
        service: ServiceInfo?,
        recSetting: RecSettingData,
        onResult: (Result<Unit>) -> Unit,
    ) {
        if (submitting) return
        val start = event.startDateTime
        if (start == null) {
            onResult(Result.failure(IllegalArgumentException("開始時間未定のため予約できません")))
            return
        }
        submitting = true
        viewModelScope.launch {
            val startTime = EdcbDateTime.from(start)
            val reserve = ReserveData(
                title = event.title,
                startTime = startTime,
                durationSecond = if (event.durationFlag == 0) 600 else event.durationSeconds,
                stationName = service?.serviceName.orEmpty(),
                onid = event.onid,
                tsid = event.tsid,
                sid = event.sid,
                eventId = event.eventId,
                comment = "",
                reserveId = 0,
                presentFlag = 0,
                overlapMode = 0,
                startTimeEpg = startTime,
                recSetting = recSetting,
                reserveStatus = 0,
                recFileNameList = emptyList(),
            )
            val result = repository.addReserve(reserve)
            submitting = false
            onResult(result)
        }
    }

    companion object {
        const val MISSING_EVENT_KEY = -1L
    }
}
