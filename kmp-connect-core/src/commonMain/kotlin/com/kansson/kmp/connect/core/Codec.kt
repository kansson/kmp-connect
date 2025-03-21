package com.kansson.kmp.connect.core

import io.ktor.http.ContentType
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerializationStrategy
import kotlinx.serialization.protobuf.ProtoBuf

public abstract class Codec(
    public val name: String,
    public val contentType: ContentType = ContentType.parse("application/$name"),
) {
    public abstract fun <Input : Any> serialize(
        serializer: SerializationStrategy<Input>,
        input: Input,
    ): ByteArray

    public abstract fun <Output : Any> deserialize(
        deserializer: DeserializationStrategy<Output>,
        output: ByteArray,
    ): Output
}

@ExperimentalSerializationApi
public object ProtoBufCodec : Codec("proto") {
    override fun <Input : Any> serialize(
        serializer: SerializationStrategy<Input>,
        input: Input,
    ): ByteArray = ProtoBuf.encodeToByteArray(serializer, input)

    override fun <Output : Any>deserialize(
        deserializer: DeserializationStrategy<Output>,
        output: ByteArray,
    ): Output = ProtoBuf.decodeFromByteArray(deserializer, output)
}
