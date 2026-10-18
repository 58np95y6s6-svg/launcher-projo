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
import androidx.compose.runtime.remember
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
        Column(
            Modifier
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

/** Menu de l'appui long sur une app. */
@Composable
fun AppMenuDialog(
    app: AppInfo,
    isFavorite: Boolean,
    favoriteIndex: Int,
    favoriteCount: Int,
    onToggleFavorite: () -> Unit,
    onMove: (Int) -> Unit,
    onHide: () -> Unit,
    onInfo: () -> Unit,
    onDismiss: () -> Unit,
) {
    val actions = buildList {
        add(MenuAction(if (isFavorite) "Retirer des favoris" else "Ajouter aux favoris", onToggleFavorite))
        if (isFavorite && favoriteIndex > 0) add(MenuAction("Déplacer à gauche") { onMove(-1) })
        if (isFavorite && favoriteIndex in 0 until favoriteCount - 1) add(MenuAction("Déplacer à droite") { onMove(1) })
        add(MenuAction("Masquer l'app", onHide))
        add(MenuAction("Infos de l'app", onInfo))
        add(MenuAction("Annuler") {})
    }
    MenuDialog(app.label, app.packageName, actions, onDismiss)
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
    val coche = { c: String? -> if (c == current) "✓ " else "" }
    val actions = buildList {
        add(MenuAction(coche(null) + "Automatique (recommandé)") { onChoose(null) })
        candidates.forEach { c ->
            val id = c.component.flattenToString()
            add(MenuAction(coche(id) + "★ " + c.label) { onChoose(id) })
        }
        // N'importe quelle app peut aussi servir (ex. app HDMI du fabricant mal nommée)
        allApps.forEach { app ->
            add(MenuAction(app.label) { onChoose("app:" + app.packageName) })
        }
    }
    MenuDialog(
        title = "Pastille « Sources »",
        subtitle = "★ = semble ouvrir l'entrée HDMI. Choisis ce qui doit s'ouvrir.",
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
