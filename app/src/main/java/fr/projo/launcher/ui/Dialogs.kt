package fr.projo.launcher.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
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
import androidx.compose.runtime.withFrameNanos
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
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.ListItem
import androidx.tv.material3.ListItemDefaults
import androidx.tv.material3.LocalContentColor
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import fr.projo.launcher.data.AppInfo
import fr.projo.launcher.data.SourceCandidate
import fr.projo.launcher.modules.HoteModules
import fr.projo.launcher.modules.RegistreModules

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
        val h = habillageDialogue()
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
                .fondDialogue(h)
                .padding(22.u)
        ) {
            Text(title, color = h.texte, fontSize = 22.us, fontWeight = FontWeight.Medium)
            if (subtitle != null) {
                Spacer(Modifier.height(4.u))
                Text(subtitle, color = h.discret, fontSize = 12.us)
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
                        colors = couleursLigne(h),
                        shape = ListItemDefaults.shape(shape = h.formeLigne),
                    )
                }
            }
        }
        // Première ligne sélectionnée (la liste « lazy » peut n'être prête qu'après une image)
        LaunchedEffect(Unit) {
            for (essai in 1..4) {
                withFrameNanos {}
                if (runCatching { premier.requestFocus() }.isSuccess) break
            }
        }
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

// ------------------------------------------------------------------ listes à choix (modules)

/**
 * Ligne d'un [ListeDialog] : action, case à cocher ([coche] non null) ou titre de section
 * ([section], non sélectionnable).
 */
class LigneListe(
    val titre: String,
    val sousTitre: String? = null,
    val coche: Boolean? = null,
    val section: Boolean = false,
    val onClick: () -> Unit = {},
)

/**
 * Dialogue en liste, pilotable à la télécommande : contrairement à [MenuDialog], un clic ne
 * ferme pas le dialogue (cases à cocher, sous-menus). Retour : [onDismiss].
 */
@Composable
fun ListeDialog(
    titre: String,
    sousTitre: String?,
    lignes: List<LigneListe>,
    onDismiss: () -> Unit,
    /** Ligne qui reçoit le focus à l'ouverture (par défaut la première sélectionnable). */
    focusInitial: Int = -1,
) {
    Dialog(onDismissRequest = onDismiss) {
        val cible = remember { FocusRequester() }
        val indexFocus = lignes.getOrNull(focusInitial)?.takeIf { !it.section }?.let { focusInitial }
            ?: lignes.indexOfFirst { !it.section }
        val h = habillageDialogue()
        Column(
            Modifier
                .width(460.u)
                .fondDialogue(h)
                .padding(22.u)
        ) {
            Text(titre, color = h.texte, fontSize = 22.us, fontWeight = FontWeight.Medium)
            if (sousTitre != null) {
                Spacer(Modifier.height(4.u))
                Text(sousTitre, color = h.discret, fontSize = 12.us)
            }
            Spacer(Modifier.height(14.u))
            LazyColumn(
                Modifier.heightIn(max = 380.u),
                verticalArrangement = Arrangement.spacedBy(4.u),
                state = androidx.compose.foundation.lazy.rememberLazyListState(maxOf(0, indexFocus - 2)),
            ) {
                items(lignes.size) { i ->
                    val l = lignes[i]
                    if (l.section) {
                        Text(
                            l.titre, color = h.discret, fontSize = 10.us, letterSpacing = 3.us,
                            fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(start = 6.u, top = 10.u, bottom = 2.u),
                        )
                    } else {
                        ListItem(
                            selected = false,
                            onClick = l.onClick,
                            headlineContent = { Text(l.titre, fontSize = 15.us, maxLines = 1) },
                            supportingContent = l.sousTitre?.let { st -> @Composable { Text(st, fontSize = 12.us, maxLines = 2) } },
                            trailingContent = l.coche?.let { c -> @Composable { CaseACocher(c) } },
                            modifier = if (i == indexFocus) Modifier.focusRequester(cible) else Modifier,
                            colors = couleursLigne(h),
                            shape = ListItemDefaults.shape(shape = h.formeLigne),
                        )
                    }
                }
            }
        }
        LaunchedEffect(Unit) {
            for (essai in 1..4) {
                withFrameNanos {}
                if (runCatching { cible.requestFocus() }.isSuccess) break
            }
        }
    }
}

/** Lignes des dialogues aux couleurs du design (fond transparent, sélection du style). */
@Composable
internal fun couleursLigne(h: HabillageDialogue) = ListItemDefaults.colors(
    containerColor = Color.Transparent,
    contentColor = h.texte,
    focusedContainerColor = h.focusFond,
    focusedContentColor = h.focusTexte,
    pressedContainerColor = h.focusFond,
    pressedContentColor = h.focusTexte,
)

/**
 * Bouton des dialogues (appairage, clé TMDB, saisie d'un nom…) aux couleurs du design :
 * voile du texte au repos (accent pour le bouton principal), sélection du style du panneau.
 */
@Composable
internal fun BoutonDialogue(texte: String, modifier: Modifier = Modifier, accent: Boolean = false, onClick: () -> Unit) {
    val h = habillageDialogue()
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = ClickableSurfaceDefaults.shape(h.formeLigne),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = if (accent) h.accentFond else h.carte,
            contentColor = h.texte,
            focusedContainerColor = h.focusFond,
            focusedContentColor = h.focusTexte,
            pressedContainerColor = h.focusFond,
            pressedContentColor = h.focusTexte,
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.05f),
    ) {
        Text(texte, fontSize = 15.us, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(horizontal = 22.u, vertical = 10.u))
    }
}

/** Case à cocher dessinée (pas de caractère spécial : certaines polices d'Android 9 n'en ont pas). */
@Composable
private fun CaseACocher(coche: Boolean) {
    val forme = RoundedCornerShape(5.u)
    Box(
        Modifier
            .size(20.u)
            .border(2.u, LocalContentColor.current.copy(alpha = if (coche) 1f else 0.6f), forme)
            .padding(4.u)
            .background(if (coche) LocalContentColor.current else Color.Transparent, RoundedCornerShape(2.u)),
    )
}

/**
 * Réglages → Modules : choix du 1er et du 2e module de la zone de modules (ex-bandeau NBA),
 * et accès aux réglages de chaque module choisi.
 */
@Composable
fun ModulesDialog(
    hote: HoteModules,
    onChoisir: (List<String>) -> Unit,
    onDismiss: () -> Unit,
) {
    var etape by remember { mutableStateOf("menu") }
    val modules = hote.modules
    val premier = modules[0]
    val second = modules.getOrNull(1)
    when {
        etape == "choix1" -> ListeDialog(
            titre = "1er module",
            sousTitre = "Affiché seul sur toute la zone, ou à gauche (en haut) avec un 2e module",
            lignes = RegistreModules.tous.map { m ->
                LigneListe(m.nom, m.description, coche = m.id == premier.id) {
                    onChoisir(listOfNotNull(m.id, second?.id?.takeIf { it != m.id }))
                    etape = "menu"
                }
            },
            onDismiss = { etape = "menu" },
            focusInitial = RegistreModules.tous.indexOf(premier),
        )
        etape == "choix2" -> {
            val autres = RegistreModules.tous.filter { it.id != premier.id }
            ListeDialog(
                titre = "2e module",
                sousTitre = "Partage la zone avec « ${premier.nom} »",
                lignes = listOf(
                    LigneListe("Aucun", "${premier.nom} seul, sur toute la zone", coche = second == null) {
                        onChoisir(listOf(premier.id))
                        etape = "menu"
                    },
                ) + autres.map { m ->
                    LigneListe(m.nom, m.description, coche = m.id == second?.id) {
                        onChoisir(listOf(premier.id, m.id))
                        etape = "menu"
                    }
                },
                onDismiss = { etape = "menu" },
                focusInitial = if (second == null) 0 else autres.indexOf(second) + 1,
            )
        }
        etape.startsWith("reglages:") -> {
            val m = RegistreModules.trouver(etape.removePrefix("reglages:"))
            if (m == null) {
                LaunchedEffect(Unit) { etape = "menu" }
            } else {
                m.Reglages(hote.reglagesDe(m.id), hote.donnees) { etape = "menu" }
            }
        }
        else -> ListeDialog(
            titre = "Modules de l'accueil",
            sousTitre = "À la place des scores NBA : un ou deux modules",
            lignes = buildList {
                add(LigneListe("1er module · ${premier.nom}", premier.description) { etape = "choix1" })
                add(
                    LigneListe(
                        "2e module · ${second?.nom ?: "aucun"}",
                        second?.description ?: "Ajouter un 2e module à côté du premier",
                    ) { etape = "choix2" },
                )
                for (m in modules.filter { it.aDesReglages }) {
                    add(LigneListe("Réglages · ${m.nom}", m.resumeReglages(hote.reglagesDe(m.id))) { etape = "reglages:" + m.id })
                }
                add(LigneListe("Fermer") { onDismiss() })
            },
            onDismiss = onDismiss,
        )
    }
}
