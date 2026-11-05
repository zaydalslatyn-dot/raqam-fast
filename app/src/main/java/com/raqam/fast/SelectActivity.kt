package com.raqam.fast

import android.app.Activity
import android.graphics.Rect
import android.os.Bundle
import android.view.View
import android.view.WindowManager

class SelectActivity : Activity() {
    private var done = false

    @Suppress("DEPRECATION")
    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        val bmp = CaptureService.pendingBitmap
        if (bmp == null) {
            finish()
            return
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS)
        window.attributes = window.attributes.apply {
            layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }
        window.decorView.systemUiVisibility =
            View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
                View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
                View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
                View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                View.SYSTEM_UI_FLAG_FULLSCREEN or
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
        setContentView(SelectView(this, bmp) { rect -> finishWith(rect) })
    }

    private fun finishWith(rect: Rect?) {
        if (done) return
        done = true
        CaptureService.instance?.onSelected(rect)
        finish()
        overridePendingTransition(0, 0)
    }

    override fun onBackPressed() {
        finishWith(null)
    }

    override fun onStop() {
        super.onStop()
        finishWith(null)
    }
}
