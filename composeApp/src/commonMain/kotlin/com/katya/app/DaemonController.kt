package com.katya.app

interface DaemonController {
    fun start()
    fun stop()
}

expect fun createDaemonController(): DaemonController
