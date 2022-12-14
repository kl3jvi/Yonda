package com.kl3jvi.nb_api.connection

import com.kl3jvi.nb_api.command.RawResponse
import com.kl3jvi.nb_api.command.ScooterCommand
import com.kl3jvi.nb_api.command.ScooterResponse
import com.kl3jvi.nb_api.command.util.hexToBytes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.io.OutputStream
import java.lang.Thread.sleep
import kotlin.system.measureTimeMillis

class DeviceConnection(
    private val inputStream: InputStream,
    private val outputStream: OutputStream
) {
    private val rawResponseCache = mutableMapOf<ScooterCommand, RawResponse>()

    /**
     * Used run blocking because we are sending commands one after the other and we need to block
     * the thread we are currently working.
     */
    suspend fun run(
        command: ScooterCommand,
        useCache: Boolean = false,
        delayTime: Long = 0
    ): ScooterResponse = runBlocking {
        val deviceRawResponse =
            if (useCache && rawResponseCache[command] != null) {
                rawResponseCache.getValue(command)
            } else {
                runCommand(command, delayTime).also {
                    // Save response to cache
                    if (useCache) {
                        rawResponseCache[command] = it
                    }
                }
            }
        command.handleResponse(deviceRawResponse)
    }

    suspend fun runCommand(command: ScooterCommand, delayTime: Long): RawResponse {
        var rawData = ""
        val elapsedTime = measureTimeMillis {
            sendCommand(command, delayTime)
            rawData = readRawData()
        }
        return RawResponse(rawData, elapsedTime)
    }

    private suspend fun sendCommand(command: ScooterCommand, delayTime: Long) {
        withContext(Dispatchers.IO) {
            outputStream.write("${command.getRequestString().hexToBytes()}\r".toByteArray())
            outputStream.flush()
            if (delayTime > 0) {
                sleep(delayTime)
            }
        }
    }

    private suspend fun readRawData(): String = runBlocking {
        var b: Byte
        var c: Char
        val res = StringBuffer()

        withContext(Dispatchers.IO) {
            /* Reading the input stream until it reaches the end of the stream or the character '>' is reached. */
            while (inputStream.available() > 0) {
                b = inputStream.read().toByte()
                if (b < 0) {
                    break
                }
                c = b.toInt().toChar()
                if (c == '>') {
                    break
                }
                res.append(c)
            }
        }
        res.toString()
    }
}
