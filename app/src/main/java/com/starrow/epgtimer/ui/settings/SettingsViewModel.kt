package com.starrow.epgtimer.ui.settings

import android.content.Context
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.starrow.epgtimer.data.edcb.ServerStatus
import com.starrow.epgtimer.data.repository.EpgRepository
import com.starrow.epgtimer.data.repository.ImportGuidesResult
import com.starrow.epgtimer.ui.errorText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SettingsViewModel(private val repository: EpgRepository) : ViewModel() {

    val serverConfig = repository.serverConfig
    val cacheEnabled = repository.cacheEnabled
    val guides = repository.guides
    val guidesSource = repository.guidesSource

    var testing by mutableStateOf(false)
        private set

    var testResult by mutableStateOf<String?>(null)
        private set

    var importing by mutableStateOf(false)
        private set

    var importResult by mutableStateOf<ImportGuidesResult?>(null)
        private set

    var importError by mutableStateOf<String?>(null)
        private set

    fun updateServer(host: String, port: Int) {
        val config = serverConfig.value
        repository.updateServerConfig(config.copy(host = host, port = port))
    }

    fun updateTimeouts(connectTimeoutMs: Int, readTimeoutMs: Int) {
        val config = serverConfig.value
        repository.updateServerConfig(
            config.copy(
                connectTimeoutMs = connectTimeoutMs.coerceIn(1000, 300000),
                readTimeoutMs = readTimeoutMs.coerceIn(1000, 600000),
            ),
        )
    }

    fun testConnection() {
        if (testing) return
        testing = true
        testResult = null
        viewModelScope.launch {
            repository.testConnection()
                .onSuccess { status: ServerStatus ->
                    testResult = "接続成功: ${status.srvStatusText}"
                }
                .onFailure {
                    testResult = "接続失敗: ${errorText(it)}"
                }
            testing = false
        }
    }

    fun setCacheEnabled(enabled: Boolean) {
        repository.setCacheEnabled(enabled)
    }

    fun clearCache() {
        repository.clearCache()
    }

    fun importGuides(context: Context, uri: Uri) {
        if (importing) return
        importing = true
        importResult = null
        importError = null
        viewModelScope.launch {
            val stream = withContext(Dispatchers.IO) {
                runCatching { context.contentResolver.openInputStream(uri) }.getOrNull()
            }
            if (stream == null) {
                importError = "ファイルを開けません"
                importing = false
                return@launch
            }
            repository.importGuides(stream)
                .onSuccess {
                    importResult = it
                }
                .onFailure {
                    importError = errorText(it)
                }
            importing = false
        }
    }
}
