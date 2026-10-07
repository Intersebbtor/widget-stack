package io.github.intersebbtor.widgetstack

import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSession
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Handler
import android.os.Looper
import android.service.notification.NotificationListenerService

/** Needed for MediaSessionManager access. Pushes widget updates on session changes. */
class MediaListenerService : NotificationListenerService() {

    private val handler = Handler(Looper.getMainLooper())
    private val refresh = Runnable { WidgetRenderer.updateAll(this) }
    private val tracked = HashMap<MediaSession.Token, Pair<MediaController, MediaController.Callback>>()
    private var msm: MediaSessionManager? = null

    private val sessionsListener = MediaSessionManager.OnActiveSessionsChangedListener { list ->
        track(list ?: emptyList())
    }

    override fun onListenerConnected() {
        val m = getSystemService(MediaSessionManager::class.java)
        msm = m
        val cn = MediaSessions.listenerComponent(this)
        runCatching {
            m.addOnActiveSessionsChangedListener(sessionsListener, cn, handler)
            track(m.getActiveSessions(cn))
        }
    }

    override fun onListenerDisconnected() {
        runCatching { msm?.removeOnActiveSessionsChangedListener(sessionsListener) }
        untrackAll()
        schedule()
        requestRebind(MediaSessions.listenerComponent(this))
    }

    override fun onDestroy() {
        untrackAll()
        super.onDestroy()
    }

    private fun track(list: List<MediaController>) {
        val tokens = list.map { it.sessionToken }.toSet()
        tracked.keys.filter { it !in tokens }.forEach { t ->
            tracked.remove(t)?.let { (c, cb) -> c.unregisterCallback(cb) }
        }
        for (c in list) {
            if (c.sessionToken in tracked) continue
            val cb = object : MediaController.Callback() {
                // Ignore pure position ticks
                private var last = c.playbackState?.let { it.state to it.actions }
                override fun onPlaybackStateChanged(state: PlaybackState?) {
                    val now = state?.let { it.state to it.actions }
                    if (now != last) {
                        last = now
                        schedule()
                    }
                }
                override fun onMetadataChanged(metadata: MediaMetadata?) = schedule()
                override fun onSessionDestroyed() = schedule()
            }
            c.registerCallback(cb, handler)
            tracked[c.sessionToken] = c to cb
        }
        schedule()
    }

    private fun untrackAll() {
        tracked.values.forEach { (c, cb) -> runCatching { c.unregisterCallback(cb) } }
        tracked.clear()
    }

    private fun schedule() {
        handler.removeCallbacks(refresh)
        handler.postDelayed(refresh, 250)
    }
}
