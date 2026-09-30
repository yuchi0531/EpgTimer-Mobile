package com.starrow.epgtimer.ui.recording

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.starrow.epgtimer.data.model.RecFileInfo
import com.starrow.epgtimer.data.repository.EpgRepository
import com.starrow.epgtimer.ui.dayHeaderLabel
import com.starrow.epgtimer.ui.recStatusText
import com.starrow.epgtimer.ui.timeLabel
import com.starrow.epgtimer.ui.vmFactory

@Composable
fun RecordingScreen(
    repository: EpgRepository,
) {
    val viewModel: RecordingViewModel = viewModel(factory = vmFactory { RecordingViewModel(repository) })
    LaunchedEffect(Unit) { viewModel.refresh() }
    val recFiles = viewModel.recFiles
    val loading = viewModel.loading
    val error = viewModel.error
    var selected by remember { mutableStateOf<RecFileInfo?>(null) }

    Box(Modifier.fillMaxSize()) {
        when {
            recFiles == null && loading -> {
                CircularProgressIndicator(Modifier.align(Alignment.Center))
            }
            recFiles == null && error != null -> {
                Column(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = error,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Button(onClick = { viewModel.refresh() }, modifier = Modifier.padding(top = 12.dp)) {
                        Text("再試行")
                    }
                }
            }
            recFiles == null -> {
                CircularProgressIndicator(Modifier.align(Alignment.Center))
            }
            recFiles.isEmpty() -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("録画済みがありません", style = MaterialTheme.typography.bodyLarge)
                }
            }
            else -> {
                val groups = remember(recFiles) {
                    recFiles
                        .mapNotNull { file -> file.startTime.toLocalDateTime()?.let { Pair(it, file) } }
                        .sortedByDescending { it.first }
                        .groupBy { it.first.toLocalDate() }
                        .toSortedMap(compareByDescending { it })
                }
                LazyColumn(Modifier.fillMaxSize()) {
                    for ((date, list) in groups) {
                        item(key = "header-$date") {
                            Text(
                                text = dayHeaderLabel(date),
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 8.dp),
                            )
                        }
                        items(list.size, key = { list[it].second.id }) { index ->
                            val file = list[index].second
                            RecFileRow(file = file, onClick = { selected = file })
                        }
                    }
                }
            }
        }
        if (loading && recFiles != null) {
            CircularProgressIndicator(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(8.dp),
            )
        }
    }

    val file = selected
    if (file != null) {
        AlertDialog(
            onDismissRequest = { selected = null },
            title = { Text(file.title, maxLines = 2, overflow = TextOverflow.Ellipsis) },
            text = {
                Column(
                    modifier = Modifier
                        .verticalScroll(rememberScrollState())
                        .padding(4.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    DialogRow("ファイルパス", file.recFilePath.ifBlank { "-" })
                    DialogRow("番組情報", file.programInfo.ifBlank { "-" })
                    DialogRow("エラー情報", file.errInfo.ifBlank { "-" })
                }
            },
            confirmButton = {
                TextButton(onClick = { selected = null }) {
                    Text("閉じる")
                }
            },
        )
    }
}

@Composable
private fun RecFileRow(file: RecFileInfo, onClick: () -> Unit) {
    val start = file.startTime.toLocalDateTime()
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Row(verticalAlignment = Alignment.Top) {
            Text(
                text = if (start == null) "--:--" else timeLabel(start),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(end = 12.dp),
            )
            Column(Modifier.weight(1f)) {
                Text(
                    text = file.title,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (file.serviceName.isNotBlank()) {
                    Text(
                        text = file.serviceName,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (file.protectFlag != 0) {
                Icon(
                    Icons.Outlined.Lock,
                    contentDescription = "保護",
                    modifier = Modifier
                        .padding(start = 8.dp)
                        .size(18.dp),
                )
            }
        }
        Text(
            text = "drops ${file.drops} / scrambles ${file.scrambles}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = recStatusText(file.recStatus, file.recFilePath.isNotBlank()),
            style = MaterialTheme.typography.bodySmall,
            color = if (file.recStatus == 1) {
                MaterialTheme.colorScheme.onSurfaceVariant
            } else {
                MaterialTheme.colorScheme.error
            },
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun DialogRow(label: String, value: String) {
    Column {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(text = value, style = MaterialTheme.typography.bodyMedium)
    }
}
