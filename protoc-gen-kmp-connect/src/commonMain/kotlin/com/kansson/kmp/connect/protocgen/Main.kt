package com.kansson.kmp.connect.protocgen

import com.google.protobuf.compiler.PluginProtos

public fun main() {
    val request = PluginProtos.CodeGeneratorRequest.parseFrom(System.`in`)
    val response = CodeGenerator.run(request)

    response.writeTo(System.out)
}
