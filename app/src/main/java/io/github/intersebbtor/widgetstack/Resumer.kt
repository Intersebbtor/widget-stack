package io.github.intersebbtor.widgetstack

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.media.browse.MediaBrowser
import android.media.session.MediaController
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.service.media.MediaBrowserService
import android.view.KeyEvent

/**
 * Start playback in an app that has no active session.
 * 1. Connect to its MediaBrowserService (recent root, like system media resumption) and call play.
 * 2. Fallback: send media button PLAY to the package.
 */
object Resumer {

    fun resume(ctx: Context, pkg: String, done: () -> Unit) {
        val svc = ctx.packageManager
            .queryIntentServices(Intent(MediaBrowserService.SERVICE_INTERFACE).setPackage(pkg), 0)
            .firstOrNull()?.serviceInfo
        if (svc == null) {
            mediaButton(ctx, pkg)
            done()
            return
        }

        val handler = Handler(Looper.getMainLooper())
        var finished = false
        var browser: MediaBrowser? = null

        fun finish(ok: Boolean) {
            if (finished) return
            finished = true
            if (!ok) mediaButton(ctx, pkg)
            handler.postDelayed({
                runCatching { browser?.disconnect() }
                done()
            }, if (ok) 1500 else 300)
        }

        val extras = Bundle().apply { putBoolean(MediaBrowserService.BrowserRoot.EXTRA_RECENT, true) }
        browser = MediaBrowser(ctx, ComponentName(svc.packageName, svc.name), object : MediaBrowser.ConnectionCallback() {
            override fun onConnected() {
                val ok = runCatching {
                    MediaController(ctx, browser!!.sessionToken).transportControls.play()
                }.isSuccess
                finish(ok)
            }

            override fun onConnectionFailed() = finish(false)
            override fun onConnectionSuspended() = finish(false)
        }, extras)

        runCatching { browser.connect() }.onFailure { finish(false) }
        handler.postDelayed({ finish(false) }, 4000)
    }

    private fun mediaButton(ctx: Context, pkg: String) {
        for (a in intArrayOf(KeyEvent.ACTION_DOWN, KeyEvent.ACTION_UP)) {
            val i = Intent(Intent.ACTION_MEDIA_BUTTON)
                .setPackage(pkg)
                .putExtra(Intent.EXTRA_KEY_EVENT, KeyEvent(a, KeyEvent.KEYCODE_MEDIA_PLAY))
            runCatching { ctx.sendBroadcast(i) }
        }
    }
}
