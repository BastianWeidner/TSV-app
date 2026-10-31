package de.dachapp.schwimmkurs.logic

import kotlinx.serialization.Serializable
import java.time.LocalDate

/** Zahlungseingang einer Familie. Gespeichert wird nur Datum und Betrag, keine weiteren Bankdaten. */
@Serializable
data class Zahlung(
    @Serializable(LocalDateIso::class) val datum: LocalDate,
    val betragCent: Long,
    /** "auszug" = aus dem Kontoauszug zugeordnet, "hand" = von Hand abgehakt. */
    val quelle: String = "hand",
)

/** Eine Gutschrift aus dem Kontoauszug. */
data class Buchung(
    val zeile: Int,
    val datum: LocalDate?,
    val name: String,
    val zweck: String,
    val betragCent: Long,
)

/** Vorschlag: diese Buchung gehört zu dieser Einladung. */
data class Treffer(
    val einladung: Einladung,
    val buchung: Buchung,
    /** Kürzel und Nachname im Zweck und Betrag passt. */
    val sicher: Boolean,
    /** z. B. "Betrag 90,00 € statt 96,50 €"; leer, wenn alles passt. */
    val hinweis: String,
)

data class Abgleich(
    val treffer: List<Treffer>,
    /** Gutschriften, die nach Schwimmkurs aussehen, aber keiner offenen Einladung zugeordnet werden konnten. */
    val unklar: List<Buchung>,
    val gutschriften: Int,
)

/**
 * Liest den CSV-Export eines Kontoauszugs (Sparkasse, Volks- und Raiffeisenbanken und ähnliche) und ordnet
 * Gutschriften den Einladungen zu, über Kurskürzel, Nachname und Betrag.
 */
object Kontoauszug {

    fun buchungen(tabelle: Tabelle): List<Buchung> {
        // Manche Banken setzen Kontoinfos über die eigentliche Kopfzeile; die Zeile mit "Betrag" gilt als Kopf.
        val alle = listOf(tabelle.kopf) + tabelle.zeilen
        val kopfIndex = alle.indexOfFirst { z -> z.any { norm(it).startsWith("betrag") } }
        if (kopfIndex < 0) throw TabellenFehler("Keine Spalte „Betrag“ gefunden. Bitte den Umsatz-Export als CSV wählen.")
        val kopf = alle[kopfIndex].map(::norm)
        fun spalte(vararg namen: String): Int = namen.firstNotNullOfOrNull { n -> kopf.indexOfFirst { it == n }.takeIf { it >= 0 } }
            ?: namen.firstNotNullOfOrNull { n -> kopf.indexOfFirst { it.startsWith(n) }.takeIf { it >= 0 } } ?: -1
        val betrag = spalte("betrag", "betrag (eur)", "umsatz")
        val datum = spalte("buchungstag", "buchungsdatum", "datum", "valutadatum", "wertstellung")
        val zweck = spalte("verwendungszweck", "zweck", "buchungstext")
        val name = spalte("beguenstigter/zahlungspflichtiger", "name zahlungsbeteiligter", "zahlungspflichtiger", "auftraggeber", "name", "empfaenger")
        val sollHaben = spalte("soll/haben", "s/h")
        return alle.drop(kopfIndex + 1).mapIndexedNotNull { i, z ->
            fun f(s: Int) = if (s >= 0) z.getOrNull(s).orEmpty().trim() else ""
            var cent = cent(f(betrag)) ?: return@mapIndexedNotNull null
            if (f(sollHaben).uppercase().startsWith("S")) cent = -cent
            if (cent <= 0) return@mapIndexedNotNull null
            Buchung(i + kopfIndex + 2, KinderImport.datum(f(datum)), f(name), f(zweck), cent)
        }
    }

    /** "1.234,56", "-96,50", "96.50", "96,50 EUR" → Cent. */
    fun cent(text: String): Long? {
        var t = text.replace("EUR", "").replace("€", "").replace(" ", "").replace(" ", "").trim()
        if (t.isEmpty()) return null
        val minus = t.startsWith("-")
        t = t.trimStart('-', '+')
        t = when {
            t.contains(',') -> t.replace(".", "").replace(',', '.')
            t.count { it == '.' } > 1 -> t.replace(".", "")
            else -> t
        }
        val wert = t.toBigDecimalOrNull() ?: return null
        val c = wert.movePointRight(2).setScale(0, java.math.RoundingMode.HALF_UP).toLong()
        return if (minus) -c else c
    }

    fun abgleichen(einladungen: List<Einladung>, buchungen: List<Buchung>, schonBezahlt: Set<String>): Abgleich {
        val offen = einladungen.filter { it.familienSchluessel !in schonBezahlt && it.betragCent > 0 }
        data class Kandidat(val e: Einladung, val b: Buchung, val punkte: Int)
        val kandidaten = offen.flatMap { e ->
            val kuerzel = e.bloecke.map { kompakt(it.kurs.kuerzel) }.filter { it.isNotEmpty() }
            val nachnamen = e.plaetze.map { norm(it.first.nachname) }.filter { it.length >= 2 }.distinct()
            val vornamen = e.plaetze.map { norm(it.first.vorname) }.filter { it.length >= 2 }
            buchungen.mapNotNull { b ->
                val text = norm(b.zweck + " " + b.name)
                val textKompakt = kompakt(b.zweck)
                val hatKuerzel = kuerzel.any { textKompakt.contains(it) }
                val hatNachname = nachnamen.any { Regex("\\b" + Regex.escape(it) + "\\b").containsMatchIn(text) }
                val hatVorname = vornamen.any { Regex("\\b" + Regex.escape(it) + "\\b").containsMatchIn(text) }
                val betragGleich = b.betragCent == e.betragCent
                val punkte = (if (hatKuerzel) 4 else 0) + (if (hatNachname) 3 else 0) + (if (hatVorname) 1 else 0) + (if (betragGleich) 2 else 0)
                // Ohne Namen reicht es nie: Kürzel und Betrag allein passen auf viele Familien.
                if (!hatNachname || punkte < 5) null else Kandidat(e, b, punkte)
            }
        }.sortedByDescending { it.punkte }
        val vergebenE = mutableSetOf<String>()
        val vergebenB = mutableSetOf<Int>()
        val treffer = mutableListOf<Treffer>()
        for (k in kandidaten) {
            if (k.e.familienSchluessel in vergebenE || k.b.zeile in vergebenB) continue
            vergebenE += k.e.familienSchluessel
            vergebenB += k.b.zeile
            val hinweis = if (k.b.betragCent != k.e.betragCent) "Betrag ${Einladungen.euro(k.b.betragCent)} statt ${Einladungen.euro(k.e.betragCent)}" else ""
            treffer += Treffer(k.e, k.b, sicher = k.punkte >= 9, hinweis = hinweis)
        }
        val alleKuerzel = einladungen.flatMap { e -> e.bloecke.map { kompakt(it.kurs.kuerzel) } }.filter { it.isNotEmpty() }.toSet()
        val alleNamen = einladungen.flatMap { e -> e.plaetze.map { norm(it.first.nachname) } }.filter { it.length >= 3 }.toSet()
        val unklar = buchungen.filter { b ->
            b.zeile !in vergebenB && (
                alleKuerzel.any { kompakt(b.zweck).contains(it) } ||
                    norm(b.zweck).contains("schwimmkurs") ||
                    alleNamen.any { Regex("\\b" + Regex.escape(it) + "\\b").containsMatchIn(norm(b.zweck)) }
                )
        }
        return Abgleich(treffer.sortedBy { it.einladung.nachname.lowercase() }, unklar, buchungen.size)
    }

    private fun norm(s: String) = s.lowercase()
        .replace("ä", "ae").replace("ö", "oe").replace("ü", "ue").replace("ß", "ss")
        .replace(Regex("\\s+"), " ").trim()

    /** Ohne Leer- und Sonderzeichen, damit "SK04 2526", "SK04-2526" und "sk042526" gleich sind. */
    private fun kompakt(s: String) = norm(s).replace(Regex("[^a-z0-9]"), "")
}
