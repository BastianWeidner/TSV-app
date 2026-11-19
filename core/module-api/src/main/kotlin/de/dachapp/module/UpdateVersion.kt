package de.dachapp.module

/**
 * Erkennt neue Versionen im Update-Ordner allein am Dateinamen (Wunsch Bastian 2026-11-19): `dach-app-1.27.0.apk`,
 * `schwimmen-1.27.0.apk`, `gym-1.27.0.apk`, also genau so, wie die APKs aus dem Build heißen. Keine version.json,
 * kein Internet. Reines Kotlin, damit es sich ohne Gerät testen lässt.
 */
object UpdateVersion {
    /** Namensanfang der APK zur App-ID: de.dachapp → dach-app, de.dachapp.schwimmen → schwimmen, de.dachapp.gym → gym. */
    fun praefix(paket: String): String = when {
        paket.endsWith(".schwimmen") -> "schwimmen"
        paket.endsWith(".gym") -> "gym"
        else -> "dach-app"
    }

    /** Version aus dem Dateinamen, z. B. „schwimmen-1.27.0.apk“ → [1, 27, 0]; null bei fremden Namen oder Kopien wie „… (1).apk“. */
    fun ausName(name: String, praefix: String): List<Int>? {
        val m = Regex("^" + Regex.escape(praefix) + "-(\\d+(?:\\.\\d+){0,3})\\.apk$", RegexOption.IGNORE_CASE).matchEntire(name.trim()) ?: return null
        return m.groupValues[1].split('.').map { it.toIntOrNull() ?: return null }
    }

    /** „1.26.1“ → [1, 26, 1]; alles nach einem Nicht-Ziffern-Zeichen (z. B. „-debug“) fällt weg. */
    fun zerlegen(version: String): List<Int> =
        version.trim().takeWhile { it.isDigit() || it == '.' }.split('.').mapNotNull { it.toIntOrNull() }

    /** Vergleich Stelle für Stelle, fehlende Stellen zählen als 0 (1.27 == 1.27.0). */
    fun vergleich(a: List<Int>, b: List<Int>): Int {
        for (i in 0 until maxOf(a.size, b.size)) {
            val d = a.getOrElse(i) { 0 }.compareTo(b.getOrElse(i) { 0 })
            if (d != 0) return d
        }
        return 0
    }

    fun text(v: List<Int>) = v.joinToString(".")

    /** Höchste Datei der eigenen Variante, die neuer als [installiert] ist, oder null. */
    fun <T> neueste(dateien: List<T>, name: (T) -> String, praefix: String, installiert: String): Pair<T, List<Int>>? {
        val jetzt = zerlegen(installiert)
        return dateien.mapNotNull { d -> ausName(name(d), praefix)?.let { d to it } }
            .filter { vergleich(it.second, jetzt) > 0 }
            .maxWithOrNull { x, y -> vergleich(x.second, y.second) }
    }
}
