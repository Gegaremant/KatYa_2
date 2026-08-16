package com.katya.app

object SandboxSessions {
    const val DEFAULT = "default"

    fun isPersistable(sessionId: String): Boolean {
        return sessionId != DEFAULT
    }
}