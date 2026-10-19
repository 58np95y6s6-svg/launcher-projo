package fr.projo.launcher.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import fr.projo.launcher.data.AppCategories
import fr.projo.launcher.data.AppInfo
import fr.projo.launcher.data.FormatVisuel
import fr.projo.launcher.data.IconLoader
import java.util.Locale
import kotlin.math.roundToInt

// ---------------------------------------------------------------- mesures (écran de 960 × 540)

/** Bandeau NBA : largeur de base et agrandissement (lisible à 3 m). */
private const val GRAPHITE_NBA_LARGEUR = 340
private const val GRAPHITE_NBA_ECHELLE = 1.1f

/** Colonne de gauche = largeur réelle du bandeau NBA (374). */
private val GRAPHITE_COLONNE = (GRAPHITE_NBA_LARGEUR * GRAPHITE_NBA_ECHELLE).roundToInt()

/** Marges extérieures et espace entre la colonne et le filet de séparation. */
private const val GRAPHITE_MARGE = 28
private const val GRAPHITE_ECART_FILET = 10

/** Retrait du contenu principal après le filet (laisse la place à la lueur du 1er élément). */
private const val GRAPHITE_RETRAIT = 46

/** Tuiles : favoris (16:9) et apps du dock. */
private const val GRAPHITE_FAV_L = 192
private const val GRAPHITE_FAV_H = 108
private const val GRAPHITE_DOCK_L = 108
private const val GRAPHITE_DOCK_H = 96

// ---------------------------------------------------------------- couleurs

private val GraphiteToile = Color(0xFF0D0E12)
private val GraphiteTexte = Color(0xFFECEDF1)
private val GraphiteTexte2 = Color(0xFFA3A7B2)
private val GraphiteTexte3 = Color(0xFF8D919E)
private val GraphiteViolet = Color(0xFF7C5CFF)
private val GraphiteBleu = Color(0xFF5B6CFF)
private val GraphiteCyan = Color(0xFF22D3EE)

/** Filet de 1 px des panneaux (blanc 7,5 %). */
private val GraphiteFilet = Color.White.copy(alpha = 0.075f)

/** Remplissage des panneaux : blanc 4 % → 1,8 %. */
private val GraphitePanneauFond = Brush.verticalGradient(
    listOf(Color.White.copy(alpha = 0.04f), Color.White.copy(alpha = 0.018f))
)

/** Textes en dégradé façon Linear : blanc → gris. */
private val GraphiteDegradeHorloge = Brush.verticalGradient(0.3f to Color.White, 1f to GraphiteTexte3)
private val GraphiteDegradeTitre = Brush.verticalGradient(0.35f to Color.White, 1f to GraphiteTexte2)

/** Petite étincelle violet → cyan devant la salutation. */
private val GraphiteEtincelle = Brush.linearGradient(listOf(Color(0xFFA78BFA), GraphiteCyan))

/** Filet de titre de section, qui s'efface vers la droite. */
private val GraphiteFiletSection = Brush.horizontalGradient(
    listOf(Color.White.copy(alpha = 0.13f), Color.Transparent)
)

/** Tuile « Source HDMI » (pas d'icône d'app). */
private val GraphiteDegradeHdmi = Brush.linearGradient(
    listOf(lerp(IconLoader.COULEUR_HDMI, Color.White, 0.12f), lerp(IconLoader.COULEUR_HDMI, Color.Black, 0.35f))
)

/** Bandeau NBA : fond graphite presque transparent, filet fin, textes gris clair. */
private val GraphiteNbaStyle = NbaStyle(
    largeur = GRAPHITE_NBA_LARGEUR,
    rayon = 14,
    fond = Color(0xFF15161C).copy(alpha = 0.6f),
    bordure = GraphiteFilet,
    texte = GraphiteTexte,
    texteDiscret = GraphiteTexte3,
    ongletActif = Color.White.copy(alpha = 0.09f),
    focusFond = GraphiteTexte,
    focusTexte = GraphiteToile,
    pastilleFond = Color.White.copy(alpha = 0.92f),
    echelle = GRAPHITE_NBA_ECHELLE,
)

/**
 * Design « Graphite » (Linear / Raycast / Arc) : fond graphite éclairé d'une lumière
 * violet → cyan venue du haut, grille fine, panneaux à filets de 1 px.
 *
 * Mise en page en deux volets, comme une fenêtre de logiciel pro :
 *  - à gauche, une colonne : salutation, horloge et date ; raccourcis empilés
 *    (Sources, Wi-Fi, Bluetooth, Réglages) ; bandeau NBA en bas ;
 *  - à droite, le volet principal : grand nom de l'élément sélectionné (avec sa rubrique),
 *    favoris, puis le dock « Mes apps » terminé par « Toutes les apps ».
 * Sélection : la lueur signature (anneau violet → cyan qui tourne + halo) sur chaque élément.
 */
@Composable
fun HomeGraphite(ctx: HomeContext) {
    // Titre affiché quand le focus n'est pas sur une app (raccourci, NBA, bibliothèque…)
    var titreLibre by remember { mutableStateOf("") }
    var sousTitreLibre by remember { mutableStateOf("") }
    val horsApp: (String, String) -> Unit = { nom, rubrique ->
        titreLibre = nom
        sousTitreLibre = rubrique
        ctx.sansSelection()
    }

    val app = ctx.selection
    val titre = app?.label ?: titreLibre
    val sousTitre = app?.let { AppCategories.rubrique(it).titre } ?: sousTitreLibre
    // Pastille de la couleur de l'app sélectionnée, violet sinon
    val teinte by animateColorAsState(
        if (app != null) ctx.couleur else GraphiteViolet, tween(300), label = "teinteGraphite",
    )

    val colonne = GRAPHITE_COLONNE.u
    val separation = GRAPHITE_MARGE.u + colonne + GRAPHITE_ECART_FILET.u

    Box(Modifier.fillMaxSize()) {
        GraphiteFond(separation)

        // --- Colonne de gauche (le bandeau NBA finit à 504, comme le dock)
        GraphiteColonne(
            ctx, horsApp,
            Modifier
                .padding(start = GRAPHITE_MARGE.u, top = 34.u, bottom = 36.u)
                .width(colonne)
                .fillMaxHeight(),
        )

        // --- Volet principal, calé en bas : la lumière du haut reste dégagée
        Column(
            Modifier
                .fillMaxSize()
                .padding(start = separation, top = 34.u, bottom = 20.u),
            verticalArrangement = Arrangement.Bottom,
        ) {
            GraphiteTitre(
                sousTitre, titre, teinte,
                Modifier.padding(start = GRAPHITE_RETRAIT.u, end = 36.u),
            )
            Spacer(Modifier.height(14.u))
            GraphiteFavoris(ctx, horsApp)
            Spacer(Modifier.height(24.u))
            GraphiteEnTete("Mes apps", Modifier.padding(start = GRAPHITE_RETRAIT.u, end = 36.u))
            Spacer(Modifier.height(2.u))
            GraphiteDock(ctx, horsApp)
        }
    }
}

// ---------------------------------------------------------------- fond

/**
 * Fond dessiné une seule fois (calque à part) : graphite, lumières violette / bleue / cyan
 * venues du haut, grille de 32 unités qui s'estompe, teinte de la colonne et filets.
 * Que des dégradés : aucun flou, aucune image.
 */
@Composable
private fun GraphiteFond(separation: Dp) {
    val pas = 32.u
    Box(
        Modifier
            .fillMaxSize()
            // Calque séparé : pas réenregistré quand le focus ou le titre changent
            .graphicsLayer { }
            .drawWithCache {
                val w = size.width
                val h = size.height
                val sep = separation.toPx()
                val p = pas.toPx()
                // Grille du volet principal : un seul tracé
                val grille = Path().apply {
                    var x = sep + p
                    while (x < w) { moveTo(x, 0f); lineTo(x, h); x += p }
                    var y = p
                    while (y < h) { moveTo(sep, y); lineTo(w, y); y += p }
                }
                val trait = Stroke(width = 1f)
                // La grille ne se voit qu'en haut, sous la lumière
                val masqueGrille = Brush.radialGradient(
                    0f to Color.White.copy(alpha = 0.07f),
                    0.5f to Color.White.copy(alpha = 0.028f),
                    1f to Color.Transparent,
                    center = Offset((sep + w) / 2f, -h * 0.05f),
                    radius = w * 0.6f,
                )
                val violet = Brush.radialGradient(
                    0f to Color(0xFF8B6CFF).copy(alpha = 0.34f),
                    0.4f to Color(0xFF6352F6).copy(alpha = 0.13f),
                    1f to Color.Transparent,
                    center = Offset(w * 0.64f, -h * 0.12f),
                    radius = w * 0.55f,
                )
                val cyan = Brush.radialGradient(
                    0f to GraphiteCyan.copy(alpha = 0.18f),
                    1f to Color.Transparent,
                    center = Offset(w * 0.98f, -h * 0.04f),
                    radius = w * 0.34f,
                )
                val bleu = Brush.radialGradient(
                    0f to Color(0xFF3B82F6).copy(alpha = 0.13f),
                    1f to Color.Transparent,
                    center = Offset(w * 0.18f, -h * 0.15f),
                    radius = w * 0.45f,
                )
                val reflet = Brush.radialGradient(
                    0f to Color(0xFF3B82F6).copy(alpha = 0.06f),
                    1f to Color.Transparent,
                    center = Offset(w * 0.78f, h * 1.15f),
                    radius = w * 0.5f,
                )
                // Colonne de gauche à peine plus claire, filet vertical façon barre latérale
                val fondColonne = Brush.verticalGradient(
                    listOf(Color.White.copy(alpha = 0.022f), Color.White.copy(alpha = 0.01f))
                )
                val filetColonne = Brush.verticalGradient(
                    0f to Color.Transparent,
                    0.12f to Color.White.copy(alpha = 0.08f),
                    0.88f to Color.White.copy(alpha = 0.08f),
                    1f to Color.Transparent,
                )
                // Rayon lumineux de 1 px tout en haut du volet principal
                val rayon = Brush.horizontalGradient(
                    0f to Color.Transparent,
                    0.35f to Color(0xFFA78BFA).copy(alpha = 0.6f),
                    0.75f to GraphiteCyan.copy(alpha = 0.55f),
                    1f to Color.Transparent,
                    startX = sep,
                    endX = w,
                )
                onDrawBehind {
                    drawRect(GraphiteToile)
                    drawRect(bleu)
                    drawRect(violet)
                    drawRect(cyan)
                    drawRect(reflet)
                    drawPath(grille, masqueGrille, style = trait)
                    drawRect(fondColonne, size = Size(sep, h))
                    drawLine(filetColonne, Offset(sep, 0f), Offset(sep, h), strokeWidth = 1f)
                    drawLine(rayon, Offset(sep, 0.5f), Offset(w, 0.5f), strokeWidth = 1f)
                }
            }
    )
}

/** Épaisseur d'un vrai pixel (filets fins). */
@Composable
private fun graphiteUnPixel(): Dp = with(LocalDensity.current) { 1.toDp() }

/** Panneau à filet : remplissage blanc 4 % → 1,8 %, contour de 1 px blanc 7,5 %. */
@Composable
private fun Modifier.graphitePanneau(rayon: Dp): Modifier {
    val forme = RoundedCornerShape(rayon)
    return this
        .background(GraphitePanneauFond, forme)
        .border(graphiteUnPixel(), GraphiteFilet, forme)
}

// ---------------------------------------------------------------- colonne de gauche

@Composable
private fun GraphiteColonne(ctx: HomeContext, horsApp: (String, String) -> Unit, modifier: Modifier) {
    val marge = 40.u
    Column(modifier) {
        GraphiteHorloge()
        Spacer(Modifier.height(16.u))
        GraphiteRaccourcis(ctx, horsApp)
        // Le bandeau NBA reste calé en bas de la colonne
        Spacer(Modifier.weight(1f))
        Spacer(Modifier.height(12.u))
        NbaPanel(
            data = ctx.nbaData,
            erreur = ctx.nbaErreur,
            repo = ctx.nbaRepo,
            modifier = Modifier
                // Focus dans le bandeau : le grand titre l'indique
                .onFocusChanged { if (it.hasFocus) horsApp("NBA", "Scores et horaires") }
                // Filet violet → cyan sur le bord haut
                .drawWithCache {
                    val m = marge.toPx()
                    val filet = Brush.horizontalGradient(
                        0f to Color.Transparent,
                        0.3f to Color(0xFFC4B5FD).copy(alpha = 0.9f),
                        0.7f to Color(0xFF67E8F9).copy(alpha = 0.8f),
                        1f to Color.Transparent,
                        startX = m,
                        endX = size.width - m,
                    )
                    onDrawWithContent {
                        drawContent()
                        drawLine(filet, Offset(m, 0.5f), Offset(size.width - m, 0.5f), strokeWidth = 1f)
                    }
                },
            onExit = ctx.sortieNba,
            style = GraphiteNbaStyle,
        )
    }
}

/** Salutation, horloge en dégradé blanc → gris, date. */
@Composable
private fun GraphiteHorloge() {
    val t = rememberMaintenant()
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(6.u).clip(CircleShape).background(GraphiteEtincelle))
            Spacer(Modifier.width(9.u))
            Text(salutation(t), color = GraphiteTexte2, fontSize = 14.us, fontWeight = FontWeight.Medium)
        }
        BasicText(
            heureTexte(t),
            style = TextStyle(
                brush = GraphiteDegradeHorloge,
                fontSize = 44.us,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = (-1).us,
            ),
            maxLines = 1,
        )
        Text(
            dateTexte(t).replaceFirstChar { it.titlecase(Locale.FRENCH) },
            color = GraphiteTexte2, fontSize = 14.us, maxLines = 1,
        )
    }
}

/** Raccourcis empilés dans un seul panneau, séparés par des filets (+ « Mise à jour »). */
@Composable
private fun GraphiteRaccourcis(ctx: HomeContext, horsApp: (String, String) -> Unit) {
    val trait = graphiteUnPixel()
    Column(
        Modifier
            .fillMaxWidth()
            .graphitePanneau(14.u)
            .padding(5.u)
    ) {
        if (ctx.majDisponible) {
            GraphiteRaccourci(
                icone = ProjoIcons.MiseAJour,
                libelle = "Mise à jour",
                onClick = ctx.actions.update,
                onLongClick = null,
                onFocus = { horsApp("Mise à jour", "Nouvelle version disponible") },
                accent = true,
            )
            GraphiteSeparateur(trait)
        }
        ctx.quickActions().forEachIndexed { i, qa ->
            if (i > 0) GraphiteSeparateur(trait)
            GraphiteRaccourci(
                icone = qa.icon,
                libelle = qa.label,
                onClick = qa.onClick,
                onLongClick = qa.onLongClick,
                onFocus = { horsApp(qa.label, "Raccourcis") },
                modifier = if (qa.estReglages) Modifier.focusRequester(ctx.focusReglages) else Modifier,
            )
        }
    }
}

@Composable
private fun GraphiteSeparateur(epaisseur: Dp) {
    Box(
        Modifier
            .padding(horizontal = 12.u, vertical = 2.u)
            .fillMaxWidth()
            .height(epaisseur)
            .background(Color.White.copy(alpha = 0.06f))
    )
}

/** Un raccourci (ligne icône + libellé) ; sélectionné : fond plus clair + lueur. */
@Composable
private fun GraphiteRaccourci(
    icone: ImageVector,
    libelle: String,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)?,
    onFocus: () -> Unit,
    modifier: Modifier = Modifier,
    accent: Boolean = false,
) {
    var focus by remember { mutableStateOf(false) }
    val zoom by animateFloatAsState(if (focus) 1f else 0f, tween(220), label = "zoomRaccourci")
    val rayon = 9.u
    val forme = RoundedCornerShape(rayon)
    Surface(
        onClick = onClick,
        onLongClick = onLongClick,
        modifier = modifier
            // Agrandissement géré ici pour que la lueur suive la ligne
            .graphicsLayer { val s = 1f + 0.02f * zoom; scaleX = s; scaleY = s }
            .lueurFocus(zoom, rayon, tourne = focus)
            .fillMaxWidth()
            .height(32.u)
            .onFocusChanged {
                focus = it.isFocused
                if (it.isFocused) onFocus()
            },
        shape = ClickableSurfaceDefaults.shape(forme),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = if (accent) GraphiteBleu.copy(alpha = 0.22f) else Color.Transparent,
            contentColor = if (accent) Color.White else GraphiteTexte.copy(alpha = 0.88f),
            focusedContainerColor = if (accent) GraphiteBleu.copy(alpha = 0.4f) else Color.White.copy(alpha = 0.09f),
            focusedContentColor = Color.White,
            pressedContainerColor = Color.White.copy(alpha = 0.14f),
            pressedContentColor = Color.White,
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f, pressedScale = 0.98f),
    ) {
        Row(
            Modifier
                .fillMaxSize()
                .padding(horizontal = 12.u),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(icone, contentDescription = null, modifier = Modifier.size(17.u))
            Spacer(Modifier.width(12.u))
            Text(
                libelle, fontSize = 14.us, fontWeight = FontWeight.Medium,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

// ---------------------------------------------------------------- volet principal

/** Rubrique (avec pastille de couleur) et grand nom de l'élément sélectionné, en fondu. */
@Composable
private fun GraphiteTitre(sousTitre: String, titre: String, teinte: Color, modifier: Modifier) {
    // Hauteur fixe : rien ne saute quand le titre change
    Box(
        modifier
            .fillMaxWidth()
            .height(96.u),
        contentAlignment = Alignment.BottomStart,
    ) {
        AnimatedContent(
            targetState = sousTitre to titre,
            transitionSpec = { fadeIn(tween(220)) togetherWith fadeOut(tween(160)) },
            contentAlignment = Alignment.BottomStart,
            label = "titreGraphite",
        ) { (sous, nom) ->
            Column {
                if (sous.isNotEmpty()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier
                                .size(8.u)
                                .clip(RoundedCornerShape(2.u))
                                .background(teinte)
                        )
                        Spacer(Modifier.width(10.u))
                        Text(
                            sous, color = GraphiteTexte2, fontSize = 14.us,
                            fontWeight = FontWeight.Medium, maxLines = 1,
                        )
                    }
                }
                // Toujours au moins aussi grand que l'horloge (44)
                val taille = when {
                    nom.length <= 12 -> 64.us
                    nom.length <= 16 -> 52.us
                    else -> 44.us
                }
                BasicText(
                    nom,
                    style = TextStyle(
                        brush = GraphiteDegradeTitre,
                        fontSize = taille,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = (-2).us,
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/** Titre de section suivi d'un filet qui s'efface. */
@Composable
private fun GraphiteEnTete(titre: String, modifier: Modifier) {
    val trait = graphiteUnPixel()
    Row(
        modifier
            .fillMaxWidth()
            .height(18.u),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(titre, color = GraphiteTexte2, fontSize = 13.us, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.width(14.u))
        Box(
            Modifier
                .weight(1f)
                .height(trait)
                .background(GraphiteFiletSection)
        )
    }
}

/** Rangée des favoris : vraies cartes d'apps (AppCard porte déjà la lueur en Graphite). */
// FocusRequester.Cancel est encore expérimental dans Compose 1.7
@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun GraphiteFavoris(ctx: HomeContext, horsApp: (String, String) -> Unit) {
    val ui = ctx.ui
    // Rien de focalisable au-dessus dans ce volet : ▲ ne part pas en biais vers la colonne
    val sansHaut = Modifier.focusProperties { up = FocusRequester.Cancel }
    LazyRow(
        state = ctx.rangeeFavoris,
        modifier = Modifier.fillMaxWidth(),
        // Marges verticales : la lueur de la carte sélectionnée a la place de s'étendre
        contentPadding = PaddingValues(start = GRAPHITE_RETRAIT.u, end = 48.u, top = 16.u, bottom = 16.u),
        horizontalArrangement = Arrangement.spacedBy(18.u),
    ) {
        if (ui.loaded && ui.favorites.isEmpty()) {
            item(key = "aide") {
                TextCard(
                    text = "Aucun favori.\nMaintiens OK sur une app pour l'ajouter.",
                    width = 280.u, height = GRAPHITE_FAV_H.u,
                    onClick = {},
                    onFocused = { horsApp("Favoris", "Accueil") },
                    modifier = sansHaut.focusRequester(ctx.premierFocus),
                )
            }
        }
        itemsIndexed(ui.favorites, key = { _, a -> "fav:" + a.packageName }) { i, app ->
            AppCard(
                app = app, icons = ctx.icons, iconPackage = ui.iconSourceOf(app.packageName),
                width = GRAPHITE_FAV_L.u, height = GRAPHITE_FAV_H.u, showLabel = false,
                onFocused = ctx.surFocus,
                onClick = { ctx.actions.launch(app) },
                onMenu = { ctx.actions.openMenu(app) },
                modifier = if (i == 0) sansHaut.focusRequester(ctx.premierFocus) else sansHaut,
            )
        }
    }
}

/** Dock « Mes apps » : tuiles séparées (icône en couleur + nom), puis « Toutes les apps ». */
@Composable
private fun GraphiteDock(ctx: HomeContext, horsApp: (String, String) -> Unit) {
    val ui = ctx.ui
    LazyRow(
        state = ctx.rangeeApps,
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(start = GRAPHITE_RETRAIT.u, end = 48.u, top = 16.u, bottom = 16.u),
        horizontalArrangement = Arrangement.spacedBy(14.u),
    ) {
        items(ui.homeApps, key = { "app:" + it.packageName }) { app ->
            GraphiteTuileApp(app, ctx)
        }
        // Bibliothèque : toutes les apps installées
        item(key = "bibliotheque") {
            GraphiteTuileBibliotheque(
                nombre = ui.allApps.size,
                onClick = ctx.actions.openLibrary,
                onFocus = { horsApp("Toutes les apps", "Bibliothèque") },
                modifier = Modifier.focusRequester(ctx.focusBiblio),
            )
        }
    }
}

/**
 * Tuile du dock : fond un peu plus clair que la toile, filet de 1 px ;
 * sélectionnée : elle grandit (1.08x) avec la lueur signature.
 */
@Composable
private fun GraphiteTuile(
    largeur: Dp,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)?,
    onFocus: () -> Unit,
    modifier: Modifier = Modifier,
    contenu: @Composable () -> Unit,
) {
    var focus by remember { mutableStateOf(false) }
    val zoom by animateFloatAsState(if (focus) 1f else 0f, tween(220), label = "zoomTuile")
    val rayon = 14.u
    val forme = RoundedCornerShape(rayon)
    val trait = graphiteUnPixel()
    Surface(
        onClick = onClick,
        onLongClick = onLongClick,
        modifier = modifier
            .graphicsLayer { val s = 1f + 0.08f * zoom; scaleX = s; scaleY = s }
            .lueurFocus(zoom, rayon, tourne = focus)
            .size(largeur, GRAPHITE_DOCK_H.u)
            .onFocusChanged {
                focus = it.isFocused
                if (it.isFocused) onFocus()
            }
            // Touche « Menu » de la télécommande = même menu que l'appui long
            .onPreviewKeyEvent {
                if (onLongClick != null && it.key == Key.Menu && it.type == KeyEventType.KeyUp) {
                    onLongClick()
                    true
                } else {
                    false
                }
            },
        shape = ClickableSurfaceDefaults.shape(forme),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color.White.copy(alpha = 0.055f),
            contentColor = GraphiteTexte.copy(alpha = 0.9f),
            focusedContainerColor = Color.White.copy(alpha = 0.1f),
            focusedContentColor = Color.White,
            pressedContainerColor = Color.White.copy(alpha = 0.14f),
            pressedContentColor = Color.White,
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f, pressedScale = 0.97f),
        border = ClickableSurfaceDefaults.border(
            border = Border(BorderStroke(trait, GraphiteFilet), shape = forme),
            focusedBorder = Border(BorderStroke(trait, Color.White.copy(alpha = 0.25f)), shape = forme),
        ),
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .padding(horizontal = 8.u, vertical = 11.u),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            contenu()
        }
    }
}

/** App du dock : vraie icône en couleur (logo remplacé respecté) et son nom. */
@Composable
private fun GraphiteTuileApp(app: AppInfo, ctx: HomeContext) {
    val paquet = ctx.ui.iconSourceOf(app.packageName)
    // Icône carrée arrondie (46 u) : visuel carré de l'app et sa couleur
    val visuel = rememberAppVisual(paquet, ctx.icons, FormatVisuel.CARRE)
    val icone = visuel?.art
    val couleur = visuel?.color ?: IconLoader.COULEUR_PAR_DEFAUT
    GraphiteTuile(
        largeur = GRAPHITE_DOCK_L.u,
        onClick = { ctx.actions.launch(app) },
        onLongClick = { ctx.actions.openMenu(app) },
        onFocus = { ctx.surFocus(app, couleur) },
    ) {
        GraphiteIcone(app, icone, couleur)
        Text(
            app.label, fontSize = 13.us, fontWeight = FontWeight.Medium,
            maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center,
        )
    }
}

/** Icône carrée arrondie : HDMI dessinée, sinon l'icône de l'app, sinon son initiale. */
@Composable
private fun GraphiteIcone(app: AppInfo, icone: ImageBitmap?, couleur: Color) {
    val taille = 46.u
    val forme = RoundedCornerShape(12.u)
    when {
        app.isSpecial -> Box(
            Modifier
                .size(taille)
                .clip(forme)
                .background(GraphiteDegradeHdmi),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                ProjoIcons.Sources, contentDescription = app.label, tint = Color.White,
                modifier = Modifier.size(25.u),
            )
        }
        icone != null -> Image(
            bitmap = icone,
            contentDescription = app.label,
            filterQuality = FilterQuality.Medium,
            modifier = Modifier
                .size(taille)
                .clip(forme),
        )
        else -> Box(
            Modifier
                .size(taille)
                .clip(forme)
                .background(lerp(couleur, Color.Black, 0.4f)),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                app.label.take(1).uppercase(), color = Color.White,
                fontSize = 20.us, fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

/** « Toutes les apps (N) » : ouvre la bibliothèque ; nombre en chiffres à chasse fixe. */
@Composable
private fun GraphiteTuileBibliotheque(
    nombre: Int,
    onClick: () -> Unit,
    onFocus: () -> Unit,
    modifier: Modifier,
) {
    val trait = graphiteUnPixel()
    val forme = RoundedCornerShape(12.u)
    GraphiteTuile(
        largeur = 164.u,
        onClick = onClick,
        onLongClick = null,
        onFocus = onFocus,
        modifier = modifier,
    ) {
        Box(
            Modifier
                .size(46.u)
                .clip(forme)
                .background(Color.White.copy(alpha = 0.08f))
                .border(trait, Color.White.copy(alpha = 0.15f), forme),
            contentAlignment = Alignment.Center,
        ) {
            Icon(ProjoIcons.Apps, contentDescription = null, modifier = Modifier.size(24.u))
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Toutes les apps", fontSize = 13.us, fontWeight = FontWeight.Medium, maxLines = 1)
            Spacer(Modifier.width(5.u))
            Text(
                "($nombre)", color = GraphiteTexte3, fontSize = 12.us,
                fontFamily = FontFamily.Monospace, maxLines = 1,
            )
        }
    }
}
