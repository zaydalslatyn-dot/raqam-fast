package com.raqam.fast

import android.content.Context
import android.graphics.*
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import com.googlecode.tesseract.android.TessBaseAPI
import java.io.File
import java.util.concurrent.ExecutorService
import kotlin.math.max
import kotlin.math.min

object LocalOcr {
    private var mix: TessBaseAPI? = null
    private var ara: TessBaseAPI? = null
    private const val WL = "tessedit_char_whitelist"
    private const val DIGIT_SET = "0123456789٠١٢٣٤٥٦٧٨٩۰۱۲۳۴۵۶۷۸۹ -"

    @Synchronized
    private fun prepareData(ctx: Context): Boolean {
        val dir = File(ctx.filesDir, "tessdata").apply { mkdirs() }
        for (n in listOf("ara", "eng")) {
            val f = File(dir, "$n.traineddata")
            if (!f.exists()) {
                try {
                    ctx.assets.open("$n.traineddata").use { i -> f.outputStream().use { i.copyTo(it) } }
                } catch (e: Exception) {
                    return false
                }
            }
        }
        return true
    }

    @Synchronized
    private fun getMix(ctx: Context): TessBaseAPI? {
        mix?.let { return it }
        if (!prepareData(ctx)) return null
        val t = TessBaseAPI()
        if (!t.init(ctx.filesDir.absolutePath, "ara+eng")) {
            t.recycle()
            return null
        }
        mix = t
        return t
    }

    @Synchronized
    private fun getAra(ctx: Context): TessBaseAPI? {
        ara?.let { return it }
        if (!prepareData(ctx)) return null
        val t = TessBaseAPI()
        if (!t.init(ctx.filesDir.absolutePath, "ara")) {
            t.recycle()
            return null
        }
        ara = t
        return t
    }

    fun warmUp(ctx: Context, ex: ExecutorService) {
        ex.execute { getMix(ctx) }
    }

    private fun clip(s: String) = s.replace("\n", " ").trim().take(40)

    /** يجب استدعاؤها من نفس الخيط الخلفي الوحيد */
    fun read(ctx: Context, src: Bitmap): String? {
        val m = getMix(ctx) ?: return null
        val bmp = prep(src)
        val raw = StringBuilder()
        try {
            // 1) كالسابق: عربي + إنجليزي بدون قيود
            m.setVariable(WL, "")
            m.setPageSegMode(TessBaseAPI.PageSegMode.PSM_SINGLE_BLOCK)
            m.setImage(bmp)
            val t1 = m.getUTF8Text() ?: ""
            raw.append("1[").append(clip(t1)).append("] ")
            Digits.extract(t1)?.let { return it }

            // 2) و3) النموذج العربي وحده، أرقام فقط
            val a = getAra(ctx)
            if (a != null) {
                a.setVariable(WL, DIGIT_SET)
                a.setPageSegMode(TessBaseAPI.PageSegMode.PSM_SINGLE_LINE)
                a.setImage(bmp)
                val t2 = a.getUTF8Text() ?: ""
                raw.append("2[").append(clip(t2)).append("] ")
                Digits.extract(t2)?.let { return it }

                a.setPageSegMode(TessBaseAPI.PageSegMode.PSM_SPARSE_TEXT)
                a.setImage(bmp)
                val t3 = a.getUTF8Text() ?: ""
                raw.append("3[").append(clip(t3)).append("]")
                Digits.extract(t3)?.let { return it }
            }

            val msg = "قرأ: $raw"
            Handler(Looper.getMainLooper()).post {
                Toast.makeText(ctx, msg, Toast.LENGTH_LONG).show()
            }
            return null
        } finally {
            bmp.recycle()
        }
    }

    private fun prep(src: Bitmap): Bitmap {
        val minSide = min(src.width, src.height).coerceAtLeast(1)
        val scale = if (max(src.width, src.height) < 1200) (150f / minSide).coerceIn(1f, 4f) else 1f
        val w = (src.width * scale).toInt()
        val h = (src.height * scale).toInt()
        // هل الخلفية داكنة؟ نقلب الألوان لأن Tesseract يفضل نصاً داكناً على خلفية فاتحة
        val small = Bitmap.createScaledBitmap(src, 16, 16, true)
        var sum = 0L
        for (x in 0 until 16) for (y in 0 until 16) sum += Color.green(small.getPixel(x, y))
        small.recycle()
        val dark = sum / 256 < 110

        val cm = ColorMatrix().apply { setSaturation(0f) }
        if (dark) cm.postConcat(ColorMatrix(floatArrayOf(-1f, 0f, 0f, 0f, 255f, 0f, -1f, 0f, 0f, 255f, 0f, 0f, -1f, 0f, 255f, 0f, 0f, 0f, 1f, 0f)))
        val k = 1.4f
        val tr = 128f * (1f - k)
        cm.postConcat(ColorMatrix(floatArrayOf(k, 0f, 0f, 0f, tr, 0f, k, 0f, 0f, tr, 0f, 0f, k, 0f, tr, 0f, 0f, 0f, 1f, 0f)))

        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val p = Paint().apply { colorFilter = ColorMatrixColorFilter(cm); isFilterBitmap = true }
        Canvas(out).drawBitmap(src, null, Rect(0, 0, w, h), p)
        return out
    }
}
