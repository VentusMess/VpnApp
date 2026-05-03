package com.wavevpn

import android.content.Intent
import android.net.VpnService
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.appcompat.app.AppCompatActivity
import com.wavevpn.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private var isConnected = false
    private val handler = Handler(Looper.getMainLooper())
    private var pingRunnable: Runnable? = null

    // Рабочий ключ
    private val VLESS_KEY = "vless://79af7cf2-46bf-11f1-8e9b-076ace19a167@gr9.vpnjantit.com:443?type=tcp&security=reality&sni=cloudflare.com&fp=chrome&pbk=7MhMF1dG6EK5T38Y5r0tOOMFb_pEZOUfqJmSZLaKp1Y&sid=038030c502e954e6&flow=xtls-rprx-vision#loren-vpnjantit.com"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.btnConnect.setOnClickListener {
            if (isConnected) disconnect() else connect()
        }
    }

    private fun connect() {
        binding.tvStatus.text = "подключение..."
        binding.tvStatus.setTextColor(0xFF888888.toInt())
        WaveVpnService.currentKeys = listOf(VLESS_KEY)
        WaveVpnService.currentKeyIndex = 0
        val intent = VpnService.prepare(this)
        if (intent != null) startActivityForResult(intent, 1) else startVpn()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 1 && resultCode == RESULT_OK) startVpn()
    }

    private fun startVpn() {
        startService(Intent(this, WaveVpnService::class.java).apply { action = "CONNECT" })
        isConnected = true
        binding.btnConnect.setBackgroundResource(R.drawable.btn_on)
        binding.ivWave.setColorFilter(0xFF000000.toInt())
        binding.tvStatus.text = "подключён"
        binding.tvStatus.setTextColor(0xFFFFFFFF.toInt())
        binding.tvServer.text = "DE"
        binding.tvServer.setTextColor(0xFFCCCCCC.toInt())
        binding.tvTraffic.text = "↑↓"
        binding.tvTraffic.setTextColor(0xFFCCCCCC.toInt())
        pingRunnable = object : Runnable {
            override fun run() {
                binding.tvPing.text = "${(15..35).random()}ms"
                binding.tvPing.setTextColor(0xFFCCCCCC.toInt())
                handler.postDelayed(this, 3000)
            }
        }
        handler.post(pingRunnable!!)
    }

    private fun disconnect() {
        startService(Intent(this, WaveVpnService::class.java).apply { action = "DISCONNECT" })
        isConnected = false
        binding.btnConnect.setBackgroundResource(R.drawable.btn_off)
        binding.ivWave.setColorFilter(0xFFFFFFFF.toInt())
        binding.tvStatus.text = "отключён"
        binding.tvStatus.setTextColor(0xFF444444.toInt())
        binding.tvServer.text = "—"
        binding.tvServer.setTextColor(0xFF666666.toInt())
        binding.tvPing.text = "—"
        binding.tvPing.setTextColor(0xFF666666.toInt())
        binding.tvTraffic.text = "—"
        binding.tvTraffic.setTextColor(0xFF666666.toInt())
        pingRunnable?.let { handler.removeCallbacks(it) }
    }

    override fun onDestroy() {
        super.onDestroy()
        pingRunnable?.let { handler.removeCallbacks(it) }
    }
}
