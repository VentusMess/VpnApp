package com.wavevpn

import android.app.*
import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import androidx.core.app.NotificationCompat

class WaveVpnService : VpnService() {
    private var vpnInterface: ParcelFileDescriptor? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == "CONNECT") {
            startForeground(1, buildNotification())
            vpnInterface = Builder()
                .addAddress("10.0.0.2", 24)
                .addDnsServer("1.1.1.1")
                .addRoute("0.0.0.0", 0)
                .setSession("Wave VPN")
                .establish()
        } else {
            vpnInterface?.close()
            vpnInterface = null
            stopForeground(true)
            stopSelf()
        }
        return START_STICKY
    }

    private fun buildNotification(): Notification {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            getSystemService(NotificationManager::class.java)
                ?.createNotificationChannel(NotificationChannel("wvpn", "Wave VPN", NotificationManager.IMPORTANCE_LOW))
        }
        return NotificationCompat.Builder(this, "wvpn")
            .setContentTitle("Wave VPN")
            .setContentText("Подключён")
            .setSmallIcon(android.R.drawable.ic_lock_lock)
            .build()
    }

    override fun onDestroy() { vpnInterface?.close() }
}
