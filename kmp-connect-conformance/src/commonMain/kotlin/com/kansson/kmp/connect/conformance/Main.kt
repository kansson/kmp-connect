@file:OptIn(ExperimentalSerializationApi::class)

package com.kansson.kmp.connect.conformance

import connectrpc.conformance.v1.ClientCompatRequest
import connectrpc.conformance.v1.ClientCompatResponse
import connectrpc.conformance.v1.ClientErrorResult
import io.ktor.utils.io.core.writeFully
import kotlinx.io.Buffer
import kotlinx.io.asSource
import kotlinx.io.buffered
import kotlinx.io.readByteArray
import kotlinx.io.readTo
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.decodeFromByteArray
import kotlinx.serialization.encodeToByteArray
import kotlinx.serialization.protobuf.ProtoBuf

public fun main() {
    System.`in`.asSource().buffered().use {
        while (!it.exhausted()) {
            val length = it.readInt()
            val request = ProtoBuf.decodeFromByteArray<ClientCompatRequest>(it.readByteArray(length))

            val response = ClientCompatResponse(
                testName = request.testName,
                result = ClientCompatResponse.Result.Error(
                    value = ClientErrorResult(
                        message = "Not implemented",
                    ),
                ),
            )

            val byteArray = ProtoBuf.encodeToByteArray(response)
            Buffer().apply {
                writeInt(byteArray.size)
                writeFully(byteArray)
            }.readTo(System.out)
        }
    }
}
