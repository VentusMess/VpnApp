package com.wavevpn

import android.app.*
import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import androidx.core.app.NotificationCompat
import java.io.File
import java.io.FileOutputStream
import java.net.InetSocketAddress

class WaveVpnService : VpnService() {
    private var vpnInterface: ParcelFileDescriptor? = null
    private var wgProcess: Process? = null

    companion object {
        var currentConfigs: List<WireGuardManager.WgConfig> = emptyList()
        var currentConfigIndex = 0
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            "CONNECT" -> {
                val notification = buildNotification()
                if (Build.VERSION.SDK_INT >= 34) {
                    startForeground(1, notification, 1 shl 30)
                } else {
                    startForeground(1, notification)
                }
                Thread { startWireGuard() }.start()
            }
            "NEXT_SERVER" -> {
                Thread { switchToNextServer() }.start()
            }
            "DISCONNECT" -> stopEverything()
        }
        return START_STICKY
    }

    private fun startWireGuard(configIndex: Int = 0) {
        try {
            if (currentConfigs.isEmpty()) return
            val config = currentConfigs[configIndex % currentConfigs.size]

            // Копируем wg-quick бинарник
            copyAsset("wireguard", "wireguard")

            // Записываем конфиг
            val configFile = File(filesDir, "wg0.conf")
            configFile.writeText(buildWgConfig(config))

            // Поднимаем VPN туннель
            val builder = Builder()
            config.address.split(",").forEach { addr ->
                val parts = addr.trim().split("/")
                if (parts.size == 2) {
                    builder.addAddress(parts[0], parts[1].toInt())
                }
            }
            config.dns.split(",").forEach { dns ->
                builder.addDnsServer(dns.trim())
            }
            builder.addRoute("0.0.0.0", 0)
            builder.setSession("Wave VPN")
            builder.setMtu(1420)
            vpnInterface = builder.establish() ?: return

            // Запускаем WireGuard
            val wgFile = File(filesDir, "wireguard")
            if (wgFile.exists()) {
                wgProcess = ProcessBuilder(
                    wgFile.absolutePath,
                    configFile.absolutePath,
                    vpnInterface!!.fd.toString()
                ).directory(filesDir).redirectErrorStream(true).start()
            }

        } catch (e: Exception) {
            e.printStackTrace()
            // Пробуем следующий сервер
            switchToNextServer()
        }
    }

    private fun switchToNextServer() {
        currentConfigIndex++
        wgProcess?.destroy()
        vpnInterface?.close()
        if (currentConfigIndex < currentConfigs.size) {
            startWireGuard(currentConfigIndex)
        } else {
            // Все серверы кончились — начинаем сначала
            currentConfigIndex = 0
            startWireGuard(0)
        }
    }

    private fun buildWgConfig(config: WireGuardManager.WgConfig): String {
        return """
[Interface]
PrivateKey = ${config.privateKey}
Address = ${config.address}
DNS = ${config.dns}

[Peer]
PublicKey = ${config.publicKey}
Endpoint = ${config.endpoint}
AllowedIPs = 0.0.0.0/0, ::/0
PersistentKeepalive = 25
        """.trimIndent()
    }

    private fun copyAsset(assetName: String, fileName: String) {
        val file = File(filesDir, fileName)
        try {
            assets.open(assetName).use { input ->
                FileOutputStream(file).use { output -> input.copyTo(output) }
            }
            file.setExecutable(true)
        } catch (e: Exception) { e.printStackTrace() }
    }

    private fun stopEverything() {
        wgProcess?.destroy()
        wgProcess = null
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
