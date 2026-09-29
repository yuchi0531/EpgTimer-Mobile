package com.starrow.epgtimer.ui.autoadd

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.starrow.epgtimer.data.model.ServiceInfo
import com.starrow.epgtimer.data.repository.EpgRepository
import com.starrow.epgtimer.ui.LEVEL1_GENRES
import com.starrow.epgtimer.ui.REC_MODE_LABELS
import com.starrow.epgtimer.ui.vmFactory

private val WEEKDAY_LABELS = listOf("日", "月", "火", "水", "木", "金", "土")

private const val KEYWORD_HELP =
    "検索対象: :title: ネコ(番組名) / :event: (番組名+概要) / :genre: (ジャンル) / " +
        ":video: (映像) / :audio: (音声) / ::title:^ネコ(正規表現) / OR検索はスペース区切り"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AutoAddEditScreen(
    repository: EpgRepository,
    initial: AutoAddForm,
    onBack: () -> Unit,
    onSaved: () -> Unit,
) {
    val viewModel: AutoAddEditViewModel =
        viewModel(factory = vmFactory { AutoAddEditViewModel(repository, initial) })
    val form = viewModel.form

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (form.isNew) "自動予約条件の追加" else "自動予約条件の変更") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "戻る")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = "条件を登録すると、EPG更新時にこの条件に合う番組が自動で予約されます。" +
                    "予約はサーバ側で作成されます。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            AutoAddFormSection(
                form = form,
                update = viewModel::update,
                services = viewModel.services,
            )
            viewModel.error?.let { message ->
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            Button(
                onClick = { viewModel.save { result -> if (result.isSuccess) onSaved() } },
                enabled = !viewModel.submitting,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
            ) {
                if (viewModel.submitting) {
                    CircularProgressIndicator(Modifier.padding(4.dp))
                } else {
                    Text(if (form.isNew) "登録" else "変更")
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AutoAddFormSection(
    form: AutoAddForm,
    update: ((AutoAddForm) -> AutoAddForm) -> Unit,
    services: List<ServiceInfo>,
) {
    SectionTitle("検索条件")
    OutlinedTextField(
        value = form.andKey,
        onValueChange = { value -> update { it.copy(andKey = value) } },
        label = { Text("キーワード(検索対象を絞る)") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
    Text(
        text = KEYWORD_HELP,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    OutlinedTextField(
        value = form.notKey,
        onValueChange = { value -> update { it.copy(notKey = value) } },
        label = { Text("NOTキーワード") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
    OutlinedTextField(
        value = form.note,
        onValueChange = { value -> update { it.copy(note = value) } },
        label = { Text("メモ(自動でエスケープ)") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
    SwitchRow("正規表現", form.regExpFlag) { on ->
        update { it.copy(regExpFlag = on, aimaiFlag = if (on) false else it.aimaiFlag) }
    }
    if (!form.regExpFlag) {
        SwitchRow("あいまい検索", form.aimaiFlag) { on -> update { it.copy(aimaiFlag = on) } }
    }
    SwitchRow("番組名のみ", form.titleOnlyFlag) { on -> update { it.copy(titleOnlyFlag = on) } }
    SwitchRow("大小文字区別", form.caseSensitive) { on -> update { it.copy(caseSensitive = on) } }
    SwitchRow("自動登録を無効にする", form.keyDisabled) { on -> update { it.copy(keyDisabled = on) } }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = form.durationMin,
            onValueChange = { value ->
                update { it.copy(durationMin = value.filter(Char::isDigit).take(4)) }
            },
            label = { Text("番組長 最小(分)") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.width(150.dp),
        )
        OutlinedTextField(
            value = form.durationMax,
            onValueChange = { value ->
                update { it.copy(durationMax = value.filter(Char::isDigit).take(4)) }
            },
            label = { Text("最大(分・0=無制限)") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.width(150.dp),
        )
    }

    SectionTitle("ジャンル絞り込み")
    LEVEL1_GENRES.chunked(3).forEach { row ->
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            row.forEach { (level1, label) ->
                FilterChip(
                    selected = level1 in form.genreLevel1,
                    onClick = {
                        update { current ->
                            val next = current.genreLevel1.toMutableSet()
                            if (!next.add(level1)) next.remove(level1)
                            current.copy(genreLevel1 = next)
                        }
                    },
                    label = { Text(label, style = MaterialTheme.typography.bodySmall) },
                )
            }
        }
    }
    SwitchRow("指定したジャンル以外を対象(NOT)", form.notContent) { on ->
        update { it.copy(notContent = on) }
    }

    SectionTitle("曜日/時刻")
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        WEEKDAY_LABELS.forEachIndexed { day, label ->
            FilterChip(
                selected = day in form.dayOfWeek,
                onClick = {
                    update { current ->
                        val next = current.dayOfWeek.toMutableSet()
                        if (!next.add(day)) next.remove(day)
                        current.copy(dayOfWeek = next)
                    }
                },
                label = { Text(label) },
            )
        }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        NumberField("開始 時刻", form.startHour, 23) { h -> update { it.copy(startHour = h) } }
        NumberField("分", form.startMin, 59) { m -> update { it.copy(startMin = m) } }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        NumberField("終了 時刻", form.endHour, 23) { h -> update { it.copy(endHour = h) } }
        NumberField("分", form.endMin, 59) { m -> update { it.copy(endMin = m) } }
    }
    SwitchRow("指定した曜日/時刻以外を対象(NOT)", form.notDate) { on -> update { it.copy(notDate = on) } }

    SectionTitle("スクランブル")
    var freeExpanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = freeExpanded, onExpandedChange = { freeExpanded = it }) {
        OutlinedTextField(
            value = FREE_CA_LABELS[form.freeCaFlag.coerceIn(0, 2)],
            onValueChange = {},
            readOnly = true,
            singleLine = true,
            label = { Text("対象") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = freeExpanded) },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(MenuAnchorType.PrimaryNotEditable),
        )
        ExposedDropdownMenu(expanded = freeExpanded, onDismissRequest = { freeExpanded = false }) {
            FREE_CA_LABELS.forEachIndexed { index, label ->
                DropdownMenuItem(
                    text = { Text(label) },
                    onClick = {
                        update { it.copy(freeCaFlag = index) }
                        freeExpanded = false
                    },
                )
            }
        }
    }

    if (services.isNotEmpty()) {
        SectionTitle("サービス絞り込み(未選択=全サービス)")
        Column(
            modifier = Modifier
                .heightIn(max = 200.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            services.forEach { service ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Switch(
                        checked = service.key in form.serviceKeys,
                        onCheckedChange = { on ->
                            update { current ->
                                val next = current.serviceKeys.toMutableSet()
                                if (on) next.add(service.key) else next.remove(service.key)
                                current.copy(serviceKeys = next)
                            }
                        },
                    )
                    Text(
                        text = "${service.networkName} ${service.serviceName}",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
    }

    SectionTitle("自動予約登録専用")
    SwitchRow("同一番組名の録画結果があれば無効で登録する", form.chkRecEnd) { on ->
        update { it.copy(chkRecEnd = on) }
    }
    OutlinedTextField(
        value = form.chkRecDay,
        onValueChange = { value -> update { it.copy(chkRecDay = value.filter(Char::isDigit).take(4)) } },
        label = { Text("確認対象期間(日)") },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = Modifier.width(180.dp),
    )
    SwitchRow("全てのサービスで無効にする", form.chkRecNoService) { on ->
        update { it.copy(chkRecNoService = on) }
    }

    SectionTitle("録画設定")
    val base = form.recSetting
    var recModeExpanded by remember { mutableStateOf(false) }
    var priorityExpanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = recModeExpanded, onExpandedChange = { recModeExpanded = it }) {
        OutlinedTextField(
            value = REC_MODE_LABELS.getOrElse(base.effectiveRecMode) { "不明(${base.effectiveRecMode})" },
            onValueChange = {},
            readOnly = true,
            singleLine = true,
            label = { Text("録画モード") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = recModeExpanded) },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(MenuAnchorType.PrimaryNotEditable),
        )
        ExposedDropdownMenu(expanded = recModeExpanded, onDismissRequest = { recModeExpanded = false }) {
            REC_MODE_LABELS.forEachIndexed { index, label ->
                DropdownMenuItem(
                    text = { Text(label) },
                    onClick = {
                        update { it.copy(recSetting = it.recSetting.copy(recMode = index)) }
                        recModeExpanded = false
                    },
                )
            }
        }
    }
    SwitchRow("予約を有効にする", !base.isNoRec) { on ->
        update { current ->
            val mode = current.recSetting.effectiveRecMode
            current.copy(
                recSetting = current.recSetting.copy(
                    recMode = if (on) mode else 5 + (mode + 4) % 5,
                ),
            )
        }
    }
    ExposedDropdownMenuBox(expanded = priorityExpanded, onExpandedChange = { priorityExpanded = it }) {
        OutlinedTextField(
            value = "優先度 ${base.priority}",
            onValueChange = {},
            readOnly = true,
            singleLine = true,
            label = { Text("優先度") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = priorityExpanded) },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(MenuAnchorType.PrimaryNotEditable),
        )
        ExposedDropdownMenu(expanded = priorityExpanded, onDismissRequest = { priorityExpanded = false }) {
            for (value in 15 downTo 1) {
                DropdownMenuItem(
                    text = {
                        Text(if (value == 15) "15 (高)" else if (value == 1) "1 (低)" else "$value")
                    },
                    onClick = {
                        update { it.copy(recSetting = it.recSetting.copy(priority = value)) }
                        priorityExpanded = false
                    },
                )
            }
        }
    }
    SwitchRow("イベントリレー追従", base.tuijyuuFlag != 0) { on ->
        update { it.copy(recSetting = it.recSetting.copy(tuijyuuFlag = if (on) 1 else 0)) }
    }
    var useMargin by remember { mutableStateOf(base.useMargineFlag != 0) }
    SwitchRow("マージン個別指定", useMargin) { on -> useMargin = on }
    if (useMargin) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = (base.startMargine / 60).toString(),
                onValueChange = { value ->
                    val minutes = value.filter { it == '-' || it.isDigit() }
                        .toIntOrNull()?.coerceIn(-1440, 1440) ?: 0
                    update { it.copy(recSetting = it.recSetting.copy(startMargine = minutes * 60)) }
                },
                label = { Text("開始(分)") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.width(140.dp),
            )
            OutlinedTextField(
                value = (base.endMargine / 60).toString(),
                onValueChange = { value ->
                    val minutes = value.filter { it == '-' || it.isDigit() }
                        .toIntOrNull()?.coerceIn(-1440, 1440) ?: 0
                    update { it.copy(recSetting = it.recSetting.copy(endMargine = minutes * 60)) }
                },
                label = { Text("終了(分)") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.width(140.dp),
            )
        }
    }
    Text(
        text = "録画フォルダ: ${base.recFolderList.firstOrNull()?.recFolder?.ifBlank { "既定" } ?: "既定"}",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun NumberField(
    label: String,
    value: Int,
    max: Int,
    onValueChange: (Int) -> Unit,
) {
    OutlinedTextField(
        value = value.toString(),
        onValueChange = { text ->
            onValueChange(text.filter(Char::isDigit).toIntOrNull()?.coerceIn(0, max) ?: 0)
        },
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = Modifier.width(110.dp),
    )
}

@Composable
private fun SwitchRow(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 8.dp),
    )
}
