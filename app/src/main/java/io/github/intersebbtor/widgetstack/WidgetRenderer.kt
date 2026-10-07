package io.github.intersebbtor.widgetstack

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.media.session.MediaController
import android.net.Uri
import android.util.TypedValue
import android.view.View
import android.widget.RemoteViews

object WidgetRenderer {

    fun updateAll(ctx: Context) {
        val mgr = AppWidgetManager.getInstance(ctx)
        val ids = mgr.getAppWidgetIds(ComponentName(ctx, WidgetStackProvider::class.java))
        if (ids.isNotEmpty()) update(ctx, mgr, ids)
    }

    fun update(ctx: Context, mgr: AppWidgetManager, ids: IntArray) {
        val sessions = MediaSessions.controllers(ctx)
        for (id in ids) {
            runCatching { mgr.updateAppWidget(id, build(ctx, mgr, id, sessions)) }
        }
    }

    private fun build(
        ctx: Context,
        mgr: AppWidgetManager,
        widgetId: Int,
        sessions: Map<String, MediaController>?,
    ): RemoteViews {
        val root = RemoteViews(ctx.packageName, R.layout.widget_root)
        root.removeAllViews(R.id.slots)
        val minWidth = mgr.getAppWidgetOptions(widgetId).getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 300)
        val compact = minWidth in 1 until 250
        val configured = Prefs.isConfigured(ctx, widgetId)
        val pkgs = Prefs.slots(ctx, widgetId)
        for (slot in 0 until Prefs.rows(ctx, widgetId)) {
            root.addView(R.id.slots, buildSlot(ctx, widgetId, slot, pkgs[slot], configured, sessions, compact))
        }
        return root
    }

    private fun buildSlot(
        ctx: Context,
        widgetId: Int,
        slot: Int,
        pkg: String?,
        configured: Boolean,
        sessions: Map<String, MediaController>?,
        compact: Boolean,
    ): RemoteViews {
        val rv = RemoteViews(ctx.packageName, R.layout.widget_slot)
        rv.setViewVisibility(R.id.btn_prev, if (compact) View.GONE else View.VISIBLE)
        val configIntent = configPending(ctx, widgetId)

        // Empty slot
        if (pkg == null) {
            rv.setTextViewText(R.id.title, if (configured) ctx.getString(R.string.slot_empty) else ctx.getString(R.string.slot_setup, slot + 1))
            rv.setTextViewText(R.id.subtitle, ctx.getString(R.string.slot_tap_choose))
            rv.setImageViewResource(R.id.art, R.drawable.ic_add)
            rv.setViewVisibility(R.id.app_badge, View.GONE)
            hideControls(rv)
            rv.setOnClickPendingIntent(R.id.slot_root, configIntent)
            return rv
        }

        val label = MediaApps.label(ctx, pkg)
        if (label == null) {
            rv.setTextViewText(R.id.title, ctx.getString(R.string.app_not_installed))
            rv.setTextViewText(R.id.subtitle, ctx.getString(R.string.tap_rechoose))
            rv.setImageViewResource(R.id.art, R.drawable.ic_add)
            rv.setViewVisibility(R.id.app_badge, View.GONE)
            hideControls(rv)
            rv.setOnClickPendingIntent(R.id.slot_root, configIntent)
            return rv
        }

        val appIcon = MediaApps.iconBitmap(ctx, pkg)
        if (appIcon != null) rv.setImageViewBitmap(R.id.app_badge, appIcon)
        else rv.setViewVisibility(R.id.app_badge, View.GONE)

        // No notification access: everything leads to setup
        if (sessions == null) {
            rv.setTextViewText(R.id.title, label)
            rv.setTextViewText(R.id.subtitle, ctx.getString(R.string.access_missing))
            rv.setImageViewResource(R.id.art, R.drawable.ic_lock)
            hideControls(rv)
            rv.setOnClickPendingIntent(R.id.slot_root, configIntent)
            return rv
        }

        val ctrl = sessions[pkg]
        val playing: Boolean
        val title: String
        val subtitle: String
        var art: Bitmap?

        if (ctrl != null) {
            val md = ctrl.metadata
            val t = MediaSessions.title(md)
            val s = MediaSessions.subtitle(md)
            art = MediaSessions.art(md)?.let { Prefs.scale(it) }
            if (t != null) Prefs.saveLast(ctx, pkg, t, s, art)
            playing = MediaSessions.isPlaying(ctrl)
            title = t ?: label
            subtitle = when {
                s != null && playing -> s
                s != null -> ctx.getString(R.string.paused_fmt, s)
                playing -> label
                else -> ctx.getString(R.string.paused_fmt, label)
            }
        } else {
            val last = Prefs.last(ctx, pkg)
            art = Prefs.lastArt(ctx, pkg)
            playing = false
            title = last?.title ?: label
            subtitle = if (last != null) ctx.getString(R.string.resume_fmt, label) else ctx.getString(R.string.not_running)
        }

        rv.setTextViewText(R.id.title, title)
        rv.setTextViewText(R.id.subtitle, subtitle)
        if (art != null) {
            rv.setImageViewBitmap(R.id.art, art)
            rv.setViewPadding(R.id.art, 0, 0, 0, 0)
        } else if (appIcon != null) {
            // App icon as art, badge would be redundant
            rv.setImageViewBitmap(R.id.art, appIcon)
            rv.setViewPadding(R.id.art, 0, 0, 0, 0)
            rv.setViewVisibility(R.id.app_badge, View.GONE)
        }
        rv.setViewOutlinePreferredRadius(R.id.art, 12f, TypedValue.COMPLEX_UNIT_DIP)

        if (playing) rv.setInt(R.id.slot_root, "setBackgroundResource", R.drawable.slot_active_bg)
        rv.setImageViewResource(R.id.btn_play, if (playing) R.drawable.ic_pause else R.drawable.ic_play)
        rv.setContentDescription(R.id.btn_play, ctx.getString(if (playing) R.string.cd_pause_fmt else R.string.cd_play_fmt, label))

        rv.setOnClickPendingIntent(R.id.btn_play, control(ctx, widgetId, slot, pkg, ControlReceiver.ACTION_PLAY_PAUSE))

        setControl(rv, R.id.btn_prev, MediaSessions.canSeek(ctrl, forward = false),
            control(ctx, widgetId, slot, pkg, ControlReceiver.ACTION_PREV))
        setControl(rv, R.id.btn_next, MediaSessions.canSeek(ctrl, forward = true),
            control(ctx, widgetId, slot, pkg, ControlReceiver.ACTION_NEXT))

        val open = ctrl?.sessionActivity ?: launchPending(ctx, widgetId, slot, pkg)
        if (open != null) {
            rv.setOnClickPendingIntent(R.id.info, open)
            rv.setOnClickPendingIntent(R.id.art_box, open)
        }
        return rv
    }

    private fun setControl(rv: RemoteViews, id: Int, enabled: Boolean, pi: PendingIntent) {
        if (enabled) {
            rv.setOnClickPendingIntent(id, pi)
        } else {
            rv.setInt(id, "setImageAlpha", 70)
        }
    }

    private fun hideControls(rv: RemoteViews) {
        rv.setViewVisibility(R.id.btn_prev, View.GONE)
        rv.setViewVisibility(R.id.btn_play, View.GONE)
        rv.setViewVisibility(R.id.btn_next, View.GONE)
    }

    private const val FLAGS = PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT

    private fun control(ctx: Context, widgetId: Int, slot: Int, pkg: String, action: String): PendingIntent {
        val i = Intent(ctx, ControlReceiver::class.java)
            .setAction(action)
            .setData(Uri.parse("widgetstack://ctl/$widgetId/$slot/$action"))
            .putExtra(ControlReceiver.EXTRA_PKG, pkg)
        return PendingIntent.getBroadcast(ctx, 0, i, FLAGS)
    }

    private fun launchPending(ctx: Context, widgetId: Int, slot: Int, pkg: String): PendingIntent? {
        val i = ctx.packageManager.getLaunchIntentForPackage(pkg) ?: return null
        i.data = Uri.parse("widgetstack://open/$widgetId/$slot")
        return PendingIntent.getActivity(ctx, widgetId * 10 + slot, i, FLAGS)
    }

    fun configPending(ctx: Context, widgetId: Int): PendingIntent {
        val i = Intent(ctx, ConfigActivity::class.java)
            .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
            .setData(Uri.parse("widgetstack://config/$widgetId"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        return PendingIntent.getActivity(ctx, widgetId, i, FLAGS)
    }
}
