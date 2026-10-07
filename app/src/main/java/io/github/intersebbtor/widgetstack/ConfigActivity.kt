package io.github.intersebbtor.widgetstack

import android.app.Activity
import android.app.AlertDialog
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.net.Uri
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.view.WindowInsets
import android.widget.BaseAdapter
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

class ConfigActivity : Activity() {

    private var widgetId = AppWidgetManager.INVALID_APPWIDGET_ID
    /** True when opened by the launcher while adding a widget. */
    private var addFlow = false
    private val slots = arrayOfNulls<String>(Prefs.SLOTS)
    private var rows = Prefs.DEFAULT_ROWS
    private lateinit var content: LinearLayout

    private val known = listOf(
        "com.spotify.music",
        "com.audible.application",
        "au.com.shiftyjelly.pocketcasts",
        "com.google.android.apps.youtube.music",
        "com.amazon.mp3",
        "deezer.android.app",
        "com.aspiro.tidal",
        "com.apple.android.music",
        "de.danoeh.antennapod",
        "com.soundcloud.android",
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestWindowFeature(Window.FEATURE_NO_TITLE)
        readIntent(intent)

        val scroll = ScrollView(this).apply { isFillViewport = true }
        content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(28), dp(20), dp(24))
        }
        scroll.addView(content)
        scroll.setOnApplyWindowInsetsListener { v, insets ->
            val b = insets.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout())
            v.setPadding(b.left, b.top, b.right, b.bottom)
            insets
        }
        scroll.setBackgroundColor(getColor(R.color.widget_bg))
        setContentView(scroll)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        readIntent(intent)
        render()
    }

    override fun onResume() {
        super.onResume()
        render()
    }

    private fun readIntent(i: Intent) {
        widgetId = i.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
        addFlow = i.action == AppWidgetManager.ACTION_APPWIDGET_CONFIGURE
        if (widgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
            loadSlots()
            if (addFlow) setResult(RESULT_CANCELED, resultIntent())
        }
    }

    private fun loadSlots() {
        val saved = Prefs.slots(this, widgetId)
        rows = Prefs.rows(this, widgetId)
        if (Prefs.isConfigured(this, widgetId)) {
            saved.copyInto(slots)
        } else {
            suggest().forEachIndexed { i, p -> slots[i] = p }
        }
    }

    /** Pre-fill: running media apps first, then well-known ones that are installed. */
    private fun suggest(): List<String?> {
        val installed = MediaApps.mediaApps(this).map { it.pkg }.toSet()
        val active = MediaSessions.controllers(this)?.keys.orEmpty()
        val order = (active + known).filter { it in installed }.distinct()
        return List(Prefs.SLOTS) { order.getOrNull(it) }
    }

    private fun resultIntent() = Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)

    // ---------- UI ----------

    private fun render() {
        content.removeAllViews()
        content.addView(text(getString(R.string.app_name), 26f, bold = true))
        content.addView(text(
            if (widgetId != AppWidgetManager.INVALID_APPWIDGET_ID) getString(R.string.subtitle_edit)
            else getString(R.string.subtitle_home),
            15f, secondary = true, top = 4,
        ))

        renderSetup()

        if (widgetId != AppWidgetManager.INVALID_APPWIDGET_ID) renderSlots() else renderWidgetList()
    }

    private fun renderSetup() {
        val access = MediaSessions.hasAccess(this)
        val battery = getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(packageName)
        if (access && battery) {
            content.addView(statusLine(getString(R.string.all_set), ok = true))
            return
        }

        content.addView(sectionTitle(getString(R.string.sec_setup)))
        val card = card()

        card.addView(checkRow(
            getString(R.string.notif_title),
            if (access) getString(R.string.notif_ok)
            else getString(R.string.notif_missing),
            access,
            getString(R.string.grant),
        ) { openListenerSettings() })

        if (!access) {
            card.addView(text(
                getString(R.string.restricted_hint),
                13f, secondary = true, top = 8,
            ))
            card.addView(linkButton(getString(R.string.open_app_info)) {
                startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName")))
            })
        }

        card.addView(divider())

        card.addView(checkRow(
            getString(R.string.battery_title),
            if (battery) getString(R.string.battery_ok)
            else getString(R.string.battery_missing),
            battery,
            getString(R.string.allow),
        ) { requestBatteryExemption() })

        content.addView(card)
    }

    private fun renderSlots() {
        content.addView(sectionTitle(getString(R.string.sec_rows)))
        content.addView(card().apply { addView(rowCountPicker()) })

        content.addView(sectionTitle(getString(R.string.sec_players)))
        val card = card()
        for (i in 0 until rows) {
            if (i > 0) card.addView(divider())
            card.addView(slotRow(i))
        }
        content.addView(card)
        content.addView(text(
            getString(R.string.players_hint),
            13f, secondary = true, top = 8,
        ))

        content.addView(primaryButton(getString(if (addFlow) R.string.add_widget else R.string.save)) { save() })
    }

    private fun renderWidgetList() {
        val mgr = AppWidgetManager.getInstance(this)
        val ids = mgr.getAppWidgetIds(ComponentName(this, WidgetStackProvider::class.java))
        content.addView(sectionTitle(getString(R.string.sec_widgets)))
        if (ids.isEmpty()) {
            val card = card()
            card.addView(text(getString(R.string.no_widget), 16f, bold = true))
            card.addView(text(
                getString(R.string.no_widget_hint),
                14f, secondary = true, top = 4,
            ))
            content.addView(card)
            return
        }
        val card = card()
        ids.forEachIndexed { n, id ->
            if (n > 0) card.addView(divider())
            val names = Prefs.slots(this, id).take(Prefs.rows(this, id)).map { p -> p?.let { MediaApps.label(this, it) ?: it } ?: getString(R.string.empty_lower) }
            card.addView(row(
                icon = null,
                title = getString(R.string.widget_n, n + 1),
                subtitle = names.joinToString(" · "),
                trailing = text(getString(R.string.edit), 14f, accent = true),
            ) {
                startActivity(Intent(this, ConfigActivity::class.java)
                    .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id))
            })
        }
        content.addView(card)
    }

    private fun slotRow(i: Int): View {
        val pkg = slots[i]
        val label = pkg?.let { MediaApps.label(this, it) }
        val trailing = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            addView(iconButton("↑", enabled = i > 0) { swap(i, i - 1) })
            addView(iconButton("↓", enabled = i < rows - 1) { swap(i, i + 1) })
        }
        return row(
            icon = pkg?.let { MediaApps.icon(this, it) },
            title = label ?: getString(if (pkg != null) R.string.not_installed else R.string.empty),
            subtitle = getString(if (pkg == null) R.string.row_tap_choose else R.string.row_n, i + 1),
            trailing = trailing,
        ) { pick(i) }
    }

    private fun rowCountPicker(): View = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        addView(text(getString(R.string.rows_question), 15f, bold = true))
        addView(LinearLayout(this@ConfigActivity).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(48)).apply { topMargin = dp(12) }
            for (n in 1..Prefs.SLOTS) {
                val selected = n == rows
                addView(TextView(this@ConfigActivity).apply {
                    text = n.toString()
                    textSize = 17f
                    typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                    gravity = Gravity.CENTER
                    setTextColor(getColor(if (selected) R.color.play_icon else R.color.text_primary))
                    background = ripple(rounded(if (selected) R.color.play_bg else R.color.slot_active_bg, 14), 14)
                    contentDescription = getString(R.string.rows_cd, n)
                    layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f).apply {
                        if (n > 1) marginStart = dp(8)
                    }
                    setOnClickListener {
                        rows = n
                        render()
                    }
                })
            }
        })
        addView(text(
            getString(R.string.rows_hint),
            13f, secondary = true, top = 10,
        ))
    }

    private fun swap(a: Int, b: Int) {
        val t = slots[a]; slots[a] = slots[b]; slots[b] = t
        render()
    }

    private fun pick(slot: Int, all: Boolean = false) {
        val apps = if (all) MediaApps.allApps(this) else MediaApps.mediaApps(this)
        val entries = mutableListOf<AppEntry?>(null)
        entries += apps
        if (!all) entries += AppEntry("__more__", getString(R.string.other_app))

        val adapter = object : BaseAdapter() {
            override fun getCount() = entries.size
            override fun getItem(p: Int) = entries[p]
            override fun getItemId(p: Int) = p.toLong()
            override fun getView(p: Int, convert: View?, parent: ViewGroup?): View {
                val e = entries[p]
                val selected = e?.pkg == slots[slot]
                return row(
                    icon = e?.pkg?.takeIf { it != "__more__" }?.let { MediaApps.icon(this@ConfigActivity, it) },
                    title = e?.label ?: getString(R.string.leave_empty),
                    subtitle = null,
                    trailing = if (selected) text("✓", 18f, accent = true, bold = true) else null,
                    padH = 24,
                    onClick = null,
                )
            }
        }

        AlertDialog.Builder(this)
            .setTitle(if (all) getString(R.string.all_apps) else getString(R.string.player_for_row, slot + 1))
            .setAdapter(adapter) { d, which ->
                val e = entries[which]
                d.dismiss()
                when {
                    e?.pkg == "__more__" -> pick(slot, all = true)
                    else -> {
                        slots[slot] = e?.pkg
                        render()
                    }
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun save() {
        Prefs.saveSlots(this, widgetId, slots, rows)
        WidgetRenderer.update(this, AppWidgetManager.getInstance(this), intArrayOf(widgetId))
        setResult(RESULT_OK, resultIntent())
        finish()
    }

    private fun openListenerSettings() {
        val cn = MediaSessions.listenerComponent(this)
        val detail = Intent(Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS)
            .putExtra(Settings.EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME, cn.flattenToString())
        runCatching { startActivity(detail) }.onFailure {
            startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
        }
    }

    private fun requestBatteryExemption() {
        val i = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:$packageName"))
        runCatching { startActivity(i) }.onFailure {
            startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
        }
    }

    // ---------- view helpers ----------

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun text(
        s: String, size: Float, bold: Boolean = false, secondary: Boolean = false,
        accent: Boolean = false, top: Int = 0,
    ) = TextView(this).apply {
        text = s
        textSize = size
        setTextColor(getColor(when {
            accent -> R.color.play_bg
            secondary -> R.color.text_secondary
            else -> R.color.text_primary
        }))
        if (bold) typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            .apply { topMargin = dp(top) }
    }

    private fun sectionTitle(s: String) = text(s.uppercase(), 13f, bold = true, secondary = true, top = 28).apply {
        letterSpacing = 0.08f
        setPadding(dp(4), 0, 0, dp(8))
    }

    private fun rounded(colorRes: Int, radius: Int) = GradientDrawable().apply {
        setColor(getColor(colorRes))
        cornerRadius = dp(radius).toFloat()
    }

    private fun ripple(content: Drawable?, radius: Int) = RippleDrawable(
        ColorStateList.valueOf(0x22888888),
        content,
        GradientDrawable().apply { setColor(0xFF000000.toInt()); cornerRadius = dp(radius).toFloat() },
    )

    private fun card() = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        background = rounded(R.color.slot_bg, 20)
        setPadding(dp(16), dp(12), dp(16), dp(14))
        clipToOutline = true
    }

    private fun divider() = View(this).apply {
        setBackgroundColor(getColor(R.color.text_secondary))
        alpha = 0.15f
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 1)
            .apply { topMargin = dp(8); bottomMargin = dp(8) }
    }

    private fun statusLine(s: String, ok: Boolean) = text(s, 14f, bold = true, accent = ok, top = 16).apply {
        background = rounded(R.color.slot_active_bg, 50)
        setPadding(dp(14), dp(8), dp(14), dp(8))
    }

    private fun row(
        icon: Drawable?, title: String, subtitle: String?, trailing: View?,
        padH: Int = 0, onClick: (() -> Unit)?,
    ): View = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        minimumHeight = dp(56)
        setPadding(dp(padH), dp(6), dp(padH), dp(6))
        if (onClick != null) {
            background = ripple(null, 12)
            setOnClickListener { onClick() }
        }
        addView(ImageView(this@ConfigActivity).apply {
            if (icon != null) setImageDrawable(icon)
            else {
                background = rounded(R.color.slot_active_bg, 10)
            }
            layoutParams = LinearLayout.LayoutParams(dp(40), dp(40)).apply { marginEnd = dp(14) }
            visibility = if (icon == null && onClick == null && padH > 0) View.INVISIBLE else View.VISIBLE
        })
        addView(LinearLayout(this@ConfigActivity).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            addView(text(title, 16f, bold = true).apply { maxLines = 1; ellipsize = TextUtils.TruncateAt.END })
            if (subtitle != null) addView(text(subtitle, 13f, secondary = true, top = 2).apply {
                maxLines = 1; ellipsize = TextUtils.TruncateAt.END
            })
        })
        if (trailing != null) addView(trailing)
    }

    private fun checkRow(title: String, desc: String, ok: Boolean, action: String, onAction: () -> Unit): View =
        LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(4), 0, dp(4))
            addView(text(if (ok) "✓" else "!", 16f, bold = true).apply {
                gravity = Gravity.CENTER
                setTextColor(getColor(if (ok) R.color.play_icon else R.color.text_primary))
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(getColor(if (ok) R.color.play_bg else R.color.slot_active_bg))
                }
                layoutParams = LinearLayout.LayoutParams(dp(32), dp(32)).apply { marginEnd = dp(14) }
            })
            addView(LinearLayout(this@ConfigActivity).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                addView(text(title, 16f, bold = true))
                addView(text(desc, 13f, secondary = true, top = 2))
            })
            if (!ok) addView(pillButton(action, onAction).apply {
                (layoutParams as LinearLayout.LayoutParams).marginStart = dp(10)
            })
        }

    private fun pillButton(label: String, onClick: () -> Unit) = TextView(this).apply {
        text = label
        textSize = 14f
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        setTextColor(getColor(R.color.play_icon))
        gravity = Gravity.CENTER
        minHeight = dp(40)
        setPadding(dp(16), 0, dp(16), 0)
        background = ripple(rounded(R.color.play_bg, 50), 50)
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        setOnClickListener { onClick() }
    }

    private fun linkButton(label: String, onClick: () -> Unit) = text(label, 14f, bold = true, accent = true, top = 4).apply {
        minHeight = dp(40)
        gravity = Gravity.CENTER_VERTICAL
        setPadding(0, dp(4), dp(8), dp(4))
        setOnClickListener { onClick() }
    }

    private fun primaryButton(label: String, onClick: () -> Unit) = pillButton(label, onClick).apply {
        textSize = 16f
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(56)).apply { topMargin = dp(24) }
    }

    private fun iconButton(label: String, enabled: Boolean, onClick: () -> Unit) = TextView(this).apply {
        text = label
        textSize = 18f
        gravity = Gravity.CENTER
        setTextColor(getColor(R.color.text_primary))
        alpha = if (enabled) 1f else 0.25f
        isEnabled = enabled
        background = ripple(null, 24)
        layoutParams = LinearLayout.LayoutParams(dp(44), dp(44))
        if (enabled) setOnClickListener { onClick() }
    }
}
