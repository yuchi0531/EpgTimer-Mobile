package com.starrow.epgtimer.data.guide

import com.starrow.epgtimer.data.model.ContentData
import com.starrow.epgtimer.data.model.ContentInfo
import com.starrow.epgtimer.data.model.CustomProgramGuide
import com.starrow.epgtimer.data.model.EpgEvent
import com.starrow.epgtimer.data.model.EdcbDateTime
import com.starrow.epgtimer.data.model.ServiceInfo
import com.starrow.epgtimer.data.model.ShortInfo
import com.starrow.epgtimer.data.repository.GuideEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime

class GuideEngineTest {

    private val engine = GuideEngine()

    @Test
    fun `special view service keys expand by onid category`() {
        val dttvLow = service(0x7880, 1, 0x101, remocon = 1)
        val dttvHigh = service(0x7FE8, 1, 0x102, remocon = 0)
        val bs = service(0x0004, 0x4000, 0x101)
        val cs1 = service(0x0006, 0x1000, 0x101)
        val cs2 = service(0x0007, 0x2000, 0x101)
        val cs3 = service(0x000A, 0x7000, 0x101)
        val otherLow = service(0x0002, 0x1000, 0x101)
        val otherBelow = service(0x787F, 0x1000, 0x101)
        val otherAbove = service(0x7FE9, 0x1000, 0x101)
        val services = listOf(dttvLow, dttvHigh, bs, cs1, cs2, cs3, otherLow, otherBelow, otherAbove)

        assertEquals(
            "ChSet5Class.cs:74-77 + EpgMainView.xaml.cs:1069-1070",
            listOf(dttvLow, dttvHigh),
            engine.expandViewServices(listOf(CustomProgramGuide.VIEW_SERVICE_DTTV), services),
        )
        assertEquals(
            "ChSet5Class.cs:78-81 + EpgMainView.xaml.cs:1071-1072",
            listOf(bs),
            engine.expandViewServices(listOf(CustomProgramGuide.VIEW_SERVICE_BS), services),
        )
        assertEquals(
            "ChSet5Class.cs:82-97 + EpgMainView.xaml.cs:1073-1074",
            listOf(cs1, cs2, cs3),
            engine.expandViewServices(listOf(CustomProgramGuide.VIEW_SERVICE_CS), services),
        )
        assertEquals(
            "ChSet5Class.cs:94-97 + EpgMainView.xaml.cs:1075-1076",
            listOf(cs3),
            engine.expandViewServices(listOf(CustomProgramGuide.VIEW_SERVICE_CS3), services),
        )
        assertEquals(
            "ChSet5Class.cs:98-101 + EpgMainView.xaml.cs:1077-1078",
            listOf(otherLow, otherBelow, otherAbove),
            engine.expandViewServices(listOf(CustomProgramGuide.VIEW_SERVICE_OTHER), services),
        )
    }

    @Test
    fun `special keys are expanded in view service list order`() {
        val dttv = service(0x7880, 1, 0x101, remocon = 1)
        val bs = service(0x0004, 0x4000, 0x101)
        val other = service(0x0002, 0x1000, 0x101)
        val services = listOf(other, bs, dttv)

        assertEquals(
            "EpgMainView.xaml.cs:1067-1094",
            listOf(bs, dttv, other),
            engine.expandViewServices(
                listOf(CustomProgramGuide.VIEW_SERVICE_BS, CustomProgramGuide.VIEW_SERVICE_DTTV, CustomProgramGuide.VIEW_SERVICE_OTHER),
                services,
            ),
        )
    }

    @Test
    fun `same transport stream services are sorted into one combined run`() {
        val tsOne104 = service(0x0004, 0x4000, 0x104)
        val tsOne101 = service(0x0004, 0x4000, 0x101)
        val tsOne102 = service(0x0004, 0x4000, 0x102)
        val tsTwo201 = service(0x0004, 0x4001, 0x201)
        val services = listOf(tsTwo201, tsOne104, tsOne101, tsOne102)

        assertEquals(
            "DBManagerClass.cs:652-667",
            listOf(tsOne101, tsOne102, tsOne104, tsTwo201),
            engine.expandViewServices(listOf(CustomProgramGuide.VIEW_SERVICE_BS), services),
        )
    }

    @Test
    fun `terrestrial services are ordered by remote control key`() {
        val tsOne103 = service(0x7880, 1, 0x103, remocon = 3)
        val tsOne101 = service(0x7880, 1, 0x101, remocon = 3)
        val tsTwo101 = service(0x7881, 2, 0x101, remocon = 1)
        val services = listOf(tsOne103, tsOne101, tsTwo101)

        assertEquals(
            "DBManagerClass.cs:662-666",
            listOf(tsTwo101, tsOne101, tsOne103),
            engine.expandViewServices(listOf(CustomProgramGuide.VIEW_SERVICE_DTTV), services),
        )
    }

    @Test
    fun `explicit service keys keep the listed order and are deduplicated`() {
        val bs = service(0x0004, 0x4000, 0x101)
        val cs3 = service(0x000A, 0x7000, 0x101)
        val services = listOf(bs, cs3)
        val unknown = 123456789L

        assertEquals(
            "EpgMainView.xaml.cs:1079-1086",
            listOf(cs3, bs),
            engine.expandViewServices(listOf(cs3.key, bs.key, bs.key, unknown, CustomProgramGuide.VIEW_SERVICE_BS), services),
        )
    }

    @Test
    fun `guide data groups events by service in start time order`() {
        val first = service(0x0004, 0x4000, 0x101)
        val second = service(0x0004, 0x4000, 0x102)
        val empty = service(0x0006, 0x1000, 0x101)
        val late = event(first, LocalDateTime.of(2024, 3, 2, 12, 0))
        val early = event(first, LocalDateTime.of(2024, 3, 2, 10, 0))
        val otherService = event(second, LocalDateTime.of(2024, 3, 2, 11, 0))
        val stray = event(service(0x0007, 0x2000, 0x101), LocalDateTime.of(2024, 3, 2, 13, 0))
        val undetermined = event(first, null)
        val weekStart = LocalDate.of(2024, 3, 3)
        val guide = CustomProgramGuide.default("tab", emptyList())

        val data = engine.buildGuideData(guide, weekStart, listOf(first, second, empty), listOf(late, undetermined, otherService, early, stray))

        assertEquals(guide, data.guide)
        assertEquals(weekStart, data.weekStart)
        assertEquals(listOf(first, second, empty), data.services)
        assertEquals(
            listOf(early, late),
            data.eventsByService.getValue(first.key).map { it.event },
        )
        assertEquals(
            listOf(otherService),
            data.eventsByService.getValue(second.key).map { it.event },
        )
        assertEquals(emptyList<GuideEvent>(), data.eventsByService.getValue(empty.key))
        assertFalse(data.eventsByService.containsKey(stray.serviceKey))
        assertTrue(data.eventsByService.getValue(first.key).none { it.dimmed })
    }

    @Test
    fun `content kind filter dims or drops unmatched programs`() {
        val svc = service(0x0004, 0x4000, 0x101)
        val matched = event(svc, LocalDateTime.of(2024, 3, 2, 10, 0), content = content(nibble(1, 4)))
        val unmatched = event(svc, LocalDateTime.of(2024, 3, 2, 11, 0), content = content(nibble(1, 5)))
        val noContent = event(svc, LocalDateTime.of(2024, 3, 2, 12, 0))
        val weekStart = LocalDate.of(2024, 3, 3)

        val highlight = CustomProgramGuide.default("tab", emptyList())
            .copy(viewContentKindList = listOf(0x0104), highlightContentKind = true)
        val dimmed = engine.buildGuideData(highlight, weekStart, listOf(svc), listOf(matched, unmatched, noContent))
        assertEquals(listOf(false, true, true), dimmed.eventsByService.getValue(svc.key).map { it.dimmed })

        val excluding = highlight.copy(highlightContentKind = false)
        val excluded = engine.buildGuideData(excluding, weekStart, listOf(svc), listOf(matched, unmatched, noContent))
        assertEquals(listOf(matched), excluded.eventsByService.getValue(svc.key).map { it.event })

        val wholeGenre = highlight.copy(viewContentKindList = listOf(0x01FF))
        assertEquals(
            "EpgMainView.xaml.cs:1179-1184",
            listOf(false, false, true),
            engine.buildGuideData(wholeGenre, weekStart, listOf(svc), listOf(matched, unmatched, noContent))
                .eventsByService.getValue(svc.key).map { it.dimmed },
        )

        val keepNoGenre = highlight.copy(viewContentKindList = listOf(0xFFFF, 0x0104))
        assertEquals(
            "EpgMainView.xaml.cs:1162-1166",
            listOf(false, true, false),
            engine.buildGuideData(keepNoGenre, weekStart, listOf(svc), listOf(matched, unmatched, noContent))
                .eventsByService.getValue(svc.key).map { it.dimmed },
        )
    }

    @Test
    fun `extended genre uses user nibble conversion`() {
        val svc = service(0x0004, 0x4000, 0x101)
        val levelZero = event(svc, LocalDateTime.of(2024, 3, 2, 10, 0), content = content(nibble(0x0E, 0x00, 0x13, 0x07)))
        val levelOne = event(svc, LocalDateTime.of(2024, 3, 2, 11, 0), content = content(nibble(0x0E, 0x01, 0x05, 0x03)))
        val weekStart = LocalDate.of(2024, 3, 3)
        val guide = CustomProgramGuide.default("tab", emptyList())

        val converted = guide.copy(viewContentKindList = listOf(0x7307, 0x7503), highlightContentKind = true)
        assertEquals(
            "EpgMainView.xaml.cs:1174-1178",
            listOf(false, false),
            engine.buildGuideData(converted, weekStart, listOf(svc), listOf(levelZero, levelOne))
                .eventsByService.getValue(svc.key).map { it.dimmed },
        )

        val raw = guide.copy(viewContentKindList = listOf(0x0E00, 0x0E01), highlightContentKind = true)
        assertEquals(
            "EpgMainView.xaml.cs:1174-1178",
            listOf(true, true),
            engine.buildGuideData(raw, weekStart, listOf(svc), listOf(levelZero, levelOne))
                .eventsByService.getValue(svc.key).map { it.dimmed },
        )
    }

    @Test
    fun `any matching nibble of an event wins`() {
        val svc = service(0x0004, 0x4000, 0x101)
        val twoNibbles = event(
            svc,
            LocalDateTime.of(2024, 3, 2, 10, 0),
            content = content(nibble(2, 0), nibble(1, 4)),
        )
        val weekStart = LocalDate.of(2024, 3, 3)
        val guide = CustomProgramGuide.default("tab", emptyList()).copy(viewContentKindList = listOf(0x0104))

        assertEquals(
            listOf(false),
            engine.buildGuideData(guide, weekStart, listOf(svc), listOf(twoNibbles))
                .eventsByService.getValue(svc.key).map { it.dimmed },
        )
    }

    @Test
    fun `filter ended applies to list view only`() {
        val svc = service(0x0004, 0x4000, 0x101)
        val past = event(svc, LocalDateTime.of(2000, 1, 1, 10, 0))
        val future = event(svc, LocalDateTime.of(2099, 1, 1, 10, 0))
        val pastUndeterminedDuration = event(svc, LocalDateTime.of(2000, 1, 1, 12, 0), durationFlag = 0)
        val weekStart = LocalDate.of(2024, 3, 3)
        val base = CustomProgramGuide.default("tab", emptyList())

        val listFiltered = base.copy(viewMode = CustomProgramGuide.VIEW_MODE_LIST, filterEnded = true)
        assertEquals(
            "EpgListMainView.xaml.cs:356-360",
            listOf(future),
            engine.buildGuideData(listFiltered, weekStart, listOf(svc), listOf(past, pastUndeterminedDuration, future))
                .eventsByService.getValue(svc.key).map { it.event },
        )

        val listPlain = listFiltered.copy(filterEnded = false)
        assertEquals(
            3,
            engine.buildGuideData(listPlain, weekStart, listOf(svc), listOf(past, pastUndeterminedDuration, future))
                .eventsByService.getValue(svc.key).size,
        )

        val standardFiltered = base.copy(viewMode = CustomProgramGuide.VIEW_MODE_STANDARD, filterEnded = true)
        assertEquals(
            3,
            engine.buildGuideData(standardFiltered, weekStart, listOf(svc), listOf(past, pastUndeterminedDuration, future))
                .eventsByService.getValue(svc.key).size,
        )

        val weekFiltered = base.copy(viewMode = CustomProgramGuide.VIEW_MODE_WEEK, filterEnded = true)
        assertEquals(
            3,
            engine.buildGuideData(weekFiltered, weekStart, listOf(svc), listOf(past, pastUndeterminedDuration, future))
                .eventsByService.getValue(svc.key).size,
        )
    }

    @Test
    fun `ended check uses the duration flag`() {
        val svc = service(0x0004, 0x4000, 0x101)
        val start = LocalDateTime.of(2024, 3, 2, 10, 0)
        val fixed = event(svc, start, durationSeconds = 1800)
        val undetermined = event(svc, start, durationFlag = 0)
        val unknownStart = event(svc, null)

        assertEquals("EpgListMainView.xaml.cs:357-359", false, engine.isEnded(fixed, start.plusMinutes(30)))
        assertTrue(engine.isEnded(fixed, start.plusSeconds(1).plusMinutes(30)))
        assertTrue(engine.isEnded(undetermined, start.plusSeconds(1)))
        assertFalse(engine.isEnded(undetermined, start))
        assertFalse(engine.isEnded(unknownStart, LocalDateTime.of(2099, 1, 1, 0, 0)))
    }

    @Test
    fun `event day column folds hours below the start time`() {
        val svc = service(0x0004, 0x4000, 0x101)
        val before = event(svc, LocalDateTime.of(2024, 3, 3, 3, 59))
        val atBorder = event(svc, LocalDateTime.of(2024, 3, 3, 4, 0))
        val later = event(svc, LocalDateTime.of(2024, 3, 3, 20, 0))
        val monthEdge = event(svc, LocalDateTime.of(2024, 3, 1, 3, 0))

        assertEquals("EpgWeekMainView.xaml.cs:1086-1095", LocalDate.of(2024, 3, 2), engine.eventDayColumn(before, 4))
        assertEquals(LocalDate.of(2024, 3, 3), engine.eventDayColumn(atBorder, 4))
        assertEquals(LocalDate.of(2024, 3, 3), engine.eventDayColumn(later, 4))
        assertEquals(LocalDate.of(2024, 2, 29), engine.eventDayColumn(monthEdge, 4))
        assertEquals(LocalDate.of(2024, 3, 3), engine.eventDayColumn(before, 0))
        assertEquals(LocalDate.of(2024, 3, 2), engine.eventDayColumn(atBorder, 5))
    }

    @Test
    fun `week layout lists the days covered by the fetched programs`() {
        val svc = service(0x0004, 0x4000, 0x101)
        val weekStart = LocalDate.of(2024, 3, 3)
        val guide = CustomProgramGuide.default("tab", emptyList()).copy(startTimeWeek = 4)
        val events = listOf(
            event(svc, LocalDateTime.of(2024, 3, 1, 12, 0)),
            event(svc, LocalDateTime.of(2024, 3, 3, 3, 0)),
            event(svc, LocalDateTime.of(2024, 3, 3, 5, 0)),
            event(svc, LocalDateTime.of(2024, 3, 3, 20, 0)),
            event(svc, LocalDateTime.of(2024, 3, 5, 10, 0)),
            event(svc, LocalDateTime.of(2024, 3, 7, 10, 0)),
            event(svc, LocalDateTime.of(2024, 3, 9, 10, 0)),
            event(svc, LocalDateTime.of(2024, 3, 11, 10, 0)),
            event(svc, null),
        )

        val layout = engine.weekLayout(guide, weekStart, events)

        assertEquals(4, layout.startHour)
        assertEquals(
            "EpgWeekMainView.xaml.cs:1040-1044 + 1086-1101 / DBManagerClass.cs:456,462",
            listOf(
                LocalDate.of(2024, 3, 2),
                LocalDate.of(2024, 3, 3),
                LocalDate.of(2024, 3, 5),
                LocalDate.of(2024, 3, 7),
                LocalDate.of(2024, 3, 9),
                LocalDate.of(2024, 3, 11),
            ),
            layout.days,
        )
    }

    @Test
    fun `week layout lists seven days for a past week window`() {
        val svc = service(0x0004, 0x4000, 0x101)
        val weekStart = LocalDate.of(2024, 3, 3)
        val guide = CustomProgramGuide.default("tab", emptyList())
        val events = (0..6).map { day ->
            event(svc, weekStart.plusDays(day.toLong()).atTime(10, 0))
        }

        assertEquals(
            "DBManagerClass.cs:456 + EpgWeekMainView.xaml.cs:1098-1101",
            (0..6).map { weekStart.plusDays(it.toLong()) },
            engine.weekLayout(guide, weekStart, events).days,
        )
    }

    @Test
    fun `event base time is the sunday at least six days ago`() {
        val sunday = engine.eventBaseTime(LocalDateTime.of(2024, 1, 7, 10, 0), pastAvailable = false)
        val monday = engine.eventBaseTime(LocalDateTime.of(2024, 1, 8, 10, 0), pastAvailable = false)
        val saturday = engine.eventBaseTime(LocalDateTime.of(2024, 1, 6, 10, 0), pastAvailable = false)
        val wednesday = engine.eventBaseTime(LocalDateTime.of(2024, 1, 10, 15, 30), pastAvailable = false)

        assertEquals("DBManagerClass.cs:351-352", LocalDate.of(2023, 12, 31).atStartOfDay(), sunday)
        assertEquals(LocalDate.of(2023, 12, 31).atStartOfDay(), monday)
        assertEquals(LocalDate.of(2023, 12, 31).atStartOfDay(), saturday)
        assertEquals(LocalDate.of(2023, 12, 31).atStartOfDay(), wednesday)
        assertEquals(DayOfWeek.SUNDAY, wednesday.dayOfWeek)
    }

    @Test
    fun `event base time shrinks to two days when past programs are available`() {
        val wednesday = engine.eventBaseTime(LocalDateTime.of(2024, 1, 10, 15, 30), pastAvailable = true)
        val monday = engine.eventBaseTime(LocalDateTime.of(2024, 1, 8, 10, 0), pastAvailable = true)
        val sunday = engine.eventBaseTime(LocalDateTime.of(2024, 1, 7, 10, 0), pastAvailable = true)

        assertEquals("DBManagerClass.cs:374-375", LocalDate.of(2024, 1, 7).atStartOfDay(), wednesday)
        assertEquals(LocalDate.of(2023, 12, 31).atStartOfDay(), monday)
        assertEquals(LocalDate.of(2023, 12, 31).atStartOfDay(), sunday)
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

    private fun event(
        service: ServiceInfo,
        start: LocalDateTime?,
        durationSeconds: Int = 1800,
        durationFlag: Int = 1,
        content: ContentInfo? = null,
    ): EpgEvent = EpgEvent(
        onid = service.onid,
        tsid = service.tsid,
        sid = service.sid,
        eventId = 1,
        startTimeFlag = if (start == null) 0 else 1,
        startTime = start?.let { EdcbDateTime.from(it) } ?: EdcbDateTime.invalid(),
        durationFlag = durationFlag,
        durationSeconds = durationSeconds,
        freeCaFlag = 0,
        shortInfo = ShortInfo(eventName = "title", text = ""),
        extInfo = null,
        contentInfo = content,
        componentInfo = null,
        audioInfo = null,
        eventGroupInfo = null,
        eventRelayInfo = null,
    )

    private fun content(vararg nibbles: ContentData): ContentInfo = ContentInfo(nibbleList = nibbles.toList())

    private fun nibble(level1: Int, level2: Int, user1: Int = 0, user2: Int = 0): ContentData =
        ContentData(nibbleLevel1 = level1, nibbleLevel2 = level2, userNibble1 = user1, userNibble2 = user2)
}
