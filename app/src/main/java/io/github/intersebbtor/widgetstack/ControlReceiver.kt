package io.github.intersebbtor.widgetstack

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.session.MediaController
import android.media.session.PlaybackState
import android.os.Handler
import android.os.Looper

class ControlReceiver : BroadcastReceiver() {

    companion object {
        const val ACTION_PLAY_PAUSE = "io.github.intersebbtor.widgetstack.PLAY_PAUSE"
        const val ACTION_NEXT = "io.github.intersebbtor.widgetstack.NEXT"
        const val ACTION_PREV = "io.github.intersebbtor.widgetstack.PREV"
        const val EXTRA_PKG = "pkg"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val ctx = context.applicationContext
        val pkg = intent.getStringExtra(EXTRA_PKG) ?: return
        val action = intent.action ?: return
        val ctrl = MediaSessions.controllerFor(ctx, pkg)
        val pending = goAsync()
        val handler = Handler(Looper.getMainLooper())

        // Refresh as fallback in case listener service is not running
        val finish = {
            handler.postDelayed({
                WidgetRenderer.updateAll(ctx)
                pending.finish()
            }, 700)
            Unit
        }

        if (ctrl != null) {
            perform(ctrl, action)
            finish()
        } else if (action == ACTION_PLAY_PAUSE) {
            Resumer.resume(ctx, pkg) { finish() }
        } else {
            finish()
        }
    }

    private fun seek(c: MediaController, actions: Long, forward: Boolean) {
        val tc = c.transportControls
        val skip = if (forward) PlaybackState.ACTION_SKIP_TO_NEXT else PlaybackState.ACTION_SKIP_TO_PREVIOUS
        val step = if (forward) PlaybackState.ACTION_FAST_FORWARD else PlaybackState.ACTION_REWIND
        val custom = MediaSessions.customSeek(c, forward)
        when {
            actions and skip != 0L -> if (forward) tc.skipToNext() else tc.skipToPrevious()
            actions and step != 0L -> if (forward) tc.fastForward() else tc.rewind()
            custom != null -> tc.sendCustomAction(custom, custom.extras)
        }
    }

    private fun perform(c: MediaController, action: String) {
        val tc = c.transportControls
        val actions = c.playbackState?.actions ?: 0L
        when (action) {
            ACTION_PLAY_PAUSE -> if (MediaSessions.isPlaying(c)) tc.pause() else tc.play()
            ACTION_NEXT -> seek(c, actions, forward = true)
            ACTION_PREV -> seek(c, actions, forward = false)
        }
    }
}
