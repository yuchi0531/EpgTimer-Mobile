package com.starrow.epgtimer.ui.reservation

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.starrow.epgtimer.data.model.RecSettingData
import com.starrow.epgtimer.data.model.ReserveData
import com.starrow.epgtimer.data.repository.EpgRepository
import com.starrow.epgtimer.ui.SelectedContent
import com.starrow.epgtimer.ui.errorText
import kotlinx.coroutines.launch

class ReservationViewModel(private val repository: EpgRepository) : ViewModel() {

    var reserves by mutableStateOf<List<ReserveData>?>(null)
        private set

    var loading by mutableStateOf(false)
        private set

    var error by mutableStateOf<String?>(null)
        private set

    var submitting by mutableStateOf(false)
        private set

    var detail by mutableStateOf<ReserveData?>(null)
        private set

    var detailLoading by mutableStateOf(false)
        private set

    var detailError by mutableStateOf<String?>(null)
        private set

    fun refresh() {
        loading = true
        viewModelScope.launch {
            repository.getReserves()
                .onSuccess {
                    reserves = it
                    error = null
                }
                .onFailure {
                    error = errorText(it)
                }
            loading = false
        }
    }

    fun loadDetail(reserveId: Int) {
        val holder = SelectedContent.reserve
        if (holder != null && holder.reserveId == reserveId) {
            detail = holder
            detailError = null
            detailLoading = false
            return
        }
        if (reserveId == MISSING_RESERVE_ID) {
            detail = null
            detailError = "表示する予約が指定されていません"
            detailLoading = false
            return
        }
        detail = null
        detailError = null
        detailLoading = true
        viewModelScope.launch {
            repository.getReserve(reserveId)
                .onSuccess {
                    detail = it
                    detailError = null
                }
                .onFailure {
                    detailError = errorText(it)
                }
            detailLoading = false
        }
    }

    fun change(reserve: ReserveData, recSetting: RecSettingData, onResult: (Result<Unit>) -> Unit) {
        if (submitting) return
        submitting = true
        viewModelScope.launch {
            val updated = reserve.copy(recSetting = recSetting)
            val result = repository.changeReserve(updated)
            submitting = false
            if (result.isSuccess) {
                SelectedContent.reserve = updated
                if (detail?.reserveId == updated.reserveId) {
                    detail = updated
                }
            }
            onResult(result)
        }
    }

    fun delete(reserveId: Int, onResult: (Result<Unit>) -> Unit) {
        if (submitting) return
        submitting = true
        viewModelScope.launch {
            val result = repository.deleteReserve(listOf(reserveId))
            submitting = false
            onResult(result)
        }
    }

    companion object {
        const val MISSING_RESERVE_ID = -1
    }
}
