package fr.projo.launcher.ui

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import fr.projo.launcher.LauncherViewModel
import fr.projo.launcher.data.CleTmdb
import fr.projo.launcher.data.ReglageNouveautes
import fr.projo.launcher.modules.HoteModules
import fr.projo.launcher.modules.IconesModules
import fr.projo.launcher.modules.LocalHoteModules
import fr.projo.launcher.system.DeviceSnapshot
import java.util.Locale

/*
 * Panneau « Réglages » : CONTENU et COMPORTEMENT, écrits une seule fois pour tous les designs.
 *
 *  - contenuReglages() dresse la liste des sections et des tuiles (textes, icônes, actions) ;
 *  - SettingsPanel() gère le focus (initial, piège, ◀ / Retour pour fermer) et le réglage du volume ;
 *  - l'HABILLAGE (position, matière, forme des tuiles, sélection…) vient du style du design actif :
 *    voir StylesReglages.kt (un style par design) et RenduReglages.kt (le dessin).
 *
 * Ajouter une tuile : une ligne Reglage(...) dans contenuReglages(), rien d'autre.
 */

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
    val design: () -> Unit = {},
    /** Réglages → Modules : ce qu'affiche l'emplacement des scores NBA. */
    val modules: () -> Unit = {},
    /** Design Studio : clé TMDB des suggestions de films et séries. */
    val suggestions: () -> Unit = {},
    /** Télécommande smartphone : appairage, téléphones appairés, aide clavier et navigation. */
    val appairer: () -> Unit = {},
    val telephones: () -> Unit = {},
    val aideTelecommande: () -> Unit = {},
)

/**
 * Une tuile du panneau. Le style du design la dessine comme il veut (ligne, carte, bouton…)
 * mais n'en change jamais le texte ni l'action.
 */
class Reglage(
    /** Clé stable : identité de la tuile, étiquette de test « reglage:<cle> ». */
    val cle: String,
    val icone: ImageVector,
    val titre: String,
    /** État ou description (sous le titre, ou à sa droite si la place le permet). */
    val valeur: String,
    /** Couleur propre du réglage : pastilles colorées, tuiles et affiche de « Une »… */
    val teinte: Color,
    /** Mise en avant : mise à jour disponible, clavier à activer, volume en cours de réglage. */
    val accent: Boolean = false,
    /** Jauge de 0 à 1 (volume, stockage). */
    val jauge: Float? = null,
    /** true : la jauge est un curseur réglable à la télécommande (volume). */
    val curseur: Boolean = false,
    /** Curseur en cours de réglage : ◀ ▶ changent la valeur. */
    val enReglage: Boolean = false,
    /** Barres de signal Wi-Fi (0 à 4). */
    val signal: Int? = null,
    /** Valeur courte mise en avant (« 70 % »). */
    val chiffre: String? = null,
    /** Message à signaler (erreur de téléchargement…), affiché en entier sous la tuile. */
    val note: String? = null,
    /** Choix possibles et choix actif (Design) : pilules de « Une ». */
    val options: List<String> = emptyList(),
    val optionActive: Int = -1,
    val onLongClick: (() -> Unit)? = null,
    val onClick: () -> Unit,
)

/** Groupe de tuiles. [titre] vide : pas d'intitulé (tuiles du haut). */
class SectionReglages(val cle: String, val titre: String, val reglages: List<Reglage>)

/** Ce que le contenu commun transmet au rendu : tuiles, état du focus et comportements. */
class PanneauReglages(
    val sections: List<SectionReglages>,
    val version: String,
    /** Clé du réglage sélectionné. */
    val focus: String,
    /**
     * Comportement d'une tuile (focus initial, réglage du volume, suivi du focus, étiquette de test) :
     * à poser par le rendu sur l'élément focalisable de chaque tuile. [colonne] : 0 pour la
     * colonne de gauche (◀ y ferme le panneau), plus pour les autres colonnes d'une grille ;
     * [finDeRangee] : dernière tuile de sa rangée (▶ n'y mène nulle part, même vers une autre rangée).
     */
    val comportement: (Reglage, colonne: Int, finDeRangee: Boolean) -> Modifier,
) {
    /** Toutes les tuiles, dans l'ordre. */
    val reglages: List<Reglage> = sections.flatMap { it.reglages }
    val selection: Reglage? get() = reglages.firstOrNull { it.cle == focus }
    /** Position de la tuile sélectionnée (1…n). */
    val rang: Int get() = reglages.indexOfFirst { it.cle == focus } + 1
}

private const val CLE_VOLUME = "volume"

/**
 * Panneau « Réglages » de Projo, ouvert par-dessus l'accueil. Volume, Wi-Fi, Bluetooth,
 * stockage et réglages de Projo ; ce qu'Android ne permet pas de régler depuis une app ouvre
 * la page système. La forme du panneau dépend du design (voir StylesReglages.kt).
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
    var volumeEnReglage by remember { mutableStateOf(false) }
    val sections = contenuReglages(
        snapshot, maj, hiddenCount, isDefaultHome, telecommande, actions,
        volumeEnReglage = volumeEnReglage,
        basculerVolume = { volumeEnReglage = !volumeEnReglage },
    )
    val premier = remember { FocusRequester() }
    var focus by remember { mutableStateOf(CLE_VOLUME) }
    var colonneFocus by remember { mutableIntStateOf(0) }
    // Premier focus sur le volume (le panneau peut n'être mis en page qu'après une image)
    LaunchedEffect(Unit) {
        for (essai in 1..4) {
            withFrameNanos {}
            if (runCatching { premier.requestFocus() }.isSuccess) break
        }
    }
    // Volume : OK active le réglage (◀ ▶ règlent, OK / Retour valident) ; hors réglage,
    // ◀ ferme le panneau comme ailleurs. Appui long OK : couper le son.
    val touchesVolume = Modifier
        .onFocusChanged { if (!it.isFocused) volumeEnReglage = false }
        .onPreviewKeyEvent {
            if (!volumeEnReglage) return@onPreviewKeyEvent false
            when (it.key) {
                Key.DirectionLeft -> { if (it.type == KeyEventType.KeyDown) actions.volume(-1); true }
                Key.DirectionRight -> { if (it.type == KeyEventType.KeyDown) actions.volume(1); true }
                // Retour quitte le réglage sans fermer le panneau
                Key.Back -> { if (it.type == KeyEventType.KeyUp) volumeEnReglage = false; true }
                Key.DirectionUp, Key.DirectionDown -> { volumeEnReglage = false; false }
                else -> false
            }
        }
    val premiereCle = sections.firstOrNull()?.reglages?.firstOrNull()?.cle
    val panneau = PanneauReglages(sections, version, focus) { r, colonne, finDeRangee ->
        Modifier
            .testTag("reglage:${r.cle}")
            .then(if (finDeRangee) Modifier.focusProperties { right = FocusRequester.Cancel } else Modifier)
            .then(if (r.cle == premiereCle) Modifier.focusRequester(premier) else Modifier)
            .onFocusChanged {
                if (it.isFocused) {
                    focus = r.cle
                    colonneFocus = colonne
                }
            }
            .then(if (r.curseur) touchesVolume else Modifier)
    }
    PanneauStyle(
        style = LocalDesign.current.styleReglages(),
        p = panneau,
        // ◀ ferme le panneau depuis la colonne de gauche (le réglage du volume consomme la touche avant)
        gaucheFerme = { colonneFocus == 0 },
        fermer = actions.close,
    )
}

/**
 * Les tuiles du panneau, dans l'ordre, pour TOUS les designs. Pour ajouter un réglage :
 * une ligne Reglage(cle, icône, titre, valeur, teinte, onClick = …) dans la bonne section.
 */
@Composable
private fun contenuReglages(
    s: DeviceSnapshot?,
    maj: LauncherViewModel.MajEtat,
    hiddenCount: Int,
    isDefaultHome: Boolean,
    t: ResumeTelecommande,
    actions: SettingsActions,
    volumeEnReglage: Boolean,
    basculerVolume: () -> Unit,
): List<SectionReglages> {
    val design = LocalDesign.current
    val hote = LocalHoteModules.current ?: HoteModules()
    val contexte = LocalContext.current
    // « Affiches des nouveautés » : réglage enregistré, lu une fois
    LaunchedEffect(Unit) { ReglageNouveautes.charger(contexte) }
    val nouveautes = ReglageNouveautes.actif
    // Studio : résumé de la clé TMDB, relu quand elle change (dialogue ouvert depuis le panneau)
    val versionCle = CleTmdb.version
    val resumeCle = if (design == Design.STUDIO) remember(versionCle) { resumeCleTmdb(contexte) } else ""
    val majDispo = maj.info?.available == true || maj.telechargement != null

    // ---------------- Volume (et mise à jour, mise en avant si disponible)
    val niveau = if (s == null) 0f else s.volume.toFloat() / s.volumeMax.coerceAtLeast(1)
    val muet = s?.muet == true
    val volume = Reglage(
        cle = CLE_VOLUME,
        icone = if (muet || niveau == 0f) ProjoIcons.Muet else ProjoIcons.Volume,
        titre = if (volumeEnReglage) "Volume · ◀ ▶" else "Volume",
        valeur = when {
            volumeEnReglage -> "OK ou Retour pour valider"
            muet -> "Son coupé · OK pour régler"
            else -> "OK pour régler · appui long : couper"
        },
        teinte = Teintes.volume,
        accent = volumeEnReglage,
        jauge = if (muet) 0f else niveau,
        curseur = true,
        enReglage = volumeEnReglage,
        chiffre = if (muet) "—" else "${(niveau * 100).toInt()} %",
        onLongClick = actions.mute,
        onClick = {
            if (muet && !volumeEnReglage) actions.mute()
            basculerVolume()
        },
    )
    val prog = maj.telechargement
    val majEnAvant = Reglage(
        cle = "maj",
        icone = ProjoIcons.MiseAJour,
        titre = if (prog != null) "Téléchargement… ${(prog * 100).toInt()} %" else "Mettre à jour Projo",
        valeur = "Nouvelle version : ${maj.info?.remoteName ?: ""}",
        teinte = Teintes.maj,
        accent = true,
        jauge = prog,
        note = maj.message,
        onClick = actions.update,
    )
    val haut = SectionReglages("s-haut", "", listOfNotNull(volume, majEnAvant.takeIf { majDispo }))

    // ---------------- Connexions
    val connexions = SectionReglages(
        "s-connexions", "CONNEXIONS",
        listOf(
            Reglage(
                cle = "wifi",
                icone = ProjoIcons.Wifi,
                titre = s?.reseau ?: "Wi-Fi",
                valeur = when {
                    s == null -> "…"
                    !s.enLigne -> "Hors ligne · choisir un réseau"
                    s.signal >= 0 -> "Connecté · signal ${libelleSignal(s.signal)}"
                    else -> "Connecté"
                },
                teinte = Teintes.wifi,
                signal = s?.signal?.takeIf { it >= 0 },
                onClick = actions.wifi,
            ),
            Reglage(
                cle = "bluetooth",
                icone = ProjoIcons.Bluetooth,
                titre = "Bluetooth",
                valeur = when {
                    s == null -> "…"
                    s.bluetoothActif == null -> "Ouvrir les réglages Bluetooth"
                    s.bluetoothActif == false -> "Désactivé"
                    s.appareilsBluetooth.isEmpty() -> "Activé · aucun appareil associé"
                    else -> s.appareilsBluetooth.take(3).joinToString(" · ")
                },
                teinte = Teintes.bluetooth,
                onClick = actions.bluetooth,
            ),
            run {
                val total = s?.stockageTotal ?: 0L
                val libre = s?.stockageLibre ?: 0L
                Reglage(
                    cle = "stockage",
                    icone = ProjoIcons.Stockage,
                    titre = "Stockage",
                    valeur = if (total > 0) "${go(libre)} libres sur ${go(total)}" else "…",
                    teinte = Teintes.stockage,
                    jauge = if (total > 0) 1f - libre.toFloat() / total else null,
                    onClick = actions.storage,
                )
            },
        ),
    )

    // ---------------- Projo
    val projo = SectionReglages(
        "s-projo", "PROJO",
        listOfNotNull(
            Reglage(
                cle = "accueil",
                icone = ProjoIcons.Accueil,
                titre = "Écran d'accueil",
                valeur = if (isDefaultHome) "Projo est l'écran d'accueil par défaut" else "Projo n'est pas l'écran par défaut",
                teinte = Teintes.accueil,
                onClick = actions.homeApp,
            ),
            Reglage(
                cle = "hdmi",
                icone = ProjoIcons.Sources,
                titre = "Entrée HDMI",
                valeur = "Choisir ce qu'ouvre la tuile Source HDMI",
                teinte = Teintes.hdmi,
                onClick = actions.sources,
            ),
            Reglage(
                cle = "design",
                icone = ProjoIcons.Apps,
                titre = "Design · ${design.nom}",
                valeur = design.resume + " · changer",
                teinte = Teintes.design,
                options = Design.entries.map { it.nom },
                optionActive = design.ordinal,
                onClick = actions.design,
            ),
            Reglage(
                cle = "modules",
                icone = IconesModules.Meteo,
                titre = "Modules · " + hote.modules.joinToString(" + ") { it.nom },
                valeur = "Météo, sport… à la place des scores NBA · changer",
                teinte = Teintes.modules,
                onClick = actions.modules,
            ),
            // Studio seulement : clé TMDB de ses suggestions (jamais la clé elle-même, voir resumeCleTmdb)
            if (design != Design.STUDIO) null else Reglage(
                cle = "suggestions",
                icone = ProjoIcons.Film,
                titre = "Suggestions de films et séries",
                valeur = resumeCle,
                teinte = Teintes.suggestions,
                onClick = actions.suggestions,
            ),
            Reglage(
                cle = "masquees",
                icone = ProjoIcons.Apps,
                titre = "Apps masquées",
                valeur = if (hiddenCount == 0) "Aucune" else "$hiddenCount app(s) · les ré-afficher",
                teinte = Teintes.masquees,
                onClick = actions.hiddenApps,
            ),
            // Photos des films et séries récents en fond de l'accueil (Verre, Pastel, Console, Une, Studio)
            Reglage(
                cle = "nouveautes",
                icone = ProjoIcons.Photo,
                titre = "Affiches des nouveautés",
                valeur = if (nouveautes) "Activées · photos des films et séries récents en fond" else "Désactivées · pas de photo",
                teinte = Teintes.nouveautes,
                onClick = { ReglageNouveautes.ecrire(contexte, !nouveautes) },
            ),
            if (majDispo) null else Reglage(
                cle = "maj",
                icone = ProjoIcons.MiseAJour,
                titre = "Mises à jour",
                valeur = when {
                    maj.verification -> "Vérification…"
                    maj.message != null -> maj.message
                    else -> "Rechercher une nouvelle version"
                },
                teinte = Teintes.maj,
                onClick = actions.checkUpdate,
            ),
        ),
    )

    // ---------------- Télécommande smartphone
    val pret = t.clavierChoisi && t.navigationActive
    val telecommande = SectionReglages(
        "s-telecommande", "TÉLÉCOMMANDE SMARTPHONE",
        listOf(
            Reglage(
                cle = "appairer",
                icone = IconesTelecommande.Qr,
                titre = "Appairer un téléphone",
                valeur = "QR code à scanner avec le téléphone",
                teinte = Teintes.appairer,
                onClick = actions.appairer,
            ),
            Reglage(
                cle = "telephones",
                icone = IconesTelecommande.Telephone,
                titre = "Téléphones appairés",
                valeur = when {
                    t.telephones == 0 -> "Aucun"
                    t.connectes > 0 -> "${t.telephones} appairé(s) · ${t.connectes} connecté(s)"
                    else -> "${t.telephones} appairé(s) · renommer, révoquer"
                },
                teinte = Teintes.telephones,
                onClick = actions.telephones,
            ),
            Reglage(
                cle = "aide-telecommande",
                icone = IconesTelecommande.Clavier,
                titre = "Clavier et navigation",
                valeur = if (pret) "Activés ✓ · texte et flèches dans toutes les applis" else "À activer une fois · OK pour l'aide",
                teinte = Teintes.clavier,
                accent = !pret && t.telephones > 0,
                onClick = actions.aideTelecommande,
            ),
        ),
    )

    // ---------------- Système
    val systeme = SectionReglages(
        "s-systeme", "SYSTÈME",
        listOf(
            Reglage("langue", ProjoIcons.Langue, "Langue et clavier", "Page système", Teintes.langue, onClick = actions.language),
            Reglage("date", ProjoIcons.Horloge, "Date et heure", "Page système", Teintes.date, onClick = actions.date),
            Reglage("son", ProjoIcons.Volume, "Son", "Page système", Teintes.son, onClick = actions.sound),
            Reglage("tous", ProjoIcons.Reglages, "Tous les réglages", "Réglages du projecteur", Teintes.tous, onClick = actions.allSettings),
        ),
    )
    return listOf(haut, connexions, projo, telecommande, systeme)
}

/**
 * Couleur propre de chaque réglage (pastilles colorées façon iOS, tuiles de « Une »…).
 * Un style peut la remplacer (StyleReglages.teintes).
 */
object Teintes {
    val volume = Color(0xFFF59E0B)
    val maj = Color(0xFFF97316)
    val wifi = Color(0xFF1F8FFF)
    val bluetooth = Color(0xFF6366F1)
    val stockage = Color(0xFF8E9AAF)
    val accueil = Color(0xFF06B6D4)
    val hdmi = Color(0xFF10B981)
    val design = Color(0xFF8B5CF6)
    val modules = Color(0xFFEC4899)
    val suggestions = Color(0xFFE11D48)
    val masquees = Color(0xFF64748B)
    val nouveautes = Color(0xFF14B8A6)
    val appairer = Color(0xFF0EA5E9)
    val telephones = Color(0xFF22C55E)
    val clavier = Color(0xFFA855F7)
    val langue = Color(0xFF3B82F6)
    val date = Color(0xFFEF4444)
    val son = Color(0xFFF43F5E)
    val tous = Color(0xFF6B7280)
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
