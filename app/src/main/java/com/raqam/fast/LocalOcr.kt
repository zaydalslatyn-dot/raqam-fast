package com.raqam.fast

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
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
    private const val AR_DIGITS = "٠١٢٣٤٥٦٧٨٩۰۱۲۳۴۵۶۷۸۹0123456789 -"

    private fun base(ctx: Context) = File(ctx.filesDir, "ocr2")

    @Synchronized
    private fun prepareData(ctx: Context): Boolean {
        // نحذف النماذج القديمة (السريعة) حتى لا تُستخدم بالخطأ
        File(ctx.filesDir, "tessdata").deleteRecursively()
        val dir = File(base(ctx), "tessdata").apply { mkdirs() }
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
        if (!t.init(base(ctx).absolutePath, "ara+eng")) {
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
        if (!t.init(base(ctx).absolutePath, "ara")) {
            t.recycle()
            return null
        }
        ara = t
        return t
    }

    fun warmUp(ctx: Context, ex: ExecutorService) {
        ex.execute { getMix(ctx) }
    }

    private fun clip(s: String) = s.replace("\n", " ").trim().take(30)

    private fun toastLater(ctx: Context, msg: String) {
        Handler(Looper.getMainLooper()).post {
            Toast.makeText(ctx, msg, Toast.LENGTH_LONG).show()
        }
    }

    /** يجب استدعاؤها من نفس الخيط الخلفي الوحيد */
    fun read(ctx: Context, src: Bitmap): String? {
        val m = getMix(ctx) ?: return null

        // 1) المسار القديم (ممتاز للأرقام الإنجليزية)
        val b0 = prep(src, false)
        val t1 = try {
            m.setVariable(WL, "")
            m.setPageSegMode(TessBaseAPI.PageSegMode.PSM_SINGLE_BLOCK)
            m.setImage(b0)
            m.getUTF8Text() ?: ""
        } finally {
            b0.recycle()
        }
        val hasAr = t1.any { it in '\u0660'..'\u0669' || it in '\u06F0'..'\u06F9' }
        val first = Digits.extract(t1)
        if (!hasAr && first != null) return first

        // 2) أرقام هندية: عدة قراءات ثم تصويت
        val a = getAra(ctx) ?: return first
        val votes = HashMap<String, Int>()
        if (first != null) votes[first] = 1
        val raw = StringBuilder()
        val modes = listOf(
            TessBaseAPI.PageSegMode.PSM_SINGLE_LINE,
            TessBaseAPI.PageSegMode.PSM_SINGLE_BLOCK
        )
        for (bin in listOf(false, true)) {
            val bmp = prep(src, bin)
            try {
                for (psm in modes) {
                    a.setVariable(WL, AR_DIGITS)
                    a.setPageSegMode(psm)
                    a.setImage(bmp)
                    val t = a.getUTF8Text() ?: ""
                    Digits.extract(t)?.let { votes[it] = (votes[it] ?: 0) + 1 }
                    if (raw.length < 120) raw.append("[").append(clip(t)).append("]")
                }
            } finally {
                bmp.recycle()
            }
        }
        val best = votes.entries
            .maxWithOrNull(compareBy<Map.Entry<String, Int>>({ it.value }, { it.key.length }))
            ?.key
        if (best == null) {
            toastLater(ctx, "قرأ: $raw")
        } else if (votes.size > 1 || (votes[best] ?: 0) < 2) {
            toastLater(ctx, "القراءات مختلفة، راجع الرقم بعينك")
        }
        return best
    }

    private fun otsu(g: IntArray): Int {
        val hist = IntArray(256)
        for (v in g) hist[v]++
        val total = g.size
        var sum = 0L
        for (i in 0..255) sum += i.toLong() * hist[i]
        var sumB = 0L
        var wB = 0
        var best = 0.0
        var th = 128
        for (t in 0..255) {
            wB += hist[t]
            if (wB == 0) continue
            val wF = total - wB
            if (wF == 0) break
            sumB += t.toLong() * hist[t]
            val mB = sumB.toDouble() / wB
            val mF = (sum - sumB).toDouble() / wF
            val between = wB.toDouble() * wF * (mB - mF) * (mB - mF)
            if (between > best) {
                best = between
                th = t
            }
        }
        return th
    }

    private fun prep(src: Bitmap, binarize: Boolean): Bitmap {
        val minSide = min(src.width, src.height).coerceAtLeast(1)
        val scale = if (max(src.width, src.height) < 1200) (100f / minSide).coerceIn(1f, 4f) else 1f
        val w = (src.width * scale).toInt().coerceAtLeast(1)
        val h = (src.height * scale).toInt().coerceAtLeast(1)
        val scaled = if (w == src.width && h == src.height) src
        else Bitmap.createScaledBitmap(src, w, h, true)
        val px = IntArray(w * h)
        scaled.getPixels(px, 0, w, 0, 0, w, h)
        if (scaled !== src) scaled.recycle()

        val g = IntArray(w * h)
        var sum = 0L
        for (i in px.indices) {
            val c = px[i]
            val v = (Color.red(c) * 299 + Color.green(c) * 587 + Color.blue(c) * 114) / 1000
            g[i] = v
            sum += v
        }
        // خلفية داكنة: نقلب الألوان لأن Tesseract يفضل نصاً داكناً على فاتح
        if (sum / g.size < 110) for (i in g.indices) g[i] = 255 - g[i]
        if (binarize) {
            val th = otsu(g)
            for (i in g.indices) g[i] = if (g[i] > th) 255 else 0
        } else {
            for (i in g.indices) g[i] = (((g[i] - 128) * 1.4f) + 128f).toInt().coerceIn(0, 255)
        }

        val pad = max(10, h / 3)
        val out = Bitmap.createBitmap(w + 2 * pad, h + 2 * pad, Bitmap.Config.ARGB_8888)
        out.eraseColor(Color.WHITE)
        for (i in g.indices) {
            val v = g[i]
            px[i] = Color.rgb(v, v, v)
        }
        out.setPixels(px, 0, w, pad, pad, w, h)
        return out
    }
}
