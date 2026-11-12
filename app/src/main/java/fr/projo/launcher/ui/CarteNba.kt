package fr.projo.launcher.ui

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.sp
import fr.projo.launcher.boxscore.EquipesNba
import fr.projo.launcher.nba.ConferenceNba
import fr.projo.launcher.nba.DivisionNba
import org.json.JSONObject
import java.util.Locale
import kotlin.math.ceil
import kotlin.math.hypot

/*
 * Design « NBA » : la carte des divisions (res/raw/carte_nba.json, en px de l'écran de
 * référence 1920 × 1080) et son dessin.
 *
 * Tout ce qui ne bouge pas (îles et leurs ombres floutées, planches, reflets, frontières,
 * liserés, ronds et logos des leaders, monuments, noms des divisions, villes, capsules des
 * pastilles et disques des logos) est dessiné UNE fois dans une image en mémoire, hors du fil
 * principal, puis seulement réaffiché : une par ambiance et par jeu de leaders. Les logos, bilans
 * et rangs des pastilles sont dessinés par-dessus (PastillesNba.kt).
 */

/** Île d'une division (contour pair-impair) et son rectangle englobant. */
class IleNba(val division: DivisionNba, val conference: ConferenceNba, val chemin: Path, val bornes: Rect)

/**
 * Place d'une pastille : logo centré en ([x], [y]) ; capsule vers la droite ([sens] 1) ou vers
 * la gauche (−1) ; [groupe] : équipes d'une même ville (« NY », « LA »), dans une seule capsule.
 */
class PlacePastilleNba(val code: String, val x: Float, val y: Float, val sens: Int, val groupe: String?)

class NomDivisionNba(val division: DivisionNba, val x: Float, val y: Float, val horsIle: Boolean)

class RondCentralNba(val division: DivisionNba, val x: Float, val y: Float, val r: Float)

/** Partie d'un monument, dans le repère propre du monument. */
sealed class PartieMonument(val opacite: Float) {
    class Forme(val chemin: Path, opacite: Float) : PartieMonument(opacite)
    class Trait(val chemin: Path, val epaisseur: Float, val bouts: StrokeCap, opacite: Float) : PartieMonument(opacite)
    class Texte(val texte: String, val x: Float, val y: Float, val taille: Float, val espace: Float, opacite: Float) : PartieMonument(opacite)
}

/** Monument centré en ([x], [y]), mis à l'échelle [echelle] depuis son repère [largeur] × [hauteur]. */
class MonumentNba(
    val id: String,
    val nom: String,
    val x: Float,
    val y: Float,
    val echelle: Float,
    val enMer: Boolean,
    val largeur: Float,
    val hauteur: Float,
    val parties: List<PartieMonument>,
) {
    val bornes: Rect get() = Rect(x - largeur * echelle / 2f, y - hauteur * echelle / 2f, x + largeur * echelle / 2f, y + hauteur * echelle / 2f)
}

/** Mesures d'une pastille (px de référence) : capsule 224 × 58, logo dans un disque de rayon 29. */
object FormePastille {
    const val LARGEUR = 224f
    const val HAUTEUR = 58f
    const val RAYON = 29f
    /** Écart entre deux lignes d'une capsule de groupe. */
    const val ECART = 4f

    fun gauche(p: PlacePastilleNba): Float = if (p.sens > 0) p.x - RAYON else p.x - LARGEUR + RAYON

    /** Capsule d'un groupe (ou d'une équipe seule) : celle de son premier membre, une ligne par équipe. */
    fun capsule(membres: List<PlacePastilleNba>): Rect {
        val p = membres.first()
        val l = gauche(p)
        val n = membres.size
        return Rect(l, p.y - HAUTEUR / 2f, l + LARGEUR, p.y - HAUTEUR / 2f + HAUTEUR * n + ECART * (n - 1))
    }

    /** Ligne d'une équipe dans sa capsule (toute la capsule pour une équipe seule). */
    fun ligne(p: PlacePastilleNba): Rect {
        val l = gauche(p)
        return Rect(l, p.y - HAUTEUR / 2f, l + LARGEUR, p.y + HAUTEUR / 2f)
    }

    /** Début des textes (bilan, rangs). */
    fun texteX(p: PlacePastilleNba): Float = if (p.sens > 0) p.x + RAYON + 13f else gauche(p) + 20f

    /** Centre de la ligne de l'équipe : repère de la navigation entre pastilles. */
    fun centre(p: PlacePastilleNba): Offset = ligne(p).center

    /** Couleur de l'anneau du logo (blanc → noir). */
    fun anneau(code: String): Color {
        val c = EquipesNba.de(code).couleur
        return if (c.equals("#ffffff", ignoreCase = true)) Color.Black else couleurHex(c)
    }

    /** Couleur des lettres du logo en attendant l'image (blanc ou gris clair → noir). */
    fun lettres(code: String): Color {
        val c = EquipesNba.de(code).couleur.lowercase(Locale.ROOT)
        return if (c == "#ffffff" || c == "#c4ced4") Color.Black else couleurHex(c)
    }
}

private fun unir(a: Rect, b: Rect) = Rect(minOf(a.left, b.left), minOf(a.top, b.top), maxOf(a.right, b.right), maxOf(a.bottom, b.bottom))

/** « #rrggbb » → couleur opaque. */
internal fun couleurHex(s: String): Color = Color(0xFF000000 or s.removePrefix("#").toLong(16))

/** La carte, lue une fois. */
class CarteNba(
    val iles: List<IleNba>,
    val frontieres: Path,
    val villes: Map<String, Offset>,
    val pastilles: List<PlacePastilleNba>,
    val noms: List<NomDivisionNba>,
    val ronds: List<RondCentralNba>,
    val monuments: List<MonumentNba>,
) {
    /** Toutes les îles en un seul contour : découpe des frontières, des ronds et des liserés de conférence. */
    val toutesLesIles: Path = Path().apply {
        fillType = PathFillType.EvenOdd
        iles.forEach { addPath(it.chemin) }
    }

    /** Une capsule par équipe, ou par groupe (même ville), dans l'ordre de la carte. */
    val capsules: List<List<PlacePastilleNba>> = run {
        val vus = HashSet<String>()
        pastilles.mapNotNull { p ->
            when {
                p.groupe == null -> listOf(p)
                vus.add(p.groupe) -> pastilles.filter { it.groupe == p.groupe }.sortedBy { it.y }
                else -> null
            }
        }
    }

    /** Partie de l'écran couverte par le dessin (ombres comprises), px de référence. */
    val cadre: Rect = run {
        var r = iles.first().bornes
        for (i in iles) r = unir(r, i.bornes)
        for (m in monuments) r = unir(r, m.bornes)
        for (c in capsules) r = unir(r, FormePastille.capsule(c))
        for (n in noms) r = unir(r, Rect(n.x - 160f, n.y - 30f, n.x + 160f, n.y + 10f))
        Rect(r.left - 44f, r.top - 44f, r.right + 44f, r.bottom + 56f)
    }

    companion object {
        /** Lit la carte (texte de carte_nba.json). Hors du fil principal. */
        fun lire(json: String): CarteNba {
            val o = JSONObject(json)
            fun chemin(d: String, pairImpair: Boolean): Path = PathParser().parsePathString(d).toPath().apply {
                fillType = if (pairImpair) PathFillType.EvenOdd else PathFillType.NonZero
            }

            val divisions = o.getJSONArray("divisions")
            val iles = (0 until divisions.length()).mapNotNull { i ->
                val d = divisions.getJSONObject(i)
                val division = DivisionNba.parId(d.getString("id")) ?: return@mapNotNull null
                val c = chemin(d.getString("d"), true)
                IleNba(division, division.conference, c, c.getBounds())
            }
            val villes = o.getJSONObject("villes").let { v ->
                v.keys().asSequence().associateWith { k -> v.getJSONArray(k).let { Offset(it.getDouble(0).toFloat(), it.getDouble(1).toFloat()) } }
            }
            val places = o.getJSONObject("pastilles").let { p ->
                p.keys().asSequence().map { k ->
                    val e = p.getJSONObject(k)
                    PlacePastilleNba(
                        k, e.getDouble("x").toFloat(), e.getDouble("y").toFloat(), e.optInt("sens", 1),
                        e.optString("groupe").ifBlank { null },
                    )
                }.toList()
            }
            val noms = o.getJSONObject("nomsDivisions").let { n ->
                n.keys().asSequence().mapNotNull { k ->
                    val e = n.getJSONObject(k)
                    DivisionNba.parId(k)?.let { NomDivisionNba(it, e.getDouble("x").toFloat(), e.getDouble("y").toFloat(), e.optBoolean("horsIle")) }
                }.toList()
            }
            val ronds = o.getJSONObject("rondsCentraux").let { n ->
                n.keys().asSequence().mapNotNull { k ->
                    val e = n.getJSONObject(k)
                    DivisionNba.parId(k)?.let { RondCentralNba(it, e.getDouble("x").toFloat(), e.getDouble("y").toFloat(), e.getDouble("r").toFloat()) }
                }.toList()
            }
            val tab = o.getJSONArray("monuments")
            val monuments = (0 until tab.length()).map { i ->
                val m = tab.getJSONObject(i)
                val pair = m.optBoolean("pairImpair")
                val parties = m.getJSONArray("parties")
                MonumentNba(
                    m.getString("id"), m.optString("nom"),
                    m.getDouble("x").toFloat(), m.getDouble("y").toFloat(), m.getDouble("echelle").toFloat(),
                    m.optBoolean("enMer"), m.getDouble("largeur").toFloat(), m.getDouble("hauteur").toFloat(),
                    (0 until parties.length()).mapNotNull { j ->
                        val p = parties.getJSONObject(j)
                        val opacite = p.optDouble("o", 1.0).toFloat()
                        when (p.getString("m")) {
                            "f" -> PartieMonument.Forme(chemin(p.getString("d"), pair || p.optBoolean("pair")), opacite)
                            "s" -> PartieMonument.Trait(
                                chemin(p.getString("d"), false), p.optDouble("e", 1.0).toFloat(),
                                if (p.optString("cap") == "round") StrokeCap.Round else StrokeCap.Butt, opacite,
                            )
                            "t" -> PartieMonument.Texte(
                                p.getString("texte"), p.getDouble("x").toFloat(), p.getDouble("y").toFloat(),
                                p.getDouble("taille").toFloat(), p.optDouble("espace", 0.0).toFloat(), opacite,
                            )
                            else -> null
                        }
                    },
                )
            }
            return CarteNba(iles, chemin(o.getString("frontieresEtats"), false), villes, places, noms, ronds, monuments)
        }

        @Volatile
        private var lue: CarteNba? = null

        /** Carte déjà lue, ou null. */
        val enMemoire: CarteNba? get() = lue

        /** Carte lue une seule fois ([texte] : contenu de carte_nba.json). Fonction bloquante. */
        fun charger(texte: () -> String): CarteNba = lue ?: synchronized(this) {
            lue ?: lire(texte()).also { lue = it }
        }
    }
}

/** Logo d'un leader en filigrane (null : pas encore là, son code à la place). */
class FiligraneNba(val code: String, val logo: ImageBitmap?)

/** Image de la carte : [image] couvre [cadre] (px de référence) à [echelle] px par px de référence. */
class ImageCarteNba(val image: ImageBitmap, val cadre: Rect, val echelle: Float)

/** Mesures de la maquette (filtres SVG « ile », « ombre » et « lueur », échelle de la carte 1,314). */
private const val ECHELLE_MAQUETTE = 1.3140162f
private const val OMBRE_ILE_DY = 10f * ECHELLE_MAQUETTE
private const val OMBRE_ILE_SIGMA = 9f * ECHELLE_MAQUETTE
private const val OMBRE_PASTILLE_DY = 6f
private const val OMBRE_PASTILLE_SIGMA = 8f
private const val LUEUR_SIGMA = 5f
/** Origine du motif des planches (coin de la carte de la maquette). */
private val ORIGINE_PLANCHES = Offset(333.08f, 107.92f)
private val FOND_PASTILLE = Color(0xFF0A0E1C).copy(alpha = 0.93f)

/**
 * Dessine la carte dans une image en mémoire, à [echelle] px par px de référence. Fonction
 * longue (ombres floutées) : hors du fil principal ; [mesure] est propre à ce dessin.
 */
fun rendreCarteNba(
    carte: CarteNba,
    palette: PaletteNba,
    filigranes: Map<DivisionNba, FiligraneNba>,
    mesure: TextMeasurer,
    echelle: Float,
): ImageCarteNba {
    val cadre = carte.cadre
    val w = ceil(cadre.width * echelle).toInt().coerceAtLeast(1)
    val h = ceil(cadre.height * echelle).toInt().coerceAtLeast(1)
    val image = ImageBitmap(w, h)
    CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, Canvas(image), Size(w.toFloat(), h.toFloat())) {
        withTransform({
            scale(echelle, echelle, pivot = Offset.Zero)
            translate(-cadre.left, -cadre.top)
        }) {
            dessinerCarteNba(carte, palette, filigranes, mesure)
        }
    }
    image.prepareToDraw()
    return ImageCarteNba(image, cadre, echelle)
}

/** Ordre de la maquette : îles, planches et reflets, filigranes, frontières, liserés, monuments, noms, villes, capsules. */
private fun DrawScope.dessinerCarteNba(
    carte: CarteNba,
    palette: PaletteNba,
    filigranes: Map<DivisionNba, FiligraneNba>,
    mesure: TextMeasurer,
) {
    // Îles : ombre portée puis remplissage, l'une après l'autre (l'ombre d'une île passe sur les précédentes)
    val ombre = Paint().apply { color = Color.Black.copy(alpha = 0.6f) }.flouNba(OMBRE_ILE_SIGMA)
    for (ile in carte.iles) {
        drawIntoCanvas { c ->
            c.save()
            c.translate(0f, OMBRE_ILE_DY)
            c.drawPath(ile.chemin, ombre)
            c.restore()
        }
        drawPath(ile.chemin, palette.fonds[ile.division] ?: PaletteNba.ARDOISE)
    }
    // Planches et reflet (clair en haut à gauche, sombre en bas à droite)
    val planches = if (palette.planche > 0f) motifPlanches(palette.planche) else null
    for (ile in carte.iles) {
        if (planches != null) {
            clipPath(ile.chemin) {
                translate(ORIGINE_PLANCHES.x, ORIGINE_PLANCHES.y) {
                    drawRect(planches, topLeft = ile.bornes.topLeft - ORIGINE_PLANCHES, size = ile.bornes.size)
                }
            }
        }
        drawPath(ile.chemin, reflet(ile.bornes, palette.eclat))
    }
    // Ambiance Leaders : ronds centraux et logo du leader en silhouette blanche
    if (palette.filigrane) {
        clipPath(carte.toutesLesIles) {
            for (rond in carte.ronds) dessinerFiligrane(rond, filigranes[rond.division], mesure)
        }
    }
    // Frontières des États, à l'intérieur des îles seulement
    clipPath(carte.toutesLesIles) {
        drawPath(carte.frontieres, palette.etats, style = Stroke(0.9f * 1.3f))
    }
    val lisere = Stroke(1.4f * 1.3f, join = StrokeJoin.Round)
    for (ile in carte.iles) drawPath(ile.chemin, palette.lisere, style = lisere)
    // Parquet, Nuit d'arène : liseré de conférence (sa moitié intérieure)
    palette.conferences?.let { conf ->
        val trait = Stroke(2.6f * 1.3f, join = StrokeJoin.Round)
        clipPath(carte.toutesLesIles) {
            for (ile in carte.iles) drawPath(ile.chemin, conf[ile.conference] ?: Color.White, alpha = 0.9f, style = trait)
        }
    }
    for (m in carte.monuments) dessinerMonument(m, palette, mesure)
    // Noms des divisions (Centrale, posé hors de son île : en clair)
    for (n in carte.noms) {
        val couleur = if (n.horsIle) PaletteNba.CLAIR.copy(alpha = 0.6f) else palette.noms
        texte(mesure, n.division.nom.uppercase(Locale.FRENCH), n.x, n.y, 22f, NbaEtroit, FontWeight.ExtraBold, 5f, couleur)
    }
    // Villes : trait vers le logo (pastille décalée) et point lumineux ; un seul par groupe
    val lueur = Paint().apply { color = Color.White.copy(alpha = 0.85f) }.flouNba(LUEUR_SIGMA)
    for (capsule in carte.capsules) {
        val p = capsule.first()
        val v = carte.villes[p.code] ?: continue
        val logo = Offset(p.x, p.y)
        if (hypot(logo.x - v.x, logo.y - v.y) > 4f) drawLine(Color.White.copy(alpha = 0.6f), v, logo, strokeWidth = 1.6f)
        drawIntoCanvas { it.drawCircle(v, 6.5f, lueur) }
        drawCircle(Color.White, 6f, v)
        drawCircle(Color.Black.copy(alpha = 0.35f), 6f, v, style = Stroke(1f))
    }
    // Capsules des pastilles : ombre, fond, liseré, séparateurs, disques blancs et anneaux
    val ombrePastille = Paint().apply { color = Color.Black.copy(alpha = 0.45f) }.flouNba(OMBRE_PASTILLE_SIGMA)
    for (capsule in carte.capsules) dessinerCapsule(capsule, ombrePastille, contour = null)
}

/** Capsule d'une pastille (ou d'un groupe), sans les logos ni les textes ; [contour] : sélection. */
internal fun DrawScope.dessinerCapsule(membres: List<PlacePastilleNba>, ombre: Paint?, contour: Rect?) {
    val r = FormePastille.capsule(membres)
    val rayon = CornerRadius(FormePastille.HAUTEUR / 2f)
    if (ombre != null) {
        drawIntoCanvas { c ->
            c.drawRoundRect(r.left, r.top + OMBRE_PASTILLE_DY, r.right, r.bottom + OMBRE_PASTILLE_DY, rayon.x, rayon.y, ombre)
        }
    }
    drawRoundRect(FOND_PASTILLE, r.topLeft, r.size, rayon)
    drawRoundRect(Color.White.copy(alpha = 0.14f), r.topLeft + Offset(0.75f, 0.75f), Size(r.width - 1.5f, r.height - 1.5f), CornerRadius(rayon.x - 0.75f), style = Stroke(1.5f))
    // Groupe : filet entre deux lignes, du côté des textes
    val p = membres.first()
    for (i in 1 until membres.size) {
        val y = p.y + i * (FormePastille.HAUTEUR + FormePastille.ECART) - FormePastille.HAUTEUR / 2f - 2f
        val x1 = if (p.sens > 0) p.x + FormePastille.RAYON + 8f else r.left + 16f
        val x2 = if (p.sens > 0) r.left + FormePastille.LARGEUR - 18f else p.x - FormePastille.RAYON - 8f
        drawLine(Color.White.copy(alpha = 0.12f), Offset(x1, y), Offset(x2, y), strokeWidth = 1f)
    }
    if (contour != null) {
        drawRoundRect(
            Color.White, contour.topLeft - Offset(1.5f, 1.5f), Size(contour.width + 3f, contour.height + 3f),
            CornerRadius(FormePastille.HAUTEUR / 2f + 1.5f), style = Stroke(3f),
        )
    }
    for (m in membres) {
        val c = Offset(m.x, m.y)
        drawCircle(Color.White, FormePastille.RAYON, c)
        drawCircle(FormePastille.anneau(m.code), FormePastille.RAYON, c, style = Stroke(4f))
    }
}

/** Rond central d'une île et logo de son leader (blanc, très discret). */
private fun DrawScope.dessinerFiligrane(rond: RondCentralNba, f: FiligraneNba?, mesure: TextMeasurer) {
    val c = Offset(rond.x, rond.y)
    drawCircle(Color.White.copy(alpha = 0.13f), rond.r, c, style = Stroke(3f))
    drawCircle(Color.White.copy(alpha = 0.10f), rond.r * 0.36f, c, style = Stroke(2f))
    if (f == null) return
    val logo = f.logo
    if (logo != null && logo.width > 0) {
        val cote = rond.r * 1.1f
        translate(c.x - cote / 2f, c.y - cote / 2f) {
            scale(cote / logo.width, cote / logo.height, pivot = Offset.Zero) {
                drawImage(
                    logo, alpha = 0.14f, colorFilter = ColorFilter.tint(Color.White, BlendMode.SrcIn),
                )
            }
        }
    } else {
        texte(mesure, f.code, c.x, c.y + rond.r * 0.2f, rond.r * 0.58f, NbaTitre, FontWeight.Bold, 0f, Color.White.copy(alpha = 0.13f))
    }
}

/** Monument : un calque à son opacité (les parties qui se recouvrent ne s'additionnent pas). */
private fun DrawScope.dessinerMonument(m: MonumentNba, palette: PaletteNba, mesure: TextMeasurer) {
    val couleur = if (m.enMer) PaletteNba.CLAIR else palette.monuments
    val opacite = if (m.enMer) 0.28f else palette.opaciteMonuments
    val b = m.bornes
    drawIntoCanvas { it.saveLayer(Rect(b.left - 8f, b.top - 8f, b.right + 8f, b.bottom + 8f), Paint().apply { alpha = opacite }) }
    withTransform({
        translate(b.left, b.top)
        scale(m.echelle, m.echelle, pivot = Offset.Zero)
    }) {
        for (p in m.parties) {
            when (p) {
                is PartieMonument.Forme -> drawPath(p.chemin, couleur, alpha = p.opacite)
                is PartieMonument.Trait -> drawPath(
                    p.chemin, couleur, alpha = p.opacite,
                    style = Stroke(p.epaisseur, cap = p.bouts, join = StrokeJoin.Round),
                )
                is PartieMonument.Texte -> texte(
                    mesure, p.texte, p.x, p.y, p.taille, NbaTitre, FontWeight.Bold, p.espace,
                    palette.texteMonuments.copy(alpha = p.opacite),
                )
            }
        }
    }
    drawIntoCanvas { it.restore() }
}

/** Texte centré en [x], sur la ligne de base [y] (px du repère courant). */
private fun DrawScope.texte(
    mesure: TextMeasurer,
    texte: String,
    x: Float,
    y: Float,
    taille: Float,
    police: androidx.compose.ui.text.font.FontFamily,
    poids: FontWeight,
    espace: Float,
    couleur: Color,
) {
    val r = mesure.measure(
        texte,
        TextStyle(fontFamily = police, fontWeight = poids, fontSize = taille.sp, letterSpacing = espace.sp),
        softWrap = false,
    )
    drawText(r, couleur, topLeft = Offset(x - r.size.width / 2f, y - r.firstBaseline))
}

/** Reflet d'une île : dégradé de la maquette (0,0 → 0,6 × 1 du rectangle englobant). */
private fun reflet(b: Rect, force: Float): Brush {
    val a = 0.6f / (1.36f * b.width)
    val c = 1f / (1.36f * b.height)
    val n = a * a + c * c
    return Brush.linearGradient(
        0f to Color.White.copy(alpha = force), 0.55f to Color.White.copy(alpha = 0f), 1f to Color.Black.copy(alpha = 0.18f),
        start = b.topLeft, end = Offset(b.left + a / n, b.top + c / n),
    )
}

/** Motif des planches (22 × 300 px) : deux lames, leurs joints et deux raccords. */
private fun motifPlanches(p: Float): ShaderBrush {
    val tuile = ImageBitmap(22, 300)
    CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, Canvas(tuile), Size(22f, 300f)) {
        drawRect(Color.Black.copy(alpha = p), Offset.Zero, Size(11f, 300f))
        drawRect(Color.White.copy(alpha = p * 0.6f), Offset(11f, 0f), Size(11f, 300f))
        drawRect(Color.Black.copy(alpha = p * 1.6f), Offset.Zero, Size(0.5f, 300f))
        drawRect(Color.Black.copy(alpha = p * 1.6f), Offset(10.5f, 0f), Size(1f, 300f))
        drawRect(Color.Black.copy(alpha = p * 2f), Offset(0f, 119.5f), Size(11f, 1f))
        drawRect(Color.Black.copy(alpha = p * 2f), Offset(11f, 229.5f), Size(11f, 1f))
    }
    return ShaderBrush(ImageShader(tuile, TileMode.Repeated, TileMode.Repeated))
}

/** Logo dans le disque d'une pastille (un peu en retrait de l'anneau). */
internal fun DrawScope.dessinerLogo(logo: ImageBitmap, centre: Offset, cote: Float) {
    if (logo.width <= 0 || logo.height <= 0) return
    translate(centre.x - cote / 2f, centre.y - cote / 2f) {
        scale(cote / logo.width, cote / logo.height, pivot = Offset.Zero) {
            drawImage(logo, filterQuality = FilterQuality.Medium)
        }
    }
}
