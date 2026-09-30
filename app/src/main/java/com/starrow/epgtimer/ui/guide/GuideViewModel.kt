package com.starrow.epgtimer.ui.guide

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.starrow.epgtimer.data.guide.GuideEngine
import com.starrow.epgtimer.data.model.CustomProgramGuide
import com.starrow.epgtimer.data.repository.EpgRepository
import com.starrow.epgtimer.data.repository.GuideData
import com.starrow.epgtimer.ui.errorText
import com.starrow.epgtimer.util.EpgClock
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class GuideViewModel(val repository: EpgRepository) : ViewModel() {

    private val engine = GuideEngine()

    val guides = repository.guides

    var tabIndex by mutableIntStateOf(0)
        private set

    var modeOverride by mutableStateOf<Int?>(null)

    var weekStart by mutableStateOf(
        engine.eventBaseTime(EpgClock.now(), true).toLocalDate(),
    )
        private set

    var guideData by mutableStateOf<GuideData?>(null)
        private set

    var loading by mutableStateOf(false)
        private set

    var error by mutableStateOf<String?>(null)
        private set

    private var loadJob: Job? = null

    fun selectTab(index: Int) {
        tabIndex = index
        modeOverride = null
    }

    fun setDisplayMode(mode: Int) {
        modeOverride = mode
    }

    fun shiftWeek(days: Long) {
        weekStart = weekStart.plusDays(days)
    }

    fun goToday() {
        weekStart = engine.eventBaseTime(EpgClock.now(), true).toLocalDate()
    }

    fun load(guide: CustomProgramGuide, mode: Int) {
        loadJob?.cancel()
        loading = true
        error = null
        loadJob = viewModelScope.launch {
            repository.loadGuideWeek(guide.copy(viewMode = mode), weekStart)
                .onSuccess {
                    guideData = it
                    loading = false
                }
                .onFailure {
                    error = errorText(it)
                    loading = false
                }
        }
    }
}
