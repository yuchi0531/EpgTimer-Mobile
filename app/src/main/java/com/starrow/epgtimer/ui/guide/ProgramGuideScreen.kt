package com.starrow.epgtimer.ui.guide

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.starrow.epgtimer.data.guide.GuideEngine
import com.starrow.epgtimer.data.model.CustomProgramGuide
import com.starrow.epgtimer.data.model.ServiceInfo
import com.starrow.epgtimer.data.repository.GuideData
import com.starrow.epgtimer.data.repository.GuideEvent
import com.starrow.epgtimer.ui.UiSettings
import com.starrow.epgtimer.ui.dateLabel
import com.starrow.epgtimer.ui.timeLabel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProgramGuideScreen(
    viewModel: GuideViewModel,
    onOpenEvent: (GuideEvent, ServiceInfo?) -> Unit,
) {
    val guides by viewModel.guides.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val hourHeightDp = remember { UiSettings.guideHourHeight(context) }
    if (guides.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("番組表がありません", style = MaterialTheme.typography.bodyLarge)
        }
        return
    }
    val tabIndex = viewModel.tabIndex.coerceIn(guides.indices)
    val guide = guides[tabIndex]
    val mode = viewModel.modeOverride ?: guide.viewMode
    val data = viewModel.guideData
    val loading = viewModel.loading
    val error = viewModel.error
    val weekStart = viewModel.weekStart
    val logos = rememberServiceLogos(viewModel.repository)

    LaunchedEffect(tabIndex, weekStart, mode, guides) {
        viewModel.load(guides[tabIndex], mode)
    }

    Column(Modifier.fillMaxSize()) {
        ScrollableTabRow(selectedTabIndex = tabIndex, edgePadding = 0.dp) {
            guides.forEachIndexed { index, item ->
                Tab(
                    selected = index == tabIndex,
                    onClick = { viewModel.selectTab(index) },
                    text = {
                        Text(item.tabName, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    },
                )
            }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 2.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = dateLabel(weekStart),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 8.dp),
            )
            IconButton(onClick = { viewModel.shiftWeek(-7) }) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "前の週")
            }
            TextButton(onClick = { viewModel.goToday() }) {
                Text("今日")
            }
            IconButton(onClick = { viewModel.shiftWeek(7) }) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "次の週")
            }
        }
        Box(
            Modifier
                .fillMaxWidth()
                .weight(1f),
        ) {
            val content: @Composable () -> Unit = when {
                data == null -> {
                    { CircularProgressIndicator(Modifier.align(Alignment.Center)) }
                }
                mode == CustomProgramGuide.VIEW_MODE_LIST -> {
                    { GuideList(data = data, onOpenEvent = onOpenEvent) }
                }
                mode == CustomProgramGuide.VIEW_MODE_WEEK -> {
                    {
                        WeekGuideContent(
                            data = data,
                            guide = guide,
                            hourHeightDp = hourHeightDp,
                            onOpenEvent = onOpenEvent,
                        )
                    }
                }
                else -> {
                    {
                        StandardGuide(
                            data = data,
                            weekStart = weekStart,
                            hourHeightDp = hourHeightDp,
                            collapse = guide.needTimeOnlyBasic,
                            logos = logos,
                            onEventClick = { event, service -> onOpenEvent(event, service) },
                        )
                    }
                }
            }
            if (error != null && data == null) {
                GuideError(
                    message = error,
                    onRetry = { viewModel.load(guide, mode) },
                )
            } else {
                content()
                if (loading && data != null) {
                    CircularProgressIndicator(
                        Modifier
                            .align(Alignment.TopCenter)
                            .padding(8.dp),
                    )
                }
                if (error != null && data != null) {
                    GuideStaleBanner(
                        message = error,
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .padding(8.dp),
                        onRetry = { viewModel.load(guide, mode) },
                    )
                }
            }
        }
    }
}

@Composable
private fun GuideStaleBanner(message: String, modifier: Modifier, onRetry: () -> Unit) {
    androidx.compose.material3.Surface(
        color = MaterialTheme.colorScheme.errorContainer,
        modifier = modifier,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onErrorContainer,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            TextButton(onClick = onRetry) { Text("再試行") }
        }
    }
}

@Composable
private fun GuideError(message: String, onRetry: () -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(24.dp)) {
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
            )
            Button(onClick = onRetry, modifier = Modifier.padding(top = 12.dp)) {
                Text("再試行")
            }
        }
    }
}

@Composable
private fun GuideList(
    data: GuideData,
    onOpenEvent: (GuideEvent, ServiceInfo?) -> Unit,
) {
    val servicesByKey = remember(data) { data.serviceGroups.flatMap { it.members }.associateBy { it.key } }
    val groups = remember(data) {
        data.eventsByService.values.flatten()
            .filter { it.event.startDateTime != null }
            .sortedBy { it.event.startDateTime }
            .groupBy { it.event.startDateTime?.toLocalDate() }
            .filterKeys { it != null }
            .mapKeys { (key, value) -> requireNotNull(key) }
            .toSortedMap()
    }
    if (groups.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("番組がありません", style = MaterialTheme.typography.bodyLarge)
        }
        return
    }
    LazyColumn(Modifier.fillMaxSize()) {
        for ((date, list) in groups) {
            item(key = "header-$date") {
                Text(
                    text = dateLabel(date),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
            items(list.size, key = { list[it].event.eventKey }) { index ->
                val guideEvent = list[index]
                val start = guideEvent.event.startDateTime
                if (start == null) return@items
                val service = servicesByKey[guideEvent.event.serviceKey]
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            onOpenEvent(guideEvent, service)
                        }
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.Top,
                ) {
                    Text(
                        text = timeLabel(start),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(end = 12.dp),
                    )
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = guideEvent.event.title,
                            style = MaterialTheme.typography.bodyLarge,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            color = MaterialTheme.colorScheme.onSurface.copy(
                                alpha = if (guideEvent.dimmed) 0.45f else 1f,
                            ),
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WeekGuideContent(
    data: GuideData,
    guide: CustomProgramGuide,
    hourHeightDp: Int,
    onOpenEvent: (GuideEvent, ServiceInfo?) -> Unit,
) {
    val engine = remember { GuideEngine() }
    val weekLayout = remember(data) {
        val events = data.eventsByService.values.flatten().map { it.event }
        val layout = engine.weekLayout(data.guide, data.weekStart, events)
        if (layout.days.isEmpty()) {
            layout.copy(days = (0..6).map { data.weekStart.plusDays(it.toLong()) })
        } else {
            layout
        }
    }
    val services = data.serviceGroups.flatMap { it.members }
    if (services.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("サービスがありません", style = MaterialTheme.typography.bodyLarge)
        }
        return
    }
    var serviceIndex by remember(data) { mutableIntStateOf(0) }
    val index = serviceIndex.coerceIn(services.indices)
    Column(Modifier.fillMaxSize()) {
        var expanded by remember { mutableStateOf(false) }
        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = { expanded = it },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 4.dp),
        ) {
            OutlinedTextField(
                value = services[index].serviceName,
                onValueChange = {},
                readOnly = true,
                singleLine = true,
                label = { Text("サービス") },
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
                services.forEachIndexed { servicePosition, service ->
                    DropdownMenuItem(
                        text = { Text(service.serviceName) },
                        onClick = {
                            serviceIndex = servicePosition
                            expanded = false
                        },
                    )
                }
            }
        }
        WeekGuide(
            data = data,
            weekLayout = weekLayout,
            hourHeightDp = hourHeightDp,
            collapse = guide.needTimeOnlyWeek,
            serviceIndex = index,
            onEventClick = { event, service -> onOpenEvent(event, service) },
        )
    }
}
