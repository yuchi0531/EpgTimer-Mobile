package com.starrow.epgtimer.ui.reservation

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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.starrow.epgtimer.data.model.EpgAutoAddData
import com.starrow.epgtimer.data.model.ReserveData
import com.starrow.epgtimer.data.repository.EpgRepository
import com.starrow.epgtimer.ui.SelectedContent
import com.starrow.epgtimer.ui.autoadd.AutoAddListScreen
import com.starrow.epgtimer.ui.dayHeaderLabel
import com.starrow.epgtimer.ui.errorText
import com.starrow.epgtimer.ui.overlapModeText
import com.starrow.epgtimer.ui.reserveStatusText
import com.starrow.epgtimer.ui.timeLabel
import com.starrow.epgtimer.ui.vmFactory
import java.time.LocalDate

@Composable
fun ReservationScreen(
    repository: EpgRepository,
    onOpenReserve: (ReserveData) -> Unit,
    onAddAutoAdd: () -> Unit,
    onEditAutoAdd: (EpgAutoAddData) -> Unit,
) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    Column(Modifier.fillMaxSize()) {
        TabRow(selectedTabIndex = tab) {
            Tab(
                selected = tab == 0,
                onClick = { tab = 0 },
                text = { Text("予約一覧") },
            )
            Tab(
                selected = tab == 1,
                onClick = { tab = 1 },
                text = { Text("自動予約") },
            )
        }
        when (tab) {
            0 -> ReserveList(repository = repository, onOpenReserve = onOpenReserve)
            else -> AutoAddListScreen(
                repository = repository,
                onAdd = onAddAutoAdd,
                onEdit = onEditAutoAdd,
            )
        }
    }
}

@Composable
private fun ReserveList(
    repository: EpgRepository,
    onOpenReserve: (ReserveData) -> Unit,
) {
    val viewModel: ReservationViewModel = viewModel(factory = vmFactory { ReservationViewModel(repository) })
    LaunchedEffect(Unit) { viewModel.refresh() }
    val reserves = viewModel.reserves
    val loading = viewModel.loading
    val error = viewModel.error

    Box(Modifier.fillMaxSize()) {
        when {
            reserves == null && loading -> {
                CircularProgressIndicator(Modifier.align(Alignment.Center))
            }
            reserves == null && error != null -> {
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
            reserves == null -> {
                CircularProgressIndicator(Modifier.align(Alignment.Center))
            }
            reserves.isEmpty() -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("予約がありません", style = MaterialTheme.typography.bodyLarge)
                }
            }
            else -> {
                val groups = remember(reserves) {
                    reserves
                        .mapNotNull { reserve ->
                            reserve.startTime.toLocalDateTime()?.let { Pair(it, reserve) }
                        }
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
                        items(list.size, key = { list[it].second.reserveId }) { index ->
                            val pair = list[index]
                            val reserve = pair.second
                            ReserveRow(reserve = reserve) {
                                SelectedContent.reserve = reserve
                                onOpenReserve(reserve)
                            }
                        }
                    }
                }
            }
        }
        if (loading && reserves != null) {
            CircularProgressIndicator(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(8.dp),
            )
        }
    }
}

@Composable
private fun ReserveRow(reserve: ReserveData, onClick: () -> Unit) {
    val start = reserve.startTime.toLocalDateTime()
    val overlap = overlapModeText(reserve.overlapMode)
    val status = reserveStatusText(reserve.reserveStatus)
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
                    text = reserve.title,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (reserve.stationName.isNotBlank()) {
                    Text(
                        text = reserve.stationName,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        if (overlap.isNotBlank() || status.isNotBlank()) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (overlap.isNotBlank()) {
                    Text(
                        text = overlap,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        fontWeight = FontWeight.Bold,
                    )
                }
                if (status.isNotBlank()) {
                    Text(
                        text = status,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.tertiary,
                    )
                }
            }
        }
    }
}
