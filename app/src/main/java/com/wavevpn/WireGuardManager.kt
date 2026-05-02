package com.wavevpn

import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

object WireGuardManager {
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    // Источники бесплатных WireGuard конфигов
    private val SOURCES = listOf(
        "https://raw.githubusercontent.com/freewireguard/wireguard-configs/main/configs.txt",
        "https://raw.githubusercontent.com/shahind/WireGuard-Configs/main/configs.txt",
        "https://raw.githubusercontent.com/ircfspace/wireguard/main/configs.txt"
    )

    data class WgConfig(
        val privateKey: String,
        val publicKey: String,
        val endpoint: String,
        val address: String,
        val dns: String = "1.1.1.1"
    )

    fun fetchConfigs(): List<WgConfig> {
        val configs = mutableListOf<WgConfig>()
        for (url in SOURCES) {
            try {
                val body = client.newCall(Request.Builder().url(url).build())
                    .execute().body?.string() ?: continue
                configs.addAll(parseConfigs(body))
                if (configs.size >= 5) break
            } catch (e: Exception) { continue }
        }
        return configs
    }

    private fun parseConfigs(text: String): List<WgConfig> {
        val configs = mutableListOf<WgConfig>()
        val blocks = text.split("[Interface]").drop(1)
        for (block in blocks) {
            try {
                val privateKey = Regex("PrivateKey\\s*=\\s*(.+)").find(block)?.groupValues?.get(1)?.trim() ?: continue
                val address = Regex("Address\\s*=\\s*(.+)").find(block)?.groupValues?.get(1)?.trim() ?: continue
                val dns = Regex("DNS\\s*=\\s*(.+)").find(block)?.groupValues?.get(1)?.trim() ?: "1.1.1.1"
                val peerBlock = block.substringAfter("[Peer]", "")
                val publicKey = Regex("PublicKey\\s*=\\s*(.+)").find(peerBlock)?.groupValues?.get(1)?.trim() ?: continue
                val endpoint = Regex("Endpoint\\s*=\\s*(.+)").find(peerBlock)?.groupValues?.get(1)?.trim() ?: continue
                configs.add(WgConfig(privateKey, publicKey, endpoint, address, dns))
            } catch (e: Exception) { continue }
        }
        return configs
    }
}
