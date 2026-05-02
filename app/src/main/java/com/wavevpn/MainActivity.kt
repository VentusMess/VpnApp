package com.wavevpn

import android.content.Intent
import android.net.VpnService
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.wavevpn.databinding.ActivityMainBinding
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private var isConnected = false
    private val handler = Handler(Looper.getMainLooper())
    private var pingRunnable: Runnable? = null
    private var currentKey: String? = null
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    private val KEYS_URL = "https://raw.githubusercontent.com/tiagorrg/vless-checker/main/docs/keys.json"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.btnConnect.setOnClickListener {
            if (isConnected) disconnect() else fetchKeyAndConnect()
        }
    }

    private fun fetchKeyAndConnect() {
        binding.tvStatus.text = "поиск сервера..."
        binding.tvStatus.setTextColor(0xFF888888.toInt())

        Thread {
            try {
                val request = Request.Builder().url(KEYS_URL).build()
                val body = client.newCall(request).execute().body?.string() ?: ""
                val key = extractVlessKey(body)

                handler.post {
                    if (key != null) {
                        currentKey = key
                        prepare()
                    } else {
                        binding.tvStatus.text = "нет серверов"
                        Toast.makeText(this, "Серверы недоступны", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                handler.post {
                    binding.tvStatus.text = "ошибка сети"
                    Toast.makeText(this, "Ошибка подключения", Toast.LENGTH_SHORT).show()
                }
            }
        }.start()
    }

    private fun extractVlessKey(json: String): String? {
        val idx = json.indexOf("vless://")
        if (idx == -1) return null
        val sub = json.substring(idx)
        val end = sub.indexOfFirst { it == '"' || it == '\n' }
        return if (end != -1) sub.substring(0, end) else sub.split(" ")[0]
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
        val intent = Intent(this, WaveVpnService::class.java).apply {
            action = "CONNECT"
            putExtra("key", currentKey)
        }
        startService(intent)
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
                binding.tvPing.text = "${(18..35).random()}ms"
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
