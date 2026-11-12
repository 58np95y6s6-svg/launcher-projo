package fr.projo.launcher.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.AlignmentLine
import androidx.compose.ui.layout.FirstBaseline
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.BaselineShift
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.TextUnit
import fr.projo.launcher.nba.BilanNba
import fr.projo.launcher.nba.DivisionNba
import kotlin.math.abs
import kotlin.math.roundToInt

/*
 * Design « NBA » : les 30 pastilles posées sur la carte (logos, bilans et rangs) et la
 * navigation de l'une à l'autre. Leurs capsules sont déjà dans l'image de la carte (CarteNba.kt) ;
 * la pastille sélectionnée est redessinée entière par-dessus, agrandie et cerclée de blanc.
 */

/** Or des leaders de division (« 1er DIV. »). */
internal val OrNba = Color(0xFFF6C445)

/** Direction de la télécommande sur la carte. */
enum class SensNba { GAUCHE, HAUT, DROITE, BAS }

/**
 * Navigation entre pastilles : la plus proche dans la direction, dans un cône de ±60°, à distance
 * pondérée (avance + 2 × écart de côté : on préfère ce qui est dans l'axe).
 */
object NavCarteNba {
    /** tan 60° */
    private const val CONE = 1.7320508f
    private const val POIDS_TRAVERS = 2f

    /** Pastille la plus proche de [depuis] vers [sens] parmi [cibles] (centres, px de référence), sauf [sauf] ; null : aucune. */
    fun voisine(depuis: Offset, sens: SensNba, cibles: Map<String, Offset>, sauf: String? = null): String? {
        var choix: String? = null
        var meilleur = Float.MAX_VALUE
        for ((code, p) in cibles) {
            if (code == sauf) continue
            val dx = p.x - depuis.x
            val dy = p.y - depuis.y
            val avance = when (sens) {
                SensNba.GAUCHE -> -dx
                SensNba.DROITE -> dx
                SensNba.HAUT -> -dy
                SensNba.BAS -> dy
            }
            val travers = if (sens == SensNba.GAUCHE || sens == SensNba.DROITE) abs(dy) else abs(dx)
            if (avance < 1f || travers > avance * CONE) continue
            val score = avance + POIDS_TRAVERS * travers
            if (score < meilleur) {
                meilleur = score
                choix = code
            }
        }
        return choix
    }
}

/** Texte du design NBA : Oswald ([titre]) ou Barlow Condensed ; tailles en px de maquette. */
@Composable
internal fun TexteNba(
    texte: String,
    taille: Float,
    couleur: Color,
    modifier: Modifier = Modifier,
    titre: Boolean = false,
    poids: FontWeight = FontWeight.Bold,
    espace: Float = 0f,
    lignes: Int = 1,
) {
    BasicText(
        texte,
        modifier,
        style = TextStyle(
            color = couleur, fontSize = taille.mqs, fontFamily = if (titre) NbaTitre else NbaEtroit,
            fontWeight = poids, letterSpacing = espace.mqs,
        ),
        maxLines = lignes,
        softWrap = lignes > 1,
        overflow = TextOverflow.Ellipsis,
    )
}

@Composable
internal fun TexteNba(
    texte: AnnotatedString,
    taille: Float,
    couleur: Color,
    modifier: Modifier = Modifier,
    titre: Boolean = false,
    poids: FontWeight = FontWeight.Bold,
    espace: Float = 0f,
) {
    BasicText(
        texte,
        modifier,
        style = TextStyle(
            color = couleur, fontSize = taille.mqs, fontFamily = if (titre) NbaTitre else NbaEtroit,
            fontWeight = poids, letterSpacing = espace.mqs,
        ),
        maxLines = 1,
        softWrap = false,
    )
}

/**
 * Place un texte par sa ligne de base : ([x], [base]) en px de maquette, dans le repère du parent ;
 * [ancre] 0 : à gauche de x, 0,5 : centré, 1 : à droite. L'élément ne prend aucune place.
 */
internal fun Modifier.ligneDeBase(x: Float, base: Float, k: Float, ancre: Float = 0f): Modifier = layout { m, _ ->
    val p = m.measure(Constraints())
    val b = p[FirstBaseline].let { if (it == AlignmentLine.Unspecified) p.height else it }
    layout(0, 0) { p.place((x * k - p.width * ancre).roundToInt(), (base * k - b).roundToInt()) }
}

/** Rangs d'une pastille, « er »/« e » en exposant (taille [exposants]) : « 1er OUEST · 1er DIV. » (« 1er DIV. » en or pour le leader). */
internal fun rangsPastille(b: BilanNba, exposants: TextUnit): AnnotatedString = buildAnnotatedString {
    val conf = DivisionNba.de(b.code)?.conference ?: return@buildAnnotatedString
    val exposant = SpanStyle(fontSize = exposants, baselineShift = BaselineShift(0.55f))
    fun rang(n: Int) {
        val (chiffre, suffixe) = TextesNba.rang(n)
        append(chiffre)
        withStyle(exposant) { append(suffixe) }
    }
    rang(b.rangConference)
    append(" " + TextesNba.conference(conf))
    withStyle(SpanStyle(color = Color.White.copy(alpha = 0.4f))) { append(" · ") }
    if (b.rangDivision == 1) {
        withStyle(SpanStyle(color = OrNba)) {
            rang(1)
            append(" DIV.")
        }
    } else {
        rang(b.rangDivision)
        append(" DIV.")
    }
}

/**
 * Logos, bilans et rangs des 30 pastilles ([bilans] null : pas encore de classement, « –– »
 * et pas de rangs), puis la pastille [choisie] par-dessus. [surClic] : toucher ou clic sur une
 * pastille.
 */
@Composable
internal fun CouchePastilles(
    carte: CarteNba,
    bilans: Map<String, BilanNba>?,
    logos: Map<String, ImageBitmap>,
    choisie: String?,
    k: Float,
    surClic: (String) -> Unit,
) {
    Box(Modifier.fillMaxSize()) {
        // Tous les logos d'un seul dessin
        Spacer(
            Modifier.fillMaxSize().drawBehind {
                for (p in carte.pastilles) {
                    val logo = logos[p.code] ?: continue
                    dessinerLogo(logo, Offset(p.x * k, p.y * k), COTE_LOGO * k)
                }
            },
        )
        for (p in carte.pastilles) {
            key(p.code) {
                val ligne = FormePastille.ligne(p)
                Box(
                    Modifier
                        .offset { IntOffset((ligne.left * k).roundToInt(), (ligne.top * k).roundToInt()) }
                        .size(ligne.width.mq, ligne.height.mq)
                        .testTag("nba:pastille:" + p.code)
                        .semantics { selected = p.code == choisie }
                        .cliquableSansFocus { surClic(p.code) },
                ) {
                    ContenuPastille(p, bilans, logos[p.code] != null, Offset(ligne.left, ligne.top), k)
                }
            }
        }
        val membres = choisie?.let { c -> carte.capsules.firstOrNull { m -> m.any { it.code == c } } }
        if (choisie != null && membres != null) PastilleChoisie(membres, choisie, bilans, logos, k)
    }
}

/** Côté du logo dans son disque (px de maquette). */
private const val COTE_LOGO = 40f

/** Textes d'une équipe (et son code tant que le logo manque), [origine] : coin du parent, px de maquette. */
@Composable
private fun ContenuPastille(p: PlacePastilleNba, bilans: Map<String, BilanNba>?, aLogo: Boolean, origine: Offset, k: Float) {
    val b = bilans?.get(p.code)
    if (!aLogo) {
        TexteNba(
            p.code, 19f, FormePastille.lettres(p.code),
            Modifier.ligneDeBase(p.x - origine.x, p.y + 7f - origine.y, k, ancre = 0.5f), titre = true,
        )
    }
    val tx = FormePastille.texteX(p) - origine.x
    // Sans bilan : « –– » seul, au milieu de la ligne
    TexteNba(TextesNba.bilan(b), 27f, Color.White, Modifier.ligneDeBase(tx, p.y + (if (b == null) 10f else -3f) - origine.y, k), titre = true)
    if (b != null) {
        TexteNba(
            rangsPastille(b, 11f.mqs), 16f, Color.White.copy(alpha = 0.72f),
            Modifier.ligneDeBase(tx, p.y + 20f - origine.y, k), espace = 1.2f,
        )
    }
}

/** Pastille sélectionnée : capsule (ou groupe) redessinée par-dessus, agrandie (× 1,08) et cerclée de blanc. */
@Composable
private fun PastilleChoisie(
    membres: List<PlacePastilleNba>,
    choisie: String,
    bilans: Map<String, BilanNba>?,
    logos: Map<String, ImageBitmap>,
    k: Float,
) {
    val r = FormePastille.capsule(membres)
    val ligne = FormePastille.ligne(membres.first { it.code == choisie })
    Box(
        Modifier
            .offset { IntOffset((r.left * k).roundToInt(), (r.top * k).roundToInt()) }
            .size(r.width.mq, r.height.mq)
            .graphicsLayer {
                scaleX = 1.08f
                scaleY = 1.08f
            }
            .drawBehind {
                scale(k, k, pivot = Offset.Zero) {
                    translate(-r.left, -r.top) {
                        dessinerCapsule(membres, ombre = null, contour = ligne)
                        for (m in membres) logos[m.code]?.let { dessinerLogo(it, Offset(m.x, m.y), COTE_LOGO) }
                    }
                }
            },
    ) {
        for (m in membres) ContenuPastille(m, bilans, logos[m.code] != null, r.topLeft, k)
    }
}
