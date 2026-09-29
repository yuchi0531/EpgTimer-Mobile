package com.starrow.epgtimer.data.edcb

import com.starrow.epgtimer.data.model.EdcbDateTime
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class CtrlCmdPrimitiveTest {

    @Test
    fun intPrimitivesRoundTrip() {
        val writer = CtrlCmdSerializer()
        writer.u8(0xAB)
        writer.u16(0xCDEF)
        writer.u32(0x89ABCDEF.toInt())
        writer.i32(-42)
        writer.i64(Long.MIN_VALUE + 5)
        writer.i64(0x0123456789ABCDEFL)
        writer.f32(1.5f)
        writer.u32(0xFFFFFFFFL)

        val bytes = writer.toByteArray()
        assertEquals(1 + 2 + 4 + 4 + 8 + 8 + 4 + 4, bytes.size)

        val reader = CtrlCmdDeserializer(bytes)
        assertEquals(0xAB, reader.u8())
        assertEquals(0xCDEF, reader.u16())
        assertEquals(0x89ABCDEF.toInt(), reader.u32())
        assertEquals(-42, reader.i32())
        assertEquals(Long.MIN_VALUE + 5, reader.i64())
        assertEquals(0x0123456789ABCDEFL, reader.i64())
        assertEquals(1.5f, reader.f32())
        assertEquals(0xFFFFFFFFL, reader.u32Long())
        assertFalse(reader.hasRemaining())
    }

    @Test
    fun intPrimitiveLayoutIsLittleEndian() {
        assertArrayEquals(byteArrayOf(0x34, 0x12), with(CtrlCmdSerializer()) {
            u16(0x1234)
            toByteArray()
        })
        assertArrayEquals(byteArrayOf(0x78, 0x56, 0x34, 0x12), with(CtrlCmdSerializer()) {
            u32(0x12345678)
            toByteArray()
        })
        assertArrayEquals(
            byteArrayOf(0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07, 0x08),
            with(CtrlCmdSerializer()) {
                i64(0x0807060504030201L)
                toByteArray()
            },
        )
    }

    @Test
    fun stringLayout() {
        assertArrayEquals(byteArrayOf(6, 0, 0, 0, 0, 0), with(CtrlCmdSerializer()) {
            string("")
            toByteArray()
        })
        assertArrayEquals(
            byteArrayOf(8, 0, 0, 0, 0x41, 0, 0, 0),
            with(CtrlCmdSerializer()) {
                string("A")
                toByteArray()
            },
        )
    }

    @Test
    fun stringRoundTrip() {
        val values = listOf("", "News", "ニュース 21", "表記 🙂", "tab\tand\nnewline", "末尾空白 ", "0123456789")
        val writer = CtrlCmdSerializer()
        for (value in values) {
            writer.string(value)
        }
        val reader = CtrlCmdDeserializer(writer.toByteArray())
        for (value in values) {
            assertEquals(value, reader.string())
        }
        assertFalse(reader.hasRemaining())
    }

    @Test
    fun systemTimeRoundTrip() {
        val value = EdcbDateTime(year = 2024, month = 1, dayOfWeek = 0, day = 15, hour = 10, minute = 30, second = 30, millisecond = 250)
        val writer = CtrlCmdSerializer()
        writer.systemTime(value)
        assertArrayEquals(
            byteArrayOf(
                0xE8.toByte(), 0x07, 0x01, 0x00, 0x00, 0x00, 0x0F, 0x00,
                0x0A, 0x00, 0x1E, 0x00, 0x1E, 0x00, 0xFA.toByte(), 0x00,
            ),
            writer.toByteArray(),
        )
        val reader = CtrlCmdDeserializer(writer.toByteArray())
        assertEquals(value, reader.systemTime())
        assertFalse(reader.hasRemaining())
    }

    @Test
    fun vectorRoundTripAndLayout() {
        val writer = CtrlCmdSerializer()
        writer.vector(listOf(1, 2)) { writer.u32(it) }
        assertArrayEquals(
            byteArrayOf(16, 0, 0, 0, 2, 0, 0, 0, 1, 0, 0, 0, 2, 0, 0, 0),
            writer.toByteArray(),
        )
        val reader = CtrlCmdDeserializer(writer.toByteArray())
        assertEquals(listOf(1, 2), reader.vector { reader.u32() })
        assertFalse(reader.hasRemaining())

        val empty = CtrlCmdSerializer()
        empty.vector(emptyList<Int>()) { empty.u32(it) }
        assertArrayEquals(byteArrayOf(8, 0, 0, 0, 0, 0, 0, 0), empty.toByteArray())
    }

    @Test
    fun nestedStructRoundTrip() {
        val writer = CtrlCmdSerializer()
        writer.struct {
            writer.u16(7)
            writer.struct {
                writer.string("inner")
            }
            writer.vector(listOf("a", "b")) { writer.string(it) }
        }
        val bytes = writer.toByteArray()

        val sizeReader = CtrlCmdDeserializer(bytes)
        assertEquals(bytes.size, sizeReader.u32())

        val reader = CtrlCmdDeserializer(bytes)
        val value = reader.struct {
            val head = reader.u16()
            val inner = reader.struct { reader.string() }
            val items = reader.vector { reader.string() }
            listOf(head.toString(), inner) + items
        }
        assertEquals(listOf("7", "inner", "a", "b"), value)
        assertFalse(reader.hasRemaining())
    }

    @Test
    fun optionalStructRoundTrip() {
        val writer = CtrlCmdSerializer()
        writer.optionalStruct<String>(null) { writer.string(it) }
        writer.optionalStruct("present") { writer.string(it) }
        assertArrayEquals(
            byteArrayOf(4, 0, 0, 0, 0x14, 0, 0, 0) +
                "present".toByteArray(Charsets.UTF_16LE) +
                byteArrayOf(0, 0),
            writer.toByteArray(),
        )
        val reader = CtrlCmdDeserializer(writer.toByteArray())
        assertNull(reader.optionalStruct { reader.string() })
        assertEquals("present", reader.optionalStruct { reader.string() })
        assertFalse(reader.hasRemaining())
    }

    @Test
    fun trailingBytesInsideStructAreSkipped() {
        val writer = CtrlCmdSerializer()
        writer.struct {
            writer.u32(9)
            writer.u32(0)
            writer.u32(0)
        }
        val reader = CtrlCmdDeserializer(writer.toByteArray())
        assertEquals(9, reader.struct { reader.u32() })
        assertFalse(reader.hasRemaining())
    }

    @Test
    fun truncatedDataThrowsEdcbException() {
        val writer = CtrlCmdSerializer()
        writer.struct {
            writer.i64(123456789012345L)
        }
        val bytes = writer.toByteArray().copyOf(6)
        val reader = CtrlCmdDeserializer(bytes)
        val error = assertThrows(EdcbException::class.java) {
            reader.struct { reader.i64() }
        }
        assertEquals(ErrCode.CMD_ERR_DISCONNECT, error.code)
    }

    @Test
    fun structSizeBeyondDataThrows() {
        val writer = CtrlCmdSerializer()
        writer.struct { writer.u32(1) }
        val bytes = writer.toByteArray()
        bytes[0] = 0x40
        val reader = CtrlCmdDeserializer(bytes)
        assertThrows(EdcbException::class.java) {
            reader.struct { reader.u32() }
        }
    }

    @Test
    fun brokenStringTerminatorThrows() {
        val writer = CtrlCmdSerializer()
        writer.string("A")
        val bytes = writer.toByteArray()
        bytes[7] = 0x11
        val reader = CtrlCmdDeserializer(bytes)
        val error = assertThrows(EdcbException::class.java) { reader.string() }
        assertEquals(ErrCode.CMD_ERR, error.code)
    }

    @Test
    fun oddSizedStringThrows() {
        val reader = CtrlCmdDeserializer(byteArrayOf(7, 0, 0, 0, 0x41, 0, 0))
        val error = assertThrows(EdcbException::class.java) { reader.string() }
        assertEquals(ErrCode.CMD_ERR, error.code)
    }

    @Test
    fun vectorCountBeyondDataThrows() {
        val bytes = byteArrayOf(
            12, 0, 0, 0,
            0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0x7F,
            1, 0, 0, 0,
        )
        val reader = CtrlCmdDeserializer(bytes)
        val error = assertThrows(EdcbException::class.java) { reader.vector { reader.u32() } }
        assertEquals(ErrCode.CMD_ERR, error.code)
    }

    @Test
    fun malformedStructSizeThrows() {
        val reader = CtrlCmdDeserializer(byteArrayOf(2, 0, 0, 0))
        val error = assertThrows(EdcbException::class.java) { reader.struct { reader.u32() } }
        assertEquals(ErrCode.CMD_ERR, error.code)
    }

    @Test
    fun versionGatesStructFields() {
        val writer = CtrlCmdSerializer(2)
        writer.struct {
            writer.u8(1)
            writer.vector(listOf(3L)) { writer.i64(it) }
        }
        val bytes = writer.toByteArray()

        val lower = CtrlCmdDeserializer(bytes, 1)
        assertEquals(1, lower.struct { lower.u8() })
        assertFalse(lower.hasRemaining())

        val upper = CtrlCmdDeserializer(bytes, 2)
        upper.struct {
            assertEquals(1, upper.u8())
            assertEquals(listOf(3L), upper.vector { upper.i64() })
        }
    }
}
