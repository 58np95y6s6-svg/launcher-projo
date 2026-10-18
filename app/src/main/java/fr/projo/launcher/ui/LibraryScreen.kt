package fr.projo.launcher.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.foundation.focusGroup
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.ui.focus.FocusRequester
import fr.projo.launcher.data.AppCategories
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.tv.material3.Text
import fr.projo.launcher.HomeUi
import fr.projo.launcher.data.AppInfo
import fr.projo.launcher.data.IconLoader

/**
 * Bibliothèque « Toutes les apps » (comme sur Apple TV) : grille de toutes les apps installées.
 * Appui long sur une app → l'ajouter à l'accueil ou aux favoris. Retour → accueil.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun LibraryScreen(
    ui: HomeUi,
    icons: IconLoader,
    onLaunch: (AppInfo) -> Unit,
    onMenu: (AppInfo) -> Unit,
    onOpenHidden: () -> Unit,
    onClose: () -> Unit,
) {
    BackHandler(onBack = onClose)
    val premier = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        for (essai in 1..4) {
            withFrameNanos {}
            if (runCatching { premier.requestFocus() }.isSuccess) break
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color(0xFA07080B))
            // Le focus reste dans la bibliothèque (pas de saut vers l'accueil caché derrière)
            .focusProperties { exit = { FocusRequester.Cancel } }
            .focusGroup()
    ) {
        Column(Modifier.fillMaxSize()) {
            Text(
                "Toutes les apps",
                color = Color.White, fontSize = 30.us, fontWeight = FontWeight.Light,
                modifier = Modifier.padding(start = 48.u, top = 34.u),
            )
            Text(
                "Maintiens OK sur une app pour l'ajouter à l'accueil ou aux favoris",
                color = Color.White.copy(alpha = 0.55f), fontSize = 12.us,
                modifier = Modifier.padding(start = 48.u, top = 4.u),
            )
            val rubriques = remember(ui.allApps) { AppCategories.grouper(ui.allApps) }
            LazyVerticalGrid(
                columns = GridCells.Fixed(6),
                contentPadding = PaddingValues(horizontal = 48.u, vertical = 20.u),
                horizontalArrangement = Arrangement.spacedBy(18.u),
                verticalArrangement = Arrangement.spacedBy(14.u),
                modifier = Modifier.fillMaxSize(),
            ) {
                // Apps rangées par sujet : un titre de rubrique sur toute la largeur, puis ses apps
                rubriques.forEachIndexed { r, (rubrique, apps) ->
                    item(key = "rubrique-${rubrique.name}", span = { GridItemSpan(maxLineSpan) }) {
                        Text(
                            rubrique.titre.uppercase() + "  ·  ${apps.size}",
                            color = Color.White.copy(alpha = 0.6f), fontSize = 11.us,
                            fontWeight = FontWeight.SemiBold, letterSpacing = 2.us,
                            modifier = Modifier.padding(top = if (r == 0) 0.u else 10.u),
                        )
                    }
                    itemsIndexed(apps, key = { _, a -> a.packageName }) { i, app ->
                        AppCard(
                            app = app, icons = icons, iconPackage = ui.iconSourceOf(app.packageName),
                            width = 125.u, height = 70.u, showLabel = true,
                            onFocused = { _, _ -> },
                            onClick = { onLaunch(app) },
                            onMenu = { onMenu(app) },
                            modifier = if (r == 0 && i == 0) Modifier.focusRequester(premier) else Modifier,
                        )
                    }
                }
                if (ui.hiddenApps.isNotEmpty()) {
                    item(key = "masquees") {
                        TextCard(
                            text = "Apps masquées (${ui.hiddenApps.size})",
                            width = 125.u, height = 70.u,
                            onClick = onOpenHidden,
                        )
                    }
                }
            }
        }
    }
}
