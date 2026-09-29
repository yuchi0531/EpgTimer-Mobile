package com.starrow.epgtimer.data.model

import java.time.LocalDateTime

data class EdcbDateTime(
    val year: Int,
    val month: Int,
    val dayOfWeek: Int,
    val day: Int,
    val hour: Int,
    val minute: Int,
    val second: Int,
    val millisecond: Int,
) {
    fun toLocalDateTime(): LocalDateTime? {
        if (year !in 1..9999 || month !in 1..12 || day !in 1..31) return null
        if (hour !in 0..23 || minute !in 0..59 || second !in 0..60) return null
        return runCatching {
            LocalDateTime.of(year, month, day, hour, minute, second, millisecond * 1_000_000)
        }.getOrNull()
    }

    companion object {
        fun from(dateTime: LocalDateTime): EdcbDateTime = EdcbDateTime(
            year = dateTime.year,
            month = dateTime.monthValue,
            dayOfWeek = dateTime.dayOfWeek.value % 7,
            day = dateTime.dayOfMonth,
            hour = dateTime.hour,
            minute = dateTime.minute,
            second = dateTime.second,
            millisecond = dateTime.nano / 1_000_000,
        )

        fun invalid(): EdcbDateTime = EdcbDateTime(0, 0, 0, 0, 0, 0, 0, 0)
    }
}
