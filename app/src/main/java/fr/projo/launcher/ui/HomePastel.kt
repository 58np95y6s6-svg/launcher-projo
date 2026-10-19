package fr.projo.launcher.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
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
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Glow
import androidx.tv.material3.Icon
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import fr.projo.launcher.data.AppCategories
import fr.projo.launcher.data.AppInfo
import fr.projo.launcher.data.AppVisual
import fr.projo.launcher.data.FormatVisuel
import fr.projo.launcher.data.IconLoader
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * Design « Pastel » (Material You) : thème clair lilas et pêche, formes rondes.
 *  - en haut, une bande « bento » : horloge dans un cookie festonné, « Bonsoir » + date,
 *    2 × 2 tuiles de réglages rapides en pilules, carte NBA blanche ;
 *  - nom de l'app sélectionnée, puis les favoris en cartes tonales (visuel réel de l'app) ;
 *  - « Mes apps » en pilules (icône + nom), terminées par « Toutes les apps » en pêche.
 * Focus : la carte grossit avec un rebond, s'arrondit et reçoit un anneau violet décollé ;
 * sur les pilules, seul l'élément sélectionné prend le violet plein.
 * Fond un ton plus soutenu que la maquette : un aplat presque blanc éblouit au projecteur
 * et délave les teintes des cartes.
 */
@Composable
fun HomePastel(ctx: HomeContext) {
    val maintenant = rememberMaintenant()
    Box(Modifier.fillMaxSize()) {
        PastelFond()

        Column(Modifier.fillMaxSize().padding(top = 32.u)) {
            // --- Bande haute : horloge, salutation + réglages rapides, NBA
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(PASTEL_BANDE.u)
                    .padding(horizontal = PASTEL_BORD.u)
            ) {
                PastelHorloge(maintenant)
                Spacer(Modifier.width(16.u))
                PastelColonneCentrale(ctx, maintenant, Modifier.weight(1f).fillMaxHeight())
                Spacer(Modifier.width(16.u))
                PastelWidgetNba(ctx)
            }

            Spacer(Modifier.height(12.u))
            // --- Nom de l'app sélectionnée + sa rubrique (fondu enchaîné)
            PastelTitre(
                ctx.selection,
                Modifier.fillMaxWidth().height(48.u).padding(start = PASTEL_BORD.u),
            )

            PastelRangeeFavoris(ctx)

            Spacer(Modifier.height(4.u))
            PastelTexte(
                "Mes apps", 14.us, Modifier.padding(start = (PASTEL_BORD + 2).u),
                couleur = PastelEncreDouce, poids = FontWeight.Bold,
            )
            PastelRangeeApps(ctx)
        }
    }
}

// ------------------------------------------------------------------ mesures (unités .u)

/** Marge gauche/droite de l'écran. */
private const val PASTEL_BORD = 32
/** Hauteur de la bande haute (horloge, réglages rapides, NBA). */
private const val PASTEL_BANDE = 186
/** Carte favori : 16:9 à l'intérieur du cadre tonal. */
private const val PASTEL_CARTE_L = 176
private const val PASTEL_CARTE_H = 104
private const val PASTEL_CADRE = 6
/** Largeur du fondu des rangées, et marge gardée autour de l'élément sélectionné. */
private const val PASTEL_FONDU = 90
private const val PASTEL_MARGE = 110

// ------------------------------------------------------------------ palette

// Palette tonale (graine lilas #8B6FD6, tertiaire pêche), un cran plus soutenue que la maquette
private val PastelPrimaire = Color(0xFF6A4FB3)
private val PastelPrimaireAppuye = Color(0xFF59409E)
private val PastelSurPrimaire = Color.White
private val PastelPrimaireConteneur = Color(0xFFDFD0FF)
private val PastelSurPrimaireConteneur = Color(0xFF2F1868)
/** Tuiles rapides : une seule teinte pour les quatre (le violet plein est réservé au focus). */
private val PastelTonal = Color(0xFFDACFEA)
private val PastelSurTonal = Color(0xFF2E2740)
private val PastelSecondaireConteneur = Color(0xFFE2D8F3)
private val PastelTertiaire = Color(0xFF9A4A2E)
private val PastelTertiaireConteneur = Color(0xFFFFD3BF)
private val PastelSurTertiaireConteneur = Color(0xFF4F1B06)
private val PastelTertiaireClair = Color(0xFFFFEEE6)
private val PastelEncre = Color(0xFF1D1A22)
private val PastelEncreDouce = Color(0xFF4A4453)
private val PastelBlanc = Color(0xFFFCF9FD)
private val PastelContour = Color(0xFFD9CFE2)
private val PastelFondPilule = Color(0xFFF7F2F9)
private val PastelCookieFond = Color(0xFFD9C8FA)
private val PastelMajFond = Color(0xFFCDB8F5)
private val PastelOngletActif = Color(0xFFECE5F1)
private val PastelPastilleLogo = Color(0xFFF1EBF4)
// Fond d'écran
private val PastelFondHaut = Color(0xFFEEE6F1)
private val PastelFondBas = Color(0xFFE5DAEC)
private val PastelHaloPeche = Color(0xFFF4DCD2)
private val PastelHaloLilas = Color(0xFFDACDF1)
private val PastelSoleil = Color(0xFFF1D8CE)
private val PastelFleur = Color(0xFFDDD0EF)

/** Textes sans l'interligne imposé par le thème TV (24 sp) : hauteur naturelle de la police. */
private val PastelStyle = TextStyle.Default
/** Chiffres à chasse fixe : l'horloge ne « danse » pas d'une minute à l'autre. */
private val PastelStyleChiffres = TextStyle(fontFeatureSettings = "tnum")

/** Bandeau NBA : texte foncé sur carte blanche, ligne sélectionnée en primaryContainer. */
private val PastelStyleNba = NbaStyle(
    largeur = 330,
    rayon = 16,
    fond = PastelBlanc,
    bordure = Color.Transparent,
    texte = PastelEncre,
    texteDiscret = PastelEncreDouce,
    ongletActif = PastelOngletActif,
    focusFond = PastelPrimaireConteneur,
    focusTexte = PastelSurPrimaireConteneur,
    pastilleFond = PastelPastilleLogo,
    echelle = 1.05f,
)

/** Rebond Material 3 Expressive (échelle et arrondi au focus). */
private fun <T> pastelRessort() = spring<T>(dampingRatio = 0.55f, stiffness = 400f)

// ------------------------------------------------------------------ formes festonnées

private const val PASTEL_POINTS = 240

/**
 * Ajoute au chemin un cercle festonné : rayon moyen [rayon], [lobes] festons arrondis
 * d'amplitude [amplitude] (même formule que la maquette, équivalent des MaterialShapes).
 */
private fun Path.pastelFestons(
    cx: Float, cy: Float, rayon: Float, lobes: Int, amplitude: Float, rotation: Float = 0f,
) {
    val pas = 2f * PI.toFloat() / PASTEL_POINTS
    for (i in 0 until PASTEL_POINTS) {
        val t = i * pas
        val r = rayon + amplitude * cos(lobes * (t + rotation))
        val x = cx + r * cos(t)
        val y = cy + r * sin(t)
        if (i == 0) moveTo(x, y) else lineTo(x, y)
    }
    close()
}

/** Forme « cookie » qui remplit sa boîte ; [profondeur] = creux des festons (part du rayon). */
private fun pastelFormeFestons(lobes: Int, profondeur: Float, rotation: Float = 0f): Shape =
    GenericShape { taille, _ ->
        val r = min(taille.width, taille.height) / 2f
        val a = r * profondeur / 2f
        pastelFestons(taille.width / 2f, taille.height / 2f, r - a, lobes, a, rotation)
    }

private val PastelCookie12 = pastelFormeFestons(12, 0.10f)
private val PastelCookie6 = pastelFormeFestons(6, 0.14f)
private val PastelCookie4 = pastelFormeFestons(4, 0.16f, (PI / 4).toFloat())
private val PastelSoleil8 = pastelFormeFestons(8, 0.10f)
/** Masques des icônes de « Mes apps » : formes variées, comme les icônes thématisées. */
private val PastelFormesIcones: List<Shape> = listOf(
    RoundedCornerShape(30), PastelCookie6, CircleShape, PastelCookie4, PastelSoleil8, PastelCookie12,
)

/** Icône calendrier (Material) de la pastille de date. */
private val PastelIconeCalendrier: ImageVector by lazy {
    ImageVector.Builder(
        defaultWidth = 24.dp, defaultHeight = 24.dp,
        viewportWidth = 24f, viewportHeight = 24f,
    ).addPath(
        pathData = PathParser().parsePathString(
            "M17,12h-5v5h5v-5zM16,1v2H8V1H6v2H5c-1.11,0 -1.99,0.9 -1.99,2L3,19c0,1.1 0.89,2 2,2h14" +
                "c1.1,0 2,-0.9 2,-2V5c0,-1.1 -0.9,-2 -2,-2h-1V1h-2zM19,19H5V8h14v11z"
        ).toNodes(),
        fill = SolidColor(Color.White),
    ).build()
}

/**
 * Teintes tonales d'une app à partir de sa couleur dominante : (repos, sélectionnée).
 * Même teinte, saturation bornée : assez soutenue pour rester visible au projecteur.
 * Couleurs neutres (noir, blanc, gris) → lilas du thème.
 */
private fun pastelTons(c: Color): Pair<Color, Color> {
    val hsv = FloatArray(3)
    android.graphics.Color.colorToHSV(c.toArgb(), hsv)
    val neutre = hsv[1] < 0.15f || hsv[2] < 0.12f
    val teinte = if (neutre) 262f else hsv[0]
    val s = if (neutre) 0.5f else hsv[1]
    fun ton(sat: Float, v: Float) = Color(android.graphics.Color.HSVToColor(floatArrayOf(teinte, sat, v)))
    return ton((s * 0.36f).coerceIn(0.14f, 0.30f), 0.90f) to ton((s * 0.55f).coerceIn(0.24f, 0.46f), 0.97f)
}

// ------------------------------------------------------------------ modificateurs

/** Touche « Menu » de la télécommande = même menu que l'appui long. */
private fun Modifier.pastelToucheMenu(onMenu: (() -> Unit)?): Modifier =
    if (onMenu == null) this else onPreviewKeyEvent {
        if (it.key == Key.Menu && it.type == KeyEventType.KeyUp) { onMenu(); true } else false
    }

/**
 * Quand l'élément prend le focus, la rangée défile pour garder [marge] de chaque côté :
 * l'élément sélectionné (agrandi, avec son anneau) ne finit jamais sous le fondu du bord.
 */
@OptIn(ExperimentalFoundationApi::class)
private fun Modifier.pastelGardeMarge(marge: Dp): Modifier = composed {
    val demande = remember { BringIntoViewRequester() }
    val scope = rememberCoroutineScope()
    val margePx = with(LocalDensity.current) { marge.toPx() }
    // Taille lue seulement au moment du focus : pas d'état, pas de recomposition
    val taille = remember { IntArray(2) }
    this
        .onSizeChanged { taille[0] = it.width; taille[1] = it.height }
        .bringIntoViewRequester(demande)
        .onFocusChanged {
            if (it.isFocused) scope.launch {
                demande.bringIntoView(Rect(-margePx, 0f, taille[0] + margePx, taille[1].toFloat()))
            }
        }
}

// ------------------------------------------------------------------ fond

/**
 * Fond d'écran statique : dégradé lilas, halos pêche et lilas, soleil pêche (6 festons)
 * en haut à droite et fleur lilas (8 festons) en bas à droite. Chemins et dégradés
 * calculés une fois par taille d'écran ; aucun flou.
 */
@Composable
private fun PastelFond() {
    Box(
        Modifier
            .fillMaxSize()
            .drawWithCache {
                val w = size.width
                val h = size.height
                val degrade = Brush.linearGradient(
                    listOf(PastelFondHaut, PastelFondBas), start = Offset.Zero, end = Offset(w * 0.4f, h),
                )
                val centrePeche = Offset(w * 0.45f, 0f)
                val rayonPeche = w * 0.34f
                val halo = Brush.radialGradient(
                    listOf(PastelHaloPeche, PastelHaloPeche.copy(alpha = 0f)), center = centrePeche, radius = rayonPeche,
                )
                val centreLilas = Offset(w * 0.08f, h * 0.97f)
                val rayonLilas = w * 0.40f
                val haloLilas = Brush.radialGradient(
                    listOf(PastelHaloLilas, PastelHaloLilas.copy(alpha = 0f)), center = centreLilas, radius = rayonLilas,
                )
                val soleil = Path().apply { pastelFestons(w * 1.005f, h * 0.04f, w * 0.16f, 6, w * 0.013f, 0.3f) }
                val fleur = Path().apply { pastelFestons(w * 0.81f, h * 0.94f, w * 0.21f, 8, w * 0.017f, 0.2f) }
                onDrawBehind {
                    drawRect(degrade)
                    drawCircle(brush = halo, radius = rayonPeche, center = centrePeche)
                    drawCircle(brush = haloLilas, radius = rayonLilas, center = centreLilas)
                    drawPath(soleil, PastelSoleil)
                    drawPath(fleur, PastelFleur)
                }
            }
    )
}

// ------------------------------------------------------------------ bande haute

/** Grande horloge : heures et minutes empilées, en deux tons, dans un cookie à 12 festons. */
@Composable
private fun PastelHorloge(maintenant: Long) {
    val heure = heureTexte(maintenant)
    Box(
        Modifier.size(PASTEL_BANDE.u).background(PastelCookieFond, PastelCookie12),
        contentAlignment = Alignment.Center,
    ) {
        PastelChiffres(heure.substringBefore(':'), PastelSurPrimaireConteneur, Modifier.offset(y = (-33).u))
        PastelChiffres(heure.substringAfter(':'), PastelPrimaire, Modifier.offset(y = 34.u))
    }
}

@Composable
private fun PastelChiffres(texte: String, couleur: Color, modifier: Modifier) {
    Text(
        texte, modifier, color = couleur, fontSize = 78.us, fontWeight = FontWeight.ExtraBold,
        letterSpacing = (-3).us, maxLines = 1, softWrap = false, style = PastelStyleChiffres,
    )
}

/** « Bonsoir », pastille de date (+ bouton de mise à jour), puis les 4 réglages rapides. */
@Composable
private fun PastelColonneCentrale(ctx: HomeContext, maintenant: Long, modifier: Modifier) {
    Column(modifier) {
        PastelTexte(
            salutation(maintenant), 42.us, couleur = PastelEncre,
            poids = FontWeight.Bold, espacement = (-1).us,
        )
        Spacer(Modifier.height(6.u))
        Row(verticalAlignment = Alignment.CenterVertically) {
            // La date cède la place au bouton de mise à jour si besoin (jamais l'inverse)
            PastelPastilleDate(maintenant, Modifier.weight(1f, fill = false))
            if (ctx.majDisponible) {
                Spacer(Modifier.width(8.u))
                PastelBoutonMaj(ctx)
            }
        }
        Spacer(Modifier.weight(1f))
        PastelReglagesRapides(ctx)
    }
}

/** Date en « sentence case » dans une pastille pêche. */
@Composable
private fun PastelPastilleDate(maintenant: Long, modifier: Modifier) {
    val texte = dateTexte(maintenant).replaceFirstChar { it.titlecase(Locale.FRENCH) }
    Row(
        modifier
            .height(26.u)
            .background(PastelTertiaireConteneur, RoundedCornerShape(13.u))
            .padding(start = 9.u, end = 13.u),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(PastelIconeCalendrier, contentDescription = null, tint = PastelTertiaire, modifier = Modifier.size(15.u))
        Spacer(Modifier.width(7.u))
        PastelTexte(texte, 13.us, couleur = PastelSurTertiaireConteneur, poids = FontWeight.SemiBold)
    }
}

/** Bouton « Mise à jour » (seulement quand une nouvelle version est disponible). */
@Composable
private fun PastelBoutonMaj(ctx: HomeContext) {
    PastelPilule(
        onClick = ctx.actions.update,
        onFocus = ctx.sansSelection,
        fond = PastelMajFond,
        contenu = PastelSurPrimaireConteneur,
        hauteur = 26.u, debut = 9.u, fin = 12.u,
        agrandi = 1.08f,
    ) { _ ->
        Icon(ProjoIcons.MiseAJour, contentDescription = null, modifier = Modifier.size(14.u))
        Spacer(Modifier.width(6.u))
        PastelTexte("Mise à jour", 12.us, poids = FontWeight.SemiBold)
    }
}

/** Grille 2 × 2 des réglages rapides (Sources, Wi-Fi, Bluetooth, Réglages). */
@Composable
private fun PastelReglagesRapides(ctx: HomeContext) {
    Column(verticalArrangement = Arrangement.spacedBy(7.u)) {
        ctx.quickActions().chunked(2).forEach { ligne ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.u)) {
                ligne.forEach { action -> PastelTuileRapide(action, ctx, Modifier.weight(1f)) }
            }
        }
    }
}

/** Tuile « Quick Settings » : icône dans un rond, titre et sous-titre. */
@Composable
private fun PastelTuileRapide(action: QuickAction, ctx: HomeContext, modifier: Modifier) {
    val wifi = action.icon === ProjoIcons.Wifi
    // Le libellé Wi-Fi est le nom du réseau : il passe en sous-titre
    val titre = if (wifi) "Wi-Fi" else action.label
    val detail = when {
        wifi -> if (action.label == "Wi-Fi") "Réseau" else action.label
        action.icon === ProjoIcons.Sources -> "HDMI"
        action.icon === ProjoIcons.Bluetooth -> "Appareils"
        action.estReglages -> "Système"
        else -> null
    }
    PastelPilule(
        onClick = action.onClick,
        onLongClick = action.onLongClick,
        onFocus = ctx.sansSelection,
        fond = PastelTonal,
        contenu = PastelSurTonal,
        hauteur = 42.u, debut = 6.u, fin = 12.u,
        agrandi = 1.05f,
        modifier = if (action.estReglages) modifier.focusRequester(ctx.focusReglages) else modifier,
    ) { focus ->
        Box(
            Modifier
                .size(30.u)
                .background(Color.White.copy(alpha = if (focus) 0.2f else 0.6f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(action.icon, contentDescription = null, modifier = Modifier.size(17.u))
        }
        Spacer(Modifier.width(9.u))
        Column {
            PastelTexte(titre, 13.us, poids = FontWeight.SemiBold)
            if (detail != null) {
                PastelTexte(detail, 11.us, Modifier.alpha(0.8f), poids = FontWeight.Medium)
            }
        }
    }
}

/** Carte NBA blanche, à la hauteur de l'horloge ; le bandeau partagé est centré dedans. */
@Composable
private fun PastelWidgetNba(ctx: HomeContext) {
    val forme = RoundedCornerShape(22.u)
    Box(
        Modifier
            .size(360.u, PASTEL_BANDE.u)
            .background(PastelBlanc, forme)
            .border(1.u, PastelContour, forme)
            // Le nom d'app affiché au-dessus des favoris n'a plus de sens quand on est ici
            .onFocusChanged { if (it.hasFocus) ctx.sansSelection() },
        contentAlignment = Alignment.Center,
    ) {
        NbaPanel(
            data = ctx.nbaData,
            erreur = ctx.nbaErreur,
            repo = ctx.nbaRepo,
            onExit = ctx.sortieNba,
            style = PastelStyleNba,
        )
    }
}

// ------------------------------------------------------------------ titre et rangées

/** Nom de l'app sélectionnée en grand, suivi de sa rubrique dans une pastille. */
@Composable
private fun PastelTitre(app: AppInfo?, modifier: Modifier) {
    AnimatedContent(
        targetState = app,
        transitionSpec = { fadeIn(tween(220)) togetherWith fadeOut(tween(160)) },
        contentAlignment = Alignment.BottomStart,
        label = "titrePastel",
        modifier = modifier,
    ) { a ->
        if (a != null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                PastelTexte(
                    a.label, 38.us, Modifier.widthIn(max = 620.u), couleur = PastelEncre,
                    poids = FontWeight.ExtraBold, espacement = (-1).us,
                )
                Spacer(Modifier.width(14.u))
                Box(
                    Modifier
                        .height(24.u)
                        .background(PastelSecondaireConteneur, RoundedCornerShape(12.u))
                        .padding(horizontal = 11.u),
                    contentAlignment = Alignment.Center,
                ) {
                    PastelTexte(
                        AppCategories.rubrique(a).titre, 12.us,
                        couleur = PastelSurTonal, poids = FontWeight.SemiBold,
                    )
                }
            }
        }
    }
}

/** Rangée des favoris (ou carte d'aide s'il n'y en a aucun). */
@Composable
private fun PastelRangeeFavoris(ctx: HomeContext) {
    val ui = ctx.ui
    LazyRow(
        state = ctx.rangeeFavoris,
        modifier = Modifier.fillMaxWidth().fadingEdges(ctx.rangeeFavoris, PASTEL_FONDU.u),
        // Haut/bas : place pour l'agrandissement et l'anneau. Fin : la dernière carte peut
        // venir hors du fondu.
        contentPadding = PaddingValues(start = PASTEL_BORD.u, end = PASTEL_MARGE.u, top = 18.u, bottom = 18.u),
        // Écart > agrandissement + anneau : la carte sélectionnée ne touche jamais ses voisines
        horizontalArrangement = Arrangement.spacedBy(22.u),
    ) {
        if (ui.loaded && ui.favorites.isEmpty()) {
            item(key = "aide") { PastelCarteAide(ctx) }
        }
        itemsIndexed(ui.favorites, key = { _, a -> "fav:" + a.packageName }) { i, app ->
            PastelFavori(app, ctx, if (i == 0) Modifier.focusRequester(ctx.premierFocus) else Modifier)
        }
    }
}

/** Rangée « Mes apps » : pilules des apps choisies, puis « Toutes les apps ». */
@Composable
private fun PastelRangeeApps(ctx: HomeContext) {
    LazyRow(
        state = ctx.rangeeApps,
        modifier = Modifier.fillMaxWidth().fadingEdges(ctx.rangeeApps, PASTEL_FONDU.u),
        contentPadding = PaddingValues(start = PASTEL_BORD.u, end = PASTEL_MARGE.u, top = 7.u, bottom = 7.u),
        horizontalArrangement = Arrangement.spacedBy(10.u),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        itemsIndexed(ctx.ui.homeApps, key = { _, a -> "app:" + a.packageName }) { i, app ->
            PastelPiluleApp(app, ctx, PastelFormesIcones[i % PastelFormesIcones.size])
        }
        item(key = "bibliotheque") { PastelPiluleBiblio(ctx) }
    }
}

// ------------------------------------------------------------------ cartes

/**
 * Carte tonale avec l'idiome de focus Pastel : agrandissement avec rebond, coins qui
 * s'arrondissent (16 → 34), anneau violet de 3 décollé de 4, teinte plus vive.
 * Le contenu est posé dans un cadre de 6 (la teinte de la carte fait passe-partout).
 */
@Composable
private fun PastelCarte(
    fond: Color,
    fondFocus: Color,
    onClick: () -> Unit,
    onFocus: () -> Unit,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
    contenu: @Composable BoxScope.() -> Unit,
) {
    var focus by remember { mutableStateOf(false) }
    val echelle = animateFloatAsState(if (focus) 1.12f else 1f, pastelRessort(), label = "echelleCarte")
    val rayon by animateDpAsState(if (focus) 34.u else 16.u, pastelRessort(), label = "rayonCarte")
    val anneau = animateFloatAsState(if (focus) 1f else 0f, tween(180), label = "anneauCarte")
    val cadre = PASTEL_CADRE.u
    val ecart = 4.u
    val epaisseur = 3.u
    val forme = RoundedCornerShape(rayon)
    Surface(
        onClick = onClick,
        onLongClick = onLongClick,
        modifier = modifier
            .size(PASTEL_CARTE_L.u, PASTEL_CARTE_H.u)
            .pastelGardeMarge(PASTEL_MARGE.u)
            // Échelle lue au dessin : l'animation ne recompose pas la carte
            .graphicsLayer {
                scaleX = echelle.value
                scaleY = echelle.value
            }
            // Anneau de focus décollé de la carte (suit l'arrondi animé)
            .drawWithCache {
                val trait = epaisseur.toPx()
                val e = ecart.toPx() + trait / 2f
                val stroke = Stroke(width = trait)
                onDrawBehind {
                    val a = anneau.value
                    if (a > 0.01f) {
                        drawRoundRect(
                            color = PastelPrimaire,
                            topLeft = Offset(-e, -e),
                            size = Size(size.width + 2f * e, size.height + 2f * e),
                            cornerRadius = CornerRadius(rayon.toPx() + e),
                            style = stroke,
                            alpha = a,
                        )
                    }
                }
            }
            .onFocusChanged {
                focus = it.isFocused
                if (it.isFocused) onFocus()
            }
            .pastelToucheMenu(onLongClick),
        shape = ClickableSurfaceDefaults.shape(forme),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = fond,
            contentColor = PastelEncre,
            focusedContainerColor = fondFocus,
            focusedContentColor = PastelEncre,
            pressedContainerColor = fondFocus,
            pressedContentColor = PastelEncre,
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f, pressedScale = 0.97f),
        glow = ClickableSurfaceDefaults.glow(
            focusedGlow = Glow(elevationColor = PastelPrimaire.copy(alpha = 0.3f), elevation = 8.u),
        ),
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .padding(cadre)
                .clip(RoundedCornerShape(maxOf(rayon - cadre, 0.dp))),
            contentAlignment = Alignment.Center,
            content = contenu,
        )
    }
}

/** Favori : carte tonale de la couleur de l'app, avec son vrai visuel dedans. */
@Composable
private fun PastelFavori(app: AppInfo, ctx: HomeContext, modifier: Modifier) {
    // Intérieur de la carte (cadre déduit) : 164 × 92, soit 16:9
    val format = FormatVisuel.pour(PASTEL_CARTE_L - 2 * PASTEL_CADRE, PASTEL_CARTE_H - 2 * PASTEL_CADRE)
    val visual = rememberAppVisual(ctx.ui.iconSourceOf(app.packageName), ctx.icons, format)
    val couleur = visual?.color ?: IconLoader.COULEUR_PAR_DEFAUT
    val tons = remember(couleur) { pastelTons(couleur) }
    PastelCarte(
        fond = tons.first,
        fondFocus = tons.second,
        onClick = { ctx.actions.launch(app) },
        onLongClick = { ctx.actions.openMenu(app) },
        onFocus = { ctx.surFocus(app, couleur) },
        modifier = modifier,
    ) {
        PastelVisuel(app, visual)
    }
}

/** Visuel 16:9 d'une app ; tuile HDMI : icône Sources sur sa couleur. */
@Composable
private fun PastelVisuel(app: AppInfo, visual: AppVisual?) {
    val art = visual?.art
    when {
        app.isSpecial -> Box(
            Modifier.fillMaxSize().background(IconLoader.COULEUR_HDMI),
            contentAlignment = Alignment.Center,
        ) {
            Icon(ProjoIcons.Sources, contentDescription = app.label, tint = Color.White, modifier = Modifier.size(38.u))
        }
        art != null -> ImageApp(visual, app.label, Modifier.fillMaxSize())
        // Visuel pas encore chargé (ou introuvable) : initiale sur la teinte de la carte
        else -> PastelTexte(app.label.take(1).uppercase(), 30.us, couleur = PastelEncreDouce, poids = FontWeight.Bold)
    }
}

/** Aucun favori : carte d'aide, focalisable (reçoit le premier focus). */
@Composable
private fun PastelCarteAide(ctx: HomeContext) {
    PastelCarte(
        fond = PastelFondPilule,
        fondFocus = PastelPrimaireConteneur,
        onClick = {},
        onFocus = ctx.sansSelection,
        modifier = Modifier.focusRequester(ctx.premierFocus),
    ) {
        Text(
            "Aucun favori.\nMaintiens OK sur une app pour l'ajouter.",
            modifier = Modifier.padding(horizontal = 8.u),
            fontSize = 12.us,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center,
            style = PastelStyle,
        )
    }
}

// ------------------------------------------------------------------ pilules

/**
 * Pilule focalisable (tuiles rapides, apps, bibliothèque) : teinte douce au repos,
 * violet plein + rebond quand elle est sélectionnée.
 */
@Composable
private fun PastelPilule(
    onClick: () -> Unit,
    onFocus: () -> Unit,
    fond: Color,
    contenu: Color,
    hauteur: Dp,
    debut: Dp,
    fin: Dp,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
    agrandi: Float = 1.06f,
    content: @Composable RowScope.(focus: Boolean) -> Unit,
) {
    var focus by remember { mutableStateOf(false) }
    val echelle = animateFloatAsState(if (focus) agrandi else 1f, pastelRessort(), label = "echellePilule")
    Surface(
        onClick = onClick,
        onLongClick = onLongClick,
        modifier = modifier
            .height(hauteur)
            .graphicsLayer {
                scaleX = echelle.value
                scaleY = echelle.value
            }
            .onFocusChanged {
                focus = it.isFocused
                if (it.isFocused) onFocus()
            },
        shape = ClickableSurfaceDefaults.shape(CircleShape),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = fond,
            contentColor = contenu,
            focusedContainerColor = PastelPrimaire,
            focusedContentColor = PastelSurPrimaire,
            pressedContainerColor = PastelPrimaireAppuye,
            pressedContentColor = PastelSurPrimaire,
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f, pressedScale = 0.97f),
    ) {
        Row(
            Modifier.fillMaxHeight().padding(start = debut, end = fin),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            content(focus)
        }
    }
}

/** App de « Mes apps » : petite icône réelle dans une forme festonnée + nom. */
@Composable
private fun PastelPiluleApp(app: AppInfo, ctx: HomeContext, formeIcone: Shape) {
    val paquet = ctx.ui.iconSourceOf(app.packageName)
    // Petite forme festonnée (32 u, carrée) : visuel carré de l'app et sa couleur
    val visual = rememberAppVisual(paquet, ctx.icons, FormatVisuel.CARRE)
    val icone = visual?.art
    val couleur = visual?.color ?: IconLoader.COULEUR_PAR_DEFAUT
    val menu = { ctx.actions.openMenu(app) }
    PastelPilule(
        onClick = { ctx.actions.launch(app) },
        onLongClick = menu,
        onFocus = { ctx.surFocus(app, couleur) },
        fond = PastelFondPilule,
        contenu = PastelEncre,
        hauteur = 48.u, debut = 8.u, fin = 18.u,
        modifier = Modifier.pastelGardeMarge(PASTEL_MARGE.u).pastelToucheMenu(menu),
    ) { _ ->
        Box(
            Modifier
                .size(32.u)
                .clip(formeIcone)
                .background(if (app.isSpecial) IconLoader.COULEUR_HDMI else PastelPrimaireConteneur),
            contentAlignment = Alignment.Center,
        ) {
            when {
                icone != null -> Image(
                    bitmap = icone,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                    filterQuality = FilterQuality.Medium,
                )
                app.isSpecial -> Icon(
                    ProjoIcons.Sources, contentDescription = null, tint = Color.White,
                    modifier = Modifier.size(18.u),
                )
                else -> PastelTexte(
                    app.label.take(1).uppercase(), 14.us,
                    couleur = PastelSurPrimaireConteneur, poids = FontWeight.Bold,
                )
            }
        }
        Spacer(Modifier.width(10.u))
        PastelTexte(app.label, 13.us, Modifier.widthIn(max = 150.u), poids = FontWeight.SemiBold)
    }
}

/** « Toutes les apps » : pilule pêche avec le nombre d'apps dans un badge. */
@Composable
private fun PastelPiluleBiblio(ctx: HomeContext) {
    PastelPilule(
        onClick = ctx.actions.openLibrary,
        onFocus = ctx.sansSelection,
        fond = PastelTertiaireConteneur,
        contenu = PastelSurTertiaireConteneur,
        hauteur = 48.u, debut = 8.u, fin = 10.u,
        modifier = Modifier.focusRequester(ctx.focusBiblio).pastelGardeMarge(PASTEL_MARGE.u),
    ) { focus ->
        Box(
            Modifier
                .size(32.u)
                .background(if (focus) Color.White.copy(alpha = 0.2f) else PastelTertiaireClair, PastelCookie4),
            contentAlignment = Alignment.Center,
        ) {
            Icon(ProjoIcons.Apps, contentDescription = null, modifier = Modifier.size(17.u))
        }
        Spacer(Modifier.width(9.u))
        PastelTexte("Toutes les apps", 13.us, poids = FontWeight.SemiBold)
        Spacer(Modifier.width(8.u))
        Box(
            Modifier
                .height(22.u)
                .widthIn(min = 28.u)
                .background(if (focus) Color.White else PastelTertiaire, CircleShape)
                .padding(horizontal = 7.u),
            contentAlignment = Alignment.Center,
        ) {
            PastelTexte(
                ctx.ui.allApps.size.toString(), 12.us,
                couleur = if (focus) PastelPrimaire else Color.White,
                poids = FontWeight.ExtraBold, style = PastelStyleChiffres,
            )
        }
    }
}

// ------------------------------------------------------------------ texte

/** Texte sur une ligne (points de suspension si trop long), à la hauteur naturelle de la police. */
@Composable
private fun PastelTexte(
    texte: String,
    taille: TextUnit,
    modifier: Modifier = Modifier,
    couleur: Color = Color.Unspecified,
    poids: FontWeight = FontWeight.Medium,
    espacement: TextUnit = TextUnit.Unspecified,
    style: TextStyle = PastelStyle,
) {
    Text(
        texte,
        modifier,
        color = couleur,
        fontSize = taille,
        fontWeight = poids,
        letterSpacing = espacement,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        style = style,
    )
}
