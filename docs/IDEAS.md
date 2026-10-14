# Ideas

## Left-handed mode

**Source:** Reddit comment (TamsinKodaTe, 2026-10-14).

Toggle to mirror each row: controls (prev / play / next) on the left, cover art and title/artist on the right. Default stays as is.

**Implementation notes**
- Per-widget setting (like `rows`): new pref `w_<id>_left` in `Prefs.kt`, toggle in `ConfigActivity`.
- RemoteViews cannot reorder children at runtime, so add a mirrored layout `widget_slot_left.xml` (same view ids) and let `WidgetRenderer.buildSlot` pick the layout by pref.
- Alternative: set layout direction RTL on the slot root. Cheaper, but would also flip text alignment and icon drawables. Check before choosing.
- Add EN/DE strings for the toggle label.
- Update `docs/config.png` and README feature list when shipped.
