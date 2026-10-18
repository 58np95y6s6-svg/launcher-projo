package fr.projo.launcher.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.tv.material3.Text
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
 * Écran d'accueil :
 *  - fond sombre + lueur de la couleur de l'app sélectionnée ;
 *  - en haut : salutation, horloge, date (gauche) et pastilles (droite) ;
 *  - nom de l'app sélectionnée, rangée « Favoris » (grandes cartes) ;
 *  - rangée « Mes apps » (petites cartes, apps choisies) qui dépasse en bas de l'écran,
 *    terminée par la carte « Toutes les apps » qui ouvre la bibliothèque.
 * Tout se pilote au D-pad : la colonne défile toute seule vers l'élément qui a le focus.
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
) {
    var selection by remember { mutableStateOf<AppInfo?>(null) }
    var couleur by remember { mutableStateOf(IconLoader.COULEUR_PAR_DEFAUT) }
    val surFocus = { app: AppInfo, c: Color -> selection = app; couleur = c }

    val defilement = rememberScrollState()
    val rangeeFavoris = rememberLazyListState()
    val rangeeApps = rememberLazyListState()
    val premierFocus = remember { FocusRequester() }


    // Focus initial (et retour en haut à chaque appui sur Maison)
    LaunchedEffect(ui.loaded, ui.prefs.defaultsApplied, homePressCount) {
        if (!ui.loaded) return@LaunchedEffect
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

    Box(Modifier.fillMaxSize()) {
        GlowBackground(couleur)

        Column(Modifier.fillMaxSize()) {
            // --- En-tête fixe : horloge à gauche, pastilles à droite
            EnTete(wifiLabel, actions, majDisponible, focusReglages)

            Column(
                Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .verticalScroll(defilement)
            ) {
                Spacer(Modifier.height(40.u))

                // --- Nom de l'app sélectionnée, en grand (fondu enchaîné)
                AnimatedContent(
                    targetState = selection?.label.orEmpty(),
                    transitionSpec = { fadeIn(tween(220)) togetherWith fadeOut(tween(160)) },
                    label = "nom",
                    modifier = Modifier.padding(start = 56.u),
                ) { nom ->
                    Text(
                        nom, color = Color.White, fontSize = 30.us, fontWeight = FontWeight.Light,
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                }

                // --- Rangée « Favoris »
                LazyRow(
                    state = rangeeFavoris,
                    modifier = Modifier.fadingEdges(rangeeFavoris, 150.u),
                    contentPadding = PaddingValues(horizontal = 56.u, vertical = 18.u),
                    horizontalArrangement = Arrangement.spacedBy(26.u),
                ) {
                    if (ui.loaded && ui.favorites.isEmpty()) {
                        item(key = "aide") {
                            TextCard(
                                text = "Aucun favori.\nMaintiens OK sur une app pour l'ajouter.",
                                width = 240.u, height = 135.u,
                                onClick = {},
                                modifier = Modifier.focusRequester(premierFocus),
                            )
                        }
                    }
                    itemsIndexed(ui.favorites, key = { _, a -> "fav:" + a.packageName }) { i, app ->
                        AppCard(
                            app = app, icons = icons, iconPackage = ui.iconSourceOf(app.packageName),
                            width = 240.u, height = 135.u, showLabel = false,
                            onFocused = surFocus,
                            onClick = { actions.launch(app) },
                            onMenu = { actions.openMenu(app) },
                            modifier = if (i == 0) Modifier.focusRequester(premierFocus) else Modifier,
                        )
                    }
                }

                // --- Rangée « Mes apps » : seulement les apps choisies
                Text(
                    "MES APPS",
                    color = Color.White.copy(alpha = 0.55f),
                    fontSize = 11.us, letterSpacing = 3.us, fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(start = 56.u, top = 2.u),
                )
                LazyRow(
                    state = rangeeApps,
                    modifier = Modifier.fadingEdges(rangeeApps, 150.u),
                    contentPadding = PaddingValues(horizontal = 56.u, vertical = 12.u),
                    horizontalArrangement = Arrangement.spacedBy(18.u),
                ) {
                    itemsIndexed(ui.homeApps, key = { _, a -> "app:" + a.packageName }) { _, app ->
                        AppCard(
                            app = app, icons = icons, iconPackage = ui.iconSourceOf(app.packageName),
                            // Pas de nom dessous : il s'affiche déjà en grand au-dessus des favoris
                            width = 136.u, height = 77.u, showLabel = false,
                            onFocused = surFocus,
                            onClick = { actions.launch(app) },
                            onMenu = { actions.openMenu(app) },
                        )
                    }
                    // Bibliothèque : toutes les apps installées (comme sur Apple TV)
                    item(key = "bibliotheque") {
                        TextCard(
                            text = "Toutes les apps\n(${ui.allApps.size})",
                            width = 136.u, height = 77.u,
                            onClick = actions.openLibrary,
                            onFocused = { selection = null },
                            modifier = Modifier.focusRequester(focusBiblio),
                        )
                    }
                }
                Spacer(Modifier.height(16.u))
            }
        }

        // --- Bandeau NBA sous les pastilles
        NbaPanel(
            data = nbaData,
            erreur = nbaErreur,
            repo = nbaRepo,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 90.u, end = 52.u),
            // Le premier favori n'existe peut-être plus à l'écran si la rangée a défilé
            onExit = {
                scope.launch {
                    rangeeFavoris.scrollToItem(0)
                    withFrameNanos {}
                    runCatching { premierFocus.requestFocus() }
                }
            },
        )
    }
}

/** En-tête : salutation, grande horloge et date à gauche ; pastilles à droite. */
@Composable
private fun EnTete(wifiLabel: String, actions: HomeActions, majDisponible: Boolean, focusReglages: FocusRequester) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(start = 56.u, end = 52.u, top = 40.u),
        verticalAlignment = Alignment.Top,
    ) {
        ClockHeader()
        Spacer(Modifier.weight(1f))
        TopPills(
            wifiLabel = wifiLabel,
            onSources = actions.sources,
            onSourcesLong = actions.sourcesLong,
            onWifi = actions.wifi,
            onBluetooth = actions.bluetooth,
            onSettings = actions.settings,
            onSettingsLong = actions.settingsLong,
            majDisponible = majDisponible,
            onMaj = actions.update,
            focusReglages = focusReglages,
            modifier = Modifier.padding(top = 6.u),
        )
    }
}
