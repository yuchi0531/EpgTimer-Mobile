package com.starrow.epgtimer.ui.reservation

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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import com.starrow.epgtimer.ui.dateTimeLabel
import com.starrow.epgtimer.ui.durationLabel
import com.starrow.epgtimer.ui.errorText
import com.starrow.epgtimer.ui.overlapModeText
import com.starrow.epgtimer.ui.recModeText
import com.starrow.epgtimer.ui.reserveStatusText
import com.starrow.epgtimer.ui.timeLabel
import com.starrow.epgtimer.ui.vmFactory
import com.starrow.epgtimer.ui.program.RecSettingDialog

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReserveDetailScreen(
    repository: EpgRepository,
    reserveId: Int,
    onBack: () -> Unit,
    onToast: (String) -> Unit,
) {
    val viewModel: ReservationViewModel = viewModel(factory = vmFactory { ReservationViewModel(repository) })
    LaunchedEffect(reserveId) { viewModel.loadDetail(reserveId) }
    val reserve = viewModel.detail
    var showChangeDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var changeError by remember { mutableStateOf<String?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("予約詳細") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "戻る")
                    }
                },
            )
        },
    ) { padding ->
        when {
            viewModel.detailLoading -> {
                Box(
                    Modifier
                        .fillMaxSize()
                        .padding(padding),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator()
                }
            }
            reserve == null -> {
                Box(
                    Modifier
                        .fillMaxSize()
                        .padding(padding),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = viewModel.detailError ?: "予約情報を取得できません",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(horizontal = 24.dp),
                        )
                        Button(
                            onClick = { viewModel.loadDetail(reserveId) },
                            modifier = Modifier.padding(top = 12.dp),
                        ) {
                            Text("再試行")
                        }
                    }
                }
            }
            else -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = reserve.title,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                    )
                    val start = reserve.startTime.toLocalDateTime()
                    ReserveRow("開始時間", dateTimeLabel(reserve.startTime))
                    if (start != null) {
                        ReserveRow(
                            "終了時間",
                            timeLabel(start.plusSeconds(reserve.durationSecond.toLong())),
                        )
                    }
                    ReserveRow("長さ", durationLabel(reserve.durationSecond))
                    ReserveRow("サービス", reserve.stationName.ifBlank { "-" })
                    ReserveRow("ONID", reserve.onid.toString())
                    ReserveRow("TSID", reserve.tsid.toString())
                    ReserveRow("SID", reserve.sid.toString())
                    ReserveRow("EventID", reserve.eventId.toString())
                    ReserveRow("予約ID", reserve.reserveId.toString())
                    ReserveRow("コメント", reserve.comment.ifBlank { "-" })
                    val overlap = overlapModeText(reserve.overlapMode)
                    val status = reserveStatusText(reserve.reserveStatus)
                    ReserveRow(
                        "状態",
                        listOf(overlap, status).filter { it.isNotBlank() }.joinToString(" / ").ifBlank { "通常" },
                    )
                    ReserveRow("録画モード", recModeText(reserve.recSetting))
                    ReserveRow("優先度", reserve.recSetting.priority.toString())
                    ReserveRow("追従", if (reserve.recSetting.tuijyuuFlag != 0) "あり" else "なし")
                    ReserveRow(
                        "マージン",
                        if (reserve.recSetting.useMargineFlag == 0) {
                            "既定"
                        } else {
                            "開始${reserve.recSetting.startMargine / 60}分 / 終了${reserve.recSetting.endMargine / 60}分"
                        },
                    )
                    val folder = reserve.recSetting.recFolderList.firstOrNull()?.recFolder
                    ReserveRow("録画フォルダ", if (folder.isNullOrBlank()) "既定" else folder)
                    ReserveRow(
                        "予定録画ファイル",
                        reserve.recFileNameList.joinToString("\n").ifBlank { "-" },
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Button(
                            onClick = {
                                changeError = null
                                showChangeDialog = true
                            },
                            modifier = Modifier.weight(1f),
                        ) {
                            Text("変更")
                        }
                        Button(
                            onClick = { showDeleteDialog = true },
                            modifier = Modifier.weight(1f),
                        ) {
                            Text("削除")
                        }
                    }
                }
                if (showChangeDialog) {
                    RecSettingDialog(
                        title = "予約変更",
                        confirmLabel = "変更",
                        initial = reserve.recSetting,
                        loaded = true,
                        loadError = null,
                        submitting = viewModel.submitting,
                        error = changeError,
                        onConfirm = { recSetting ->
                            viewModel.change(reserve, recSetting) { result ->
                                result
                                    .onSuccess {
                                        showChangeDialog = false
                                        onToast("予約を変更しました")
                                    }
                                    .onFailure {
                                        changeError = errorText(it)
                                    }
                            }
                        },
                        onDismiss = { showChangeDialog = false },
                    )
                }
                if (showDeleteDialog) {
                    AlertDialog(
                        onDismissRequest = { if (!viewModel.submitting) showDeleteDialog = false },
                        title = { Text("予約削除") },
                        text = { Text("この予約を削除しますか？") },
                        confirmButton = {
                            TextButton(
                                onClick = {
                                    viewModel.delete(reserve.reserveId) { result ->
                                        result
                                            .onSuccess {
                                                showDeleteDialog = false
                                                onToast("予約を削除しました")
                                                onBack()
                                            }
                                            .onFailure {
                                                onToast(errorText(it))
                                            }
                                    }
                                },
                                enabled = !viewModel.submitting,
                            ) {
                                Text("削除")
                            }
                        },
                        dismissButton = {
                            TextButton(
                                onClick = { showDeleteDialog = false },
                                enabled = !viewModel.submitting,
                            ) {
                                Text("キャンセル")
                            }
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun ReserveRow(label: String, value: String) {
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
