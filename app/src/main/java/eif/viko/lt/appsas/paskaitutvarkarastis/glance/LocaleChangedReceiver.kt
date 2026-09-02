package eif.viko.lt.appsas.paskaitutvarkarastis.glance

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.state.updateAppWidgetState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Glance widgets do not re-render on a locale change on their own: the last RemoteViews
 * stays on screen in the old language until something else triggers an update. This listens
 * for both the system locale and the Android 13+ per-app locale changing and re-renders.
 */
class LocaleChangedReceiver : BroadcastReceiver() {

    private companion object {
        const val TAG = "LocaleChangedReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        Log.i(TAG, "Locale change received: ${intent.action}, re-rendering widgets")
        // Re-rendering is suspend work; keep the receiver alive until it is done.
        val pending = goAsync()
        CoroutineScope(Dispatchers.Default).launch {
            runCatching {
                val app = context.applicationContext
                // A plain update of a live session recomposes, and Compose skips a body whose
                // inputs did not change. Writing a nonce makes the change observable.
                GlanceAppWidgetManager(app).getGlanceIds(TimetableWidget::class.java).forEach { id ->
                    updateAppWidgetState(app, id) { prefs ->
                        prefs[TimetableWidget.localeNonceKey] = System.currentTimeMillis()
                    }
                    TimetableWidget.update(app, id)
                }
            }
            pending.finish()
        }
    }
}
