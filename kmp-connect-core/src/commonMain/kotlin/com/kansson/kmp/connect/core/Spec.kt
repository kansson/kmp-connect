package com.kansson.kmp.connect.core

import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.SerializationStrategy

public class Spec<Input : Any, Output : Any> (
    public val procedure: String,
    public val type: Type,
    public val idempotency: Idempotency,
    public val serializer: SerializationStrategy<Input>,
    public val deserializer: DeserializationStrategy<Output>,
) {
    public enum class Type {
        UNARY,
        CLIENT,
        SERVER,
        BIDIRECTIONAL,
    }

    public enum class Idempotency {
        UNKNOWN,
        NO_SIDE_EFFECTS,
        IDEMPOTENT,
    }
}
