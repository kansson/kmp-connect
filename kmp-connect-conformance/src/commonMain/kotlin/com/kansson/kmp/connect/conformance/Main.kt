@file:OptIn(ExperimentalSerializationApi::class)

package com.kansson.kmp.connect.conformance

import connectrpc.conformance.v1.ClientCompatRequest
import io.ktor.utils.io.core.writeFully
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.io.Buffer
import kotlinx.io.asSource
import kotlinx.io.buffered
import kotlinx.io.readByteArray
import kotlinx.io.readTo
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.decodeFromByteArray
import kotlinx.serialization.encodeToByteArray
import kotlinx.serialization.protobuf.ProtoBuf

public fun main(): Unit = System.`in`.asSource().buffered().use { source ->
    while (!source.exhausted()) {
        val length = source.readInt()
        val request = ProtoBuf.decodeFromByteArray<ClientCompatRequest>(source.readByteArray(length))

        runBlocking {
            val job = launch {
                ConformanceTest
                    .run(request)
                    .also {
                        ProtoBuf.encodeToByteArray(it)
                            .let {
                                Buffer().apply {
                                    writeInt(it.size)
                                    writeFully(it)
                                }
                            }
                            .readTo(System.out)
                    }
            }

            if (request.cancel?.cancelTiming is ClientCompatRequest.Cancel.CancelTiming.AfterCloseSendMs) {
                delay(request.cancel.cancelTiming.value!!.toLong())
                job.cancel()
            }
        }
    }
}
