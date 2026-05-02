package com.wavevpn

import android.app.*
import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import androidx.core.app.NotificationCompat

class WaveVpnService : VpnService() {
    private var vpnInterface: ParcelFileDescriptor? = null
    private var xrayProcess: Process? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == "CONNECT") {
            val notification = buildNotification()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                startForeground(1, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
            } else {
                startForeground(1, notification)
            }
            val key = intent.getStringExtra("key")
            if (key != null) startXray(key)
            startVpnTunnel()
        } else {
            stopEverything()
        }
        return START_STICKY
    }

    private fun startXray(vlessKey: String) {
        try {
            val xrayFile = java.io.File(filesDir, "xray")
            if (!xrayFile.exists()) {
                assets.open("xray").use { input ->
                    xrayFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
                xrayFile.setExecutable(true)
            }
            val config = generateXrayConfig(vlessKey)
            val configFile = java.io.File(filesDir, "config.json")
            configFile.writeText(config)
            xrayProcess = ProcessBuilder(xrayFile.absolutePath, "run", "-c", configFile.absolutePath)
                .directory(filesDir)
                .redirectErrorStream(true)
                .start()
        } catch (e: Exception) {
            e.printStackTrace()
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
                if (kv.size == 2) params[kv[0]] = kv[1]
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
            else -> """
                "streamSettings": {"network": "$type"}
            """.trimIndent()
        }
        val flowSetting = if (flow.isNotEmpty()) "\"flow\": \"$flow\"," else ""
        return """
        {
            "log": {"loglevel": "warning"},
            "inbounds": [
                {
                    "tag": "socks",
                    "port": 10808,
                    "listen": "127.0.0.1",
                    "protocol": "socks",
                    "settings": {"udp": true}
                }
            ],
            "outbounds": [
                {
                    "tag": "proxy",
                    "protocol": "vless",
                    "settings": {
                        "vnext": [{
                            "address": "$host",
                            "port": $port,
                            "users": [{
                                "id": "$uuid",
                                $flowSetting
                                "encryption": "none"
                            }]
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

    private fun startVpnTunnel() {
        try {
            vpnInterface = Builder()
                .addAddress("10.0.0.2", 24)
                .addDnsServer("1.1.1.1")
                .addDnsServer("8.8.8.8")
                .addRoute("0.0.0.0", 0)
                .setSession("Wave VPN")
                .setMtu(1500)
                .establish()
        } catch (e: Exception) {
            e.printStackTrace()
            stopEverything()
        }
    }

    private fun stopEverything() {
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
