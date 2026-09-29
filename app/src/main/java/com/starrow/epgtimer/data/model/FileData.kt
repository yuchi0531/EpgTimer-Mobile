package com.starrow.epgtimer.data.model

data class FileData(
    val name: String,
    val status: Int,
    val data: ByteArray,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is FileData) return false
        return name == other.name && status == other.status && data.contentEquals(other.data)
    }

    override fun hashCode(): Int {
        var result = name.hashCode()
        result = 31 * result + status
        result = 31 * result + data.contentHashCode()
        return result
    }
}
