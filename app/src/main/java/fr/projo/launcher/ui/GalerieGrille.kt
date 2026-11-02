package fr.projo.launcher.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.tv.material3.ClickableSurfaceDefaults
import fr.projo.launcher.data.FormatVisuel

/**
 * « Agrandir » : tous les titres de l'onglet en grille plein écran (affiches, ou visuels des
 * apps à la une). OK (ou clic) : ce titre passe au centre du carrousel ; Retour : fermeture.
 * Le focus reste dans la grille ; s'il en sort (touche Maison), la grille se ferme.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
internal fun GrilleGalerie(
    titres: List<StudioSuggestion>,
    courant: Int,
    onglet: OngletGalerie,
    ctx: HomeContext,
    k: Float,
    choisir: (Int) -> Unit,
    fermer: () -> Unit,
) {
    val affiches = titres.all { it is StudioSuggestion.Programme }
    val colonnes = if (affiches) 6 else 4
    val etat = rememberLazyGridState(initialFirstVisibleItemIndex = (courant - courant % colonnes).coerceAtLeast(0))
    val premier = remember { FocusRequester() }
    var aEuLeFocus by remember { mutableStateOf(false) }
    // Une seule sortie : la grille qui disparaît perd aussi le focus (sans refermer une 2e fois)
    val fini = remember { booleanArrayOf(false) }
    val sortir: (() -> Unit) -> Unit = { action ->
        if (!fini[0]) {
            fini[0] = true
            action()
        }
    }
    LaunchedEffect(Unit) {
        withFrameNanos {}
        runCatching { premier.requestFocus() }
    }
    Box(
        Modifier
            .fillMaxSize()
            .drawWithCache {
                val fond = Brush.radialGradient(
                    0f to Color(0xFFEDEEF0), 0.6f to Color(0xFFD6D7DB), 1f to Color(0xFFB9BBC0),
                    center = Offset(size.width * 0.5f, size.height * 0.1f), radius = size.width * 0.9f,
                )
                onDrawBehind { drawRect(fond) }
            }
            // Un clic à vide ne traverse pas vers l'accueil caché dessous
            .retenirClics()
            .testTag("galerie:grille")
            .onPreviewKeyEvent {
                if (it.key == Key.Back) {
                    if (it.type == KeyEventType.KeyUp) sortir(fermer)
                    true
                } else false
            }
            .onFocusChanged {
                if (it.hasFocus) aEuLeFocus = true
                else if (aEuLeFocus) sortir(fermer)
            }
            .focusProperties { exit = { FocusRequester.Cancel } }
            .focusGroup(),
    ) {
        Column(Modifier.fillMaxSize()) {
            Column(Modifier.padding(start = 96.mq, top = 56.mq, bottom = 22.mq)) {
                TexteGalerie(onglet.titre, 56f, GalerieEncre, poids = FontWeight.Normal, serif = true, interligne = 62f)
                TexteGalerie(
                    "${titres.size} titre${if (titres.size > 1) "s" else ""} · OK : l'afficher au centre · Retour : revenir",
                    17f, GalerieGris, Modifier.padding(top = 6.mq), interligne = 24f,
                )
            }
            LazyVerticalGrid(
                columns = GridCells.Fixed(colonnes),
                state = etat,
                modifier = Modifier.fillMaxWidth().weight(1f),
                contentPadding = PaddingValues(start = 96.mq, end = 96.mq, top = 16.mq, bottom = 48.mq),
                horizontalArrangement = Arrangement.spacedBy(32.mq),
                verticalArrangement = Arrangement.spacedBy(28.mq),
            ) {
                items(titres.size, key = { titres[it].cle }) { i ->
                    CaseGrille(
                        titres[i], affiches, ctx, k,
                        onClick = { sortir { choisir(i) } },
                        modifier = Modifier.testTag("grille:" + titres[i].cle).then(if (i == courant) Modifier.focusRequester(premier) else Modifier),
                    )
                }
            }
        }
    }
}

/** Une case de la grille : affiche (ou visuel) et titre dessous. */
@Composable
private fun CaseGrille(
    s: StudioSuggestion,
    affiche: Boolean,
    ctx: HomeContext,
    k: Float,
    onClick: () -> Unit,
    modifier: Modifier,
) {
    var focus by remember { mutableStateOf(false) }
    val t by animateFloatAsState(if (focus) 1f else 0f, tween(160), label = "anneau")
    val nom = when (s) {
        is StudioSuggestion.Programme -> s.programme.titre
        is StudioSuggestion.AppUne -> s.app.label
    }
    val sous = when (s) {
        is StudioSuggestion.Programme -> sousTitreProgramme(s.programme)
        is StudioSuggestion.AppUne -> s.phrase
    }
    val image = when (s) {
        is StudioSuggestion.Programme -> rememberImageProgramme(
            if (affiche) s.programme.affiche ?: s.programme.image else s.programme.image,
            (260f * k).toInt().coerceIn(160, 342),
        )
        is StudioSuggestion.AppUne -> rememberAppVisual(ctx.ui.iconSourceOf(s.app.packageName), ctx.icons, FormatVisuel.BANNIERE)?.art
    }
    Column {
        SurfaceCliquable(
            onClick = onClick,
            modifier = modifier
                .drawBehind {
                    OmbreGalerie.dessiner(this, 0f, 0f, size.width, size.height, 34f * k, 16f * k, Color(0xFF14161C).copy(alpha = 0.22f + 0.1f * t))
                    anneauGalerie(k, 18f * k, t, ombre = false)
                }
                .onFocusChanged { focus = it.hasFocus }
                .fillMaxWidth()
                .aspectRatio(if (affiche) 2f / 3f else 16f / 9f),
            shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(18.mq)),
            colors = ClickableSurfaceDefaults.colors(
                containerColor = Color(0xFF0B0B0E), contentColor = Color.White,
                focusedContainerColor = Color(0xFF0B0B0E), focusedContentColor = Color.White,
                pressedContainerColor = Color(0xFF0B0B0E), pressedContentColor = Color.White,
            ),
            scale = ClickableSurfaceDefaults.scale(focusedScale = 1.04f, pressedScale = 0.98f),
        ) {
            if (image != null) {
                Image(
                    image, contentDescription = nom, contentScale = ContentScale.Crop,
                    filterQuality = FilterQuality.Medium, modifier = Modifier.fillMaxSize(),
                )
            } else {
                Box(Modifier.fillMaxSize().background(Color(0xFF2B2D33)))
            }
        }
        TexteGalerie(nom, 17f, GalerieEncre, Modifier.padding(top = 12.mq, start = 4.mq, end = 4.mq), poids = FontWeight.Bold, interligne = 22f)
        TexteGalerie(sous, 14f, GalerieGris, Modifier.padding(top = 2.mq, start = 4.mq, end = 4.mq), interligne = 19f)
    }
}
