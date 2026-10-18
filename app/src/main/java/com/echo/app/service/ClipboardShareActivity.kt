package com.echo.app.service

import android.app.Activity
import com.echo.app.EchoApplication

/**
 * Invisible activity opened from the notification. Android only allows clipboard reads
 * for the focused app, so this takes focus for a moment, reads the clip and closes.
 */
class ClipboardShareActivity : Activity() {
    private var handled = false

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus && !handled) {
            handled = true
            (application as EchoApplication).container.clipboardAccess.captureNow(force = true)
            finish()
        }
    }
}
