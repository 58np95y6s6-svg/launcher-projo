package fr.projo.launcher.ui

import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.focus.FocusRequester
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.withFrameNanos
import fr.projo.launcher.HomeUi
import fr.projo.launcher.data.AppInfo
import fr.projo.launcher.data.IconLoader
import fr.projo.launcher.data.NbaData
import fr.projo.launcher.data.NbaRepository

/** Toutes les actions déclenchables depuis l'écran d'accueil. */
class HomeActions(
    val launch: (AppInfo) -> Unit,
    val openMenu: (AppInfo) -> Unit,
    val openLibrary: () -> Unit,
    val sources: () -> Unit,
    val sourcesLong: () -> Unit,
    val wifi: () -> Unit,
    val bluetooth: () -> Unit,
    val settings: () -> Unit,
    val settingsLong: () -> Unit,
    val update: () -> Unit,
)

/**
 * Écran d'accueil : prépare l'état commun (sélection, rangées, focus, navigation entre les
 * rangées : [HomeContext]) puis affiche la mise en page du [design] choisi (HomeXxx.kt).
 * Tout se pilote au D-pad.
 */
@Composable
fun HomeScreen(
    ui: HomeUi,
    icons: IconLoader,
    wifiLabel: String,
    /** Incrémenté quand on appuie sur Maison alors que le launcher est déjà affiché. */
    homePressCount: Int,
    /** Incrémenté pour remettre le focus sur le premier favori (après « Retirer… »). */
    retourFocus: Int = 0,
    /** Pastille Réglages et carte « Toutes les apps » : le focus y revient à la fermeture. */
    focusReglages: FocusRequester = remember { FocusRequester() },
    focusBiblio: FocusRequester = remember { FocusRequester() },
    actions: HomeActions,
    nbaData: NbaData?,
    nbaErreur: Boolean,
    nbaRepo: NbaRepository,
    majDisponible: Boolean = false,
    design: Design = Design.PAR_DEFAUT,
) {
    var selection by remember { mutableStateOf<AppInfo?>(null) }
    var couleur by remember { mutableStateOf(IconLoader.COULEUR_PAR_DEFAUT) }

    val defilement = rememberScrollState()
    val rangeeFavoris = rememberLazyListState()
    val rangeeApps = rememberLazyListState()
    val premierFocus = remember { FocusRequester() }
    // Navigation entre les rangées : repart de zéro à chaque changement de design
    val nav = remember(rangeeFavoris, rangeeApps, design) { NavRangees(rangeeFavoris, rangeeApps) }

    // Focus initial (et retour en haut à chaque appui sur Maison ou changement de design)
    LaunchedEffect(ui.loaded, ui.prefs.defaultsApplied, homePressCount, design) {
        if (!ui.loaded) return@LaunchedEffect
        withFrameNanos {}
        defilement.scrollTo(0)
        rangeeFavoris.scrollToItem(0)
        rangeeApps.scrollToItem(0)
        runCatching { premierFocus.requestFocus() }
    }
    LaunchedEffect(retourFocus) {
        if (retourFocus == 0) return@LaunchedEffect
        withFrameNanos {}
        rangeeFavoris.scrollToItem(0)
        withFrameNanos {}
        runCatching { premierFocus.requestFocus() }
    }
    val scope = rememberCoroutineScope()

    val ctx = HomeContext(
        ui = ui, icons = icons, wifiLabel = wifiLabel, actions = actions,
        nbaData = nbaData, nbaErreur = nbaErreur, nbaRepo = nbaRepo, majDisponible = majDisponible,
        premierFocus = premierFocus, focusReglages = focusReglages, focusBiblio = focusBiblio,
        rangeeFavoris = rangeeFavoris, rangeeApps = rangeeApps, nav = nav, defilement = defilement,
        selection = selection, couleur = couleur,
        surFocus = { app, c -> selection = app; couleur = c },
        sansSelection = { selection = null },
        fireTv = LocalFireTv.current,
        // Le premier favori n'existe peut-être plus à l'écran si la rangée a défilé
        sortieNba = {
            scope.launch {
                rangeeFavoris.scrollToItem(0)
                withFrameNanos {}
                runCatching { premierFocus.requestFocus() }
            }
        },
    )
    when (design) {
        Design.VERRE -> HomeVerre(ctx)
        Design.PASTEL -> HomePastel(ctx)
        Design.CONSOLE -> HomeConsole(ctx)
        Design.UNE -> HomeUne(ctx)
        Design.CHAINES -> HomeChaines(ctx)
        Design.STUDIO -> HomeStudio(ctx)
    }
}
