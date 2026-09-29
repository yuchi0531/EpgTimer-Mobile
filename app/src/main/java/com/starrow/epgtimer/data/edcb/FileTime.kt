package com.starrow.epgtimer.data.edcb

import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset

object FileTime {
    private const val EPOCH_OFFSET = 116444736000000000L
    private const val TICKS_PER_SECOND = 10_000_000L

    fun toFileTime(time: LocalDateTime): Long {
        val instant = time.toInstant(ZoneOffset.UTC)
        return instant.epochSecond * TICKS_PER_SECOND +
            instant.nano / 100L +
            EPOCH_OFFSET
    }

    fun fromFileTime(fileTime: Long): LocalDateTime {
        val ticks = fileTime - EPOCH_OFFSET
        val seconds = Math.floorDiv(ticks, TICKS_PER_SECOND)
        val nanos = Math.floorMod(ticks, TICKS_PER_SECOND) * 100L
        return LocalDateTime.ofInstant(Instant.ofEpochSecond(seconds, nanos), ZoneOffset.UTC)
    }
}
