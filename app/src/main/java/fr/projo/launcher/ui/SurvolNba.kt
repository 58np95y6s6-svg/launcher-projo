package fr.projo.launcher.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import fr.projo.launcher.nba.BilanNba
import fr.projo.launcher.nba.DivisionNba
import fr.projo.launcher.nba.MatchEquipe
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt

/*
 * Design « NBA » : le survol d'une division (docs/design-nba.md, « Survol »). Une pastille
 * sélectionnée, la caméra cadre sa division à gauche du panneau ; la division se soulève (agrandie,
 * montée, ombre portée laissée à sa place, bord lumineux), le reste de la carte passe en retrait
 * (assombri, désaturé). À droite, le panneau de survol : en-tête de l'équipe, ses 3 derniers
 * matchs, le prochain, le classement de sa division.
 *
 * Coût : la carte et ses pastilles ne bougent que par la transformation de leur calque ; la
 * division soulevée est une image nette dessinée une fois à l'échelle finale de la caméra (hors du
 * fil principal), son ombre une petite image floutée ; seuls les logos et textes des pastilles sont
 * redessinés (vectoriels). Aucune image n'est créée pendant l'animation.
 */

/** Courbe des mouvements de la caméra (lent – rapide – lent). */
internal val EaseInOutNba = CubicBezierEasing(0.42f, 0f, 0.58f, 1f)

/** Durées : entrée en survol et passage d'une division à l'autre, retour à la carte entière. */
internal const val SURVOL_MS = 450
internal const val RETOUR_CARTE_MS = 400

/** Fiche de l'équipe survolée : demandée après cette pause (les flèches enchaînées ne la demandent pas). */
internal const val DELAI_FICHE_MS = 300L

/**
 * Caméra de la carte : le point [centre] de la carte (px de référence) vu au point [CIBLE] de
 * l'écran, agrandi [echelle] fois. [CARTE] : la carte entière, telle quelle.
 */
data class CameraNba(val echelle: Float, val centre: Offset) {

    /** Point de l'écran (px de référence) où se voit le point [p] de la carte. */
    fun vers(p: Offset): Offset = (p - centre) * echelle + CIBLE

    /** Décalage du calque de la carte (px de référence) : écran = carte × [echelle] + décalage. */
    val decalage: Offset get() = CIBLE - centre * echelle

    /**
     * Calque d'une division soulevée de [levee] (0 : posée, 1 : soulevée), de cadre centré en
     * [milieu] : agrandie autour de son milieu (× [AGRANDI]) et montée de [LEVEE] px d'écran.
     * Écran = carte × échelle + décalage (px de référence).
     */
    fun soulevee(milieu: Offset, levee: Float): Pair<Float, Offset> {
        val a = 1f + (AGRANDI - 1f) * levee
        val d = (milieu * (1f - a) - centre) * echelle + CIBLE
        return echelle * a to Offset(d.x, d.y - LEVEE * levee)
    }

    companion object {
        /** Partie de l'écran où la division est cadrée (à gauche du panneau, sous l'en-tête), px de référence. */
        val ZONE = Rect(40f, 140f, 1230f, 900f)

        /** Agrandissement maximal de la caméra. */
        const val ECHELLE_MAX = 1.75f

        /** Division soulevée : agrandie de 5 % et montée de 26 px (écran 1080p). */
        const val AGRANDI = 1.05f
        const val LEVEE = 26f

        /** Où se voit le milieu de la division cadrée (soulevée, elle est au milieu de [ZONE]). */
        val CIBLE = Offset(ZONE.center.x, ZONE.center.y + LEVEE)

        /** La carte entière. */
        val CARTE = CameraNba(1f, CIBLE)

        /** Marge autour d'une division (pastille choisie agrandie et cerclée), px de référence. */
        private const val MARGE = 8f

        /** Cadre d'une division : son île, ses pastilles et ses villes (px de référence). */
        fun cadre(carte: CarteNba, d: DivisionNba): Rect {
            var l = Float.MAX_VALUE
            var t = Float.MAX_VALUE
            var r = -Float.MAX_VALUE
            var b = -Float.MAX_VALUE
            fun unir(x: Rect) {
                l = min(l, x.left)
                t = min(t, x.top)
                r = maxOf(r, x.right)
                b = maxOf(b, x.bottom)
            }
            carte.iles.firstOrNull { it.division == d }?.let { unir(it.bornes) }
            for (capsule in carte.capsules) {
                if (capsule.none { DivisionNba.de(it.code) == d }) continue
                unir(FormePastille.capsule(capsule))
                carte.villes[capsule.first().code]?.let { v -> unir(Rect(v.x - 8f, v.y - 8f, v.x + 8f, v.y + 8f)) }
            }
            if (l > r || t > b) return ZONE
            return Rect(l - MARGE, t - MARGE, r + MARGE, b + MARGE)
        }

        /** Caméra cadrant [cadre] soulevé dans [ZONE] (au plus × [ECHELLE_MAX]). */
        fun sur(cadre: Rect): CameraNba {
            val e = minOf(ZONE.width / (cadre.width * AGRANDI), ZONE.height / (cadre.height * AGRANDI), ECHELLE_MAX)
            return CameraNba(e, cadre.center)
        }

        fun division(carte: CarteNba, d: DivisionNba): CameraNba = sur(cadre(carte, d))

        /**
         * Entre [a] et [b] ([t] de 0 à 1) : l'échelle change de façon géométrique et le milieu de [b]
         * glisse en ligne droite vers [CIBLE].
         */
        fun entre(a: CameraNba, b: CameraNba, t: Float): CameraNba {
            if (t <= 0f) return a
            if (t >= 1f) return b
            val e = a.echelle * (b.echelle / a.echelle).pow(t)
            val depart = a.vers(b.centre)
            val vu = depart + (CIBLE - depart) * t
            return CameraNba(e, b.centre - (vu - CIBLE) / e)
        }
    }
}

/** Caméra animée : glisse de la caméra du moment vers chaque nouvelle cible. */
@Stable
internal class CameraAnimee(initiale: CameraNba) {
    private var depart by mutableStateOf(initiale)
    var arrivee by mutableStateOf(initiale)
        private set
    private val trajet = Animatable(1f)

    /** Caméra du moment (à lire au dessin : aucune recomposition pendant le mouvement). */
    val valeur: CameraNba get() = CameraNba.entre(depart, arrivee, trajet.value)

    suspend fun aller(cible: CameraNba, ms: Int) {
        if (cible == arrivee) return
        depart = valeur
        arrivee = cible
        trajet.snapTo(0f)
        trajet.animateTo(1f, tween(ms, easing = EaseInOutNba))
    }
}

/** Calque suivant la caméra (carte, ombres). */
internal fun Modifier.cameraNba(camera: CameraAnimee, k: Float): Modifier = graphicsLayer {
    val c = camera.valeur
    val d = c.decalage
    scaleX = c.echelle
    scaleY = c.echelle
    translationX = d.x * k
    translationY = d.y * k
    transformOrigin = TransformOrigin(0f, 0f)
}

/**
 * Carte en retrait (survol), désaturée de moitié quand [voile] vaut 1 (l'assombrissement est un
 * voile noir posé dessus) : filtres préparés une fois, 17 crans.
 */
internal object RetraitNba {
    private const val CRANS = 16
    private val filtres = arrayOfNulls<ColorFilter>(CRANS + 1)

    fun filtre(voile: Float): ColorFilter? {
        val i = (voile.coerceIn(0f, 1f) * CRANS).roundToInt()
        if (i == 0) return null
        return filtres[i] ?: ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(1f - 0.5f * i / CRANS) }).also { filtres[i] = it }
    }

    /** Opacité du voile noir : la carte en retrait garde 38 % de sa luminosité. */
    const val VOILE = 0.62f

    /** Logos et textes des pastilles en retrait : à moitié effacés en plus (moins lisibles que la division soulevée). */
    fun opacitePastilles(voile: Float): Float = 1f - 0.45f * voile.coerceIn(0f, 1f)
}

/** Images d'une division soulevée : l'île nette et son ombre ; [cle] : ce qui a servi à les dessiner. */
internal class ImagesLevee(val ile: ImageCarteNba, val ombre: ImageCarteNba, val cle: Any)

/** Ce qui fait redessiner une division soulevée. */
internal data class CleLevee(val palette: PaletteNba, val filigranes: Map<DivisionNba, FiligraneNba>, val k: Float)

/**
 * Pixels au plus de l'image d'une division soulevée (≈ 5 Mo : 3 gardées, moins de 16 Mo) ; en
 * 1080p, la plus grande en a ≈ 1 million ; au-delà (écran plus grand), elle est un peu moins fine.
 */
private const val PIXELS_MAX_LEVEE = 1_300_000f

/**
 * Images d'une division soulevée : l'île à l'échelle finale de la caméra (× [k] px par px de
 * référence), son ombre au quart. Fonction longue : hors du fil principal.
 */
internal fun rendreLevee(
    carte: CarteNba,
    d: DivisionNba,
    palette: PaletteNba,
    filigranes: Map<DivisionNba, FiligraneNba>,
    mesure: androidx.compose.ui.text.TextMeasurer,
    k: Float,
    cle: Any,
): ImagesLevee {
    val zoom = CameraNba.division(carte, d).echelle
    val cadre = cadreLevee(carte, d)
    var echelle = zoom * CameraNba.AGRANDI * k
    val pixels = cadre.width * cadre.height * echelle * echelle
    if (pixels > PIXELS_MAX_LEVEE) echelle *= kotlin.math.sqrt(PIXELS_MAX_LEVEE / pixels)
    val ile = rendreDivisionNba(carte, d, palette, filigranes, mesure, echelle, bord = 2f / (zoom * CameraNba.AGRANDI))
    val ombre = rendreOmbreDivisionNba(carte, d, zoom, (zoom * k / 4f).coerceAtLeast(0.1f))
    return ImagesLevee(ile, ombre, cle)
}

/**
 * Divisions soulevées : celles qui redescendent d'abord, puis la division [courante] ; chacune avec
 * son ombre (repère de la caméra), son île nette et ses pastilles (repère soulevé).
 */
@Composable
internal fun CouchesLevees(
    carte: CarteNba,
    courante: DivisionNba?,
    levees: Map<DivisionNba, Animatable<Float, AnimationVector1D>>,
    images: Map<DivisionNba, ImagesLevee>,
    camera: CameraAnimee,
    bilans: Map<String, BilanNba>?,
    logos: Map<String, ImageBitmap>,
    choisie: String?,
    k: Float,
    surClic: (String) -> Unit,
) {
    val ordre = DivisionNba.entries.filter { it != courante } + listOfNotNull(courante)
    for (d in ordre) {
        val levee = levees.getValue(d)
        val enVue by remember(levee) { derivedStateOf { levee.value > 0.001f } }
        if (d != courante && !enVue) continue
        key(d) {
            CoucheLevee(carte, d, d == courante, levee, images[d], camera, bilans, logos, choisie, k, surClic)
        }
    }
}

/** Une division soulevée ; elle redescend en s'effaçant quand elle n'est plus la [courante]. */
@Composable
private fun CoucheLevee(
    carte: CarteNba,
    d: DivisionNba,
    courante: Boolean,
    levee: Animatable<Float, AnimationVector1D>,
    images: ImagesLevee?,
    camera: CameraAnimee,
    bilans: Map<String, BilanNba>?,
    logos: Map<String, ImageBitmap>,
    choisie: String?,
    k: Float,
    surClic: (String) -> Unit,
) {
    val milieu = remember(carte, d) { CameraNba.cadre(carte, d).center }
    val equipes = remember(d) { d.equipes.toSet() }
    if (images != null) {
        // Ombre laissée à sa place, plus forte à mesure que la division monte
        Spacer(
            Modifier.fillMaxSize().cameraNba(camera, k).drawBehind {
                val l = levee.value
                if (l > 0.001f) dessinerImageCarte(images.ombre, k, alpha = l.coerceAtMost(1f))
            },
        )
    }
    Box(
        Modifier
            .fillMaxSize()
            .graphicsLayer {
                val l = levee.value
                val (e, d0) = camera.valeur.soulevee(milieu, l)
                var tx = d0.x * k
                var ty = d0.y * k
                // Image de l'île posée sur des pixels entiers (nette une fois la caméra arrêtée)
                if (images != null) {
                    val x = images.ile.cadre.left * k * e + tx
                    val y = images.ile.cadre.top * k * e + ty
                    tx += x.roundToInt() - x
                    ty += y.roundToInt() - y
                }
                scaleX = e
                scaleY = e
                translationX = tx
                translationY = ty
                transformOrigin = TransformOrigin(0f, 0f)
                // Celle qui redescend s'efface (chaque dessin à son opacité : pas de calque en plus)
                alpha = if (courante) 1f else levee.value.coerceIn(0f, 1f)
                compositingStrategy = CompositingStrategy.ModulateAlpha
            }
            .testTag("nba:levee:" + d.id),
    ) {
        if (images != null) Spacer(Modifier.fillMaxSize().drawBehind { dessinerImageCarte(images.ile, k) })
        CouchePastilles(carte, bilans, logos, choisie, k, surClic, equipes = equipes, etiquette = "nba:levee:")
    }
}

/**
 * Voile du survol : la carte en retrait (noir 62 %, bords plus sombres) et l'en-tête dégagé
 * (fondu de la couleur [haut] sous le titre et l'heure).
 */
internal fun Modifier.voileSurvol(voile: () -> Float, haut: Color, k: Float): Modifier = drawWithCache {
    val vignette = Brush.radialGradient(
        0.5f to Color.Black.copy(alpha = 0f), 1f to Color.Black.copy(alpha = 0.5f),
        center = CameraNba.ZONE.center * k, radius = 1250f * k,
    )
    val h = 215f * k
    val fondu = Brush.verticalGradient(0f to haut.copy(alpha = 0.94f), 0.55f to haut.copy(alpha = 0.8f), 1f to haut.copy(alpha = 0f), endY = h)
    onDrawBehind {
        val v = voile()
        if (v <= 0.001f) return@onDrawBehind
        drawRect(Color.Black, alpha = RetraitNba.VOILE * v)
        drawRect(vignette, alpha = v)
        drawRect(fondu, size = Size(size.width, h), alpha = v)
    }
}

// ------------------------------------------------------------------ panneau

/** Panneau de survol (px de maquette) : à droite, sous l'heure. */
internal object PanneauSurvol {
    const val GAUCHE = 1262f
    const val HAUT = 140f
    const val LARGEUR = 600f

    /** Bas du panneau quand le dock ne passe pas dessous (sinon, au-dessus du dock). */
    const val BAS = 986f
}

private val VertSurvol = Color(0xFF50DC82)

/** Hauteur d'une ligne de match (px de maquette). */
private const val HAUTEUR_MATCH = 54f
private val RougeSurvol = Color(0xFFFF5C5C)

/**
 * Panneau de survol de l'équipe [code] (affiché tant que [visible] ; fondu à l'entrée et à la
 * sortie) : en-tête et classement de la division tout de suite (classement), derniers matchs,
 * prochain match et meilleur marqueur quand la fiche [etat] est là. [bas] : bas du panneau (px de
 * maquette).
 */
@Composable
internal fun PanneauSurvolNba(
    visible: Boolean,
    code: String?,
    bilans: Map<String, BilanNba>?,
    etat: EtatFiche,
    logos: Map<String, ImageBitmap>,
    k: Float,
    bas: Float,
) {
    // Dernière équipe montrée : le panneau s'efface avec elle
    val montree = remember { arrayOfNulls<String>(1) }
    val dernierEtat = remember { arrayOfNulls<EtatFiche>(1) }
    if (visible && code != null) {
        montree[0] = code
        dernierEtat[0] = etat
    }
    val apparition = animateFloatAsState(if (visible) 1f else 0f, tween(if (visible) 220 else 160), label = "survol")
    val enVue by remember { derivedStateOf { apparition.value > 0.001f } }
    val c = montree[0] ?: return
    if (!visible && !enVue) return
    val e = if (visible) etat else dernierEtat[0] ?: EtatFiche.Chargement
    Box(
        Modifier
            .offset { IntOffset((PanneauSurvol.GAUCHE * k).roundToInt(), (PanneauSurvol.HAUT * k).roundToInt()) }
            .size(PanneauSurvol.LARGEUR.mq, (bas - PanneauSurvol.HAUT).mq)
            .graphicsLayer {
                alpha = apparition.value
                translationX = (1f - apparition.value) * 40f * k
            }
            .verreNba(k, 30f, opacite = 0.95f)
            .testTag("nba:survol"),
    ) {
        ContenuSurvol(c, bilans?.get(c), bilans, e, logos, k)
    }
}

@Composable
private fun ContenuSurvol(
    code: String,
    bilan: BilanNba?,
    bilans: Map<String, BilanNba>?,
    etat: EtatFiche,
    logos: Map<String, ImageBitmap>,
    k: Float,
) {
    val fiche = (etat as? EtatFiche.Prete)?.fiche
    Column(Modifier.fillMaxSize().padding(start = 34.mq, end = 34.mq, top = 26.mq, bottom = 22.mq)) {
        // En-tête : logo, nom, bilan et rangs ; série et meilleur marqueur
        Row(verticalAlignment = Alignment.CenterVertically) {
            DisqueEquipe(code, logos[code], 92f, 5f, k)
            Spacer(Modifier.width(20.mq))
            Column {
                TexteNba(fiche?.nomComplet ?: TextesNba.nomComplet(code), 38f, Color.White, titre = true)
                Row(verticalAlignment = Alignment.Bottom) {
                    TexteNba(TextesNba.bilan(bilan), 32f, Color.White, Modifier.alignByBaseline(), titre = true)
                    if (bilan != null) {
                        Spacer(Modifier.width(16.mq))
                        TexteNba(rangsFiche(bilan, 14f.mqs), 21f, Color.White.copy(alpha = 0.6f), Modifier.alignByBaseline(), espace = 1.2f)
                    }
                }
            }
        }
        DetailsSurvol(bilan?.let { TextesNba.serie(it.serie) }, fiche?.let { TextesNba.meilleurMarqueur(it.titulaires) }, k)
        Spacer(Modifier.weight(1f))
        Filet()
        Spacer(Modifier.weight(1f))
        // 3 derniers matchs
        IntituleSurvol("3 DERNIERS MATCHS")
        Box(Modifier.fillMaxWidth().height((3 * HAUTEUR_MATCH + 2 * 8f).mq)) {
            when (etat) {
                EtatFiche.Chargement -> Column(Modifier.testTag("nba:survol:chargement"), verticalArrangement = Arrangement.spacedBy(8.mq)) {
                    repeat(3) { LigneAttente(k) }
                }
                EtatFiche.Erreur -> MessageSurvol("Matchs indisponibles pour l'instant")
                is EtatFiche.Prete -> {
                    val derniers = etat.fiche.derniers.take(3)
                    if (derniers.isEmpty()) {
                        MessageSurvol("Aucun match joué pour l'instant")
                    } else {
                        Column(Modifier.testTag("nba:survol:matchs"), verticalArrangement = Arrangement.spacedBy(8.mq)) {
                            for (m in derniers) LigneMatch(m, logos, k)
                        }
                    }
                }
            }
        }
        Spacer(Modifier.weight(1f))
        // Prochain match
        IntituleSurvol("PROCHAIN MATCH", Modifier.padding(top = 4.mq))
        Box(Modifier.fillMaxWidth().height(40.mq), contentAlignment = Alignment.CenterStart) {
            when (etat) {
                EtatFiche.Chargement -> Barre(260f, 22f, k)
                EtatFiche.Erreur -> TexteNba("–", 21f, Color.White.copy(alpha = 0.5f), poids = FontWeight.SemiBold)
                is EtatFiche.Prete -> {
                    val m = etat.fiche.prochain
                    if (m == null) {
                        TexteNba("Pas de match prévu", 21f, Color.White.copy(alpha = 0.5f), poids = FontWeight.SemiBold)
                    } else {
                        ProchainMatch(m, logos, k)
                    }
                }
            }
        }
        Spacer(Modifier.weight(1f))
        Filet()
        Spacer(Modifier.weight(1f))
        // Classement de la division
        DivisionNba.de(code)?.let { d -> TableDivision(d, code, bilans, logos, k) }
        Spacer(Modifier.weight(1f))
        Filet()
        Spacer(Modifier.height(12.mq))
        AideSurvol(k)
    }
}

/**
 * Série et meilleur marqueur sur une ligne (« 10 victoires de suite · meilleur marqueur : … »),
 * sur deux s'ils n'y tiennent pas ; la ligne reste réservée en attendant.
 */
@Composable
private fun DetailsSurvol(serie: String?, marqueur: String?, k: Float) {
    val couleur = Color.White.copy(alpha = 0.6f)
    Layout(
        content = {
            TexteNba(serie ?: "", 19f, couleur, poids = FontWeight.SemiBold)
            TexteNba("  ·  ", 19f, couleur, poids = FontWeight.SemiBold)
            TexteNba(marqueur ?: "", 19f, couleur, poids = FontWeight.SemiBold)
        },
        modifier = Modifier.padding(top = 12.mq).fillMaxWidth(),
    ) { mesures, c ->
        val libre = c.copy(minWidth = 0, minHeight = 0)
        val (a, point, b) = mesures.map { it.measure(libre) }
        val deux = serie != null && marqueur != null
        val ligne = maxOf(a.height, b.height)
        val uneLigne = !deux || a.width + point.width + b.width <= c.maxWidth
        val haut = if (uneLigne) ligne else ligne * 2 + (4f * k).roundToInt()
        layout(c.maxWidth, haut) {
            when {
                !deux -> (if (serie != null) a else b).place(0, 0)
                uneLigne -> {
                    a.place(0, 0)
                    point.place(a.width, 0)
                    b.place(a.width + point.width, 0)
                }
                else -> {
                    a.place(0, 0)
                    b.place(0, ligne + (4f * k).roundToInt())
                }
            }
        }
    }
}

/** Filet entre deux parties du panneau. */
@Composable
private fun Filet() {
    Spacer(Modifier.fillMaxWidth().height(1.mq).drawBehind { drawRect(Color.White.copy(alpha = 0.11f)) })
}

@Composable
private fun IntituleSurvol(texte: String, modifier: Modifier = Modifier) {
    TexteNba(texte, 17f, Color.White.copy(alpha = 0.4f), modifier.padding(bottom = 8.mq), espace = 4f)
}

@Composable
private fun MessageSurvol(texte: String) {
    Box(Modifier.fillMaxWidth().height(HAUTEUR_MATCH.mq), contentAlignment = Alignment.CenterStart) {
        TexteNba(texte, 20f, Color.White.copy(alpha = 0.55f), Modifier.padding(start = 4.mq), poids = FontWeight.SemiBold)
    }
}

/** Barre grise d'attente. */
@Composable
private fun Barre(largeur: Float, hauteur: Float, k: Float) {
    Spacer(
        Modifier.width(largeur.mq).height(hauteur.mq)
            .drawBehind { drawRoundRect(Color.White.copy(alpha = 0.07f), cornerRadius = CornerRadius(8f * k)) },
    )
}

/** Fond d'une ligne de match. */
private fun Modifier.fondLigne(k: Float): Modifier = drawBehind {
    drawRoundRect(Color.White.copy(alpha = 0.05f), cornerRadius = CornerRadius(15f * k))
}

@Composable
private fun LigneAttente(k: Float) {
    Row(Modifier.fillMaxWidth().height(HAUTEUR_MATCH.mq).fondLigne(k).padding(horizontal = 14.mq), verticalAlignment = Alignment.CenterVertically) {
        Barre(34f, 30f, k)
        Spacer(Modifier.width(18.mq))
        Spacer(Modifier.size(40.mq).drawBehind { drawCircle(Color.White.copy(alpha = 0.07f)) })
        Spacer(Modifier.width(16.mq))
        Barre(110f, 24f, k)
        Spacer(Modifier.width(22.mq))
        Barre(150f, 18f, k)
    }
}

/** Un match joué : V/D, logo de l'adversaire, score (l'équipe d'abord), lieu, date. */
@Composable
private fun LigneMatch(m: MatchEquipe, logos: Map<String, ImageBitmap>, k: Float) {
    Row(Modifier.fillMaxWidth().height(HAUTEUR_MATCH.mq).fondLigne(k).padding(start = 14.mq, end = 18.mq), verticalAlignment = Alignment.CenterVertically) {
        val couleur = when (m.victoire) {
            true -> VertSurvol
            false -> RougeSurvol
            null -> Color.White.copy(alpha = 0.5f)
        }
        Box(
            Modifier.size(34.mq, 30.mq).drawBehind {
                val r = CornerRadius(9f * k)
                drawRoundRect(couleur.copy(alpha = 0.18f), cornerRadius = r)
                val e = 2f * k
                drawRoundRect(couleur, topLeft = Offset(e / 2f, e / 2f), size = Size(size.width - e, size.height - e), cornerRadius = r, style = Stroke(e))
            },
            contentAlignment = Alignment.Center,
        ) {
            TexteNba(TextesNba.issue(m), 21f, couleur, titre = true)
        }
        Spacer(Modifier.width(18.mq))
        DisqueEquipe(m.adversaire, logos[m.adversaire], 40f, 3f, k)
        Spacer(Modifier.width(16.mq))
        Box(Modifier.width(118.mq)) { TexteNba(TextesNba.score(m), 29f, Color.White, titre = true) }
        Box(Modifier.weight(1f)) {
            TexteNba(TextesNba.lieu(m), 21f, Color.White.copy(alpha = 0.62f), poids = FontWeight.SemiBold)
        }
        TexteNba(TextesNba.jour(m.debut), 19f, Color.White.copy(alpha = 0.42f), poids = FontWeight.SemiBold)
    }
}

/** Prochain match : logo de l'adversaire, « sam. 14 nov. · 3:30 », lieu. */
@Composable
private fun ProchainMatch(m: MatchEquipe, logos: Map<String, ImageBitmap>, k: Float) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        DisqueEquipe(m.adversaire, logos[m.adversaire], 40f, 3f, k)
        Spacer(Modifier.width(16.mq))
        TexteNba(TextesNba.jour(m.debut) + " · " + TextesNba.heure(m.debut), 26f, Color.White, Modifier.alignByBaseline(), titre = true)
        Spacer(Modifier.width(22.mq))
        TexteNba(TextesNba.lieu(m), 21f, Color.White.copy(alpha = 0.62f), Modifier.alignByBaseline(), poids = FontWeight.SemiBold)
    }
}

/** Classement de la division : rang, logo, code, bilan, écart avec le leader ; l'équipe survolée en clair. */
@Composable
private fun TableDivision(d: DivisionNba, code: String, bilans: Map<String, BilanNba>?, logos: Map<String, ImageBitmap>, k: Float) {
    val lignes = d.equipes.map { it to bilans?.get(it) }
        .sortedWith(compareBy<Pair<String, BilanNba?>> { it.second?.rangDivision ?: Int.MAX_VALUE }.thenBy { d.equipes.indexOf(it.first) })
    val chef = lignes.firstOrNull()?.second
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
        IntituleSurvol(TextesNba.intituleDivision(d), Modifier.weight(1f))
        TexteNba("ÉCART", 15f, Color.White.copy(alpha = 0.4f), Modifier.padding(bottom = 9.mq, end = 18.mq), espace = 2f)
    }
    Column(Modifier.testTag("nba:survol:division")) {
        for ((i, ligne) in lignes.withIndex()) {
            val (c, b) = ligne
            val choisie = c == code
            Row(
                Modifier.fillMaxWidth().height(36.mq)
                    .drawBehind { if (choisie) drawRoundRect(Color.White.copy(alpha = 0.09f), cornerRadius = CornerRadius(12f * k)) }
                    .padding(start = 8.mq, end = 18.mq),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.width(22.mq), contentAlignment = Alignment.Center) {
                    TexteNba(if (b != null) "${b.rangDivision}" else "${i + 1}", 20f, Color.White.copy(alpha = 0.4f), titre = true)
                }
                Spacer(Modifier.width(12.mq))
                DisqueEquipe(c, logos[c], 30f, 2.5f, k)
                Spacer(Modifier.width(14.mq))
                val blanc = if (choisie) Color.White else Color.White.copy(alpha = 0.82f)
                Box(Modifier.width(96.mq)) { TexteNba(c, 21f, blanc, titre = true, poids = if (choisie) FontWeight.Bold else FontWeight.SemiBold) }
                Box(Modifier.weight(1f)) { TexteNba(TextesNba.bilan(b), 21f, blanc, titre = true, poids = if (choisie) FontWeight.Bold else FontWeight.SemiBold) }
                TexteNba(if (b != null && chef != null) TextesNba.ecart(chef, b) else "", 19f, Color.White.copy(alpha = 0.6f))
            }
        }
    }
}

/** Aide : « OK » fiche complète · « FLÈCHES » équipe voisine · « RETOUR » carte entière. */
@Composable
private fun AideSurvol(k: Float) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        for ((touche, action) in listOf("OK" to "fiche complète", "FLÈCHES" to "équipe voisine", "RETOUR" to "carte entière")) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .drawBehind {
                            val e = 2f * k
                            drawRoundRect(
                                Color.White.copy(alpha = 0.43f), topLeft = Offset(e / 2f, e / 2f), size = Size(size.width - e, size.height - e),
                                cornerRadius = CornerRadius(7f * k), style = Stroke(e),
                            )
                        }
                        .padding(horizontal = 9.mq, vertical = 2.mq),
                ) {
                    TexteNba(touche, 15f, Color.White.copy(alpha = 0.8f), espace = 0.6f)
                }
                Spacer(Modifier.width(10.mq))
                TexteNba(action, 18f, Color.White.copy(alpha = 0.6f), poids = FontWeight.SemiBold)
            }
        }
    }
}
