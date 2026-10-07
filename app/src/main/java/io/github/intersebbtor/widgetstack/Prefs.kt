package io.github.intersebbtor.widgetstack

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.File

/** Slot config per widget + last-known track per app. */
object Prefs {
    const val SLOTS = 4
    const val DEFAULT_ROWS = 3
    private const val FILE = "widget_stack"

    private fun sp(ctx: Context) = ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun slots(ctx: Context, widgetId: Int): Array<String?> =
        Array(SLOTS) { sp(ctx).getString("w_${widgetId}_$it", null) }

    fun rows(ctx: Context, widgetId: Int) = sp(ctx).getInt("w_${widgetId}_rows", DEFAULT_ROWS).coerceIn(1, SLOTS)

    /** Hidden slots beyond [rows] are kept, so growing again restores them. */
    fun saveSlots(ctx: Context, widgetId: Int, pkgs: Array<String?>, rows: Int) {
        val e = sp(ctx).edit()
        e.putInt("w_${widgetId}_rows", rows)
        pkgs.forEachIndexed { i, p ->
            if (p == null) e.remove("w_${widgetId}_$i") else e.putString("w_${widgetId}_$i", p)
        }
        e.putBoolean("w_${widgetId}_configured", true).apply()
    }

    fun isConfigured(ctx: Context, widgetId: Int) = sp(ctx).getBoolean("w_${widgetId}_configured", false)

    fun clear(ctx: Context, widgetId: Int) {
        val e = sp(ctx).edit()
        repeat(SLOTS) { e.remove("w_${widgetId}_$it") }
        e.remove("w_${widgetId}_configured").remove("w_${widgetId}_rows").apply()
    }

    data class Last(val title: String, val subtitle: String?)

    fun last(ctx: Context, pkg: String): Last? {
        val t = sp(ctx).getString("last_t_$pkg", null) ?: return null
        return Last(t, sp(ctx).getString("last_s_$pkg", null))
    }

    fun saveLast(ctx: Context, pkg: String, title: String, subtitle: String?, art: Bitmap?) {
        val old = last(ctx, pkg)
        if (old?.title == title && old.subtitle == subtitle && artFile(ctx, pkg).exists() == (art != null)) return
        sp(ctx).edit().putString("last_t_$pkg", title).putString("last_s_$pkg", subtitle).apply()
        val f = artFile(ctx, pkg)
        if (art == null) {
            f.delete()
        } else {
            runCatching { f.outputStream().use { scale(art).compress(Bitmap.CompressFormat.PNG, 100, it) } }
        }
    }

    fun lastArt(ctx: Context, pkg: String): Bitmap? {
        val f = artFile(ctx, pkg)
        return if (f.exists()) BitmapFactory.decodeFile(f.path) else null
    }

    private fun artFile(ctx: Context, pkg: String) = File(ctx.filesDir, "art_$pkg.png")

    /** RemoteViews have an IPC size cap. Keep art small. */
    fun scale(b: Bitmap, max: Int = 160): Bitmap {
        val s = maxOf(b.width, b.height)
        if (s <= max) return b
        val f = max.toFloat() / s
        return Bitmap.createScaledBitmap(b, (b.width * f).toInt().coerceAtLeast(1), (b.height * f).toInt().coerceAtLeast(1), true)
    }
}
