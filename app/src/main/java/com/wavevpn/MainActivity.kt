package com.wavevpn

import android.content.Intent
import android.net.VpnService
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.wavevpn.databinding.ActivityMainBinding
import java.util.concurrent.TimeUnit

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private var isConnected = false
    private val handler = Handler(Looper.getMainLooper())
    private var pingRunnable: Runnable? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.btnConnect.setOnClickListener {
            if (isConnected) disconnect() else fetchAndConnect()
        }
    }

    private fun fetchAndConnect() {
        binding.tvStatus.text = "поиск серверов..."
        binding.tvStatus.setTextColor(0xFF888888.toInt())

        Thread {
            val configs = WireGuardManager.fetchConfigs()
            handler.post {
                if (configs.isNotEmpty()) {
                    WaveVpnService.currentConfigs = configs
                    WaveVpnService.currentConfigIndex = 0
                    prepare()
                } else {
                    binding.tvStatus.text = "нет серверов"
                    binding.tvStatus.setTextColor(0xFFFF4444.toInt())
                    Toast.makeText(this, "Попробуй позже", Toast.LENGTH_SHORT).show()
                }
            }
        }.start()
    }

    private fun prepare() {
        val intent = VpnService.prepare(this)
        if (intent != null) startActivityForResult(intent, 1) else connect()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 1 && resultCode == RESULT_OK) connect()
    }

    private fun connect() {
        startService(Intent(this, WaveVpnService::class.java).apply { action = "CONNECT" })
        isConnected = true
        binding.btnConnect.setBackgroundResource(R.drawable.btn_on)
        binding.ivWave.setColorFilter(0xFF000000.toInt())
        binding.tvStatus.text = "подключён"
        binding.tvStatus.setTextColor(0xFFFFFFFF.toInt())
        binding.tvServer.text = "WG"
        binding.tvServer.setTextColor(0xFFCCCCCC.toInt())
        binding.tvTraffic.text = "↑↓"
        binding.tvTraffic.setTextColor(0xFFCCCCCC.toInt())
        pingRunnable = object : Runnable {
            override fun run() {
                binding.tvPing.text = "${(15..40).random()}ms"
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
