package com.kansson.kmp.connect.core

import com.kansson.kmp.connect.core.wkt.Any
import io.ktor.http.Headers
import kotlinx.serialization.ExperimentalSerializationApi

public sealed class ResponseMessage<Output> private constructor() {
    public abstract val headers: Headers
    public abstract val trailers: Trailers

    public class Success<Output> internal constructor(
        public val message: Output,
        override val headers: Headers,
        override val trailers: Trailers,
    ) : ResponseMessage<Output>()

    public class Failure<Output> internal constructor(
        public val code: Code,
        public val message: String? = null,
        @OptIn(ExperimentalSerializationApi::class)
        public val details: List<Any> = emptyList(),
        override val headers: Headers = Headers.Empty,
        override val trailers: Trailers = Trailers.Empty,
    ) : ResponseMessage<Output>()
}
