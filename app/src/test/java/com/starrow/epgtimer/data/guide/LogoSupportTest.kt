package com.starrow.epgtimer.data.repository

import com.starrow.epgtimer.data.guide.ChSet5Parser
import com.starrow.epgtimer.data.guide.LogoImageDetector
import com.starrow.epgtimer.data.guide.LogoImageFormat
import com.starrow.epgtimer.data.guide.LogoIndex
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LogoSupportTest {

    @Test
    fun `ch set5 lines are parsed into service entries`() {
        val text = buildString {
            appendLine(";ChSet5.txt")
            appendLine("ＮＨＫ総合\tNHK\t1\t14080\t1024\t1\t0\t1\t1\t1")
            appendLine("ＢＳ朝日１\tBS\t4\t16400\t151\t1\t0\t1\t1\t0")
        }
        val entries = ChSet5Parser.parse(text)
        assertEquals(2, entries.size)
        assertEquals("ＮＨＫ総合", entries[0].serviceName)
        assertEquals(1, entries[0].onid)
        assertEquals(14080, entries[0].tsid)
        assertEquals(1024, entries[0].sid)
        assertEquals(1, entries[0].remoconId)
        assertEquals("ＢＳ朝日１", entries[1].serviceName)
        assertEquals(4, entries[1].onid)
    }

    @Test
    fun `malformed ch set5 lines are skipped instead of crashing`() {
        val text = buildString {
            appendLine("　")
            appendLine(";comment")
            appendLine("broken\tline")
            appendLine("ok\tnet\t4\t16400\t151\t1\t0\t1\t1\t0")
        }
        val entries = ChSet5Parser.parse(text)
        assertEquals(1, entries.size)
        assertEquals("ok", entries[0].serviceName)
    }

    @Test
    fun `logo id map is parsed from the ini format`() {
        val ini = "00000004=16\r\n00007fff=511\r\ninvalid=x\r\n"
        val map = LogoIndex.parseLogoIdMap(ini)
        assertEquals(16, map[0x00000004L])
        assertEquals(511, map[0x00007ffFL])
        assertEquals(2, map.size)
    }

    @Test
    fun `logo id map decodes hexadecimal keys`() {
        val map = LogoIndex.parseLogoIdMap("00040064=1\r\n")
        assertEquals(setOf(0x0004_0064L), map.keys)
        assertEquals(1, map[0x0004_0064L])
    }

    @Test
    fun `logo file name resolution prefers the highest quality type`() {
        val names = listOf(
            "0004_010_00.png",
            "0004_010_02.png",
            "0004_010_05.png",
        )
        val resolved = LogoIndex.resolveFileName((0x0004 shl 16) or 0x010, names)
        assertEquals("LogoData\\0004_010_05.png", resolved)
    }

    @Test
    fun `logo file name resolution finds a matching type`() {
        val names = listOf("0004_010_00.png", "0004_010_02.png")
        val resolved = LogoIndex.resolveFileName((0x0004 shl 16) or 0x010, names)
        assertEquals("LogoData\\0004_010_02.png", resolved)
    }

    @Test
    fun `missing logo file name resolves to null`() {
        assertNull(LogoIndex.resolveFileName((0x0004 shl 16) or 0x010, listOf("1111_001_00.png")))
    }

    @Test
    fun `png and bmp are detected by their magic bytes`() {
        val png = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 0)
        assertEquals(LogoImageFormat.PNG, LogoImageDetector.detect(png))
        assertEquals(LogoImageFormat.UNKNOWN, LogoImageDetector.detect(byteArrayOf(1, 2, 3)))
        assertEquals(LogoImageFormat.UNKNOWN, LogoImageDetector.detect(ByteArray(0)))
    }

    @Test
    fun `a structurally valid bitmap header is accepted`() {
        val size = 26
        val data = ByteArray(size)
        data[0] = 'B'.code.toByte()
        data[1] = 'M'.code.toByte()
        writeU32(data, 2, size)
        writeU32(data, 6, 0)
        writeU32(data, 10, 14)
        writeU32(data, 14, 12)
        assertEquals(LogoImageFormat.BMP, LogoImageDetector.detect(data))
    }

    @Test
    fun `a broken bitmap header is rejected`() {
        val data = ByteArray(26)
        data[0] = 'B'.code.toByte()
        data[1] = 'M'.code.toByte()
        writeU32(data, 2, 999)
        writeU32(data, 6, 0)
        writeU32(data, 10, 14)
        writeU32(data, 14, 12)
        assertEquals(LogoImageFormat.UNKNOWN, LogoImageDetector.detect(data))
    }

    private fun writeU32(data: ByteArray, offset: Int, value: Int) {
        data[offset] = value.toByte()
        data[offset + 1] = (value ushr 8).toByte()
        data[offset + 2] = (value ushr 16).toByte()
        data[offset + 3] = (value ushr 24).toByte()
    }

    @Test
    fun `empty logo data never throws`() {
        assertTrue(LogoIndex.decodeText(ByteArray(0)).isEmpty())
        assertTrue(LogoIndex.parseFileNames("").isEmpty())
    }
}
