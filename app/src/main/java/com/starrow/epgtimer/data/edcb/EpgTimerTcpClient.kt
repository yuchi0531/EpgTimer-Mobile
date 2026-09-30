package com.starrow.epgtimer.data.edcb

import com.starrow.epgtimer.data.model.EpgAutoAddData
import com.starrow.epgtimer.data.model.EpgEvent
import com.starrow.epgtimer.data.model.FileData
import com.starrow.epgtimer.data.model.RecFileInfo
import com.starrow.epgtimer.data.model.ReserveData
import com.starrow.epgtimer.data.model.SearchCondition
import com.starrow.epgtimer.data.model.ServiceEventInfo
import com.starrow.epgtimer.data.model.ServiceInfo
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.LocalDateTime

class EpgTimerTcpClient(
    private val host: String,
    private val port: Int = 4510,
    private val connectTimeoutMs: Int = 5000,
    private val readTimeoutMs: Int = 30000,
) : EpgTimerClient {

    private val mutex = Mutex()

    private val empty = ByteArray(0)

    private suspend fun request(cmd: Int, data: ByteArray? = null): ByteArray =
        mutex.withLock {
            EDCBConnection.request(host, port, connectTimeoutMs, readTimeoutMs, cmd, data ?: empty)
        }

    override suspend fun getStatus(): ServerStatus {
        val send = CtrlCmdSerializer().apply {
            u16(0)
            u32(0)
        }
        val res = CtrlCmdDeserializer(request(CtrlCmd.CMD_EPG_SRV_GET_STATUS_NOTIFY2, send.toByteArray()))
        return with(res) {
            readVersion()
            readServerStatus()
        }
    }

    override suspend fun enumService(): List<ServiceInfo> {
        val res = CtrlCmdDeserializer(request(CtrlCmd.CMD_EPG_SRV_ENUM_SERVICE))
        return with(res) { vector { readServiceInfo() } }
    }

    override suspend fun enumPgInfo(serviceKey: Long): List<EpgEvent> {
        val send = CtrlCmdSerializer().apply { i64(serviceKey) }
        val res = CtrlCmdDeserializer(request(CtrlCmd.CMD_EPG_SRV_GET_PG_INFO, send.toByteArray()))
        return listOf(with(res) { readEpgEvent() })
    }

    override suspend fun enumPgInfoEx(
        serviceKeys: List<Long>,
        start: LocalDateTime?,
        end: LocalDateTime?,
    ): List<ServiceEventInfo> = enumPg(CtrlCmd.CMD_EPG_SRV_ENUM_PG_INFO_EX, serviceKeys, start, end)

    override suspend fun enumPgArc(
        serviceKeys: List<Long>,
        start: LocalDateTime?,
        end: LocalDateTime?,
    ): List<ServiceEventInfo> = enumPg(CtrlCmd.CMD_EPG_SRV_ENUM_PG_ARC, serviceKeys, start, end)

    private suspend fun enumPg(
        cmd: Int,
        serviceKeys: List<Long>,
        start: LocalDateTime?,
        end: LocalDateTime?,
    ): List<ServiceEventInfo> {
        val payload = ArrayList<Long>(serviceKeys.size * 2 + 2)
        for (serviceKey in serviceKeys) {
            payload.add(0L)
            payload.add(serviceKey)
        }
        payload.add(start?.let { FileTime.toFileTime(it) } ?: 0L)
        payload.add(end?.let { FileTime.toFileTime(it) } ?: Long.MAX_VALUE)
        val send = CtrlCmdSerializer()
        send.writeVectorI64(payload)
        val res = CtrlCmdDeserializer(request(cmd, send.toByteArray()))
        return with(res) { vector { readServiceEventInfo() } }
    }

    override suspend fun searchPg(
        keys: List<SearchCondition>,
        start: LocalDateTime?,
        end: LocalDateTime?,
    ): List<EpgEvent> {
        val sendCurrent = CtrlCmdSerializer()
        with(sendCurrent) { vector(keys) { writeSearchCondition(it) } }
        val currentRes = CtrlCmdDeserializer(
            request(CtrlCmd.CMD_EPG_SRV_SEARCH_PG, sendCurrent.toByteArray()),
        )
        val currentEvents = with(currentRes) { vector { readEpgEvent() } }

        val sendArchive = CtrlCmdSerializer(CtrlCmd.CMD_VER)
        sendArchive.u16(CtrlCmd.CMD_VER)
        sendArchive.writeSearchPgParam(keys, start, end)
        val archiveRes = CtrlCmdDeserializer(
            request(CtrlCmd.CMD_EPG_SRV_SEARCH_PG_ARC2, sendArchive.toByteArray()),
        )
        val archiveEvents = with(archiveRes) {
            readVersion()
            vector { readEpgEvent() }
        }
        val currentInRange = if (start == null && end == null) {
            currentEvents
        } else {
            currentEvents.filter { event ->
                val eventStart = event.startDateTime
                eventStart != null &&
                    (start == null || !eventStart.isBefore(start)) &&
                    (end == null || eventStart.isBefore(end))
            }
        }
        val merged = LinkedHashMap<Long, EpgEvent>()
        for (event in currentInRange) {
            if (!merged.containsKey(event.eventKey)) {
                merged[event.eventKey] = event
            }
        }
        for (event in archiveEvents) {
            if (!merged.containsKey(event.eventKey)) {
                merged[event.eventKey] = event
            }
        }
        return merged.values.sortedBy { it.startDateTime ?: LocalDateTime.MAX }
    }

    override suspend fun enumReserve(): List<ReserveData> {
        val send = CtrlCmdSerializer(CtrlCmd.CMD_VER).apply { u16(CtrlCmd.CMD_VER) }
        val res = CtrlCmdDeserializer(request(CtrlCmd.CMD_EPG_SRV_ENUM_RESERVE2, send.toByteArray()))
        return with(res) {
            readVersion()
            vector { readReserveData() }
        }
    }

    override suspend fun getReserve(reserveId: Int): ReserveData {
        val send = CtrlCmdSerializer(CtrlCmd.CMD_VER).apply {
            u16(CtrlCmd.CMD_VER)
            u32(reserveId)
        }
        val res = CtrlCmdDeserializer(request(CtrlCmd.CMD_EPG_SRV_GET_RESERVE2, send.toByteArray()))
        return with(res) {
            readVersion()
            readReserveData()
        }
    }

    override suspend fun addReserve(items: List<ReserveData>) {
        val send = CtrlCmdSerializer(CtrlCmd.CMD_VER).apply { u16(CtrlCmd.CMD_VER) }
        with(send) { vector(items) { writeReserveData(it) } }
        request(CtrlCmd.CMD_EPG_SRV_ADD_RESERVE2, send.toByteArray())
    }

    override suspend fun changeReserve(items: List<ReserveData>) {
        val send = CtrlCmdSerializer(CtrlCmd.CMD_VER).apply { u16(CtrlCmd.CMD_VER) }
        with(send) { vector(items) { writeReserveData(it) } }
        request(CtrlCmd.CMD_EPG_SRV_CHG_RESERVE2, send.toByteArray())
    }

    override suspend fun deleteReserve(ids: List<Int>) {
        val send = CtrlCmdSerializer()
        send.writeVectorU32(ids)
        request(CtrlCmd.CMD_EPG_SRV_DEL_RESERVE, send.toByteArray())
    }

    override suspend fun enumRecFile(): List<RecFileInfo> {
        val send = CtrlCmdSerializer(CtrlCmd.CMD_VER).apply { u16(CtrlCmd.CMD_VER) }
        val res = CtrlCmdDeserializer(request(CtrlCmd.CMD_EPG_SRV_ENUM_RECINFO2, send.toByteArray()))
        return with(res) {
            readVersion()
            vector { readRecFileInfo() }
        }
    }

    override suspend fun enumEpgAutoAdd(): List<EpgAutoAddData> {
        val send = CtrlCmdSerializer(CtrlCmd.CMD_VER).apply { u16(CtrlCmd.CMD_VER) }
        val res = CtrlCmdDeserializer(request(CtrlCmd.CMD_EPG_SRV_ENUM_AUTO_ADD2, send.toByteArray()))
        return with(res) {
            readVersion()
            vector { readEpgAutoAddData() }
        }
    }

    override suspend fun addEpgAutoAdd(items: List<EpgAutoAddData>) {
        writeAutoAdd(CtrlCmd.CMD_EPG_SRV_ADD_AUTO_ADD2, items)
    }

    override suspend fun changeEpgAutoAdd(items: List<EpgAutoAddData>) {
        writeAutoAdd(CtrlCmd.CMD_EPG_SRV_CHG_AUTO_ADD2, items)
    }

    private suspend fun writeAutoAdd(cmd: Int, items: List<EpgAutoAddData>) {
        val send = CtrlCmdSerializer(CtrlCmd.CMD_VER).apply { u16(CtrlCmd.CMD_VER) }
        with(send) { vector(items) { writeEpgAutoAddData(it) } }
        val res = CtrlCmdDeserializer(request(cmd, send.toByteArray()))
        with(res) { readVersion() }
    }

    override suspend fun deleteEpgAutoAdd(ids: List<Int>) {
        val send = CtrlCmdSerializer()
        send.writeVectorU32(ids)
        request(CtrlCmd.CMD_EPG_SRV_DEL_AUTO_ADD, send.toByteArray())
    }

    override suspend fun fileCopy2(names: List<String>): List<FileData> {
        val send = CtrlCmdSerializer(CtrlCmd.CMD_VER).apply { u16(CtrlCmd.CMD_VER) }
        with(send) { writeVectorString(names) }
        val res = CtrlCmdDeserializer(request(CtrlCmd.CMD2_EPG_SRV_FILE_COPY2, send.toByteArray()))
        return with(res) {
            readVersion()
            vector { readFileData() }
        }
    }
}
