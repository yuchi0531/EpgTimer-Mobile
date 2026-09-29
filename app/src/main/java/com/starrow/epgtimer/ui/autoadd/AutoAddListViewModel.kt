package com.starrow.epgtimer.ui.autoadd

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.starrow.epgtimer.data.model.EpgAutoAddData
import com.starrow.epgtimer.data.repository.EpgRepository
import com.starrow.epgtimer.ui.errorText
import kotlinx.coroutines.launch

class AutoAddListViewModel(private val repository: EpgRepository) : ViewModel() {

    var items by mutableStateOf<List<EpgAutoAddData>?>(null)
        private set

    var loading by mutableStateOf(false)
        private set

    var error by mutableStateOf<String?>(null)
        private set

    var deleting by mutableStateOf(false)
        private set

    fun refresh() {
        loading = true
        viewModelScope.launch {
            repository.getAutoAdds()
                .onSuccess {
                    items = it
                    error = null
                }
                .onFailure {
                    error = errorText(it)
                }
            loading = false
        }
    }

    fun delete(dataId: Int, onResult: (Result<Unit>) -> Unit) {
        if (deleting) return
        deleting = true
        viewModelScope.launch {
            val result = repository.deleteAutoAdd(listOf(dataId))
            deleting = false
            result.onSuccess { refresh() }
            onResult(result)
        }
    }
}
