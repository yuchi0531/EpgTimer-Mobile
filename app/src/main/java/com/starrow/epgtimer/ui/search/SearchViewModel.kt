package com.starrow.epgtimer.ui.search

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.starrow.epgtimer.data.model.EpgEvent
import com.starrow.epgtimer.data.model.SearchCondition
import com.starrow.epgtimer.data.model.ServiceInfo
import com.starrow.epgtimer.data.repository.EpgRepository
import com.starrow.epgtimer.ui.contentDataForLevel1
import com.starrow.epgtimer.ui.errorText
import com.starrow.epgtimer.util.EpgClock
import kotlinx.coroutines.launch

class SearchViewModel(private val repository: EpgRepository) : ViewModel() {

    var services by mutableStateOf<List<ServiceInfo>?>(null)
        private set

    var servicesError by mutableStateOf<String?>(null)
        private set

    var results by mutableStateOf<List<EpgEvent>?>(null)
        private set

    var searching by mutableStateOf(false)
        private set

    var searchError by mutableStateOf<String?>(null)
        private set

    init {
        loadServices()
    }

    fun loadServices() {
        viewModelScope.launch {
            repository.getServices()
                .onSuccess {
                    services = it
                    servicesError = null
                }
                .onFailure {
                    servicesError = errorText(it)
                }
        }
    }

    fun buildCondition(
        keyword: String,
        genreLevel1: Int?,
        service: ServiceInfo?,
    ): SearchCondition = SearchCondition.empty().copy(
        andKey = keyword.trim(),
        contentList = if (genreLevel1 != null) {
            listOf(contentDataForLevel1(genreLevel1))
        } else {
            emptyList()
        },
        serviceList = if (service != null) listOf(service.key) else emptyList(),
    )

    fun search(keyword: String, genreLevel1: Int?, service: ServiceInfo?, periodIndex: Int) {
        if (searching) return
        val condition = buildCondition(keyword, genreLevel1, service)
        val today = EpgClock.now().toLocalDate()
        val (start, end) = when (periodIndex) {
            1 -> Pair(today, today)
            2 -> Pair(today, today.plusDays(6))
            3 -> Pair(today, today.plusDays(13))
            4 -> Pair(today, today.plusMonths(1).minusDays(1))
            else -> Pair(today.minusDays(7), today.plusDays(14))
        }
        searching = true
        searchError = null
        viewModelScope.launch {
            repository.searchEvents(condition, start, end)
                .onSuccess {
                    results = it
                }
                .onFailure {
                    searchError = errorText(it)
                }
            searching = false
        }
    }
}
