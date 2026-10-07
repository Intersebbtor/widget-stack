package io.github.intersebbtor.widgetstack

import android.app.NotificationManager
import android.content.ComponentName
import android.content.Context
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState

object MediaSessions {

    fun listenerComponent(ctx: Context) = ComponentName(ctx, MediaListenerService::class.java)

    fun hasAccess(ctx: Context): Boolean =
        ctx.getSystemService(NotificationManager::class.java)
            .isNotificationListenerAccessGranted(listenerComponent(ctx))

    /** Active controllers keyed by package, playing session wins. Null = no access. */
    fun controllers(ctx: Context): Map<String, MediaController>? {
        if (!hasAccess(ctx)) return null
        val msm = ctx.getSystemService(MediaSessionManager::class.java)
        val list = try {
            msm.getActiveSessions(listenerComponent(ctx))
        } catch (e: SecurityException) {
            return null
        }
        val map = LinkedHashMap<String, MediaController>()
        for (c in list) {
            val cur = map[c.packageName]
            if (cur == null || (!isPlaying(cur) && isPlaying(c))) map[c.packageName] = c
        }
        return map
    }

    fun controllerFor(ctx: Context, pkg: String): MediaController? = controllers(ctx)?.get(pkg)

    fun isPlaying(c: MediaController): Boolean = when (c.playbackState?.state) {
        PlaybackState.STATE_PLAYING,
        PlaybackState.STATE_BUFFERING,
        PlaybackState.STATE_CONNECTING -> true
        else -> false
    }

    fun title(md: MediaMetadata?): String? =
        md?.getString(MediaMetadata.METADATA_KEY_TITLE).nullIfBlank()
            ?: md?.getString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE).nullIfBlank()

    fun subtitle(md: MediaMetadata?): String? =
        md?.getString(MediaMetadata.METADATA_KEY_ARTIST).nullIfBlank()
            ?: md?.getString(MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE).nullIfBlank()
            ?: md?.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST).nullIfBlank()
            ?: md?.getString(MediaMetadata.METADATA_KEY_AUTHOR).nullIfBlank()
            ?: md?.getString(MediaMetadata.METADATA_KEY_ALBUM).nullIfBlank()

    fun art(md: MediaMetadata?) =
        md?.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
            ?: md?.getBitmap(MediaMetadata.METADATA_KEY_ART)
            ?: md?.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON)

    /** Custom seek actions (Spotify podcasts, Audible) when standard skip/seek is missing. */
    fun customSeek(c: MediaController?, forward: Boolean): PlaybackState.CustomAction? {
        val words = if (forward) listOf("forward", "vor") else listOf("back", "rewind", "zurück")
        return c?.playbackState?.customActions?.firstOrNull { a ->
            val n = "${a.name} ${a.action}".lowercase()
            words.any { it in n } && "speed" !in n
        }
    }

    fun canSeek(c: MediaController?, forward: Boolean): Boolean {
        val actions = c?.playbackState?.actions ?: return false
        val std = if (forward) PlaybackState.ACTION_SKIP_TO_NEXT or PlaybackState.ACTION_FAST_FORWARD
        else PlaybackState.ACTION_SKIP_TO_PREVIOUS or PlaybackState.ACTION_REWIND
        return actions and std != 0L || customSeek(c, forward) != null
    }

    private fun String?.nullIfBlank() = if (this.isNullOrBlank()) null else this
}
