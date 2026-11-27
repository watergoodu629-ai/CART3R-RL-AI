package com.carter.rlai

import android.app.*
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.Image
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.util.DisplayMetrics
import android.view.WindowManager
import androidx.core.app.NotificationCompat
import kotlin.math.max

class CaptureService : Service() {
    companion object {
        const val ACTION_START = "com.carter.rlai.START"
        const val EXTRA_RESULT_CODE = "resultCode"
        const val EXTRA_DATA = "resultData"
        private const val CHANNEL = "rlai_capture"
        private const val NOTIFICATION_ID = 71
    }

    private var projection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var reader: ImageReader? = null
    private var worker: Handler? = null
    private var lastAnalysis = 0L

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_START) {
            createNotificationChannel()
            val notification = NotificationCompat.Builder(this, CHANNEL)
                .setContentTitle("CART3R RL AI is analyzing")
                .setContentText("Screen analysis is active. Tap stop in the app to end.")
                .setSmallIcon(android.R.drawable.ic_menu_view)
                .setOngoing(true).build()
            if (Build.VERSION.SDK_INT >= 29) startForeground(NOTIFICATION_ID, notification, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)
            else startForeground(NOTIFICATION_ID, notification)
            startCapture(intent)
        }
        return START_NOT_STICKY
    }

    private fun startCapture(intent: Intent) {
        val resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, Activity.RESULT_CANCELED)
        @Suppress("DEPRECATION")
        val data = if (Build.VERSION.SDK_INT >= 33) intent.getParcelableExtra(EXTRA_DATA, Intent::class.java) else intent.getParcelableExtra(EXTRA_DATA)
        if (data == null || resultCode != Activity.RESULT_OK) { stopSelf(); return }

        val metrics = DisplayMetrics()
        @Suppress("DEPRECATION")
        (getSystemService(WINDOW_SERVICE) as WindowManager).defaultDisplay.getRealMetrics(metrics)
        val width = max(1, metrics.widthPixels)
        val height = max(1, metrics.heightPixels)
        val density = metrics.densityDpi

        val thread = HandlerThread("rlai-frame-worker").apply { start() }
        worker = Handler(thread.looper)
        val projectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        projection = projectionManager.getMediaProjection(resultCode, data)
        reader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2)
        reader?.setOnImageAvailableListener({ imageReader ->
            val image = imageReader.acquireLatestImage() ?: return@setOnImageAvailableListener
            try {
                val now = System.currentTimeMillis()
                if (now - lastAnalysis >= 650) {
                    lastAnalysis = now
                    analyze(image)
                }
            } finally { image.close() }
        }, worker)
        virtualDisplay = projection?.createVirtualDisplay(
            "CART3R-RL-AI", width, height, density,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            reader?.surface, null, worker
        )
        projection?.registerCallback(object : MediaProjection.Callback() {
            override fun onStop() { stopCapture() }
        }, worker)
        getSharedPreferences("rlai", MODE_PRIVATE).edit().putBoolean("running", true).apply()
    }

    private fun analyze(image: Image) {
        val plane = image.planes[0]
        val buffer = plane.buffer
        val pixelStride = plane.pixelStride
        val rowStride = plane.rowStride
        val width = image.width
        val height = image.height
        val rowPadding = rowStride - pixelStride * width
        val paddedWidth = width + rowPadding / pixelStride
        val pixels = IntArray(paddedWidth * height)
        buffer.rewind()
        for (i in pixels.indices) {
            if (buffer.remaining() >= 4) {
                val r = buffer.get().toInt() and 255
                val g = buffer.get().toInt() and 255
                val b = buffer.get().toInt() and 255
                buffer.get()
                pixels[i] = (r shl 16) or (g shl 8) or b
            }
        }

        // Basic orange-pixel heuristic; not a trained object detector.
        var count = 0
        var sumX = 0L
        var sumY = 0L
        val step = 4
        for (y in height / 8 until height * 7 / 8 step step) {
            for (x in width / 10 until width * 9 / 10 step step) {
                val p = pixels[y * paddedWidth + x]
                val r = (p shr 16) and 255
                val g = (p shr 8) and 255
                val b = p and 255
                if (r > 175 && g in 55..185 && b < 105 && r > g * 1.25 && g > b * 1.25) {
                    count++; sumX += x; sumY += y
                }
            }
        }

        val detection: String
        val advice: String
        if (count > 18) {
            val cx = (sumX / count).toFloat() / width
            val cy = (sumY / count).toFloat() / height
            detection = "Orange-color candidate at X=${(cx * 100).toInt()}%, Y=${(cy * 100).toInt()}% (confidence: uncalibrated)"
            advice = when {
                cy < 0.38f -> "Candidate is high on screen. Watch the aerial and avoid overcommitting."
                cx < 0.38f -> "Candidate is left of center. Check your rotation and defensive positioning."
                cx > 0.62f -> "Candidate is right of center. Track the play and keep recovery space."
                else -> "Candidate near center. Prioritize a controlled approach and preserve boost."
            }
        } else {
            detection = "No reliable orange candidate detected."
            advice = "Keep a safe rotation, watch the ball, and avoid committing without teammate support."
        }
        getSharedPreferences("rlai", MODE_PRIVATE).edit()
            .putString("detection", detection).putString("advice", advice).apply()
    }

    private fun stopCapture() {
        virtualDisplay?.release(); virtualDisplay = null
        reader?.close(); reader = null
        projection?.stop(); projection = null
        worker?.looper?.quitSafely(); worker = null
        getSharedPreferences("rlai", MODE_PRIVATE).edit()
            .putBoolean("running", false).putString("detection", "Capture stopped.")
            .putString("advice", "Start a new screen-sharing session to resume coaching.").apply()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() { stopCapture(); super.onDestroy() }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            val channel = NotificationChannel(CHANNEL, "Screen analysis", NotificationManager.IMPORTANCE_LOW)
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }
}
