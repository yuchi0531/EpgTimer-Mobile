package com.starrow.epgtimer.data.edcb

import java.io.EOFException
import java.io.IOException
import java.io.InputStream
import java.net.ConnectException
import java.net.InetSocketAddress
import java.net.NoRouteToHostException
import java.net.Socket
import java.net.SocketTimeoutException
import java.net.UnknownHostException

object EDCBConnection {
    const val HEADER_SIZE = 8
    const val MAX_RESPONSE_SIZE = 64 * 1024 * 1024

    private val EMPTY = ByteArray(0)

    private const val MIN_ERR = 0
    private const val MAX_ERR = 0x1000

    private fun looksLikeHttp(head: ByteArray): Boolean {
        val text = String(head, Charsets.US_ASCII)
        return text.startsWith("HTTP/") || text.contains("HTTP/1.")
    }

    fun request(
        host: String,
        port: Int,
        connectTimeoutMs: Int,
        readTimeoutMs: Int,
        cmdId: Int,
        data: ByteArray = EMPTY,
    ): ByteArray {
        if (cmdId ushr 16 != 0) {
            throw EdcbException(ErrCode.CMD_ERR_INVALID_ARG, "cmdID=$cmdId")
        }
        var socket: Socket? = null
        try {
            val s = Socket()
            socket = s
            s.tcpNoDelay = true
            s.connect(InetSocketAddress(host, port), connectTimeoutMs)
            s.soTimeout = readTimeoutMs
            val head = ByteArray(HEADER_SIZE + data.size)
            writeLeInt(head, 0, cmdId)
            writeLeInt(head, 4, data.size)
            data.copyInto(head, HEADER_SIZE)
            val output = s.getOutputStream()
            output.write(head)
            output.flush()
            val input = s.getInputStream()
            val responseHead = readFully(input, HEADER_SIZE)
            if (looksLikeHttp(responseHead)) {
                throw EdcbException(
                    ErrCode.CMD_ERR_DISCONNECT,
                    "$host:$port はEDCBのTCPポートではありません(HTTP応答を受信)",
                )
            }
            val err = readLeInt(responseHead, 0)
            val size = readLeInt(responseHead, 4)
            if (err !in MIN_ERR..MAX_ERR) {
                throw EdcbException(ErrCode.CMD_ERR_DISCONNECT, "応答コードが不正です: $err")
            }
            if (size < 0 || size > MAX_RESPONSE_SIZE) {
                throw EdcbException(ErrCode.CMD_ERR_DISCONNECT, "応答データサイズが異常です: $size")
            }
            val body = if (size == 0) EMPTY else readFully(input, size)
            if (err != ErrCode.CMD_SUCCESS) {
                throw EdcbException(err, "コマンド $cmdId が失敗しました: ${ErrCode.name(err)}")
            }
            return body
        } catch (e: EdcbException) {
            throw e
        } catch (e: SocketTimeoutException) {
            throw EdcbException(ErrCode.CMD_ERR_TIMEOUT, "コマンド $cmdId がタイムアウトしました", e)
        } catch (e: ConnectException) {
            throw EdcbException(ErrCode.CMD_ERR_CONNECT, "$host:$port に接続できませんでした", e)
        } catch (e: UnknownHostException) {
            throw EdcbException(ErrCode.CMD_ERR_CONNECT, "$host を解決できませんでした", e)
        } catch (e: NoRouteToHostException) {
            throw EdcbException(ErrCode.CMD_ERR_CONNECT, "$host:$port に到達できませんでした", e)
        } catch (e: EOFException) {
            throw EdcbException(ErrCode.CMD_ERR_DISCONNECT, "コマンド $cmdId の応答が途中で切断されました", e)
        } finally {
            try {
                socket?.close()
            } catch (_: IOException) {
            }
        }
    }

    private fun readFully(input: InputStream, size: Int): ByteArray {
        val buffer = ByteArray(size)
        var offset = 0
        while (offset < size) {
            val read = input.read(buffer, offset, size - offset)
            if (read < 0) {
                throw EOFException("応答が期待されたバイト数に達する前に終了しました")
            }
            offset += read
        }
        return buffer
    }

    private fun writeLeInt(target: ByteArray, offset: Int, value: Int) {
        target[offset] = value.toByte()
        target[offset + 1] = (value ushr 8).toByte()
        target[offset + 2] = (value ushr 16).toByte()
        target[offset + 3] = (value ushr 24).toByte()
    }

    private fun readLeInt(source: ByteArray, offset: Int): Int =
        (source[offset].toInt() and 0xFF) or
            ((source[offset + 1].toInt() and 0xFF) shl 8) or
            ((source[offset + 2].toInt() and 0xFF) shl 16) or
            ((source[offset + 3].toInt() and 0xFF) shl 24)
}
