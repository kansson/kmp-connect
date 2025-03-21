package com.kansson.kmp.connect

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform