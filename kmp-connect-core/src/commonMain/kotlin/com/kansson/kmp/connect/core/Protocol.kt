package com.kansson.kmp.connect.core

import io.ktor.http.HttpStatusCode
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.io.encoding.Base64 as Base64Format

internal const val CONNECT_TIMEOUT_MS = "connect-timeout-ms"
internal const val CONNECT_PROTOCOL_VERSION = "connect-protocol-version"

public enum class Code {
    CANCELED,
    UNKNOWN,
    INVALID_ARGUMENT,
    DEADLINE_EXCEEDED,
    NOT_FOUND,
    ALREADY_EXISTS,
    PERMISSION_DENIED,
    RESOURCE_EXHAUSTED,
    FAILED_PRECONDITION,
    ABORTED,
    OUT_OF_RANGE,
    UNIMPLEMENTED,
    INTERNAL,
    UNAVAILABLE,
    DATA_LOSS,
    UNAUTHENTICATED,
    ;

    internal companion object {
        fun fromHttp(statusCode: HttpStatusCode): Code = when (statusCode) {
            HttpStatusCode.BadRequest -> INTERNAL
            HttpStatusCode.Unauthorized -> UNAUTHENTICATED
            HttpStatusCode.Forbidden -> PERMISSION_DENIED
            HttpStatusCode.NotFound -> UNIMPLEMENTED
            HttpStatusCode.TooManyRequests,
            HttpStatusCode.BadGateway,
            HttpStatusCode.ServiceUnavailable,
            HttpStatusCode.GatewayTimeout,
            -> UNAVAILABLE
            else -> UNKNOWN
        }
    }
}

@Serializable
internal class Error(
    val code: Code? = null,
    val message: String? = null,
    val details: List<Detail> = emptyList(),
) {
    @ExperimentalSerializationApi
    companion object {
        val Json = Json {
            ignoreUnknownKeys = true
            coerceInputValues = true
            decodeEnumsCaseInsensitive = true
        }
    }

    @Serializable
    data class Detail(
        val type: String,
        val value: String,
    ) {
        @ExperimentalEncodingApi
        companion object {
            val Base64 = Base64Format.withPadding(Base64Format.PaddingOption.ABSENT)
        }
    }
}
