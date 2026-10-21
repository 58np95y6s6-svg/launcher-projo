package fr.projo.launcher.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.em
import androidx.tv.material3.Text
import fr.projo.launcher.data.AppInfo
import fr.projo.launcher.data.CleTmdb
import fr.projo.launcher.data.Nouveautes
import fr.projo.launcher.data.ReglageNouveautes
import fr.projo.launcher.data.TitreTmdb
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/*
 * Arrière-plan photo d'« À la une » : quand l'app sélectionnée est un service de streaming
 * (Nouveautes.aUnCatalogue), ses dernières nouveautés (TMDB, jusqu'à 3) défilent derrière
 * l'affiche, à droite, fondues dans l'aplat calibré, assombries et teintées : le texte blanc
 * (heure, score, nom géant) reste lisible et rien n'éblouit. Aucun flou : un filtre de couleur
 * (matrice) et des dégradés de la couleur de l'aplat.
 * Mémoire : au plus deux images décodées (affichée et suivante, ou précédente pendant un fondu),
 * à la largeur de la zone (1280 px au plus), sans cache mémoire (le cache disque de Tmdb.kt sert).
 */

/** Mode Soir imposé (tests et captures) ; null : selon l'heure. */
internal object SoirUne {
    var impose: Boolean? = null
}

/** Une photo de l'affiche : image décodée et légende (« Nouveau sur Netflix · The Storm »). */
internal class PhotoUne(val image: ImageBitmap, val legende: String)

/** État de l'arrière-plan : photo affichée, photo qui s'efface, progression du fondu. */
internal class PhotosUne {
    var precedente by mutableStateOf<PhotoUne?>(null)
        private set
    var courante by mutableStateOf<PhotoUne?>(null)
        private set

    /** 0 → 1 : la photo courante remplace la précédente (ou l'aplat seul). */
    val fondu = Animatable(1f)

    /** Termine un fondu en cours et lâche la photo précédente (jamais plus de deux images). */
    suspend fun terminer() {
        fondu.snapTo(1f)
        precedente = null
    }

    /** Fondu de [duree] ms vers [nouvelle] (null : retour à l'aplat seul). */
    suspend fun vers(nouvelle: PhotoUne?, duree: Int) {
        terminer()
        if (nouvelle == null && courante == null) return
        precedente = courante
        courante = nouvelle
        fondu.snapTo(0f)
        fondu.animateTo(1f, tween(duree, easing = LinearOutSlowInEasing))
        precedente = null
    }
}

/**
 * Photos de l'app [app] (déjà retenue après 150 ms d'immobilité), décodées à [largeurPx] :
 * fondu de 350 ms à l'arrivée, puis une nouveauté suivante toutes les 12 s (fondu de 600 ms),
 * préchargée pendant l'attente. Rien si le réglage « Affiches des nouveautés » est désactivé,
 * sans clé TMDB, hors ligne ou pour une app sans catalogue.
 */
@Composable
internal fun rememberPhotosUne(app: AppInfo?, largeurPx: Int): PhotosUne {
    val photos = remember { PhotosUne() }
    val contexte = LocalContext.current
    LaunchedEffect(Unit) { ReglageNouveautes.charger(contexte) }
    val cible = app?.takeIf { ReglageNouveautes.actif && Nouveautes.aUnCatalogue(it) }
    val version = CleTmdb.version
    LaunchedEffect(cible?.packageName, version, largeurPx) {
        photos.terminer()
        if (cible == null) {
            photos.vers(null, 350)
            return@LaunchedEffect
        }
        val titres = Nouveautes.titresRecents(contexte, cible, 3).orEmpty()
        suspend fun charger(t: TitreTmdb): PhotoUne? = withContext(Dispatchers.IO) {
            Nouveautes.photo(contexte, t.image, largeurPx)?.let { PhotoUne(it, "Nouveau sur ${cible.label} · ${t.titre}") }
        }
        var suivante = titres.firstOrNull()?.let { charger(it) }
        if (suivante == null) {
            photos.vers(null, 350)
            return@LaunchedEffect
        }
        var duree = 350
        var i = 0
        while (true) {
            suivante?.let { photos.vers(it, duree) }
            duree = 600
            if (titres.size < 2) return@LaunchedEffect
            i = (i + 1) % titres.size
            // Préchargée pendant l'attente : affichée + suivante = deux images au plus
            suivante = charger(titres[i])
            delay(12_000)
        }
    }
    return photos
}

/** Assombrit (×0,58 le jour, ×0,44 le soir) et désature (70 %) la photo. */
internal fun filtrePhotosUne(soir: Boolean): ColorFilter {
    val k = if (soir) 0.44f else 0.58f
    val s = 0.7f
    val lr = 0.213f * (1f - s)
    val lg = 0.715f * (1f - s)
    val lb = 0.072f * (1f - s)
    return ColorFilter.colorMatrix(
        ColorMatrix(
            floatArrayOf(
                k * (lr + s), k * lg, k * lb, 0f, 0f,
                k * lr, k * (lg + s), k * lb, 0f, 0f,
                k * lr, k * lg, k * (lb + s), 0f, 0f,
                0f, 0f, 0f, 1f, 0f,
            )
        )
    )
}

/** Dessine [image] pour couvrir [zone] (recadrée au centre, un peu vers le haut). */
private fun DrawScope.photoUne(image: ImageBitmap, zone: Rect, alpha: Float, filtre: ColorFilter) {
    val s = maxOf(zone.width / image.width, zone.height / image.height)
    val sw = (zone.width / s).toInt().coerceIn(1, image.width)
    val sh = (zone.height / s).toInt().coerceIn(1, image.height)
    drawImage(
        image,
        srcOffset = IntOffset(((image.width - sw) / 2f).toInt(), ((image.height - sh) * 0.3f).toInt()),
        srcSize = IntSize(sw, sh),
        dstOffset = IntOffset(zone.left.toInt(), zone.top.toInt()),
        dstSize = IntSize(zone.width.toInt(), zone.height.toInt()),
        alpha = alpha,
        colorFilter = filtre,
        filterQuality = FilterQuality.Medium,
    )
}

/**
 * Photos derrière l'affiche (après l'aplat, avant le voile) : sur les 64 % de droite, teintées
 * de la couleur de l'aplat, fondues vers l'aplat à gauche (salutation, heure, nom géant) et en
 * bas (bande d'index), légère ombre en haut (pilules, score).
 */
internal fun DrawScope.dessinerPhotosUne(p: PhotosUne, aplat: Color, soir: Boolean, filtre: ColorFilter) {
    val cour = p.courante
    val prec = p.precedente
    if (cour == null && prec == null) return
    val f = p.fondu.value
    val w = size.width
    val h = size.height
    val zone = Rect(w * 0.36f, 0f, w, h)
    // Présence des photos (pour l'ombre noire : les couches de la couleur de l'aplat sont invisibles sur l'aplat)
    val presence = when {
        prec == null -> f
        cour == null -> 1f - f
        else -> 1f
    }
    if (prec != null) photoUne(prec.image, zone, if (cour == null) 1f - f else 1f, filtre)
    if (cour != null) photoUne(cour.image, zone, f, filtre)
    // Teinte de l'aplat
    drawRect(aplat, topLeft = zone.topLeft, size = zone.size, alpha = if (soir) 0.36f else 0.28f)
    // Fondu vers l'aplat, à gauche puis en bas
    drawRect(
        Brush.horizontalGradient(
            0f to aplat, 0.45f to aplat.copy(alpha = 0.6f), 1f to aplat.copy(alpha = 0f),
            startX = w * 0.36f, endX = w * 0.64f,
        ),
        topLeft = zone.topLeft, size = zone.size,
    )
    drawRect(
        Brush.verticalGradient(
            0f to aplat.copy(alpha = 0f), 0.55f to aplat.copy(alpha = 0.55f), 1f to aplat,
            startY = h * 0.6f, endY = h,
        ),
        topLeft = Offset(zone.left, h * 0.6f), size = androidx.compose.ui.geometry.Size(zone.width, h * 0.4f),
    )
    // Ombre douce en haut (pilules et score NBA par-dessus)
    drawRect(
        Brush.verticalGradient(
            0f to Color.Black.copy(alpha = 0.3f * presence), 1f to Color.Black.copy(alpha = 0f),
            startY = 0f, endY = h * 0.5f,
        ),
        topLeft = zone.topLeft, size = androidx.compose.ui.geometry.Size(zone.width, h * 0.5f),
    )
}

/** Légende discrète de la photo affichée, et l'attribution TMDB obligatoire. */
@Composable
internal fun LegendePhotosUne(p: PhotosUne, modifier: Modifier) {
    val photo = p.courante ?: p.precedente ?: return
    val entrante = p.courante != null
    Row(
        modifier
            .testTag(if (entrante) "une-photo" else "une-photo-sortante")
            .graphicsLayer { alpha = if (entrante) p.fondu.value else 1f - p.fondu.value },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            photo.legende, color = Color.White.copy(alpha = 0.78f), fontSize = 11.us, fontWeight = FontWeight.SemiBold,
            fontFamily = UnePolice, letterSpacing = 0.01.em, maxLines = 1, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.widthIn(max = 420.u),
        )
        Text(
            "  ·  Données : TMDB", color = Color.White.copy(alpha = 0.5f), fontSize = 10.us, fontWeight = FontWeight.Medium,
            fontFamily = UnePolice, maxLines = 1,
        )
    }
}
