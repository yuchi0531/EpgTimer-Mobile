package com.starrow.epgtimer.ui.program

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.starrow.epgtimer.data.repository.EpgRepository
import com.starrow.epgtimer.ui.audioComponentTexts
import com.starrow.epgtimer.ui.dateTimeRangeLabel
import com.starrow.epgtimer.ui.durationLabel
import com.starrow.epgtimer.ui.errorText
import com.starrow.epgtimer.ui.freeCaText
import com.starrow.epgtimer.ui.genreNames
import com.starrow.epgtimer.ui.videoComponentText
import com.starrow.epgtimer.ui.vmFactory

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProgramDetailScreen(
    repository: EpgRepository,
    eventKey: Long,
    onBack: () -> Unit,
    onToast: (String) -> Unit,
) {
    val viewModel: ProgramDetailViewModel = viewModel(factory = vmFactory { ProgramDetailViewModel(repository) })
    LaunchedEffect(eventKey) { viewModel.load(eventKey) }
    val event = viewModel.event
    val loading = viewModel.eventLoading
    val eventError = viewModel.eventError
    var showDialog by remember { mutableStateOf(false) }
    var reserveError by remember { mutableStateOf<String?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("番組詳細") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "戻る")
                    }
                },
            )
        },
    ) { padding ->
        when {
            loading -> {
                Box(
                    Modifier
                        .fillMaxSize()
                        .padding(padding),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator()
                }
            }
            event == null -> {
                Box(
                    Modifier
                        .fillMaxSize()
                        .padding(padding),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = eventError ?: "番組情報を取得できません",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(horizontal = 24.dp),
                        )
                        Button(
                            onClick = { viewModel.load(eventKey) },
                            modifier = Modifier.padding(top = 12.dp),
                        ) {
                            Text("再試行")
                        }
                    }
                }
            }
            else -> {
                val service = viewModel.service
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text(
                        text = event.title,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                    )
                    val serviceName = when {
                        service == null -> "-"
                        service.serviceName.isNotBlank() -> service.serviceName
                        else -> service.networkName
                    }
                    DetailRow(label = "放送局", value = serviceName)
                    val start = event.startDateTime
                    if (start != null) {
                        DetailRow(label = "放送日時", value = dateTimeRangeLabel(start, event.endDateTime))
                    }
                    DetailRow(label = "放送時間", value = durationLabel(event.durationSeconds))
                    val genres = genreNames(event.contentInfo?.nibbleList)
                    if (genres.isNotEmpty()) {
                        DetailRow(label = "ジャンル", value = genres.joinToString(" / "))
                    }
                    val component = event.componentInfo
                    if (component != null) {
                        DetailRow(
                            label = "映像",
                            value = videoComponentText(component.streamContent, component.componentType, component.text),
                        )
                    }
                    val audioTexts = audioComponentTexts(event.audioInfo?.componentList)
                    if (audioTexts.isNotEmpty()) {
                        DetailRow(label = "音声", value = audioTexts.joinToString(" / "))
                    }
                    DetailRow(label = "無料/有料", value = freeCaText(event.freeCaFlag))
                    val description = event.extInfo?.text?.takeIf { it.isNotBlank() }
                        ?: event.shortInfo?.text?.takeIf { it.isNotBlank() }
                        ?: "なし"
                    Text("番組説明", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Text(description, style = MaterialTheme.typography.bodyMedium)
                    Button(
                        onClick = {
                            reserveError = null
                            showDialog = true
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("録画予約")
                    }
                    Text(
                        text = "予約済みの場合は「予約」画面から変更・削除してください",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (showDialog) {
                    RecSettingDialog(
                        title = "録画予約",
                        confirmLabel = "登録",
                        initial = viewModel.defaultRecSetting,
                        loaded = viewModel.defaultLoaded,
                        loadError = viewModel.defaultError,
                        submitting = viewModel.submitting,
                        error = reserveError,
                        onConfirm = { recSetting ->
                            viewModel.reserve(event, service, recSetting) { result ->
                                result
                                    .onSuccess {
                                        onToast("録画予約を登録しました")
                                        showDialog = false
                                        onBack()
                                    }
                                    .onFailure {
                                        reserveError = errorText(it)
                                    }
                            }
                        },
                        onDismiss = { showDialog = false },
                    )
                }
            }
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth()) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(end = 16.dp),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f),
        )
    }
}
