package com.example.expenditure

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform