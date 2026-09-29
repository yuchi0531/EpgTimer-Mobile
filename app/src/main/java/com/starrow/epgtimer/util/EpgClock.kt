package com.starrow.epgtimer.util

import java.time.LocalDateTime
import java.time.ZoneOffset

object EpgClock {
    fun now(): LocalDateTime = LocalDateTime.now(ZoneOffset.ofHours(9))
}
