package com.starrow.epgtimer.data.repository

import com.starrow.epgtimer.data.model.RecFileSetInfo
import com.starrow.epgtimer.data.model.RecSettingData
import java.io.ByteArrayInputStream
import java.nio.charset.Charset

/**
 * EpgTimer's own default recording settings.
 *
 * EpgTimer does not take the server's `getReserve(0x7FFFFFFF)` reply as the
 * default: that reply only carries a "no recording" marker plus a few runtime
 * values. EpgTimer instead reads `[REC_DEF]` from its local `EpgTimerSrv.ini`
 * (SettingClass.cs `CreateRecSetting`). When that file cannot be read the
 * server reply is used with its invalid marker cleared, so a reservation is
 * never rejected for being disabled.
 */
object DefaultRecSetting {

    private const val SECTION = "REC_DEF"
    private const val SECTION_FOLDER = "REC_DEF_FOLDER"

    fun fromIni(text: String): RecSettingData? {
        val ini = IniFile(text) ?: return null
        val recMode = ini.intOrNull("$SECTION", "RecMode") ?: return null
        val noRecMode = ini.intOrNull("$SECTION", "NoRecMode")
        val effective = if (recMode / 5 % 2 != 0 && noRecMode != null) {
            (5 + (noRecMode + 4) % 5)
        } else {
            recMode
        }
        val useMargin = (ini.intOrNull("$SECTION", "UseMargineFlag") ?: 0) != 0
        val batPath = ini.string("$SECTION", "BatFilePath") ?: ""
        val batTag = batPath.substringAfter('*', "")
        return RecSettingData(
            recMode = effective,
            priority = ini.intOrNull("$SECTION", "Priority") ?: 2,
            tuijyuuFlag = (ini.intOrNull("$SECTION", "TuijyuuFlag") ?: 1),
            serviceMode = ini.intOrNull("$SECTION", "ServiceMode") ?: 0,
            pittariFlag = ini.intOrNull("$SECTION", "PittariFlag") ?: 0,
            batFilePath = if (batPath.isEmpty()) "" else batTag,
            recFolderList = folderList(ini),
            suspendMode = ini.intOrNull("$SECTION", "SuspendMode") ?: 0,
            rebootFlag = ini.intOrNull("$SECTION", "RebootFlag") ?: 0,
            useMargineFlag = if (useMargin) 1 else 0,
            startMargine = if (useMargin) (ini.intOrNull("$SECTION", "StartMargine") ?: 0) else 0,
            endMargine = if (useMargin) (ini.intOrNull("$SECTION", "EndMargine") ?: 0) else 0,
            continueRecFlag = ini.intOrNull("$SECTION", "ContinueRec") ?: 0,
            partialRecFlag = ini.intOrNull("$SECTION", "PartialRec") ?: 0,
            tunerID = 0,
            partialRecFolder = emptyList(),
        )
    }

    fun withServerFallback(server: RecSettingData): RecSettingData {
        val invalid = server.isNoRec
        val mode = if (invalid) server.effectiveRecMode else server.recMode
        val folders = if (server.recFolderList.isEmpty()) listOf(RecFileSetInfo("", "", "", "")) else server.recFolderList
        return server.copy(
            recMode = mode,
            recFolderList = folders,
            partialRecFolder = emptyList(),
        )
    }

    private fun folderList(ini: IniFile): List<RecFileSetInfo> {
        val count = ini.intOrNull(SECTION_FOLDER, "Count") ?: 0
        if (count <= 0) {
            return listOf(RecFileSetInfo("", "", "", ""))
        }
        return (1..count).map { index ->
            RecFileSetInfo(
                recFolder = ini.string(SECTION_FOLDER, index.toString()).orEmpty(),
                writePlugIn = ini.string(SECTION_FOLDER, "WritePlugIn$index").orEmpty(),
                recNamePlugIn = ini.string(SECTION_FOLDER, "RecNamePlugIn$index").orEmpty(),
                recFileName = "",
            )
        }
    }

    private class IniFile(text: String) {
        private val sections = LinkedHashMap<String, LinkedHashMap<String, String>>()

        init {
            var current: LinkedHashMap<String, String>? = null
            for (raw in text.split('\n')) {
                val line = raw.trim()
                if (line.isEmpty() || line.startsWith(';') || line.startsWith('#')) continue
                if (line.startsWith('[') && line.endsWith(']')) {
                    current = LinkedHashMap()
                    sections[line.substring(1, line.length - 1).trim()] = current
                    continue
                }
                val eq = line.indexOf('=')
                if (eq <= 0) continue
                val target = current ?: sections.getOrPut("") { LinkedHashMap() }
                target[line.substring(0, eq).trim()] = line.substring(eq + 1).trim()
            }
        }

        fun sectionNames(): Set<String> = sections.keys

        fun string(section: String, key: String): String? {
            val s = sections[section] ?: return null
            val v = s[key] ?: return null
            return v.trim('"')
        }

        fun intOrNull(section: String, key: String): Int? =
            string(section, key)?.trim()?.toIntOrNull()
    }

    fun decode(bytes: ByteArray): String {
        if (bytes.size >= 2 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xFE.toByte()) {
            return String(bytes, 2, bytes.size - 2, Charset.forName("UTF-16LE"))
        }
        return String(bytes, Charsets.UTF_8)
    }

    @Suppress("unused")
    private fun ByteArray.asStream() = ByteArrayInputStream(this)
}
