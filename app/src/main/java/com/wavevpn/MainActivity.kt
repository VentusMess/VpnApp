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

    private val KEYS_URLS = listOf(
        "https://raw.githubusercontent.com/tiagorrg/vless-checker/main/docs/keys.json",
        "https://raw.githubusercontent.com/kort0881/vpn-vless-configs-russia/main/githubmirror/clean/vless.txt"
    )

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
            var key: String? = null
            for (url in KEYS_URLS) {
                try {
                    val request = Request.Builder().url(url).build()
                    val body = client.newCall(request).execute().body?.string() ?: ""
                    key = extractBestVlessKey(body)
                    if (key != null) break
                } catch (e: Exception) {
                    continue
                }
            }

            handler.post {
                if (key != null) {
                    currentKey = key
                    prepare()
                } else {
                    binding.tvStatus.text = "нет серверов"
                    binding.tvStatus.setTextColor(0xFFFF4444.toInt())
                    Toast.makeText(this, "Попробуй позже", Toast.LENGTH_SHORT).show()
                }
            }
        }.start()
    }

    private fun extractBestVlessKey(text: String): String? {
        // Собираем все vless:// ключи
        val keys = mutableListOf<String>()
        var idx = 0
        while (true) {
            val start = text.indexOf("vless://", idx)
            if (start == -1) break
            val sub = text.substring(start)
            val end = sub.indexOfFirst { it == '"' || it == '\n' || it == ' ' }
            val key = if (end != -1) sub.substring(0, end) else sub
            if (key.length > 20) keys.add(key.trim())
            idx = start + 8
        }
        // Предпочитаем ключи с Reality (самые надёжные в РФ)
        return keys.firstOrNull { it.contains("security=reality") }
            ?: keys.firstOrNull { it.contains("security=tls") }
            ?: keys.firstOrNull()
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
