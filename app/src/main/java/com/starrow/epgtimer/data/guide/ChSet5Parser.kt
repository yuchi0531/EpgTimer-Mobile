package com.starrow.epgtimer.data.guide

data class ChSet5Entry(
    val serviceName: String,
    val networkName: String,
    val onid: Int,
    val tsid: Int,
    val sid: Int,
    val serviceType: Int,
    val partialFlag: Boolean,
    val epgCapFlag: Boolean,
    val searchFlag: Boolean,
    val remoconId: Int,
)

object ChSet5Parser {

    fun parse(text: String): List<ChSet5Entry> {
        val body = text.removePrefix("﻿")
        val entries = ArrayList<ChSet5Entry>()
        val seen = HashSet<Long>()
        for (rawLine in body.split('\n')) {
            val line = rawLine.trimEnd('\r')
            if (line.isEmpty() || line.startsWith(";")) continue
            val fields = line.split('\t')
            if (fields.size < 9) continue
            val entry = ChSet5Entry(
                serviceName = fields[0],
                networkName = fields[1],
                onid = fields[2].trim().toIntOrNull() ?: continue,
                tsid = fields[3].trim().toIntOrNull() ?: continue,
                sid = fields[4].trim().toIntOrNull() ?: continue,
                serviceType = fields[5].trim().toIntOrNull() ?: 0,
                partialFlag = fields[6].trim().toIntOrNull() != 0,
                epgCapFlag = fields[7].trim().toIntOrNull() != 0,
                searchFlag = fields[8].trim().toIntOrNull() != 0,
                remoconId = if (fields.size < 10) 0 else fields[9].trim().toIntOrNull() ?: 0,
            )
            val key = com.starrow.epgtimer.data.model.ServiceKey.create(entry.onid, entry.tsid, entry.sid)
            if (seen.add(key)) entries.add(entry)
        }
        return entries
    }
}
