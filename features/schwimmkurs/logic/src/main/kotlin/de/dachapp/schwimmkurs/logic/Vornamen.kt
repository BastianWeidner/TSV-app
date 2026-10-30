package de.dachapp.schwimmkurs.logic

/**
 * Häufige Vornamen von Kindern in Deutschland (Jahrgänge etwa 2012–2024). Hilft beim Import, wenn in einer
 * Namensspalte mal "Vorname Nachname" und mal "Nachname Vorname" steht. Keine vollständige Liste.
 */
internal object Vornamen {
    private val namen = """
        Aaron Adam Adrian Ahmad Ahmed Ali Alexander Alexandra Alina Amelia Amelie Amira Amy Andreas Anna Annika Anton Antonia
        Arne Arthur Aurelia Ava Ayla Ben Benedikt Benjamin Benno Bennet Carl Carla Carlo Caroline Charlotte Christian Clara Clemens
        Constantin Daniel David Dominik Elena Elias Elif Elisa Elisabeth Elise Ella Elli Emil Emilia Emilian Emily Emil Emma Erik
        Eva Fabian Felix Ferdinand Finn Finja Florian Franz Franziska Frederik Frida Frieda Gabriel Greta Hanna Hannah Hannes Heidi
        Helena Henri Henry Hugo Ida Isabel Isabella Jakob Jan Jana Jannik Johanna Johannes Jonas Jonathan Josef Joshua Julia Julian
        Juna Justus Kai Karl Karla Karolina Katharina Kilian Kira Klara Konstantin Lara Laura Lea Leah Lena Leni Lennard Lenny Leo
        Leon Leonard Leonie Leonardo Liam Lilly Lina Linda Linus Lisa Lotta Louis Luca Lucas Luis Luisa Luise Luka Lukas Magdalena
        Magnus Malia Malte Marie Marina Marlene Martha Mathilda Matteo Mattis Max Maxi Maximilian Mia Michael Mila Milan Mira Moritz
        Nala Nele Niklas Nico Noah Noel Nora Ole Oskar Paul Paula Pauline Peter Philipp Quirin Rafael Raphael Romy Ronja Rosa Sarah
        Samuel Sebastian Simon Sofia Sophia Sophie Stella Stefan Svenja Theo Thea Theodor Theresa Tim Tobias Toni Tom Valentin
        Valentina Valerie Vincent Viktoria Xaver Yusuf Zoe Najeeb Mohammed Mohamad Maryam Leyla Lia Malea Liv Juna
    """.split(Regex("\\s+")).filter { it.isNotEmpty() }.map { it.lowercase() }.toSet()

    fun bekannt(wort: String): Boolean = wort.lowercase().substringBefore('-') in namen
}
