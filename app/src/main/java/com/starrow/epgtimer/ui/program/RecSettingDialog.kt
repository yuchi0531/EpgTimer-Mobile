package com.starrow.epgtimer.ui.program

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.starrow.epgtimer.data.model.RecSettingData
import com.starrow.epgtimer.ui.REC_MODE_LABELS

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecSettingDialog(
    title: String,
    confirmLabel: String,
    initial: RecSettingData?,
    loaded: Boolean,
    loadError: String?,
    submitting: Boolean,
    error: String?,
    onConfirm: (RecSettingData) -> Unit,
    onDismiss: () -> Unit,
) {
    val base = initial ?: RecSettingData.empty()
    var recMode by remember(base) { mutableIntStateOf(base.effectiveRecMode) }
    var noRec by remember(base) { mutableStateOf(base.isNoRec) }
    var priority by remember(base) { mutableIntStateOf(base.priority.coerceIn(1, 15)) }
    var tuijyuu by remember(base) { mutableStateOf(base.tuijyuuFlag != 0) }
    var useMargin by remember(base) { mutableStateOf(base.useMargineFlag != 0) }
    var startMargin by remember(base) { mutableStateOf((base.startMargine / 60).toString()) }
    var endMargin by remember(base) { mutableStateOf((base.endMargine / 60).toString()) }
    var modeExpanded by remember { mutableStateOf(false) }
    var priorityExpanded by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = { if (!submitting) onDismiss() },
        title = { Text(title) },
        text = {
            if (!loaded) {
                CircularProgressIndicator(Modifier.padding(16.dp))
            } else {
                Column(
                    modifier = Modifier
                        .heightIn(max = 420.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (loadError != null) {
                        Text(
                            text = "既定設定を取得できません(既定値で登録)",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                    ExposedDropdownMenuBox(
                        expanded = modeExpanded,
                        onExpandedChange = { modeExpanded = it },
                    ) {
                        OutlinedTextField(
                            value = REC_MODE_LABELS.getOrElse(recMode) { "不明($recMode)" },
                            onValueChange = {},
                            readOnly = true,
                            singleLine = true,
                            label = { Text("録画モード") },
                            trailingIcon = {
                                ExposedDropdownMenuDefaults.TrailingIcon(expanded = modeExpanded)
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .menuAnchor(MenuAnchorType.PrimaryNotEditable),
                        )
                        ExposedDropdownMenu(
                            expanded = modeExpanded,
                            onDismissRequest = { modeExpanded = false },
                        ) {
                            REC_MODE_LABELS.forEachIndexed { index, label ->
                                DropdownMenuItem(
                                    text = { Text(label) },
                                    onClick = {
                                        recMode = index
                                        modeExpanded = false
                                    },
                                )
                            }
                        }
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("予約を有効にする", style = MaterialTheme.typography.bodyMedium)
                        Switch(checked = !noRec, onCheckedChange = { noRec = !it })
                    }
                    ExposedDropdownMenuBox(
                        expanded = priorityExpanded,
                        onExpandedChange = { priorityExpanded = it },
                    ) {
                        OutlinedTextField(
                            value = "優先度 $priority",
                            onValueChange = {},
                            readOnly = true,
                            singleLine = true,
                            label = { Text("優先度") },
                            trailingIcon = {
                                ExposedDropdownMenuDefaults.TrailingIcon(expanded = priorityExpanded)
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .menuAnchor(MenuAnchorType.PrimaryNotEditable),
                        )
                        ExposedDropdownMenu(
                            expanded = priorityExpanded,
                            onDismissRequest = { priorityExpanded = false },
                        ) {
                            for (value in 15 downTo 1) {
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            when (value) {
                                                15 -> "15 (高)"
                                                1 -> "1 (低)"
                                                else -> "$value"
                                            },
                                        )
                                    },
                                    onClick = {
                                        priority = value
                                        priorityExpanded = false
                                    },
                                )
                            }
                        }
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("イベントリレー追従", style = MaterialTheme.typography.bodyMedium)
                        Switch(checked = tuijyuu, onCheckedChange = { tuijyuu = it })
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("マージン個別指定", style = MaterialTheme.typography.bodyMedium)
                        Switch(checked = useMargin, onCheckedChange = { useMargin = it })
                    }
                    if (useMargin) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = startMargin,
                                onValueChange = { text ->
                                    startMargin = text.filter { it == '-' || it.isDigit() }.take(6)
                                },
                                label = { Text("開始(分)") },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier.width(140.dp),
                            )
                            OutlinedTextField(
                                value = endMargin,
                                onValueChange = { text ->
                                    endMargin = text.filter { it == '-' || it.isDigit() }.take(6)
                                },
                                label = { Text("終了(分)") },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier.width(140.dp),
                            )
                        }
                    }
                    val folder = base.recFolderList.firstOrNull()?.recFolder
                    Text(
                        text = "録画フォルダ: ${if (folder.isNullOrBlank()) "既定" else folder}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (base.batFilePath.isNotBlank()) {
                        Text(
                            text = "録画後実行: ${base.batFilePath}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (error != null) {
                        Text(
                            text = error,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val startValue = marginMinutes(startMargin)
                    val endValue = marginMinutes(endMargin)
                    onConfirm(
                        base.copy(
                            recMode = if (noRec) 5 + (recMode + 4) % 5 else recMode,
                            priority = priority,
                            tuijyuuFlag = if (tuijyuu) 1 else 0,
                            useMargineFlag = if (useMargin) 1 else 0,
                            startMargine = if (useMargin) startValue * 60 else base.startMargine,
                            endMargine = if (useMargin) endValue * 60 else base.endMargine,
                        ),
                    )
                },
                enabled = loaded && !submitting,
            ) {
                if (submitting) {
                    CircularProgressIndicator(Modifier.padding(4.dp))
                } else {
                    Text(confirmLabel)
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !submitting) {
                Text("キャンセル")
            }
        },
    )
}

private fun marginMinutes(text: String): Int =
    text.toIntOrNull()?.coerceIn(-1440, 1440) ?: 0
