package com.starrow.epgtimer.data.model

data class RecFileInfo(
    val id: Int,
    val recFilePath: String,
    val title: String,
    val startTime: EdcbDateTime,
    val durationSecond: Int,
    val serviceName: String,
    val onid: Int,
    val tsid: Int,
    val sid: Int,
    val eventId: Int,
    val drops: Long,
    val scrambles: Long,
    val recStatus: Int,
    val startTimeEpg: EdcbDateTime,
    val comment: String,
    val programInfo: String,
    val errInfo: String,
    val protectFlag: Int,
)
