package de.dachapp

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import de.dachapp.module.Erinnerungen
import de.dachapp.module.SubApp
import de.dachapp.module.Wochenbeitrag
import de.dachapp.module.Wochenrueckblick
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters

/** Pseudo-ID der Rückblick-Seite in der Übersicht (keine Unter-App heißt so); auch Ziel der Benachrichtigung. */
internal const val WOCHE = "__woche"

/**
 * Sonntags-Benachrichtigung „Dein Wochenrückblick“. Ein Wecker auf den nächsten Sonntag zur gewählten Stunde; dort
 * sammelt sie die Beiträge aller Unter-Apps ([SubApp.woche]) und meldet sich nur, wenn es etwas zu berichten gibt.
 * Antippen öffnet die Rückblick-Seite der Übersicht. Empfänger stehen nur in den Manifesten von „alle“ und „gym“;
 * „schwimmen“ bleibt ohne Berechtigungen und plant nie.
 */
object WochenWecker {
    /** Unter-Apps, die zum Rückblick beitragen; nur mit einer davon gibt es Seite und Wecker. */
    val QUELLEN = setOf("tracker", "krafttraining", "brennholz")

    private const val KANAL = "dach_wochenrueckblick"
    private const val NR = 76_001
    private const val WECKER_NR = 76_020
    const val MELDEN = "de.dachapp.WOCHE_MELDEN"
    private const val PREFS = "dach_wochenrueckblick"
    private const val AN = "an"
    private const val STUNDE = "stunde"

    /** Uhrzeiten zur Auswahl. */
    val STUNDEN = listOf(9, 12, 18, 20)

    fun aktiv(module: List<SubApp>) = module.any { it.id in QUELLEN }

    private fun prefs(c: Context) = c.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    fun an(c: Context): Boolean = prefs(c).getBoolean(AN, true)
    fun stunde(c: Context): Int = prefs(c).getInt(STUNDE, 18)

    fun einstellen(c: Context, an: Boolean, stunde: Int) {
        prefs(c).edit().putBoolean(AN, an).putInt(STUNDE, stunde.coerceIn(0, 23)).apply()
        planen(c)
    }

    private fun wecker(c: Context): PendingIntent =
        PendingIntent.getBroadcast(
            c, WECKER_NR, Intent(c, WochenEmpfaenger::class.java).setAction(MELDEN),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

    /** Nächster Sonntag zur [stunde] nach [jetzt] (heute, wenn Sonntag und noch nicht so spät). */
    fun naechster(jetzt: LocalDateTime, stunde: Int): LocalDateTime {
        val t = jetzt.toLocalDate().with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY)).atTime(stunde.coerceIn(0, 23), 0)
        return if (t.isAfter(jetzt)) t else t.plusWeeks(1)
    }

    /** Wecker stellen oder löschen (beim App-Start, nach Neustart und nach jeder Meldung). */
    fun planen(c: Context) {
        if (!aktiv(ModuleRegistry.all)) return
        val p = wecker(c)
        if (!an(c)) { Erinnerungen.weckerWeg(c, p); return }
        val t = naechster(LocalDateTime.now(), stunde(c))
        // Ungefähr reicht: Android darf ein paar Minuten schieben, dafür braucht es keine Sonderberechtigung.
        Erinnerungen.wecker(c, t.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli(), p, genau = false)
    }

    /** Beiträge aller Unter-Apps für die Woche ab [montag]; fehlerhafte Beiträge fallen still weg. */
    fun beitraege(c: Context, module: List<SubApp>, montag: LocalDate, jetzt: Long = System.currentTimeMillis()): List<Wochenbeitrag> =
        module.mapNotNull { m -> runCatching { m.woche(c.applicationContext, montag, jetzt) }.getOrNull() }

    fun melden(c: Context) {
        val montag = Wochenrueckblick.montag(LocalDate.now())
        val b = beitraege(c, ModuleRegistry.all, montag)
        if (b.isEmpty()) return
        Erinnerungen.kanal(c, KANAL, "Wochenrückblick", "Sonntags deine Woche: Training, Ernährung, Koffein, Alkohol und Supplements")
        Erinnerungen.melden(
            c, KANAL, NR, "Dein Wochenrückblick: ${Wochenrueckblick.ueberschrift(b)}", Wochenrueckblick.meldungText(b),
            unterApp = WOCHE, langtext = Wochenrueckblick.meldungLangtext(b, montag),
        )
    }

    internal fun empfangen(c: Context, i: Intent) {
        if (i.action == MELDEN) runCatching { melden(c) }
        planen(c)
    }
}

/** Wecker des Wochenrückblicks. */
class WochenEmpfaenger : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val p = goAsync()
        Thread {
            try { WochenWecker.empfangen(context, intent) } finally { p.finish() }
        }.start()
    }
}

/** Nach einem Neustart oder Update den Wecker wieder stellen. */
class WochenNeustart : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED && intent.action != Intent.ACTION_MY_PACKAGE_REPLACED) return
        WochenWecker.planen(context)
    }
}
