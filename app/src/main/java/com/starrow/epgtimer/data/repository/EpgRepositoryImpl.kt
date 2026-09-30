package com.starrow.epgtimer.data.repository

import com.starrow.epgtimer.data.edcb.EdcbException
import com.starrow.epgtimer.data.edcb.EpgTimerClient
import com.starrow.epgtimer.data.edcb.EpgTimerTcpClient
import com.starrow.epgtimer.data.edcb.ErrCode
import com.starrow.epgtimer.data.guide.ChSet5Parser
import com.starrow.epgtimer.data.guide.DefaultGuides
import com.starrow.epgtimer.data.guide.GuideEngine
import com.starrow.epgtimer.data.guide.GuideXmlParser
import com.starrow.epgtimer.data.guide.LogoIndex
import com.starrow.epgtimer.data.model.CustomProgramGuide
import com.starrow.epgtimer.data.model.EpgAutoAddData
import com.starrow.epgtimer.data.model.EpgEvent
import com.starrow.epgtimer.data.model.RecFileInfo
import com.starrow.epgtimer.data.model.RecSettingData
import com.starrow.epgtimer.data.model.ReserveData
import com.starrow.epgtimer.data.model.SearchCondition
import com.starrow.epgtimer.data.model.ServiceInfo
import com.starrow.epgtimer.data.model.ServiceKey
import com.starrow.epgtimer.util.EpgClock
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException
import java.io.InputStream
import java.time.LocalDate

class EpgRepositoryImpl(
    private val store: SettingsStore,
    private val clientFactory: (ServerConfig) -> EpgTimerClient = { config ->
        EpgTimerTcpClient(
            host = config.host,
            port = config.port,
            connectTimeoutMs = config.connectTimeoutMs,
            readTimeoutMs = config.readTimeoutMs,
        )
    },
    private val guideEngine: GuideEngine = GuideEngine(),
) : EpgRepository {

    private val prefs = store

    private val _serverConfig = MutableStateFlow(
        ServerConfig(
            host = prefs.getString(KEY_HOST, DEFAULT_HOST) ?: DEFAULT_HOST,
            port = prefs.getInt(KEY_PORT, DEFAULT_PORT),
            connectTimeoutMs = prefs.getInt(KEY_CONNECT_TIMEOUT_MS, DEFAULT_CONNECT_TIMEOUT_MS),
            readTimeoutMs = prefs.getInt(KEY_READ_TIMEOUT_MS, DEFAULT_READ_TIMEOUT_MS),
        ),
    )
    override val serverConfig: StateFlow<ServerConfig> = _serverConfig.asStateFlow()

    private val importedXml: String? = prefs.getString(KEY_GUIDES_XML, DEFAULT_HOST)?.takeIf { it.isNotEmpty() }

    private val _guides = MutableStateFlow(DefaultGuides.create())
    override val guides: StateFlow<List<CustomProgramGuide>> = _guides.asStateFlow()

    private val _guidesSource = MutableStateFlow(
        if (importedXml != null) GuidesSource.IMPORTED else GuidesSource.BUILT_IN,
    )
    override val guidesSource: StateFlow<GuidesSource> = _guidesSource.asStateFlow()

    private val parseScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    init {
        val xml = importedXml
        if (xml != null) {
            parseScope.launch {
                _guides.value = loadGuides(xml)
                _guidesSource.value = GuidesSource.IMPORTED
            }
        }
    }

    private val _cacheEnabled = MutableStateFlow(prefs.getBoolean(KEY_CACHE_ENABLED, true))
    override val cacheEnabled: StateFlow<Boolean> = _cacheEnabled.asStateFlow()

    private val servicesLock = Any()

    private var servicesCache: List<ServiceInfo>? = null

    private val clientLock = Any()

    private var cachedClient: EpgTimerClient? = null

    private val guideCache = object : LinkedHashMap<String, GuideData>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, GuideData>): Boolean =
            size > MAX_CACHE_ENTRIES
    }

    private val logoLock = Any()

    private var logoCache: Map<String, ByteArray>? = null

    private fun cachedServices(): List<ServiceInfo>? = synchronized(servicesLock) { servicesCache }

    private fun invalidateServices() {
        synchronized(servicesLock) { servicesCache = null }
    }

    private suspend fun services(client: EpgTimerClient): List<ServiceInfo> {
        cachedServices()?.let { return it }
        val fetched = client.enumService()
        synchronized(servicesLock) {
            val existing = servicesCache
            if (existing != null) return existing
            servicesCache = fetched
        }
        return fetched
    }

    private fun loadGuides(xml: String?): List<CustomProgramGuide> {
        if (xml == null) return DefaultGuides.create()
        return try {
            val parsed = GuideXmlParser.parse(xml)
            if (parsed.useCustomEpgView) parsed.guides else DefaultGuides.create()
        } catch (e: Exception) {
            DefaultGuides.create()
        }
    }

    private fun client(): EpgTimerClient {
        val config = _serverConfig.value
        if (config.host.isBlank()) {
            throw EdcbException(ErrCode.CMD_ERR_CONNECT, "サーバーが設定されていません")
        }
        return synchronized(clientLock) {
            cachedClient ?: clientFactory(config).also { cachedClient = it }
        }
    }

    private suspend fun <T> withServer(block: suspend (EpgTimerClient) -> T): Result<T> =
        withContext(Dispatchers.IO) { fetchWithRetry(block) }

    private suspend fun <T> fetchWithRetry(block: suspend (EpgTimerClient) -> T): Result<T> {
        var attempt = 0
        while (true) {
            try {
                return Result.success(block(client()))
            } catch (e: EdcbException) {
                if (e.code == ErrCode.CMD_ERR_BUSY && attempt < BUSY_RETRIES) {
                    attempt++
                    delay(BUSY_RETRY_DELAY_MS)
                } else {
                    return Result.failure(e)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: IOException) {
                return Result.failure(e)
            } catch (e: Exception) {
                return Result.failure(e)
            }
        }
    }

    override fun updateServerConfig(config: ServerConfig) {
        prefs.edit()
            .putString(KEY_HOST, config.host)
            .putInt(KEY_PORT, config.port)
            .putInt(KEY_CONNECT_TIMEOUT_MS, config.connectTimeoutMs)
            .putInt(KEY_READ_TIMEOUT_MS, config.readTimeoutMs)
            .apply()
        _serverConfig.value = config
        invalidateServices()
        synchronized(clientLock) { cachedClient = null }
        clearCache()
    }

    override suspend fun importGuides(input: InputStream): Result<ImportGuidesResult> =
        withContext(Dispatchers.IO) {
            try {
                val bytes = input.use { it.readNBytes(MAX_GUIDES_XML + 1) }
                if (bytes.size > MAX_GUIDES_XML) {
                    return@withContext Result.failure(
                        IllegalArgumentException("設定ファイルが大きすぎます(上限 ${MAX_GUIDES_XML / 1024}KB)"),
                    )
                }
                val text = bytes.toString(Charsets.UTF_8).removePrefix("﻿")
                if (!text.contains("<Settings")) {
                    return@withContext Result.failure(
                        IllegalArgumentException("EpgTimer/EpgTimerNWの設定XML(<Settings>)ではありません"),
                    )
                }
                val parsed = GuideXmlParser.parseOrNull(text)
                    ?: return@withContext Result.failure(
                        IllegalArgumentException("設定XMLを解析できませんでした"),
                    )
                prefs.edit().putString(KEY_GUIDES_XML, text).apply()
                _guides.value =
                    if (parsed.useCustomEpgView) parsed.guides else DefaultGuides.create()
                _guidesSource.value = GuidesSource.IMPORTED
                clearCache()
                Result.success(ImportGuidesResult(parsed.guides.size, parsed.useCustomEpgView))
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    override fun setCacheEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_CACHE_ENABLED, enabled).apply()
        _cacheEnabled.value = enabled
        if (!enabled) clearCache()
    }

    override fun clearCache() {
        synchronized(guideCache) { guideCache.clear() }
        synchronized(logoLock) { logoCache = null }
        invalidateServices()
    }

    override suspend fun testConnection(): Result<com.starrow.epgtimer.data.edcb.ServerStatus> =
        withServer { it.getStatus() }

    override suspend fun getServices(): Result<List<ServiceInfo>> = withServer { services(it) }

    override suspend fun getEvent(serviceKey: Long, eventId: Int): Result<EpgEvent> = withServer { client ->
        val pgKey = ServiceKey.event(
            ServiceKey.onid(serviceKey),
            ServiceKey.tsid(serviceKey),
            ServiceKey.sid(serviceKey),
            eventId,
        )
        client.enumPgInfo(pgKey).first()
    }

    override suspend fun loadGuideWeek(
        guide: CustomProgramGuide,
        weekStart: LocalDate,
    ): Result<GuideData> {
        val cacheKey = "$guide|${weekStart}"
        if (_cacheEnabled.value) {
            synchronized(guideCache) { guideCache[cacheKey] }?.let { return Result.success(it) }
        }
        val now = EpgClock.now()
        val base = guideEngine.eventBaseTime(now, pastAvailable = true).toLocalDate()
        val window = guideWindow(weekStart, base)
        val result = withServer { client ->
            val all = services(client)
            val expanded = guideEngine.expandViewServices(guide.viewServiceList, all)
            val start = window.start
            val end = window.end
            val events = if (expanded.isEmpty()) {
                emptyList()
            } else if (guide.searchMode && !guide.searchKey.isEmpty) {
                client.searchPg(listOf(guide.searchKey), start, end)
            } else {
                val keys = expanded.map { it.key }
                val merged = LinkedHashMap<Long, EpgEvent>()
                for (event in client.enumPgInfoEx(keys, start, end).flatMap { it.eventList }) {
                    merged.putIfAbsent(event.eventKey, event)
                }
                if (start.isBefore(now)) {
                    val arcEnd = if (end != null && end.isBefore(now)) end else now
                    for (event in client.enumPgArc(keys, start, arcEnd).flatMap { it.eventList }) {
                        merged.putIfAbsent(event.eventKey, event)
                    }
                }
                merged.values.toList()
            }
            guideEngine.buildGuideData(guide, weekStart, expanded, events)
        }
        result.onSuccess { data ->
            if (_cacheEnabled.value) {
                synchronized(guideCache) { guideCache[cacheKey] = data }
            }
        }
        return result
    }

    override suspend fun getReserves(): Result<List<ReserveData>> =
        withServer { it.enumReserve() }

    override suspend fun getReserve(reserveId: Int): Result<ReserveData> =
        withServer { it.getReserve(reserveId) }

    override suspend fun getDefaultRecSetting(): Result<RecSettingData> = withServer { client ->
        val serverDefault = client.getReserve(ReserveData.DEFAULT_RESERVE_ID).recSetting
        val iniDefault = fetchDefaultRecSettingFromIni(client)
        iniDefault ?: DefaultRecSetting.withServerFallback(serverDefault)
    }

    private suspend fun fetchDefaultRecSettingFromIni(client: EpgTimerClient): RecSettingData? {
        val files = try {
            client.fileCopy2(listOf(TIMER_SRV_INI_NAME))
        } catch (e: Exception) {
            return null
        }
        val ini = files.firstOrNull { it.name.endsWith(TIMER_SRV_INI_NAME) } ?: return null
        if (ini.data.isEmpty()) return null
        return DefaultRecSetting.fromIni(DefaultRecSetting.decode(ini.data))
    }

    override suspend fun addReserve(reserve: ReserveData): Result<Unit> =
        withServer { it.addReserve(listOf(reserve)) }

    override suspend fun changeReserve(reserve: ReserveData): Result<Unit> =
        withServer { it.changeReserve(listOf(reserve)) }

    override suspend fun deleteReserve(ids: List<Int>): Result<Unit> =
        withServer { it.deleteReserve(ids) }

    override suspend fun getRecFiles(): Result<List<RecFileInfo>> =
        withServer { it.enumRecFile() }

    override suspend fun getAutoAdds(): Result<List<EpgAutoAddData>> =
        withServer { it.enumEpgAutoAdd() }

    override suspend fun addAutoAdd(item: EpgAutoAddData): Result<Int> {
        val before = getAutoAdds().getOrElse { return Result.failure(it) }.map { it.dataId }.toSet()
        val result = withServer { it.addEpgAutoAdd(listOf(item.copy(dataId = EpgAutoAddData.NEW_DATA_ID, addCount = 0))) }
        val failure = result.exceptionOrNull()
        if (failure != null) return Result.failure(failure)
        val after = getAutoAdds().getOrElse { return Result.failure(it) }
        val added = after.firstOrNull { it.dataId !in before }
            ?: return Result.failure(IllegalStateException("登録した自動予約条件が一覧に見つかりませんでした"))
        return Result.success(added.dataId)
    }

    override suspend fun changeAutoAdd(item: EpgAutoAddData): Result<Unit> {
        val result = withServer { it.changeEpgAutoAdd(listOf(item)) }
        if (result.isFailure) return result
        return getAutoAdds().map { }
    }

    override suspend fun deleteAutoAdd(ids: List<Int>): Result<Unit> =
        withServer { it.deleteEpgAutoAdd(ids) }

    override suspend fun searchEvents(
        key: SearchCondition,
        start: LocalDate,
        end: LocalDate,
    ): Result<List<EpgEvent>> {
        if (key.isEmpty) {
            return Result.failure(IllegalArgumentException("検索条件を指定してください"))
        }
        return withServer { client ->
            val all = services(client)
            val scoped = if (key.serviceList.isEmpty()) {
                key.copy(serviceList = all.map { it.key })
            } else {
                key
            }
            client.searchPg(listOf(scoped), start.atStartOfDay(), end.plusDays(1).atStartOfDay())
        }
    }

    override suspend fun loadLogos(): Result<Map<String, ByteArray>> {
        synchronized(logoLock) { logoCache }?.let { return Result.success(it) }
        val result = withServer { client -> fetchLogos(client) }
        result.onSuccess { logos ->
            synchronized(logoLock) {
                logoCache = if (logos.isEmpty()) null else logos
            }
        }
        return result
    }

    private suspend fun fetchLogos(client: EpgTimerClient): Map<String, ByteArray> {
        val index = client.fileCopy2(listOf(CH_SET5_NAME, LOGO_INI_NAME))
        val chSet5 = index.firstOrNull { it.name.equals(CH_SET5_NAME, ignoreCase = true) }?.data
            ?: return emptyMap()
        val entries = ChSet5Parser.parse(LogoIndex.decodeText(chSet5))
        if (entries.isEmpty()) return emptyMap()

        val logoIni = index.firstOrNull { it.name.equals(LOGO_INI_NAME, ignoreCase = true) }?.data
        val listing = client.fileCopy2(listOf(LOGO_FOLDER_WILDCARD))
            .firstOrNull { it.name.equals(LOGO_FOLDER_WILDCARD, ignoreCase = true) }?.data
        if (logoIni == null || logoIni.isEmpty() || listing == null || listing.isEmpty()) {
            return emptyMap()
        }

        val logoIdMap = LogoIndex.parseLogoIdMap(LogoIndex.decodeText(logoIni))
        val fileNames = LogoIndex.parseFileNames(LogoIndex.decodeText(listing))

        val wanted = HashMap<String, String>(entries.size)
        for (entry in entries) {
            val chId = (entry.onid.toLong() shl 16) or entry.sid.toLong()
            val logoId = logoIdMap[chId] ?: continue
            val fileName = LogoIndex.resolveFileName((entry.onid shl 16) or logoId, fileNames) ?: continue
            val serviceKey = ServiceKey.create(entry.onid, entry.tsid, entry.sid)
            wanted.putIfAbsent(fileName, serviceKey.toString(16))
        }
        if (wanted.isEmpty()) return emptyMap()

        val logos = LinkedHashMap<String, ByteArray>(wanted.size)
        var totalBytes = 0
        for (batch in wanted.entries.chunked(FILE_COPY_BATCH)) {
            for (fileData in client.fileCopy2(batch.map { it.key })) {
                if (fileData.data.isEmpty() || fileData.data.size > MAX_LOGO_BYTES) continue
                val serviceKey = batch.firstOrNull { it.key == fileData.name }?.value ?: continue
                if (serviceKey in logos) continue
                totalBytes += fileData.data.size
                if (totalBytes > MAX_LOGO_TOTAL_BYTES) return logos
                logos[serviceKey] = fileData.data
            }
        }
        return logos
    }

    companion object {
        private const val KEY_HOST = "server_host"
        private const val KEY_PORT = "server_port"
        private const val KEY_CONNECT_TIMEOUT_MS = "connect_timeout_ms"
        private const val KEY_READ_TIMEOUT_MS = "read_timeout_ms"
        private const val KEY_GUIDES_XML = "guides_xml"
        private const val KEY_CACHE_ENABLED = "cache_enabled"

        private const val DEFAULT_HOST = ""
        private const val DEFAULT_PORT = 4510
        private const val DEFAULT_CONNECT_TIMEOUT_MS = 5000
        private const val DEFAULT_READ_TIMEOUT_MS = 30000

        private const val BUSY_RETRIES = 3
        private const val BUSY_RETRY_DELAY_MS = 1000L
        private const val MAX_CACHE_ENTRIES = 4
        private const val MAX_GUIDES_XML = 4 * 1024 * 1024

        private const val CH_SET5_NAME = "ChSet5.txt"
        private const val LOGO_INI_NAME = "LogoData.ini"
        private const val TIMER_SRV_INI_NAME = "EpgTimerSrv.ini"
        private const val LOGO_FOLDER_WILDCARD = "LogoData\\*.*"
        private const val FILE_COPY_BATCH = 100
        private const val MAX_LOGO_BYTES = 32 * 1024
        private const val MAX_LOGO_TOTAL_BYTES = 8 * 1024 * 1024
    }
}
