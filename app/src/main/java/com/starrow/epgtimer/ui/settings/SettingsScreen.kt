package com.starrow.epgtimer.ui.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.starrow.epgtimer.data.repository.EpgRepository
import com.starrow.epgtimer.data.repository.GuidesSource
import com.starrow.epgtimer.ui.UiSettings
import com.starrow.epgtimer.ui.vmFactory

@Composable
fun SettingsScreen(
    repository: EpgRepository,
    themeMode: Int,
    onThemeModeChange: (Int) -> Unit,
    onToast: (String) -> Unit,
) {
    val viewModel: SettingsViewModel = viewModel(factory = vmFactory { SettingsViewModel(repository) })
    val context = LocalContext.current
    val config by viewModel.serverConfig.collectAsStateWithLifecycle()
    val cacheEnabled by viewModel.cacheEnabled.collectAsStateWithLifecycle()
    val guides by viewModel.guides.collectAsStateWithLifecycle()
    val guidesSource by viewModel.guidesSource.collectAsStateWithLifecycle()
    var selectedTheme by remember(themeMode) { mutableIntStateOf(themeMode) }
    var hourHeight by remember {
        mutableFloatStateOf(UiSettings.guideHourHeight(context).toFloat())
    }
    val version = remember {
        runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        }.getOrNull() ?: "-"
    }
    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) {
            viewModel.importGuides(context, uri)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        SectionTitle("サーバー")
        ServerField(
            label = "アドレス",
            value = config.host,
            onCommit = { host -> viewModel.updateServer(host, config.port) },
        )
        NumberField(
            label = "ポート",
            value = config.port,
            onCommit = { port -> viewModel.updateServer(config.host, port.coerceIn(1, 65535)) },
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Button(onClick = { viewModel.testConnection() }, enabled = !viewModel.testing) {
                if (viewModel.testing) {
                    CircularProgressIndicator(Modifier.padding(4.dp))
                } else {
                    Text("接続テスト")
                }
            }
            viewModel.testResult?.let { result ->
                Text(
                    text = result,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (result.startsWith("接続成功")) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.error
                    },
                )
            }
        }
        HorizontalDivider()
        SectionTitle("表示")
        Text("テーマ", style = MaterialTheme.typography.bodyMedium)
        Row(verticalAlignment = Alignment.CenterVertically) {
            ThemeRadio("システム", UiSettings.THEME_SYSTEM, selectedTheme) {
                selectedTheme = it
                onThemeModeChange(it)
            }
            ThemeRadio("ライト", UiSettings.THEME_LIGHT, selectedTheme) {
                selectedTheme = it
                onThemeModeChange(it)
            }
            ThemeRadio("ダーク", UiSettings.THEME_DARK, selectedTheme) {
                selectedTheme = it
                onThemeModeChange(it)
            }
        }
        Text(
            text = "番組表1時間の高さ: ${hourHeight.toInt()}dp",
            style = MaterialTheme.typography.bodyMedium,
        )
        Slider(
            value = hourHeight,
            onValueChange = { hourHeight = it },
            onValueChangeFinished = {
                UiSettings.setGuideHourHeight(context, hourHeight.toInt())
            },
            valueRange = UiSettings.MIN_GUIDE_HOUR_HEIGHT.toFloat()..UiSettings.MAX_GUIDE_HOUR_HEIGHT.toFloat(),
        )
        HorizontalDivider()
        SectionTitle("通信")
        NumberField(
            label = "接続タイムアウト(ms)",
            value = config.connectTimeoutMs,
            onCommit = { value -> viewModel.updateTimeouts(value, config.readTimeoutMs) },
        )
        NumberField(
            label = "読み取りタイムアウト(ms)",
            value = config.readTimeoutMs,
            onCommit = { value -> viewModel.updateTimeouts(config.connectTimeoutMs, value) },
        )
        HorizontalDivider()
        SectionTitle("キャッシュ")
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("EPGキャッシュ", style = MaterialTheme.typography.bodyMedium)
            Switch(
                checked = cacheEnabled,
                onCheckedChange = { viewModel.setCacheEnabled(it) },
            )
        }
        OutlinedButton(
            onClick = {
                viewModel.clearCache()
                onToast("キャッシュをクリアしました")
            },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("キャッシュをクリア")
        }
        HorizontalDivider()
        SectionTitle("カスタム番組表")
        val sourceLabel = if (guidesSource == GuidesSource.IMPORTED) "取込済みタブ" else "既定タブ"
        Text(
            text = "$sourceLabel ${guides.size}件",
            style = MaterialTheme.typography.bodyMedium,
        )
        Button(
            onClick = { importLauncher.launch(arrayOf("text/xml", "application/xml", "*/*")) },
            enabled = !viewModel.importing,
            modifier = Modifier.fillMaxWidth(),
        ) {
            if (viewModel.importing) {
                CircularProgressIndicator(Modifier.padding(4.dp))
            } else {
                Text("EpgTimerNW設定XMLを取込")
            }
        }
        viewModel.importResult?.let { result ->
            Text(
                text = if (result.useCustomEpgView) {
                    "${result.guidesCount}件のカスタム番組表を取り込みました"
                } else {
                    "取り込みました(カスタマイズ表示が無効のため既定タブを使用)"
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        viewModel.importError?.let { message ->
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
        HorizontalDivider()
        SectionTitle("アプリ")
        Row(
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("バージョン", style = MaterialTheme.typography.bodyMedium)
            Text(version, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun SectionTitle(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary,
    )
}

@Composable
private fun ThemeRadio(label: String, mode: Int, selected: Int, onSelect: (Int) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        RadioButton(selected = selected == mode, onClick = { onSelect(mode) })
        Text(label, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun ServerField(label: String, value: String, onCommit: (String) -> Unit) {
    var text by remember(value) { mutableStateOf(value) }
    var hadFocus by remember { mutableStateOf(false) }
    OutlinedTextField(
        value = text,
        onValueChange = { text = it },
        label = { Text(label) },
        singleLine = true,
        modifier = Modifier
            .fillMaxWidth()
            .onFocusChanged { state ->
                if (hadFocus && !state.isFocused && text != value) {
                    onCommit(text)
                }
                hadFocus = state.isFocused
            },
    )
}

@Composable
private fun NumberField(label: String, value: Int, onCommit: (Int) -> Unit) {
    var text by remember(value) { mutableStateOf(value.toString()) }
    var hadFocus by remember { mutableStateOf(false) }
    OutlinedTextField(
        value = text,
        onValueChange = { input -> text = input.filter { it.isDigit() }.take(7) },
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = Modifier
            .fillMaxWidth()
            .onFocusChanged { state ->
                if (hadFocus && !state.isFocused) {
                    text.toIntOrNull()?.let { parsed -> if (parsed != value) onCommit(parsed) }
                }
                hadFocus = state.isFocused
            },
    )
}
