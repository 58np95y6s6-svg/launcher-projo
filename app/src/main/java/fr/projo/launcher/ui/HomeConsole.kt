package fr.projo.launcher.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.zIndex
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import fr.projo.launcher.data.AppCategories
import fr.projo.launcher.data.AppInfo
import fr.projo.launcher.data.AppVisual
import fr.projo.launcher.data.FormatVisuel
import fr.projo.launcher.data.IconLoader
import java.util.Locale
import kotlinx.coroutines.delay

// ------------------------------------------------------------------ palette « Console »
// (couleurs déclarées en premier : les valeurs plus bas s'en servent à l'initialisation)

/** Bleu nuit profond du fond. */
private val ConsoleNavy = Color(0xFF050817)
/** Voiles de lisibilité (gauche, haut, bas). */
private val ConsoleVoile = Color(0xFF040716)
/** Encre des textes, puis ses variantes secondaire et tertiaire. */
private val ConsoleEncre = Color(0xFFF4F6FB)
private val ConsoleEncre2 = ConsoleEncre.copy(alpha = 0.72f)
private val ConsoleEncre3 = ConsoleEncre.copy(alpha = 0.55f)
/** Bleu clair (bouton « Mise à jour »). */
private val ConsoleAccent = Color(0xFF8FB4FF)

/** Halo blanc autour de l'élément sélectionné : opacité de chaque contour, de l'intérieur vers l'extérieur. */
private val ConsoleHalo = floatArrayOf(0.22f, 0.13f, 0.07f, 0.03f)

/** Reflet en diagonale sur les tuiles (le dégradé s'adapte à la taille : un seul objet). */
private val ConsoleReflet = Brush.linearGradient(
    0f to Color.White.copy(alpha = 0.16f),
    0.4f to Color.White.copy(alpha = 0f),
)

/** Tuile « Source HDMI » : dégradé de sa couleur fixe. */
private val ConsoleDegradeHdmi = Brush.linearGradient(
    listOf(lerp(IconLoader.COULEUR_HDMI, Color.White, 0.1f), lerp(IconLoader.COULEUR_HDMI, Color.Black, 0.55f)),
)

/** Verre sombre du bandeau NBA (laisse deviner les rubans de lumière derrière). */
private val ConsoleVerreNba = Brush.verticalGradient(
    0f to Color(0xFF0C1028).copy(alpha = 0.62f),
    1f to Color(0xFF080B1C).copy(alpha = 0.78f),
)

private val ConsoleNba = NbaStyle(
    largeur = 340,
    rayon = 12,
    // Le verre (dégradé) est dessiné derrière le bandeau
    fond = Color.Transparent,
    bordure = Color.White.copy(alpha = 0.12f),
    texte = ConsoleEncre,
    texteDiscret = ConsoleEncre.copy(alpha = 0.62f),
    ongletActif = Color.White.copy(alpha = 0.14f),
    focusFond = ConsoleEncre,
    focusTexte = ConsoleNavy,
    pastilleFond = Color.White.copy(alpha = 0.92f),
    echelle = 1.1f,
)

/** Chiffres à chasse fixe (l'heure ne « danse » pas d'une minute à l'autre). */
private val ConsoleChiffres = TextStyle(fontFeatureSettings = "tnum")

// Dimensions (unités .u, écran de 960 × 540 ; maquette 1920 px → divisée par 2)
private const val CONSOLE_MARGE = 48
/** Tuiles carrées de la bande du haut, et taille de la tuile sélectionnée. */
private const val CONSOLE_TUILE = 72
private const val CONSOLE_TUILE_GRANDE = 110
/** Coins des tuiles : 9 % du côté (même arrondi relatif, petite ou grande). */
private const val CONSOLE_RAYON_PCT = 9
private val ConsoleFormeTuile = RoundedCornerShape(percent = CONSOLE_RAYON_PCT)
/** Cartes « Mes apps ». */
private const val CONSOLE_CARTE_L = 100
private const val CONSOLE_CARTE_H = 66
/** Assez large pour « Toutes les apps (123) » en entier (le nombre ne doit pas être coupé). */
private const val CONSOLE_BIBLIO_L = 160
private const val CONSOLE_BOUTON = 34

/**
 * Design « Console » (accueil de console de salon) :
 *  - fond « cinéma » généré à partir de la couleur de l'app sélectionnée : bleu nuit,
 *    grande lumière douce et rubans de lumière à droite, voiles sombres à gauche et en bas ;
 *  - fine barre du haut : salutation et date à gauche, boutons ronds et heure à droite ;
 *  - bande de tuiles carrées (favoris) : la tuile sélectionnée grandit, anneau blanc,
 *    son nom et sa rubrique s'affichent à côté ;
 *  - au centre, le nom de l'app en très grand et très fin, et les scores NBA dans une
 *    carte de verre à droite ;
 *  - en bas, « Mes apps » en cartes larges (icône + nom), puis « Toutes les apps (N) ».
 */
@Composable
fun HomeConsole(ctx: HomeContext) {
    val ui = ctx.ui
    // ▼ / ▲ entre les rangées : commun aux designs (NavRangees.kt). La carte NBA, entre les
    // deux rangées à droite, s'atteint par ▶ au bout d'une des deux rangées (dernière tuile
    // des favoris ou « Toutes les apps »).
    val nav = ctx.nav
    val focusNba = remember { FocusRequester() }
    val anneau = rememberConsoleAnneau()
    // « Toutes les apps » sélectionnée : le grand titre la présente
    var biblioFocus by remember { mutableStateOf(false) }

    val selection = ctx.selection
    val rubrique = remember(selection) { selection?.let { AppCategories.rubrique(it).titre } }
    val heros = when {
        selection != null -> ConsoleHerosInfo(
            cle = "app:" + selection.packageName,
            titre = selection.label,
            surtitre = rubrique.orEmpty(),
            favori = ui.favorites.any { it.packageName == selection.packageName },
            action = "Ouvrir",
            options = true,
        )
        biblioFocus -> ConsoleHerosInfo(
            cle = "bibliotheque",
            titre = "Toutes les apps",
            surtitre = "Bibliothèque · " + consoleNombreApps(ui.allApps.size),
            favori = false,
            action = "Ouvrir la bibliothèque",
            options = false,
        )
        else -> null
    }

    Box(Modifier.fillMaxSize()) {
        ConsoleFond(ctx.couleur)

        Column(Modifier.fillMaxSize()) {
            ConsoleBarre(ctx)
            Spacer(Modifier.height(4.u))
            ConsoleBande(ctx, nav, anneau, focusNba)

            // --- Héros : grand titre à gauche, carte NBA en verre à droite
            Row(
                Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(horizontal = CONSOLE_MARGE.u),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ConsoleHeros(heros, Modifier.weight(1f).padding(end = 24.u))
                Box(
                    Modifier
                        .focusRequester(focusNba)
                        .background(ConsoleVerreNba, RoundedCornerShape(ConsoleNba.rayon.u * ConsoleNba.echelle))
                ) {
                    NbaPanel(
                        data = ctx.nbaData,
                        erreur = ctx.nbaErreur,
                        repo = ctx.nbaRepo,
                        onExit = ctx.sortieNba,
                        style = ConsoleNba,
                    )
                }
            }

            ConsoleMesApps(ctx, nav, anneau, focusNba, surBiblio = { biblioFocus = it })
            Spacer(Modifier.height(22.u))
        }
    }
}

// ------------------------------------------------------------------ fond « cinéma »

/** Ruban de lumière : courbe de Bézier (x0,y0 … x3,y3 en fractions de l'écran), largeur (fraction de la largeur). */
private class ConsoleRuban(val points: FloatArray, val largeur: Float, val intensite: Float)

/**
 * Rubans : tous dans la moitié droite, sous la bande des favoris (y ≥ 37 %) et estompés
 * avant « Mes apps » : ils passent derrière la carte NBA sans traverser tuiles ni boutons.
 */
private val ConsoleRubans = listOf(
    ConsoleRuban(floatArrayOf(1.10f, 0.40f, 0.82f, 0.37f, 0.64f, 0.56f, 0.50f, 0.98f), 0.17f, 1f),
    ConsoleRuban(floatArrayOf(1.08f, 0.56f, 0.88f, 0.47f, 0.74f, 0.66f, 0.66f, 1.00f), 0.12f, 0.8f),
    ConsoleRuban(floatArrayOf(1.06f, 0.46f, 0.80f, 0.43f, 0.60f, 0.62f, 0.46f, 0.90f), 0.04f, 0.9f),
)
/** Halo d'un ruban : contours superposés de plus en plus étroits et lumineux (bords doux, sans flou). */
private val ConsoleRubanLargeurs = floatArrayOf(1f, 0.70f, 0.45f, 0.26f, 0.12f)
private val ConsoleRubanAlphas = floatArrayOf(0.07f, 0.08f, 0.11f, 0.15f, 0.22f)

/** Ruban prêt à dessiner pour une taille d'écran donnée (chemin, pinceau et traits créés une fois). */
private class ConsoleRubanPret(
    private val chemin: Path,
    private val pinceau: Brush,
    private val halos: List<Stroke>,
    private val coeur: Stroke,
    private val eclat: Stroke,
    private val intensite: Float,
) {
    fun dessiner(d: DrawScope, teinte: ColorFilter) {
        // Halo et cœur teintés de la couleur de l'app, fin éclat blanc au centre
        for (i in halos.indices) {
            d.drawPath(chemin, pinceau, alpha = ConsoleRubanAlphas[i] * intensite, style = halos[i], colorFilter = teinte)
        }
        d.drawPath(chemin, pinceau, alpha = 0.9f * intensite, style = coeur, colorFilter = teinte)
        d.drawPath(chemin, pinceau, alpha = 0.4f * intensite, style = eclat)
    }
}

private fun ConsoleRuban.preparer(w: Float, h: Float): ConsoleRubanPret {
    val p = points
    val chemin = Path().apply {
        moveTo(p[0] * w, p[1] * h)
        cubicTo(p[2] * w, p[3] * h, p[4] * w, p[5] * h, p[6] * w, p[7] * h)
    }
    // Blanc (teinté au dessin) qui s'allume puis s'éteint le long du ruban
    val pinceau = Brush.linearGradient(
        0f to Color.White.copy(alpha = 0f),
        0.30f to Color.White,
        0.62f to Color.White.copy(alpha = 0.85f),
        1f to Color.White.copy(alpha = 0f),
        start = Offset(p[0] * w, p[1] * h),
        end = Offset(p[6] * w, p[7] * h),
    )
    val l = largeur * w
    return ConsoleRubanPret(
        chemin = chemin,
        pinceau = pinceau,
        halos = ConsoleRubanLargeurs.map { Stroke(width = l * it) },
        coeur = Stroke(width = maxOf(l * 0.035f, 1.5f), cap = StrokeCap.Round),
        eclat = Stroke(width = maxOf(l * 0.010f, 1f), cap = StrokeCap.Round),
        intensite = intensite,
    )
}

/** Filtre de teinte gardé tant que la couleur ne change pas (recréé seulement pendant le fondu). */
private class ConsoleFiltre {
    private var couleur = Color.Unspecified
    private var filtre: ColorFilter? = null

    fun pour(c: Color): ColorFilter {
        val actuel = filtre
        if (actuel != null && c == couleur) return actuel
        return ColorFilter.tint(c).also { filtre = it; couleur = c }
    }
}

/** Couleur trop sombre (logo noir…) : éclaircie en gardant sa teinte, pour que la lumière se voie. */
private fun consoleRaviver(c: Color): Color {
    val m = maxOf(c.red, c.green, c.blue)
    return when {
        m < 0.02f -> IconLoader.COULEUR_PAR_DEFAUT
        m >= 0.62f -> c.copy(alpha = 1f)
        else -> Color(c.red * 0.62f / m, c.green * 0.62f / m, c.blue * 0.62f / m)
    }
}

/**
 * Fond plein écran : bleu nuit, grande lumière douce et rubans de la couleur de l'app,
 * puis voiles de lisibilité. Formes et dégradés sont créés une fois par taille (en blanc)
 * et teintés au dessin : un changement de couleur ne recrée qu'un filtre. Le tout est
 * rendu dans un calque gardé en mémoire : rien n'est redessiné quand le focus bouge,
 * seulement pendant le fondu de couleur (450 ms).
 */
@Composable
private fun ConsoleFond(couleur: Color) {
    val cible = remember(couleur) { consoleRaviver(couleur) }
    val teinte by animateColorAsState(cible, tween(450), label = "teinteConsole")
    val filtre = remember { ConsoleFiltre() }
    Spacer(
        Modifier
            .fillMaxSize()
            .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
            .drawWithCache {
                val w = size.width
                val h = size.height
                val rubans = ConsoleRubans.map { it.preparer(w, h) }
                // Grande lumière en haut à droite, et reflet plus bas derrière les rubans
                val lumiere = Brush.radialGradient(
                    0f to Color.White.copy(alpha = 0.62f),
                    0.35f to Color.White.copy(alpha = 0.30f),
                    0.7f to Color.White.copy(alpha = 0.08f),
                    1f to Color.White.copy(alpha = 0f),
                    center = Offset(0.76f * w, 0.30f * h),
                    radius = 0.62f * w,
                )
                val reflet = Brush.radialGradient(
                    0f to Color.White.copy(alpha = 0.30f),
                    1f to Color.White.copy(alpha = 0f),
                    center = Offset(0.70f * w, 0.62f * h),
                    radius = 0.40f * w,
                )
                // Voiles bleu nuit : texte du héros à gauche, barre du haut, « Mes apps » en bas
                val voileGauche = Brush.horizontalGradient(
                    0f to ConsoleVoile.copy(alpha = 0.92f),
                    0.26f to ConsoleVoile.copy(alpha = 0.74f),
                    0.48f to ConsoleVoile.copy(alpha = 0.30f),
                    0.64f to ConsoleVoile.copy(alpha = 0f),
                    startX = 0f,
                    endX = w,
                )
                val voileHaut = Brush.verticalGradient(
                    0f to ConsoleVoile.copy(alpha = 0.55f),
                    0.20f to ConsoleVoile.copy(alpha = 0.10f),
                    0.32f to ConsoleVoile.copy(alpha = 0f),
                    startY = 0f,
                    endY = h,
                )
                val centreCoin = Offset(0.80f * w, 0.09f * h)
                val rayonCoin = 0.30f * w
                val voileCoin = Brush.radialGradient(
                    0f to ConsoleVoile.copy(alpha = 0.55f),
                    1f to ConsoleVoile.copy(alpha = 0f),
                    center = centreCoin,
                    radius = rayonCoin,
                )
                val voileBas = Brush.verticalGradient(
                    0.50f to ConsoleVoile.copy(alpha = 0f),
                    0.66f to ConsoleVoile.copy(alpha = 0.50f),
                    0.80f to ConsoleVoile.copy(alpha = 0.95f),
                    1f to ConsoleVoile,
                    startY = 0f,
                    endY = h,
                )
                onDrawBehind {
                    val t = filtre.pour(teinte)
                    drawRect(ConsoleNavy)
                    drawRect(lumiere, colorFilter = t)
                    drawRect(reflet, colorFilter = t)
                    for (r in rubans) r.dessiner(this, t)
                    drawRect(voileGauche)
                    drawRect(voileHaut)
                    // Ombre ovale derrière les boutons et l'heure
                    scale(1f, 0.32f, pivot = centreCoin) {
                        drawCircle(voileCoin, radius = rayonCoin, center = centreCoin)
                    }
                    drawRect(voileBas)
                }
            },
    )
}

// ------------------------------------------------------------------ barre du haut

/** Salutation et date à gauche ; Mise à jour, boutons rapides, séparateur et heure à droite. */
@Composable
private fun ConsoleBarre(ctx: HomeContext) {
    val t = rememberMaintenant()
    Row(
        Modifier
            .fillMaxWidth()
            .padding(start = CONSOLE_MARGE.u, end = CONSOLE_MARGE.u, top = 30.u)
            .height(CONSOLE_BOUTON.u),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Mesuré en dernier : la date se raccourcit plutôt que de pousser l'heure hors de l'écran
        Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
            Text(
                salutation(t), color = ConsoleEncre, fontSize = 17.us, fontWeight = FontWeight.SemiBold,
                maxLines = 1,
            )
            Text(
                dateTexte(t).replaceFirstChar { it.titlecase(Locale.FRENCH) },
                color = ConsoleEncre2, fontSize = 16.us, fontWeight = FontWeight.Light,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(start = 16.u),
            )
        }
        if (ctx.majDisponible) {
            ConsoleBoutonMaj(ctx.actions.update)
            Spacer(Modifier.width(10.u))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.u), verticalAlignment = Alignment.CenterVertically) {
            for (a in ctx.quickActions()) {
                ConsoleBouton(
                    icone = a.icon,
                    libelle = a.label,
                    onClick = a.onClick,
                    onLongClick = a.onLongClick,
                    modifier = if (a.estReglages) Modifier.focusRequester(ctx.focusReglages) else Modifier,
                )
            }
        }
        Box(
            Modifier
                .padding(horizontal = 16.u)
                .size(1.u, 20.u)
                .background(Color.White.copy(alpha = 0.22f))
        )
        Text(
            heureTexte(t), color = ConsoleEncre, fontSize = 26.us, fontWeight = FontWeight.Light,
            maxLines = 1, style = ConsoleChiffres,
        )
    }
}

/** Bouton rond (icône pleine) ; sélectionné : disque clair, son nom apparaît en glissant. */
@Composable
private fun ConsoleBouton(
    icone: ImageVector,
    libelle: String,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    var focus by remember { mutableStateOf(false) }
    Surface(
        onClick = onClick,
        onLongClick = onLongClick,
        modifier = modifier
            .testTag("rapide:$libelle")
            .height(CONSOLE_BOUTON.u)
            .onFocusChanged { focus = it.isFocused },
        shape = ClickableSurfaceDefaults.shape(CircleShape),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color.White.copy(alpha = 0.10f),
            contentColor = ConsoleEncre,
            focusedContainerColor = ConsoleEncre,
            focusedContentColor = ConsoleNavy,
            pressedContainerColor = ConsoleEncre.copy(alpha = 0.8f),
            pressedContentColor = ConsoleNavy,
        ),
        border = ClickableSurfaceDefaults.border(
            border = Border(BorderStroke(1.u, Color.White.copy(alpha = 0.16f)), shape = CircleShape),
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.08f),
    ) {
        Row(
            Modifier.height(CONSOLE_BOUTON.u).padding(horizontal = 8.u),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(icone, contentDescription = libelle, modifier = Modifier.size(18.u))
            AnimatedVisibility(
                visible = focus,
                enter = expandHorizontally(tween(220)) + fadeIn(tween(220)),
                exit = shrinkHorizontally(tween(180)) + fadeOut(tween(120)),
                label = "libelleBouton",
            ) {
                Text(
                    libelle, fontSize = 13.us, fontWeight = FontWeight.SemiBold,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(start = 7.u, end = 3.u).widthIn(max = 150.u),
                )
            }
        }
    }
}

/** « Mise à jour » : pilule bleu clair, libellé toujours visible. */
@Composable
private fun ConsoleBoutonMaj(onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        modifier = Modifier.height(CONSOLE_BOUTON.u),
        shape = ClickableSurfaceDefaults.shape(CircleShape),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = ConsoleAccent.copy(alpha = 0.26f),
            contentColor = ConsoleEncre,
            focusedContainerColor = ConsoleAccent,
            focusedContentColor = ConsoleNavy,
            pressedContainerColor = ConsoleAccent.copy(alpha = 0.8f),
            pressedContentColor = ConsoleNavy,
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.08f),
    ) {
        Row(
            Modifier.height(CONSOLE_BOUTON.u).padding(horizontal = 13.u),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(ProjoIcons.MiseAJour, contentDescription = null, modifier = Modifier.size(17.u))
            Text(
                "Mise à jour", fontSize = 13.us, fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(start = 7.u),
            )
        }
    }
}

// ------------------------------------------------------------------ bande des favoris

/** Bande de tuiles carrées (favoris), ou la carte d'aide s'il n'y en a aucun. */
@Composable
private fun ConsoleBande(ctx: HomeContext, nav: NavRangees, anneau: ConsoleAnneau, focusNba: FocusRequester) {
    val ui = ctx.ui
    val actions = ctx.actions
    val derniere = ui.favorites.lastOrNull()?.let { "fav:" + it.packageName } ?: "aide"
    LazyRow(
        state = ctx.rangeeFavoris,
        modifier = Modifier
            .fillMaxWidth()
            // Hauteur fixe (tuile agrandie + anneau) : le héros ne bouge pas avec le focus
            .testTag("rangee:favoris")
            .height((CONSOLE_TUILE_GRANDE + 28).u)
            // ▼ : directement à « Mes apps » (centre de la tuile seule, sans son nom)
            .navFavoris(nav)
            .onPreviewKeyEvent { e ->
                // ▶ sur la dernière tuile : dans la carte NBA (à droite, entre les rangées) ;
                // si elle ne peut pas prendre le focus, on reste sur place
                if (e.type == KeyEventType.KeyDown && e.key == Key.DirectionRight && nav.favoris.cle == derniere) {
                    runCatching { focusNba.requestFocus() }
                    true
                } else {
                    false
                }
            }
            .fadingEdges(ctx.rangeeFavoris, 56.u),
        // Marges hautes/basses : place pour l'anneau et le halo de la tuile sélectionnée
        contentPadding = PaddingValues(start = CONSOLE_MARGE.u, end = CONSOLE_MARGE.u, top = 14.u, bottom = 14.u),
        horizontalArrangement = Arrangement.spacedBy(12.u),
    ) {
        if (ui.loaded && ui.favorites.isEmpty()) {
            item(key = "aide") {
                ConsoleCarte(
                    largeur = 300.u,
                    hauteur = CONSOLE_TUILE.u,
                    anneau = anneau,
                    onClick = {},
                    onLongClick = null,
                    onFocus = { f -> if (f) ctx.sansSelection() },
                    modifier = Modifier
                        .focusRequester(ctx.premierFocus)
                        .then(rememberCibleNav(nav.favoris, "aide"))
                        .testTag("aide"),
                ) {
                    Box(Modifier.fillMaxSize().padding(horizontal = 18.u), contentAlignment = Alignment.CenterStart) {
                        Text(
                            "Aucun favori.\nMaintiens OK sur une app pour l'ajouter.",
                            fontSize = 13.us, fontWeight = FontWeight.Medium,
                        )
                    }
                }
            }
        }
        itemsIndexed(ui.favorites, key = { _, a -> "fav:" + a.packageName }) { i, app ->
            val cible = rememberCibleNav(nav.favoris, "fav:" + app.packageName)
            ConsoleTuile(
                app = app,
                icons = ctx.icons,
                iconPackage = ui.iconSourceOf(app.packageName),
                anneau = anneau,
                onFocused = ctx.surFocus,
                onClick = { actions.launch(app) },
                onMenu = { actions.openMenu(app) },
                modifier = (if (i == 0) Modifier.focusRequester(ctx.premierFocus) else Modifier).then(cible)
                    .testTag("favori:" + app.packageName),
            )
        }
    }
}

/**
 * Tuile carrée d'un favori (visuel de l'app recadré). Sélectionnée : elle passe de 72 à
 * 110, entourée d'un liseré bleu nuit, d'un anneau blanc et d'un halo ; son nom et sa
 * rubrique apparaissent à côté.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ConsoleTuile(
    app: AppInfo,
    icons: IconLoader,
    /** Paquet dont on affiche le visuel (logo éventuellement remplacé). */
    iconPackage: String,
    anneau: ConsoleAnneau,
    onFocused: (AppInfo, Color) -> Unit,
    onClick: () -> Unit,
    onMenu: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Tuile carrée : visuel carré de l'app (l'image 16:9 y serait rognée)
    val visual = rememberAppVisual(iconPackage, icons, FormatVisuel.CARRE)
    val couleur = visual?.color ?: IconLoader.COULEUR_PAR_DEFAUT
    val rubrique = remember(app) { AppCategories.rubrique(app).titre }
    var focus by remember { mutableStateOf(false) }
    val cote by animateDpAsState(
        if (focus) CONSOLE_TUILE_GRANDE.u else CONSOLE_TUILE.u,
        tween(220, easing = FastOutSlowInEasing),
        label = "coteTuile",
    )
    val p by animateFloatAsState(if (focus) 1f else 0f, tween(220), label = "anneauTuile")
    // Visuel arrivé après la prise de focus : on transmet la vraie couleur au fond
    LaunchedEffect(couleur) { if (focus) onFocused(app, couleur) }
    // La rangée défile vers la tuile au moment du focus, quand elle est encore petite : une fois
    // agrandie, on la ramène entièrement à l'écran (tuile au bord droit, arrivée par ▲ ou ▶)
    val visible = remember { BringIntoViewRequester() }
    LaunchedEffect(focus) {
        if (focus) {
            delay(240)
            runCatching { visible.bringIntoView() }
        }
    }

    Row {
        Box(
            Modifier
                .bringIntoViewRequester(visible)
                // Taille lue à la mise en page : l'agrandissement ne recompose pas la tuile
                .consoleCarre { cote }
                .drawBehind { anneau.dessiner(this, p, size.minDimension * CONSOLE_RAYON_PCT / 100f) }
        ) {
            Surface(
                onClick = onClick,
                onLongClick = onMenu,
                modifier = modifier
                    .fillMaxSize()
                    .onFocusChanged {
                        focus = it.isFocused
                        if (it.isFocused) onFocused(app, couleur)
                    }
                    .consoleToucheMenu(onMenu),
                shape = ClickableSurfaceDefaults.shape(ConsoleFormeTuile),
                colors = ClickableSurfaceDefaults.colors(
                    containerColor = Color.White.copy(alpha = 0.06f),
                    focusedContainerColor = Color.White.copy(alpha = 0.06f),
                    pressedContainerColor = Color.White.copy(alpha = 0.06f),
                ),
                scale = ClickableSurfaceDefaults.scale(focusedScale = 1f, pressedScale = 0.96f),
            ) {
                ConsoleVisuel(
                    app = app,
                    visual = visual,
                    couleur = couleur,
                    lettre = 26.us,
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(ConsoleFormeTuile)
                        .drawWithContent {
                            drawContent()
                            drawRect(ConsoleReflet)
                        },
                )
            }
        }
        // Nom et rubrique à droite de la tuile sélectionnée (apparition un peu retardée)
        AnimatedVisibility(
            visible = focus,
            enter = fadeIn(tween(220, delayMillis = 100)) + expandHorizontally(tween(220), expandFrom = Alignment.Start),
            exit = fadeOut(tween(100)) + shrinkHorizontally(tween(200), shrinkTowards = Alignment.Start),
            label = "nomTuile",
        ) {
            Column(Modifier.padding(start = 18.u, end = 10.u, top = 28.u).widthIn(max = 210.u)) {
                Text(
                    app.label, color = ConsoleEncre, fontSize = 18.us, fontWeight = FontWeight.Medium,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
                Text(
                    rubrique, color = ConsoleEncre2, fontSize = 12.us, fontWeight = FontWeight.Light,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 5.u),
                )
            }
        }
    }
}

// ------------------------------------------------------------------ héros

/** Ce que présente le grand titre : l'app sélectionnée ou la bibliothèque. */
private data class ConsoleHerosInfo(
    val cle: String,
    val titre: String,
    val surtitre: String,
    val favori: Boolean,
    /** Libellé de l'appui sur OK. */
    val action: String,
    /** Appui long possible (menu de l'app). */
    val options: Boolean,
)

/** Grand titre (fondu enchaîné et léger glissement à chaque changement d'app). */
@Composable
private fun ConsoleHeros(info: ConsoleHerosInfo?, modifier: Modifier) {
    AnimatedContent(
        targetState = info,
        modifier = modifier,
        transitionSpec = {
            (fadeIn(tween(300, delayMillis = 60)) + slideInHorizontally(tween(360)) { -it / 24 }) togetherWith
                fadeOut(tween(150)) using SizeTransform(clip = false)
        },
        contentAlignment = Alignment.CenterStart,
        contentKey = { it?.cle },
        label = "heros",
    ) { h ->
        if (h != null) ConsoleHerosTexte(h) else Spacer(Modifier.fillMaxWidth())
    }
}

@Composable
private fun ConsoleHerosTexte(h: ConsoleHerosInfo) {
    // Taille selon la longueur du nom : il tient toujours sur une ligne à gauche de la carte NBA
    val taille = when {
        h.titre.length <= 9 -> 80
        h.titre.length <= 13 -> 64
        h.titre.length <= 18 -> 50
        else -> 40
    }
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (h.favori) {
                Text(
                    "FAVORI", color = ConsoleEncre, fontSize = 10.us, fontWeight = FontWeight.SemiBold,
                    letterSpacing = 1.us, maxLines = 1,
                    modifier = Modifier
                        .border(1.u, ConsoleEncre.copy(alpha = 0.4f), RoundedCornerShape(3.u))
                        .padding(horizontal = 6.u, vertical = 2.u),
                )
                Spacer(Modifier.width(10.u))
            }
            Text(
                h.surtitre, color = ConsoleEncre2, fontSize = 13.us,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
        Text(
            h.titre,
            color = ConsoleEncre,
            fontSize = taille.us,
            fontWeight = FontWeight.ExtraLight,
            letterSpacing = (-taille / 32).us,
            lineHeight = (taille * 11 / 10).us,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 2.u),
        )
        // Rappel des touches (comportements réels : OK ouvre, appui long ouvre le menu)
        Row(Modifier.padding(top = 8.u), verticalAlignment = Alignment.CenterVertically) {
            ConsoleTouche("OK")
            ConsoleIndication(h.action)
            if (h.options) {
                Spacer(Modifier.width(14.u))
                ConsoleIndication("Maintenir")
                ConsoleTouche("OK")
                ConsoleIndication("Options")
            }
        }
    }
}

/** Petite touche de télécommande dessinée (rappel discret). */
@Composable
private fun ConsoleTouche(texte: String) {
    Text(
        texte, color = ConsoleEncre.copy(alpha = 0.85f), fontSize = 10.us, fontWeight = FontWeight.SemiBold,
        maxLines = 1,
        modifier = Modifier
            .border(1.u, ConsoleEncre.copy(alpha = 0.35f), RoundedCornerShape(4.u))
            .padding(horizontal = 5.u, vertical = 1.u),
    )
}

@Composable
private fun ConsoleIndication(texte: String) {
    Text(
        texte, color = ConsoleEncre3, fontSize = 12.us, maxLines = 1,
        modifier = Modifier.padding(horizontal = 6.u),
    )
}

private fun consoleNombreApps(n: Int): String = if (n > 1) "$n apps" else "$n app"

// ------------------------------------------------------------------ mes apps

/** « Mes apps » : cartes larges (icône + nom), puis « Toutes les apps (N) ». */
@Composable
private fun ConsoleMesApps(
    ctx: HomeContext,
    nav: NavRangees,
    anneau: ConsoleAnneau,
    focusNba: FocusRequester,
    surBiblio: (Boolean) -> Unit,
) {
    val ui = ctx.ui
    val actions = ctx.actions
    val densite = LocalDensity.current
    val trait = with(densite) { 1.u.toPx() }
    val tiret = with(densite) { 5.u.toPx() }
    val espace = with(densite) { 4.u.toPx() }
    val pointille = remember(trait, tiret, espace) {
        Stroke(width = trait, pathEffect = PathEffect.dashPathEffect(floatArrayOf(tiret, espace)))
    }
    Column(Modifier.fillMaxWidth()) {
        Text(
            "Mes apps", color = ConsoleEncre, fontSize = 14.us, fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(start = CONSOLE_MARGE.u, bottom = 4.u),
        )
        LazyRow(
            state = ctx.rangeeApps,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("rangee:apps")
                .height((CONSOLE_CARTE_H + 26).u)
                // ▲ : directement à la tuile du dessus
                .navApps(nav)
                .onPreviewKeyEvent { e ->
                    // ▶ sur « Toutes les apps » (toujours la dernière) : dans la carte NBA, au-dessus
                    // à droite ; si elle ne peut pas prendre le focus, on reste sur place
                    if (e.type == KeyEventType.KeyDown && e.key == Key.DirectionRight && nav.apps.cle == "bibliotheque") {
                        runCatching { focusNba.requestFocus() }
                        true
                    } else {
                        false
                    }
                }
                .fadingEdges(ctx.rangeeApps, 56.u),
            contentPadding = PaddingValues(horizontal = CONSOLE_MARGE.u, vertical = 13.u),
            horizontalArrangement = Arrangement.spacedBy(10.u),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            itemsIndexed(ui.homeApps, key = { _, a -> "app:" + a.packageName }) { _, app ->
                ConsoleCarteApp(
                    app = app,
                    icons = ctx.icons,
                    iconPackage = ui.iconSourceOf(app.packageName),
                    anneau = anneau,
                    onFocused = ctx.surFocus,
                    onClick = { actions.launch(app) },
                    onMenu = { actions.openMenu(app) },
                    modifier = Modifier.then(rememberCibleNav(nav.apps, "app:" + app.packageName)).testTag("app:" + app.packageName),
                )
            }
            // Bibliothèque : toutes les apps installées
            item(key = "bibliotheque") {
                ConsoleCarte(
                    largeur = CONSOLE_BIBLIO_L.u,
                    anneau = anneau,
                    onClick = actions.openLibrary,
                    onLongClick = null,
                    onFocus = { f ->
                        surBiblio(f)
                        if (f) ctx.sansSelection()
                    },
                    pointille = pointille,
                    modifier = Modifier
                        .focusRequester(ctx.focusBiblio)
                        .then(rememberCibleNav(nav.apps, "bibliotheque"))
                        .testTag("biblio"),
                ) {
                    ConsoleContenuCarte("Toutes les apps (${ui.allApps.size})") {
                        Box(Modifier.fillMaxSize().background(Color.White.copy(alpha = 0.12f)))
                        Icon(ProjoIcons.Apps, contentDescription = null, modifier = Modifier.size(15.u))
                    }
                }
            }
        }
    }
}

/** Carte d'une app de « Mes apps » : pastille avec le visuel de l'app, nom dessous. */
@Composable
private fun ConsoleCarteApp(
    app: AppInfo,
    icons: IconLoader,
    iconPackage: String,
    anneau: ConsoleAnneau,
    onFocused: (AppInfo, Color) -> Unit,
    onClick: () -> Unit,
    onMenu: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Pastille carrée (26 u) : visuel carré de l'app
    val visual = rememberAppVisual(iconPackage, icons, FormatVisuel.CARRE)
    val couleur = visual?.color ?: IconLoader.COULEUR_PAR_DEFAUT
    var focus by remember { mutableStateOf(false) }
    LaunchedEffect(couleur) { if (focus) onFocused(app, couleur) }
    ConsoleCarte(
        largeur = CONSOLE_CARTE_L.u,
        anneau = anneau,
        onClick = onClick,
        onLongClick = onMenu,
        onFocus = { f ->
            focus = f
            if (f) onFocused(app, couleur)
        },
        modifier = modifier.consoleToucheMenu(onMenu),
    ) {
        ConsoleContenuCarte(app.label) {
            ConsoleVisuel(app = app, visual = visual, couleur = couleur, lettre = 13.us, modifier = Modifier.fillMaxSize())
        }
    }
}

/** Contenu d'une carte : pastille carrée en haut à gauche, libellé en bas. */
@Composable
private fun ConsoleContenuCarte(libelle: String, pastille: @Composable BoxScope.() -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(9.u),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Box(
            Modifier.size(26.u).clip(RoundedCornerShape(7.u)),
            contentAlignment = Alignment.Center,
        ) {
            pastille()
        }
        Text(
            libelle, fontSize = 13.us, fontWeight = FontWeight.Medium,
            maxLines = 1, overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * Carte de verre sombre (Mes apps, bibliothèque, aide). Sélectionnée : elle grandit un peu
 * et reçoit le même anneau que les tuiles du haut. [pointille] : contour en tirets.
 */
@Composable
private fun ConsoleCarte(
    largeur: Dp,
    anneau: ConsoleAnneau,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)?,
    onFocus: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    hauteur: Dp = CONSOLE_CARTE_H.u,
    pointille: Stroke? = null,
    contenu: @Composable BoxScope.() -> Unit,
) {
    var focus by remember { mutableStateOf(false) }
    val p by animateFloatAsState(if (focus) 1f else 0f, tween(200), label = "anneauCarte")
    val rayon = 10.u
    val rayonPx = with(LocalDensity.current) { rayon.toPx() }
    val forme = RoundedCornerShape(rayon)
    Box(
        Modifier
            // Au-dessus de ses voisines : le halo n'est pas recouvert
            .zIndex(if (focus) 1f else 0f)
            .size(largeur, hauteur)
            .graphicsLayer {
                val s = 1f + 0.05f * p
                scaleX = s
                scaleY = s
            }
            .drawBehind { anneau.dessiner(this, p, rayonPx) }
            .drawWithContent {
                drawContent()
                if (pointille != null) {
                    val m = pointille.width / 2f
                    drawRoundRect(
                        color = Color.White.copy(alpha = 0.26f),
                        topLeft = Offset(m, m),
                        size = Size(size.width - 2 * m, size.height - 2 * m),
                        cornerRadius = CornerRadius(rayonPx - m),
                        style = pointille,
                    )
                }
            }
    ) {
        Surface(
            onClick = onClick,
            onLongClick = onLongClick,
            modifier = modifier
                .fillMaxSize()
                .onFocusChanged {
                    focus = it.isFocused
                    onFocus(it.isFocused)
                },
            shape = ClickableSurfaceDefaults.shape(forme),
            colors = ClickableSurfaceDefaults.colors(
                containerColor = Color.White.copy(alpha = if (pointille != null) 0.035f else 0.065f),
                contentColor = ConsoleEncre,
                focusedContainerColor = Color.White.copy(alpha = 0.14f),
                focusedContentColor = ConsoleEncre,
                pressedContainerColor = Color.White.copy(alpha = 0.20f),
                pressedContentColor = ConsoleEncre,
            ),
            border = if (pointille != null) ClickableSurfaceDefaults.border()
            else ClickableSurfaceDefaults.border(
                border = Border(BorderStroke(1.u, Color.White.copy(alpha = 0.09f)), shape = forme),
            ),
            scale = ClickableSurfaceDefaults.scale(focusedScale = 1f, pressedScale = 0.97f),
        ) {
            contenu()
        }
    }
}

// ------------------------------------------------------------------ éléments communs

/**
 * Visuel carré d'une app (format CARRE : logo entier, jamais rogné), tuile HDMI dessinée,
 * ou initiale en attendant.
 */
@Composable
private fun ConsoleVisuel(
    app: AppInfo,
    visual: AppVisual?,
    couleur: Color,
    lettre: TextUnit,
    modifier: Modifier = Modifier,
) {
    Box(modifier, contentAlignment = Alignment.Center) {
        val art = visual?.art
        when {
            app.isSpecial -> {
                Box(Modifier.fillMaxSize().background(ConsoleDegradeHdmi))
                Icon(
                    ProjoIcons.Sources, contentDescription = app.label, tint = Color.White,
                    modifier = Modifier.fillMaxSize(0.46f),
                )
            }
            art != null -> ImageApp(visual, app.label, Modifier.fillMaxSize())
            else -> {
                Box(Modifier.fillMaxSize().background(lerp(couleur, Color.Black, 0.5f)))
                Text(
                    app.label.take(1).uppercase(), color = Color.White,
                    fontSize = lettre, fontWeight = FontWeight.Light,
                )
            }
        }
    }
}

/** Touche « Menu » de la télécommande = même menu que l'appui long. */
private fun Modifier.consoleToucheMenu(onMenu: () -> Unit): Modifier = onPreviewKeyEvent {
    if (it.key == Key.Menu && it.type == KeyEventType.KeyUp) {
        onMenu()
        true
    } else {
        false
    }
}

/** Carré dont le côté est lu à la mise en page (animation sans recomposition). */
private fun Modifier.consoleCarre(cote: () -> Dp): Modifier = layout { mesurable, _ ->
    val px = cote().roundToPx()
    val placeable = mesurable.measure(Constraints.fixed(px, px))
    layout(px, px) { placeable.place(0, 0) }
}

/**
 * Anneau de sélection : liseré bleu nuit, anneau blanc net puis halo blanc (contours de
 * plus en plus larges et transparents, sans flou). Dessiné autour de l'élément, hors de
 * ses bords ; traits créés une seule fois.
 */
private class ConsoleAnneau(private val ecart: Float, private val trait: Float, halo: Float) {
    private val pas = halo / ConsoleHalo.size
    private val styleEcart = Stroke(width = ecart)
    private val styleTrait = Stroke(width = trait)
    private val styleHalo = Stroke(width = pas)

    /** [p] : 0 = invisible, 1 = complet ; [rayon] : rayon des coins de l'élément (px). */
    fun dessiner(d: DrawScope, p: Float, rayon: Float) {
        if (p <= 0.01f) return
        for (i in ConsoleHalo.indices) {
            d.consoleContour(ecart + trait + pas * (i + 0.5f), rayon, styleHalo, Color.White, ConsoleHalo[i] * p)
        }
        d.consoleContour(ecart / 2f, rayon, styleEcart, ConsoleNavy, p)
        d.consoleContour(ecart + trait / 2f, rayon, styleTrait, Color.White, p)
    }
}

@Composable
private fun rememberConsoleAnneau(): ConsoleAnneau {
    val densite = LocalDensity.current
    val ecart = with(densite) { 2.u.toPx() }
    val trait = with(densite) { 2.u.toPx() }
    val halo = with(densite) { 7.u.toPx() }
    return remember(ecart, trait, halo) { ConsoleAnneau(ecart, trait, halo) }
}

/** Contour arrondi centré à [decalage] px à l'extérieur des bords. */
private fun DrawScope.consoleContour(decalage: Float, rayon: Float, trait: Stroke, couleur: Color, alpha: Float) {
    drawRoundRect(
        color = couleur,
        topLeft = Offset(-decalage, -decalage),
        size = Size(size.width + 2 * decalage, size.height + 2 * decalage),
        cornerRadius = CornerRadius(rayon + decalage),
        style = trait,
        alpha = alpha,
    )
}
