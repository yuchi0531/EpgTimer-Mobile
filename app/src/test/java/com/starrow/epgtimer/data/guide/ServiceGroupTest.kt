package com.starrow.epgtimer.data.guide

import com.starrow.epgtimer.data.model.CustomProgramGuide
import com.starrow.epgtimer.data.model.EdcbDateTime
import com.starrow.epgtimer.data.model.EpgEvent
import com.starrow.epgtimer.data.model.ServiceInfo
import com.starrow.epgtimer.data.model.ShortInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime

class ServiceGroupTest {

    @Test
    fun `ascending services of one transport stream are not merged`() {
        val services = listOf(
            service(0x0004, 0x4000, 0x101),
            service(0x0004, 0x4000, 0x102),
            service(0x0004, 0x4000, 0x103),
        )

        val groups = ServiceGroup.listing(services)

        assertEquals("EpgMainView.xaml.cs:1106-1121", 3, groups.size)
        groups.forEachIndexed { index, group ->
            assertEquals(1, group.span)
            assertFalse(group.isMerged)
            assertEquals(services[index], group.primary)
            assertEquals(listOf(services[index]), group.members)
        }
    }

    @Test
    fun `descending services of one transport stream merge and the lowest sid represents them`() {
        val three = service(0x0004, 0x4000, 0x103)
        val two = service(0x0004, 0x4000, 0x102)
        val one = service(0x0004, 0x4000, 0x101)

        val groups = ServiceGroup.listing(listOf(three, two, one))

        assertEquals(1, groups.size)
        assertEquals(3, groups[0].span)
        assertTrue(groups[0].isMerged)
        assertEquals("EpgMainView.xaml.cs:1144 primeServiceList.Add(serviceList[mergePos])", one, groups[0].primary)
        assertEquals(listOf(three, two, one), groups[0].members)
    }

    @Test
    fun `two descending services merge into a span of two`() {
        val high = service(0x0004, 0x4000, 0x102)
        val low = service(0x0004, 0x4000, 0x101)

        val groups = ServiceGroup.listing(listOf(high, low))

        assertEquals(1, groups.size)
        assertEquals(2, groups[0].span)
        assertEquals(low, groups[0].primary)
        assertEquals(listOf(high, low), groups[0].members)
    }

    @Test
    fun `descending runs of different transport streams stay separate`() {
        val services = listOf(
            service(0x0004, 0x4000, 0x103),
            service(0x0004, 0x4000, 0x102),
            service(0x0004, 0x4000, 0x101),
            service(0x0004, 0x4001, 0x204),
            service(0x0004, 0x4001, 0x203),
        )

        val groups = ServiceGroup.listing(services)

        assertEquals(2, groups.size)
        assertEquals(3, groups[0].span)
        assertEquals(services[2], groups[0].primary)
        assertEquals(2, groups[1].span)
        assertEquals(services[4], groups[1].primary)
    }

    @Test
    fun `neighbours with a different tsid are never merged`() {
        val services = listOf(
            service(0x0004, 0x4000, 0x102),
            service(0x0004, 0x4001, 0x101),
        )

        assertEquals(2, ServiceGroup.listing(services).size)
    }

    @Test
    fun `neighbours with a different onid are never merged`() {
        val services = listOf(
            service(0x0004, 0x4000, 0x102),
            service(0x0006, 0x4000, 0x101),
        )

        assertEquals(2, ServiceGroup.listing(services).size)
    }

    @Test
    fun `a single service and an empty list are handled`() {
        val only = service(0x7880, 1, 0x101)

        val single = ServiceGroup.listing(listOf(only))
        assertEquals(1, single.size)
        assertEquals(1, single[0].span)
        assertEquals(only, single[0].primary)
        assertEquals(emptyList<ServiceGroup>(), ServiceGroup.listing(emptyList()))
    }

    @Test
    fun `only the descending head of a transport stream merges`() {
        val two = service(0x0004, 0x4000, 0x102)
        val one = service(0x0004, 0x4000, 0x101)
        val three = service(0x0004, 0x4000, 0x103)

        val groups = ServiceGroup.listing(listOf(two, one, three))

        assertEquals(2, groups.size)
        assertEquals(2, groups[0].span)
        assertEquals(one, groups[0].primary)
        assertEquals(1, groups[1].span)
        assertEquals(three, groups[1].primary)
    }

    @Test
    fun `build guide data collects every member program under the group primary key`() {
        val three = service(0x0004, 0x4000, 0x103)
        val two = service(0x0004, 0x4000, 0x102)
        val one = service(0x0004, 0x4000, 0x101)
        val weekStart = LocalDate.of(2024, 3, 3)
        val guide = CustomProgramGuide.default("tab", emptyList())
        val engine = GuideEngine()
        val third = event(three, LocalDateTime.of(2024, 3, 3, 10, 0), eventId = 3)
        val second = event(two, LocalDateTime.of(2024, 3, 3, 11, 0), eventId = 2)
        val first = event(one, LocalDateTime.of(2024, 3, 3, 12, 0), eventId = 1)

        val data = engine.buildGuideData(guide, weekStart, listOf(three, two, one), listOf(third, second, first))

        assertEquals(listOf(one), data.services)
        assertEquals(1, data.serviceGroups.size)
        assertEquals(3, data.serviceGroups[0].span)
        assertEquals(one, data.serviceGroups[0].primary)
        assertEquals(
            listOf(third, second, first),
            data.eventsByService.getValue(one.key).map { it.event },
        )
        assertFalse(data.eventsByService.containsKey(two.key))
        assertFalse(data.eventsByService.containsKey(three.key))
    }

    @Test
    fun `build guide data keeps unmerged services under their own key`() {
        val first = service(0x0004, 0x4000, 0x101)
        val second = service(0x0004, 0x4000, 0x102)
        val weekStart = LocalDate.of(2024, 3, 3)
        val engine = GuideEngine()
        val guide = CustomProgramGuide.default("tab", emptyList())
        val firstEvent = event(first, LocalDateTime.of(2024, 3, 3, 10, 0), eventId = 1)
        val secondEvent = event(second, LocalDateTime.of(2024, 3, 3, 11, 0), eventId = 2)

        val data = engine.buildGuideData(guide, weekStart, listOf(first, second), listOf(firstEvent, secondEvent))

        assertEquals(listOf(first, second), data.services)
        assertEquals(2, data.serviceGroups.size)
        assertEquals(listOf(firstEvent), data.eventsByService.getValue(first.key).map { it.event })
        assertEquals(listOf(secondEvent), data.eventsByService.getValue(second.key).map { it.event })
    }

    private fun service(onid: Int, tsid: Int, sid: Int, remocon: Int = 0): ServiceInfo = ServiceInfo(
        onid = onid,
        tsid = tsid,
        sid = sid,
        serviceType = 1,
        partialReceptionFlag = 0,
        serviceProviderName = "sp",
        serviceName = "svc-$onid-$sid",
        networkName = "net",
        tsName = "ts-$tsid",
        remoteControlKeyId = remocon,
    )

    private fun event(service: ServiceInfo, start: LocalDateTime, eventId: Int): EpgEvent = EpgEvent(
        onid = service.onid,
        tsid = service.tsid,
        sid = service.sid,
        eventId = eventId,
        startTimeFlag = 1,
        startTime = EdcbDateTime.from(start),
        durationFlag = 1,
        durationSeconds = 1800,
        freeCaFlag = 0,
        shortInfo = ShortInfo(eventName = "title-$eventId", text = ""),
        extInfo = null,
        contentInfo = null,
        componentInfo = null,
        audioInfo = null,
        eventGroupInfo = null,
        eventRelayInfo = null,
    )
}
