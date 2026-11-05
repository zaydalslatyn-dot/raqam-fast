package com.raqam.fast

import android.graphics.Bitmap
import android.util.Base64
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/** احتياطي: يعمل فقط إذا وُضع مفتاح OCR_API_KEY أثناء البناء */
object ServerOcr {
    fun read(bmp: Bitmap): String? = try {
        val bo = ByteArrayOutputStream()
        bmp.compress(Bitmap.CompressFormat.JPEG, 85, bo)
        val b64 = Base64.encodeToString(bo.toByteArray(), Base64.NO_WRAP)
        val body = "apikey=" + BuildConfig.OCR_API_KEY +
            "&language=ara&OCREngine=1&scale=true&base64Image=" +
            URLEncoder.encode("data:image/jpeg;base64,$b64", "UTF-8")
        val c = URL("https://api.ocr.space/parse/image").openConnection() as HttpURLConnection
        c.requestMethod = "POST"
        c.doOutput = true
        c.connectTimeout = 8000
        c.readTimeout = 12000
        c.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
        c.outputStream.use { it.write(body.toByteArray()) }
        val s = c.inputStream.bufferedReader().readText()
        val arr = JSONObject(s).optJSONArray("ParsedResults")
        Digits.extract(arr?.optJSONObject(0)?.optString("ParsedText") ?: "")
    } catch (e: Exception) {
        null
    }
}
