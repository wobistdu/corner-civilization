package com.example.cornercivilization.p37f776dc.ui.world

import com.example.cornercivilization.p37f776dc.domain.model.TimeSlice
import java.time.Instant
import java.time.ZoneId

/** Monotonic deltas only. Wall clock chooses dates, never adds elapsed duration. */
internal class WorldClock(private val zone: () -> ZoneId = { ZoneId.systemDefault() }) {
    private var uptime: Long? = null
    private var wall: Instant? = null

    fun rebase(nowUptime: Long, nowWall: Instant) { uptime = nowUptime; wall = nowWall }

    fun settle(nowUptime: Long, nowWall: Instant, growth: Boolean, battle: Boolean): List<TimeSlice> {
        val previous = uptime
        var cursor = wall ?: nowWall
        rebase(nowUptime, nowWall)
        if (previous == null) return emptyList()
        var remaining = (nowUptime - previous).coerceAtLeast(0)
        val result = mutableListOf<TimeSlice>()
        while (remaining > 0) {
            val date = cursor.atZone(zone()).toLocalDate()
            val midnight = date.plusDays(1).atStartOfDay(zone()).toInstant()
            val untilMidnight = java.time.Duration.between(cursor, midnight).toMillis().coerceAtLeast(1)
            val duration = minOf(remaining, untilMidnight)
            result += TimeSlice(date, duration, growth, battle && growth)
            remaining -= duration
            cursor = cursor.plusMillis(duration)
        }
        return result
    }
}
