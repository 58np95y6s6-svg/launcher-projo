package fr.projo.launcher.ui

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import fr.projo.launcher.HomeUi
import fr.projo.launcher.data.AppInfo
import fr.projo.launcher.data.IconLoader
import fr.projo.launcher.data.NbaData
import fr.projo.launcher.data.NbaRepository
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * Designs de l'écran d'accueil, au choix dans Réglages → Design.
 * Chacun a sa propre mise en page (fichiers HomeXxx.kt) et ses couleurs de panneaux.
 */
enum class Design(val id: String, val nom: String, val resume: String) {
    AURORE("aurore", "Aurore", "Lueur de la couleur de l'app"),
    VERRE("verre", "Verre", "Verre dépoli, lumière douce"),
    PASTEL("pastel", "Pastel", "Formes rondes, tons doux"),
    CONSOLE("console", "Console", "Grand visuel de l'app choisie"),
    GRAPHITE("graphite", "Graphite", "Gris graphite, lueur violet-cyan"),
    UNE("une", "À la une", "Nom géant, l'écran aux couleurs de l'app"),
    CHAINES("chaines", "Chaînes", "Grille de chaînes, heure à segments"),
    PLAYLIST("playlist", "Playlist", "Bibliothèque à gauche, cartes et lecteur, façon appli de musique"),
    STUDIO("studio", "Studio", "Clair, verre et suggestions, sur deux écrans"),
    NEON("neon", "Néon", "Tuiles carrées, cadre cyan, boutons ronds colorés");

    companion object {
        fun from(id: String?): Design = entries.firstOrNull { it.id == id } ?: AURORE
    }
}

/** Couleurs partagées par les panneaux (Réglages, bibliothèque) pour chaque design. */
data class DesignColors(
    /** Fond du panneau Réglages (opaque ou presque). */
    val panneau: Color,
    /** Fond des tuiles non sélectionnées. */
    val carte: Color,
    val texte: Color,
    val texteDiscret: Color,
    val accent: Color,
    /** Tuile sélectionnée. */
    val focusFond: Color,
    val focusTexte: Color,
    /** Voile posé sur l'accueil quand un panneau est ouvert. */
    val voile: Color,
    /** Fond plein écran de la bibliothèque. */
    val fondBibliotheque: Color,
    /** true pour un design clair (textes foncés). */
    val clair: Boolean = false,
)

fun Design.colors(): DesignColors = when (this) {
    Design.AURORE -> DesignColors(
        panneau = Color(0xFB101318), carte = Color.White.copy(alpha = 0.06f),
        texte = Color.White, texteDiscret = Color.White.copy(alpha = 0.55f), accent = Color(0xFF5AB0FF),
        focusFond = Color.White, focusTexte = Color(0xFF0B0D12),
        voile = Color.Black.copy(alpha = 0.45f), fondBibliotheque = Color(0xFA07080B),
    )
    Design.VERRE -> DesignColors(
        panneau = Color(0xF21B1638), carte = Color.White.copy(alpha = 0.09f),
        texte = Color.White, texteDiscret = Color.White.copy(alpha = 0.62f), accent = Color(0xFF0A84FF),
        focusFond = Color.White, focusTexte = Color(0xFF111111),
        voile = Color(0xFF060416).copy(alpha = 0.6f), fondBibliotheque = Color(0xF50B0824),
    )
    Design.PASTEL -> DesignColors(
        panneau = Color(0xFFF7F0F7), carte = Color(0xFFEBE3EF),
        texte = Color(0xFF1D1A22), texteDiscret = Color(0xFF4A4453), accent = Color(0xFF6A4FB3),
        focusFond = Color(0xFFE7DAFF), focusTexte = Color(0xFF2F1868),
        voile = Color(0xFF2F1868).copy(alpha = 0.35f), fondBibliotheque = Color(0xFFF4EDF6),
        clair = true,
    )
    Design.CONSOLE -> DesignColors(
        panneau = Color(0xF5080B1C), carte = Color.White.copy(alpha = 0.065f),
        texte = Color(0xFFF4F6FB), texteDiscret = Color(0xFFF4F6FB).copy(alpha = 0.6f), accent = Color(0xFF8FB4FF),
        focusFond = Color(0xFFF4F6FB), focusTexte = Color(0xFF050817),
        voile = Color(0xFF040716).copy(alpha = 0.55f), fondBibliotheque = Color(0xFA050817),
    )
    Design.GRAPHITE -> DesignColors(
        panneau = Color(0xF7121419), carte = Color.White.copy(alpha = 0.045f),
        texte = Color(0xFFECEDF1), texteDiscret = Color(0xFF8D919E), accent = Color(0xFF5B6CFF),
        focusFond = Color(0xFFECEDF1), focusTexte = Color(0xFF0D0E12),
        voile = Color(0xFF0D0E12).copy(alpha = 0.7f), fondBibliotheque = Color(0xFA0D0E12),
    )
    // Maquette « Une » : panneau #0E0E12, tuiles #17171D, interrupteur actif #10B981, voile #050508 à 55 %
    Design.UNE -> DesignColors(
        panneau = Color(0xFA0E0E12), carte = Color(0xFF17171D),
        texte = Color.White, texteDiscret = Color.White.copy(alpha = 0.62f), accent = Color(0xFF10B981),
        focusFond = Color.White, focusTexte = Color(0xFF0A0A0D),
        voile = Color(0xFF050508).copy(alpha = 0.55f), fondBibliotheque = Color(0xFA0A0A0D),
    )
    // « Chaînes » : blanc grisé, liserés gris, sélection bleu clair (design clair)
    Design.CHAINES -> DesignColors(
        panneau = Color(0xFFF5F6F7), carte = Color(0xFFE4E7EA),
        texte = Color(0xFF34393F), texteDiscret = Color(0xFF656B72), accent = Color(0xFF1FA9DE),
        focusFond = Color(0xFFCDEEFC), focusTexte = Color(0xFF0E4A63),
        voile = Color(0xFF6E757C).copy(alpha = 0.35f), fondBibliotheque = Color(0xFFF2F3F5),
        clair = true,
    )
    // « Playlist » : panneaux #121212 sur fond noir, gris #B3B3B3, accent vert #1ED760
    Design.PLAYLIST -> DesignColors(
        panneau = Color(0xFA121212), carte = Color.White.copy(alpha = 0.07f),
        texte = Color.White, texteDiscret = Color(0xFFB3B3B3), accent = Color(0xFF1ED760),
        focusFond = Color.White, focusTexte = Color.Black,
        voile = Color.Black.copy(alpha = 0.6f), fondBibliotheque = Color(0xFA121212),
    )
    // « Studio » : clair, verre blanc, encre #1C1C1E ; sélection en encre (contraste au projecteur)
    Design.STUDIO -> DesignColors(
        panneau = Color(0xFCF4F5F8), carte = Color(0xFFE5E7EC),
        texte = Color(0xFF1C1C1E), texteDiscret = Color(0xFF5F5F66), accent = Color(0xFF0A84FF),
        focusFond = Color(0xFF1C1C1E), focusTexte = Color.White,
        voile = Color(0xFF1C1C1E).copy(alpha = 0.3f), fondBibliotheque = Color(0xFFF1F2F5),
        clair = true,
    )
    // Néon : fond uni #EBEBEB (clair) ou #2D2D2D (sombre, par défaut), sélection cyan #00C3E3
    Design.NEON -> if (NeonTheme.clair) DesignColors(
        panneau = Color(0xFAF4F4F4), carte = Color.White,
        texte = Color(0xFF2D2D2D), texteDiscret = Color(0xFF6B6B6B), accent = Color(0xFF00A5C0),
        focusFond = Color(0xFF00C3E3), focusTexte = Color(0xFF0B2328),
        voile = Color(0xFF2D2D2D).copy(alpha = 0.35f), fondBibliotheque = Color(0xFAEBEBEB),
        clair = true,
    ) else DesignColors(
        panneau = Color(0xFA262626), carte = Color.White.copy(alpha = 0.07f),
        texte = Color.White, texteDiscret = Color.White.copy(alpha = 0.6f), accent = Color(0xFF00C3E3),
        focusFond = Color(0xFF00C3E3), focusTexte = Color(0xFF0B2328),
        voile = Color.Black.copy(alpha = 0.5f), fondBibliotheque = Color(0xFA2D2D2D),
    )
}

/** Design actif, accessible partout (panneaux, dialogues…). */
val LocalDesign = staticCompositionLocalOf { Design.AURORE }

/**
 * Tout ce dont une mise en page d'accueil a besoin : données, actions, états de défilement
 * et points de retour du focus. Fourni par HomeScreen, identique pour tous les designs.
 */
class HomeContext(
    val ui: HomeUi,
    val icons: IconLoader,
    val wifiLabel: String,
    val actions: HomeActions,
    val nbaData: NbaData?,
    val nbaErreur: Boolean,
    val nbaRepo: NbaRepository,
    val majDisponible: Boolean,
    /** À poser sur le PREMIER favori (ou la carte d'aide s'il n'y a pas de favori). */
    val premierFocus: FocusRequester,
    /** À poser sur le bouton Réglages : le focus y revient quand le panneau se ferme. */
    val focusReglages: FocusRequester,
    /** À poser sur la carte « Toutes les apps » : le focus y revient en quittant la bibliothèque. */
    val focusBiblio: FocusRequester,
    val rangeeFavoris: LazyListState,
    val rangeeApps: LazyListState,
    /** ▼ / ▲ entre les favoris et « Mes apps » (voir NavRangees.kt). */
    val nav: NavRangees,
    /** Défilement vertical éventuel de la page (remis en haut par le bouton Maison). */
    val defilement: ScrollState,
    /** App sélectionnée (null sur une carte qui n'est pas une app) et sa couleur dominante. */
    val selection: AppInfo?,
    val couleur: Color,
    /** À appeler quand une app prend le focus (AppCard le fait via onFocused). */
    val surFocus: (AppInfo, Color) -> Unit,
    /** À appeler quand une carte qui n'est pas une app prend le focus. */
    val sansSelection: () -> Unit,
    /** Sortie du bandeau NBA (◀ ou Retour) : revient au premier favori. */
    val sortieNba: () -> Unit,
    /** Fire TV : pas d'entrée HDMI, donc pas de bouton Sources. */
    val fireTv: Boolean = false,
    /** Incrémenté à chaque appui sur Maison : un design peut remettre son état visuel au départ. */
    val retourAccueil: Int = 0,
)

/** Bouton rapide (Sources, Wi-Fi, Bluetooth, Réglages) avec son action. */
class QuickAction(
    val icon: ImageVector,
    val label: String,
    val onClick: () -> Unit,
    val onLongClick: (() -> Unit)? = null,
    /** Bouton Réglages : reçoit HomeContext.focusReglages. */
    val estReglages: Boolean = false,
)

/** Les 4 boutons rapides, dans l'ordre d'affichage. */
fun HomeContext.quickActions(): List<QuickAction> = listOfNotNull(
    if (fireTv) null else QuickAction(ProjoIcons.Sources, "Sources", actions.sources, actions.sourcesLong),
    QuickAction(ProjoIcons.Wifi, wifiLabel, actions.wifi),
    QuickAction(ProjoIcons.Bluetooth, "Bluetooth", actions.bluetooth),
    QuickAction(ProjoIcons.Reglages, "Réglages", actions.settings, actions.settingsLong, estReglages = true),
)

/** Heure courante, rafraîchie au début de chaque minute. */
@Composable
fun rememberMaintenant(): Long {
    var maintenant by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            maintenant = System.currentTimeMillis()
            delay(60_000L - maintenant % 60_000L + 50L)
        }
    }
    return maintenant
}

private val FR = Locale.FRENCH

/** « Bonjour » / « Bon après-midi » / « Bonsoir » selon l'heure (casse normale). */
fun salutation(t: Long): String = when (Calendar.getInstance().apply { timeInMillis = t }.get(Calendar.HOUR_OF_DAY)) {
    in 5..11 -> "Bonjour"
    in 12..17 -> "Bon après-midi"
    else -> "Bonsoir"
}

/** « 21:42 » */
fun heureTexte(t: Long): String = SimpleDateFormat("HH:mm", FR).format(Date(t))

/** « samedi 18 octobre » (à mettre en capitales ou non selon le design). */
fun dateTexte(t: Long): String = SimpleDateFormat("EEEE d MMMM", FR).format(Date(t))
