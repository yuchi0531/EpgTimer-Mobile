package com.starrow.epgtimer.data.edcb

import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.io.IOException
import java.io.InputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.time.LocalDateTime

class EDCBConnectionTest {

    private var server: ServerSocket? = null

    @After
    fun tearDown() {
        try {
            server?.close()
        } catch (_: IOException) {
        }
    }

    private fun statusResponseBody(): ByteArray {
        val time = byteArrayOf(
            0xE8.toByte(), 0x07, 0x01, 0x00, 0x02, 0x00, 0x02, 0x00,
            0x03, 0x00, 0x04, 0x00, 0x05, 0x00, 0xA6.toByte(), 0x02,
        )
        val emptyString = byteArrayOf(6, 0, 0, 0, 0, 0)
        return byteArrayOf(0x02, 0x00) +
            byteArrayOf(0x36, 0, 0, 0) +
            byteArrayOf(0x64, 0, 0, 0) +
            time +
            byteArrayOf(0, 0, 0, 0) +
            byteArrayOf(1, 0, 0, 0) +
            byteArrayOf(0x2A, 0, 0, 0) +
            emptyString + emptyString + emptyString
    }

    private fun readFully(input: InputStream, size: Int): ByteArray {
        val buffer = ByteArray(size)
        var position = 0
        while (position < size) {
            val count = input.read(buffer, position, size - position)
            if (count < 0) {
                throw IOException("connection closed")
            }
            position += count
        }
        return buffer
    }

    private fun leInt(source: ByteArray, offset: Int): Int =
        (source[offset].toInt() and 0xFF) or
            ((source[offset + 1].toInt() and 0xFF) shl 8) or
            ((source[offset + 2].toInt() and 0xFF) shl 16) or
            ((source[offset + 3].toInt() and 0xFF) shl 24)

    private fun writeLeInt(target: ByteArray, offset: Int, value: Int) {
        target[offset] = value.toByte()
        target[offset + 1] = (value ushr 8).toByte()
        target[offset + 2] = (value ushr 16).toByte()
        target[offset + 3] = (value ushr 24).toByte()
    }

    private fun startServer(handler: (cmdId: Int, data: ByteArray) -> Pair<Int, ByteArray>): Int {
        val socket = ServerSocket(0, 8, InetAddress.getByName("127.0.0.1"))
        server = socket
        val thread = Thread {
            try {
                socket.accept().use { client ->
                    val input = client.getInputStream()
                    val head = readFully(input, 8)
                    val cmdId = leInt(head, 0)
                    val data = readFully(input, leInt(head, 4))
                    val (err, body) = handler(cmdId, data)
                    val output = client.getOutputStream()
                    val responseHead = ByteArray(8)
                    writeLeInt(responseHead, 0, err)
                    writeLeInt(responseHead, 4, body.size)
                    output.write(responseHead)
                    output.write(body)
                    output.flush()
                }
            } catch (_: IOException) {
            }
        }
        thread.isDaemon = true
        thread.start()
        return socket.localPort
    }

    @Test
    fun statusRequestIsFramedAndParsed() {
        var receivedCmd = -1
        var receivedData: ByteArray? = null
        val port = startServer { cmdId, data ->
            receivedCmd = cmdId
            receivedData = data
            ErrCode.CMD_SUCCESS to statusResponseBody()
        }

        val send = CtrlCmdSerializer().apply {
            u16(0)
            u32(0)
        }
        val body = EDCBConnection.request(
            "127.0.0.1", port, 2000, 2000, CtrlCmd.CMD_EPG_SRV_GET_STATUS_NOTIFY2, send.toByteArray(),
        )
        assertArrayEquals(statusResponseBody(), body)
        assertEquals(CtrlCmd.CMD_EPG_SRV_GET_STATUS_NOTIFY2, receivedCmd)
        assertArrayEquals(byteArrayOf(0, 0, 0, 0, 0, 0), receivedData)

        val reader = CtrlCmdDeserializer(body)
        assertEquals(2, reader.readVersion())
        val status = reader.readServerStatus()
        assertEquals(100, status.notifyId)
        assertEquals(0, status.srvStatus)
        assertEquals(0x2A, status.notifyCount)
    }

    @Test
    fun highBitsInCmdIdAreRejected() {
        val error = assertThrows(EdcbException::class.java) {
            EDCBConnection.request("127.0.0.1", 4510, 1000, 1000, 0x00010000)
        }
        assertEquals(ErrCode.CMD_ERR_INVALID_ARG, error.code)
    }

    @Test
    fun serverErrorCodeBecomesEdcbException() {
        val port = startServer { _, _ -> ErrCode.CMD_ERR_BUSY to ByteArray(0) }
        val error = assertThrows(EdcbException::class.java) {
            EDCBConnection.request("127.0.0.1", port, 2000, 2000, CtrlCmd.CMD_EPG_SRV_ENUM_SERVICE)
        }
        assertEquals(ErrCode.CMD_ERR_BUSY, error.code)
    }

    @Test
    fun readTimeoutBecomesTimeoutError() {
        val socket = ServerSocket(0, 8, InetAddress.getByName("127.0.0.1"))
        server = socket
        val thread = Thread {
            try {
                socket.accept().use { client ->
                    Thread.sleep(3000)
                    client.getInputStream().read()
                }
            } catch (_: IOException) {
            } catch (_: InterruptedException) {
            }
        }
        thread.isDaemon = true
        thread.start()

        val error = assertThrows(EdcbException::class.java) {
            EDCBConnection.request(
                "127.0.0.1", socket.localPort, 2000, 300, CtrlCmd.CMD_EPG_SRV_ENUM_SERVICE,
            )
        }
        assertEquals(ErrCode.CMD_ERR_TIMEOUT, error.code)
    }

    @Test
    fun connectRefusedBecomesConnectError() {
        val probe = ServerSocket(0)
        val port = probe.localPort
        probe.close()

        val error = assertThrows(EdcbException::class.java) {
            EDCBConnection.request("127.0.0.1", port, 1000, 1000, CtrlCmd.CMD_EPG_SRV_ENUM_SERVICE)
        }
        assertEquals(ErrCode.CMD_ERR_CONNECT, error.code)
    }

    @Test
    fun clientSendsEnumerateServiceWithoutPayload() {
        var receivedCmd = -1
        var receivedSize = -1
        val port = startServer { cmdId, data ->
            receivedCmd = cmdId
            receivedSize = data.size
            ErrCode.CMD_SUCCESS to byteArrayOf(8, 0, 0, 0, 0, 0, 0, 0)
        }
        val client = EpgTimerTcpClient("127.0.0.1", port, 2000, 2000)
        val services = runBlocking { client.enumService() }
        assertEquals(CtrlCmd.CMD_EPG_SRV_ENUM_SERVICE, receivedCmd)
        assertEquals(0, receivedSize)
        assertEquals(0, services.size)
    }

    @Test
    fun clientSendsStatusPayloadAsWordZeroAndCountZero() {
        var payload: ByteArray? = null
        val port = startServer { _, data ->
            payload = data
            ErrCode.CMD_SUCCESS to statusResponseBody()
        }
        val client = EpgTimerTcpClient("127.0.0.1", port, 2000, 2000)
        val status = runBlocking { client.getStatus() }
        assertArrayEquals(byteArrayOf(0, 0, 0, 0, 0, 0), payload)
        assertEquals(100, status.notifyId)
        assertEquals(LocalDateTime.of(2024, 1, 2, 3, 4, 5, 678_000_000), status.time.toLocalDateTime())
    }

    @Test
    fun clientPropagatesServerBusyError() {
        val port = startServer { _, _ -> ErrCode.CMD_ERR_BUSY to ByteArray(0) }
        val client = EpgTimerTcpClient("127.0.0.1", port, 2000, 2000)
        val error = assertThrows(EdcbException::class.java) {
            runBlocking { client.enumService() }
        }
        assertEquals(ErrCode.CMD_ERR_BUSY, error.code)
    }
}
