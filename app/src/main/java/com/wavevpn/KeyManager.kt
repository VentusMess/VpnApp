package com.wavevpn

import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

object KeyManager {
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    private val SOURCES = listOf(
        "https://raw.githubusercontent.com/igareck/vpn-configs-for-russia/main/BLACK_VLESS_RUS_mobile.txt",
        "https://raw.githubusercontent.com/tiagorrg/vless-checker/main/docs/keys.json",
        "https://raw.githubusercontent.com/soroushmirzaei/telegram-configs-collector/main/protocols/vless"
    )

    fun fetchAllKeys(): List<String> {
        val keys = mutableListOf<String>()
        for (url in SOURCES) {
            try {
                val body = client.newCall(Request.Builder().url(url).build())
                    .execute().body?.string() ?: continue
                keys.addAll(extractAllKeys(body))
                if (keys.size >= 20) break
            } catch (e: Exception) { continue }
        }
        // Reality ключи первыми — они лучше обходят блокировки в РФ
        return keys.sortedByDescending {
            when {
                it.contains("security=reality") -> 2
                it.contains("security=tls") -> 1
                else -> 0
            }
        }
    }

    private fun extractAllKeys(text: String): List<String> {
        val keys = mutableListOf<String>()
        var idx = 0
        while (true) {
            val start = text.indexOf("vless://", idx)
            if (start == -1) break
            val sub = text.substring(start)
            val end = sub.indexOfFirst { it == '"' || it == '\n' || it == ' ' || it == ',' }
            val key = if (end != -1) sub.substring(0, end).trim() else sub.trim()
            if (key.length > 20) keys.add(key)
            idx = start + 8
        }
        return keys
    }
}
