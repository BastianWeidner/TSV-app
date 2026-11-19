package de.dachapp.module

/**
 * „Was ist neu“ nach einem Update (Wunsch Bastian 2026-11-19, gedacht für Tester und Freunde).
 * Reine Logik ohne Android: welche Punkte nach einem Update einmal gezeigt werden. Die Liste selbst
 * steht in app/src/main/kotlin/de/dachapp/Neuigkeiten.kt, gespeichert wird nur der zuletzt gesehene versionCode.
 */
object WasIstNeu {
    /**
     * Ein Punkt der Änderungsliste. [varianten]: „dach-app“, „schwimmen“, „gym“ (wie [UpdateVersion.praefix]),
     * leer = alle. [module]: Unter-App-IDs; der Punkt erscheint nur, wenn mindestens eine davon in der Variante
     * steckt, leer = immer. So sieht Gym keine Café-Punkte und Schwimmen keine Food-&-Boost-Punkte.
     */
    data class Punkt(val text: String, val varianten: Set<String> = emptySet(), val module: Set<String> = emptySet())

    data class Version(val versionCode: Long, val name: String, val punkte: List<Punkt>)

    /** Höchstens so viele Versionen zeigen, falls jemand viele Updates übersprungen hat. */
    const val MAX_VERSIONEN = 3

    fun passt(p: Punkt, variante: String, moduleIds: Set<String>): Boolean =
        (p.varianten.isEmpty() || variante in p.varianten) && (p.module.isEmpty() || p.module.any { it in moduleIds })

    /** Nur die Punkte, die in diese Variante passen; Versionen ohne passenden Punkt fallen weg. Neueste zuerst. */
    fun filtern(liste: List<Version>, variante: String, moduleIds: Set<String>): List<Version> =
        liste.map { v -> v.copy(punkte = v.punkte.filter { passt(it, variante, moduleIds) }) }
            .filter { it.punkte.isNotEmpty() }
            .sortedByDescending { it.versionCode }

    /**
     * Was nach dem Start gezeigt werden soll.
     * [gesehen]: zuletzt gesehener versionCode, null wenn noch nie gespeichert.
     * [neuInstalliert]: frisch installiert statt aktualisiert (dann nichts zeigen, nur merken).
     * Ohne gespeicherten Wert, aber nach einem Update (Version vor dieser Funktion): nur die aktuelle Version zeigen.
     */
    fun zuZeigen(
        liste: List<Version>, installiert: Long, gesehen: Long?, neuInstalliert: Boolean,
        variante: String, moduleIds: Set<String>,
    ): List<Version> {
        if (gesehen == null && neuInstalliert) return emptyList()
        val ab = gesehen ?: (installiert - 1)
        if (installiert <= ab) return emptyList()
        return filtern(liste, variante, moduleIds)
            .filter { it.versionCode in (ab + 1)..installiert }
            .take(MAX_VERSIONEN)
    }
}
