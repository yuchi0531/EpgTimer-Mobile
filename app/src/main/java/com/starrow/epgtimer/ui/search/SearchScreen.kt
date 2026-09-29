package com.starrow.epgtimer.ui.search

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.starrow.epgtimer.data.model.EpgEvent
import com.starrow.epgtimer.data.repository.EpgRepository
import com.starrow.epgtimer.ui.SelectedContent
import com.starrow.epgtimer.ui.LEVEL1_GENRES
import com.starrow.epgtimer.ui.timeLabel
import com.starrow.epgtimer.ui.vmFactory

private val SEARCH_HINT = "\u30AD\u30FC\u30EF\u30FC\u30C9\u30FB\u5E7F\u64AD\u5C40\u30FB\u30B8\u30E3\u30F3\u30EB\u306e\u3044\u305A\u308C\u3092\u6307\u5B9A\u3057\u3066\u304F\u3060\u3055\u3044"

private val PERIOD_LABELS = listOf("指定なし", "本日", "1週間", "2週間", "1ヶ月")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    repository: EpgRepository,
    onOpenEvent: (EpgEvent) -> Unit,
    onRegisterAutoAdd: (com.starrow.epgtimer.data.model.SearchCondition) -> Unit,
) {
    val viewModel: SearchViewModel = viewModel(factory = vmFactory { SearchViewModel(repository) })
    val services = viewModel.services
    var keyword by remember { mutableStateOf("") }
    var serviceIndex by remember { mutableIntStateOf(0) }
    var genreIndex by remember { mutableIntStateOf(0) }
    var periodIndex by remember { mutableIntStateOf(0) }

    Column(Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedTextField(
                value = keyword,
                onValueChange = { keyword = it },
                label = { Text("キーワード") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            DropdownField(
                label = "放送局",
                value = if (serviceIndex == 0) "すべて" else services?.getOrNull(serviceIndex - 1)?.serviceName ?: "すべて",
                options = listOf("すべて") + services.orEmpty().map { it.serviceName },
                selectedIndex = serviceIndex,
                onSelect = { serviceIndex = it },
            )
            DropdownField(
                label = "ジャンル",
                value = if (genreIndex == 0) "すべて" else LEVEL1_GENRES[genreIndex - 1].second,
                options = listOf("すべて") + LEVEL1_GENRES.map { it.second },
                selectedIndex = genreIndex,
                onSelect = { genreIndex = it },
            )
            DropdownField(
                label = "期間",
                value = PERIOD_LABELS[periodIndex],
                options = PERIOD_LABELS,
                selectedIndex = periodIndex,
                onSelect = { periodIndex = it },
            )
            if (viewModel.servicesError != null) {
                Text(
                    text = "放送局一覧を取得できません(${viewModel.servicesError})",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            val currentGenreLevel1 = if (genreIndex == 0) null else LEVEL1_GENRES[genreIndex - 1].first
            val currentService = if (serviceIndex == 0) null else services?.getOrNull(serviceIndex - 1)
            val hasCondition = keyword.isNotBlank() || currentGenreLevel1 != null || currentService != null
            if (!hasCondition) {
                Text(
                    text = SEARCH_HINT,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
            ) {
                androidx.compose.material3.TextButton(
                    onClick = {
                        onRegisterAutoAdd(viewModel.buildCondition(keyword, currentGenreLevel1, currentService))
                    },
                    enabled = hasCondition,
                ) {
                    Text("この条件で自動予約")
                }
                Button(
                    onClick = {
                        viewModel.search(keyword, currentGenreLevel1, currentService, periodIndex)
                    },
                    enabled = !viewModel.searching && hasCondition,
                ) {
                    Text("検索")
                }
            }
        }
        Box(
            Modifier
                .fillMaxWidth()
                .weight(1f),
        ) {
            val results = viewModel.results
            val searchError = viewModel.searchError
            when {
                viewModel.searching -> {
                    CircularProgressIndicator(Modifier.align(Alignment.Center))
                }
                searchError != null -> {
                    Text(
                        text = searchError,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(24.dp),
                    )
                }
                results == null -> {
                    Text(
                        text = "条件を指定して検索してください",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(24.dp),
                    )
                }
                results.isEmpty() -> {
                    Text(
                        text = "該当する番組がありません",
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(24.dp),
                    )
                }
                else -> {
                    val servicesByKey = remember(services) { services.orEmpty().associateBy { it.key } }
                    LazyColumn(Modifier.fillMaxSize()) {
                        items(results.size, key = { results[it].eventKey }) { index ->
                            val event = results[index]
                            val start = event.startDateTime
                            val dateText = if (start == null) "--/-- --:--" else {
                                "${start.monthValue}/${start.dayOfMonth} ${timeLabel(start)}"
                            }
                            val service = servicesByKey[event.serviceKey]
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        SelectedContent.event = event
                                        SelectedContent.service = service
                                        onOpenEvent(event)
                                    }
                                    .padding(horizontal = 16.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.Top,
                            ) {
                                Text(
                                    text = dateText,
                                    style = MaterialTheme.typography.bodyMedium,
                                    modifier = Modifier.padding(end = 12.dp),
                                )
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        text = event.title,
                                        style = MaterialTheme.typography.bodyLarge,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    if (service != null) {
                                        Text(
                                            text = service.serviceName,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DropdownField(
    label: String,
    value: String,
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
    ) {
        OutlinedTextField(
            value = value,
            onValueChange = {},
            readOnly = true,
            singleLine = true,
            label = { Text(label) },
            trailingIcon = {
                ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded)
            },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(MenuAnchorType.PrimaryNotEditable),
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            options.forEachIndexed { index, option ->
                DropdownMenuItem(
                    text = { Text(option, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    onClick = {
                        onSelect(index)
                        expanded = false
                    },
                    leadingIcon = if (index == selectedIndex) {
                        {
                            Text("✓", style = MaterialTheme.typography.bodyMedium)
                        }
                    } else {
                        null
                    },
                )
            }
        }
    }
}
