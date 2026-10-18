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
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.tv.material3.Text
import fr.projo.launcher.HomeUi
import fr.projo.launcher.data.AppInfo
import fr.projo.launcher.data.IconLoader

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
    actions: HomeActions,
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

    Box(Modifier.fillMaxSize()) {
        GlowBackground(couleur)

        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(defilement)
        ) {
            // --- En-tête : horloge à gauche, pastilles à droite
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(start = 48.u, end = 40.u, top = 34.u),
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
                    modifier = Modifier.padding(top = 6.u),
                )
            }

            Spacer(Modifier.height(78.u))

            // --- Nom de l'app sélectionnée, en grand (fondu enchaîné)
            AnimatedContent(
                targetState = selection?.label.orEmpty(),
                transitionSpec = { fadeIn(tween(220)) togetherWith fadeOut(tween(160)) },
                label = "nom",
                modifier = Modifier.padding(start = 48.u),
            ) { nom ->
                Text(
                    nom, color = Color.White, fontSize = 30.us, fontWeight = FontWeight.Light,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }

            // --- Rangée « Favoris »
            LazyRow(
                state = rangeeFavoris,
                contentPadding = PaddingValues(horizontal = 48.u, vertical = 16.u),
                horizontalArrangement = Arrangement.spacedBy(22.u),
            ) {
                if (ui.loaded && ui.favorites.isEmpty()) {
                    item(key = "aide") {
                        TextCard(
                            text = "Aucun favori.\nMaintiens OK sur une app pour l'ajouter.",
                            width = 220.u, height = 124.u,
                            onClick = {},
                            modifier = Modifier.focusRequester(premierFocus),
                        )
                    }
                }
                itemsIndexed(ui.favorites, key = { _, a -> "fav:" + a.packageName }) { i, app ->
                    AppCard(
                        app = app, icons = icons, iconPackage = ui.iconSourceOf(app.packageName), width = 220.u, height = 124.u, showLabel = false,
                        onFocused = surFocus,
                        onClick = { actions.launch(app) },
                        onMenu = { actions.openMenu(app) },
                        modifier = if (i == 0) Modifier.focusRequester(premierFocus) else Modifier,
                    )
                }
            }

            // --- Rangée « Mes apps » : seulement les apps choisies (dépasse en bas de l'écran)
            Text(
                "MES APPS",
                color = Color.White.copy(alpha = 0.55f),
                fontSize = 11.us, letterSpacing = 3.us, fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(start = 48.u, top = 4.u),
            )
            LazyRow(
                state = rangeeApps,
                contentPadding = PaddingValues(horizontal = 48.u, vertical = 12.u),
                horizontalArrangement = Arrangement.spacedBy(16.u),
            ) {
                itemsIndexed(ui.homeApps, key = { _, a -> "app:" + a.packageName }) { _, app ->
                    AppCard(
                        app = app, icons = icons, iconPackage = ui.iconSourceOf(app.packageName),
                        width = 150.u, height = 84.u, showLabel = true,
                        onFocused = surFocus,
                        onClick = { actions.launch(app) },
                        onMenu = { actions.openMenu(app) },
                    )
                }
                // Bibliothèque : toutes les apps installées (comme sur Apple TV)
                item(key = "bibliotheque") {
                    TextCard(
                        text = "Toutes les apps\n(${ui.allApps.size})",
                        width = 150.u, height = 84.u,
                        onClick = actions.openLibrary,
                        onFocused = { selection = null },
                    )
                }
            }
            Spacer(Modifier.height(40.u))
        }
    }
}
