package de.dachapp.schwimmgruppen.logic

import java.time.LocalTime

/** Startwerte nach dem Trainingsplan auf der Vereinsseite. Alles lässt sich in der App ändern. */
object Vorgaben {
    val STRECKEN = listOf(
        "25 m Freistil", "50 m Freistil", "100 m Freistil", "200 m Freistil", "400 m Freistil",
        "25 m Brust", "50 m Brust", "100 m Brust", "200 m Brust",
        "25 m Rücken", "50 m Rücken", "100 m Rücken", "200 m Rücken",
        "25 m Schmetterling", "50 m Schmetterling", "100 m Schmetterling",
        "100 m Lagen", "200 m Lagen",
    )

    val ABZEICHEN = listOf("Seepferdchen", "Bronze", "Silber", "Gold", "Junior-Retter", "Rettungsschwimmer Bronze")

    private fun t(tag: Int, von: String, bis: String) = Trainingszeit(tag, LocalTime.parse(von), LocalTime.parse(bis))

    fun gruppen(): List<Gruppe> = listOf(
        // Gruppe I und II: je Trainingstag andere Schwimmer, deshalb eigene Gruppen. Der Kader ist an beiden Tagen derselbe.
        Gruppe("gruppe-1", "Gruppe I Mittwoch", zeiten = listOf(t(3, "16:00", "17:00"))),
        Gruppe("gruppe-1-6", "Gruppe I Samstag", zeiten = listOf(t(6, "12:00", "13:00"))),
        Gruppe("gruppe-2", "Gruppe II Dienstag", zeiten = listOf(t(2, "17:15", "18:00"))),
        Gruppe("gruppe-2-3", "Gruppe II Mittwoch", zeiten = listOf(t(3, "17:00", "18:00"))),
        Gruppe("gruppe-2-6", "Gruppe II Samstag", zeiten = listOf(t(6, "13:00", "14:00"))),
        Gruppe("gruppe-3", "Gruppe III", zeiten = listOf(t(5, "16:00", "17:00"))),
        Gruppe("gruppe-4", "Gruppe IV", zeiten = listOf(t(5, "17:00", "18:30"))),
        Gruppe("kader", "Kader", zeiten = listOf(t(2, "18:15", "19:30"), t(6, "18:00", "19:30"))),
        Gruppe("wassergymnastik", "Wassergymnastik", GruppenArt.EXTERN, listOf(t(2, "16:30", "17:00"), t(4, "16:30", "17:00")), nurZaehlen = true),
        Gruppe("erwachsene", "Erwachsenenschwimmen / Stiloptimierung", GruppenArt.EXTERN, listOf(t(4, "17:15", "17:45")), nurZaehlen = true),
    )
}
