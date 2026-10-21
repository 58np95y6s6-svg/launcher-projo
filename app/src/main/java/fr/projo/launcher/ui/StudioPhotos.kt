package fr.projo.launcher.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Text
import fr.projo.launcher.data.AppInfo
import fr.projo.launcher.data.ReglageNouveautes
import fr.projo.launcher.modules.ligne
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.withContext

/*
 * Arrière-plan photo de « Studio » (écran 1) : la photo apparaît en fondu sur le GRAND FOND
 * clair, en haut à droite, au-dessus des petites cartes, et se fond dans le fond vers la
 * gauche (« Pour vous ») et vers le bas (titres des cartes, barre des apps).
 *  - app de la barre avec catalogue (Netflix, Prime Video…) : ses nouveautés TMDB, trois en
 *    alternance toutes les 12 s (chargeur partagé de UnePhotos.kt) ;
 *  - suggestion sélectionnée : l'image de CE programme, celle que la carte a déjà chargée
 *    (aucun téléchargement ni décodage de plus), façon « héros » ;
 *  - sinon (autre app, raccourci, écran 2, sans clé, hors ligne, réglage coupé) : le fond
 *    clair ne change pas. Retour en fondu.
 * Rendu clair : photo mate (noirs relevés, blancs sous le blanc du fond : rien n'éblouit),
 * un peu désaturée, fondue par un masque (calque à part, aucun flou). En haut à droite, une
 * ombre douce posée sur la photo seule (vignettage) : les pastilles blanches, l'heure et la
 * légende, passées au blanc pendant le fondu, restent lisibles.
 * Mémoire : au plus deux images décodées pour les nouveautés (à la largeur de la zone),
 * celles des suggestions sont partagées avec les cartes.
 */

/** Zone de la photo, en unités .u : bord gauche, largeur (16:9) et haut (un peu au-dessus de l'écran). */
private const val P_GAUCHE = 346f
internal const val P_LARGEUR = 614
private const val P_HAUT = -48f

/** Fondu vers la gauche : transparent en P_GAUCHE, pleine photo à partir d'ici. */
private const val P_PLEINE_X = 640f

/** Fondu vers le bas : pleine photo jusqu'ici, plus rien à P_FIN_Y (au-dessus des titres des cartes). */
private const val P_PLEINE_Y = 132f
private const val P_FIN_Y = 300f

/** Ombre sous les pastilles, l'heure et la légende (centre, demi-largeur, demi-hauteur). */
private const val O_X = 910f
private const val O_Y = 50f
private const val O_RX = 340f
private const val O_RY = 150f

/** Suggestion sélectionnée et son image (déjà chargée par la carte). */
internal class HerosStudio(val cle: String, val image: ImageBitmap)

/** Ce qui est sélectionné sur l'écran 1 : une app (barre, carte d'app) ou une suggestion. */
internal class SelectionFondStudio {
    var app by mutableStateOf<AppInfo?>(null)
        private set
    var heros by mutableStateOf<HerosStudio?>(null)
        private set

    /** Une app de la barre (ou une carte d'app) prend ou perd le focus. */
    fun app(a: AppInfo, focus: Boolean) {
        if (focus) {
            app = a
            heros = null
        } else if (app?.packageName == a.packageName) {
            app = null
        }
    }

    /** Une carte de suggestion prend ou perd le focus ; [image] : null tant qu'elle charge. */
    fun suggestion(cle: String, image: ImageBitmap?, focus: Boolean) {
        if (focus) {
            heros = image?.let { HerosStudio(cle, it) }
            app = null
        } else if (heros?.cle == cle) {
            heros = null
        }
    }
}

/** Photos affichées : nouveautés de l'app sélectionnée et héros de la suggestion sélectionnée. */
internal class FondStudio(val apps: PhotosUne) {
    val heros = PhotosUne()

    /** Suggestion dont l'image est affichée (null : aucune). */
    var cleHeros by mutableStateOf<String?>(null)
        private set

    suspend fun versHeros(h: HerosStudio?) {
        if (h != null) cleHeros = h.cle
        heros.vers(h?.let { PhotoUne(it.image, "") }, if (h == null) 350 else 450)
        if (h == null) cleHeros = null
    }
}

/**
 * Photos de l'écran 1 selon [selection], seulement quand [ecran1] (page en haut) : 150 ms
 * d'immobilité avant de changer (flèche maintenue : pas de chargements ni de fondus en rafale).
 * [largeurPx] : largeur de décodage des nouveautés (celle de la zone, 1280 px au plus).
 */
@Composable
internal fun rememberFondStudio(selection: SelectionFondStudio, largeurPx: Int, ecran1: () -> Boolean): FondStudio {
    val surEcran1 = rememberUpdatedState(ecran1)
    // Nouveautés de l'app : chargeur d'« À la une » (alternance, fondus, deux images au plus)
    var app by remember { mutableStateOf<AppInfo?>(null) }
    LaunchedEffect(selection) {
        snapshotFlow { selection.app?.takeIf { surEcran1.value() } }
            .distinctUntilChanged()
            .collectLatest { a ->
                if (app != null) delay(150)
                app = a
            }
    }
    val apps = rememberPhotosUne(app, largeurPx)
    val fond = remember(apps) { FondStudio(apps) }
    // Héros : chaque fondu va jusqu'au bout (pas de saut d'opacité), puis la dernière sélection
    LaunchedEffect(fond, selection) {
        snapshotFlow { selection.heros?.takeIf { surEcran1.value() && ReglageNouveautes.actif } }
            .distinctUntilChanged()
            .collectLatest { h ->
                delay(150)
                withContext(NonCancellable) { fond.versHeros(h) }
            }
    }
    return fond
}

/** Photo mate pour un design clair : désaturée (78 %), noirs relevés, blancs un peu sous le fond. */
internal fun filtrePhotosStudio(): ColorFilter {
    val k = 0.84f
    val s = 0.78f
    val lr = 0.213f * (1f - s)
    val lg = 0.715f * (1f - s)
    val lb = 0.072f * (1f - s)
    // Noirs relevés vers le gris bleuté du fond (0..255)
    val r0 = 26f
    val g0 = 27f
    val b0 = 31f
    return ColorFilter.colorMatrix(
        ColorMatrix(
            floatArrayOf(
                k * (lr + s), k * lg, k * lb, 0f, r0,
                k * lr, k * (lg + s), k * lb, 0f, g0,
                k * lr, k * lg, k * (lb + s), 0f, b0,
                0f, 0f, 0f, 1f, 0f,
            )
        )
    )
}

/** Dessine [image] pour couvrir [zone] (recadrée au centre). */
private fun DrawScope.photoStudio(image: ImageBitmap, zone: Rect, alpha: Float, filtre: ColorFilter) {
    if (alpha <= 0f) return
    val s = maxOf(zone.width / image.width, zone.height / image.height)
    val sw = (zone.width / s).toInt().coerceIn(1, image.width)
    val sh = (zone.height / s).toInt().coerceIn(1, image.height)
    drawImage(
        image,
        srcOffset = IntOffset((image.width - sw) / 2, (image.height - sh) / 2),
        srcSize = IntSize(sw, sh),
        dstOffset = IntOffset(zone.left.toInt(), zone.top.toInt()),
        dstSize = IntSize(zone.width.toInt(), zone.height.toInt()),
        alpha = alpha,
        colorFilter = filtre,
        filterQuality = FilterQuality.Medium,
    )
}

/** Présence (0 → 1) d'une couche de photos. */
private fun presenceCouche(p: PhotosUne): Float {
    val f = p.fondu.value
    return when {
        p.courante != null && p.precedente != null -> 1f
        p.courante != null -> f
        p.precedente != null -> 1f - f
        else -> 0f
    }
}

/** Une couche : la photo précédente sous la courante qui arrive (ou qui s'efface seule). */
private fun DrawScope.couche(p: PhotosUne, zone: Rect, filtre: ColorFilter) {
    val f = p.fondu.value
    val cour = p.courante
    p.precedente?.let { photoStudio(it.image, zone, if (cour == null) 1f - f else 1f, filtre) }
    cour?.let { photoStudio(it.image, zone, f, filtre) }
}

/**
 * Arrière-plan photo de l'écran 1, sous l'en-tête et les cartes (à poser sur toute la largeur,
 * en haut de l'écran 1). Rien n'est composé sans photo. Les fondus ne recomposent rien : la
 * progression n'est lue qu'au dessin, dans un calque à part (le masque n'efface que la photo).
 */
@Composable
internal fun FondPhotosStudio(fond: FondStudio, modifier: Modifier) {
    val a = fond.apps
    val h = fond.heros
    if (a.courante == null && a.precedente == null && h.courante == null && h.precedente == null) return
    val k = with(LocalDensity.current) { 1.u.toPx() }
    val filtre = remember { filtrePhotosStudio() }
    Box(
        modifier
            .testTag("studio:fond-photo")
            .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
            .drawWithCache {
                val zone = Rect(P_GAUCHE * k, P_HAUT * k, (P_GAUCHE + P_LARGEUR) * k, (P_HAUT + P_LARGEUR * 9f / 16f) * k)
                // Courbes douces (ease) : aucun bord ne se devine
                val gauche = Brush.horizontalGradient(
                    0f to Color.Transparent, 0.25f to Color.Black.copy(alpha = 0.1f), 0.5f to Color.Black.copy(alpha = 0.42f),
                    0.75f to Color.Black.copy(alpha = 0.8f), 1f to Color.Black,
                    startX = P_GAUCHE * k, endX = P_PLEINE_X * k,
                )
                val bas = Brush.verticalGradient(
                    0f to Color.Black, 0.25f to Color.Black.copy(alpha = 0.86f), 0.5f to Color.Black.copy(alpha = 0.52f),
                    0.75f to Color.Black.copy(alpha = 0.16f), 1f to Color.Transparent,
                    startY = P_PLEINE_Y * k, endY = P_FIN_Y * k,
                )
                val centreOmbre = Offset(O_X * k, O_Y * k)
                val ombre = Brush.radialGradient(
                    0f to Color.Black.copy(alpha = 0.5f), 0.4f to Color.Black.copy(alpha = 0.44f),
                    0.7f to Color.Black.copy(alpha = 0.22f), 1f to Color.Transparent,
                    center = centreOmbre, radius = O_RY * k,
                )
                onDrawBehind {
                    couche(a, zone, filtre)
                    couche(h, zone, filtre)
                    // Ombre douce (ellipse sans flou) sur la photo seule (SrcAtop : jamais sur le fond)
                    scale(O_RX / O_RY, 1f, pivot = centreOmbre) {
                        drawCircle(ombre, radius = O_RY * k, center = centreOmbre, blendMode = BlendMode.SrcAtop)
                    }
                    drawRect(gauche, blendMode = BlendMode.DstIn)
                    drawRect(bas, blendMode = BlendMode.DstIn)
                }
            },
    )
    // Repère des tests : programme affiché en héros
    val cle = fond.cleHeros
    if (h.courante != null && cle != null) Box(Modifier.size(1.dp).testTag("studio:heros:$cle"))
}

/** Légende de la nouveauté affichée (« Nouveau sur Netflix · The Storm »), sous l'heure, sur la photo. */
@Composable
internal fun LegendePhotosStudio(fond: FondStudio, modifier: Modifier) {
    val p = fond.apps
    val photo = p.courante ?: p.precedente ?: return
    val entrante = p.courante != null
    Text(
        photo.legende, color = Color.White.copy(alpha = 0.86f), fontSize = 10.us, fontWeight = FontWeight.SemiBold,
        style = ligne(13.us), maxLines = 1, overflow = TextOverflow.Ellipsis,
        modifier = modifier
            .testTag(if (entrante) "studio:photo" else "studio:photo-sortante")
            .widthIn(max = 320.u)
            .graphicsLayer { alpha = if (entrante) p.fondu.value else 1f - p.fondu.value },
    )
}

/** Présence (0 → 1) d'une photo sur le fond : encre de l'heure (lue au dessin seulement). */
internal fun FondStudio.presence(): Float = 1f - (1f - presenceCouche(apps)) * (1f - presenceCouche(heros))

/** Une nouveauté est-elle affichée (pour l'attribution TMDB, même sans suggestions) ? */
internal fun FondStudio.nouveauteAffichee(): Boolean = apps.courante != null || apps.precedente != null
