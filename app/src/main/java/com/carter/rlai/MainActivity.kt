package com.carter.rlai

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.graphics.Color
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

class MainActivity : Activity() {
    private val captureRequest = 4101
    private lateinit var status: TextView
    private lateinit var detection: TextView
    private lateinit var advice: TextView
    private val handler = Handler(Looper.getMainLooper())
    private val refresh = object : Runnable {
        override fun run() {
            val prefs = getSharedPreferences("rlai", MODE_PRIVATE)
            status.text = if (prefs.getBoolean("running", false)) "CAPTURE ACTIVE" else "NOT CAPTURING"
            detection.text = prefs.getString("detection", "No frame analyzed yet") ?: ""
            advice.text = prefs.getString("advice", "Start screen analysis to receive coaching.") ?: ""
            handler.postDelayed(this, 700)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Color.rgb(8, 8, 8)
        window.navigationBarColor = Color.rgb(8, 8, 8)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(28, 36, 28, 24)
            setBackgroundColor(Color.rgb(8, 8, 8))
        }
        fun text(value: String, size: Float, color: Int = Color.WHITE): TextView =
            TextView(this).apply { this.text = value; textSize = size; setTextColor(color); setPadding(0, 12, 0, 12) }
        root.addView(text("CART3R RL AI", 28f, Color.rgb(255, 65, 75)))
        root.addView(text("ONLINE MATCH COACH  •  VERSION 1.0", 12f, Color.LTGRAY))
        status = text("NOT CAPTURING", 18f, Color.GREEN)
        root.addView(status)
        root.addView(text("VISION ENGINE", 16f, Color.rgb(255, 90, 90)))
        detection = text("No frame analyzed yet", 15f)
        root.addView(detection)
        root.addView(text("TACTICAL COACHING", 16f, Color.rgb(255, 90, 90)))
        advice = text("Start screen analysis to receive coaching.", 15f)
        root.addView(advice)
        val start = Button(this).apply {
            text = "START SCREEN ANALYSIS"
            setOnClickListener { requestCapture() }
        }
        root.addView(start)
        root.addView(Button(this).apply {
            text = "STOP ANALYSIS"
            setOnClickListener { stopService(Intent(this@MainActivity, CaptureService::class.java)) }
        })
        root.addView(text(
            "This is an experimental visual coach. It does not control the game, guarantee wins, or interact with ranked-match controls. Ball detection is heuristic and may be inaccurate depending on arena colors, effects and screen brightness.",
            12f, Color.LTGRAY
        ))
        val scroll = ScrollView(this).apply { addView(root) }
        setContentView(scroll)
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.POST_NOTIFICATIONS), 4102)
        }
    }

    private fun requestCapture() {
        val manager = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        startActivityForResult(manager.createScreenCaptureIntent(), captureRequest)
    }

    @Deprecated("Deprecated Android callback retained for broad device compatibility")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == captureRequest && resultCode == RESULT_OK && data != null) {
            val service = Intent(this, CaptureService::class.java).apply {
                action = CaptureService.ACTION_START
                putExtra(CaptureService.EXTRA_RESULT_CODE, resultCode)
                putExtra(CaptureService.EXTRA_DATA, data)
            }
            ContextCompat.startForegroundService(this, service)
        }
    }

    override fun onResume() { super.onResume(); handler.post(refresh) }
    override fun onPause() { super.onPause(); handler.removeCallbacks(refresh) }
}
