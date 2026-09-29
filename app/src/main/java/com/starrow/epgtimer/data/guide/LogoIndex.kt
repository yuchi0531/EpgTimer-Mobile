package com.starrow.epgtimer.data.guide

object LogoIndex {

    private val LOGO_TYPES = intArrayOf(5, 2, 4, 1, 3, 0)

    fun decodeText(data: ByteArray): String {
        if (data.size >= 2 && data[0] == 0xFF.toByte() && data[1] == 0xFE.toByte()) {
            return String(data, 2, data.size - 2, Charsets.UTF_16LE).trimStart('﻿')
        }
        return String(data, Charsets.UTF_8).trimStart('﻿')
    }

    fun parseLogoIdMap(logoIni: String): Map<Long, Int> {
        val map = HashMap<Long, Int>()
        for (line in logoIni.split('\r', '\n')) {
            val text = line.trimEnd()
            if (text.length <= 9) continue
            if (text[8] != '=') continue
            val key = text.substring(0, 8).toLongOrNull(16) ?: continue
            val raw = text.substring(9).trim()
            val logoId = when {
                raw.startsWith("0x", ignoreCase = true) -> raw.substring(2).toIntOrNull(16)
                raw.any { it in 'A'..'F' || it in 'a'..'f' } -> raw.toIntOrNull(16)
                else -> raw.toIntOrNull()
            } ?: continue
            if (logoId in 0..0x1FF) map[key] = logoId
        }
        return map
    }

    fun parseFileNames(listing: String): List<String> {
        val names = ArrayList<String>()
        for (line in listing.split('\n')) {
            val text = line.trimEnd('\r')
            if (text.count { it == ' ' } < 3) continue
            names.add(text.substring(text.indexOf(' ', text.indexOf(' ', text.indexOf(' ') + 1) + 1) + 1))
        }
        names.sortWith(String.CASE_INSENSITIVE_ORDER)
        return names
    }

    fun startKey(onid: Int, logoId: Int): String =
        "%04X_%03X_".format(onid and 0xFFFF, logoId and 0x1FF)

    fun resolveFileName(onidLogoId: Int, fileNames: List<String>): String? {
        val onid = (onidLogoId ushr 16) and 0xFFFF
        val key = startKey(onid, onidLogoId and 0x1FF)
        val candidates = fileNames.filter { it.startsWith(key, ignoreCase = true) }
        if (candidates.isEmpty()) return null
        for (type in LOGO_TYPES) {
            val suffix = "_0$type.png"
            val hit = candidates.firstOrNull { it.endsWith(suffix, ignoreCase = true) } ?: continue
            return "LogoData\\$hit"
        }
        return null
    }
}
