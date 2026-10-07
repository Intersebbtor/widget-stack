package io.github.intersebbtor.widgetstack

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.os.Bundle
import android.service.notification.NotificationListenerService

class WidgetStackProvider : AppWidgetProvider() {

    override fun onUpdate(ctx: Context, mgr: AppWidgetManager, ids: IntArray) {
        WidgetRenderer.update(ctx, mgr, ids)
        // Listener killed by OEM? Ask system to rebind.
        if (MediaSessions.hasAccess(ctx)) {
            NotificationListenerService.requestRebind(MediaSessions.listenerComponent(ctx))
        }
    }

    override fun onAppWidgetOptionsChanged(ctx: Context, mgr: AppWidgetManager, id: Int, options: Bundle) {
        WidgetRenderer.update(ctx, mgr, intArrayOf(id))
    }

    override fun onDeleted(ctx: Context, ids: IntArray) {
        ids.forEach { Prefs.clear(ctx, it) }
    }
}
