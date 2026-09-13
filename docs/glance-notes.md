# Glance rendering-model traps

Two things that bit this widget and will bite again. Neither is a bug in Glance; both are
consequences of how it turns a composition into RemoteViews, and both fail silently.

## 1. A `LazyColumn` item with two root composables is drawn as a `Box`

Each `item { }` in a Glance `LazyColumn` becomes one RemoteViews entry. If the lambda emits
two root composables — say a `Spacer` meant as a hairline and then a `Row` — Glance has to
wrap them in a single container, and that container is a `Box`. The children are stacked on
top of each other, not laid out one after the other.

That is how the day dividers ended up drawn *through* the day-header text: the line was not
above the header, it was overlaid on it.

Rule: an `item` must emit exactly one root. Put a `Column` around anything that needs to
stack vertically, or move the extra element into its own `item`.

## 2. A locale change is invisible to Compose, so a re-render does nothing

Three separate facts combine here, and fixing any one of them alone is not enough.

**Glance widgets do not re-render on a locale change.** The last RemoteViews stays on screen
in the old language. Something has to trigger an update — a `BroadcastReceiver` for
`ACTION_LOCALE_CHANGED`, in this app `LocaleChangedReceiver`.

**A per-app locale change (Android 13+) arrives as plain `ACTION_LOCALE_CHANGED`.** Not as
`ACTION_APPLICATION_LOCALE_CHANGED`, which is never delivered to the app itself. Register
the plain one.

**`updateAll()` on a live session only recomposes, and Compose skips the body.** A locale
change touches no tracked state. `currentState()` values are unchanged, the composable's
parameters are unchanged, so Compose's skip is *correct* — from its point of view nothing
happened — and no new RemoteViews are produced. On top of that, `provideGlance()` runs once
per session, so anything resolved there (a localized context, a formatted string) is frozen
for the session's lifetime.

And one more: the broadcast is delivered *before* the process's own resources reflect the
new locale, so even a render that did happen would read the old strings.

What works, all three together:

- The receiver writes a nonce (`localeNonceKey`, any changing value) to every widget's state
  before calling `update`. That is a real state change, so the composition runs.
- `Content` reads that key, and resolves the locale on **every composition** from
  `LocaleManager.applicationLocales` (per-app wins over system), building a
  `createConfigurationContext` from it. Nothing locale-dependent is resolved in
  `provideGlance`.
- That localized context is provided as `LocalContext` for the whole tree, so every
  `getString` and the day-name formatting read from the same source.

Symptom if any leg is missing: the widget flips language only after the next sync or the
next tap, or never. Verified on device by toggling the per-app locale four times with no
manual refresh.

## 3. Fixed dp that ignores the font scale is a bug waiting to happen

`width(44.dp)` on the time column and `height(48.dp)` on the header were both deliberate
specifications, and both baked a default-scale assumption into a hard limit. At font scale
2.0 the first turned `10:15` into `1…` and the second clipped the caption - taking the
entity name, the only route to the picker, with it. A widget that cannot be rebound at max
scale is locked, not degraded, and locked for exactly the users who run that scale.

The trap is not fixed dp as such. The fixed 44dp column is *right* at default scale: the
times line up down one edge, and the text sits well inside the in-progress tint's corner
radius. Replacing it with `wrapContentWidth()` unconditionally fixed 2.0 and broke 1.0 -
the time hugged the tinted background's edge and the rounded corner ate the leading `2` of
`22:26`. Only on in-progress rows, so only during a lecture, so invisible to almost any
screenshot.

Rule: a fixed size is fine when it is chosen for the scale it renders at. Gate it on
`FontScale.isLargeText` and give the large branch its own sizing (content width plus an
inset) rather than removing the fixed size for everyone. Prefer minimums over ceilings
where a minimum is what you mean - the 48dp icons set the header's floor; pinning the row
to 48dp made it a ceiling.

Glance cannot measure text, so the scale itself is the only lever for "will it fit":
`FontScale.isLargeText` gates `maxLines = 2` on the densest lines, the stacked caption, and
the time column. One threshold, pinned by a test so a change to it is deliberate.

## 4. Week parity comes from a per-term constant, not the backend

The header's I / II parity is derived in `WeekParity` from an anchor: one Monday plus that
week's parity, counted forward in plain 7-day steps on epoch days. It is **not** read from
`/timetable/currentweek` for display.

Why: that endpoint is (ISO week + the Firebase node `savaite`) % 2. Observed values show
`savaite` is the department's *own* parity toggle (0 in the week of 08-31 = I, 1 in the
week of 09-07 = II, both matching the official timetable), so adding the ISO week to it
double-counts: in odd ISO weeks the endpoint returns the opposite of the truth (09-13:
`savaite`=1, endpoint=0). It was also edited mid-week once. Parity is the one thing on the
header that gets read, so it must not depend on that value. The `Parity disagreement`
warning therefore fires in every odd ISO week until the backend is fixed to return
`savaite` directly.

**The anchor is a per-term constant.** If the header drifts from the official timetable,
`WeekParity.ANCHOR_MONDAY` / `ANCHOR_PARITY` is what needs updating - a new term, or a
break the department did not count as a week. The sync still fetches the endpoint and logs
a `Parity disagreement` warning when the two differ, which is the cue to look here.

Epoch-day arithmetic rather than ISO week numbers on purpose: 2026 has an ISO week 53, so
ISO arithmetic gives the weeks of 2026-12-28 and 2027-01-04 the same parity.

## 5. Period times are an institutional constant the feed does not carry

The feed is an aSc Timetables export. A double lecture is one card with the block's outer
times, `uniperiod` = its first period and `durationperiods` = how many it spans (about a
fifth of all records). The widget keeps the block as one row - it is the department's card -
and labels it "Periods 2–3" from those two fields (`PeriodLabel`). The 45-minute break
inside such a block is therefore not shown; that is accepted.

If a row per period is ever wanted, the inner boundaries are not in the record. They come
from VIKO's fixed period grid, pinned here from the blocks the feed itself produces:

| period | time          |
|--------|---------------|
| 1      | 08:30 – 10:00 |
| 2      | 10:15 – 11:45 |
| 3      | 12:30 – 14:00 |
| 4      | 14:15 – 15:45 |
| 5      | 16:00 – 17:30 |
| 6      | 17:45 – 19:15 |
| 7      | 19:30 – 21:00 |

Like the parity anchor in §4 it is a per-institution constant, not data. Splitting would
also change change-matching: a cancellation filed under a block's first period would then
hit only that period's row.

## 6. A delayed WorkManager request is not a timer on Android 14+

`OneTimeWorkRequest` with `setInitialDelay(125s)`, no constraints, unique-name replaced,
enqueued from the sync. `RefreshChain` logs `Re-render in 125s`. `dumpsys jobscheduler`
lists the job with `TIMING_DELAY FLEXIBILITY` required and, once the delay has passed,
**every constraint satisfied and none unsatisfied**. The process is alive, the app is in
the ACTIVE standby bucket, the screen is on. The worker does not run. Polled for six and
a half minutes: nothing. `cmd jobscheduler run -f` runs it instantly and it behaves.

The mechanism: since Android 14, JobScheduler defers regular jobs - not expedited, not
user-initiated - for batching. A job whose constraints are all met is still not started
until the system decides to run a batch, which in practice happens when the next periodic
job (here the 15-minute sync) or some other app's job comes due. The delay is a floor, not
a schedule. No log line or dumpsys field says "deferred for batching"; the job simply
sits there looking ready.

Consequences for this widget:

- Nothing finer than the 15-minute sync can be promised without an exact alarm, and
  `SCHEDULE_EXACT_ALARM` is a user toggle on 14+ plus a permanent status-bar icon - not
  worth it for a widget. Inexact alarms are widened to ten-minute windows since Android 12.
- So the countdown ("2h 50m left") was dropped: rounded to what the platform guarantees
  it would have been coarser than the end time already on the row. The re-render chain is
  kept as best effort - free, invisible, and it does drop a stale tint at a lecture's end
  whenever a batch happens to run then.
- Any future "re-render at a boundary" idea must be verified by polling the device with no
  input for longer than the delay, watching the actual render. The arming log line, the
  job listing and even "all constraints satisfied" prove nothing.

The debug fixture has a row that ends two minutes after it loads (`baigiasi`), and a
`pin-current-week` marker so today's fixture rows are visible at the weekend. Both exist
for this kind of test.

## 7. A hex that lands in a theme role is decorative

For most of this widget's life the status colours were reviewed and signed off as fixed
values - a cancelled bar "at full strength", a moved bar in amber. In the code they were
`GlanceTheme.colors.outline` and `GlanceTheme.colors.tertiary`, and the theme was
`dynamicLightColorScheme(context)`: Material You, resolved from the wallpaper at render
time. On the emulator's wallpaper "cancelled" was a grey and "moved" was a purple. The hex
values in the design existed nowhere, and every screenshot agreed with the design because
one wallpaper happened to produce colours that read the same way.

Neither a code read nor a screenshot catches this. A code read sees a role name and
assumes the role is what the design says; a screenshot on one wallpaper shows one of
infinitely many resolutions. The check is one of two things:

- resolve the colour at render time and compare it to the specification (log it, or
  sample the capture's pixels), or
- view the same screen on two different wallpapers and confirm nothing that is meant to be
  fixed moved.

The widget is now on `WidgetPalette`, fixed pairs per role, and `GlanceTheme` is not in
the tree.

**Moved and cancelled have the same luminance on purpose.** Both status colours sit at AA
text contrast against the surface, which puts them at 1.02:1 against each other: the
amber bar is exactly as dark as the red one. Hue carries the distinction for most people.
For everyone else it is carried by the strikethrough on a cancelled subject and by the
words on the status line - "Cancelled" versus "→ Room 320". Those are load-bearing
accessibility features, not decoration. Anyone tempted to drop the strikethrough or the
status line to save a line of height needs to know that they are the only thing left
separating the two states for a reader who cannot use hue. The remaining dynamic surfaces are deliberate: the app's own screens (Material
You, `ui/theme`), and the launcher's themed (monochrome) icon. The first-drop
`initialLayout` used system theme attributes and now mirrors the palette through
`colors_widget.xml` (day and night).

## Related, cheaper to remember

- `initialLayout` is only shown on a genuine first drop. A launcher restart or rebind shows
  the launcher's own pending card, which the app cannot influence.
- Glance rc01's `ColorProviders` stops at `outline`; there is no `outlineVariant`.
- `Settings.Global` custom keys are not readable by the app on newer Android — the read
  silently returns the default. The debug fixture uses a marker file for that reason.
- To check "nothing changed at 1.0", crop the same region from both builds and view them
  magnified side by side. Eyeballing two full screenshots missed a clipped glyph.
