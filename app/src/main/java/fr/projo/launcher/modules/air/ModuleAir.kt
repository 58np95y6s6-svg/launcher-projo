package fr.projo.launcher.modules.air

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Text
import fr.projo.launcher.modules.CarteModule
import fr.projo.launcher.modules.EtatDonnees
import fr.projo.launcher.modules.Module
import fr.projo.launcher.modules.ModuleContexte
import fr.projo.launcher.modules.TailleModule
import fr.projo.launcher.modules.filet
import fr.projo.launcher.modules.ligne
import fr.projo.launcher.modules.meteo.rememberPositionMeteo
import fr.projo.launcher.modules.rememberDonnees
import fr.projo.launcher.modules.u
import fr.projo.launcher.ui.NbaStyle
import fr.projo.launcher.ui.u
import fr.projo.launcher.ui.us
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Module « Qualité de l'air et pollen » (Open-Meteo, sans clé) : indice européen (European AQI)
 * avec son libellé et sa couleur, polluants principaux, pollens du jour avec leur niveau.
 * Position : celle de la Météo. Rafraîchi toutes les heures. Les pollens ne sont prévus
 * qu'en Europe et en saison : « Hors saison » sinon.
 */
object ModuleAir : Module(
    id = "air",
    nom = "Qualité de l'air et pollen",
    description = "Indice européen, polluants et pollens du jour",
    icone = IconeAir,
    tailles = setOf(TailleModule.PETIT, TailleModule.MOYEN),
) {
    @Composable
    override fun Contenu(ctx: ModuleContexte, modifier: Modifier) {
        val position = rememberPositionMeteo(ctx)
        val lieu = position.valeur
        val etat: EtatDonnees<AirDonnees>? = if (lieu != null) {
            ctx.rememberDonnees(AirReseau.cle(lieu), delai = { _: AirDonnees -> 60 * 60_000L }, delaiErreur = 5 * 60_000L) {
                AirReseau.charger(lieu)
            }
        } else null
        val style = ctx.style
        CarteModule(ctx, modifier.height(ctx.hauteur.u)) {
            val a = etat?.valeur
            if (a == null) {
                val erreur = position.erreur || etat?.erreur == true
                Column(Modifier.align(Alignment.Center).padding(8.u), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        if (erreur) "Qualité de l'air indisponible" else "Qualité de l'air…",
                        color = style.texteDiscret, fontSize = 12.us, style = ligne(16.us), textAlign = TextAlign.Center,
                    )
                    if (position.erreur) {
                        Text(
                            "Choisis une ville : Réglages · Météo", color = style.texteDiscret, fontSize = 10.us,
                            style = ligne(13.us), textAlign = TextAlign.Center,
                        )
                    }
                }
            } else {
                when (ctx.taille) {
                    TailleModule.PETIT -> AirPetit(a, style)
                    else -> AirMoyen(a, style, ctx.hauteur)
                }
            }
        }
    }
}

// ------------------------------------------------------------------ couleurs

private val COULEURS_POLLEN = listOf(
    Color(0xFF4CC38A), Color(0xFFF2C230), Color(0xFFFF8A3D), Color(0xFFE5484D),
)

/** Texte lisible sur une pastille de couleur. */
private fun encreSur(fond: Color): Color = if (fond.luminance() > 0.35f) Color(0xFF14161C) else Color.White

// ------------------------------------------------------------------ rendus

@Composable
private fun Titre(a: AirDonnees, style: NbaStyle) {
    Text(
        "AIR · " + a.lieu.uppercase(Locale.FRENCH), color = style.texteDiscret, fontSize = 9.us,
        letterSpacing = 2.us, fontWeight = FontWeight.Bold, style = ligne(12.us),
        maxLines = 1, overflow = TextOverflow.Ellipsis,
    )
}

/** Pastille ronde de la couleur du niveau, avec l'indice. */
@Composable
private fun Pastille(a: AirDonnees, style: NbaStyle, taille: Dp, police: TextUnit) {
    val fond = a.niveau?.couleur ?: style.texteDiscret.copy(alpha = 0.25f)
    Box(Modifier.size(taille).background(fond, CircleShape), contentAlignment = Alignment.Center) {
        Text(
            a.indice?.toString() ?: "—", color = if (a.niveau != null) encreSur(fond) else style.texte,
            fontSize = police, fontWeight = FontWeight.Bold, style = ligne(police * 1.2f), maxLines = 1, softWrap = false,
        )
    }
}

/** Échelle des 6 niveaux, le niveau actuel marqué d'un trait. */
@Composable
private fun EchelleNiveaux(a: AirDonnees, style: NbaStyle, modifier: Modifier) {
    val marque = style.texte
    val ecart = 2.u
    val trait = 2.u
    Canvas(modifier) {
        val n = NiveauAir.entries.size
        val e = ecart.toPx()
        val l = (size.width - e * (n - 1)) / n
        val h = size.height * 0.55f
        val y = (size.height - h) / 2f
        NiveauAir.entries.forEachIndexed { i, niv ->
            drawRoundRect(
                niv.couleur.copy(alpha = if (niv == a.niveau) 1f else 0.45f),
                Offset(i * (l + e), y), Size(l, h), CornerRadius(h / 2f),
            )
        }
        val indice = a.indice ?: return@Canvas
        val niv = NiveauAir.de(indice)
        val i = niv.ordinal
        // Position dans le segment du niveau (20 points par niveau)
        val dans = ((indice - i * 20) / 20f).coerceIn(0.1f, 0.9f)
        val x = i * (l + e) + l * dans
        drawLine(marque, Offset(x, 0f), Offset(x, size.height), strokeWidth = trait.toPx(), cap = StrokeCap.Round)
    }
}

/** « O₃ 54 · NO₂ 12 » : les [n] polluants les plus pénalisants. */
private fun textePolluants(a: AirDonnees, n: Int): String? =
    a.polluants.filter { it.concentration != null }.take(n)
        .joinToString(" · ") { "${it.nom} ${it.concentration!!.roundToInt()}" }
        .ifEmpty { null }

/** Jauge de 4 segments pour un niveau de pollen (1 à 4). */
@Composable
private fun JaugePollen(niveau: Int, style: NbaStyle) {
    val vide = style.texteDiscret.copy(alpha = style.texteDiscret.alpha * 0.35f)
    val plein = COULEURS_POLLEN[(niveau - 1).coerceIn(0, 3)]
    Row(horizontalArrangement = Arrangement.spacedBy(2.u), verticalAlignment = Alignment.CenterVertically) {
        for (i in 1..4) {
            Box(Modifier.size(width = 5.u, height = 10.u).background(if (i <= niveau) plein else vide, RoundedCornerShape(2.u)))
        }
    }
}

/** Message des pollens quand aucun n'est présent : hors saison, ou hors d'Europe. */
private fun pollensAbsents(a: AirDonnees): Pair<String, String> = when {
    a.pollens == null && !a.europe -> "Non disponibles" to "Pollens suivis en Europe seulement"
    a.pollens == null -> "Hors saison" to "Pas de relevé de pollens"
    else -> "Hors saison" to "Aucun pollen aujourd'hui"
}

@Composable
private fun AirMoyen(a: AirDonnees, style: NbaStyle, hauteur: Float) {
    Row(Modifier.fillMaxSize().padding(horizontal = 8.u, vertical = 6.u), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.Center) {
            Titre(a, style)
            Spacer(Modifier.height(6.u))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Pastille(a, style, 40.u, 16.us)
                Spacer(Modifier.width(8.u))
                Column {
                    Text(
                        a.niveau?.libelle ?: "Indisponible", color = style.texte, fontSize = 15.us,
                        fontWeight = FontWeight.SemiBold, style = ligne(18.us), maxLines = 2, overflow = TextOverflow.Ellipsis,
                    )
                    Text("Indice européen", color = style.texteDiscret, fontSize = 9.us, style = ligne(12.us), maxLines = 1)
                }
            }
            Spacer(Modifier.height(6.u))
            EchelleNiveaux(a, style, Modifier.fillMaxWidth().height(8.u))
            Spacer(Modifier.height(6.u))
            textePolluants(a, 2)?.let {
                Text("Polluants principaux (µg/m³)", color = style.texteDiscret, fontSize = 9.us, style = ligne(12.us), maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(it, color = style.texte, fontSize = 11.us, style = ligne(14.us), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        Spacer(Modifier.width(8.u))
        Spacer(Modifier.fillMaxHeight().padding(vertical = 6.u).width(1.u).background(style.filet))
        Spacer(Modifier.width(10.u))
        Column(Modifier.width(124.u), verticalArrangement = Arrangement.Center) {
            Text("POLLENS", color = style.texteDiscret, fontSize = 9.us, letterSpacing = 2.us, fontWeight = FontWeight.Bold, style = ligne(12.us), maxLines = 1)
            Spacer(Modifier.height(4.u))
            val pollens = a.pollens.orEmpty()
            if (pollens.isEmpty()) {
                val (titre, detail) = pollensAbsents(a)
                Text(titre, color = style.texte, fontSize = 13.us, fontWeight = FontWeight.SemiBold, style = ligne(17.us), maxLines = 1)
                Text(detail, color = style.texteDiscret, fontSize = 9.us, style = ligne(12.us), maxLines = 2, overflow = TextOverflow.Ellipsis)
            } else {
                // Lignes de 27 : autant que la hauteur le permet (3 dans l'emplacement NBA)
                val lignes = ((hauteur - 12 - 18) / 27f).toInt().coerceIn(1, 4)
                pollens.take(lignes).forEach { p -> LignePollen(p, style) }
            }
        }
    }
}

@Composable
private fun LignePollen(p: Pollen, style: NbaStyle) {
    Row(Modifier.fillMaxWidth().height(27.u), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(p.type.nom, color = style.texte, fontSize = 11.us, fontWeight = FontWeight.Medium, style = ligne(14.us), maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(NIVEAUX_POLLEN[p.niveau.coerceIn(0, 4)], color = style.texteDiscret, fontSize = 9.us, style = ligne(12.us), maxLines = 1)
        }
        Spacer(Modifier.width(4.u))
        JaugePollen(p.niveau, style)
    }
}

@Composable
private fun AirPetit(a: AirDonnees, style: NbaStyle) {
    Column(Modifier.fillMaxSize().padding(horizontal = 8.u, vertical = 6.u), verticalArrangement = Arrangement.Center) {
        Titre(a, style)
        Spacer(Modifier.height(5.u))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Pastille(a, style, 34.u, 14.us)
            Spacer(Modifier.width(7.u))
            Column {
                Text(
                    a.niveau?.libelle ?: "Indisponible", color = style.texte, fontSize = 12.us,
                    fontWeight = FontWeight.SemiBold, style = ligne(14.us), maxLines = 2, overflow = TextOverflow.Ellipsis,
                )
                Text("Indice européen", color = style.texteDiscret, fontSize = 8.us, style = ligne(11.us), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        Spacer(Modifier.height(5.u))
        textePolluants(a, 1)?.let {
            Text("Principal : $it µg/m³", color = style.texte, fontSize = 9.us, style = ligne(12.us), maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(4.u))
        }
        val pollens = a.pollens.orEmpty()
        if (pollens.isEmpty()) {
            Text(
                if (a.pollens == null && !a.europe) "Pollens : hors d'Europe" else "Pollens : hors saison",
                color = style.texteDiscret, fontSize = 10.us, style = ligne(13.us), maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        } else {
            pollens.take(2).forEach { p ->
                Row(Modifier.fillMaxWidth().height(15.u), verticalAlignment = Alignment.CenterVertically) {
                    Text(p.type.nom, color = style.texte, fontSize = 10.us, style = ligne(13.us), maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                    JaugePollen(p.niveau, style)
                }
            }
        }
    }
}

/** Icône du module : souffle d'air (24 × 24, blanche). */
val IconeAir: ImageVector = ImageVector.Builder(
    defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f,
).apply {
    addPath(
        pathData = PathParser().parsePathString(
            "M3,8.5H13.5A2.5,2.5 0 1,0 11,6 M3,12.5H18A2.5,2.5 0 1,1 15.5,15 M3,16.5H10"
        ).toNodes(),
        stroke = SolidColor(Color.White),
        strokeLineWidth = 1.8f,
        strokeLineCap = StrokeCap.Round,
    )
}.build()
