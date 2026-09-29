package com.starrow.epgtimer.data.repository

import com.starrow.epgtimer.data.edcb.ServerStatus
import com.starrow.epgtimer.data.model.CustomProgramGuide
import com.starrow.epgtimer.data.model.EpgAutoAddData
import com.starrow.epgtimer.data.model.EpgEvent
import com.starrow.epgtimer.data.model.RecFileInfo
import com.starrow.epgtimer.data.model.ReserveData
import com.starrow.epgtimer.data.model.SearchCondition
import com.starrow.epgtimer.data.model.ServiceInfo
import kotlinx.coroutines.flow.StateFlow
import java.io.InputStream
import java.time.LocalDate
import java.time.LocalDateTime

data class ServerConfig(
    val host: String,
    val port: Int = 4510,
    val connectTimeoutMs: Int = 5000,
    val readTimeoutMs: Int = 30000,
)

data class GuideEvent(
    val event: EpgEvent,
    val dimmed: Boolean,
)

data class GuideData(
    val guide: CustomProgramGuide,
    val weekStart: LocalDate,
    val services: List<ServiceInfo>,
    val eventsByService: Map<Long, List<GuideEvent>>,
)

data class ImportGuidesResult(
    val guidesCount: Int,
    val useCustomEpgView: Boolean,
)

data class GuideWindow(
    val start: LocalDateTime,
    val end: LocalDateTime?,
)

fun guideWindow(weekStart: LocalDate, base: LocalDate): GuideWindow = GuideWindow(
    start = weekStart.atStartOfDay(),
    end = if (weekStart >= base) null else weekStart.plusDays(7).atStartOfDay(),
)

enum class GuidesSource {
    BUILT_IN,
    IMPORTED,
}

interface EpgRepository {
    val serverConfig: StateFlow<ServerConfig>
    fun updateServerConfig(config: ServerConfig)

    val guides: StateFlow<List<CustomProgramGuide>>
    val guidesSource: StateFlow<GuidesSource>
    suspend fun importGuides(input: InputStream): Result<ImportGuidesResult>

    val cacheEnabled: StateFlow<Boolean>
    fun setCacheEnabled(enabled: Boolean)
    fun clearCache()

    suspend fun testConnection(): Result<ServerStatus>

    suspend fun getServices(): Result<List<ServiceInfo>>

    suspend fun getEvent(serviceKey: Long, eventId: Int): Result<EpgEvent>

    suspend fun loadGuideWeek(guide: CustomProgramGuide, weekStart: LocalDate): Result<GuideData>

    suspend fun getReserves(): Result<List<ReserveData>>
    suspend fun getReserve(reserveId: Int): Result<ReserveData>
    suspend fun getDefaultRecSetting(): Result<ReserveData>
    suspend fun addReserve(reserve: ReserveData): Result<Unit>
    suspend fun changeReserve(reserve: ReserveData): Result<Unit>
    suspend fun deleteReserve(ids: List<Int>): Result<Unit>

    suspend fun getRecFiles(): Result<List<RecFileInfo>>

    suspend fun getAutoAdds(): Result<List<EpgAutoAddData>>
    suspend fun addAutoAdd(item: EpgAutoAddData): Result<Int>
    suspend fun changeAutoAdd(item: EpgAutoAddData): Result<Unit>
    suspend fun deleteAutoAdd(ids: List<Int>): Result<Unit>

    suspend fun searchEvents(
        key: SearchCondition,
        start: LocalDate,
        end: LocalDate,
    ): Result<List<EpgEvent>>

    suspend fun loadLogos(): Result<Map<String, ByteArray>>
}
