package com.raqam.fast

import android.content.Context
import android.graphics.*
import com.googlecode.tesseract.android.TessBaseAPI
import java.io.File
import java.util.concurrent.ExecutorService
import kotlin.math.max
import kotlin.math.min

object LocalOcr {
    private var api: TessBaseAPI? = null
    private const val WL = "tessedit_char_whitelist"
    private const val DIGIT_SET = "0123456789٠١٢٣٤٥٦٧٨٩۰۱۲۳۴۵۶۷۸۹ -"

    @Synchronized
    private fun get(ctx: Context): TessBaseAPI? {
        api?.let { return it }
        val dir = File(ctx.filesDir, "tessdata").apply { mkdirs() }
        for (n in listOf("ara", "eng")) {
            val f = File(dir, "$n.traineddata")
            if (!f.exists()) {
                try {
                    ctx.assets.open("$n.traineddata").use { i -> f.outputStream().use { i.copyTo(it) } }
                } catch (e: Exception) {
                    return null
                }
            }
        }
        val t = TessBaseAPI()
        if (!t.init(ctx.filesDir.absolutePath, "ara+eng")) {
            t.recycle()
            return null
        }
        api = t
        return t
    }

    fun warmUp(ctx: Context, ex: ExecutorService) {
        ex.execute { get(ctx) }
    }

    /** يجب استدعاؤها من نفس الخيط الخلفي الوحيد */
    fun read(ctx: Context, src: Bitmap): String? {
        val t = get(ctx) ?: return null
        val bmp = prep(src)
        try {
            // مرحلة 1: أرقام فقط
            t.setVariable(WL, DIGIT_SET)
            t.setPageSegMode(TessBaseAPI.PageSegMode.PSM_SINGLE_BLOCK)
            t.setImage(bmp)
            Digits.extract(t.getUTF8Text() ?: "")?.let { return it }

            // مرحلة 2: قراءة عادية بدون قيود
            t.setVariable(WL, "")
            t.setPageSegMode(TessBaseAPI.PageSegMode.PSM_SPARSE_TEXT)
            t.setImage(bmp)
            return Digits.extract(t.getUTF8Text() ?: "")
        } finally {
            bmp.recycle()
        }
    }

    private fun prep(src: Bitmap): Bitmap {
        // نكبّر الصور الصغيرة حتى يصير ارتفاعها ~150 بكسل على الأقل
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
