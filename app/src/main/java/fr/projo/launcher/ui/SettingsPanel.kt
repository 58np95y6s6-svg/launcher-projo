package fr.projo.launcher.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.focusGroup
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.LocalContext
import fr.projo.launcher.system.FireTv
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import fr.projo.launcher.LauncherViewModel
import fr.projo.launcher.modules.HoteModules
import fr.projo.launcher.modules.IconesModules
import fr.projo.launcher.modules.LocalHoteModules
import fr.projo.launcher.system.DeviceSnapshot
import java.util.Locale

/** Actions du panneau Réglages. */
class SettingsActions(
    val volume: (Int) -> Unit,
    val mute: () -> Unit,
    val wifi: () -> Unit,
    val bluetooth: () -> Unit,
    val storage: () -> Unit,
    val update: () -> Unit,
    val checkUpdate: () -> Unit,
    val hiddenApps: () -> Unit,
    val sources: () -> Unit,
    val homeApp: () -> Unit,
    val language: () -> Unit,
    val date: () -> Unit,
    val sound: () -> Unit,
    val allSettings: () -> Unit,
    val close: () -> Unit,
    /** Fire TV : activer l'ouverture automatique (service d'accessibilité). */
    val ouvertureAuto: () -> Unit = {},
    val design: () -> Unit = {},
    /** Réglages → Modules : ce qu'affiche l'emplacement des scores NBA. */
    val modules: () -> Unit = {},
    /** Télécommande smartphone : appairage, téléphones appairés, aide clavier et navigation. */
    val appairer: () -> Unit = {},
    val telephones: () -> Unit = {},
    val aideTelecommande: () -> Unit = {},
)

/** Couleurs du design choisi (Réglages → Design). */
private val couleurs: DesignColors
    @Composable get() = LocalDesign.current.colors()

/**
 * Panneau « Réglages » de Projo, ouvert sur le côté droit (comme le centre de contrôle de
 * l'Apple TV). Volume, Wi-Fi, Bluetooth, stockage et réglages de Projo dans le même style ;
 * ce qu'Android ne permet pas de régler depuis une app ouvre la page système.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun SettingsPanel(
    snapshot: DeviceSnapshot?,
    maj: LauncherViewModel.MajEtat,
    version: String,
    hiddenCount: Int,
    isDefaultHome: Boolean,
    actions: SettingsActions,
    telecommande: ResumeTelecommande = ResumeTelecommande(),
) {
    BackHandler(onBack = actions.close)
    val fireTv = LocalFireTv.current
    val premier = remember { FocusRequester() }
    // Premier focus : la tuile est dans une liste « lazy », elle peut n'être prête qu'après une image
    LaunchedEffect(Unit) {
        for (essai in 1..4) {
            withFrameNanos {}
            if (runCatching { premier.requestFocus() }.isSuccess) break
        }
    }

    Box(Modifier.fillMaxSize().background(couleurs.voile)) {
        Column(
            Modifier
                .align(Alignment.CenterEnd)
                .fillMaxHeight()
                .width(372.u)
                .clip(RoundedCornerShape(topStart = 26.u, bottomStart = 26.u))
                .background(couleurs.panneau)
                // Le focus ne peut pas s'échapper vers l'accueil caché derrière
                .focusProperties { exit = { FocusRequester.Cancel } }
                .focusGroup()
                // ◀ ferme le panneau (sauf pendant le réglage du volume, qui consomme la touche avant)
                .onKeyEvent {
                    if (it.key == Key.DirectionLeft && it.type == KeyEventType.KeyDown) { actions.close(); true } else false
                }
                .padding(top = 30.u)
        ) {
            Text(
                "Réglages", color = couleurs.texte, fontSize = 28.us, fontWeight = FontWeight.Light,
                modifier = Modifier.padding(horizontal = 26.u),
            )
            Text(
                "Projo $version", color = couleurs.texteDiscret, fontSize = 11.us,
                modifier = Modifier.padding(horizontal = 26.u, vertical = 2.u),
            )
            Spacer(Modifier.height(12.u))

            LazyColumn(
                contentPadding = PaddingValues(start = 22.u, end = 22.u, top = 6.u, bottom = 30.u),
                verticalArrangement = Arrangement.spacedBy(8.u),
            ) {
                // ---------------- Volume
                item(key = "volume") {
                    Volume(snapshot, actions, Modifier.focusRequester(premier))
                }
                // Version Fire TV : ouverture automatique, toujours visible et tout en haut
                item(key = "auto") {
                    val contexte = LocalContext.current
                    val actif = FireTv.redirectionActive(contexte)
                    // Après une mise à jour (Android 9) : le service tourne sans la touche Maison
                    val aFinir = remember(actif) { actif && FireTv.majAFinir(contexte) }
                    Tuile(
                        icone = ProjoIcons.Accueil,
                        titre = "Ouverture automatique",
                        sousTitre = when {
                            aFinir -> "À finir : redémarre le Fire TV (ou OK → Activer en un clic)"
                            actif -> "Activée ✓ · Maison ouvre iBiProjo, appui long : Amazon"
                            else -> "Désactivée · OK pour l'activer"
                        },
                        accent = !actif || aFinir,
                        onClick = actions.ouvertureAuto,
                    )
                }
                // ---------------- Mise à jour (mise en avant si disponible)
                if (maj.info?.available == true || maj.telechargement != null) {
                    item(key = "maj") {
                        val prog = maj.telechargement
                        Tuile(
                            icone = ProjoIcons.MiseAJour,
                            titre = if (prog != null) "Téléchargement… ${(prog * 100).toInt()} %" else "Mettre à jour Projo",
                            sousTitre = "Nouvelle version : ${maj.info?.remoteName ?: ""}",
                            accent = true,
                            onClick = actions.update,
                        )
                    }
                }
                item(key = "s-connexions") { Section("CONNEXIONS") }
                item(key = "wifi") {
                    val s = snapshot
                    Tuile(
                        icone = ProjoIcons.Wifi,
                        titre = s?.reseau ?: "Wi-Fi",
                        sousTitre = when {
                            s == null -> "…"
                            !s.enLigne -> "Hors ligne · choisir un réseau"
                            s.signal >= 0 -> "Connecté · signal ${libelleSignal(s.signal)}"
                            else -> "Connecté"
                        },
                        droite = { s?.signal?.takeIf { it >= 0 }?.let { Barres(it) } },
                        onClick = actions.wifi,
                    )
                }
                item(key = "bluetooth") {
                    val s = snapshot
                    Tuile(
                        icone = ProjoIcons.Bluetooth,
                        titre = "Bluetooth",
                        sousTitre = when {
                            s == null -> "…"
                            s.bluetoothActif == null -> "Ouvrir les réglages Bluetooth"
                            s.bluetoothActif == false -> "Désactivé"
                            s.appareilsBluetooth.isEmpty() -> "Activé · aucun appareil associé"
                            else -> s.appareilsBluetooth.take(3).joinToString(" · ")
                        },
                        onClick = actions.bluetooth,
                    )
                }
                item(key = "stockage") {
                    val s = snapshot
                    val total = s?.stockageTotal ?: 0L
                    val libre = s?.stockageLibre ?: 0L
                    Tuile(
                        icone = ProjoIcons.Stockage,
                        titre = "Stockage",
                        sousTitre = if (total > 0) "${go(libre)} libres sur ${go(total)}" else "…",
                        bas = { if (total > 0) Jauge(1f - libre.toFloat() / total) },
                        onClick = actions.storage,
                    )
                }

                item(key = "s-projo") { Section("PROJO") }
                if (!fireTv) item(key = "accueil") {
                    Tuile(
                        icone = ProjoIcons.Accueil,
                        titre = "Écran d'accueil",
                        sousTitre = if (isDefaultHome) "Projo est l'écran d'accueil par défaut" else "Projo n'est pas l'écran par défaut",
                        onClick = actions.homeApp,
                    )
                }
                if (!fireTv) item(key = "hdmi") {
                    Tuile(
                        icone = ProjoIcons.Sources,
                        titre = "Entrée HDMI",
                        sousTitre = "Choisir ce qu'ouvre la tuile Source HDMI",
                        onClick = actions.sources,
                    )
                }
                item(key = "design") {
                    val d = LocalDesign.current
                    Tuile(
                        icone = ProjoIcons.Apps,
                        titre = "Design · ${d.nom}",
                        sousTitre = d.resume + " · changer",
                        onClick = actions.design,
                    )
                }
                item(key = "modules") {
                    val hote = LocalHoteModules.current ?: HoteModules()
                    Tuile(
                        icone = IconesModules.Meteo,
                        titre = "Modules · " + hote.modules.joinToString(" + ") { it.nom },
                        sousTitre = "Météo, sport… à la place des scores NBA · changer",
                        onClick = actions.modules,
                    )
                }
                item(key = "masquees") {
                    Tuile(
                        icone = ProjoIcons.Apps,
                        titre = "Apps masquées",
                        sousTitre = if (hiddenCount == 0) "Aucune" else "$hiddenCount app(s) · les ré-afficher",
                        onClick = actions.hiddenApps,
                    )
                }
                if (maj.info?.available != true && maj.telechargement == null) {
                    item(key = "maj") {
                        Tuile(
                            icone = ProjoIcons.MiseAJour,
                            titre = "Mises à jour",
                            sousTitre = when {
                                maj.verification -> "Vérification…"
                                maj.message != null -> maj.message
                                else -> "Rechercher une nouvelle version"
                            },
                            onClick = actions.checkUpdate,
                        )
                    }
                } else if (maj.message != null) {
                    item(key = "maj-message") { Text(maj.message, color = Color(0xFFFFB86B), fontSize = 11.us, modifier = Modifier.padding(4.u)) }
                }

                item(key = "s-telecommande") { Section("TÉLÉCOMMANDE SMARTPHONE") }
                item(key = "appairer") {
                    Tuile(IconesTelecommande.Qr, "Appairer un téléphone", "QR code à scanner avec le téléphone", onClick = actions.appairer)
                }
                item(key = "telephones") {
                    val t = telecommande
                    Tuile(
                        icone = IconesTelecommande.Telephone,
                        titre = "Téléphones appairés",
                        sousTitre = when {
                            t.telephones == 0 -> "Aucun"
                            t.connectes > 0 -> "${t.telephones} appairé(s) · ${t.connectes} connecté(s)"
                            else -> "${t.telephones} appairé(s) · renommer, révoquer"
                        },
                        onClick = actions.telephones,
                    )
                }
                item(key = "aide-telecommande") {
                    val t = telecommande
                    val pret = t.clavierChoisi && t.navigationActive
                    Tuile(
                        icone = IconesTelecommande.Clavier,
                        titre = "Clavier et navigation",
                        sousTitre = if (pret) "Activés ✓ · texte et flèches dans toutes les applis" else "À activer une fois · OK pour l'aide",
                        accent = !pret && t.telephones > 0,
                        onClick = actions.aideTelecommande,
                    )
                }

                item(key = "s-systeme") { Section("SYSTÈME") }
                item(key = "langue") { Tuile(ProjoIcons.Langue, "Langue et clavier", "Page système", onClick = actions.language) }
                item(key = "date") { Tuile(ProjoIcons.Horloge, "Date et heure", "Page système", onClick = actions.date) }
                item(key = "son") { Tuile(ProjoIcons.Volume, "Son", "Page système", onClick = actions.sound) }
                item(key = "tous") { Tuile(ProjoIcons.Reglages, "Tous les réglages", "Réglages du projecteur", onClick = actions.allSettings) }
            }
        }
    }
}

@Composable
private fun Section(titre: String) {
    Text(
        titre, color = couleurs.texteDiscret, fontSize = 10.us, letterSpacing = 3.us,
        fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(start = 6.u, top = 12.u, bottom = 2.u),
    )
}

/** Tuile du panneau : icône ronde, titre, sous-titre, élément à droite ou en bas. */
@Composable
private fun Tuile(
    icone: ImageVector,
    titre: String,
    sousTitre: String,
    modifier: Modifier = Modifier,
    accent: Boolean = false,
    droite: (@Composable () -> Unit)? = null,
    bas: (@Composable () -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
    onClick: () -> Unit,
) {
    var focus by remember { mutableStateOf(false) }
    val forme = RoundedCornerShape(16.u)
    Surface(
        onClick = onClick,
        onLongClick = onLongClick,
        modifier = modifier.fillMaxWidth().onFocusChanged { focus = it.isFocused },
        shape = ClickableSurfaceDefaults.shape(forme),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = if (accent) couleurs.accent.copy(alpha = 0.22f) else couleurs.carte,
            contentColor = couleurs.texte,
            focusedContainerColor = couleurs.focusFond,
            focusedContentColor = couleurs.focusTexte,
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.03f),
    ) {
        Column(Modifier.padding(horizontal = 14.u, vertical = 11.u)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(32.u)
                        .clip(CircleShape)
                        .background(
                            when {
                                focus -> couleurs.focusTexte.copy(alpha = 0.08f)
                                accent -> couleurs.accent.copy(alpha = 0.35f)
                                else -> couleurs.texte.copy(alpha = 0.08f)
                            }
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(icone, contentDescription = null, modifier = Modifier.size(17.u))
                }
                Spacer(Modifier.width(12.u))
                Column(Modifier.weight(1f)) {
                    Text(titre, fontSize = 14.us, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        sousTitre, fontSize = 11.us, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        color = if (focus) couleurs.focusTexte.copy(alpha = 0.65f) else couleurs.texteDiscret,
                    )
                }
                if (droite != null) {
                    Spacer(Modifier.width(8.u))
                    droite()
                }
            }
            if (bas != null) {
                Spacer(Modifier.height(9.u))
                bas()
            }
        }
    }
}

/**
 * Volume : OK active le réglage (◀ ▶ règlent, OK / Retour valident) ;
 * hors réglage, ◀ ferme le panneau comme ailleurs. Appui long OK : couper le son.
 */
@Composable
private fun Volume(s: DeviceSnapshot?, actions: SettingsActions, modifier: Modifier) {
    val niveau = if (s == null) 0f else s.volume.toFloat() / s.volumeMax
    val muet = s?.muet == true
    var reglage by remember { mutableStateOf(false) }
    Tuile(
        icone = if (muet || niveau == 0f) ProjoIcons.Muet else ProjoIcons.Volume,
        titre = if (reglage) "Volume · ◀ ▶" else "Volume",
        sousTitre = when {
            reglage -> "OK ou Retour pour valider"
            muet -> "Son coupé · OK pour régler"
            else -> "OK pour régler · appui long : couper"
        },
        accent = reglage,
        modifier = modifier
            .onFocusChanged { if (!it.isFocused) reglage = false }
            .onPreviewKeyEvent {
                if (!reglage) return@onPreviewKeyEvent false
                when (it.key) {
                    Key.DirectionLeft -> { if (it.type == KeyEventType.KeyDown) actions.volume(-1); true }
                    Key.DirectionRight -> { if (it.type == KeyEventType.KeyDown) actions.volume(1); true }
                    // Retour quitte le réglage sans fermer le panneau
                    Key.Back -> { if (it.type == KeyEventType.KeyUp) reglage = false; true }
                    Key.DirectionUp, Key.DirectionDown -> { reglage = false; false }
                    else -> false
                }
            },
        droite = {
            Text(
                if (muet) "—" else "${(niveau * 100).toInt()} %",
                fontSize = 14.us, fontWeight = FontWeight.SemiBold,
            )
        },
        bas = { Jauge(if (muet) 0f else niveau) },
        onClick = {
            if (muet && !reglage) actions.mute()
            reglage = !reglage
        },
        onLongClick = actions.mute,
    )
}

/** Jauge horizontale fine (volume, stockage). */
@Composable
private fun Jauge(valeur: Float) {
    Box(
        Modifier
            .fillMaxWidth()
            .height(5.u)
            .clip(RoundedCornerShape(3.u))
            .background(couleurs.texte.copy(alpha = 0.14f))
    ) {
        Box(
            Modifier
                .fillMaxWidth(valeur.coerceIn(0f, 1f))
                .height(5.u)
                .clip(RoundedCornerShape(3.u))
                .background(couleurs.accent)
        )
    }
}

/** Barres de signal Wi-Fi (0 à 4). */
@Composable
private fun Barres(niveau: Int) {
    Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(2.u)) {
        for (i in 1..4) {
            Box(
                Modifier
                    .width(4.u)
                    .height((4 + i * 3).u)
                    .clip(RoundedCornerShape(1.u))
                    .background(if (i <= niveau) couleurs.accent else couleurs.texte.copy(alpha = 0.2f))
            )
        }
    }
}

private fun libelleSignal(n: Int) = when {
    n >= 4 -> "excellent"
    n == 3 -> "bon"
    n == 2 -> "moyen"
    else -> "faible"
}

private fun go(octets: Long): String {
    val g = octets / 1_000_000_000.0
    return if (g >= 10) String.format(Locale.FRENCH, "%.0f Go", g) else String.format(Locale.FRENCH, "%.1f Go", g)
}
