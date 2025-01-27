package com.kansson.kmp.connect.conformance

import com.kansson.kmp.connect.core.ConnectException
import com.kansson.kmp.connect.core.ConnectTransport
import com.kansson.kmp.connect.core.ProtoBufCodec
import com.kansson.kmp.connect.core.ResponseMessage
import com.kansson.kmp.connect.core.Spec
import connectrpc.conformance.v1.ClientCompatRequest
import connectrpc.conformance.v1.ClientCompatResponse
import connectrpc.conformance.v1.ClientErrorResult
import connectrpc.conformance.v1.ClientResponseResult
import connectrpc.conformance.v1.ConformancePayload
import connectrpc.conformance.v1.Error
import connectrpc.conformance.v1.Header
import connectrpc.conformance.v1.UnaryRequest
import connectrpc.conformance.v1.UnaryResponse
import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.defaultRequest
import io.ktor.http.headers
import kotlinx.serialization.decodeFromByteArray
import kotlinx.serialization.protobuf.ProtoBuf
import kotlin.time.Duration.Companion.milliseconds
import connectrpc.conformance.v1.Code as ConformanceCode

public object ConformanceTest {
    public suspend fun run(request: ClientCompatRequest): ClientCompatResponse {
        val transport = ConnectTransport(
            httpClient = HttpClient {
                defaultRequest {
                    host = request.host
                    port = request.port.toInt()
                }
                install(HttpTimeout)
            },
            codec = ProtoBufCodec,
        )

        return try {
            val response = transport.unary(
                input = ProtoBuf.decodeFromByteArray<UnaryRequest>(request.requestMessages.first().value),
                headers = headers {
                    append("x-test-case-name", request.testName)
                    request.requestHeaders.forEach { header ->
                        header.value.forEach {
                            append(header.name, it)
                        }
                    }
                },
                spec = Spec(
                    procedure = "${request.service}/${request.method}",
                    type = Spec.Type.UNARY,
                    idempotency = when (request.useGetHttpMethod) {
                        true -> Spec.Idempotency.NO_SIDE_EFFECTS
                        else -> Spec.Idempotency.IDEMPOTENT
                    },
                    serializer = UnaryRequest.serializer(),
                    deserializer = UnaryResponse.serializer(),
                ),
                timeout = request.timeoutMs?.toInt()?.milliseconds,
            )

            val headers = response.headers.entries().map { Header(it.key, it.value) }
            val trailers = response.trailers.entries().map { Header(it.key, it.value) }

            when (response) {
                is ResponseMessage.Success -> ClientCompatResponse(
                    testName = request.testName,
                    result = ClientCompatResponse.Result.Response(
                        value = ClientResponseResult(
                            responseHeaders = headers,
                            payloads = listOf(response.message.payload ?: ConformancePayload()),
                            responseTrailers = trailers,
                        ),
                    ),
                )

                is ResponseMessage.Failure -> ClientCompatResponse(
                    testName = request.testName,
                    result = ClientCompatResponse.Result.Response(
                        value = ClientResponseResult(
                            responseHeaders = headers,
                            error = Error(
                                code = ConformanceCode.valueOf("CODE_${response.code}"),
                                message = response.message,
                                details = response.details,
                            ),
                            responseTrailers = trailers,
                        ),
                    ),
                )
            }
        } catch (exception: ConnectException) {
            ClientCompatResponse(
                testName = request.testName,
                result = ClientCompatResponse.Result.Error(
                    value = ClientErrorResult(
                        message = exception.message.toString(),
                    ),
                ),
            )
        }
    }
}
