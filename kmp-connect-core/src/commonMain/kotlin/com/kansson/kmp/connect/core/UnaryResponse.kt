package com.kansson.kmp.connect.core

import io.ktor.http.Headers

public sealed class UnaryResponse<Output> private constructor() {
    public abstract val headers: Headers
    public abstract val trailers: Trailers

    public class Success<Output> internal constructor(
        public val message: Output,
        override val headers: Headers,
        override val trailers: Trailers,
    ) : UnaryResponse<Output>()

    public class Failure<Output> internal constructor(
        public val code: Code,
        public val message: String? = null,
        public val details: List<Any> = emptyList(),
        override val headers: Headers = Headers.Empty,
        override val trailers: Trailers = Trailers.Empty,
    ) : UnaryResponse<Output>()
}
