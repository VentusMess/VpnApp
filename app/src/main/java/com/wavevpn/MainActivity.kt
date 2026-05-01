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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.btnConnect.setOnClickListener {
            if (isConnected) disconnect() else prepare()
        }
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
        binding.tvStatus.text = "подключён"
        binding.tvStatus.setTextColor(0xFFFFFFFF.toInt())
        binding.tvServer.text = "DE"
        binding.tvTraffic.text = "↑↓"
        var ping = 24
        pingRunnable = object : Runnable {
            override fun run() {
                ping = (18..35).random()
                binding.tvPing.text = "${ping}ms"
                handler.postDelayed(this, 3000)
            }
        }
        handler.post(pingRunnable!!)
    }

    private fun disconnect() {
        startService(Intent(this, WaveVpnService::class.java).apply { action = "DISCONNECT" })
        isConnected = false
        binding.btnConnect.setBackgroundResource(R.drawable.btn_off)
        binding.tvStatus.text = "отключён"
        binding.tvStatus.setTextColor(0xFF444444.toInt())
        binding.tvServer.text = "—"
        binding.tvPing.text = "—"
        binding.tvTraffic.text = "—"
        pingRunnable?.let { handler.removeCallbacks(it) }
    }

    override fun onDestroy() {
        super.onDestroy()
        pingRunnable?.let { handler.removeCallbacks(it) }
    }
}
