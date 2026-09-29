package com.starrow.epgtimer.data.edcb

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDateTime

class FileTimeTest {

    @Test
    fun windowsEpochIsZero() {
        assertEquals(0L, FileTime.toFileTime(LocalDateTime.of(1601, 1, 1, 0, 0, 0)))
        assertEquals(LocalDateTime.of(1601, 1, 1, 0, 0, 0), FileTime.fromFileTime(0L))
    }

    @Test
    fun unixEpochOffset() {
        assertEquals(116444736000000000L, FileTime.toFileTime(LocalDateTime.of(1970, 1, 1, 0, 0, 0)))
        assertEquals(LocalDateTime.of(1970, 1, 1, 0, 0, 0), FileTime.fromFileTime(116444736000000000L))
    }

    @Test
    fun knownValue() {
        assertEquals(
            133485408000000000L,
            FileTime.toFileTime(LocalDateTime.of(2024, 1, 1, 0, 0, 0)),
        )
    }

    @Test
    fun roundTripKeepsMilliseconds() {
        val values = listOf(
            LocalDateTime.of(2024, 6, 6, 19, 0, 0),
            LocalDateTime.of(2024, 6, 6, 19, 0, 0, 123_000_000),
            LocalDateTime.of(1998, 12, 31, 23, 59, 59, 999_000_000),
            LocalDateTime.of(1601, 1, 2, 3, 4, 5, 0),
        )
        for (value in values) {
            assertEquals(value, FileTime.fromFileTime(FileTime.toFileTime(value)))
        }
    }

    @Test
    fun subMillisecondIsTruncatedToHundredNanoseconds() {
        val value = LocalDateTime.of(2024, 6, 6, 19, 0, 0).plusNanos(123_456_789)
        assertEquals(value.withNano(123_456_700), FileTime.fromFileTime(FileTime.toFileTime(value)))
    }

    @Test
    fun oneSecondIsTenMillionTicks() {
        val base = LocalDateTime.of(2024, 6, 6, 19, 0, 0)
        assertEquals(
            FileTime.toFileTime(base.plusSeconds(1)) - FileTime.toFileTime(base),
            10_000_000L,
        )
    }
}
