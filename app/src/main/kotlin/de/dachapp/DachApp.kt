package de.dachapp

import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Backup
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import de.dachapp.module.SubApp
import java.time.LocalTime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/** Pseudo-ID der Sicherungsseite (keine Unter-App heißt so). */
private const val SICHERUNG = "__sicherung"

/** Kurzer Name klein unter jeder Kachel; Beschreibung und Langtext gibt es nur über das „i“. */
private const val NAME_UNTER_KACHEL = true

/**
 * Übersicht aller Unter-Apps; die gewählte Unter-App füllt den ganzen Bildschirm und bekommt
 * die Farbe ihres Icons als eigenes Farbschema.
 */
@Composable
fun DachApp(modules: List<SubApp>, start: String? = null, startNr: Int = 0) {
    var openId by rememberSaveable { mutableStateOf<String?>(null) }
    // Aus einer Benachrichtigung oder einem Widget geöffnet: gleich die passende Unter-App zeigen.
    LaunchedEffect(start, startNr) { if (start != null && modules.any { it.id == start }) openId = start }

    AnimatedContent(
        targetState = openId,
        transitionSpec = {
            if (targetState != null) {
                (fadeIn(tween(220)) + scaleIn(tween(220), initialScale = 0.94f)) togetherWith fadeOut(tween(120))
            } else {
                fadeIn(tween(220)) togetherWith (fadeOut(tween(160)) + scaleOut(tween(160), targetScale = 0.94f))
            }
        },
        label = "Unter-App",
    ) { id ->
        val open = modules.firstOrNull { it.id == id }
        if (id == SICHERUNG) {
            Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                Box(Modifier.fillMaxSize().background(farbSchimmer()).safeDrawingPadding()) {
                    SicherungScreen(modules, onBack = { openId = null })
                }
            }
        } else if (open != null) {
            val info = ModulInfos.zu(open.id, open.description)
            DachTheme(info.farbe) {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    Box(Modifier.fillMaxSize().background(farbSchimmer()).safeDrawingPadding()) {
                        // Zuerst registriert = geringere Priorität: eigene BackHandler der Unter-App gewinnen.
                        BackHandler { openId = null }
                        open.Content(onExit = { openId = null })
                    }
                }
            }
        } else {
            Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                Box(Modifier.safeDrawingPadding()) {
                    Overview(modules, onOpen = { openId = it.id }, onSicherung = { openId = SICHERUNG })
                }
            }
        }
    }
}

/** Merkt sich dauerhaft, welche Unter-Apps in der Übersicht ausgeblendet sind. Ihre Daten bleiben unberührt. */
private class Ausgeblendet(context: Context) {
    private val prefs = context.getSharedPreferences("dach_uebersicht", Context.MODE_PRIVATE)

    fun laden(): Set<String> = prefs.getStringSet(KEY, emptySet()).orEmpty().toSet()

    fun speichern(ids: Set<String>) {
        prefs.edit().putStringSet(KEY, ids).apply()
    }

    private companion object {
        const val KEY = "ausgeblendet"
    }
}

private fun gruss(): String = when (LocalTime.now().hour) {
    in 5..10 -> "Guten Morgen"
    in 11..17 -> "Hallo"
    in 18..22 -> "Guten Abend"
    else -> "Gute Nacht"
}

@Composable
private fun Overview(modules: List<SubApp>, onOpen: (SubApp) -> Unit, onSicherung: () -> Unit) {
    val context = LocalContext.current
    val speicher = remember(context) { Ausgeblendet(context.applicationContext) }
    var ausgeblendet by remember { mutableStateOf(speicher.laden()) }
    var einstellungen by rememberSaveable { mutableStateOf(false) }
    var infoId by rememberSaveable { mutableStateOf<String?>(null) }
    val sichtbar = modules.filter { it.id !in ausgeblendet }
    var heute by remember { mutableStateOf<List<Pair<SubApp, String>>>(emptyList()) }
    // Heute-Zeile: jede Unter-App darf einen kurzen Hinweis liefern; jede Minute neu.
    LaunchedEffect(sichtbar) {
        while (true) {
            heute = withContext(Dispatchers.IO) { sichtbar.mapNotNull { m -> runCatching { m.heute(context.applicationContext) }.getOrNull()?.let { m to it } } }
            delay(60_000)
        }
    }

    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        // Zwei Spalten auf dem Handy, auf dem Tablet entsprechend mehr.
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 150.dp),
            modifier = Modifier.widthIn(max = 720.dp).fillMaxWidth(),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Row(
                    Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            gruss(),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Text(stringResource(R.string.app_name), style = MaterialTheme.typography.headlineLarge)
                    }
                    FilledTonalIconButton(onClick = onSicherung, modifier = Modifier.padding(end = 8.dp)) {
                        Icon(Icons.Outlined.Backup, contentDescription = "Sicherung")
                    }
                    FilledTonalIconButton(onClick = { einstellungen = true }) {
                        Icon(Icons.Outlined.Settings, contentDescription = "Module ein- und ausblenden")
                    }
                }
            }
            if (heute.isNotEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        heute.forEach { (m, text) ->
                            val info = ModulInfos.zu(m.id, m.description)
                            AssistChip(
                                onClick = { onOpen(m) },
                                label = { Text(text, maxLines = 1) },
                                leadingIcon = { Icon(info.icon, contentDescription = null, tint = info.farbe, modifier = Modifier.size(18.dp)) },
                            )
                        }
                    }
                }
            }
            items(sichtbar, key = { it.id }) { module ->
                ModulKachel(module, onOpen = { onOpen(module) }, onInfo = { infoId = module.id })
            }
            if (sichtbar.isEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Text(
                        "Alle Module sind ausgeblendet. Über das Zahnrad oben rechts lassen sie sich wieder einblenden.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }

    modules.firstOrNull { it.id == infoId }?.let { module ->
        val info = ModulInfos.zu(module.id, module.description)
        // Der Dialog trägt schon die Farbe der Unter-App.
        DachTheme(info.farbe) {
            AlertDialog(
                onDismissRequest = { infoId = null },
                icon = { ModulSymbol(info, groesse = 64.dp) },
                title = { Text(module.title, textAlign = TextAlign.Center) },
                text = {
                    Text(info.info, Modifier.verticalScroll(rememberScrollState()))
                },
                confirmButton = { TextButton(onClick = { infoId = null; onOpen(module) }) { Text("Öffnen") } },
                dismissButton = { TextButton(onClick = { infoId = null }) { Text("Schließen") } },
            )
        }
    }

    if (einstellungen) {
        AlertDialog(
            onDismissRequest = { einstellungen = false },
            icon = { Icon(Icons.Outlined.Settings, contentDescription = null) },
            title = { Text("Module in der Übersicht") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    Text(
                        "Ausgeblendete Module verschwinden nur aus der Übersicht, ihre Daten bleiben erhalten.",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(bottom = 8.dp),
                    )
                    modules.forEach { module ->
                        val an = module.id !in ausgeblendet
                        val umschalten = {
                            ausgeblendet = if (an) ausgeblendet + module.id else ausgeblendet - module.id
                            speicher.speichern(ausgeblendet)
                        }
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .heightIn(min = 56.dp)
                                .clickable(role = Role.Switch, onClick = umschalten),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            ModulSymbol(ModulInfos.zu(module.id, module.description), groesse = 40.dp)
                            Text(
                                module.title,
                                style = MaterialTheme.typography.bodyLarge,
                                modifier = Modifier.weight(1f).padding(horizontal = 12.dp),
                            )
                            Switch(checked = an, onCheckedChange = { umschalten() })
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { einstellungen = false }) { Text("Fertig") } },
        )
    }
}

/** Sanfter Schimmer der Icon-Farbe oben auf jeder Seite, der nach unten in den Hintergrund ausläuft. */
@Composable
private fun farbSchimmer(): Brush {
    val oben = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)
    return Brush.verticalGradient(0f to oben, 0.3f to Color.Transparent)
}

/** Verlauf in der Icon-Farbe, hell oben links, kräftiger unten rechts. */
@Composable
private fun kachelVerlauf(farbe: Color): Brush {
    val dunkel = isSystemInDarkTheme()
    return remember(farbe, dunkel) {
        val (oben, unten) = if (dunkel) 54f to 38f else 64f to 46f
        Brush.linearGradient(listOf(Farbschema.ton(farbe, oben), Farbschema.ton(farbe, unten)))
    }
}

/** Große Icon-Kachel: Tippen öffnet, „i“ oder langes Drücken zeigt die Beschreibung. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ModulKachel(module: SubApp, onOpen: () -> Unit, onInfo: () -> Unit) {
    val info = ModulInfos.zu(module.id, module.description)
    val form = RoundedCornerShape(30.dp)
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .shadow(10.dp, form, ambientColor = info.farbe, spotColor = info.farbe)
                .clip(form)
                .background(kachelVerlauf(info.farbe))
                .drawBehind {
                    // Zwei weiche Lichtkreise geben der Fläche etwas Verspieltes.
                    drawCircle(Color.White.copy(alpha = 0.14f), size.minDimension * 0.42f, Offset(size.width * 0.92f, size.height * 0.04f))
                    drawCircle(Color.White.copy(alpha = 0.08f), size.minDimension * 0.30f, Offset(size.width * 0.06f, size.height * 0.98f))
                }
                .combinedClickable(
                    onClickLabel = "Öffnen",
                    onLongClickLabel = "Info",
                    onLongClick = onInfo,
                    onClick = onOpen,
                )
                .semantics { contentDescription = module.title },
            contentAlignment = Alignment.Center,
        ) {
            Icon(info.icon, contentDescription = null, tint = Color.White, modifier = Modifier.fillMaxSize(0.42f))
            IconButton(onClick = onInfo, modifier = Modifier.align(Alignment.TopEnd).padding(4.dp)) {
                Icon(
                    Icons.Outlined.Info,
                    contentDescription = "Info zu ${module.title}",
                    tint = Color.White.copy(alpha = 0.9f),
                )
            }
        }
        if (NAME_UNTER_KACHEL) {
            Text(
                module.title,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}

/** Kleines Symbol in der Kachel-Optik, für Dialoge und Listen. */
@Composable
private fun ModulSymbol(info: ModulInfo, groesse: Dp) {
    Box(
        Modifier
            .size(groesse)
            .clip(RoundedCornerShape(groesse * 0.3f))
            .background(kachelVerlauf(info.farbe)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(info.icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(groesse * 0.56f))
    }
}
