package com.wavevpn

import android.app.*
import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import androidx.core.app.NotificationCompat
import java.io.File
import java.io.FileOutputStream

class WaveVpnService : VpnService() {
    private var vpnInterface: ParcelFileDescriptor? = null
    private var xrayProcess: Process? = null
    private var tun2socksProcess: Process? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == "CONNECT") {
            val notification = buildNotification()
            if (Build.VERSION.SDK_INT >= 34) {
                startForeground(1, notification, 1 shl 30)
            } else {
                startForeground(1, notification)
            }
            val key = intent.getStringExtra("key")
            Thread {
                setupAndStart(key)
            }.start()
        } else {
            stopEverything()
        }
        return START_STICKY
    }

    private fun setupAndStart(key: String?) {
        try {
            // 1. Копируем бинарники
            copyAsset("xray", "xray")
            copyAsset("tun2socks", "tun2socks")

            // 2. Запускаем Xray с ключом
            if (key != null) {
                val config = generateXrayConfig(key)
                File(filesDir, "config.json").writeText(config)
                xrayProcess = ProcessBuilder(
                    File(filesDir, "xray").absolutePath,
                    "run", "-c", File(filesDir, "config.json").absolutePath
                ).directory(filesDir).redirectErrorStream(true).start()
                Thread.sleep(1000) // ждём старта Xray
            }

            // 3. Поднимаем VPN туннель
            vpnInterface = Builder()
                .addAddress("10.0.0.2", 24)
                .addDnsServer("1.1.1.1")
                .addDnsServer("8.8.8.8")
                .addRoute("0.0.0.0", 0)
                .setSession("Wave VPN")
                .setMtu(1500)
                .establish()

            // 4. Запускаем tun2socks — он перенаправляет трафик из туннеля в Xray
            val tun2socksFile = File(filesDir, "tun2socks")
            if (tun2socksFile.exists()) {
                tun2socksProcess = ProcessBuilder(
                    tun2socksFile.absolutePath,
                    "-device", "fd://${vpnInterface!!.fd}",
                    "-proxy", "socks5://127.0.0.1:10808",
                    "-loglevel", "warning"
                ).directory(filesDir).redirectErrorStream(true).start()
            }

        } catch (e: Exception) {
            e.printStackTrace()
            stopEverything()
        }
    }

    private fun copyAsset(assetName: String, fileName: String) {
        val file = File(filesDir, fileName)
        if (!file.exists()) {
            try {
                assets.open(assetName).use { input ->
                    FileOutputStream(file).use { output ->
                        input.copyTo(output)
                    }
                }
                file.setExecutable(true)
            } catch (e: Exception) {
                // файл не найден в assets — пропускаем
            }
        }
    }

    private fun generateXrayConfig(vlessUrl: String): String {
        val withoutScheme = vlessUrl.removePrefix("vless://")
        val atIdx = withoutScheme.indexOf('@')
        val uuid = withoutScheme.substring(0, atIdx)
        val rest = withoutScheme.substring(atIdx + 1)
        val questionIdx = rest.indexOf('?')
        val hostPort = if (questionIdx != -1) rest.substring(0, questionIdx) else rest.split("#")[0]
        val colonIdx = hostPort.lastIndexOf(':')
        val host = hostPort.substring(0, colonIdx)
        val port = hostPort.substring(colonIdx + 1).split("#")[0].toIntOrNull() ?: 443
        val params = mutableMapOf<String, String>()
        if (questionIdx != -1) {
            rest.substring(questionIdx + 1).split("#")[0].split("&").forEach { param ->
                val kv = param.split("=")
                if (kv.size == 2) params[kv[0]] = java.net.URLDecoder.decode(kv[1], "UTF-8")
            }
        }
        val security = params["security"] ?: "none"
        val sni = params["sni"] ?: host
        val fp = params["fp"] ?: "chrome"
        val pbk = params["pbk"] ?: ""
        val sid = params["sid"] ?: ""
        val type = params["type"] ?: "tcp"
        val flow = params["flow"] ?: ""
        val streamSettings = when (security) {
            "reality" -> """
                "streamSettings": {
                    "network": "$type",
                    "security": "reality",
                    "realitySettings": {
                        "serverName": "$sni",
                        "fingerprint": "$fp",
                        "publicKey": "$pbk",
                        "shortId": "$sid"
                    }
                }
            """.trimIndent()
            "tls" -> """
                "streamSettings": {
                    "network": "$type",
                    "security": "tls",
                    "tlsSettings": {
                        "serverName": "$sni",
                        "fingerprint": "$fp"
                    }
                }
            """.trimIndent()
            else -> """"streamSettings": {"network": "$type"}"""
        }
        val flowSetting = if (flow.isNotEmpty()) "\"flow\": \"$flow\"," else ""
        return """
        {
            "log": {"loglevel": "warning"},
            "inbounds": [{
                "tag": "socks",
                "port": 10808,
                "listen": "127.0.0.1",
                "protocol": "socks",
                "settings": {"udp": true}
            }],
            "outbounds": [
                {
                    "tag": "proxy",
                    "protocol": "vless",
                    "settings": {
                        "vnext": [{
                            "address": "$host",
                            "port": $port,
                            "users": [{"id": "$uuid", $flowSetting "encryption": "none"}]
                        }]
                    },
                    $streamSettings
                },
                {"tag": "direct", "protocol": "freedom"}
            ],
            "routing": {
                "rules": [{"type": "field", "outboundTag": "direct", "ip": ["geoip:private"]}]
            }
        }
        """.trimIndent()
    }

    private fun stopEverything() {
        tun2socksProcess?.destroy()
        tun2socksProcess = null
        xrayProcess?.destroy()
        xrayProcess = null
        vpnInterface?.close()
        vpnInterface = null
        stopForeground(true)
        stopSelf()
    }

    private fun buildNotification(): Notification {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            getSystemService(NotificationManager::class.java)
                ?.createNotificationChannel(
                    NotificationChannel("wvpn", "Wave VPN", NotificationManager.IMPORTANCE_LOW)
                )
        }
        return NotificationCompat.Builder(this, "wvpn")
            .setContentTitle("Wave VPN")
            .setContentText("Подключён")
            .setSmallIcon(android.R.drawable.ic_lock_lock)
            .setOngoing(true)
            .build()
    }

    override fun onDestroy() { stopEverything() }
}
