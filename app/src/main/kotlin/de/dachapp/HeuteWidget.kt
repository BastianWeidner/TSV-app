package de.dachapp

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews

/** Widget „Heute“: die Hinweise aller Unter-Apps (Training, Feuer, Einkauf, Fristen, Koffein …), wie die Heute-Zeile. */
class HeuteWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        val p = goAsync()
        Thread { try { aktualisieren(context) } finally { p.finish() } }.start()
    }

    companion object {
        /** Nicht im Hauptthread aufrufen. */
        fun aktualisieren(context: Context) {
            val app = context.applicationContext
            val manager = AppWidgetManager.getInstance(app) ?: return
            val ids = manager.getAppWidgetIds(ComponentName(app, HeuteWidget::class.java))
            if (ids.isEmpty()) return
            val zeilen = ModuleRegistry.all.mapNotNull { m -> runCatching { m.heute(app) }.getOrNull() }
            val v = RemoteViews(app.packageName, R.layout.dach_widget)
            v.setTextViewText(R.id.dach_widget_text, if (zeilen.isEmpty()) "Heute steht nichts an." else zeilen.joinToString("\n"))
            app.packageManager.getLaunchIntentForPackage(app.packageName)?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)?.let {
                v.setOnClickPendingIntent(R.id.dach_widget_flaeche, PendingIntent.getActivity(app, 70_001, it, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT))
            }
            manager.updateAppWidget(ids, v)
        }
    }
}
