package de.dachapp

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf

class MainActivity : ComponentActivity() {
    /** Unter-App, die eine Benachrichtigung oder ein Widget öffnen will (Extra "unterApp"). */
    private val start = mutableStateOf<String?>(null)
    private val startNr = mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        // Täglichen Sicherungs-Job wieder einplanen, falls er nach einem Neustart fehlt.
        de.dachapp.module.Gesamtsicherung.planen(applicationContext)
        if (savedInstanceState == null) uebernehmen(intent)
        setContent {
            DachTheme(VariantenInfos.grundfarbe) {
                DachApp(ModuleRegistry.all, start.value, startNr.intValue)
            }
        }
    }

    override fun onStop() {
        super.onStop()
        // Beim Verlassen der App das Heute-Widget auf den neuen Stand bringen.
        val app = applicationContext
        Thread { runCatching { HeuteWidget.aktualisieren(app) } }.start()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        uebernehmen(intent)
    }

    private fun uebernehmen(i: Intent?) {
        val id = i?.getStringExtra("unterApp") ?: return
        start.value = id
        startNr.intValue++
    }
}
