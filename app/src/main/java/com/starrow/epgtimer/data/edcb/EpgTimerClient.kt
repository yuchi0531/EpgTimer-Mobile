package com.starrow.epgtimer.data.edcb

import com.starrow.epgtimer.data.model.EdcbDateTime
import com.starrow.epgtimer.data.model.EpgAutoAddData
import com.starrow.epgtimer.data.model.EpgEvent
import com.starrow.epgtimer.data.model.FileData
import com.starrow.epgtimer.data.model.RecFileInfo
import com.starrow.epgtimer.data.model.ReserveData
import com.starrow.epgtimer.data.model.SearchCondition
import com.starrow.epgtimer.data.model.ServiceEventInfo
import com.starrow.epgtimer.data.model.ServiceInfo
import java.io.IOException
import java.time.LocalDateTime

class EdcbException(
    val code: Int,
    message: String,
    cause: Throwable? = null,
) : IOException(message, cause)

data class ServerStatus(
    val notifyId: Int,
    val time: EdcbDateTime,
    val srvStatus: Int,
    val param2: Int,
    val notifyCount: Int,
    val param4: String,
    val param5: String,
    val param6: String,
) {
    val srvStatusText: String
        get() = when (srvStatus) {
            0 -> "通常"
            1 -> "録画中"
            2 -> "EPG取得中"
            else -> "不明($srvStatus)"
        }
}

interface EpgTimerClient {
    suspend fun getStatus(): ServerStatus

    suspend fun enumService(): List<ServiceInfo>

    suspend fun enumPgInfo(serviceKey: Long): List<EpgEvent>

    suspend fun enumPgInfoEx(
        serviceKeys: List<Long>,
        start: LocalDateTime?,
        end: LocalDateTime?,
    ): List<ServiceEventInfo>

    suspend fun enumPgArc(
        serviceKeys: List<Long>,
        start: LocalDateTime?,
        end: LocalDateTime?,
    ): List<ServiceEventInfo>

    suspend fun searchPg(
        keys: List<SearchCondition>,
        start: LocalDateTime?,
        end: LocalDateTime?,
    ): List<EpgEvent>

    suspend fun enumReserve(): List<ReserveData>

    suspend fun getReserve(reserveId: Int): ReserveData

    suspend fun addReserve(items: List<ReserveData>)

    suspend fun changeReserve(items: List<ReserveData>)

    suspend fun deleteReserve(ids: List<Int>)

    suspend fun enumRecFile(): List<RecFileInfo>

    suspend fun enumEpgAutoAdd(): List<EpgAutoAddData>

    suspend fun addEpgAutoAdd(items: List<EpgAutoAddData>)

    suspend fun changeEpgAutoAdd(items: List<EpgAutoAddData>)

    suspend fun deleteEpgAutoAdd(ids: List<Int>)

    suspend fun fileCopy2(names: List<String>): List<FileData>
}
