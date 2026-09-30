package com.starrow.epgtimer.data.repository

import com.starrow.epgtimer.data.model.RecSettingData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DefaultRecSettingTest {

    @Test
    fun `rec def section is parsed with every field`() {
        val setting = DefaultRecSetting.fromIni(
            """
            [REC_DEF]
            RecMode=2
            Priority=5
            TuijyuuFlag=1
            ServiceMode=17
            PittariFlag=1
            BatFilePath=after.bat --tag
            SuspendMode=2
            RebootFlag=1
            UseMargineFlag=1
            StartMargine=300
            EndMargine=120
            ContinueRec=1
            PartialRec=1

            [REC_DEF_FOLDER]
            Count=2
            1=/mnt/rec/a
            WritePlugIn1=Write_Default.dll
            RecNamePlugIn1=RecName_Default.dll
            2=/mnt/rec/b
            WritePlugIn2=Write_Default.dll
            RecNamePlugIn2=
            """.trimIndent(),
        )!!

        assertEquals(2, setting.recMode)
        assertFalse(setting.isNoRec)
        assertEquals(5, setting.priority)
        assertEquals(1, setting.tuijyuuFlag)
        assertEquals(17, setting.serviceMode)
        assertEquals(1, setting.pittariFlag)
        assertEquals(2, setting.suspendMode)
        assertEquals(1, setting.rebootFlag)
        assertEquals(1, setting.useMargineFlag)
        assertEquals(300, setting.startMargine)
        assertEquals(120, setting.endMargine)
        assertEquals(1, setting.continueRecFlag)
        assertEquals(1, setting.partialRecFlag)
        assertEquals(2, setting.recFolderList.size)
        assertEquals("/mnt/rec/a", setting.recFolderList[0].recFolder)
        assertEquals("RecName_Default.dll", setting.recFolderList[0].recNamePlugIn)
        assertEquals("/mnt/rec/b", setting.recFolderList[1].recFolder)
        assertEquals("", setting.recFolderList[1].recNamePlugIn)
    }

    @Test
    fun `a bat file tag is taken from after the asterisk`() {
        val setting = DefaultRecSetting.fromIni(
            """
            [REC_DEF]
            RecMode=1
            BatFilePath=*mytag
            """.trimIndent(),
        )!!
        assertEquals("mytag", setting.batFilePath)
    }

    @Test
    fun `an invalid default falls back to the recorded mode`() {
        val setting = DefaultRecSetting.fromIni(
            """
            [REC_DEF]
            RecMode=5
            NoRecMode=3
            """.trimIndent(),
        )!!
        assertTrue(setting.isNoRec)
        assertEquals(3, setting.effectiveRecMode)
    }

    @Test
    fun `margins are zeroed when the flag is off`() {
        val setting = DefaultRecSetting.fromIni(
            """
            [REC_DEF]
            RecMode=1
            UseMargineFlag=0
            StartMargine=300
            EndMargine=120
            """.trimIndent(),
        )!!
        assertEquals(0, setting.startMargine)
        assertEquals(0, setting.endMargine)
    }

    @Test
    fun `an ini without a rec def section yields nothing`() {
        assertNull(DefaultRecSetting.fromIni("[SET]\nEnableTCPSrv=1\n"))
        assertNull(DefaultRecSetting.fromIni(""))
    }

    @Test
    fun `the server fallback clears the invalid marker`() {
        val server = RecSettingData.empty().copy(recMode = 5, batFilePath = "*")
        val fixed = DefaultRecSetting.withServerFallback(server)
        assertFalse("サーバ既定の無効ビットが残っている", fixed.isNoRec)
        assertEquals(1, fixed.effectiveRecMode)
        assertEquals("*", fixed.batFilePath)
        assertEquals(1, fixed.recFolderList.size)
    }

    @Test
    fun `the server fallback keeps an already enabled setting`() {
        val server = RecSettingData.empty().copy(recMode = 3, priority = 4)
        val fixed = DefaultRecSetting.withServerFallback(server)
        assertEquals(3, fixed.recMode)
        assertEquals(4, fixed.priority)
    }

    @Test
    fun `utf16 ini with a bom is decoded`() {
        val text = "[REC_DEF]\r\nRecMode=4\r\n"
        val bytes = byteArrayOf(0xFF.toByte(), 0xFE.toByte()) +
            text.toByteArray(Charsets.UTF_16LE)
        val decoded = DefaultRecSetting.decode(bytes)
        assertTrue(decoded.contains("REC_DEF"))
        assertEquals(4, DefaultRecSetting.fromIni(decoded)?.recMode)
    }
}
