package com.starrow.epgtimer.data.model

object ServiceKey {
    fun create(onid: Int, tsid: Int, sid: Int): Long =
        ((onid.toLong() and 0xFFFF) shl 32) or
            ((tsid.toLong() and 0xFFFF) shl 16) or
            (sid.toLong() and 0xFFFF)

    fun event(onid: Int, tsid: Int, sid: Int, eventId: Int): Long =
        ((onid.toLong() and 0xFFFF) shl 48) or
            ((tsid.toLong() and 0xFFFF) shl 32) or
            ((sid.toLong() and 0xFFFF) shl 16) or
            (eventId.toLong() and 0xFFFF)

    fun onid(key: Long): Int = ((key ushr 32) and 0xFFFF).toInt()
    fun tsid(key: Long): Int = ((key ushr 16) and 0xFFFF).toInt()
    fun sid(key: Long): Int = (key and 0xFFFF).toInt()
}

data class ServiceInfo(
    val onid: Int,
    val tsid: Int,
    val sid: Int,
    val serviceType: Int,
    val partialReceptionFlag: Int,
    val serviceProviderName: String,
    val serviceName: String,
    val networkName: String,
    val tsName: String,
    val remoteControlKeyId: Int,
) {
    val key: Long
        get() = ServiceKey.create(onid, tsid, sid)
}

data class ServiceEventInfo(
    val serviceInfo: ServiceInfo,
    val eventList: List<EpgEvent>,
)
