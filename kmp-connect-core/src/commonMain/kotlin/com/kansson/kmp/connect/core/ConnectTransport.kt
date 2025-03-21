package com.kansson.kmp.connect.core

import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.client.plugins.ResponseException
import io.ktor.client.plugins.timeout
import io.ktor.client.request.header
import io.ktor.client.request.headers
import io.ktor.client.request.request
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsBytes
import io.ktor.client.statement.bodyAsText
import io.ktor.http.Headers
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.appendPathSegments
import io.ktor.http.contentType
import io.ktor.util.flattenForEach
import kotlinx.io.IOException
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerializationException
import kotlin.coroutines.cancellation.CancellationException
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.time.Duration

public typealias Trailers = Headers

@ExperimentalSerializationApi
@ExperimentalEncodingApi
public class ConnectTransport(
    private val httpClient: HttpClient,
    private val codec: Codec,
) {
    public suspend fun <Input : Any, Output : Any> unary(
        input: Input,
        spec: Spec<Input, Output>,
        headers: Headers,
        timeout: Duration?,
    ): UnaryResponse<Output> = try {
        val message = codec.serialize(spec.serializer, input)
        val response = httpClient.request {
            timeout?.let {
                header(CONNECT_TIMEOUT_MS, it.inWholeMilliseconds.toString())
                timeout {
                    requestTimeoutMillis = it.inWholeMilliseconds
                }
            }

            headers { appendAll(headers) }
            url { appendPathSegments(spec.procedure) }

            when (spec.idempotency) {
                Spec.Idempotency.NO_SIDE_EFFECTS -> {
                    method = HttpMethod.Get
                    url {
                        with(parameters) {
                            append("message", Base64.encode(message))
                            append("encoding", codec.name)
                            append("base64", "1")
                            append("connect", "v1")
                        }
                    }
                }

                else -> {
                    method = HttpMethod.Post
                    contentType(codec.contentType)
                    header(CONNECT_PROTOCOL_VERSION, "1")
                    setBody(message)
                }
            }
        }

        val responseHeaders = response.headers.format()
        val trailers = response.headers.format(trailers = true)

        when (response.status) {
            HttpStatusCode.OK -> {
                if (response.contentType() != codec.contentType) {
                    UnaryResponse.Failure(
                        code = if (response.contentType()?.contentType == "application") {
                            Code.INTERNAL
                        } else {
                            Code.UNKNOWN
                        },
                        headers = responseHeaders,
                        trailers = trailers,
                    )
                } else {
                    val output = codec.deserialize(spec.deserializer, response.bodyAsBytes())
                    UnaryResponse.Success(
                        message = output,
                        headers = responseHeaders,
                        trailers = trailers,
                    )
                }
            }

            else -> {
                val error = try {
                    Error.Json.decodeFromString<Error>(response.bodyAsText())
                } catch (_: SerializationException) {
                    null
                }

                UnaryResponse.Failure(
                    code = error?.code ?: Code.fromHttp(response.status),
                    message = error?.message,
                    details = error?.details.orEmpty(),
                    headers = responseHeaders,
                    trailers = trailers,
                )
            }
        }
    } catch (expected: Exception) {
        val code = when (expected) {
            is CancellationException -> Code.CANCELED
            is HttpRequestTimeoutException -> Code.DEADLINE_EXCEEDED
            is SerializationException,
            is ResponseException,
            is IOException,
            -> Code.INTERNAL
            else -> throw ConnectException(
                message = expected.message,
                cause = expected,
            )
        }

        UnaryResponse.Failure(
            code = code,
            message = expected.message,
        )
    }

    private fun Headers.format(trailers: Boolean = false) = Headers.build {
        flattenForEach { key, value ->
            when {
                trailers && key.startsWith("trailer-") -> append(key.substringAfter("trailer-"), value)
                else -> append(key, value)
            }
        }
    }
}
