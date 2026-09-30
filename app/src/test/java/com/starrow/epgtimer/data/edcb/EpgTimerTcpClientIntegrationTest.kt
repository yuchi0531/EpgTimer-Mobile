package com.starrow.epgtimer.data.edcb

import com.starrow.epgtimer.data.model.SearchCondition
import com.starrow.epgtimer.data.model.ServiceKey
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import java.time.LocalDateTime

class EpgTimerTcpClientIntegrationTest {

    private val host: String? = System.getenv("EDCB_TEST_HOST")
    private val port: Int = (System.getenv("EDCB_TEST_PORT") ?: "4510").toInt()

    private lateinit var client: EpgTimerTcpClient

    @Before
    fun setUp() {
        assumeTrue("EDCB_TEST_HOST is not set", host != null && host!!.isNotBlank())
        client = EpgTimerTcpClient(host!!, port, 5000, 30000)
    }

    @Test
    fun getStatusFromServer() {
        val status = runBlocking { client.getStatus() }
        assertEquals(100, status.notifyId)
        assertTrue("srvStatus=${status.srvStatus}", status.srvStatus in 0..2)
        assertNotNull(status.time.toLocalDateTime())
        println("getStatus: notifyId=${status.notifyId} srvStatus=${status.srvStatus} (${status.srvStatusText}) time=${status.time}")
    }

    @Test
    fun enumServiceFromServer() {
        val services = runBlocking { client.enumService() }
        assertTrue("services=${services.size}", services.isNotEmpty())
        assertTrue(services.any { it.serviceName.isNotBlank() })
        println("enumService: ${services.size} services")
        services.take(5).forEach {
            println("  ${it.key.toString(16)} ${it.serviceName} rkc=${it.remoteControlKeyId}")
        }
    }

    @Test
    fun enumReserveFromServer() {
        val reserves = runBlocking { client.enumReserve() }
        assertTrue(reserves.all { it.startTime.toLocalDateTime() != null })
        println("enumReserve: ${reserves.size} reserves")
        reserves.take(3).forEach { println("  id=${it.reserveId} ${it.title}") }
    }

    @Test
    fun enumRecFileFromServer() {
        val files = runBlocking { client.enumRecFile() }
        assertTrue(files.all { it.startTime.toLocalDateTime() != null })
        println("enumRecFile: ${files.size} files")
        files.take(3).forEach { println("  id=${it.id} drops=${it.drops} ${it.title}") }
    }

    @Test
    fun enumPgInfoExInRange() {
        val start = LocalDateTime.now().minusHours(6)
        val end = LocalDateTime.now().plusDays(3)
        val services = runBlocking { client.enumService() }
        val keys = services.take(8).map { it.key }
        val result = runBlocking { client.enumPgInfoEx(keys, start, end) }
        val events = result.flatMap { it.eventList }
        println("enumPgInfoEx: ${result.size} services, ${events.size} events")
        assertTrue("events=${events.size}", events.isNotEmpty())
        assertTrue(result.all { it.eventList.isNotEmpty() })
        events.forEach { event ->
            val eventStart = event.startDateTime
            assertNotNull("start time of ${event.eventKey.toString(16)}", eventStart)
            assertTrue(eventStart!!.isAfter(start.minusSeconds(1)))
            assertTrue(eventStart.isBefore(end))
        }
    }

    @Test
    fun enumPgInfoReturnsSingleEvent() {
        val start = LocalDateTime.now().minusHours(6)
        val end = LocalDateTime.now().plusDays(3)
        val services = runBlocking { client.enumService() }
        val keys = services.take(8).map { it.key }
        val events = runBlocking { client.enumPgInfoEx(keys, start, end) }
            .flatMap { it.eventList }
            .filter { it.startTimeFlag != 0 }
        assumeTrue("no events in range", events.isNotEmpty())
        val target = events.first()
        val pgKey = ServiceKey.event(target.onid, target.tsid, target.sid, target.eventId)
        val found = runBlocking { client.enumPgInfo(pgKey) }
        assertEquals(1, found.size)
        assertEquals(target.eventId, found[0].eventId)
        assertEquals(target.serviceKey, found[0].serviceKey)
        assertEquals(target.startTime, found[0].startTime)
        println("enumPgInfo: ${found[0].serviceKey.toString(16)}/${found[0].eventId} ${found[0].title}")
    }

    @Test
    fun searchPgWithEmptyKeywordAndRange() {
        val start = LocalDateTime.now().minusHours(6)
        val end = LocalDateTime.now().plusDays(3)
        val services = runBlocking { client.enumService() }
        val condition = SearchCondition.empty().copy(serviceList = services.take(12).map { it.key })
        val results = runBlocking {
            client.searchPg(listOf(condition), start, end)
        }
        println("searchPg: ${results.size} events")
        assertTrue("results=${results.size}", results.size >= 3)
        assertEquals(results.size, results.map { it.eventKey }.distinct().size)
        results.forEach { event ->
            val eventStart = event.startDateTime
            assertNotNull("start time of ${event.eventKey.toString(16)}", eventStart)
            assertTrue(eventStart!!.isAfter(start.minusSeconds(1)))
            assertTrue(eventStart.isBefore(end))
        }
        for (index in 1 until results.size) {
            val previous = results[index - 1].startDateTime!!
            val current = results[index].startDateTime!!
            assertTrue("not sorted at $index", !current.isBefore(previous))
        }
    }
}
