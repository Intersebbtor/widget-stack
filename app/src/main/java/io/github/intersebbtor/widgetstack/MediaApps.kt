package io.github.intersebbtor.widgetstack

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import android.service.media.MediaBrowserService

data class AppEntry(val pkg: String, val label: String)

object MediaApps {

    /** Apps exposing a media browser or media button receiver. */
    fun mediaApps(ctx: Context): List<AppEntry> {
        val pm = ctx.packageManager
        val pkgs = mutableSetOf<String>()
        pm.queryIntentServices(Intent(MediaBrowserService.SERVICE_INTERFACE), 0)
            .forEach { pkgs += it.serviceInfo.packageName }
        pm.queryBroadcastReceivers(Intent(Intent.ACTION_MEDIA_BUTTON), 0)
            .forEach { pkgs += it.activityInfo.packageName }
        pkgs -= ctx.packageName
        return pkgs.filter { pm.getLaunchIntentForPackage(it) != null }
            .map { AppEntry(it, label(ctx, it) ?: it) }
            .sortedBy { it.label.lowercase() }
    }

    fun allApps(ctx: Context): List<AppEntry> {
        val pm = ctx.packageManager
        val i = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        return pm.queryIntentActivities(i, 0)
            .map { it.activityInfo.packageName }
            .distinct()
            .filter { it != ctx.packageName }
            .map { AppEntry(it, label(ctx, it) ?: it) }
            .sortedBy { it.label.lowercase() }
    }

    fun label(ctx: Context, pkg: String): String? = try {
        val pm = ctx.packageManager
        pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
    } catch (e: PackageManager.NameNotFoundException) {
        null
    }

    fun icon(ctx: Context, pkg: String): Drawable? = try {
        ctx.packageManager.getApplicationIcon(pkg)
    } catch (e: PackageManager.NameNotFoundException) {
        null
    }

    private val iconCache = HashMap<String, Bitmap>()

    fun iconBitmap(ctx: Context, pkg: String, px: Int = 72): Bitmap? = synchronized(iconCache) {
        iconCache[pkg] ?: icon(ctx, pkg)?.let { d ->
            val b = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888)
            d.setBounds(0, 0, px, px)
            d.draw(Canvas(b))
            iconCache[pkg] = b
            b
        }
    }
}
