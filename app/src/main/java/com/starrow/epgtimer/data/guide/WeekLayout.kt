package com.starrow.epgtimer.data.guide

import java.time.LocalDate

data class WeekLayout(
    val days: List<LocalDate>,
    val startHour: Int,
)
