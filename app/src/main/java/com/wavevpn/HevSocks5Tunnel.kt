package com.wavevpn

object HevSocks5Tunnel {
    init {
        try {
            System.loadLibrary("hev")
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    external fun start(configPath: String, tunFd: Int): Int
    external fun stop()

    fun isAvailable(): Boolean {
        return try {
            System.loadLibrary("hev")
            true
        } catch (e: Exception) {
            false
        }
    }
}
