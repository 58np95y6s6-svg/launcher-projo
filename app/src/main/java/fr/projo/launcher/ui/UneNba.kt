package fr.projo.launcher.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.layout.AlignmentLine
import androidx.compose.ui.layout.FirstBaseline
import androidx.compose.ui.layout.LastBaseline
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.Placeable
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.em
import androidx.tv.material3.Text
import fr.projo.launcher.data.NbaGame
import fr.projo.launcher.data.NbaRepository
import fr.projo.launcher.data.NbaTeam
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Chiffres des scores : Black, interligne serré (la boîte du texte colle aux chiffres). */
private val UneStyleScore = TextStyle(
    fontFamily = UnePolice,
    fontWeight = FontWeight.Black,
    letterSpacing = (-0.04).em,
    lineHeight = 0.8.em,
    lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.Both),
    fontFeatureSettings = "tnum",
)

/**
 * Score NBA façon journal sportif : « NBA  CETTE NUIT  RÉSULTATS » (onglets focalisables),
 * le match à la une en chiffres géants (meneur ▲, pastille DIRECT, temps de jeu), puis les
 * matchs suivants sur une ligne. Sans match / sans réseau : un message à la place du score.
 * ◀ ▶ changent d'onglet ; ◀ sur le premier onglet ou Retour : retour aux favoris.
 */
@Composable
internal fun UneNba(ctx: HomeContext, nav: UneNav, montrer: (UneSujet) -> Unit, modifier: Modifier) {
    var onglet by remember { mutableIntStateOf(0) }
    val data = ctx.nbaData
    val nuit = data?.tonight
    val libelleNuit = if (nuit == null || nuit.isTonight) "Cette nuit" else NbaRepository.libelleJour(nuit.day)

    val jeux = (if (onglet == 0) nuit?.games else data?.results?.games).orEmpty()
    val vedette: NbaGame? = when {
        onglet == 1 -> jeux.firstOrNull()
        else -> jeux.firstOrNull { it.state == "in" } ?: jeux.firstOrNull { it.state == "pre" } ?: jeux.lastOrNull()
    }
    val autres = jeux.filter { it !== vedette }.let { l ->
        if (onglet == 0) l.filter { it.state != "post" }.ifEmpty { l } else l
    }.take(2)

    // Repères verticaux de la maquette (ligne de base, en .u depuis le haut de l'affiche)
    Box(
        modifier
            .testTag("nba")
            .width(360.u)
            .height(184.u)
            .onPreviewKeyEvent { nav.toucheNba(it, ctx) }
    ) {
        Row(Modifier.uneLigne(0.u, 83.u), verticalAlignment = Alignment.CenterVertically) {
            Text(
                "NBA", color = Color.White, fontSize = 13.us, fontWeight = FontWeight.Black,
                fontFamily = UnePolice, letterSpacing = 0.06.em, maxLines = 1,
            )
            Spacer(Modifier.width(7.u))
            val titres = listOf(libelleNuit, "Résultats")
            titres.forEachIndexed { i, titre ->
                val cle = "nba:$i"
                val actif = onglet == i
                UneBouton(
                    onClick = { onglet = i },
                    onFocus = {
                        onglet = i
                        nav.ongletNba = i
                        nav.focusCle = cle
                        ctx.sansSelection()
                        montrer(
                            UneSujet(
                                cle = "nba", titre = "NBA", suivant = if (i == 0) "Résultats" else null,
                                rubrique = "NBA", position = i + 1, total = 2,
                                detail = if (i == 0) titre + " · scores et horaires" else "Scores de la nuit passée",
                                action = null, couleur = UneBleuNba, ordre = 1000 + i,
                            )
                        )
                    },
                    hauteur = 24.u,
                    rayon = 12.u,
                    padding = PaddingValues(horizontal = 6.u),
                    modifier = Modifier
                        .testTag("nba:onglet$i")
                        .onGloballyPositioned { nav.centres[cle] = it.positionInRoot().x + it.size.width / 2f }
                        .then(nav.cible(cle)),
                ) { focus ->
                    val souligne = actif && !focus
                    val trait = 1.5f.uf
                    val ecart = 3.u
                    Text(
                        titre.uppercase(Locale.FRENCH),
                        fontSize = 13.us, fontWeight = FontWeight.Bold, fontFamily = UnePolice,
                        letterSpacing = 0.12.em, maxLines = 1,
                        color = if (focus) UneEncreSombre else Color.White.copy(alpha = if (actif) 1f else 0.72f),
                        modifier = Modifier.drawBehind {
                            if (souligne) {
                                val y = size.height + ecart.toPx()
                                drawLine(
                                    Color.White, Offset(0f, y), Offset(size.width, y),
                                    strokeWidth = trait.toPx(),
                                )
                            }
                        },
                    )
                }
                if (i == 0) Spacer(Modifier.width(1.u))
            }
        }

        if (vedette == null) {
            val message = when {
                data == null && !ctx.nbaErreur -> "Chargement des matchs…"
                data == null -> "Matchs indisponibles\n(pas de connexion)"
                onglet == 1 -> "Pas de résultat récent"
                else -> "Aucun match NBA\ncette semaine"
            }
            Text(
                message, color = Color.White.copy(alpha = 0.85f), fontSize = 18.us, fontWeight = FontWeight.Bold,
                fontFamily = UnePolice, lineHeight = 1.2.em,
                modifier = Modifier.uneLigne(0.u, 122.u),
            )
        } else {
            UneScore(vedette)
        }

        if (vedette != null && autres.isNotEmpty()) {
            Row(
                Modifier.uneLigne(0.u, 180.u),
                horizontalArrangement = Arrangement.spacedBy(23.u),
            ) {
                for (g in autres) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            g.away.abbr + " – " + g.home.abbr, color = Color.White, fontSize = 15.us,
                            fontWeight = FontWeight.Bold, fontFamily = UnePolice, maxLines = 1,
                        )
                        Text(
                            uneComplement(g), color = Color.White.copy(alpha = 0.8f), fontSize = 15.us,
                            fontWeight = FontWeight.Medium, fontFamily = UnePolice, maxLines = 1,
                            overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(start = 6.u),
                        )
                    }
                }
            }
        }
    }
}

/** Le match à la une : codes des équipes, scores géants (ou codes si le match n'a pas commencé), état. */
@Composable
private fun BoxScope.UneScore(g: NbaGame) {
    val avant = g.state == "pre"
    val a = g.away.score?.toIntOrNull()
    val h = g.home.score?.toIntOrNull()
    val meneur = when {
        avant || a == null || h == null || a == h -> 0
        a > h -> 1
        else -> 2
    }
    // Scores alignés sur leur ligne de base (156.u), codes des équipes au-dessus
    Row(Modifier.uneLigne(0.u, 156.u, derniere = true)) {
        UneEquipe(g.away, avant, meneur == 1, Modifier.alignBy(LastBaseline))
        Text(
            "–", color = Color.White.copy(alpha = 0.6f), fontSize = 45.us, fontWeight = FontWeight.Light,
            fontFamily = UnePolice, maxLines = 1,
            modifier = Modifier
                .alignBy(LastBaseline)
                .padding(start = 10.u, end = 12.u),
        )
        UneEquipe(g.home, avant, meneur == 2, Modifier.alignBy(LastBaseline))
    }
    Column(
        Modifier
            .align(Alignment.TopEnd)
            .padding(top = 108.u),
        horizontalAlignment = Alignment.End,
    ) {
        val (etiquette, direct) = when (g.state) {
            "in" -> "DIRECT" to true
            "post" -> "FINAL" to false
            else -> "À VENIR" to false
        }
        Row(
            Modifier
                .clip(RoundedCornerShape(4.u))
                .background(Color.White)
                .padding(start = 7.u, end = 8.u, top = 5.u, bottom = 5.u),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (direct) {
                Box(Modifier.size(6.u).clip(CircleShape).background(UneRouge))
                Spacer(Modifier.width(5.u))
            }
            Text(
                etiquette, color = UneEncreSombre, fontSize = 11.us, fontWeight = FontWeight.ExtraBold,
                fontFamily = UnePolice, letterSpacing = 0.14.em, maxLines = 1,
            )
        }
        val temps = when (g.state) {
            "in" -> uneTempsDeJeu(g.detail)
            "post" -> if (g.detail.contains("OT")) "Prol." else ""
            else -> uneHeure(g.start)
        }
        if (temps.isNotEmpty()) {
            Spacer(Modifier.height(4.u))
            Text(
                temps, color = Color.White, fontSize = 23.us, fontWeight = FontWeight.ExtraBold,
                fontFamily = UnePolice, letterSpacing = (-0.01).em, maxLines = 1,
            )
        }
    }
}

/**
 * Une équipe : code (et ▲ si elle mène) 48.u au-dessus de la ligne de base de son score géant.
 * Placement par lignes de base (indépendant des marges internes de la police) ; la ligne de
 * base du score est exposée comme LastBaseline pour aligner les deux équipes et le tiret.
 */
@Composable
private fun UneEquipe(t: NbaTeam, avant: Boolean, mene: Boolean, modifier: Modifier) {
    val ecart = 48.u
    Layout(
        modifier = modifier,
        content = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (avant) " " else t.abbr, color = Color.White, fontSize = 14.us, fontWeight = FontWeight.ExtraBold,
                    fontFamily = UnePolice, letterSpacing = 0.06.em, maxLines = 1,
                )
                if (mene) {
                    Spacer(Modifier.width(5.u))
                    UneTriangle(Color.White.copy(alpha = 0.85f), versDroite = false, versHaut = true, modifier = Modifier.size(8.u, 7.u))
                }
            }
            Text(
                if (avant) t.abbr else t.score.orEmpty(),
                color = Color.White, fontSize = 58.us, style = UneStyleScore, maxLines = 1,
            )
        },
    ) { mesurables, c ->
        val libre = c.copy(minWidth = 0, minHeight = 0)
        val code = mesurables[0].measure(libre)
        val score = mesurables[1].measure(libre)
        fun base(p: Placeable) = p[FirstBaseline].let { if (it == AlignmentLine.Unspecified) p.height else it }
        val bCode = base(code)
        val bScore = base(score)
        val e = ecart.roundToPx()
        val ligne = maxOf(bCode + e, bScore)
        val yScore = ligne - bScore
        val yCode = ligne - e - bCode
        layout(
            maxOf(code.width, score.width),
            maxOf(yScore + score.height, yCode + code.height),
            mapOf(LastBaseline to ligne),
        ) {
            code.place(0, yCode)
            score.place(0, yScore)
        }
    }
}

/** Complément d'un match de la ligne du bas : heure (à venir) ou score. */
private fun uneComplement(g: NbaGame): String = when (g.state) {
    "pre" -> uneHeure(g.start)
    else -> "${g.away.score.orEmpty()}-${g.home.score.orEmpty()}"
}

private fun uneHeure(t: Long): String = SimpleDateFormat("HH:mm", Locale.FRENCH).format(Date(t))

/** « Q3 4:12 » → « QT3 4:12 », « Halftime » → « Mi-temps ». */
private fun uneTempsDeJeu(s: String): String = when {
    s.contains("half", ignoreCase = true) -> "Mi-temps"
    s.startsWith("End of", ignoreCase = true) -> "Fin " + s.substringAfter("of ").replace("1st", "QT1")
        .replace("2nd", "QT2").replace("3rd", "QT3").replace("4th", "QT4")
    else -> s.replace(Regex("^(\\d)(st|nd|rd|th) Quarter"), "QT$1").replace(Regex("^Q(\\d)"), "QT$1").replace("OT", "Prol.")
}
