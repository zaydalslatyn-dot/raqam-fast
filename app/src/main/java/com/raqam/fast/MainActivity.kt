package com.raqam.fast

import android.app.Activity
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.os.Bundle
import android.widget.Toast

class MainActivity : Activity() {
    private val reqCode = 77

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        val svc = CaptureService.instance
        if (svc != null && svc.ready) {
            svc.startCapture()
            finish()
            overridePendingTransition(0, 0)
            return
        }
        val mpm = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        startActivityForResult(mpm.createScreenCaptureIntent(), reqCode)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == reqCode) {
            if (resultCode == RESULT_OK && data != null) {
                val i = Intent(this, CaptureService::class.java)
                    .putExtra("code", resultCode)
                    .putExtra("data", data)
                startForegroundService(i)
            } else {
                Toast.makeText(this, "لازم توافق على «ابدأ الآن» عشان يشتغل", Toast.LENGTH_LONG).show()
            }
        }
        finish()
        overridePendingTransition(0, 0)
    }
}
