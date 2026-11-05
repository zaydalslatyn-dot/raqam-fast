package com.raqam.fast

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.graphics.Rect
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import android.util.DisplayMetrics
import android.view.WindowManager
import android.widget.Toast
import java.util.Locale
import java.util.concurrent.Executors

class CaptureService : Service() {
    companion object {
        @Volatile var instance: CaptureService? = null
        @Volatile var pendingBitmap: Bitmap? = null
    }

    val ready: Boolean get() = projection != null

    private val main = Handler(Looper.getMainLooper())
    private val bg = Executors.newSingleThreadExecutor()
    private var projection: MediaProjection? = null
    private var busy = false

    override fun onBind(intent: Intent?): IBinder? = null

    @Suppress("DEPRECATION")
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForegroundNow()
        val code = intent?.getIntExtra("code", 0) ?: 0
        val data = intent?.getParcelableExtra<Intent>("data")
        if (data == null) {
            stopSelf()
            return START_NOT_STICKY
        }
        val mpm = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        projection?.stop()
        val p = mpm.getMediaProjection(code, data)
        p.registerCallback(object : MediaProjection.Callback() {
            override fun onStop() {
                projection = null
            }
        }, main)
        projection = p
        instance = this
        LocalOcr.warmUp(this, bg)
        startCapture()
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        instance = null
        projection?.stop()
        projection = null
        super.onDestroy()
    }

    /** نقطة البداية: من الأيقونة أو الإشعار أو التايل */
    fun startCapture() {
        if (busy) return
        val p = projection ?: return
        busy = true
        main.postDelayed({ shoot(p) }, 350)
    }

    @Suppress("DEPRECATION")
    private fun shoot(p: MediaProjection) {
        val wm = getSystemService(WINDOW_SERVICE) as WindowManager
        val dm = DisplayMetrics()
        wm.defaultDisplay.getRealMetrics(dm)
        val w = dm.widthPixels
        val h = dm.heightPixels
        val reader = ImageReader.newInstance(w, h, PixelFormat.RGBA_8888, 2)
        var vd: VirtualDisplay? = null
        var done = false

        reader.setOnImageAvailableListener({ r ->
            if (done) return@setOnImageAvailableListener
            val img = r.acquireLatestImage() ?: return@setOnImageAvailableListener
            done = true
            val bmp: Bitmap? = try {
                val pl = img.planes[0]
                val rowPad = pl.rowStride - pl.pixelStride * w
                val tmp = Bitmap.createBitmap(
                    w + rowPad / pl.pixelStride, h, Bitmap.Config.ARGB_8888
                )
                tmp.copyPixelsFromBuffer(pl.buffer)
                val out = Bitmap.createBitmap(tmp, 0, 0, w, h)
                if (out !== tmp) tmp.recycle()
                out
            } catch (e: Exception) {
                null
            } finally {
                img.close()
            }
            vd?.release()
            reader.close()
            // من أندرويد 14 الإذن يُستخدم مرة واحدة فقط
            if (Build.VERSION.SDK_INT >= 34) p.stop()
            if (bmp == null) fail("تعذّر أخذ لقطة الشاشة") else showSelector(bmp)
        }, main)

        vd = p.createVirtualDisplay(
            "raqam", w, h, dm.densityDpi,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            reader.surface, null, main
        )

        main.postDelayed({
            if (!done) {
                done = true
                vd?.release()
                reader.close()
                fail("ما وصلت لقطة، جرّب مرة ثانية")
            }
        }, 3000)
    }

    private fun showSelector(bmp: Bitmap) {
        pendingBitmap = bmp
        startActivity(
            Intent(this, SelectActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }

    /** تُستدعى من شاشة التحديد */
    fun onSelected(rect: Rect?) {
        val bmp = pendingBitmap
        pendingBitmap = null
        if (bmp == null) {
            busy = false
            return
        }
        if (rect == null) {
            bmp.recycle()
            busy = false
            return
        }
        process(bmp, rect)
    }

    private fun process(bmp: Bitmap, r: Rect) {
        val t0 = SystemClock.elapsedRealtime()
        bg.execute {
            val crop = Bitmap.createBitmap(bmp, r.left, r.top, r.width(), r.height())
            var num = LocalOcr.read(this, crop)
            var src = "محلي"
            if (num == null && BuildConfig.OCR_API_KEY.isNotEmpty()) {
                num = ServerOcr.read(crop)
                src = "سيرفر"
            }
            crop.recycle()
            bmp.recycle()
            val secs = String.format(
                Locale.US, "%.1f", (SystemClock.elapsedRealtime() - t0) / 1000.0
            )
            main.post {
                busy = false
                if (num == null) {
                    toast("ما لقيت رقماً، جرّب تحديداً أقرب للرقم")
                } else {
                    val cm = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
                    cm.setPrimaryClip(ClipData.newPlainText("number", num))
                    toast("تم النسخ: $num  ($src - $secs ث)")
                }
            }
        }
    }

    private fun fail(msg: String) {
        busy = false
        toast(msg)
    }

    private fun toast(m: String) = Toast.makeText(this, m, Toast.LENGTH_LONG).show()

    private fun startForegroundNow() {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel("raqam", "رقم سريع", NotificationManager.IMPORTANCE_LOW)
        )
        val pi = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val n = Notification.Builder(this, "raqam")
            .setSmallIcon(android.R.drawable.ic_menu_search)
            .setContentTitle("رقم سريع")
            .setContentText("اضغط هنا ثم حدد الرقم لنسخه")
            .setContentIntent(pi)
            .setOngoing(true)
            .build()
        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(1, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)
        } else {
            startForeground(1, n)
        }
    }
}
