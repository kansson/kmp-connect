package com.kansson.kmp.connect.protocgen

import com.google.protobuf.compiler.PluginProtos

public fun main() {
    val request = PluginProtos.CodeGeneratorRequest.parseFrom(System.`in`)
    CodeGenerator
        .run(request)
        .also {
            it.writeTo(System.out)
        }
}
