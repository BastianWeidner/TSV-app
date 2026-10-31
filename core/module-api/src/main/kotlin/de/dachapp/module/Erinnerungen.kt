package de.dachapp.module

import android.Manifest
import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * Gemeinsamer Baustein für Erinnerungen: Benachrichtigung mit Knöpfen, die die richtige Unter-App öffnet, und Wecker
 * (genau, wenn erlaubt, sonst ungefähr; oder täglich). Unter-Apps brauchen dafür im eigenen Manifest
 * POST_NOTIFICATIONS und für genaue Wecker USE_EXACT_ALARM bzw. SCHEDULE_EXACT_ALARM.
 */
object Erinnerungen {
    data class Knopf(val text: String, val aktion: PendingIntent)

    /** Dürfen Benachrichtigungen gezeigt werden (Android 13+: Berechtigung, sonst Einstellung der App)? */
    fun erlaubt(c: Context): Boolean =
        (Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(c, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) &&
            NotificationManagerCompat.from(c).areNotificationsEnabled()

    /** Kanal anlegen, falls es ihn noch nicht gibt. */
    fun kanal(c: Context, id: String, name: String, beschreibung: String, wichtigkeit: Int = NotificationManager.IMPORTANCE_DEFAULT) {
        val nm = c.getSystemService(NotificationManager::class.java)
        if (nm.getNotificationChannel(id) == null) nm.createNotificationChannel(NotificationChannel(id, name, wichtigkeit).apply { description = beschreibung })
    }

    /** Öffnet die Dach-App direkt in der Unter-App [unterApp]. */
    fun oeffnen(c: Context, unterApp: String, nr: Int): PendingIntent? {
        val i = c.packageManager.getLaunchIntentForPackage(c.packageName)?.apply {
            putExtra("unterApp", unterApp)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
        } ?: return null
        return PendingIntent.getActivity(c, nr, i, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }

    /** PendingIntent an einen eigenen BroadcastReceiver, z. B. für Knöpfe oder Wecker. */
    fun anEmpfaenger(c: Context, empfaenger: Class<*>, aktion: String, nr: Int): PendingIntent =
        PendingIntent.getBroadcast(c, nr, Intent(c, empfaenger).setAction(aktion), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)

    /** Benachrichtigung zeigen (nichts, wenn nicht erlaubt). [langtext] erscheint aufgeklappt. */
    @SuppressLint("MissingPermission")
    fun melden(
        c: Context, kanal: String, nr: Int, titel: String, text: String, unterApp: String,
        langtext: String? = null, knoepfe: List<Knopf> = emptyList(), alarm: Boolean = false,
    ) {
        if (!erlaubt(c)) return
        val b = NotificationCompat.Builder(c, kanal)
            .setSmallIcon(android.R.drawable.ic_popup_reminder)
            .setContentTitle(titel)
            .setContentText(text)
            .setAutoCancel(true)
            .setPriority(if (alarm) NotificationCompat.PRIORITY_HIGH else NotificationCompat.PRIORITY_DEFAULT)
        if (alarm) b.setCategory(NotificationCompat.CATEGORY_ALARM)
        langtext?.let { b.setStyle(NotificationCompat.BigTextStyle().bigText(it)) }
        knoepfe.forEach { b.addAction(0, it.text, it.aktion) }
        oeffnen(c, unterApp, nr + 1)?.let { b.setContentIntent(it) }
        NotificationManagerCompat.from(c).notify(nr, b.build())
    }

    fun weg(c: Context, nr: Int) = NotificationManagerCompat.from(c).cancel(nr)

    /** Darf die App auf die Minute genau wecken? */
    fun kannGenau(c: Context): Boolean =
        Build.VERSION.SDK_INT < 31 || c.getSystemService(AlarmManager::class.java).canScheduleExactAlarms()

    /** Wecker auf [wann] (ms), genau wenn möglich, sonst so genau wie Android es zulässt. */
    fun wecker(c: Context, wann: Long, p: PendingIntent, genau: Boolean = true) {
        val am = c.getSystemService(AlarmManager::class.java)
        try {
            if (genau && kannGenau(c)) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, wann, p)
            else am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, wann, p)
        } catch (e: SecurityException) {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, wann, p)
        }
    }

    /** Ungefähr täglich zur [stunde], ohne Sonderberechtigung. */
    fun taeglich(c: Context, stunde: Int, p: PendingIntent, minute: Int = 0) {
        val jetzt = LocalDateTime.now()
        var t = jetzt.toLocalDate().atTime(stunde.coerceIn(0, 23), minute.coerceIn(0, 59))
        if (!t.isAfter(jetzt)) t = t.plusDays(1)
        c.getSystemService(AlarmManager::class.java)
            .setInexactRepeating(AlarmManager.RTC_WAKEUP, t.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli(), AlarmManager.INTERVAL_DAY, p)
    }

    fun weckerWeg(c: Context, p: PendingIntent) = c.getSystemService(AlarmManager::class.java).cancel(p)
}
