package com.starrow.epgtimer.data.guide

import com.starrow.epgtimer.data.model.CustomProgramGuide
import com.starrow.epgtimer.data.model.EpgEvent
import com.starrow.epgtimer.data.model.ServiceInfo
import com.starrow.epgtimer.data.model.ServiceKey
import com.starrow.epgtimer.data.repository.GuideData
import com.starrow.epgtimer.data.repository.GuideEvent
import com.starrow.epgtimer.util.EpgClock
import java.time.LocalDate
import java.time.LocalDateTime

class GuideEngine {

    fun expandViewServices(viewServiceList: List<Long>, services: List<ServiceInfo>): List<ServiceInfo> {
        val result = LinkedHashMap<Long, ServiceInfo>()
        for (id in viewServiceList) {
            val selected = when (id) {
                CustomProgramGuide.VIEW_SERVICE_DTTV -> services.filter { isDttv(it.onid) }
                CustomProgramGuide.VIEW_SERVICE_BS -> services.filter { isBS(it.onid) }
                CustomProgramGuide.VIEW_SERVICE_CS -> services.filter { isCS(it.onid) }
                CustomProgramGuide.VIEW_SERVICE_CS3 -> services.filter { isCS3(it.onid) }
                CustomProgramGuide.VIEW_SERVICE_OTHER -> services.filter { isOther(it.onid) }
                else -> null
            }
            if (selected == null) {
                val service = services.firstOrNull { it.key == id } ?: continue
                if (!result.containsKey(service.key)) {
                    result[service.key] = service
                }
            } else {
                for (service in selectServiceEventList(selected)) {
                    if (!result.containsKey(service.key)) {
                        result[service.key] = service
                    }
                }
            }
        }
        return result.values.toList()
    }

    fun buildGuideData(
        guide: CustomProgramGuide,
        weekStart: LocalDate,
        expandedServices: List<ServiceInfo>,
        events: List<EpgEvent>,
    ): GuideData {
        val contentKinds = guide.viewContentKindList.sorted()
        val now = EpgClock.now()
        val grouped = LinkedHashMap<Long, MutableList<GuideEvent>>()
        for (service in expandedServices) {
            if (!grouped.containsKey(service.key)) {
                grouped[service.key] = mutableListOf()
            }
        }
        for (event in events) {
            if (event.startDateTime == null) {
                continue
            }
            val target = grouped[event.serviceKey] ?: continue
            var dimmed = false
            if (contentKinds.isNotEmpty() && isGenreFiltered(event, contentKinds)) {
                if (!guide.highlightContentKind) {
                    continue
                }
                dimmed = true
            }
            if (guide.viewMode == CustomProgramGuide.VIEW_MODE_LIST && guide.filterEnded && isEnded(event, now)) {
                continue
            }
            target.add(GuideEvent(event = event, dimmed = dimmed))
        }
        val eventsByService = grouped.mapValues { (_, list) -> list.sortedBy { it.event.startDateTime } }
        return GuideData(
            guide = guide,
            weekStart = weekStart,
            services = expandedServices,
            eventsByService = eventsByService,
        )
    }

    fun isEnded(event: EpgEvent, now: LocalDateTime): Boolean {
        val start = event.startDateTime ?: return false
        val end = if (event.durationFlag == 0) start else start.plusSeconds(event.durationSeconds.toLong())
        return end.isBefore(now)
    }

    fun eventBaseTime(now: LocalDateTime, pastAvailable: Boolean): LocalDateTime {
        val base = now.minusDays(if (pastAvailable) 2L else 6L)
        return base.minusDays((base.dayOfWeek.value % 7).toLong()).toLocalDate().atStartOfDay()
    }

    fun eventDayColumn(event: EpgEvent, startTimeWeek: Int): LocalDate {
        val start = event.startTime.toLocalDateTime() ?: return LocalDate.of(1, 1, 1)
        return if (start.hour < startTimeWeek) start.toLocalDate().minusDays(1) else start.toLocalDate()
    }

    fun weekLayout(guide: CustomProgramGuide, weekStart: LocalDate, events: List<EpgEvent>): WeekLayout {
        val from = weekStart.atStartOfDay()
        val days = events
            .filter { event -> event.startDateTime?.let { !it.isBefore(from) } == true }
            .map { eventDayColumn(it, guide.startTimeWeek) }
            .distinct()
            .sorted()
        return WeekLayout(days = days, startHour = guide.startTimeWeek)
    }

    private fun isGenreFiltered(event: EpgEvent, sortedContentKinds: List<Int>): Boolean {
        val nibbleList = event.contentInfo?.nibbleList
        if (nibbleList.isNullOrEmpty()) {
            return sortedContentKinds.binarySearch(0xFFFF) < 0
        }
        for (content in nibbleList) {
            var nibble1 = content.nibbleLevel1
            var nibble2 = content.nibbleLevel2
            if (nibble1 == 0x0E && nibble2 <= 0x01) {
                nibble1 = content.userNibble1 or (0x60 + nibble2 * 16)
                nibble2 = content.userNibble2
            }
            val wholeGenre = ((nibble1 shl 8) or 0xFF) and 0xFFFF
            val exact = ((nibble1 shl 8) or nibble2) and 0xFFFF
            if (sortedContentKinds.binarySearch(wholeGenre) >= 0 || sortedContentKinds.binarySearch(exact) >= 0) {
                return false
            }
        }
        return true
    }

    private fun selectServiceEventList(services: List<ServiceInfo>): List<ServiceInfo> {
        val bsMinSid = HashMap<Int, Int>()
        for (service in services) {
            if (isBS(service.onid)) {
                val current = bsMinSid[service.tsid]
                if (current == null || current > service.sid) {
                    bsMinSid[service.tsid] = service.sid
                }
            }
        }
        return services.sortedWith(compareBy<ServiceInfo> { serviceKey(service = it, bsMinSid = bsMinSid) })
    }

    private fun serviceKey(service: ServiceInfo, bsMinSid: Map<Int, Int>): Long {
        val remocon = if (isDttv(service.onid)) (service.remoteControlKeyId + 255) % 256 else 0
        val tsid = if (isBS(service.onid)) bsMinSid[service.tsid] ?: service.tsid else service.tsid
        return (remocon.toLong() shl 48) or ServiceKey.create(service.onid, tsid, service.sid)
    }

    private fun isDttv(onid: Int): Boolean = 0x7880 <= onid && onid <= 0x7FE8

    private fun isBS(onid: Int): Boolean = onid == 0x0004

    private fun isCS1(onid: Int): Boolean = onid == 0x0006

    private fun isCS2(onid: Int): Boolean = onid == 0x0007

    private fun isCS3(onid: Int): Boolean = onid == 0x000A

    private fun isCS(onid: Int): Boolean = isCS1(onid) || isCS2(onid) || isCS3(onid)

    private fun isOther(onid: Int): Boolean = !isDttv(onid) && !isBS(onid) && !isCS(onid)
}
