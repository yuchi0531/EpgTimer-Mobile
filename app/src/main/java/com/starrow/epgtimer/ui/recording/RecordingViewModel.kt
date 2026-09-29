package com.starrow.epgtimer.ui.recording

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.starrow.epgtimer.data.model.RecFileInfo
import com.starrow.epgtimer.data.repository.EpgRepository
import com.starrow.epgtimer.ui.errorText
import kotlinx.coroutines.launch

class RecordingViewModel(private val repository: EpgRepository) : ViewModel() {

    var recFiles by mutableStateOf<List<RecFileInfo>?>(null)
        private set

    var loading by mutableStateOf(false)
        private set

    var error by mutableStateOf<String?>(null)
        private set

    fun refresh() {
        loading = true
        viewModelScope.launch {
            repository.getRecFiles()
                .onSuccess {
                    recFiles = it
                    error = null
                }
                .onFailure {
                    error = errorText(it)
                }
            loading = false
        }
    }
}
