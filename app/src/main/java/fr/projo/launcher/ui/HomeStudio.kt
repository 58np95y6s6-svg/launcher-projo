package fr.projo.launcher.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import fr.projo.launcher.data.AppInfo
import fr.projo.launcher.data.FormatVisuel
import fr.projo.launcher.data.IconLoader
import fr.projo.launcher.modules.HoteModules
import fr.projo.launcher.modules.LocalHoteModules
import fr.projo.launcher.modules.TailleModule
import fr.projo.launcher.modules.ligne
import fr.projo.launcher.modules.u
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

// Mesures de la mise en page (unités .u de l'écran de référence 960 × 540)
private const val S_MARGE = 40
/** Barre des apps : hauteur, écart au bas de l'écran 1 et au haut de l'écran 2. */
private const val S_DOCK = 96
private const val S_DOCK_BAS = 22
private const val S_DOCK_HAUT = 24
private const val S_TUILE_L = 112
private const val S_TUILE_H = 63
/** Entrées et raccourcis : libellé + tuiles. */
private const val S_BANDE = 86
private const val S_ECART_BANDE = 14
private const val S_ECART_MODULES = 18
private const val S_BAS_MODULES = 20
/** Écart entre les deux panneaux de modules. */
private const val S_ECART_PANNEAUX = 12
/** Hauteur des panneaux de modules en taille MOYEN (la place intérieure, 134, reste sous GRAND). */
private const val S_PANNEAU_MOYEN = 150

/** Onglets des suggestions (affichés seulement avec de vraies suggestions). */
private enum class StudioOnglet(val titre: String) { EXPLORER("Explorer"), FILMS("Films"), SERIES("Séries") }

/**
 * Design « Studio » (référence « Free My TV ») : clair, matières de verre, sur deux écrans.
 *
 * Écran 1 : « Pour vous » (onglets si de vraies suggestions), pastilles et infos en haut à
 * droite ; rangée des suggestions (une grande carte 16:9 et de plus petites) ; en bas, la
 * barre des apps (favoris puis « Mes apps ») dans un dock de verre.
 * Écran 2 (▼ depuis la barre ; la page glisse) : la barre remonte en haut, puis « Entrées »
 * (sources HDMI, absentes sur Fire TV) et « Raccourcis » (Toutes les apps, Réglages, Wi-Fi,
 * Bluetooth), puis les modules choisis dans Réglages → Modules, en grands panneaux de verre.
 *
 * Focus initial (premierFocus) : 1re app de la barre, l'élément stable de l'accueil (les
 * suggestions changent avec le contenu et peuvent manquer) ; OK lance directement l'app.
 * Navigation : voir StudioNav (▼ / ▲ vers l'élément le plus aligné de la bande voisine).
 */
@Composable
fun HomeStudio(ctx: HomeContext) {
    val ui = ctx.ui
    val nav = remember { StudioNav() }
    val scope = rememberCoroutineScope()
    var reglagesBas by remember { mutableStateOf(false) }

    // Barre des apps : favoris (ou la carte d'aide), puis « Mes apps » ; les entrées HDMI vont dans « Entrées »
    val mesApps = ui.homeApps.filter { !it.isSpecial && ui.favorites.none { f -> f.packageName == it.packageName } }
    val entrees = ui.allApps.filter { it.isSpecial }

    // Suggestions : vraies (fournisseur TV) ou repli « apps à la une »
    val paquets = remember(ui.favorites, ui.homeApps) {
        (ui.favorites + ui.homeApps).filter { !it.isSpecial }.map { it.packageName }.distinct()
    }
    val etat = rememberStudioSuggestions(paquets)
    val programmes = etat.programmes
    val onglets = remember(programmes) {
        if (programmes.isEmpty()) emptyList()
        else listOfNotNull(
            StudioOnglet.EXPLORER,
            StudioOnglet.FILMS.takeIf { programmes.any { it.genre == StudioGenre.FILM } },
            StudioOnglet.SERIES.takeIf { programmes.any { it.genre == StudioGenre.SERIE } },
        )
    }
    var ongletChoisi by remember { mutableStateOf(StudioOnglet.EXPLORER) }
    val onglet = if (ongletChoisi in onglets) ongletChoisi else StudioOnglet.EXPLORER
    val suggestions: List<StudioSuggestion> = if (programmes.isNotEmpty()) {
        programmes
            .filter {
                when (onglet) {
                    StudioOnglet.EXPLORER -> true
                    StudioOnglet.FILMS -> it.genre == StudioGenre.FILM
                    StudioOnglet.SERIES -> it.genre == StudioGenre.SERIE
                }
            }
            .map { p -> StudioSuggestion.Programme(p, ui.allApps.firstOrNull { it.packageName == p.paquet }) }
    } else {
        appsALaUne(ui.favorites, ui.homeApps, etat.recente)
    }

    Box(Modifier.fillMaxSize().fondStudio()) {
        BoxWithConstraints(Modifier.fillMaxSize().clipToBounds()) {
            val unite = 1.u.value
            val hauteur = maxHeight.value / unite
            val hautEcran1 = hauteur - S_DOCK - S_DOCK_BAS
            // Défilement de l'écran 2 : la barre des apps arrive à S_DOCK_HAUT du haut
            val decalage = hautEcran1 - S_DOCK_HAUT
            val decalagePx = with(LocalDensity.current) { decalage.u.toPx() }.roundToInt()
            val versEcran: (StudioBande) -> Unit = { b ->
                val cible = when (b) {
                    StudioBande.HAUT, StudioBande.SUGGESTIONS -> 0
                    StudioBande.RACCOURCIS, StudioBande.MODULES -> decalagePx
                    StudioBande.DOCK -> null
                }
                if (cible != null && ctx.defilement.value != cible) {
                    scope.launch { ctx.defilement.animateScrollTo(cible, tween(480, easing = FastOutSlowInEasing)) }
                }
            }
            val hautModules = hauteur - S_DOCK_HAUT - S_DOCK - S_ECART_BANDE - S_BANDE - S_ECART_MODULES - S_BAS_MODULES

            Column(
                Modifier
                    .fillMaxWidth()
                    .wrapContentHeight(Alignment.Top, unbounded = true)
                    .height((hauteur + decalage).u)
                    // Page sur deux écrans, déplacée d'un bloc (pas de défilement automatique au focus)
                    .offset { IntOffset(0, -ctx.defilement.value) }
                    .testTag("studio:page"),
            ) {
                // ------------------------------------------------ écran 1
                Column(Modifier.fillMaxWidth().height(hautEcran1.u)) {
                    StudioEnTete(ctx, nav, onglets, onglet, { ongletChoisi = it }, reglagesBas, { reglagesBas = it }, versEcran)
                    StudioRangeeSuggestions(ctx, nav, suggestions, onglet, versEcran)
                }
                StudioDock(ctx, nav, mesApps, versEcran)
                // ------------------------------------------------ écran 2
                Spacer(Modifier.height(S_ECART_BANDE.u))
                StudioEntreesRaccourcis(ctx, nav, entrees, reglagesBas, { reglagesBas = it }, versEcran)
                Spacer(Modifier.height(S_ECART_MODULES.u))
                StudioModules(ctx, nav, hautModules, versEcran)
            }
        }
    }
}

// ------------------------------------------------------------------ en-tête

@Composable
private fun StudioEnTete(
    ctx: HomeContext,
    nav: StudioNav,
    onglets: List<StudioOnglet>,
    onglet: StudioOnglet,
    choisir: (StudioOnglet) -> Unit,
    reglagesBas: Boolean,
    setReglagesBas: (Boolean) -> Unit,
    versEcran: (StudioBande) -> Unit,
) {
    val a = ctx.actions
    Row(
        Modifier
            .fillMaxWidth()
            .padding(start = S_MARGE.u, end = S_MARGE.u, top = 28.u)
            .bandeStudio(nav, StudioBande.HAUT, versEcran),
        verticalAlignment = Alignment.Top,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                "Pour vous", color = StudioEncre, fontSize = 27.us, fontWeight = FontWeight.Bold,
                style = ligne(34.us),
            )
            if (onglets.isNotEmpty()) {
                Row(Modifier.padding(top = 6.u), horizontalArrangement = Arrangement.spacedBy(4.u)) {
                    for (o in onglets) {
                        StudioOngletBouton(
                            o.titre, o == onglet,
                            onFocus = { choisir(o); ctx.sansSelection() },
                            modifier = Modifier.testTag("onglet:" + o.name.lowercase())
                                .then(rememberCibleStudio(nav, StudioBande.HAUT, "onglet:" + o.name)),
                        )
                    }
                }
            }
        }
        Column(horizontalAlignment = Alignment.End) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.u), verticalAlignment = Alignment.CenterVertically) {
                if (ctx.majDisponible) {
                    StudioPastille(
                        ProjoIcons.MiseAJour, "Mise à jour", a.update, miseEnAvant = true,
                        modifier = rememberCibleStudio(nav, StudioBande.HAUT, "maj").onFocusChanged { if (it.hasFocus) ctx.sansSelection() },
                    )
                }
                StudioPastille(
                    ProjoIcons.Wifi, ctx.wifiLabel, a.wifi,
                    modifier = rememberCibleStudio(nav, StudioBande.HAUT, "wifi").onFocusChanged { if (it.hasFocus) ctx.sansSelection() },
                )
                StudioPastille(
                    ProjoIcons.Sources, "Sources", a.sources, onLong = a.sourcesLong,
                    modifier = rememberCibleStudio(nav, StudioBande.HAUT, "sources").onFocusChanged { if (it.hasFocus) ctx.sansSelection() },
                )
                StudioPastille(
                    ProjoIcons.Reglages, "Réglages",
                    onClick = { setReglagesBas(false); a.settings() },
                    onLong = { setReglagesBas(false); a.settingsLong() },
                    modifier = rememberCibleStudio(
                        nav, StudioBande.HAUT, "reglages", fr = if (!reglagesBas) ctx.focusReglages else null,
                    ).onFocusChanged { if (it.hasFocus) ctx.sansSelection() },
                )
            }
            val t = rememberMaintenant()
            Text(
                "${heureTexte(t)} · ${dateTexte(t).replaceFirstChar { it.uppercase() }}",
                color = StudioGrisClair, fontSize = 10.us, fontWeight = FontWeight.Medium,
                style = ligne(13.us), modifier = Modifier.padding(top = 9.u, end = 2.u),
            )
        }
    }
}

/** Onglet Explorer / Films / Séries : le choisir le sélectionne (filtre immédiat). */
@Composable
private fun StudioOngletBouton(titre: String, actif: Boolean, onFocus: () -> Unit, modifier: Modifier) {
    var focus by remember { mutableStateOf(false) }
    Surface(
        onClick = onFocus,
        modifier = modifier.height(24.u).onFocusChanged {
            focus = it.hasFocus
            if (it.hasFocus) onFocus()
        },
        shape = ClickableSurfaceDefaults.shape(CircleShape),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color.Transparent, contentColor = if (actif) StudioEncre else StudioGris,
            focusedContainerColor = StudioEncre, focusedContentColor = Color.White,
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.05f),
    ) {
        Box(Modifier.fillMaxHeight().padding(horizontal = 10.u), contentAlignment = Alignment.Center) {
            Text(
                titre, fontSize = 12.us, fontWeight = if (actif || focus) FontWeight.SemiBold else FontWeight.Medium,
                style = ligne(15.us),
            )
        }
    }
}

// ------------------------------------------------------------------ suggestions

private const val S_GRANDE_L = 400
private const val S_GRANDE_H = 225
private const val S_PETITE_L = 220
private const val S_PETITE_H = 124

@Composable
private fun StudioRangeeSuggestions(
    ctx: HomeContext,
    nav: StudioNav,
    suggestions: List<StudioSuggestion>,
    onglet: StudioOnglet,
    versEcran: (StudioBande) -> Unit,
) {
    val etat = rememberLazyListState()
    LaunchedEffect(onglet) { etat.scrollToItem(0) }
    val context = LocalContext.current
    LazyRow(
        state = etat,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 12.u)
            .testTag("rangee:suggestions")
            .bandeStudio(nav, StudioBande.SUGGESTIONS, versEcran),
        contentPadding = PaddingValues(horizontal = S_MARGE.u, vertical = 12.u),
        horizontalArrangement = Arrangement.spacedBy(18.u),
        verticalAlignment = Alignment.Bottom,
    ) {
        items(suggestions, key = { it.cle }) { s ->
            val grande = s === suggestions.first()
            val l = if (grande) S_GRANDE_L else S_PETITE_L
            val h = if (grande) S_GRANDE_H else S_PETITE_H
            val cible = rememberCibleStudio(nav, StudioBande.SUGGESTIONS, s.cle)
            when (s) {
                is StudioSuggestion.AppUne -> StudioCarteSuggestion(
                    titre = s.app.label, sousTitre = s.phrase, largeur = l.u, hauteur = h.u, grande = grande,
                    onClick = { ctx.actions.launch(s.app) }, onMenu = { ctx.actions.openMenu(s.app) },
                    onFocus = { c -> ctx.surFocus(s.app, c ?: IconLoader.COULEUR_PAR_DEFAUT) },
                    modifier = Modifier.testTag("suggestion:" + s.app.packageName).then(cible),
                    app = s.app, icons = ctx.icons, iconPackage = ctx.ui.iconSourceOf(s.app.packageName),
                )
                is StudioSuggestion.Programme -> {
                    val p = s.programme
                    val app = s.app
                    StudioCarteSuggestion(
                        titre = p.titre,
                        sousTitre = listOfNotNull(
                            p.detail,
                            when (p.genre) { StudioGenre.FILM -> "Film"; StudioGenre.SERIE -> "Série"; else -> null },
                            app?.label,
                        ).joinToString(" · "),
                        largeur = l.u, hauteur = h.u, grande = grande,
                        onClick = { ouvrirProgramme(context, p) { app?.let(ctx.actions.launch) } },
                        onMenu = app?.let { { ctx.actions.openMenu(it) } },
                        onFocus = { c -> if (app != null) ctx.surFocus(app, c ?: IconLoader.COULEUR_PAR_DEFAUT) else ctx.sansSelection() },
                        modifier = Modifier.testTag("suggestion:" + p.id).then(cible),
                        app = app, icons = ctx.icons, iconPackage = app?.let { ctx.ui.iconSourceOf(it.packageName) },
                        imageProgramme = p.image, largeurImage = if (grande) 640 else 360,
                    )
                }
            }
        }
    }
}

/**
 * Carte de suggestion : image 16:9 (illustration de l'app, ou image du programme sur
 * l'illustration de son app tant qu'elle charge), titre et sous-titre dessous.
 */
@Composable
private fun StudioCarteSuggestion(
    titre: String,
    sousTitre: String,
    largeur: Dp,
    hauteur: Dp,
    grande: Boolean,
    onClick: () -> Unit,
    onMenu: (() -> Unit)?,
    onFocus: (Color?) -> Unit,
    modifier: Modifier,
    app: AppInfo?,
    icons: IconLoader,
    iconPackage: String?,
    imageProgramme: String? = null,
    largeurImage: Int = 0,
) {
    val visuel = iconPackage?.let { rememberAppVisual(it, icons, FormatVisuel.BANNIERE) }
    val image = if (imageProgramme != null) rememberImageProgramme(imageProgramme, largeurImage) else null
    Column(Modifier.width(largeur)) {
        StudioCadre(
            onClick = onClick, onMenu = onMenu, largeur = largeur, hauteur = hauteur,
            rayon = if (grande) 14.u else 11.u, modifier = modifier, levee = if (grande) 1.04f else 1.07f,
            fond = visuel?.fond ?: Color(0xFFDADDE3), ombreRepos = 0.8f,
            onFocus = { if (it) onFocus(visuel?.color) },
        ) {
            when {
                image != null -> Image(
                    image, contentDescription = titre, contentScale = ContentScale.Crop,
                    filterQuality = FilterQuality.Medium, modifier = Modifier.fillMaxSize(),
                )
                visuel?.art != null -> ImageApp(visuel, app?.label, Modifier.fillMaxSize())
                else -> InitialeStudio(app?.label ?: titre, visuel?.color ?: IconLoader.COULEUR_PAR_DEFAUT, if (grande) 40 else 24)
            }
        }
        Text(
            titre, color = StudioEncre, fontSize = (if (grande) 13 else 12).us, fontWeight = FontWeight.SemiBold,
            maxLines = 1, overflow = TextOverflow.Ellipsis, style = ligne(16.us),
            modifier = Modifier.padding(top = 9.u, start = 4.u, end = 4.u),
        )
        Text(
            sousTitre, color = StudioGris, fontSize = 10.us, fontWeight = FontWeight.Medium,
            maxLines = 1, overflow = TextOverflow.Ellipsis, style = ligne(13.us),
            modifier = Modifier.padding(top = 2.u, start = 4.u, end = 4.u),
        )
    }
}

/** Visuel de repli d'une app sans image : dégradé de sa couleur et son nom. */
@Composable
private fun InitialeStudio(nom: String, couleur: Color, taille: Int) {
    Box(
        Modifier.fillMaxSize().background(
            Brush.linearGradient(listOf(lerp(couleur, Color.White, 0.15f), lerp(couleur, Color.Black, 0.35f)))
        ),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            nom, color = Color.White, fontSize = (taille * 0.45f).roundToInt().us, fontWeight = FontWeight.SemiBold,
            maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(horizontal = 8.u),
        )
    }
}

// ------------------------------------------------------------------ barre des apps

@Composable
private fun StudioDock(ctx: HomeContext, nav: StudioNav, mesApps: List<AppInfo>, versEcran: (StudioBande) -> Unit) {
    val ui = ctx.ui
    Box(
        Modifier
            .fillMaxWidth()
            .height(S_DOCK.u)
            .padding(horizontal = 30.u)
            .verreStudio(26.u, opacite = 1.05f),
        contentAlignment = Alignment.Center,
    ) {
        LazyRow(
            state = ctx.rangeeFavoris,
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(26.u))
                .testTag("rangee:favoris")
                .bandeStudio(nav, StudioBande.DOCK, versEcran),
            contentPadding = PaddingValues(horizontal = 18.u, vertical = 12.u),
            horizontalArrangement = Arrangement.spacedBy(13.u),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (ui.loaded && ui.favorites.isEmpty()) {
                item(key = "aide") {
                    StudioCadre(
                        onClick = {}, largeur = (S_TUILE_L * 2).u, hauteur = S_TUILE_H.u, rayon = 11.u,
                        modifier = Modifier.testTag("aide")
                            .then(rememberCibleStudio(nav, StudioBande.DOCK, "aide", fr = ctx.premierFocus)),
                        fond = Color.White.copy(alpha = 0.6f), fondFocus = Color.White,
                        onFocus = { if (it) ctx.sansSelection() },
                    ) {
                        Text(
                            "Aucun favori. Maintiens OK sur une app pour l'ajouter.",
                            color = StudioGris, fontSize = 11.us, style = ligne(14.us),
                            modifier = Modifier.padding(horizontal = 12.u),
                        )
                    }
                }
            }
            items(ui.favorites.size, key = { "fav:" + ui.favorites[it].packageName }) { i ->
                val app = ui.favorites[i]
                StudioTuileApp(
                    ctx, app,
                    Modifier.testTag("favori:" + app.packageName).then(
                        rememberCibleStudio(nav, StudioBande.DOCK, "fav:" + app.packageName, fr = if (i == 0) ctx.premierFocus else null)
                    ),
                )
            }
            if (ui.favorites.isNotEmpty() && mesApps.isNotEmpty()) {
                item(key = "separateur") {
                    Box(Modifier.width(1.u).height(36.u).background(StudioEncre.copy(alpha = 0.12f)))
                }
            }
            items(mesApps, key = { "app:" + it.packageName }) { app ->
                StudioTuileApp(
                    ctx, app,
                    Modifier.testTag("app:" + app.packageName).then(rememberCibleStudio(nav, StudioBande.DOCK, "app:" + app.packageName)),
                )
            }
        }
    }
}

/** Tuile 16:9 d'une app dans la barre : son illustration, aux couleurs de la marque. */
@Composable
private fun StudioTuileApp(ctx: HomeContext, app: AppInfo, modifier: Modifier) {
    val visuel = rememberAppVisual(ctx.ui.iconSourceOf(app.packageName), ctx.icons, FormatVisuel.BANNIERE)
    StudioCadre(
        onClick = { ctx.actions.launch(app) }, onMenu = { ctx.actions.openMenu(app) },
        largeur = S_TUILE_L.u, hauteur = S_TUILE_H.u, rayon = 11.u, modifier = modifier, levee = 1.12f,
        fond = visuel?.fond ?: Color(0xFFDADDE3), ombreRepos = 0.6f,
        onFocus = { if (it) ctx.surFocus(app, visuel?.color ?: IconLoader.COULEUR_PAR_DEFAUT) },
    ) {
        if (visuel?.art != null) ImageApp(visuel, app.label, Modifier.fillMaxSize())
        else InitialeStudio(app.label, visuel?.color ?: IconLoader.COULEUR_PAR_DEFAUT, 24)
    }
}

// ------------------------------------------------------------------ entrées et raccourcis

@Composable
private fun StudioEntreesRaccourcis(
    ctx: HomeContext,
    nav: StudioNav,
    entrees: List<AppInfo>,
    reglagesBas: Boolean,
    setReglagesBas: (Boolean) -> Unit,
    versEcran: (StudioBande) -> Unit,
) {
    val a = ctx.actions
    val demi = (960 - 2 * S_MARGE - S_ECART_PANNEAUX) / 2 + S_ECART_PANNEAUX
    Row(
        Modifier
            .fillMaxWidth()
            .height(S_BANDE.u)
            .padding(horizontal = S_MARGE.u)
            .testTag("studio:raccourcis")
            .bandeStudio(nav, StudioBande.RACCOURCIS, versEcran),
    ) {
        // Entrées : sources HDMI (aucune sur Fire TV : la section disparaît)
        if (entrees.isNotEmpty()) {
            Column(Modifier.width(demi.u)) {
                TitreSection("Entrées")
                Row(horizontalArrangement = Arrangement.spacedBy(8.u)) {
                    for (e in entrees) {
                        StudioRaccourci(
                            ProjoIcons.Sources, e.label, onClick = { a.launch(e) }, onMenu = { a.openMenu(e) },
                            modifier = Modifier.testTag("app:" + e.packageName)
                                .then(rememberCibleStudio(nav, StudioBande.RACCOURCIS, "entree:" + e.packageName)),
                            onFocus = { if (it) ctx.surFocus(e, IconLoader.COULEUR_HDMI) },
                        )
                    }
                }
            }
        }
        Column {
            TitreSection("Raccourcis")
            Row(horizontalArrangement = Arrangement.spacedBy(8.u)) {
                StudioRaccourci(
                    ProjoIcons.Apps, "Toutes les apps", a.openLibrary,
                    modifier = Modifier.testTag("biblio")
                        .then(rememberCibleStudio(nav, StudioBande.RACCOURCIS, "biblio", fr = ctx.focusBiblio)),
                    onFocus = { if (it) ctx.sansSelection() },
                )
                StudioRaccourci(
                    ProjoIcons.Reglages, "Réglages", { setReglagesBas(true); a.settings() },
                    onMenu = { setReglagesBas(true); a.settingsLong() },
                    modifier = Modifier.testTag("raccourci:Réglages").then(
                        rememberCibleStudio(nav, StudioBande.RACCOURCIS, "reglages", fr = if (reglagesBas) ctx.focusReglages else null)
                    ),
                    onFocus = { if (it) ctx.sansSelection() },
                )
                StudioRaccourci(
                    ProjoIcons.Wifi, "Wi-Fi", a.wifi,
                    modifier = Modifier.testTag("raccourci:Wi-Fi").then(rememberCibleStudio(nav, StudioBande.RACCOURCIS, "wifi")),
                    onFocus = { if (it) ctx.sansSelection() },
                )
                StudioRaccourci(
                    ProjoIcons.Bluetooth, "Bluetooth", a.bluetooth,
                    modifier = Modifier.testTag("raccourci:Bluetooth").then(rememberCibleStudio(nav, StudioBande.RACCOURCIS, "bluetooth")),
                    onFocus = { if (it) ctx.sansSelection() },
                )
            }
        }
    }
}

@Composable
private fun TitreSection(titre: String) {
    Text(
        titre, color = StudioGris, fontSize = 11.us, fontWeight = FontWeight.SemiBold, style = ligne(14.us),
        modifier = Modifier.padding(start = 2.u, bottom = 8.u),
    )
}

// ------------------------------------------------------------------ modules

/**
 * Modules choisis dans Réglages → Modules, chacun dans son grand panneau de verre : deux
 * côte à côte, ou un seul sur toute la largeur. Taille GRAND si un module la propose (la
 * place est là), sinon MOYEN (panneaux moins hauts).
 */
@Composable
private fun StudioModules(ctx: HomeContext, nav: StudioNav, hautDispo: Float, versEcran: (StudioBande) -> Unit) {
    val hote = LocalHoteModules.current
    val modules = hote?.modules
    val grand = modules == null || modules.any { TailleModule.GRAND in it.tailles }
    val hauteur = (if (grand) hautDispo else minOf(hautDispo, S_PANNEAU_MOYEN.toFloat())).roundToInt()
    val n = if (hote != null && modules != null && modules.size > 1) 2 else 1
    val pleine = 960 - 2 * S_MARGE
    val largeur = if (n == 2) (pleine - S_ECART_PANNEAUX) / 2 else pleine
    val entrees = remember { listOf(FocusRequester(), FocusRequester()) }
    Row(
        Modifier
            .padding(horizontal = S_MARGE.u)
            .height(hauteur.u)
            .bandeModulesStudio(nav, versEcran),
        horizontalArrangement = Arrangement.spacedBy(S_ECART_PANNEAUX.u),
    ) {
        for (i in 0 until n) {
            key(i, modules?.getOrNull(i)?.id) {
                val hoteZone = remember(hote, i, n) {
                    if (hote == null || n == 1) hote
                    else HoteModules(listOf(hote.modules[i].id), hote.reglages, hote.donnees, hote.ecrireReglage)
                }
                Box(
                    Modifier
                        .width(largeur.u)
                        .fillMaxHeight()
                        .verreStudio(22.u, opacite = 1.1f)
                        .then(rememberCibleStudio(nav, StudioBande.MODULES, "zone:$i", fr = entrees[i], attacher = false))
                        .onFocusChanged { if (it.hasFocus) ctx.sansSelection() },
                    contentAlignment = Alignment.Center,
                ) {
                    CompositionLocalProvider(LocalHoteModules provides hoteZone) {
                        NbaPanel(
                            data = ctx.nbaData,
                            erreur = ctx.nbaErreur,
                            repo = ctx.nbaRepo,
                            onExit = ctx.sortieNba,
                            style = styleModulesStudio(largeur, hauteur),
                            entree = entrees[i],
                            onGauche = if (i == 1) ({ runCatching { entrees[0].requestFocus() }; Unit }) else null,
                        )
                    }
                }
            }
        }
    }
}
