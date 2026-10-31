package de.dachapp.schwimmkurs.logic

import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel

/**
 * GiroCode (EPC-QR-Code, EPC069-12 Version 002): Banking-Apps lesen daraus Empfänger, IBAN, Betrag und
 * Verwendungszweck, die Eltern müssen nichts abtippen.
 */
object GiroCode {

    /** Inhalt des QR-Codes oder null, wenn IBAN oder Betrag nicht passen. */
    fun inhalt(empfaenger: String, iban: String, bic: String, betragCent: Long, zweck: String): String? {
        val ibanKurz = iban.replace(" ", "").uppercase()
        if (!ibanGueltig(ibanKurz) || betragCent <= 0 || betragCent > 99_999_999_999L) return null
        val name = empfaenger.trim().take(70)
        if (name.isEmpty()) return null
        val betrag = "EUR" + (betragCent / 100) + "." + (betragCent % 100).toString().padStart(2, '0')
        return listOf(
            "BCD", "002", "1", "SCT",
            bic.replace(" ", "").uppercase(),
            name,
            ibanKurz,
            betrag,
            "", // Purpose-Code
            "", // strukturierte Referenz (nicht zusammen mit Freitext)
            zweck.replace(Regex("\\s+"), " ").trim().take(140),
        ).joinToString("\n")
    }

    /** QR-Matrix (true = dunkel) mit Fehlerkorrektur M, wie im EPC-Standard verlangt; ohne Ruhezone. */
    fun matrix(inhalt: String): Array<BooleanArray> {
        val hints = mapOf(
            EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M,
            EncodeHintType.CHARACTER_SET to "UTF-8",
            EncodeHintType.MARGIN to 0,
        )
        val bits = QRCodeWriter().encode(inhalt, BarcodeFormat.QR_CODE, 0, 0, hints)
        return Array(bits.height) { y -> BooleanArray(bits.width) { x -> bits[x, y] } }
    }

    /** Prüfziffer nach ISO 13616 (Modulo 97). */
    fun ibanGueltig(iban: String): Boolean {
        val i = iban.replace(" ", "").uppercase()
        if (!Regex("[A-Z]{2}[0-9]{2}[A-Z0-9]{11,30}").matches(i)) return false
        val umgestellt = i.drop(4) + i.take(4)
        var rest = 0
        for (c in umgestellt) {
            val wert = if (c.isDigit()) c.toString() else (c - 'A' + 10).toString()
            for (z in wert) rest = (rest * 10 + (z - '0')) % 97
        }
        return rest == 1
    }
}
