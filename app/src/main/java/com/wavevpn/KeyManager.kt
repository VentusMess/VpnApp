package com.wavevpn

import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

object KeyManager {
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    data class WarpConfig(
        val privateKey: String,
        val publicKey: String,
        val address4: String,
        val address6: String,
        val endpoint: String = "162.159.192.1:2408"
    )

    fun fetchWarpConfig(): WarpConfig? {
        return try {
            val json = JSONObject().apply {
                put("key", generatePublicKey())
                put("install_id", generateId())
                put("fcm_token", "")
                put("tos", "2024-01-01T00:00:00.000Z")
                put("model", "Android")
                put("serial_number", generateId())
                put("locale", "en_US")
            }

            val body = json.toString().toRequestBody("application/json".toMediaType())

            // Пробуем разные версии API
            val urls = listOf(
                "https://api.cloudflareclient.com/v0a2158/reg",
                "https://api.cloudflareclient.com/v0a1922/reg",
                "https://api.cloudflareclient.com/v0a977/reg"
            )

            for (url in urls) {
                try {
                    val request = Request.Builder()
                        .url(url)
                        .post(body)
                        .addHeader("Content-Type", "application/json")
                        .addHeader("User-Agent", "okhttp/3.12.1")
                        .build()

                    val response = client.newCall(request).execute()
                    val responseBody = response.body?.string() ?: continue

                    android.util.Log.d("WaveVPN", "Response: $responseBody")

                    if (!response.isSuccessful) continue

                    val responseJson = JSONObject(responseBody)
                    val config = responseJson.getJSONObject("config")
                    val peers = config.getJSONArray("peers")
                    val peer = peers.getJSONObject(0)
                    val peerPublicKey = peer.getString("public_key")
                    val endpoint = peer.getJSONObject("endpoint").getString("host")
                    val interface_ = config.getJSONObject("interface")
                    val addresses = interface_.getJSONObject("addresses")
                    val address4 = addresses.getString("v4")
                    val address6 = addresses.getString("v6")
                    val privateKey = responseJson.getString("private_key")

                    return WarpConfig(privateKey, peerPublicKey, address4, address6, endpoint)
                } catch (e: Exception) {
                    android.util.Log.e("WaveVPN", "URL $url failed: ${e.message}")
                    continue
                }
            }
            null
        } catch (e: Exception) {
            android.util.Log.e("WaveVPN", "Error: ${e.message}")
            null
        }
    }

    private fun generatePublicKey(): String {
        val chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/"
        return (1..44).map { chars.random() }.joinToString("") + "="
    }

    private fun generateId(): String {
        val chars = "abcdefghijklmnopqrstuvwxyz0123456789"
        return (1..22).map { chars.random() }.joinToString("")
    }
}
