package de.dachapp

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import de.dachapp.module.Erinnerungen
import de.dachapp.module.Rueckblickart
import de.dachapp.module.SubApp
import de.dachapp.module.Wochenbeitrag
import de.dachapp.module.Wochenrueckblick
import de.dachapp.module.Zeitraum
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

/** Pseudo-ID der Rückblick-Seite in der Übersicht (keine Unter-App heißt so); auch Ziel der Benachrichtigung. */
internal const val WOCHE = "__woche"

/** Ziel der Benachrichtigung für eine bestimmte Art, z. B. „__woche/MONAT“. */
internal fun rueckblickZiel(art: Rueckblickart) = "$WOCHE/${art.name}"

/** Art aus der Pseudo-ID (ohne Zusatz: null). */
internal fun rueckblickArt(id: String?): Rueckblickart? =
    id?.substringAfter("$WOCHE/", "")?.let { n -> Rueckblickart.entries.firstOrNull { it.name == n } }

internal fun istRueckblick(id: String?) = id == WOCHE || id?.startsWith("$WOCHE/") == true

/**
 * Benachrichtigung „Dein Wochen-, Monats-, Quartals-, Halbjahres- oder Jahresrückblick“ am letzten Tag des
 * Zeitraums zur gewählten Stunde (Woche: Sonntag). Fallen mehrere auf einen Tag (31. Dezember), meldet sich nur der
 * größte. Ein Wecker auf den nächsten solchen Tag; dort sammelt sie die Beiträge aller Unter-Apps
 * ([SubApp.rueckblick]) und meldet sich nur, wenn es etwas zu berichten gibt. Antippen öffnet die Rückblick-Seite
 * in dieser Art. Empfänger stehen nur in den Manifesten von „alle“ und „gym“; „schwimmen“ hat keine Quelle und plant nie.
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

    /** Schlüssel je Art; die Woche behält den alten Schlüssel „an“. */
    private fun schluessel(a: Rueckblickart) = if (a == Rueckblickart.WOCHE) AN else "${AN}_${a.name}"

    fun an(c: Context, a: Rueckblickart = Rueckblickart.WOCHE): Boolean = prefs(c).getBoolean(schluessel(a), true)
    fun arten(c: Context): Set<Rueckblickart> = Rueckblickart.entries.filter { an(c, it) }.toSet()
    fun stunde(c: Context): Int = prefs(c).getInt(STUNDE, 18)

    fun einstellen(c: Context, a: Rueckblickart, an: Boolean) {
        prefs(c).edit().putBoolean(schluessel(a), an).apply()
        planen(c)
    }

    fun stundeSetzen(c: Context, stunde: Int) {
        prefs(c).edit().putInt(STUNDE, stunde.coerceIn(0, 23)).apply()
        planen(c)
    }

    private fun wecker(c: Context): PendingIntent =
        PendingIntent.getBroadcast(
            c, WECKER_NR, Intent(c, WochenEmpfaenger::class.java).setAction(MELDEN),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

    /** Größte eingeschaltete Art, die an [tag] endet, oder null. */
    fun faellig(tag: LocalDate, arten: Set<Rueckblickart>): Rueckblickart? = Zeitraum.endenAm(tag).firstOrNull { it in arten }

    /** Nächster Meldezeitpunkt nach [jetzt]: letzter Tag eines eingeschalteten Zeitraums zur [stunde]; null = alles aus. */
    fun naechster(jetzt: LocalDateTime, stunde: Int, arten: Set<Rueckblickart> = setOf(Rueckblickart.WOCHE)): LocalDateTime? {
        if (arten.isEmpty()) return null
        for (i in 0..370) {
            val d = jetzt.toLocalDate().plusDays(i.toLong())
            val t = d.atTime(stunde.coerceIn(0, 23), 0)
            if (t.isAfter(jetzt) && faellig(d, arten) != null) return t
        }
        return null
    }

    /** Wecker stellen oder löschen (beim App-Start, nach Neustart und nach jeder Meldung). */
    fun planen(c: Context) {
        if (!aktiv(ModuleRegistry.all)) return
        val p = wecker(c)
        val t = naechster(LocalDateTime.now(), stunde(c), arten(c))
        if (t == null) { Erinnerungen.weckerWeg(c, p); return }
        // Ungefähr reicht: Android darf ein paar Minuten schieben, dafür braucht es keine Sonderberechtigung.
        Erinnerungen.wecker(c, t.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli(), p, genau = false)
    }

    /** Beiträge aller Unter-Apps für [z]; fehlerhafte Beiträge fallen still weg. */
    fun beitraege(c: Context, module: List<SubApp>, z: Zeitraum, jetzt: Long = System.currentTimeMillis()): List<Wochenbeitrag> =
        module.mapNotNull { m -> runCatching { m.rueckblick(c.applicationContext, z, jetzt) }.getOrNull() }

    fun melden(c: Context) {
        val heute = LocalDate.now()
        val art = faellig(heute, arten(c)) ?: return
        val z = Zeitraum.um(art, heute)
        val b = beitraege(c, ModuleRegistry.all, z)
        if (b.isEmpty()) return
        Erinnerungen.kanal(c, KANAL, "Rückblick", "Deine Woche, dein Monat, Quartal, Halbjahr und Jahr: Training, Ernährung, Koffein, Alkohol und Supplements")
        Erinnerungen.melden(
            c, KANAL, NR, "Dein ${art.rueckblick}: ${Wochenrueckblick.ueberschrift(b, art)}", Wochenrueckblick.meldungText(b),
            unterApp = rueckblickZiel(art), langtext = Wochenrueckblick.meldungLangtext(b, z),
        )
    }

    internal fun empfangen(c: Context, i: Intent) {
        if (i.action == MELDEN) runCatching { melden(c) }
        planen(c)
    }
}

/** Wecker des Rückblicks. */
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
