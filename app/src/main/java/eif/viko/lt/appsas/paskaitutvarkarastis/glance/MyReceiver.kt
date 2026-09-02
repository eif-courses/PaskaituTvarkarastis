package eif.viko.lt.appsas.paskaitutvarkarastis.glance

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.content.Intent.FLAG_ACTIVITY_NEW_TASK
import android.net.ParseException
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.unit.DpSize
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.glance.ColorFilter
import androidx.glance.GlanceTheme
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.state.getAppWidgetState
import androidx.glance.color.ColorProviders
import androidx.glance.layout.Box
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.capitalize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.glance.Button
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.action.ActionParameters
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.itemsIndexed
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.background

import androidx.glance.Image
import androidx.glance.appwidget.ImageProvider

import androidx.glance.currentState
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.ContentScale
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxHeight
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.layout.wrapContentWidth
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextDecoration
import androidx.glance.text.TextDefaults
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.google.gson.Gson
import eif.viko.lt.appsas.paskaitutvarkarastis.MainActivity
import eif.viko.lt.appsas.paskaitutvarkarastis.MainDataStorage
import eif.viko.lt.appsas.paskaitutvarkarastis.R
import java.text.SimpleDateFormat
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.util.Locale

object TimetableWidget : GlanceAppWidget() {

    // Glance's equivalent of RemoteViews(Map<SizeF, RemoteViews>): one composition per
    // bucket, chosen by the host. Content reads LocalSize to decide how many days fit.
    override val sizeMode = SizeMode.Responsive(
        setOf(
            DpSize(180.dp, 110.dp),
            DpSize(250.dp, 200.dp),
            DpSize(300.dp, 340.dp)
        )
    )

    val countKey = stringPreferencesKey("count")
    val additionalDataKey = stringPreferencesKey("additionalData")
    val changesKey = stringPreferencesKey("changes")
    val teacherNameKey = stringPreferencesKey("teacherName")

    // Which entity this particular widget shows. Absent on widgets placed before per-widget
    // selection existed; those fall back to TEACHER plus MainDataStorage's TEACHER_ID, so no
    // migration step is needed.
    val entityTypeKey = stringPreferencesKey("entityType")
    val entityIdKey = stringPreferencesKey("entityId")

    /** Blank shows every subgroup. Only consulted for GROUP widgets. */
    val subgroupKey = stringPreferencesKey("subgroup")


    /** Leading separator the caption strings carry; see [SyncCaption]. */
    private const val CAPTION_SEPARATOR = " · "

    /** 0 = the current week, 1 = next, and so on. Clamped to [0, MAX_WEEK_OFFSET]. */
    val weekOffsetKey = intPreferencesKey("weekOffset")

    /** How far ahead the arrows may step. The backend has data well beyond this. */
    const val MAX_WEEK_OFFSET = 3

    /**
     * The stored offset is absolute: 0 is always the calendar week containing today. At the
     * weekend that week has nothing left to show, so every offset rolls forward by one and
     * the widget's "this week" becomes the one starting Monday. Applied identically by the
     * sync and the renderer, so they never disagree about which week is meant.
     */
    fun effectiveWeekOffset(stored: Int): Int =
        (stored + WeekRange.defaultOffset()).coerceIn(0, MAX_WEEK_OFFSET)

    /**
     * Epoch millis of the last successful sync. The caption derives "HH:mm" or, once it is
     * more than 12h old, "MM-dd" from this - a stale timestamp should look stale.
     */
    val lastSyncedAtKey = androidx.datastore.preferences.core.longPreferencesKey("lastSyncedAt")

    /** Swaps the caption to "atnaujinama..." while a refresh is in flight. */
    val refreshingKey = booleanPreferencesKey("refreshing")

    /**
     * Set when the last fetch for this widget threw, cleared when one succeeds. The cached
     * week stays on screen either way; this only drives the caption, so "fetch failed" and
     * "fetched fine, week is genuinely empty" can never look the same.
     */
    val lastSyncFailedKey = booleanPreferencesKey("lastSyncFailed")

    /** Bumped on a locale change so the recomposition is not skipped. Value is irrelevant. */
    val localeNonceKey = androidx.datastore.preferences.core.longPreferencesKey("localeNonce")

    /**
     * Placeholder rows: the feed emits one per day with subject "N/A", no group, no room and
     * 00:00-24:00. Filtering on the subject alone rather than uniperiod == "ad" is deliberate:
     * a genuine all-day entry would carry a subject, and should show up as an odd-looking row
     * rather than silently vanish.
     */
    /**
     * A context whose resources resolve in the locale that should actually be used. On
     * Android 13+ the per-app language from LocaleManager takes precedence over the system
     * locale; if none is set, the platform configuration is left alone. Below 13 there is no
     * per-app language and the context is returned unchanged.
     */
    private fun Context.withEffectiveLocale(): Context {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return this
        val perApp = runCatching {
            getSystemService(android.app.LocaleManager::class.java)?.applicationLocales
        }.getOrNull() ?: return this
        if (perApp.isEmpty) return this
        val config = android.content.res.Configuration(resources.configuration).apply {
            setLocales(perApp)
        }
        return createConfigurationContext(config)
    }

    /** The locale of the given context - after [withEffectiveLocale], the right one. */
    private fun Context.appLocale(): Locale =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            resources.configuration.locales[0]
        } else {
            @Suppress("DEPRECATION")
            resources.configuration.locale
        }

    private fun LecturesDto.isPlaceholder(): Boolean =
        subjectid.isBlank() || subjectid.equals("N/A", ignoreCase = true)

    @RequiresApi(Build.VERSION_CODES.O)
    @Composable
    fun Content(
        rawContext: Context,
        storedTeacherName: String = "",
        appWidgetId: Int = AppWidgetManager.INVALID_APPWIDGET_ID
    ) {
        // Read on every composition. A locale-change render is only a recomposition of a
        // live session, so anything resolved once in provideGlance would be stale here.
        // The nonce below is written by LocaleChangedReceiver purely so that a locale change
        // counts as a state change and the composition is not skipped as unchanged.
        currentState(key = localeNonceKey)
        val context = rawContext.withEffectiveLocale()

        // Glance cannot measure text, so this is the only lever for "will it fit": above the
        // threshold the two densest single lines may wrap instead of ellipsize.
        val largeText = FontScale.isLargeText(context.resources.configuration.fontScale)

        val count = currentState(key = countKey) ?: "nera paskaitų"
        val additionalDataJson = currentState(key = additionalDataKey) ?: ""
        val changesJson = currentState(key = changesKey) ?: ""

        val entityLabel = currentState(key = teacherNameKey)?.takeIf { it.isNotBlank() }
            ?: storedTeacherName.takeIf { it.isNotBlank() }
            ?: context.getString(R.string.widget_choose_teacher)

        val refreshing = currentState(key = refreshingKey) ?: false
        // The staleness rule changes the FORM of the timestamp; the failure flag APPENDS a
        // fragment. They never merge into a sentence: "updated 09-01 · failed" is two facts,
        // last good sync and latest attempt, and reads as such.
        val lastSynced = currentState(key = lastSyncedAtKey)
            ?.let { SyncAge.label(it, System.currentTimeMillis()) }
            .orEmpty()
        val lastSyncFailed = currentState(key = lastSyncFailedKey) ?: false

        // No countKey at all means no sync has ever completed for this widget. That is a
        // different situation from a stored, genuinely empty week, and must not look like one.
        val neverSynced = currentState(key = countKey) == null
        val hasEntity = !currentState(key = entityIdKey).isNullOrBlank() ||
            storedTeacherName.isNotBlank()

        val gson = Gson()

        val safeData = if (additionalDataJson.isNotBlank()) {
            gson.fromJson(additionalDataJson, Int::class.java) ?: 0
        } else {
            0
        }

        val storedOffset = currentState(key = weekOffsetKey) ?: 0
        val weekOffset = effectiveWeekOffset(storedOffset)
        val monday = WeekRange.mondayOf(weekOffset)
        val sunday = WeekRange.sundayOf(weekOffset)

        // Only the calendar week containing today is trimmed to "today forward". A week
        // rolled to at the weekend has no today in it, and future weeks show in full.
        val today = LocalDate.now()
        val todayIso = today.toString()
        val isCurrentWeek = storedOffset == 0 && WeekRange.defaultOffset() == 0

        // Zero-padded, so it compares against the feed's "HH:mm" as plain strings.
        val now = LocalTime.now()
        val nowHHmm = String.format(Locale.ROOT, "%02d:%02d", now.hour, now.minute)

        // Parity shows on every week, not only future ones: a label that appears and
        // disappears would reflow the header on every arrow press.
        val weekLabel = if ((((safeData + weekOffset) % 2) + 2) % 2 == 0) "I" else "II"

        val entityType = PickerMode.fromNameOrTeacher(currentState(key = entityTypeKey))
        val highlightChanges = entityType != PickerMode.CLASSROOM
        val showTeacher = entityType != PickerMode.TEACHER

        val subgroup = if (entityType == PickerMode.GROUP) {
            currentState(key = subgroupKey).orEmpty()
        } else {
            ""
        }

        val changes: List<ChangeDto> = if (changesJson.isNotBlank()) {
            runCatching {
                gson.fromJson(changesJson, Array<ChangeDto>::class.java)?.toList().orEmpty()
            }.getOrDefault(emptyList())
        } else {
            emptyList()
        }

        // Everything under here reads LocalContext for strings and the day-name locale, so
        // it must be the localized context from provideGlance, not Glance's own.
        CompositionLocalProvider(LocalContext provides context) {
        GlanceTheme(colors = widgetColors(context)) {
            Column(
                modifier = GlanceModifier
                    .fillMaxSize()
                    .background(GlanceTheme.colors.background)
                    .appWidgetBackgroundRadius()
                    .padding(horizontal = 8.dp, vertical = 6.dp)
            ) {
                Header(
                    context = context,
                    appWidgetId = appWidgetId,
                    // Parity is unconditional everywhere except here: before the first sync
                    // there is no currentweek to derive it from, and showing a guess that is
                    // wrong half the time - with no way for the user to tell - is worse than
                    // an honest gap. The range alone is still correct, so it stays.
                    weekLabel = if (neverSynced) null else weekLabel,
                    monday = monday,
                    sunday = sunday,
                    entityLabel = entityLabel,
                    lastSynced = lastSynced,
                    refreshing = refreshing,
                    lastSyncFailed = lastSyncFailed,
                    largeText = largeText
                )

                run {
                    val weekLectures = if (neverSynced) {
                        emptyList()
                    } else {
                        runCatching {
                            gson.fromJson(count, Array<LecturesDto>::class.java).orEmpty().toList()
                        }.getOrDefault(emptyList())
                            .filterNot { it.isPlaceholder() }
                            .filter { SubgroupFilter.matches(it, subgroup) }
                            .filter { it.date in monday..sunday }
                    }

                    // Date-based, so today's lectures stay visible all day; greying out the
                    // ones already over is a separate concern.
                    val lectures = if (isCurrentWeek) {
                        weekLectures.filter { it.date >= todayIso }
                    } else {
                        weekLectures
                    }

                    // The week had lectures, but none are left ahead of today - a Friday
                    // evening, or a teacher whose week ends on Thursday. Distinct from a week
                    // that never had any, which is its own state.
                    val exhausted = isCurrentWeek && weekLectures.isNotEmpty() && lectures.isEmpty()

                    // Short widgets show fewer days rather than clipping mid-row.
                    val height = LocalSize.current.height
                    val maxDays = when {
                        height < 160.dp -> 2
                        height < 260.dp -> 4
                        else -> 7
                    }

                    val days = DayOfWeek.values()
                        .map { day ->
                            day to lectures.filter {
                                getDayOfWeekFromString(it.date, "yyyy-MM-dd") == day
                            }
                        }
                        .filter { (_, forDay) -> forDay.isNotEmpty() }
                        .take(maxDays)

                    // Exactly one of these can be true: neverSynced short-circuits the list
                    // to empty, "empty week" is weekLectures.isEmpty(), and "exhausted" needs
                    // weekLectures.isNotEmpty(). Listed in the order they win.
                    // "Never synced" splits in two. With no entity bound, pressing refresh
                    // does nothing - the caption link is the way forward, so the body must
                    // not contradict it by pointing at the refresh button.
                    val stateLine = when {
                        neverSynced && !hasEntity -> context.getString(R.string.widget_no_timetable_selected)
                        neverSynced && refreshing -> context.getString(R.string.widget_loading)
                        neverSynced -> context.getString(R.string.widget_not_fetched)
                        weekLectures.isEmpty() -> context.getString(R.string.widget_empty_week)
                        exhausted -> context.getString(R.string.widget_exhausted_week)
                        else -> null
                    }

                    LazyColumn {
                        if (stateLine != null) {
                            item {
                                Text(
                                    text = stateLine,
                                    modifier = GlanceModifier.padding(top = 12.dp),
                                    style = TextStyle(
                                        color = GlanceTheme.colors.onSurfaceVariant,
                                        fontSize = 12.sp
                                    ),
                                    maxLines = 1
                                )
                            }
                        }

                        days.forEachIndexed { index, (day, lecturesForDay) ->
                            item {
                                DayHeader(
                                    day = day,
                                    date = lecturesForDay.first().date,
                                    isToday = weekOffset == 0 && today.dayOfWeek == day,
                                    first = index == 0
                                )
                            }
                            itemsIndexed(lecturesForDay) { _, lecture ->
                                LessonRow(
                                    lecture = lecture,
                                    change = if (highlightChanges) {
                                        ChangeMatcher.findFor(lecture, changes)
                                    } else {
                                        null
                                    },
                                    showTeacher = showTeacher,
                                    binding = entityType,
                                    // Today only, and against the END time: a lecture that
                                    // is in progress is not finished. Other days keep full
                                    // contrast whether past or future.
                                    largeText = largeText,
                                    finished = lecture.date == todayIso &&
                                        LessonClock.hasEnded(lecture.endtime, nowHHmm),
                                    inProgress = lecture.date == todayIso &&
                                        LessonClock.isInProgress(
                                            lecture.starttime, lecture.endtime, nowHHmm
                                        )
                                )
                            }
                        }
                        // The clip at the bottom of a scroll lands in whitespace rather than
                        // slicing through a title's baseline.
                        item { Spacer(modifier = GlanceModifier.height(12.dp)) }
                    }
                }
            }
        }
        }
    }

    /**
     * Material You where the platform has it. Below API 31 there is no dynamic palette, so a
     * static scheme keeps the widget legible instead of leaving colours undefined.
     */
    @Composable
    private fun widgetColors(context: Context): ColorProviders =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            androidx.glance.material3.ColorProviders(
                light = dynamicLightColorScheme(context),
                dark = dynamicDarkColorScheme(context)
            )
        } else {
            androidx.glance.material3.ColorProviders(
                light = lightColorScheme(),
                dark = darkColorScheme()
            )
        }

    /** The platform widget radius on API 31+, and a sane constant below it. */
    private fun GlanceModifier.appWidgetBackgroundRadius(): GlanceModifier =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            cornerRadius(android.R.dimen.system_app_widget_background_radius)
        } else {
            cornerRadius(16.dp)
        }

    private fun GlanceModifier.innerRadius(): GlanceModifier =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            cornerRadius(android.R.dimen.system_app_widget_inner_radius)
        } else {
            cornerRadius(8.dp)
        }

    @Composable
    private fun Header(
        context: Context,
        appWidgetId: Int,
        weekLabel: String?,
        monday: String,
        sunday: String,
        entityLabel: String,
        lastSynced: String,
        refreshing: Boolean,
        lastSyncFailed: Boolean,
        largeText: Boolean
    ) {
        // No fixed height. The 48dp icon buttons set the minimum; the text column may grow
        // past it at large font scales. A fixed 48dp clipped the caption - and with it the
        // entity name, the only way to open the picker - at 2x.
        Row(
            modifier = GlanceModifier.fillMaxWidth(),
            verticalAlignment = Alignment.Vertical.CenterVertically
        ) {
            Column(modifier = GlanceModifier.defaultWeight()) {
                Text(
                    text = listOfNotNull(
                        weekLabel?.let { context.getString(R.string.widget_week_parity, it) },
                        monday.takeLast(5) + " – " + sunday.takeLast(5)
                    ).joinToString(" · "),
                    style = TextStyle(
                        color = GlanceTheme.colors.onBackground,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium
                    ),
                    maxLines = if (largeText) 2 else 1
                )
                // Split deliberately: only the entity name opens the picker. Tapping a
                // timestamp and getting a group chooser would be the wrong affordance.
                //
                // Side by side at large font scale, the content-sized timestamp took the
                // width first and left the name - the only route to the picker - as a
                // three-dot target. So above the FontScale threshold the two stack.
                if (largeText) {
                    Column {
                        EntityLink(
                            context = context,
                            entityLabel = entityLabel,
                            appWidgetId = appWidgetId,
                            modifier = GlanceModifier.fillMaxWidth(),
                            maxLines = 2
                        )
                        Row(verticalAlignment = Alignment.Vertical.CenterVertically) {
                            SyncCaption(context, refreshing, lastSynced, lastSyncFailed, ownLine = true)
                        }
                    }
                } else {
                    Row(verticalAlignment = Alignment.Vertical.CenterVertically) {
                        EntityLink(
                            context = context,
                            entityLabel = entityLabel,
                            appWidgetId = appWidgetId,
                            modifier = GlanceModifier.defaultWeight(),
                            maxLines = 1
                        )
                        SyncCaption(context, refreshing, lastSynced, lastSyncFailed, ownLine = false)
                    }
                }
            }

            IconButton(
                resId = R.drawable.ic_chevron_left_24,
                description = context.getString(R.string.cd_previous_week),
                callback = SelectPreviousDateActionCallback::class.java
            )
            IconButton(
                resId = R.drawable.ic_chevron_right_24,
                description = context.getString(R.string.cd_next_week),
                callback = SelectNextDateActionCallback::class.java
            )
            // Refresh is a different kind of action from week navigation, so it is set apart
            // rather than sitting inside the same cluster.
            Spacer(modifier = GlanceModifier.width(12.dp))
            IconButton(
                resId = R.drawable.baseline_refresh_24,
                description = context.getString(R.string.cd_refresh),
                callback = IncrementActionCallback::class.java
            )
        }
    }

    /** A 48dp touch target regardless of the 20dp glyph inside it. */
    /** The underlined entity name; the only tap target that opens the picker. */
    @Composable
    private fun EntityLink(
        context: Context,
        entityLabel: String,
        appWidgetId: Int,
        modifier: GlanceModifier,
        maxLines: Int
    ) {
        Text(
            text = entityLabel.withoutAcademicTitle(),
            modifier = modifier.clickable {
                openMainActivity(context = context, appWidgetId = appWidgetId)
            },
            style = TextStyle(
                color = GlanceTheme.colors.primary,
                fontSize = 12.sp,
                textDecoration = TextDecoration.Underline
            ),
            maxLines = maxLines
        )
    }

    /**
     * The sync timestamp and, when the last sync failed, an error-coloured fragment after
     * it. The strings carry their own " · " so they read as a continuation of the entity
     * name; when the caption is on its own line ([ownLine]) there is nothing to continue
     * from and the first fragment drops it.
     */
    @Composable
    private fun SyncCaption(
        context: Context,
        refreshing: Boolean,
        lastSynced: String,
        lastSyncFailed: Boolean,
        ownLine: Boolean
    ) {
        val timestamp = when {
            refreshing -> context.getString(R.string.widget_updating)
            lastSynced.isNotBlank() -> context.getString(R.string.widget_updated, lastSynced)
            else -> ""
        }
        Text(
            modifier = GlanceModifier.wrapContentWidth(),
            text = if (ownLine) timestamp.removePrefix(CAPTION_SEPARATOR) else timestamp,
            style = TextStyle(
                color = GlanceTheme.colors.onSurfaceVariant,
                fontSize = 12.sp
            ),
            maxLines = 1
        )
        // Failure is a second, error-coloured fragment rather than a rewrite of
        // the timestamp: the last good time stays readable next to it.
        if (lastSyncFailed && !refreshing) {
            val failure = context.getString(
                if (lastSynced.isBlank()) R.string.widget_update_failed
                else R.string.widget_update_failed_short
            )
            Text(
                text = if (ownLine && timestamp.isBlank()) failure.removePrefix(CAPTION_SEPARATOR)
                       else failure,
                modifier = GlanceModifier.wrapContentWidth(),
                style = TextStyle(
                    color = GlanceTheme.colors.error,
                    fontSize = 12.sp
                ),
                maxLines = 1
            )
        }
    }

    @Composable
    private fun IconButton(
        resId: Int,
        description: String,
        callback: Class<out ActionCallback>
    ) {
        Box(
            modifier = GlanceModifier
                .size(48.dp)
                .clickable(actionRunCallback(callback)),
            contentAlignment = Alignment.Center
        ) {
            Image(
                provider = androidx.glance.ImageProvider(resId),
                contentDescription = description,
                modifier = GlanceModifier.size(20.dp),
                colorFilter = ColorFilter.tint(GlanceTheme.colors.onBackground)
            )
        }
    }

    @Composable
    private fun DayHeader(day: DayOfWeek, date: String, isToday: Boolean, first: Boolean) {
        // Day names are generated from the date, not taken from the feed, so they follow the
        // app locale like everything else.
        val locale = LocalContext.current.appLocale()
        val name = day.getDisplayName(java.time.format.TextStyle.FULL, locale)
            .replaceFirstChar { if (it.isLowerCase()) it.titlecase(locale) else it.toString() }

        // Days are separated by spacing alone, not a rule. The header is already the only
        // left-flush text in the list - everything else sits past the time column - so that
        // edge does the grouping, and a horizontal line would add a second axis competing
        // with the vertical accent bars. Asymmetric: more above the header than below it, so
        // the header attaches to the day it introduces rather than floating between two.
        Row(
            modifier = GlanceModifier
                .fillMaxWidth()
                .padding(top = if (first) 10.dp else 16.dp, bottom = 4.dp),
            verticalAlignment = Alignment.Vertical.CenterVertically
        ) {
            Text(
                text = name + " " + date.takeLast(5),
                style = TextStyle(
                    color = GlanceTheme.colors.onSurfaceVariant,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                ),
                maxLines = 1
            )
            if (isToday) {
                Spacer(modifier = GlanceModifier.width(6.dp))
                Text(
                    text = LocalContext.current.getString(R.string.widget_today),
                    modifier = GlanceModifier
                        .background(GlanceTheme.colors.primaryContainer)
                        .innerRadius()
                        .padding(horizontal = 6.dp, vertical = 1.dp),
                    style = TextStyle(
                        color = GlanceTheme.colors.onPrimaryContainer,
                        fontSize = 12.sp
                    ),
                    maxLines = 1
                )
            }
        }
    }

    @Composable
    private fun LessonRow(
        lecture: LecturesDto,
        change: ChangeDto?,
        showTeacher: Boolean,
        binding: PickerMode,
        largeText: Boolean,
        finished: Boolean,
        inProgress: Boolean
    ) {
        val status = change?.let { ChangeMatcher.statusOf(it) }
        val cancelled = status == ChangeStatus.CANCELLED

        // Precedence: a cancelled lecture during its own slot is cancelled, not in progress -
        // a tint there would assert something false. Changed + in progress coexist: the bar
        // and the tint are different layers (figure and ground), so neither overrides the
        // other. Finished and in progress are exclusive by the clock and need no rule.
        val tinted = inProgress && !cancelled

        // One muted tone shared by "finished" and "cancelled", never stacked: a cancelled
        // lecture that has also ended stays exactly as legible as a cancelled one that has
        // not. What keeps it reading as cancelled rather than merely faded is the
        // strikethrough and the "Paskaitos nėra" line, which finished rows never get.
        val muted = cancelled || finished

        // The bar encodes ChangeStatus only, so the normal case has to be near-invisible:
        // if every ordinary row already carries a heavy stripe, a differently coloured one
        // reads as a slightly different stripe in a striped list, not as an exception.
        // Cancelled is muted rather than loud: the row is already struck through and greyed.
        val accent = when (status) {
            ChangeStatus.CANCELLED -> GlanceTheme.colors.outline
            ChangeStatus.CHANGED -> GlanceTheme.colors.tertiary
            null -> androidx.glance.color.ColorProvider(
                day = Color(0xFFC9C9C2),
                night = Color(0xFF3A3A37)
            )
        }
        val titleColor = if (muted) {
            GlanceTheme.colors.onSurfaceVariant
        } else {
            GlanceTheme.colors.onBackground
        }

        // Two nested rows so the tint is a bounded object, not a full-bleed band: the outer
        // one keeps a transparent gutter between rows, the inner one carries the wash and the
        // inner corner radius. Both exist on every row, tinted or not, so geometry never
        // shifts when a lecture starts or ends.
        //
        // The wash is surfaceVariant, never a container colour: it must read as "where you
        // are", not as a fourth status competing with the bar. The two values below are
        // chosen per mode rather than one value inverted - a tint that works on a light
        // surface is far too loud on a dark one.
        val tint = androidx.glance.color.ColorProvider(
            day = Color(0xFFE9E6EE),
            night = Color(0xFF2B2A30)
        )

        // Deliberately not clickable: the only way into the app is the entity name in the
        // header. A tap on a lecture used to open the app, which read as an accident.
        Row(
            modifier = GlanceModifier
                .fillMaxWidth()
                .padding(vertical = 3.dp)
        ) {
        Row(
            modifier = if (tinted) {
                GlanceModifier
                    .fillMaxWidth()
                    .background(tint)
                    .innerRadius()
                    .padding(vertical = 2.dp)
            } else {
                GlanceModifier.fillMaxWidth().padding(vertical = 2.dp)
            }
        ) {
            // Right-aligned column. At default scale it is a fixed 44dp: the times line up
            // down one edge and sit well inside the in-progress tint's corner radius. At
            // large font scale that 44dp reduced "10:15" to "1…", so the column is sized by
            // its content instead (the feed zero-pads every time to five characters, so rows
            // still line up) with a 4dp inset to keep the first glyph out of the rounded
            // corner - hugging the edge, the corner ate the leading "2" of "22:26".
            val timeColumn =
                if (largeText) GlanceModifier.wrapContentWidth().padding(start = 4.dp)
                else GlanceModifier.width(44.dp)
            val timeText =
                if (largeText) GlanceModifier.wrapContentWidth() else GlanceModifier.fillMaxWidth()
            Column(modifier = timeColumn) {
                Text(
                    text = lecture.starttime,
                    modifier = timeText,
                    style = TextStyle(
                        color = titleColor,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        textAlign = TextAlign.End
                    ),
                    maxLines = 1
                )
                Text(
                    text = lecture.endtime,
                    modifier = timeText,
                    style = TextStyle(
                        color = GlanceTheme.colors.onSurfaceVariant,
                        fontSize = 12.sp,
                        textAlign = TextAlign.End
                    ),
                    maxLines = 1
                )
            }

            Spacer(modifier = GlanceModifier.width(8.dp))
            // Square corners on purpose. Decorative only, so a fixed height is an acceptable
            // fallback if fillMaxHeight will not stretch inside a lazy item.
            Box(
                modifier = GlanceModifier
                    .width(3.dp)
                    .fillMaxHeight()
                    .background(accent),
                contentAlignment = Alignment.Center
            ) {}
            Spacer(modifier = GlanceModifier.width(8.dp))

            Column(modifier = GlanceModifier.defaultWeight()) {
                Text(
                    text = lecture.subjectid,
                    style = TextStyle(
                        color = titleColor,
                        fontSize = 14.sp,
                        textDecoration = if (cancelled) {
                            TextDecoration.LineThrough
                        } else {
                            TextDecoration.None
                        }
                    ),
                    maxLines = 2
                )
                Row(verticalAlignment = Alignment.Vertical.CenterVertically) {
                    // An icon rather than a colour: if the room data is messy, a missing icon
                    // reads as "not stated", whereas a wrong colour reads as a claim.
                    if (lecture.isOnline()) {
                        Image(
                            provider = androidx.glance.ImageProvider(R.drawable.ic_video_24),
                            contentDescription = LocalContext.current.getString(R.string.cd_online),
                            modifier = GlanceModifier.size(12.dp),
                            colorFilter = ColorFilter.tint(GlanceTheme.colors.onSurfaceVariant)
                        )
                        Spacer(modifier = GlanceModifier.width(4.dp))
                    }
                    Text(
                        text = metaLine(LocalContext.current, lecture, cancelled, binding),
                        style = TextStyle(
                            color = GlanceTheme.colors.onSurfaceVariant,
                            fontSize = 12.sp
                        ),
                        maxLines = if (largeText) 2 else 1
                    )
                }
                if (showTeacher && lecture.teacherids.isNotEmpty()) {
                    // The feed resolves teacherids to display names; the academic title is
                    // dropped the same way the header and notifications drop it.
                    Text(
                        text = lecture.teacherids.joinToString(", ") { it.trim().withoutAcademicTitle() },
                        style = TextStyle(
                            color = GlanceTheme.colors.onSurfaceVariant,
                            fontSize = 12.sp
                        ),
                        maxLines = 1
                    )
                }
                if (change != null) {
                    Text(
                        text = if (cancelled) {
                            // Display only. Matching uses ChangeMatcher.CANCELLED_MARKER, the
                            // department's own Lithuanian marker, and is never translated.
                            LocalContext.current.getString(R.string.lesson_cancelled)
                        } else {
                            LocalContext.current.getString(
                                R.string.widget_moved_to, change.auditorija.trim()
                            )
                        },
                        style = TextStyle(
                            color = accent,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        ),
                        maxLines = 1
                    )
                }
            }
        }
        }
    }

    // Rooms are three-state, not a binary: a lecture can have no room, a physical room, an
    // online room, or both at once ("203" + "MS Teams" is a real combination in the feed).
    private val ONLINE = Regex("teams", RegexOption.IGNORE_CASE)

    // Codes are not always numeric - "104A" is a real room - so the suffix test allows a
    // trailing letter rather than requiring digits alone.
    private val ROOM_CODE = Regex("""\d+[A-Za-z]?""")

    private fun LecturesDto.isOnline(): Boolean =
        classroomids.any { ONLINE.containsMatchIn(it) }

    private fun LecturesDto.physicalRooms(): List<String> =
        classroomids.filter { ROOM_CODE.matches(it.trim()) }

    /**
     * "PI24E · 202 aud. · 2 pask." - the format carries the words, so "paskaita" and "val."
     * are dropped. The first segment depends on what the widget is bound to; see [MetaLead].
     *
     * "aud." is appended only to something that looks like a room code, so an online-only
     * lecture never reads "MS Teams aud."; the video icon carries that instead.
     */
    private fun metaLine(
        context: Context,
        lecture: LecturesDto,
        cancelled: Boolean,
        binding: PickerMode
    ): String =
        listOfNotNull(
            MetaLead.of(lecture, binding),
            // A cancelled lecture has no room to go to.
            lecture.physicalRooms().joinToString(", ")
                .takeIf { it.isNotBlank() && !cancelled }
                ?.let { context.getString(R.string.widget_room, it) },
            lecture.uniperiod.trim().takeIf { it.isNotBlank() }
                ?.let { context.getString(R.string.widget_period, it) }
        ).joinToString(" · ")

    /**
     * Carries the widget id along so the picker can bind the choice to this widget alone
     * rather than to every placed widget.
     */
    fun openMainActivity(
        context: Context,
        appWidgetId: Int = AppWidgetManager.INVALID_APPWIDGET_ID
    ) {
        val intent = Intent(context, MainActivity::class.java)
        intent.flags = FLAG_ACTIVITY_NEW_TASK
        if (appWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
            intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
        }
        context.startActivity(intent)
    }


    // TODO need upgrade version to minimal api 23 instead 26
    @RequiresApi(Build.VERSION_CODES.O)
    fun getDayOfWeekFromString(dateString: String, dateFormat: String): DayOfWeek? {
        try {
            val sdf = SimpleDateFormat(dateFormat, Locale.US)
            val date = sdf.parse(dateString)
            val sdf2 = SimpleDateFormat("EEEE", Locale.US)
            val dayOfWeekString = sdf2.format(date)

            return when (dayOfWeekString) {
                "Monday" -> DayOfWeek.MONDAY
                "Tuesday" -> DayOfWeek.TUESDAY
                "Wednesday" -> DayOfWeek.WEDNESDAY
                "Thursday" -> DayOfWeek.THURSDAY
                "Friday" -> DayOfWeek.FRIDAY
                "Saturday" -> DayOfWeek.SATURDAY
                "Sunday" -> DayOfWeek.SUNDAY
                else -> null // Invalid day of the week string
            }
        } catch (e: ParseException) {
            e.printStackTrace()
            return null // Invalid date string
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        // Read here rather than in Content: MainDataStorage is suspend-only and composition
        // must not block.
        val storedTeacherName = runCatching {
            MainDataStorage.getInstance(context).readString("TEACHER_NAME")
        }.getOrNull().orEmpty()

        val appWidgetId = runCatching {
            GlanceAppWidgetManager(context).getAppWidgetId(id)
        }.getOrDefault(AppWidgetManager.INVALID_APPWIDGET_ID)

        // Resolved explicitly rather than trusting the process configuration. The locale
        // broadcast arrives before the app's resources reflect a per-app language change, so
        // a render triggered by it would otherwise come out in the OLD language and stick.
        provideContent {
            Content(context, storedTeacherName, appWidgetId)
        }
    }


}



class TimetableMyReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget
        get() = TimetableWidget


}

/**
 * Steps the widget back or forward a week. The stored lectures are for the old week, so the
 * offset change has to be followed by a refetch, not just a redraw.
 */
private suspend fun stepWeek(context: Context, glanceId: GlanceId, delta: Int) {
    updateAppWidgetState(context, glanceId) { prefs ->
        val current = prefs[TimetableWidget.weekOffsetKey] ?: 0
        prefs[TimetableWidget.weekOffsetKey] =
            (current + delta).coerceIn(0, TimetableWidget.MAX_WEEK_OFFSET)
    }
    TimetableSync.syncTimetable(context)
    TimetableWidget.update(context, glanceId)
}

class SelectPreviousDateActionCallback : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters
    ) = stepWeek(context, glanceId, -1)
}

class SelectNextDateActionCallback : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters
    ) = stepWeek(context, glanceId, +1)
}


class IncrementActionCallback : ActionCallback {

    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters
    ) {
        // A text swap rather than a spinner: two RemoteViews pushes either way, but a
        // caption that flashes is invisible, whereas a spinner that flashes reads as a bug.
        updateAppWidgetState(context, glanceId) { prefs ->
            prefs[TimetableWidget.refreshingKey] = true
        }
        TimetableWidget.update(context, glanceId)

        // Same fetch-and-store path the periodic worker uses.
        TimetableSync.syncTimetable(context)

        updateAppWidgetState(context, glanceId) { prefs ->
            prefs[TimetableWidget.refreshingKey] = false
        }
        TimetableWidget.update(context, glanceId)
    }
}