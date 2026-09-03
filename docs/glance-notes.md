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

Why: that endpoint is (ISO week + a Firebase node) % 2, and the node was changed on a
Thursday once, so the same calendar week rendered as "II" on Wednesday and "I" on
Thursday. Parity is the one thing on the header that gets read, so it must not depend on
a value that can change mid-week.

**The anchor is a per-term constant.** If the header drifts from the official timetable,
`WeekParity.ANCHOR_MONDAY` / `ANCHOR_PARITY` is what needs updating - a new term, or a
break the department did not count as a week. The sync still fetches the endpoint and logs
a `Parity disagreement` warning when the two differ, which is the cue to look here.

Epoch-day arithmetic rather than ISO week numbers on purpose: 2026 has an ISO week 53, so
ISO arithmetic gives the weeks of 2026-12-28 and 2027-01-04 the same parity.

## Related, cheaper to remember

- `initialLayout` is only shown on a genuine first drop. A launcher restart or rebind shows
  the launcher's own pending card, which the app cannot influence.
- Glance rc01's `ColorProviders` stops at `outline`; there is no `outlineVariant`.
- `Settings.Global` custom keys are not readable by the app on newer Android — the read
  silently returns the default. The debug fixture uses a marker file for that reason.
- To check "nothing changed at 1.0", crop the same region from both builds and view them
  magnified side by side. Eyeballing two full screenshots missed a clipped glyph.
