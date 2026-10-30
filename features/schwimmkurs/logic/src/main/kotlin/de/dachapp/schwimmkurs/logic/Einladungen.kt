package de.dachapp.schwimmkurs.logic

import java.time.Duration
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

/** Ein Kurs mit den Kindern einer Familie, die darin sitzen. */
data class KursBlock(
    val kurs: Kurs,
    val kinder: List<Kind>,
    val preisProKindCent: Long,
    /** "Achtung: Am 31.01. und 21.02. ist kein Schwimmkurs." oder null. */
    val pausen: String?,
)

/** Eine fertige Einladung an eine Familie. */
data class Einladung(
    val familienSchluessel: String,
    val empfaenger: List<String>,
    /** Kinder der Familie mit ihrem Kurs, sortiert nach Vorname. */
    val plaetze: List<Pair<Kind, Kurs>>,
    val bloecke: List<KursBlock>,
    val betragCent: Long,
    val rabattCent: Long,
    val verwendungszweck: String,
    /** z. B. "bis spätestens Samstag, 11.10.2025" oder der Text aus den Einstellungen. */
    val zahlungsfrist: String,
    val betreff: String,
    /** Mailtext; Zeilen mit "## " am Anfang sind Überschriften. */
    val text: String,
    val haftungTitel: String,
    val haftungText: String,
) {
    val kinderNamen: String get() = Einladungen.aufzaehlen(plaetze.map { it.first.vorname })
    val nachname: String get() = plaetze.map { it.first.nachname }.filter { it.isNotBlank() }.distinct().joinToString("/")

    /** Mailtext ohne Überschrift-Markierungen, z. B. für die Vorschau. */
    val textOhneMarken: String get() = text.lines().joinToString("\n") { it.removePrefix("## ") }
}

object Einladungen {
    private val DE = Locale.GERMANY
    private val DATUM = DateTimeFormatter.ofPattern("dd.MM.yyyy", DE)
    private val TAG_MONAT = DateTimeFormatter.ofPattern("dd.MM.", DE)
    private val UHR = DateTimeFormatter.ofPattern("HH:mm", DE)

    /** Gültig aussehende Mail-Adressen aus einem Feld mit einer oder mehreren Adressen. */
    fun adressen(feld: String): List<String> =
        feld.split(Regex("[,;\\s]+")).map { it.trim().trim('<', '>') }
            .filter { Regex("[^@\\s]+@[^@\\s]+\\.[^@\\s]+").matches(it) }
            .distinctBy { it.lowercase() }

    fun aufzaehlen(teile: List<String>): String = when (teile.size) {
        0 -> ""
        1 -> teile[0]
        else -> teile.dropLast(1).joinToString(", ") + " und " + teile.last()
    }

    fun euro(cent: Long): String = (if (cent < 0) "−" else "") +
        "%d,%02d €".format(DE, kotlin.math.abs(cent) / 100, kotlin.math.abs(cent) % 100)

    fun datum(d: LocalDate): String =
        d.dayOfWeek.getDisplayName(TextStyle.SHORT, DE).trimEnd('.') + ". " + d.format(DATUM)

    fun tagMonat(d: LocalDate): String = d.format(TAG_MONAT)

    fun wochentag(d: LocalDate): String = d.dayOfWeek.getDisplayName(TextStyle.FULL, DE)

    fun zeit(von: LocalTime, bis: LocalTime) = "${von.format(UHR)}–${bis.format(UHR)} Uhr"

    /** Termine eines Kurses als kurzer Text für die Oberfläche. */
    fun termine(kurs: Kurs): String {
        val t = kurs.sortierteTermine
        if (t.isEmpty()) return "Termine folgen"
        val zeiten = t.map { it.beginn to it.ende }.distinct()
        return if (zeiten.size == 1) {
            t.joinToString(", ") { datum(it.datum) } + ", jeweils " + zeit(zeiten[0].first, zeiten[0].second)
        } else {
            t.joinToString("\n") { "${datum(it.datum)}, ${zeit(it.beginn, it.ende)}" }
        }
    }

    /** Preis pro Kind: fest eingetragen oder Termine × (Kursgebühr + Eintritt). */
    fun preis(kurs: Kurs, e: Einstellungen): Long =
        kurs.preisCent ?: (kurs.termine.size * (e.gebuehrProTerminCent + e.eintrittProTerminCent))

    /** Dauer eines Termins in Minuten, wenn alle Termine gleich lang sind. */
    fun minuten(kurs: Kurs): Long? =
        kurs.termine.map { Duration.between(it.beginn, it.ende).toMinutes() }.distinct().singleOrNull()?.takeIf { it > 0 }

    /** Ausfallende Wochen zwischen erstem und letztem Termin, wenn der Kurs immer am selben Wochentag ist. */
    fun pausen(kurs: Kurs): String? {
        val tage = kurs.termine.map { it.datum }.distinct().sorted()
        if (tage.size < 2 || tage.map { it.dayOfWeek }.distinct().size != 1) return null
        val fehlend = generateSequence(tage.first()) { it.plusWeeks(1) }.takeWhile { !it.isAfter(tage.last()) }
            .filter { it !in tage }.toList()
        if (fehlend.isEmpty()) return null
        return "Achtung: Am ${aufzaehlen(fehlend.map(::tagMonat))} ist kein Schwimmkurs."
    }

    /** Alle Familien mit mindestens einem Kind in einem Kurs, sortiert nach Nachname. */
    fun erstellen(bestand: Bestand): List<Einladung> {
        val e = bestand.einstellungen
        return bestand.kinder
            .mapNotNull { k -> bestand.kursVon(k.id)?.let { k to it } }
            .groupBy { it.first.familienSchluessel }
            .map { (schluessel, plaetze) -> einladung(schluessel, plaetze.sortedBy { it.first.vorname.lowercase() }, e) }
            .sortedWith(compareBy({ it.plaetze.first().first.nachname.lowercase() }, { it.kinderNamen.lowercase() }))
    }

    private fun einladung(schluessel: String, plaetze: List<Pair<Kind, Kurs>>, e: Einstellungen): Einladung {
        val kinder = plaetze.map { it.first }
        val bloecke = plaetze.groupBy { it.second.id }.values.map { gruppe ->
            val kurs = gruppe.first().second
            KursBlock(kurs, gruppe.map { it.first }, preis(kurs, e), pausen(kurs))
        }.sortedWith(compareBy({ it.kurs.ersterTag ?: LocalDate.MAX }, { it.kurs.sortierteTermine.firstOrNull()?.beginn }, { it.kurs.name }))
        val summe = bloecke.sumOf { it.preisProKindCent * it.kinder.size }
        val rabatt = if (plaetze.size > 1) minOf(e.geschwisterRabattCent * (plaetze.size - 1), summe) else 0
        val betrag = summe - rabatt
        val frist = bloecke.mapNotNull { it.kurs.zahlungsfrist }.minOrNull()
            ?.let { "bis spätestens ${wochentag(it)}, ${it.format(DATUM)}" } ?: e.zahlungsfrist
        val nachname = kinder.map { it.nachname }.filter { it.isNotBlank() }.distinct().joinToString("/")
        val basis = mapOf(
            "Kinder" to aufzaehlen(kinder.map { it.vorname }),
            "KinderListe" to kinder.joinToString(", ") { "${it.nachname} ${it.vorname}".trim() },
            "Kuerzel" to bloecke.map { it.kurs.kuerzel.trim() }.filter { it.isNotEmpty() }.distinct().joinToString("/"),
            "Nachname" to nachname,
            "Verein" to e.verein,
        )
        val zweck = einsetzen(e.verwendungszweck, basis).replace(Regex("\\s+"), " ").trim().trimStart('-', ' ').trim()
        val werte = basis + mapOf(
            "ist" to if (kinder.size > 1) "sind" else "ist",
            "einenPlatz" to if (kinder.size > 1) "jeweils einen Platz" else "einen Platz",
            "Eltern" to (kinder.map { it.eltern }.firstOrNull { it.isNotBlank() } ?: "Familie $nachname"),
            "Absender" to e.absender,
            "Kontoinhaber" to e.kontoinhaber.ifBlank { e.verein },
            "IBAN" to e.iban,
            "BIC" to e.bic,
            "Zahlungsfrist" to frist,
            "Verwendungszweck" to zweck,
            "Betrag" to euro(betrag),
            "Kurse" to bloecke.joinToString("\n\n") { kursText(it, kinder.size > 1) },
            "Kosten" to kostenText(bloecke, rabatt, betrag, e),
            "KinderDaten" to kinder.joinToString("\n") { k -> k.name + (k.geburtsdatum?.let { ", geb. " + it.format(DATUM) } ?: "") },
            "ErsterTermin" to (plaetze.mapNotNull { it.second.sortierteTermine.firstOrNull() }.minWithOrNull(compareBy({ it.datum }, { it.beginn }))
                ?.let { "${wochentag(it.datum)}, ${it.datum.format(DATUM)}, ${it.beginn.format(UHR)} Uhr" } ?: "folgt"),
        )
        return Einladung(
            familienSchluessel = schluessel,
            empfaenger = kinder.flatMap { adressen(it.email) }.distinctBy { it.lowercase() },
            plaetze = plaetze,
            bloecke = bloecke,
            betragCent = betrag,
            rabattCent = rabatt,
            verwendungszweck = zweck,
            zahlungsfrist = frist,
            betreff = einsetzen(e.betreff, werte),
            text = einsetzen(e.mailText, werte),
            haftungTitel = einsetzen(e.haftungTitel, werte),
            haftungText = einsetzen(e.haftungText, werte),
        )
    }

    /** Ein Kurs in der Mail: Name, für wen, Termine als Liste, Pausen und Hinweis. */
    private fun kursText(b: KursBlock, mehrereKinder: Boolean): String = buildString {
        val k = b.kurs
        append(k.name)
        if (mehrereKinder) append(" – für ").append(aufzaehlen(b.kinder.map { it.vorname }))
        val t = k.sortierteTermine
        if (t.isEmpty()) {
            append("\nDie Termine geben wir noch bekannt.")
            return@buildString
        }
        val zeiten = t.map { it.beginn to it.ende }.distinct()
        val tage = t.map { it.datum.dayOfWeek }.distinct()
        append("\n").append(if (t.size == 1) "1 Termin" else "${t.size} Termine")
        if (zeiten.size == 1) {
            append(", jeweils ")
            if (tage.size == 1) append(wochentag(t[0].datum)).append(" ")
            append(zeit(zeiten[0].first, zeiten[0].second))
        }
        if (k.ort.isNotBlank()) append(", ").append(k.ort)
        append(":")
        t.forEach { append("\n• ").append(datum(it.datum)); if (zeiten.size > 1) append(", ").append(zeit(it.beginn, it.ende)) }
        b.pausen?.let { append("\n").append(it) }
        if (k.hinweis.isNotBlank()) append("\n").append(k.hinweis.trim())
        if (k.kursleitung.isNotBlank()) append("\nKursleitung: ").append(k.kursleitung)
    }

    private fun kostenZeile(b: KursBlock, e: Einstellungen): String {
        val n = b.kurs.termine.size
        if (b.kurs.preisCent != null || n == 0) return euro(b.preisProKindCent)
        val dauer = minuten(b.kurs)?.let { " à $it Minuten" } ?: ""
        return "Kursgebühr $n × ${euro(e.gebuehrProTerminCent)}$dauer" +
            (if (e.eintrittProTerminCent > 0) " + Eintrittspauschale $n × ${euro(e.eintrittProTerminCent)}" else "") +
            " = ${euro(b.preisProKindCent)}"
    }

    private fun kostenText(bloecke: List<KursBlock>, rabatt: Long, betrag: Long, e: Einstellungen): String = buildString {
        val kinder = bloecke.flatMap { b -> b.kinder.map { it to b } }
        if (kinder.size == 1) {
            val b = kinder[0].second
            val n = b.kurs.termine.size
            if (b.kurs.preisCent == null && n > 0) {
                append("Der Kursbeitrag beträgt ${euro(betrag)} und setzt sich so zusammen:\n")
                append("• Kursgebühr: $n × ${euro(e.gebuehrProTerminCent)}${minuten(b.kurs)?.let { " à $it Minuten Unterricht" } ?: ""}\n")
                if (e.eintrittProTerminCent > 0) append("• Eintrittspauschale: $n × ${euro(e.eintrittProTerminCent)}\n")
                append("• Gesamtbetrag: ${euro(betrag)}")
            } else {
                append("Der Kursbeitrag beträgt ${euro(betrag)}.")
            }
            return@buildString
        }
        append("Der Kursbeitrag beträgt insgesamt ${euro(betrag)}:\n")
        kinder.forEach { (k, b) -> append("• ${k.vorname}: ${kostenZeile(b, e)}\n") }
        if (rabatt > 0) append("• Geschwisterrabatt: ${euro(-rabatt)}\n")
        append("• Gesamtbetrag: ${euro(betrag)}")
    }

    /** Ersetzt {Name} durch den Wert; unbekannte Platzhalter bleiben stehen, damit der Fehler auffällt. */
    fun einsetzen(vorlage: String, werte: Map<String, String>): String =
        Regex("\\{([A-Za-zÄÖÜäöüß]+)\\}").replace(vorlage) { m -> werte[m.groupValues[1]] ?: m.value }

    /** Platzhalter mit Erklärung, für die Hilfe in den Einstellungen. */
    val PLATZHALTER = listOf(
        "Nachname" to "Nachname der Familie, z. B. für „Liebe Familie {Nachname},“",
        "Eltern" to "Name des Elternteils, sonst „Familie Nachname“",
        "Kinder" to "Vornamen, z. B. „Anna und Ben“",
        "KinderListe" to "„Nachname Vorname“ je Kind, für den Verwendungszweck",
        "ist" to "„ist“ bei einem Kind, „sind“ bei mehreren",
        "einenPlatz" to "„einen Platz“ bzw. „jeweils einen Platz“",
        "Kurse" to "je Kurs Name, Termine als Liste, Uhrzeit, Ort, Pausen und Hinweis",
        "Kosten" to "Zusammensetzung des Betrags (Kursgebühr, Eintritt, Rabatt)",
        "Betrag" to "Gesamtbetrag der Familie",
        "Zahlungsfrist" to "„bis spätestens Samstag, 11.10.2025“ aus dem Kurs, sonst Text aus den Einstellungen",
        "Kuerzel" to "Kurskürzel, z. B. SK04 2526",
        "Verwendungszweck" to "aus der Vorlage oben",
        "ErsterTermin" to "frühester Kurstag mit Uhrzeit",
        "KinderDaten" to "je Kind Name und Geburtsdatum (für die Haftungserklärung)",
        "Kontoinhaber" to "", "IBAN" to "", "BIC" to "", "Verein" to "", "Absender" to "",
    )
}

/** Standardtexte, angelehnt an die bisherigen Mails des Vereins. Alles ist in den Einstellungen änderbar. */
object Vorlagen {
    const val VEREIN = "TSV Fischach Abteilung Schwimmen"
    const val ABSENDER = "Ihr Team des TSV Fischach Abteilung Schwimmen"
    const val KONTOINHABER = "TSV Fischach - Abteilung Schwimmen"
    const val IBAN = "DE03 7206 9220 0502 5149 31"
    const val ORT = "Hallenbad Fischach"

    const val BETREFF = "Seepferdchen-Schwimmkurs beim TSV Fischach – Ihr Kursplatz für {Kinder}"

    const val MAIL = """Liebe Familie {Nachname},

wir freuen uns, Ihnen mitteilen zu können, dass wir {Kinder} aufgrund unserer Warteliste {einenPlatz} in unserem nächsten Kursblock anbieten können!

## Kurstermine und Ort
{Kurse}

Bitte kommen Sie frühzeitig, damit Ihr Kind pünktlich zu Kursbeginn umgezogen und startbereit ist.

## Kursgebühr und Zahlung
{Kosten}

Der Kursbeitrag ist vollständig zu entrichten. Eine Teilbuchung ist leider nicht möglich, da der Kurs als Block konzipiert ist.

Bitte überweisen Sie den Betrag {Zahlungsfrist} (Zahlungseingang) auf das folgende Konto, um den Platz verbindlich zu buchen:

Empfänger: {Kontoinhaber}
IBAN: {IBAN}
Betrag: {Betrag}
Verwendungszweck: {Verwendungszweck}

## Bestätigung und Fristen
Wichtig: Der Kursplatz gilt erst nach fristgerechtem Zahlungseingang als fest gebucht.

Sollten Sie den Platz nicht wahrnehmen wollen, bitten wir Sie um schnellstmögliche Rückmeldung, damit wir das nächste Kind auf der Warteliste informieren können.

Bitte beachten Sie: Geht die Zahlung nicht fristgerecht ein, verfällt der Platz automatisch und wird an das nächste Kind auf der Warteliste vergeben. Ohne aktive Absage verlieren Sie dabei auch Ihren Platz auf der Warteliste.

## Unterlagen
Im Anhang finden Sie eine Kursübersicht zum Ausdrucken und unsere Haftungserklärung. Mit der Überweisung des Kursbeitrags erklären Sie sich mit den Bedingungen der Haftungserklärung einverstanden. Der erste Kurstag ist {ErsterTermin}.

Bei Fragen stehen wir Ihnen gerne zur Verfügung.

Mit freundlichen Grüßen
{Absender}"""

    const val HAFTUNG_TITEL = "Haftungserklärung Schwimmkurs"

    /** Frühere Standardtexte: Wer sie unverändert gespeichert hat, bekommt beim Laden die aktuellen. */
    private const val ALTE_UNTERLAGEN = "Im Anhang finden Sie eine Kursübersicht zum Ausdrucken und die Haftungserklärung. " +
        "Bitte bringen Sie die Haftungserklärung unterschrieben zum ersten Kurstag ({ErsterTermin}) mit."
    private const val NEUE_UNTERLAGEN = "Im Anhang finden Sie eine Kursübersicht zum Ausdrucken und unsere Haftungserklärung. " +
        "Mit der Überweisung des Kursbeitrags erklären Sie sich mit den Bedingungen der Haftungserklärung einverstanden. " +
        "Der erste Kurstag ist {ErsterTermin}."
    private val HAFTUNG_ALT = HAFTUNG.substringBefore("\n\nMit der Überweisung")

    fun aktualisieren(e: Einstellungen): Einstellungen = e.copy(
        mailText = e.mailText.replace(ALTE_UNTERLAGEN, NEUE_UNTERLAGEN),
        haftungText = if (e.haftungText == HAFTUNG_ALT) HAFTUNG else e.haftungText,
    ).let(::nachbarorteErgaenzen)

    /** Nachbarorte, die seit dem Wunsch vom 2026-10-20 ans Ende des Einzugsgebiets gehören. */
    private val NACHBARORTE = listOf("Kutzenhausen", "Mittelneufnach", "Walkertshofen", "Rommelsried")

    /**
     * Einmalige Umstellung auf Vorrang-Stand 2: Leere oder alte Standardliste wird zur neuen; in jeder anderen Liste
     * werden fehlende Nachbarorte hinten angehängt und aus "außerhalb" genommen. Danach bleibt die Liste, wie der Nutzer sie pflegt.
     */
    private fun nachbarorteErgaenzen(e: Einstellungen): Einstellungen {
        if (e.vorrangStand >= 2) return e
        fun namen(zeilen: List<String>) = zeilen.flatMap { z -> z.split(':', ',', ';').map { it.trim().lowercase() } }.toSet()
        val vorrang = when {
            e.wohnortVorrang.isEmpty() || e.wohnortVorrang == EINZUGSGEBIET_ALT -> EINZUGSGEBIET
            else -> e.wohnortVorrang + NACHBARORTE.filter { it.lowercase() !in namen(e.wohnortVorrang) }
        }
        val nachbarn = NACHBARORTE.map { it.lowercase() }.toSet()
        return e.copy(
            wohnortVorrang = vorrang,
            orteAusserhalb = e.orteAusserhalb.filter { it.trim().lowercase() !in nachbarn },
            vorrangStand = 2,
        )
    }

    /**
     * Einzugsgebiet des TSV Fischach Schwimmen in Vorrangreihenfolge (Wunsch 2026-10-20), mit Ortsteilen und Schreibvarianten.
     * Die vier Nachbarorte am Ende kommen nach Dinkelscherben, aber noch vor Kindern ohne erkennbaren Ort.
     */
    val EINZUGSGEBIET get() = EINZUGSGEBIET_ALT + NACHBARORTE

    private val EINZUGSGEBIET_ALT = listOf(
        "Fischach: Aretsried, Elmischwang, Heimberg, Itzlishofen, Reitenbuch, Siegertshofen, Todtenschläule, Tronetshofen, Willmatshofen, Wollmetshofen",
        "Langenneufnach: Langeneufnach, Habertsweiler, Unterrothan",
        "Gessertshausen: Gesserthausen, Deubach, Dietkirch, Döpshofen, Margertshausen, Oberschönenfeld, Wollishausen",
        "Ustersbach: Baschenegg, Mödishofen, Osterkühbach",
        "Mickhausen: Grimoldsried, Münster, Laiber, Rielhofen",
        "Dinkelscherben: Anried, Breitenbronn, Engertshofen, Ettelried, Fleinhausen, Grünenbaindt, Häder, Holzara, Lindach, Neuhäder, Oberschöneberg, Reischenau, Saulach, Schempach, Siefenwang",
    )

    /** Orte aus der bisherigen Anmeldeliste, die nicht im Einzugsgebiet liegen. */
    val AUSSERHALB get() = listOf(
        "Scherstetten", "Ziemetshausen", "Augsburg", "Neusäß", "Zusmarshausen", "Bobingen", "Schwabmünchen", "Stadtbergen", "Diedorf",
    )

    const val HAFTUNG = """Teilnehmende Kinder:
{KinderDaten}

Hiermit melde ich mein Kind bzw. meine Kinder verbindlich zum oben genannten Schwimmkurs an.

Mein Kind ist gesund und es bestehen keine gesundheitlichen Bedenken gegen die Teilnahme. Über Erkrankungen, Allergien oder Medikamente informiere ich die Kursleitung vor Kursbeginn.

Die Aufsichtspflicht der Kursleitung beginnt mit Beginn der Kursstunde am Beckenrand und endet mit deren Ende. Für den Weg zum Bad und zurück sowie für Umkleide und Duschen bin ich selbst verantwortlich.

Für den Verlust oder die Beschädigung mitgebrachter Gegenstände wird keine Haftung übernommen.

Mit der Überweisung des Kursbeitrags erklären sich die Erziehungsberechtigten mit diesen Bedingungen einverstanden. Eine Unterschrift ist nicht nötig."""
}
