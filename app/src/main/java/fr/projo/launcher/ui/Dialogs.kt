package fr.projo.launcher.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.window.Dialog
import androidx.tv.material3.ListItem
import androidx.tv.material3.Text
import fr.projo.launcher.data.AppInfo
import fr.projo.launcher.data.SourceCandidate

/** Une ligne d'action dans un menu. */
data class MenuAction(val label: String, val onClick: () -> Unit)

/** Fenêtre de menu générique, centrée, pilotable à la télécommande (Retour = fermer). */
@Composable
fun MenuDialog(
    title: String,
    subtitle: String? = null,
    actions: List<MenuAction>,
    onDismiss: () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        val premier = remember { FocusRequester() }
        // Le menu s'ouvre souvent par un appui long sur OK : tant que ce bouton n'a pas été
        // relâché puis ré-appuyé, on ignore ses événements (sinon le relâchement « cliquerait »
        // sur la première ligne et fermerait le menu aussitôt).
        var arme by remember { mutableStateOf(false) }
        Column(
            Modifier
                .onPreviewKeyEvent { e ->
                    val valider = e.key == Key.DirectionCenter || e.key == Key.Enter || e.key == Key.NumPadEnter
                    if (!valider || arme) return@onPreviewKeyEvent false
                    if (e.type == KeyEventType.KeyDown && e.nativeKeyEvent.repeatCount == 0) {
                        arme = true
                        false // nouvel appui franc : on le laisse passer
                    } else {
                        true // répétitions et relâchement de l'appui long : ignorés
                    }
                }
                .width(420.u)
                .background(Color(0xF2161A22), RoundedCornerShape(20.u))
                .padding(22.u)
        ) {
            Text(title, color = Color.White, fontSize = 22.us, fontWeight = FontWeight.Medium)
            if (subtitle != null) {
                Spacer(Modifier.height(4.u))
                Text(subtitle, color = Color.White.copy(alpha = 0.6f), fontSize = 12.us)
            }
            Spacer(Modifier.height(14.u))
            LazyColumn(
                Modifier.heightIn(max = 380.u),
                verticalArrangement = Arrangement.spacedBy(4.u),
            ) {
                items(actions.size) { i ->
                    val a = actions[i]
                    ListItem(
                        selected = false,
                        onClick = { a.onClick(); onDismiss() },
                        headlineContent = { Text(a.label, fontSize = 15.us) },
                        modifier = if (i == 0) Modifier.focusRequester(premier) else Modifier,
                    )
                }
            }
        }
        LaunchedEffect(Unit) { runCatching { premier.requestFocus() } }
    }
}

/** Liste des apps masquées : un clic les ré-affiche. */
@Composable
fun HiddenAppsDialog(apps: List<AppInfo>, onUnhide: (AppInfo) -> Unit, onDismiss: () -> Unit) {
    MenuDialog(
        title = "Apps masquées",
        subtitle = "Choisis une app pour la ré-afficher",
        actions = apps.map { app -> MenuAction(app.label) { onUnhide(app) } } + MenuAction("Fermer") {},
        onDismiss = onDismiss,
    )
}

/** Choix de ce qu'ouvre la pastille « Sources ». */
@Composable
fun SourcesDialog(
    candidates: List<SourceCandidate>,
    allApps: List<AppInfo>,
    current: String?,
    onChoose: (String?) -> Unit,
    onDismiss: () -> Unit,
) {
    // Anciens choix « Entrée TV » (tv:…) abandonnés : comptent comme « par défaut »
    val actuel = current?.takeUnless { it.startsWith("tv:") }
    val coche = { c: String? -> if (c == actuel) "✓ " else "" }
    val actions = buildList {
        add(MenuAction(coche(null) + "Par défaut : AwSource") { onChoose(null) })
        candidates.filter { it.label != "AwSource" }.forEach { c ->
            val id = c.id
            add(MenuAction(coche(id) + c.label) { onChoose(id) })
        }
    }
    MenuDialog(
        title = "Pastille « Sources »",
        subtitle = "Écran ouvert par la tuile « Source HDMI » et la pastille Sources",
        actions = actions,
        onDismiss = onDismiss,
    )
}

/** Diagnostic (appui long sur « Réglages ») : écran d'accueil par défaut, version. */
@Composable
fun DiagnosticDialog(
    currentHome: String?,
    ourPackage: String,
    homeCandidates: List<String>,
    version: String,
    onChooseHome: () -> Unit,
    onDismiss: () -> Unit,
) {
    val etat = when (currentHome) {
        ourPackage -> "Projo est l'écran d'accueil par défaut ✓"
        null -> "Aucun écran d'accueil par défaut choisi"
        else -> "Écran d'accueil actuel : $currentHome"
    }
    MenuDialog(
        title = "Projo $version",
        subtitle = "$etat\nAccueils disponibles : ${homeCandidates.joinToString()}",
        actions = listOf(
            MenuAction("Choisir l'écran d'accueil", onChooseHome),
            MenuAction("Fermer") {},
        ),
        onDismiss = onDismiss,
    )
}

/** Choix du logo affiché pour une app : celui d'une autre app installée, ou l'original. */
@Composable
fun IconChoiceDialog(
    app: AppInfo,
    allApps: List<AppInfo>,
    onChoose: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    MenuDialog(
        title = "Logo de ${app.label}",
        subtitle = "Choisis l'app dont le logo sera affiché",
        actions = listOf(MenuAction("Logo d'origine") { onChoose(app.packageName) }) +
            allApps.filter { it.packageName != app.packageName }
                .map { autre -> MenuAction(autre.label) { onChoose(autre.packageName) } },
        onDismiss = onDismiss,
    )
}

/** Choix du design de l'écran d'accueil (Réglages → Design). */
@Composable
fun DesignDialog(
    current: Design,
    onChoose: (Design) -> Unit,
    onDismiss: () -> Unit,
) {
    MenuDialog(
        title = "Design de l'accueil",
        subtitle = "Change l'apparence de l'écran d'accueil en un clic",
        actions = Design.entries.map { d ->
            MenuAction((if (d == current) "✓ " else "") + d.nom + " — " + d.resume) { onChoose(d) }
        },
        onDismiss = onDismiss,
    )
}
