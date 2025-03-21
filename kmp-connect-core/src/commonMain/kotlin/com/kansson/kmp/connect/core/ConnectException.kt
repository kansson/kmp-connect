package com.kansson.kmp.connect.core

public class ConnectException internal constructor(
    override val message: String?,
    override val cause: Throwable?,
) : RuntimeException()
