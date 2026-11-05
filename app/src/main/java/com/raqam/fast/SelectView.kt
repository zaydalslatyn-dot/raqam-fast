package com.raqam.fast

import android.content.Context
import android.graphics.*
import android.view.MotionEvent
import android.view.View
import kotlin.math.max
import kotlin.math.min

class SelectView(
    ctx: Context,
    private val bmp: Bitmap,
    private val onDone: (Rect?) -> Unit
) : View(ctx) {
    private var sel: RectF? = null
    private var sx = 0f
    private var sy = 0f
    private val dim = Paint().apply { color = 0x99000000.toInt() }
    private val border = Paint().apply {
        color = 0xFF2E9BFF.toInt(); style = Paint.Style.STROKE; strokeWidth = 5f; isAntiAlias = true
    }
    private val txt = Paint().apply {
        color = Color.WHITE; textSize = 46f; textAlign = Paint.Align.CENTER; isAntiAlias = true
        setShadowLayer(8f, 0f, 0f, Color.BLACK)
    }
    private val dst = Rect()

    override fun onDraw(c: Canvas) {
        dst.set(0, 0, width, height)
        c.drawBitmap(bmp, null, dst, null)
        val s = sel
        c.save()
        if (s != null) c.clipOutRect(s)
        c.drawRect(0f, 0f, width.toFloat(), height.toFloat(), dim)
        c.restore()
        if (s != null) {
            c.drawRect(s, border)
        } else {
            c.drawText("اسحب حول الرقم لتحديده", width / 2f, height * 0.12f, txt)
            c.drawText("لمسة واحدة = إلغاء", width / 2f, height * 0.12f + 64f, txt)
        }
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        when (e.action) {
            MotionEvent.ACTION_DOWN -> { sx = e.x; sy = e.y; sel = null }
            MotionEvent.ACTION_MOVE -> {
                sel = RectF(min(sx, e.x), min(sy, e.y), max(sx, e.x), max(sy, e.y))
                invalidate()
            }
            MotionEvent.ACTION_CANCEL -> onDone(null)
            MotionEvent.ACTION_UP -> {
                val s = sel
                if (s == null || s.width() < 30f || s.height() < 20f) {
                    onDone(null)
                } else {
                    val kx = bmp.width.toFloat() / width
                    val ky = bmp.height.toFloat() / height
                    val pad = 10
                    onDone(
                        Rect(
                            ((s.left * kx).toInt() - pad).coerceAtLeast(0),
                            ((s.top * ky).toInt() - pad).coerceAtLeast(0),
                            ((s.right * kx).toInt() + pad).coerceAtMost(bmp.width),
                            ((s.bottom * ky).toInt() + pad).coerceAtMost(bmp.height)
                        )
                    )
                }
            }
        }
        return true
    }
}
