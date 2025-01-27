package com.kansson.kmp.connect.core.wkt

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.ProtoNumber

@ExperimentalSerializationApi
@Serializable
public data class Any(
    @ProtoNumber(1)
    public val typeUrl: String = "",
    @ProtoNumber(2)
    public val value: ByteArray = byteArrayOf(),
)
