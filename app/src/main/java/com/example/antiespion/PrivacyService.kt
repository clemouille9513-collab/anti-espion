package com.example.antiespion

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.SystemClock
import android.provider.Settings
import android.util.Log
import android.util.Size
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleService
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetector
import com.google.mlkit.vision.face.FaceDetectorOptions
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * Service au premier plan qui affiche le filtre sombre par-dessus toutes les applis,
 * la poignée de la bande de lecture, et (option) surveille la caméra frontale :
 * si 2 visages ou plus sont détectés, l'écran passe en noir.
 */
class PrivacyService : LifecycleService() {

    companion object {
        const val ACTION_START = "com.example.antiespion.START"
        const val ACTION_STOP = "com.example.antiespion.STOP"
        private const val CHANNEL = "antiespion"
        private const val NOTIF_ID = 1
        private const val TAG = "AntiEspion"
    }

    private lateinit var wm: WindowManager

    private var dimView: DimView? = null
    private var dimParams: WindowManager.LayoutParams? = null
    private var handle: View? = null
    private var bandY = -1f

    private var guardOn = false
    private var provider: ProcessCameraProvider? = null
    private var executor: ExecutorService? = null
    private var detector: FaceDetector? = null
    private var lastAnalysis = 0L
    private var multiFrames = 0
    private var calmFrames = 0
    private var intruder = false

    override fun onCreate() {
        super.onCreate()
        wm = getSystemService(WindowManager::class.java)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)

        if (intent?.action == ACTION_STOP) {
            Config.setEnabled(this, false)
            shutDown()
            return START_NOT_STICKY
        }

        val cfg = Config.load(this)
        val camOk = cfg.guard && hasCamera()

        // Toujours passer au premier plan d'abord (obligatoire après startForegroundService)
        goForeground(camOk)

        if (!cfg.enabled || !Settings.canDrawOverlays(this)) {
            shutDown()
            return START_NOT_STICKY
        }

        applyOverlay(cfg)
        if (camOk) startGuard() else stopGuard()
        return START_STICKY
    }

    override fun onDestroy() {
        removeOverlay()
        stopGuard()
        super.onDestroy()
    }

    // ---------------------------------------------------------------- notification

    private fun hasCamera() =
        checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED

    private fun goForeground(withCamera: Boolean) {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL, "Anti-espion", NotificationManager.IMPORTANCE_LOW)
        )
        val stop = PendingIntent.getService(
            this, 0,
            Intent(this, PrivacyService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val open = PendingIntent.getActivity(
            this, 1,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )
        val notif = NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_lock_idle_lock)
            .setContentTitle("Écran anti-espion actif")
            .setContentText("Touche pour régler")
            .setContentIntent(open)
            .addAction(0, "Désactiver", stop)
            .setOngoing(true)
            .build()

        var type = ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
        if (withCamera) type = type or ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA
        startForeground(NOTIF_ID, notif, type)
    }

    // ---------------------------------------------------------------- filtre sombre

    private fun applyOverlay(cfg: Config.State) {
        val screenH = wm.currentWindowMetrics.bounds.height()

        if (dimView == null) {
            val v = DimView(this)
            val lp = WindowManager.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT
            )
            lp.gravity = Gravity.TOP or Gravity.START
            // Android laisse passer les touches à travers une fenêtre seulement si son alpha <= 0.8
            lp.alpha = if (intruder) 1f else 0.8f
            lp.layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
            wm.addView(v, lp)
            dimView = v
            dimParams = lp
        }

        if (bandY < 0f) bandY = screenH / 2f

        dimView?.let {
            it.dim = cfg.dim
            it.band = cfg.band
            it.bandCenter = bandY
            it.intruder = intruder
        }

        if (cfg.band) showHandle(screenH) else hideHandle()
    }

    private fun showHandle(screenH: Int) {
        if (handle != null) return
        val d = resources.displayMetrics.density
        val w = (36 * d).toInt()
        val h = (96 * d).toInt()

        val v = View(this)
        v.background = GradientDrawable().apply {
            cornerRadius = 18 * d
            setColor(0xCCFFFFFF.toInt())
            setStroke((2 * d).toInt(), 0xFF333333.toInt())
        }

        val lp = WindowManager.LayoutParams(
            w, h,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        )
        lp.gravity = Gravity.TOP or Gravity.END
        lp.y = (bandY - h / 2f).toInt().coerceIn(0, screenH - h)

        var startRawY = 0f
        var startY = 0
        v.setOnTouchListener { _, e ->
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    startRawY = e.rawY
                    startY = lp.y
                }
                MotionEvent.ACTION_MOVE -> {
                    lp.y = (startY + (e.rawY - startRawY)).toInt().coerceIn(0, screenH - h)
                    wm.updateViewLayout(v, lp)
                    bandY = lp.y + h / 2f
                    dimView?.bandCenter = bandY
                }
            }
            true
        }

        wm.addView(v, lp)
        handle = v
    }

    private fun hideHandle() {
        handle?.let { runCatching { wm.removeView(it) } }
        handle = null
    }

    private fun removeOverlay() {
        hideHandle()
        dimView?.let { runCatching { wm.removeView(it) } }
        dimView = null
        dimParams = null
    }

    // ---------------------------------------------------------------- détection 2e visage

    private fun startGuard() {
        if (guardOn) return
        guardOn = true

        val future = ProcessCameraProvider.getInstance(this)
        future.addListener({
            try {
                val p = future.get()
                val exec = Executors.newSingleThreadExecutor()
                val det = FaceDetection.getClient(
                    FaceDetectorOptions.Builder()
                        .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
                        .setMinFaceSize(0.08f)
                        .build()
                )
                val analysis = ImageAnalysis.Builder()
                    .setTargetResolution(Size(480, 360))
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()
                analysis.setAnalyzer(exec) { proxy -> analyze(proxy, det) }

                p.unbindAll()
                p.bindToLifecycle(this, CameraSelector.DEFAULT_FRONT_CAMERA, analysis)

                provider = p
                executor = exec
                detector = det
            } catch (e: Exception) {
                Log.e(TAG, "Caméra indisponible", e)
                guardOn = false
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun stopGuard() {
        guardOn = false
        provider?.unbindAll()
        provider = null
        executor?.shutdown()
        executor = null
        detector?.close()
        detector = null
        multiFrames = 0
        calmFrames = 0
        if (intruder) setIntruder(false)
    }

    @Suppress("UnsafeOptInUsageError")
    private fun analyze(proxy: ImageProxy, det: FaceDetector) {
        val now = SystemClock.elapsedRealtime()
        val media = proxy.image
        if (now - lastAnalysis < 250 || media == null) {
            proxy.close()
            return
        }
        lastAnalysis = now

        val image = InputImage.fromMediaImage(media, proxy.imageInfo.rotationDegrees)
        det.process(image)
            .addOnSuccessListener { faces -> onFaces(faces.size) }
            .addOnCompleteListener { proxy.close() }
    }

    private fun onFaces(count: Int) {
        if (count >= 2) {
            multiFrames++
            calmFrames = 0
        } else {
            calmFrames++
            multiFrames = 0
        }
        if (!intruder && multiFrames >= 2) setIntruder(true)
        else if (intruder && calmFrames >= 8) setIntruder(false)
    }

    private fun setIntruder(value: Boolean) {
        intruder = value
        val v = dimView ?: return
        val lp = dimParams ?: return
        v.intruder = value
        lp.alpha = if (value) 1f else 0.8f
        runCatching { wm.updateViewLayout(v, lp) }
    }

    // ---------------------------------------------------------------- arrêt

    private fun shutDown() {
        removeOverlay()
        stopGuard()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }
}
