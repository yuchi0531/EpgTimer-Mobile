package com.starrow.epgtimer.util

import java.time.LocalDateTime
import java.time.ZoneOffset

object EpgClock {
    private val JST = ZoneOffset.ofHours(9)

    fun now(): LocalDateTime = LocalDateTime.now(JST)
}
