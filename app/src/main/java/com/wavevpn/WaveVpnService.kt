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
    private var wgProcess: Process? = null

    companion object {
        var warpConfig: KeyManager.WarpConfig? = null
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
                Thread { startWarp() }.start()
            }
            "DISCONNECT" -> stopEverything()
        }
        return START_STICKY
    }

    private fun startWarp() {
        try {
            val config = warpConfig ?: return

            // Копируем wireguard-go бинарник
            copyAsset("wireguard", "wireguard")

            // Генерируем WireGuard конфиг для WARP
            val wgConfig = """
[Interface]
PrivateKey = ${config.privateKey}
Address = ${config.address4}/32, ${config.address6}/128
DNS = 1.1.1.1, 1.0.0.1

[Peer]
PublicKey = ${config.publicKey}
AllowedIPs = 0.0.0.0/0, ::/0
Endpoint = ${config.endpoint}
PersistentKeepalive = 25
            """.trimIndent()

            File(filesDir, "warp.conf").writeText(wgConfig)

            // Поднимаем VPN туннель
            vpnInterface = Builder()
                .addAddress(config.address4, 32)
                .addDnsServer("1.1.1.1")
                .addDnsServer("1.0.0.1")
                .addRoute("0.0.0.0", 0)
                .setSession("Wave VPN")
                .setMtu(1280)
                .establish() ?: return

            // Запускаем wireguard-go
            val wgFile = File(filesDir, "wireguard")
            if (wgFile.exists()) {
                wgProcess = ProcessBuilder(
                    wgFile.absolutePath,
                    File(filesDir, "warp.conf").absolutePath,
                    vpnInterface!!.fd.toString()
                ).directory(filesDir).redirectErrorStream(true).start()
            }

        } catch (e: Exception) {
            e.printStackTrace()
            stopEverything()
        }
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
