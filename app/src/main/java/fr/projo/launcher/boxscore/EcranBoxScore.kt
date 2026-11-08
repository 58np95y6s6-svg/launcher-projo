package fr.projo.launcher.boxscore

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontSynthesis
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.em
import fr.projo.launcher.ui.GalerieSans
import fr.projo.launcher.ui.formeArrondie
import fr.projo.launcher.ui.mq
import fr.projo.launcher.ui.mqs
import fr.projo.launcher.ui.ombreExterieure
import fr.projo.launcher.ui.pxMaquette
import kotlin.math.roundToInt

/*
 * Box score NBA à l'écran (maquettes tv-resume.png et tv-boxscore.png, écran de 1920 × 1080) :
 * verre sombre (#0c0e14 à 86 %, pas de flou), coins arrondis, Manrope (police « Galerie »),
 * chiffres tabulaires, badges ronds de la couleur des équipes. Mesures en pixels de la maquette
 * (.mq). Aucune animation : l'écran ne change qu'avec les données.
 */

private val FondVerre = Color(0xFF0C0E14).copy(alpha = 0.86f)
private val Lisere = Color.White.copy(alpha = 0.09f)
private val RougeDirect = Color(0xFFFF5A5F)
private val PointDirect = Color(0xFFFF3B42)

/** Largeur des colonnes du tableau des joueurs (MIN, PTS, REB, PD, TIRS, 3 PTS, +/-), marge de 14 comprise. */
private val Colonnes = listOf("MIN" to 50, "PTS" to 55, "REB" to 52, "PD" to 49, "TIRS" to 89, "3 PTS" to 88, "+/-" to 49)

/** Place des lignes de joueurs dans le panneau (hauteur de l'écran moins le reste du panneau). */
private const val PLACE_LIGNES = 505f
private const val LIGNE = 37f

/**
 * Contenu de la fenêtre : la carte (résumé) ou le panneau (détails) en haut à droite, à 56 px des
 * bords, avec de la place à gauche et en bas pour l'ombre. Rien quand le box score est caché.
 */
@Composable
fun EcranBoxScore(vue: VueTv, modifier: Modifier = Modifier) {
    if (vue.affichage == Affichage.CACHE) return
    Box(modifier.padding(start = 96.mq, top = 56.mq, end = 56.mq, bottom = 120.mq)) {
        val box = vue.box
        when {
            box == null -> CarteAttente(vue.attente.orEmpty(), details = vue.affichage == Affichage.DETAILS)
            vue.affichage == Affichage.RESUME -> CarteResume(box)
            else -> PanneauDetails(box, vue.equipe)
        }
    }
}

// ------------------------------------------------------------------ formats

@Composable
private fun CarteResume(b: BoxScore) {
    Verre(largeur = 600, rayon = 28, haut = 22, cotes = 26, bas = 22, flou = 70, dy = 24, modifier = Modifier.testTag("nba_resume")) {
        Tete(b.match, MesuresResume)
        val joueurs = b.ext.joueurs.any { it.aJoue } || b.dom.joueurs.any { it.aJoue }
        if (joueurs) {
            TitreSection("MEILLEURS JOUEURS")
            Meilleur(b, "PTS") { it.points }
            Meilleur(b, "REB") { it.rebonds }
            Meilleur(b, "PD") { it.passes }
        }
        val se = b.ext.stats
        val sd = b.dom.stats
        if (se != null || sd != null) {
            TitreSection("ÉQUIPES")
            Comparaison(b, "TIRS", se?.pctTirs, sd?.pctTirs, pourcentage = true)
            Comparaison(b, "3 PTS", se?.pctTrois, sd?.pctTrois, pourcentage = true)
            Comparaison(b, "REBONDS", se?.rebonds?.toDouble(), sd?.rebonds?.toDouble(), pourcentage = false)
            Comparaison(b, "PERTES", se?.pertes?.toDouble(), sd?.pertes?.toDouble(), pourcentage = false)
        }
    }
}

@Composable
private fun PanneauDetails(b: BoxScore, equipe: Int) {
    Verre(largeur = 680, rayon = 32, haut = 30, cotes = 32, bas = 24, flou = 90, dy = 30, modifier = Modifier.testTag("nba_details")) {
        Tete(b.match, MesuresDetails)
        Periodes(b.match)
        Row(Modifier.fillMaxWidth().padding(bottom = 14.mq), horizontalArrangement = Arrangement.spacedBy(10.mq)) {
            Onglet(nomEquipe(b.match.ext), equipe == 0, b.match.ext.code, Modifier.weight(1f))
            Onglet(nomEquipe(b.match.dom), equipe == 1, b.match.dom.code, Modifier.weight(1f))
        }
        val (eq, eqMatch) = if (equipe == 0) b.ext to b.match.ext else b.dom to b.match.dom
        Tableau(eq, eqMatch, b.surTerrainConnu)
        Aide()
    }
}

/** Avant la première réponse assez ancienne : « Synchronisation avec la vidéo… ». */
@Composable
private fun CarteAttente(texte: String, details: Boolean) {
    Verre(
        largeur = if (details) 680 else 600, rayon = if (details) 32 else 28, haut = 34, cotes = 26, bas = 34, flou = 70, dy = 24,
        modifier = Modifier.testTag("nba_attente"),
    ) {
        Texte(texte, 20f, gras = true, couleur = Color.White.copy(alpha = 0.85f), modifier = Modifier.fillMaxWidth(), alignement = TextAlign.Center)
    }
}

// ------------------------------------------------------------------ verre

@Composable
private fun Verre(
    largeur: Int,
    rayon: Int,
    haut: Int,
    cotes: Int,
    bas: Int,
    flou: Int,
    dy: Int,
    modifier: Modifier = Modifier,
    contenu: @Composable ColumnScope.() -> Unit,
) {
    val px = pxMaquette()
    val forme = RoundedCornerShape(rayon.mq)
    Column(
        modifier
            .width(largeur.mq)
            .drawBehind {
                // Ombre portée à l'extérieur seulement (jamais sous le verre translucide)
                val contour: Path = formeArrondie(0f, 0f, size.width, size.height, rayon * px)
                ombreExterieure(contour, size.width, size.height, flou * px, dy * px, Color.Black.copy(alpha = 0.5f))
            }
            .background(FondVerre, forme)
            .border(1.5f.mq, Lisere, forme)
            .padding(start = cotes.mq, end = cotes.mq, top = haut.mq, bottom = bas.mq),
        content = contenu,
    )
}

// ------------------------------------------------------------------ en-tête

private class Mesures(val badge: Int, val badgeTexte: Float, val nom: Float, val points: Float, val direct: Float, val temps: Float)

private val MesuresResume = Mesures(badge = 48, badgeTexte = 15f, nom = 15f, points = 44f, direct = 12f, temps = 22f)
private val MesuresDetails = Mesures(badge = 62, badgeTexte = 19f, nom = 18f, points = 60f, direct = 15f, temps = 30f)

/** Badges, noms, scores (l'équipe menée en retrait), « EN DIRECT » et statut au centre. */
@Composable
private fun Tete(m: MatchNba, d: Mesures) {
    val extMenee = m.ext.score < m.dom.score
    val domMenee = m.dom.score < m.ext.score
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
            Badge(m.ext.code, d)
            Spacer(Modifier.width(16.mq))
            Column {
                Texte(nomEquipe(m.ext), d.nom, gras = false, couleur = Color.White.copy(alpha = 0.7f))
                Score(m.ext.score, d.points, extMenee)
            }
        }
        Column(Modifier.padding(horizontal = 10.mq), horizontalAlignment = Alignment.CenterHorizontally) {
            if (m.direct) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Canvas(Modifier.size(10.mq)) {
                        val r = size.minDimension / 2f
                        drawCircle(Brush.radialGradient(listOf(PointDirect.copy(alpha = 0.45f), Color.Transparent), center, r * 2.8f), r * 2.8f)
                        drawCircle(PointDirect, r)
                    }
                    Spacer(Modifier.width(8.mq))
                    Texte("EN DIRECT", d.direct, gras = true, couleur = RougeDirect, espacement = 0.12f)
                }
            }
            Texte(m.statut, d.temps, gras = true, modifier = if (m.direct) Modifier.padding(top = 4.mq) else Modifier)
        }
        Row(Modifier.weight(1f), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
            Column(horizontalAlignment = Alignment.End) {
                Texte(nomEquipe(m.dom), d.nom, gras = false, couleur = Color.White.copy(alpha = 0.7f))
                Score(m.dom.score, d.points, domMenee)
            }
            Spacer(Modifier.width(16.mq))
            Badge(m.dom.code, d)
        }
    }
}

/** Score : ligne aussi haute que les chiffres (line-height 1 de la maquette), chiffres centrés dedans. */
@Composable
private fun Score(points: Int, taille: Float, menee: Boolean) {
    Box(Modifier.height(taille.mq).wrapContentHeight(Alignment.CenterVertically, unbounded = true)) {
        Texte(points.toString(), taille, gras = true, couleur = Color.White.copy(alpha = if (menee) 0.55f else 1f), espacement = -0.02f)
    }
}

/** Cercle de la couleur de l'équipe avec son code (aucun logo). */
@Composable
private fun Badge(code: String, d: Mesures) {
    val e = EquipesNba.de(code)
    Box(Modifier.size(d.badge.mq).background(couleur(e.couleur), CircleShape), contentAlignment = Alignment.Center) {
        Texte(code, d.badgeTexte, gras = true, couleur = couleur(e.texte), espacement = 0.02f)
    }
}

// ------------------------------------------------------------------ résumé

@Composable
private fun TitreSection(titre: String) {
    Texte(
        titre, 12f, gras = true, couleur = Color.White.copy(alpha = 0.45f), espacement = 0.12f,
        modifier = Modifier.fillMaxWidth().padding(top = 16.mq, bottom = 6.mq), alignement = TextAlign.Center,
    )
}

/** Meilleur joueur de chaque équipe pour une statistique (chiffre de la couleur de l'équipe). */
@Composable
private fun Meilleur(b: BoxScore, libelle: String, stat: (JoueurNba) -> Int) {
    val je = b.ext.joueurs.filter { it.aJoue }.maxByOrNull(stat)
    val jd = b.dom.joueurs.filter { it.aJoue }.maxByOrNull(stat)
    Row(Modifier.fillMaxWidth().height(34.mq), verticalAlignment = Alignment.CenterVertically) {
        Row(Modifier.weight(1f)) {
            if (je != null) {
                Texte(stat(je).toString(), 20f, gras = true, couleur = accent(b.match.ext.code), modifier = Modifier.alignByBaseline())
                Spacer(Modifier.width(10.mq))
                Texte(je.nom, 18f, gras = true, modifier = Modifier.alignByBaseline())
            }
        }
        Box(Modifier.width(64.mq), contentAlignment = Alignment.Center) {
            Texte(libelle, 12f, gras = true, couleur = Color.White.copy(alpha = 0.5f), espacement = 0.1f)
        }
        Row(Modifier.weight(1f), horizontalArrangement = Arrangement.End) {
            if (jd != null) {
                Texte(jd.nom, 18f, gras = true, modifier = Modifier.alignByBaseline())
                Spacer(Modifier.width(10.mq))
                Texte(stat(jd).toString(), 20f, gras = true, couleur = accent(b.match.dom.code), modifier = Modifier.alignByBaseline())
            }
        }
    }
}

/** Statistique d'équipe : valeur et barre de chaque côté (pourcentage, ou part du total). */
@Composable
private fun Comparaison(b: BoxScore, libelle: String, gauche: Double?, droite: Double?, pourcentage: Boolean) {
    val (fg, fd) = if (pourcentage) {
        (gauche ?: 0.0) to (droite ?: 0.0)
    } else {
        val total = (gauche ?: 0.0) + (droite ?: 0.0)
        if (total <= 0.0) 0.0 to 0.0 else (gauche ?: 0.0) / total to (droite ?: 0.0) / total
    }
    Row(Modifier.fillMaxWidth().height(30.mq), verticalAlignment = Alignment.CenterVertically) {
        Texte(valeur(gauche, pourcentage), 17f, gras = true, modifier = Modifier.width(70.mq))
        Barre(part = fg.toFloat(), depuisLaDroite = true, couleur = couleur(EquipesNba.de(b.match.ext.code).couleur), modifier = Modifier.weight(1f))
        Texte(
            libelle, 12f, gras = true, couleur = Color.White.copy(alpha = 0.5f), espacement = 0.1f,
            modifier = Modifier.width(120.mq), alignement = TextAlign.Center,
        )
        Barre(part = fd.toFloat(), depuisLaDroite = false, couleur = couleur(EquipesNba.de(b.match.dom.code).couleur), modifier = Modifier.weight(1f))
        Texte(valeur(droite, pourcentage), 17f, gras = true, modifier = Modifier.width(70.mq), alignement = TextAlign.End)
    }
}

@Composable
private fun Barre(part: Float, depuisLaDroite: Boolean, couleur: Color, modifier: Modifier) {
    val forme = RoundedCornerShape(3.mq)
    Box(modifier.height(6.mq).clip(forme).background(Color.White.copy(alpha = 0.1f))) {
        if (part > 0f) {
            Box(
                Modifier
                    .align(if (depuisLaDroite) Alignment.CenterEnd else Alignment.CenterStart)
                    .fillMaxHeight()
                    .fillMaxWidth(part.coerceIn(0f, 1f))
                    .background(couleur, forme),
            )
        }
    }
}

private fun valeur(v: Double?, pourcentage: Boolean): String = when {
    v == null -> "–"
    pourcentage -> "${(v * 100).roundToInt()} %"
    else -> v.roundToInt().toString()
}

// ------------------------------------------------------------------ détails

/** Points par quart-temps (et prolongations) des deux équipes. */
@Composable
private fun Periodes(m: MatchNba) {
    val n = maxOf(4, m.ext.periodes.size, m.dom.periodes.size, m.periode)
    val prolongations = n - 4
    val titres = (0 until n).map { i ->
        when {
            i < 4 -> "${i + 1}"
            prolongations == 1 -> "P"
            else -> "P${i - 3}"
        }
    }
    Column(
        Modifier
            .padding(top = 20.mq, bottom = 18.mq)
            .fillMaxWidth()
            .background(Color.White.copy(alpha = 0.05f), RoundedCornerShape(16.mq))
            .padding(horizontal = 16.mq, vertical = 12.mq),
        verticalArrangement = Arrangement.spacedBy(6.mq),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Spacer(Modifier.width(92.mq))
            for (t in titres) {
                Texte(t, 14f, gras = true, couleur = Color.White.copy(alpha = 0.5f), espacement = 0.08f, modifier = Modifier.weight(1f), alignement = TextAlign.Center)
            }
            Texte("TOTAL", 14f, gras = true, couleur = Color.White.copy(alpha = 0.5f), espacement = 0.08f, modifier = Modifier.width(70.mq), alignement = TextAlign.Center)
        }
        for (e in listOf(m.ext, m.dom)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Texte(e.code, 18f, gras = true, modifier = Modifier.width(92.mq))
                for (i in 0 until n) {
                    // Période pas encore jouée : case vide
                    val v = e.periodes.getOrNull(i)?.takeUnless { m.direct && i + 1 > m.periode }
                    Texte(v?.toString().orEmpty(), 18f, gras = false, modifier = Modifier.weight(1f), alignement = TextAlign.Center)
                }
                Texte(e.score.toString(), 18f, gras = true, modifier = Modifier.width(70.mq), alignement = TextAlign.Center)
            }
        }
    }
}

/** Onglet d'une équipe ; l'onglet actif prend la couleur de l'équipe, avec un anneau blanc. */
@Composable
private fun Onglet(nom: String, actif: Boolean, code: String, modifier: Modifier) {
    val e = EquipesNba.de(code)
    val fond = if (actif) couleur(e.couleur) else Color.White.copy(alpha = 0.06f)
    val texte = when {
        !actif -> Color.White.copy(alpha = 0.7f)
        contraste(Color.White, fond) >= 3f -> Color.White
        else -> couleur(e.texte)
    }
    val px = pxMaquette()
    val anneau = if (!actif) Modifier else Modifier.drawBehind {
        val a = 2f * px
        drawRoundRect(
            Color.White.copy(alpha = 0.85f),
            topLeft = Offset(-a, -a),
            size = Size(size.width + 2 * a, size.height + 2 * a),
            cornerRadius = CornerRadius(size.height / 2f + a),
        )
    }
    Box(
        modifier.height(50.mq).then(anneau).background(fond, RoundedCornerShape(25.mq)),
        contentAlignment = Alignment.Center,
    ) {
        Texte(nom, 19f, gras = true, couleur = texte)
    }
}

/**
 * Tableau JOUEUR, MIN, PTS, REB, PD, TIRS, 3 PTS, +/- : titulaires, puis « BANC » (joueurs
 * n'ayant pas joué exclus), point de couleur pour ceux sur le terrain, meilleur marqueur coloré,
 * ligne « Équipe ».
 */
@Composable
private fun Tableau(eq: EquipeBox, em: EquipeMatch, surTerrainConnu: Boolean) {
    val joues = eq.joueurs.filter { it.aJoue }
    val titulaires = joues.filter { it.titulaire }
    val banc = joues.filter { !it.titulaire }
    val accent = accent(em.code)
    val meilleur = joues.maxByOrNull { it.points }?.takeIf { it.points > 0 }
    // Beaucoup de joueurs (large écart) : lignes resserrées pour tenir dans l'écran
    val hauteur = if (joues.isEmpty()) LIGNE else minOf(LIGNE, maxOf(28f, PLACE_LIGNES / joues.size))
    Row(Modifier.fillMaxWidth().padding(bottom = 8.mq)) {
        Texte("JOUEUR", 13f, gras = true, couleur = Color.White.copy(alpha = 0.5f), espacement = 0.08f, modifier = Modifier.weight(1f))
        for ((titre, largeur) in Colonnes) {
            Texte(titre, 13f, gras = true, couleur = Color.White.copy(alpha = 0.5f), espacement = 0.08f, modifier = Modifier.width(largeur.mq), alignement = TextAlign.End)
        }
    }
    if (joues.isEmpty()) {
        Texte(
            "Statistiques bientôt disponibles", 17f, gras = false, couleur = Color.White.copy(alpha = 0.6f),
            modifier = Modifier.fillMaxWidth().filet(Color.White.copy(alpha = 0.06f), 1f).padding(vertical = 24.mq), alignement = TextAlign.Center,
        )
    }
    for (j in titulaires) LigneJoueur(j, hauteur, accent, j === meilleur, surTerrainConnu)
    if (banc.isNotEmpty()) {
        Box(Modifier.fillMaxWidth().height(30.mq).padding(top = 8.mq), contentAlignment = Alignment.CenterStart) {
            Texte("BANC", 13f, gras = true, couleur = Color.White.copy(alpha = 0.45f), espacement = 0.1f)
        }
        for (j in banc) LigneJoueur(j, hauteur, accent, j === meilleur, surTerrainConnu)
    }
    val s = eq.stats
    Row(
        Modifier.fillMaxWidth().height(42.mq).filet(Color.White.copy(alpha = 0.3f), 1.5f),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Texte("Équipe", 19f, gras = true, modifier = Modifier.weight(1f))
        Cellule("", 0)
        Cellule(em.score.toString(), 1, gras = true)
        Cellule(s?.rebonds?.toString() ?: "–", 2, gras = true)
        Cellule(s?.passes?.toString() ?: "–", 3, gras = true)
        Cellule(tirs(s?.tirsReussis, s?.tirsTentes), 4, gras = true)
        Cellule(tirs(s?.troisReussis, s?.troisTentes), 5, gras = true)
        Cellule("", 6)
    }
}

@Composable
private fun LigneJoueur(j: JoueurNba, hauteur: Float, accent: Color, meilleur: Boolean, surTerrainConnu: Boolean) {
    Row(
        Modifier.fillMaxWidth().height(hauteur.mq).filet(Color.White.copy(alpha = 0.06f), 1f),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
            // Point de couleur : joueur sur le terrain (source NBA seulement)
            if (surTerrainConnu) {
                Box(Modifier.size(8.mq).then(if (j.surTerrain == true) Modifier.background(accent, CircleShape) else Modifier))
                Spacer(Modifier.width(10.mq))
            }
            Texte(j.nom, 19f, gras = true)
        }
        Cellule(j.minutes.toString(), 0)
        Cellule(j.points.toString(), 1, gras = meilleur, couleur = if (meilleur) accent else Color.White)
        Cellule(j.rebonds.toString(), 2)
        Cellule(j.passes.toString(), 3)
        Cellule("${j.tirsReussis}-${j.tirsTentes}", 4)
        Cellule("${j.troisReussis}-${j.troisTentes}", 5)
        Cellule(j.plusMoins?.let { if (it > 0) "+$it" else it.toString() }.orEmpty(), 6)
    }
}

@Composable
private fun Cellule(texte: String, colonne: Int, gras: Boolean = false, couleur: Color = Color.White) {
    Texte(texte, 19f, gras = gras, couleur = couleur, modifier = Modifier.width(Colonnes[colonne].second.mq), alignement = TextAlign.End)
}

private fun tirs(reussis: Int?, tentes: Int?): String = if (reussis == null || tentes == null) "–" else "$reussis-$tentes"

/** « ◀ ▶ Équipe   Retour Masquer » : touches de la télécommande de la TV. */
@Composable
private fun Aide() {
    val texte = Color.White.copy(alpha = 0.62f)
    Row(
        Modifier.fillMaxWidth().padding(top = 18.mq),
        horizontalArrangement = Arrangement.spacedBy(28.mq, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Touche {
                Canvas(Modifier.width(24.mq).height(12.mq)) {
                    val w = size.width
                    val h = size.height
                    val t = w * 10f / 24f
                    val gauche = Path().apply { moveTo(0f, h / 2f); lineTo(t, 0f); lineTo(t, h); close() }
                    val droite = Path().apply { moveTo(w, h / 2f); lineTo(w - t, 0f); lineTo(w - t, h); close() }
                    drawPath(gauche, texte)
                    drawPath(droite, texte)
                }
            }
            Texte("Équipe", 16f, gras = false, couleur = texte)
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Touche { Texte("Retour", 14f, gras = true, couleur = texte) }
            Texte("Masquer", 16f, gras = false, couleur = texte)
        }
    }
}

@Composable
private fun Touche(contenu: @Composable () -> Unit) {
    Box(
        Modifier
            .padding(end = 8.mq)
            .heightIn(min = 30.mq)
            .widthIn(min = 34.mq)
            .border(1.5f.mq, Color.White.copy(alpha = 0.31f), RoundedCornerShape(8.mq))
            .padding(horizontal = 8.mq),
        contentAlignment = Alignment.Center,
    ) { contenu() }
}

// ------------------------------------------------------------------ outils

/** Filet horizontal en haut d'une ligne ([epaisseur] en px de la maquette). */
@Composable
private fun Modifier.filet(couleur: Color, epaisseur: Float): Modifier {
    val px = pxMaquette()
    return drawBehind { drawRect(couleur, size = Size(size.width, epaisseur * px)) }
}

/** Texte Manrope (moyen : 600 de la maquette ; gras : 700 à 900), chiffres tabulaires, sur une ligne. */
@Composable
private fun Texte(
    texte: String,
    taille: Float,
    gras: Boolean,
    modifier: Modifier = Modifier,
    couleur: Color = Color.White,
    espacement: Float = 0f,
    alignement: TextAlign = TextAlign.Start,
) {
    // Hauteur de ligne : celle de la police (comme « normal » en CSS)
    val taillePx = taille.mqs
    val style = remember(taillePx, gras, couleur, espacement, alignement) {
        TextStyle(
            color = couleur,
            fontSize = taillePx,
            fontWeight = if (gras) FontWeight.Bold else FontWeight.Medium,
            fontFamily = GalerieSans,
            fontSynthesis = FontSynthesis.None,
            fontFeatureSettings = "tnum",
            letterSpacing = espacement.em,
            textAlign = alignement,
        )
    }
    BasicText(texte, modifier, style = style, maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)
}

private fun nomEquipe(e: EquipeMatch): String = e.nom.ifEmpty { EquipesNba.de(e.code).nom }.ifEmpty { e.code }

private fun accent(code: String): Color = couleur(EquipesNba.de(code).accent)

/** « #rrggbb » → couleur opaque (gris si illisible). */
internal fun couleur(hex: String): Color {
    val v = hex.removePrefix("#").toLongOrNull(16) ?: return Color.Gray
    return Color(0xFF000000 or (v and 0xFFFFFF))
}

/** Rapport de contraste WCAG entre deux couleurs opaques. */
internal fun contraste(a: Color, b: Color): Float {
    val la = a.luminance()
    val lb = b.luminance()
    return (maxOf(la, lb) + 0.05f) / (minOf(la, lb) + 0.05f)
}
