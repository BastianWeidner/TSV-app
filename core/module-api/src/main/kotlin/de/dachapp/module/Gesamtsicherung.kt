package de.dachapp.module

import android.Manifest
import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.DocumentsContract
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * Sicherung aller Unter-Apps in eine ZIP-Datei: von Hand an einen frei gewählten Ort, automatisch etwa einmal am Tag
 * in eine feste Datei (z. B. in Google Drive, wird jedes Mal überschrieben) und/oder in einen Ordner auf dem Gerät
 * (die letzten [BEHALTEN] bleiben). Alles über das Speicher-Framework von Android, ohne Internet-Berechtigung:
 * das Hochladen übernimmt die Drive-App.
 */
object Gesamtsicherung {
    const val BEHALTEN = 7
    private const val JOB_ID = 74_001
    private const val PREFS = "dach_sicherung"
    private val stempel = DateTimeFormatter.ofPattern("yyyy-MM-dd-HHmm")

    data class Stand(
        val automatisch: Boolean,
        val datei: Uri?,
        val ordner: Uri?,
        val letzteZeit: Long,
        val letzteMeldung: String,
        val letzteOk: Boolean,
    )

    private fun prefs(c: Context) = c.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun stand(c: Context): Stand {
        val p = prefs(c)
        return Stand(
            p.getBoolean("automatisch", false),
            p.getString("datei", null)?.let(Uri::parse),
            p.getString("ordner", null)?.let(Uri::parse),
            p.getLong("letzteZeit", 0L),
            p.getString("letzteMeldung", "").orEmpty(),
            p.getBoolean("letzteOk", true),
        )
    }

    /** Ziel merken; die URI-Berechtigung muss dauerhaft genommen werden, sonst klappt es nachts nicht. */
    fun zielSetzen(c: Context, datei: Uri? = stand(c).datei, ordner: Uri? = stand(c).ordner) {
        val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        val alt = stand(c)
        listOfNotNull(datei, ordner).filter { it != alt.datei && it != alt.ordner }.forEach {
            runCatching { c.contentResolver.takePersistableUriPermission(it, flags) }
        }
        listOfNotNull(alt.datei, alt.ordner).filter { it != datei && it != ordner }.forEach {
            runCatching { c.contentResolver.releasePersistableUriPermission(it, flags) }
        }
        prefs(c).edit().putString("datei", datei?.toString()).putString("ordner", ordner?.toString()).apply()
        planen(c)
    }

    fun automatischSetzen(c: Context, an: Boolean) {
        prefs(c).edit().putBoolean("automatisch", an).apply()
        planen(c)
    }

    fun dateiname(zeit: LocalDateTime = LocalDateTime.now()) = "dach-sicherung-${zeit.format(stempel)}.zip"

    /** Schreibt die Sicherung nach [out]; Rückgabe: Zahl der Dateien. */
    fun schreiben(c: Context, out: OutputStream): Int {
        val app = c.applicationContext
        val kopf = mapOf(
            "zeit" to LocalDateTime.now().toString(),
            "app" to app.packageName,
            "version" to runCatching { app.packageManager.getPackageInfo(app.packageName, 0).versionName.orEmpty() }.getOrDefault(""),
        )
        return Sicherungsarchiv.schreiben(out, Sicherungsarchiv.quellen(app.filesDir, File(app.dataDir, "shared_prefs")), kopf)
    }

    /** In eine bestehende Datei schreiben und auf die neue Länge kürzen (nicht jeder Anbieter kürzt selbst). */
    fun inDatei(c: Context, uri: Uri): Int {
        val pfd = c.contentResolver.openFileDescriptor(uri, "rwt") ?: c.contentResolver.openFileDescriptor(uri, "wt")
            ?: error("Datei lässt sich nicht öffnen")
        return pfd.use { fd ->
            val fos = FileOutputStream(fd.fileDescriptor)
            val zaehler = object : OutputStream() {
                var laenge = 0L
                override fun write(b: Int) { fos.write(b); laenge++ }
                override fun write(b: ByteArray, off: Int, len: Int) { fos.write(b, off, len); laenge += len }
                override fun flush() = fos.flush()
                override fun close() = fos.flush()
            }
            val n = schreiben(c, zaehler)
            runCatching { fos.channel.truncate(zaehler.laenge) }
            runCatching { fos.fd.sync() }
            n
        }
    }

    /** Neue Datei im gewählten Ordner, danach nur die neuesten [BEHALTEN] Sicherungen behalten. */
    fun inOrdner(c: Context, baum: Uri): Int {
        val r = c.contentResolver
        val wurzel = DocumentsContract.buildDocumentUriUsingTree(baum, DocumentsContract.getTreeDocumentId(baum))
        val neu = DocumentsContract.createDocument(r, wurzel, "application/zip", dateiname()) ?: error("Datei im Ordner lässt sich nicht anlegen")
        val n = r.openOutputStream(neu, "w")?.use { schreiben(c, it) } ?: error("Datei im Ordner lässt sich nicht öffnen")
        val kinder = DocumentsContract.buildChildDocumentsUriUsingTree(baum, DocumentsContract.getTreeDocumentId(baum))
        val alte = ArrayList<Pair<String, String>>()
        r.query(kinder, arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME), null, null, null)?.use { cur ->
            while (cur.moveToNext()) {
                val name = cur.getString(1).orEmpty()
                if (name.startsWith("dach-sicherung-") && name.endsWith(".zip")) alte += cur.getString(0) to name
            }
        }
        alte.sortedByDescending { it.second }.drop(BEHALTEN).forEach { (id, _) ->
            runCatching { DocumentsContract.deleteDocument(r, DocumentsContract.buildDocumentUriUsingTree(baum, id)) }
        }
        return n
    }

    /** Die automatische Sicherung jetzt ausführen (auch zum Ausprobieren). Ergebnis steht danach in [stand]. */
    fun automatisch(c: Context): Boolean {
        val s = stand(c)
        val teile = ArrayList<String>()
        var ok = true
        if (s.datei == null && s.ordner == null) {
            teile += "Kein Ziel gewählt."
            ok = false
        }
        s.datei?.let { u ->
            runCatching { inDatei(c, u) }
                .onSuccess { teile += "Datei: $it Dateien gesichert." }
                .onFailure { ok = false; teile += "Datei: fehlgeschlagen (${it.message ?: it.javaClass.simpleName})." }
        }
        s.ordner?.let { u ->
            runCatching { inOrdner(c, u) }
                .onSuccess { teile += "Ordner: $it Dateien gesichert." }
                .onFailure { ok = false; teile += "Ordner: fehlgeschlagen (${it.message ?: it.javaClass.simpleName})." }
        }
        prefs(c).edit().putLong("letzteZeit", System.currentTimeMillis()).putString("letzteMeldung", teile.joinToString(" "))
            .putBoolean("letzteOk", ok).apply()
        return ok
    }

    /** Täglichen Job (neu) einplanen oder entfernen. Beim Start der App aufrufen, falls der Job nach Neustart fehlt. */
    @android.annotation.SuppressLint("MissingPermission") // setPersisted nur nach Prüfung der Berechtigung, siehe unten
    fun planen(c: Context) {
        val js = c.getSystemService(JobScheduler::class.java) ?: return
        val s = stand(c)
        if (!s.automatisch || (s.datei == null && s.ordner == null)) {
            js.cancel(JOB_ID)
            return
        }
        if (js.getPendingJob(JOB_ID) != null) return
        val b = JobInfo.Builder(JOB_ID, ComponentName(c, SicherungsJob::class.java))
            .setPeriodic(24 * 3_600_000L, 6 * 3_600_000L)
            .setRequiresBatteryNotLow(true)
        // Über Neustarts hinweg nur mit dieser Berechtigung (hat die Variante „alle“); sonst plant der nächste App-Start neu.
        if (c.checkSelfPermission(Manifest.permission.RECEIVE_BOOT_COMPLETED) == PackageManager.PERMISSION_GRANTED) b.setPersisted(true)
        runCatching { js.schedule(b.build()) }
    }

    /** Zielordner für einen Sicherungseintrag ("dateien/…" bzw. "prefs/…"). */
    fun zielDatei(c: Context, zipName: String): File? {
        val app = c.applicationContext
        return when {
            zipName.startsWith(Sicherungsarchiv.DATEIEN) -> File(app.filesDir, zipName.removePrefix(Sicherungsarchiv.DATEIEN))
            zipName.startsWith(Sicherungsarchiv.PREFS) -> File(File(app.dataDir, "shared_prefs"), zipName.removePrefix(Sicherungsarchiv.PREFS))
            else -> null
        }
    }

    /**
     * Spielt die Einträge ein, für die [nehmen] true sagt. Ordner (z. B. Fotos), aus denen etwas eingespielt wird,
     * werden vorher geleert, damit der Stand genau der Sicherung entspricht. Danach muss die App neu starten.
     */
    fun einspielen(c: Context, uri: Uri, nehmen: (String) -> Boolean): Int {
        val inhalt = c.contentResolver.openInputStream(uri)?.use { Sicherungsarchiv.inhalt(it) } ?: error("Datei lässt sich nicht lesen")
        require(inhalt.gueltig) { "Das ist keine Sicherung der Dach-App." }
        val app = c.applicationContext
        inhalt.eintraege.filter(nehmen).filter { it.startsWith(Sicherungsarchiv.DATEIEN) }
            .map { it.removePrefix(Sicherungsarchiv.DATEIEN) }.filter { '/' in it }.map { it.substringBefore('/') }.toSet()
            .forEach { File(app.filesDir, it).takeIf { d -> d.isDirectory }?.deleteRecursively() }
        return c.contentResolver.openInputStream(uri)?.use { s ->
            Sicherungsarchiv.auspacken(s) { name -> if (nehmen(name)) zielDatei(c, name) else null }
        } ?: 0
    }
}

/** Läuft etwa einmal am Tag, wenn der Akku nicht knapp ist. */
class SicherungsJob : JobService() {
    override fun onStartJob(params: JobParameters): Boolean {
        Thread {
            val ok = runCatching { Gesamtsicherung.automatisch(applicationContext) }.getOrDefault(false)
            jobFinished(params, !ok)
        }.start()
        return true
    }

    override fun onStopJob(params: JobParameters): Boolean = true
}
