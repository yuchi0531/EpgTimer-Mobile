package com.starrow.epgtimer.ui.autoadd

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FloatingActionButton
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.starrow.epgtimer.data.guide.parseAndKey
import com.starrow.epgtimer.data.model.EpgAutoAddData
import com.starrow.epgtimer.data.repository.EpgRepository
import com.starrow.epgtimer.ui.genreNames
import com.starrow.epgtimer.ui.recModeText
import com.starrow.epgtimer.ui.vmFactory

@Composable
fun AutoAddListScreen(
    repository: EpgRepository,
    onAdd: () -> Unit,
    onEdit: (EpgAutoAddData) -> Unit,
) {
    val viewModel: AutoAddListViewModel = viewModel(factory = vmFactory { AutoAddListViewModel(repository) })
    LaunchedEffect(Unit) { viewModel.refresh() }
    val items = viewModel.items
    var pendingDelete by remember { mutableStateOf<EpgAutoAddData?>(null) }

    Box(Modifier.fillMaxSize()) {
        when {
            items == null && viewModel.error != null -> {
                Column(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = viewModel.error.orEmpty(),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Button(onClick = { viewModel.refresh() }, modifier = Modifier.padding(top = 12.dp)) {
                        Text("再試行")
                    }
                }
            }
            items == null -> {
                CircularProgressIndicator(Modifier.align(Alignment.Center))
            }
            items.isEmpty() -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("自動予約条件がありません", style = MaterialTheme.typography.bodyLarge)
                }
            }
            else -> {
                LazyColumn(Modifier.fillMaxSize()) {
                    items(items.size, key = { items[it].dataId }) { index ->
                        AutoAddRow(
                            item = items[index],
                            onClick = { onEdit(items[index]) },
                            onDelete = { pendingDelete = items[index] },
                        )
                    }
                }
            }
        }
        if (items != null && viewModel.loading) {
            CircularProgressIndicator(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(8.dp),
            )
        }
        FloatingActionButton(
            onClick = onAdd,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp),
        ) {
            Icon(Icons.Filled.Add, contentDescription = "自動予約条件を追加")
        }
    }

    pendingDelete?.let { target ->
        AlertDialog(
            onDismissRequest = { if (!viewModel.deleting) pendingDelete = null },
            title = { Text("自動予約条件の削除") },
            text = { Text("この条件と、条件によって作成された予約を削除しますか？") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.delete(target.dataId) { result ->
                            if (result.isSuccess) pendingDelete = null
                        }
                    },
                    enabled = !viewModel.deleting,
                ) {
                    Text("削除")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { pendingDelete = null },
                    enabled = !viewModel.deleting,
                ) {
                    Text("キャンセル")
                }
            },
        )
    }
}

@Composable
private fun AutoAddRow(
    item: EpgAutoAddData,
    onClick: () -> Unit,
    onDelete: () -> Unit,
) {
    val and = parseAndKey(item.searchKey.andKey)
    val title = and.plain.ifBlank { "(キーワードなし)" }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            color = if (and.disabled) {
                MaterialTheme.colorScheme.onSurfaceVariant
            } else {
                MaterialTheme.colorScheme.onSurface
            },
        )
        if (item.searchKey.notKey.isNotBlank()) {
            Text(
                text = "NOT: ${item.searchKey.notKey}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Text(
            text = "検索ヒット数 ${item.addCount} ・ ${recModeText(item.recSetting)} ・ 優先度 ${item.recSetting.priority}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = "サービス ${item.searchKey.serviceList.size} ・ ジャンル ${item.searchKey.contentList.size}" +
                if (item.searchKey.contentList.isEmpty()) {
                    ""
                } else {
                    "(${genreNames(item.searchKey.contentList).joinToString(",")})"
                },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (and.disabled) {
                Text(
                    text = "登録OFF",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            TextButton(onClick = onDelete) {
                Text("削除")
            }
        }
    }
}
