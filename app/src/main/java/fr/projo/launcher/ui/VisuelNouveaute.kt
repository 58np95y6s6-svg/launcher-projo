package fr.projo.launcher.ui

import android.graphics.Bitmap
import android.util.LruCache
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import fr.projo.launcher.data.AppInfo
import fr.projo.launcher.data.AppVisual
import fr.projo.launcher.data.CleTmdb
import fr.projo.launcher.data.FormatVisuel
import fr.projo.launcher.data.IconLoader
import fr.projo.launcher.data.Illustrations
import fr.projo.launcher.data.Nouveaute
import fr.projo.launcher.data.Nouveautes
import fr.projo.launcher.data.ReglageNouveautes
import fr.projo.launcher.data.ServicesTmdb
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.ceil

/*
 * Carte « nouveauté » partagée par les designs : la photo du dernier film ou de la dernière
 * série sortis sur le service de l'app (data/Nouveautes.kt), stylisée aux couleurs de l'app,
 * avec le logo de l'app en petit. Aucun flou : dégradés, formes simples et, pour le duotone,
 * une image recolorée calculée une fois puis mise en cache.
 *
 * Utilisation dans un design :
 *     VisuelNouveaute(app, ctx.icons, ctx.ui.iconSourceOf(app.packageName), FormeNouveaute.CARRE, 100.u) {
 *         // repli : l'illustration actuelle (pas un service, pas de clé, pas de réseau, réglage désactivé)
 *     }
 * Le titre (pour une ligne « Nouveau : … ») : rememberNouveaute(app, largeurImageNouveaute(100.u, CARRE)).
 */

/** Traitement de la photo. */
enum class StyleNouveaute {
    /** La photo se fond en dégradé dans la couleur de l'app ; « Nouveau » et le titre dessous. */
    FONDU,

    /** La photo est découpée par une forme (grand disque, bande inclinée) sur un aplat de la couleur de l'app. */
    DECOUPE,

    /** La photo est recolorée en deux tons : noir teinté et couleur de l'app. */
    DUOTONE,
}

/** Forme de la carte : 16:9 ou carrée. */
enum class FormeNouveaute { BANNIERE, CARRE }

object StylesNouveautes {
    /** Traitement retenu pour tous les designs (à changer ici une fois choisi). */
    val PAR_DEFAUT = StyleNouveaute.FONDU

    /** Traitement affiché (les captures de comparaison le changent). */
    var courant by mutableStateOf(PAR_DEFAUT)
}

/** Couleurs de marque profondes des services (le texte blanc reste lisible dessus). */
private val MarquesNouveautes: Map<String, Color> = mapOf(
    "netflix" to Color(0xFFC20812),
    "primevideo" to Color(0xFF0C2340),
    "disneyplus" to Color(0xFF0B1C52),
    "canalplus" to Color(0xFF151515),
    "appletv" to Color(0xFF1C1C1E),
    "max" to Color(0xFF0B26B8),
    "paramountplus" to Color(0xFF0A3FA8),
    "crunchyroll" to Color(0xFFC85A10),
    "francetv" to Color(0xFF1B1464),
    "arte" to Color(0xFFB8340E),
    "molotov" to Color(0xFF1A1A1A),
)

/**
 * Couleur de l'app pour le fondu, l'aplat et le duotone : la marque connue, sinon la couleur
 * de l'illustration ou de l'icône, assombrie si elle est trop claire.
 */
fun couleurNouveaute(app: AppInfo, extraite: Color): Color {
    val id = ServicesTmdb.idDe(app)
    MarquesNouveautes[id]?.let { return it }
    val regle = Illustrations.parPaquet(app.packageName) ?: Illustrations.parLibelle(app.label)
    val base = (regle?.couleur?.let { Color(it) } ?: extraite).copy(alpha = 1f)
    val l = base.luminance()
    return if (l > 0.18f) lerp(base, Color.Black, ((l - 0.18f) / l).coerceIn(0f, 0.75f)) else base
}

/** Largeur (px) à laquelle décoder la photo pour une carte de [largeur] : multiple de 100, 200 à 780. */
@Composable
fun largeurImageNouveaute(largeur: Dp, forme: FormeNouveaute): Int {
    val px = with(LocalDensity.current) { largeur.toPx() }
    // Carré : la photo 16:9 doit couvrir toute la hauteur
    val utile = if (forme == FormeNouveaute.CARRE) px * 16f / 9f else px
    return (ceil(utile / 100f).toInt() * 100).coerceIn(200, 780)
}

/**
 * Nouveauté du service de [app] (chargée hors du thread principal, cache partagé), ou null :
 * réglage désactivé, pas un service, pas de clé TMDB, pas de réseau.
 */
@Composable
fun rememberNouveaute(app: AppInfo, largeurPx: Int): Nouveaute? {
    val contexte = LocalContext.current
    LaunchedEffect(Unit) { ReglageNouveautes.charger(contexte) }
    if (!ReglageNouveautes.actif || !Nouveautes.aUnCatalogue(app)) return null
    val version = CleTmdb.version
    val n by produceState(Nouveautes.enCache(app, largeurPx), app.packageName, largeurPx, version) {
        value = Nouveautes.charger(contexte, app, largeurPx)
    }
    return n
}

/**
 * Visuel « nouveauté » de [app] : [forme] de [largeur] (hauteur 9/16 ou carrée), traitement
 * [style], logo de l'app ([paquetIcone] : son visuel CARRÉ) dans un coin. Sans nouveauté :
 * [repli] (l'illustration actuelle), à la même taille.
 * [legende] : en FONDU, « Nouveau » et le titre sont écrits sur la zone de couleur.
 */
@Composable
fun VisuelNouveaute(
    app: AppInfo,
    icons: IconLoader,
    paquetIcone: String,
    forme: FormeNouveaute,
    largeur: Dp,
    modifier: Modifier = Modifier,
    rayon: Dp = 6.u,
    style: StyleNouveaute = StylesNouveautes.courant,
    legende: Boolean = true,
    nouveaute: Nouveaute? = rememberNouveaute(app, largeurImageNouveaute(largeur, forme)),
    repli: @Composable () -> Unit,
) {
    val hauteur = if (forme == FormeNouveaute.CARRE) largeur else largeur * (9f / 16f)
    val n = nouveaute
    if (n == null) {
        Box(modifier.size(largeur, hauteur)) { repli() }
        return
    }
    val visuel = rememberAppVisual(paquetIcone, icons, FormatVisuel.CARRE)
    val marque = remember(app.packageName, visuel?.color) {
        couleurNouveaute(app, visuel?.color ?: IconLoader.COULEUR_PAR_DEFAUT)
    }
    val carre = forme == FormeNouveaute.CARRE
    // Tailles proportionnelles à la carte (100.u : carte carrée de référence)
    val r = largeur / (if (carre) 100.u else 178.u)
    val k = with(LocalDensity.current) { 1.u.toPx() }
    Box(
        modifier
            .size(largeur, hauteur)
            .clip(RoundedCornerShape(rayon))
            .testTag("nouveaute:" + app.packageName)
    ) {
        when (style) {
            StyleNouveaute.FONDU -> FonduNouveaute(n.image, marque, carre)
            StyleNouveaute.DECOUPE -> DecoupeNouveaute(n.image, marque, carre, k * r)
            StyleNouveaute.DUOTONE -> DuotoneNouveaute(n, marque)
        }
        val logo = largeur * (if (carre) 0.24f else 0.16f)
        when (style) {
            StyleNouveaute.FONDU -> {
                LogoNouveaute(visuel, logo, Modifier.align(Alignment.TopStart).padding(7.u * r))
                if (legende) {
                    LegendeNouveaute(
                        n.titre, r, carre,
                        if (carre) {
                            Modifier.align(Alignment.BottomStart).fillMaxWidth().padding(horizontal = 8.u * r, vertical = 7.u * r)
                        } else {
                            Modifier.align(Alignment.CenterStart).width(largeur * 0.42f).padding(start = 12.u * r)
                        },
                    )
                }
            }
            else -> LogoNouveaute(visuel, logo, Modifier.align(Alignment.BottomStart).padding(8.u * r))
        }
    }
}

/** Logo de l'app : son visuel carré, coins arrondis, fin liseré clair. */
@Composable
private fun LogoNouveaute(visuel: AppVisual?, taille: Dp, modifier: Modifier) {
    if (visuel?.art == null) return
    val forme = RoundedCornerShape(taille * 0.22f)
    ImageApp(
        visuel, null,
        modifier
            .size(taille)
            .clip(forme)
            .border(1.u, Color.White.copy(alpha = 0.3f), forme),
    )
}

/** « NOUVEAU » et le titre (2 lignes), en blanc sur la zone de couleur. */
@Composable
private fun LegendeNouveaute(titre: String, r: Float, carre: Boolean, modifier: Modifier) {
    val k = LocalScale.current
    fun taille(u: Float): TextUnit = (u * r * k).sp
    Column(modifier) {
        Text(
            "NOUVEAU", color = Color.White.copy(alpha = 0.85f), fontSize = taille(8f), fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.SansSerif, letterSpacing = 0.14.em, lineHeight = taille(10f), maxLines = 1,
        )
        Text(
            titre, color = Color.White, fontSize = taille(if (carre) 12f else 15f), fontWeight = FontWeight.ExtraBold,
            fontFamily = FontFamily.SansSerif, letterSpacing = (-0.01).em, lineHeight = taille(if (carre) 13.5f else 17f),
            maxLines = 2, overflow = TextOverflow.Ellipsis,
        )
    }
}

/** Dessine [image] pour couvrir [dst] (recadrée, centrée ; un peu vers le haut : les visages). */
private fun DrawScope.photo(image: ImageBitmap, dst: Rect) {
    val s = maxOf(dst.width / image.width, dst.height / image.height)
    val sw = (dst.width / s).toInt().coerceIn(1, image.width)
    val sh = (dst.height / s).toInt().coerceIn(1, image.height)
    val sx = ((image.width - sw) / 2f).toInt().coerceAtLeast(0)
    val sy = ((image.height - sh) * 0.35f).toInt().coerceAtLeast(0)
    drawImage(
        image,
        srcOffset = IntOffset(sx, sy),
        srcSize = IntSize(sw, sh),
        dstOffset = IntOffset(dst.left.toInt(), dst.top.toInt()),
        dstSize = IntSize(dst.width.toInt().coerceAtLeast(1), dst.height.toInt().coerceAtLeast(1)),
        filterQuality = FilterQuality.Medium,
    )
}

/** (A) Fondu : la photo en haut (carré) ou à droite (16:9), fondue dans la couleur de l'app. */
@Composable
private fun FonduNouveaute(image: ImageBitmap, marque: Color, carre: Boolean) {
    Box(
        Modifier
            .fillMaxSize()
            .drawWithCache {
                val w = size.width
                val h = size.height
                val zone = if (carre) Rect(0f, 0f, w, h * 0.7f) else Rect(w * 0.3f, 0f, w, h)
                val fondu = if (carre) {
                    Brush.verticalGradient(0f to Color.Transparent, 1f to marque, startY = h * 0.28f, endY = h * 0.7f)
                } else {
                    Brush.horizontalGradient(0f to marque, 1f to Color.Transparent, startX = w * 0.3f, endX = w * 0.62f)
                }
                // Ombre douce sous le logo (coin haut gauche)
                val ombre = Brush.radialGradient(
                    listOf(Color.Black.copy(alpha = 0.45f), Color.Transparent),
                    center = Offset(0f, 0f), radius = w * 0.45f,
                )
                onDrawBehind {
                    drawRect(marque)
                    photo(image, zone)
                    drawRect(fondu, topLeft = zone.topLeft, size = zone.size)
                    if (carre) drawRect(ombre)
                }
            }
    )
}

/** (B) Découpe : aplat de la couleur de l'app avec un reflet, photo dans un grand disque ou une bande inclinée. */
@Composable
private fun DecoupeNouveaute(image: ImageBitmap, marque: Color, carre: Boolean, k: Float) {
    Box(
        Modifier
            .fillMaxSize()
            .drawWithCache {
                val w = size.width
                val h = size.height
                val clair = lerp(marque, Color.White, 0.18f)
                val sombre = lerp(marque, Color.Black, 0.35f)
                val reflet = Brush.linearGradient(listOf(clair, marque, sombre), start = Offset.Zero, end = Offset(w, h))
                val forme = Path()
                val filet = Path()
                if (carre) {
                    // Grand disque en haut à droite, qui déborde des bords
                    val c = Offset(w * 0.66f, h * 0.36f)
                    val rayon = w * 0.58f
                    forme.addOval(Rect(c, rayon))
                    filet.addOval(Rect(c, rayon + 4f * k))
                } else {
                    // Bande inclinée sur les deux tiers droits
                    forme.moveTo(w * 0.42f, 0f)
                    forme.lineTo(w, 0f)
                    forme.lineTo(w, h)
                    forme.lineTo(w * 0.26f, h)
                    forme.close()
                    filet.moveTo(w * 0.42f - 6f * k, 0f)
                    filet.lineTo(w * 0.26f - 6f * k, h)
                }
                val trait = Stroke(width = 1.6f * k)
                onDrawBehind {
                    drawRect(reflet)
                    drawPath(filet, Color.White.copy(alpha = 0.35f), style = trait)
                    clipPath(forme) { photo(image, Rect(0f, 0f, w, h)) }
                }
            }
    )
}

/** (C) Duotone : la photo recolorée (ombres : noir teinté, lumières : couleur de l'app éclaircie). */
@Composable
private fun DuotoneNouveaute(n: Nouveaute, marque: Color) {
    val sombre = lerp(marque, Color.Black, 0.86f)
    val clair = lerp(marque, Color.White, 0.28f).let { if (it.luminance() < 0.12f) lerp(it, Color.White, 0.25f) else it }
    val duo by produceState(DuotonesNouveautes.enCache(n, sombre, clair), n.adresse, n.image.width, marque) {
        value = withContext(Dispatchers.Default) { DuotonesNouveautes.obtenir(n, sombre, clair) }
    }
    Box(
        Modifier
            .fillMaxSize()
            .drawWithCache {
                val w = size.width
                val h = size.height
                val bas = Brush.verticalGradient(0f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.55f), startY = h * 0.5f, endY = h)
                onDrawBehind {
                    drawRect(sombre)
                    duo?.let { photo(it, Rect(0f, 0f, w, h)) }
                    drawRect(bas)
                }
            }
    )
}

/** Photos recolorées en deux tons, calculées une fois (hors du thread principal) et gardées ~4 Mo. */
private object DuotonesNouveautes {
    private val cache = object : LruCache<String, ImageBitmap>(4 * 1024 * 1024) {
        override fun sizeOf(key: String, value: ImageBitmap) = value.width * value.height * 2
    }

    private fun cle(n: Nouveaute, sombre: Color, clair: Color) =
        n.adresse + "@" + n.image.width + "#" + sombre.toArgb() + "/" + clair.toArgb()

    fun enCache(n: Nouveaute, sombre: Color, clair: Color): ImageBitmap? = cache.get(cle(n, sombre, clair))

    fun obtenir(n: Nouveaute, sombre: Color, clair: Color): ImageBitmap? {
        val c = cle(n, sombre, clair)
        cache.get(c)?.let { return it }
        return try {
            calculer(n.image, sombre, clair).also { cache.put(c, it) }
        } catch (e: Exception) {
            null
        } catch (e: OutOfMemoryError) {
            null
        }
    }

    private fun calculer(source: ImageBitmap, sombre: Color, clair: Color): ImageBitmap {
        val b = source.asAndroidBitmap()
        val w = b.width
        val h = b.height
        val px = IntArray(w * h)
        b.getPixels(px, 0, w, 0, 0, w, h)
        // Table des 256 niveaux de gris → couleur (contraste un peu relevé)
        val table = IntArray(256) { i ->
            val t = ((i / 255f - 0.06f) / 0.86f).coerceIn(0f, 1f)
            lerp(sombre, clair, t * t * (3f - 2f * t)).toArgb()
        }
        for (i in px.indices) {
            val c = px[i]
            val l = (((c shr 16) and 0xFF) * 77 + ((c shr 8) and 0xFF) * 150 + (c and 0xFF) * 29) shr 8
            px[i] = table[l]
        }
        return Bitmap.createBitmap(px, w, h, Bitmap.Config.RGB_565).asImageBitmap()
    }
}
