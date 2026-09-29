package com.starrow.epgtimer.data.guide

enum class LogoImageFormat { PNG, BMP, UNKNOWN }

object LogoImageDetector {

    private val PNG_MAGIC = byteArrayOf(
        0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A,
    )

    fun detect(data: ByteArray): LogoImageFormat {
        if (data.size < 8) return LogoImageFormat.UNKNOWN
        if (matches(data, PNG_MAGIC, 0)) return LogoImageFormat.PNG
        if (matches(data, byteArrayOf('B'.code.toByte(), 'M'.code.toByte()), 0)) {
            if (data.size < 18) return LogoImageFormat.UNKNOWN
            val fileSize = readU32(data, 2)
            val reserved = readU32(data, 6)
            val pixelOffset = readU32(data, 10)
            val dibSize = readU32(data, 14)
            val valid = dibSize in 12..124 &&
                reserved == 0 &&
                fileSize == data.size &&
                pixelOffset in 14..data.size
            return if (valid) LogoImageFormat.BMP else LogoImageFormat.UNKNOWN
        }
        return LogoImageFormat.UNKNOWN
    }

    private fun matches(data: ByteArray, magic: ByteArray, offset: Int): Boolean {
        if (data.size < offset + magic.size) return false
        for (i in magic.indices) {
            if (data[offset + i] != magic[i]) return false
        }
        return true
    }

    private fun readU32(data: ByteArray, offset: Int): Int =
        (data[offset].toInt() and 0xFF) or
            ((data[offset + 1].toInt() and 0xFF) shl 8) or
            ((data[offset + 2].toInt() and 0xFF) shl 16) or
            ((data[offset + 3].toInt() and 0xFF) shl 24)
}
