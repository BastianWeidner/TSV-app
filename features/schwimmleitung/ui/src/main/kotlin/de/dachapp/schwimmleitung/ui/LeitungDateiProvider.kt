package de.dachapp.schwimmleitung.ui

import androidx.core.content.FileProvider

/**
 * Eigene Unterklasse, damit sich dieser Provider im zusammengeführten Manifest nicht mit dem FileProvider des
 * Schwimmkurses überschneidet (der Manifest-Merger erkennt Provider am Klassennamen).
 */
class LeitungDateiProvider : FileProvider()
