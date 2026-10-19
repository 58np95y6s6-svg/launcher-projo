package fr.projo.launcher.modules.soleil

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Text
import fr.projo.launcher.modules.CarteModule
import fr.projo.launcher.modules.Module
import fr.projo.launcher.modules.ModuleContexte
import fr.projo.launcher.modules.TailleModule
import fr.projo.launcher.modules.filet
import fr.projo.launcher.modules.horloges.rememberMinute
import fr.projo.launcher.modules.ligne
import fr.projo.launcher.modules.meteo.Lieu
import fr.projo.launcher.modules.meteo.rememberPositionMeteo
import fr.projo.launcher.modules.u
import fr.projo.launcher.ui.NbaStyle
import fr.projo.launcher.ui.u
import fr.projo.launcher.ui.us
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Module « Soleil et lune » : lever, coucher et durée du jour à la position de la Météo, arc
 * du soleil avec sa position actuelle ; phase de la lune (dessin vectoriel), pourcentage
 * éclairé et prochaine phase principale. Calcul local (Astronomie.kt), sans réseau, mis à
 * jour à la minute. La position se règle dans Réglages · Météo.
 */
object ModuleSoleil : Module(
    id = "soleil",
    nom = "Soleil et lune",
    description = "Lever, coucher, durée du jour et phase de la lune",
    icone = IconeSoleilLune,
    tailles = setOf(TailleModule.PETIT, TailleModule.MOYEN),
) {
    @Composable
    override fun Contenu(ctx: ModuleContexte, modifier: Modifier) {
        val position = rememberPositionMeteo(ctx)
        val maintenant = rememberMinute()
        val style = ctx.style
        CarteModule(ctx, modifier.height(ctx.hauteur.u)) {
            val lieu = position.valeur
            val ciel = remember(lieu, maintenant) { lieu?.let { CielDuJour.calculer(maintenant, it) } }
            if (ciel == null) {
                Column(Modifier.align(Alignment.Center).padding(8.u), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        if (position.erreur || lieu != null) "Position inconnue" else "Soleil et lune…",
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
                    TailleModule.PETIT -> SoleilPetit(ciel, style)
                    else -> SoleilMoyen(ciel, style)
                }
            }
        }
    }
}

/** Tout ce qu'affiche le module, calculé une fois par minute. */
internal class CielDuJour(
    val lieu: Lieu,
    val maintenant: Long,
    val journee: JourneeSoleil,
    val lune: EtatLune,
) {
    companion object {
        /** null si le calcul échoue (coordonnées invalides…) : le module affiche alors un message. */
        fun calculer(t: Long, lieu: Lieu): CielDuJour? = try {
            if (lieu.lat.isNaN() || lieu.lon.isNaN()) null
            else CielDuJour(lieu, t, Astronomie.journee(t, lieu.lat, lieu.lon), Astronomie.lune(t))
        } catch (e: Exception) {
            null
        }
    }
}

// ------------------------------------------------------------------ textes

private fun heure(t: Long): String = SimpleDateFormat("HH:mm", Locale.FRENCH).format(Date(t))

/** « 10 h 44 », « 45 min ». */
internal fun duree(ms: Long): String {
    val minutes = ((ms + 30_000L) / 60_000L).coerceAtLeast(0L)
    val h = minutes / 60
    val m = minutes % 60
    return when {
        h == 0L -> "$m min"
        m == 0L -> "$h h"
        else -> "$h h " + m.toString().padStart(2, '0')
    }
}

/** « Coucher dans 3 h 20 », « Lever dans 9 h 12 » ; null en nuit ou jour polaire. */
private fun prochainEvenement(c: CielDuJour): String? {
    val j = c.journee
    val lever = j.lever ?: return null
    val coucher = j.coucher ?: return null
    val t = c.maintenant
    return when {
        t < lever -> "Lever dans " + duree(lever - t)
        t < coucher -> "Coucher dans " + duree(coucher - t)
        // Après le coucher : le lever de demain arrive environ 24 h après celui d'aujourd'hui
        else -> "Lever demain à " + heure(Astronomie.journee(t + 86_400_000L, c.lieu.lat, c.lieu.lon).lever ?: (lever + 86_400_000L))
    }
}

/** Date de la prochaine phase : « aujourd'hui à 16:14 », « demain », « lun. 26 », « 3 nov. ». */
private fun dateProchainePhase(c: CielDuJour): String {
    val d = c.lune.prochaineDate
    return when (val ecart = Astronomie.ecartJours(d, c.maintenant, TimeZone.getDefault())) {
        0 -> "aujourd'hui à " + heure(d)
        1 -> "demain à " + heure(d)
        in 2..6 -> SimpleDateFormat("EEEE d", Locale.FRENCH).format(Date(d))
        else -> if (ecart < 0) heure(d) else SimpleDateFormat("d MMM", Locale.FRENCH).format(Date(d))
    }
}

private fun pourcent(f: Double): String = "${(f * 100).roundToInt().coerceIn(0, 100)} %"

// ------------------------------------------------------------------ couleurs

/** Design clair (Pastel) : texte foncé. */
private fun NbaStyle.clair(): Boolean = texte.luminance() < 0.5f

private fun couleurSoleil(style: NbaStyle) = if (style.clair()) Color(0xFFEE9A12) else Color(0xFFFFC94D)
private fun luneEclairee(style: NbaStyle) = if (style.clair()) Color(0xFFF3D27C) else Color(0xFFF1E9C6)
private fun luneOmbre(style: NbaStyle) = style.texte.copy(alpha = if (style.clair()) 0.12f else 0.16f)

// ------------------------------------------------------------------ rendus

@Composable
private fun Titre(c: CielDuJour, style: NbaStyle, prefixe: String) {
    Text(
        prefixe + c.lieu.nom.uppercase(Locale.FRENCH), color = style.texteDiscret, fontSize = 9.us,
        letterSpacing = 2.us, fontWeight = FontWeight.Bold, style = ligne(12.us),
        maxLines = 1, overflow = TextOverflow.Ellipsis,
    )
}

/** Heure de lever ou de coucher avec son intitulé. */
@Composable
private fun BlocHeure(intitule: String, t: Long?, style: NbaStyle, alignement: Alignment.Horizontal) {
    Column(horizontalAlignment = alignement) {
        Text(intitule, color = style.texteDiscret, fontSize = 8.us, letterSpacing = 1.us, fontWeight = FontWeight.Bold, style = ligne(11.us), maxLines = 1, softWrap = false)
        Text(t?.let { heure(it) } ?: "—", color = style.texte, fontSize = 13.us, fontWeight = FontWeight.SemiBold, style = ligne(16.us), maxLines = 1, softWrap = false)
    }
}

@Composable
private fun SoleilMoyen(c: CielDuJour, style: NbaStyle) {
    val j = c.journee
    Row(Modifier.fillMaxSize().padding(horizontal = 8.u, vertical = 6.u), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f).fillMaxHeight()) {
            Titre(c, style, "SOLEIL · ")
            Spacer(Modifier.height(2.u))
            ArcSoleil(c, style, Modifier.fillMaxWidth().weight(1f))
            Spacer(Modifier.height(2.u))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
                BlocHeure("LEVER", j.lever, style, Alignment.Start)
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    val polaire = when (j.polaire) {
                        Polaire.JOUR -> "Jour polaire"
                        Polaire.NUIT -> "Nuit polaire"
                        null -> null
                    }
                    Text(if (polaire == null) "JOUR" else "", color = style.texteDiscret, fontSize = 8.us, letterSpacing = 1.us, fontWeight = FontWeight.Bold, style = ligne(11.us), maxLines = 1)
                    Text(polaire ?: duree(j.dureeJour), color = style.texte, fontSize = 12.us, style = ligne(16.us), maxLines = 1, softWrap = false)
                }
                BlocHeure("COUCHER", j.coucher, style, Alignment.End)
            }
            prochainEvenement(c)?.let {
                Text(
                    it, color = style.texteDiscret, fontSize = 10.us, style = ligne(13.us), maxLines = 1,
                    overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth(),
                )
            }
        }
        Spacer(Modifier.width(8.u))
        Spacer(Modifier.fillMaxHeight().padding(vertical = 6.u).width(1.u).background(style.filet))
        Spacer(Modifier.width(10.u))
        Column(Modifier.width(110.u), verticalArrangement = Arrangement.Center) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                DessinLune(c.lune, c.lieu.lat < 0, style, Modifier.size(40.u))
                Spacer(Modifier.width(8.u))
                Column {
                    Text(pourcent(c.lune.fraction), color = style.texte, fontSize = 19.us, fontWeight = FontWeight.Light, style = ligne(23.us), maxLines = 1, softWrap = false)
                    Text("éclairée", color = style.texteDiscret, fontSize = 9.us, style = ligne(12.us), maxLines = 1)
                }
            }
            Spacer(Modifier.height(6.u))
            Text(c.lune.phase.libelle, color = style.texte, fontSize = 11.us, fontWeight = FontWeight.SemiBold, style = ligne(14.us), maxLines = 2, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(4.u))
            Text(c.lune.prochaine.libelle, color = style.texteDiscret, fontSize = 10.us, style = ligne(13.us), maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(dateProchainePhase(c), color = style.texte, fontSize = 10.us, style = ligne(13.us), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun SoleilPetit(c: CielDuJour, style: NbaStyle) {
    val j = c.journee
    Column(Modifier.fillMaxSize().padding(horizontal = 8.u, vertical = 6.u)) {
        Titre(c, style, "SOLEIL · ")
        Spacer(Modifier.height(2.u))
        ArcSoleil(c, style, Modifier.fillMaxWidth().weight(1f))
        Spacer(Modifier.height(2.u))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            val polaire = when (j.polaire) {
                Polaire.JOUR -> "Jour polaire"
                Polaire.NUIT -> "Nuit polaire"
                null -> null
            }
            if (polaire != null) {
                Text(polaire, color = style.texte, fontSize = 11.us, style = ligne(14.us), maxLines = 1, modifier = Modifier.weight(1f), textAlign = TextAlign.Center)
            } else {
                Text(j.lever?.let { heure(it) } ?: "—", color = style.texte, fontSize = 11.us, fontWeight = FontWeight.SemiBold, style = ligne(14.us), maxLines = 1, softWrap = false)
                Text(duree(j.dureeJour), color = style.texteDiscret, fontSize = 9.us, style = ligne(14.us), maxLines = 1, softWrap = false, textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
                Text(j.coucher?.let { heure(it) } ?: "—", color = style.texte, fontSize = 11.us, fontWeight = FontWeight.SemiBold, style = ligne(14.us), maxLines = 1, softWrap = false)
            }
        }
        Spacer(Modifier.height(5.u))
        Row(Modifier.height(28.u), verticalAlignment = Alignment.CenterVertically) {
            DessinLune(c.lune, c.lieu.lat < 0, style, Modifier.size(26.u))
            Spacer(Modifier.width(7.u))
            Column {
                Text(c.lune.phase.court, color = style.texte, fontSize = 10.us, fontWeight = FontWeight.SemiBold, style = ligne(13.us), maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("Lune " + pourcent(c.lune.fraction), color = style.texteDiscret, fontSize = 9.us, style = ligne(12.us), maxLines = 1)
            }
        }
    }
}

// ------------------------------------------------------------------ dessins

/**
 * Arc du soleil, du lever (à gauche) au coucher (à droite), au-dessus de l'horizon : trajet
 * parcouru en trait plein, reste en pointillé, soleil à sa position actuelle (absent la nuit).
 */
@Composable
private fun ArcSoleil(c: CielDuJour, style: NbaStyle, modifier: Modifier) {
    val soleil = couleurSoleil(style)
    val discret = style.texteDiscret
    val epais = 2.u
    val rayonSoleil = 5.u
    val tiret = 4.u
    Canvas(modifier) {
        val e = epais.toPx()
        val r = rayonSoleil.toPx()
        val halo = r * 1.8f
        val rx = (size.width / 2f - halo).coerceAtLeast(1f)
        val ry = min(size.height - 2 * halo, rx * 0.62f).coerceAtLeast(1f)
        // Arc et halo centrés verticalement dans la place donnée
        val yHorizon = (size.height - (ry + 2 * halo)) / 2f + halo + ry
        val cx = size.width / 2f
        fun point(f: Float) = Offset(cx - rx * cos(PI * f).toFloat(), yHorizon - ry * sin(PI * f).toFloat())
        fun chemin(de: Float, a: Float): Path = Path().apply {
            val n = 48
            for (i in 0..n) {
                val p = point(de + (a - de) * i / n)
                if (i == 0) moveTo(p.x, p.y) else lineTo(p.x, p.y)
            }
        }

        val j = c.journee
        // Position sur l'arc : fraction du jour écoulée (jour polaire : heure solaire)
        val f: Float? = when {
            j.polaire == Polaire.JOUR -> (((c.maintenant - j.midi) / 86_400_000.0) + 0.5).toFloat().coerceIn(0f, 1f)
            j.lever != null && j.coucher != null && c.maintenant in j.lever..j.coucher ->
                ((c.maintenant - j.lever).toDouble() / (j.coucher - j.lever).coerceAtLeast(1L)).toFloat()
            else -> null
        }

        drawLine(discret.copy(alpha = discret.alpha * 0.5f), Offset(0f, yHorizon), Offset(size.width, yHorizon), strokeWidth = e * 0.6f)
        drawPath(
            chemin(0f, 1f), discret.copy(alpha = discret.alpha * 0.7f),
            style = Stroke(width = e * 0.7f, cap = StrokeCap.Round, pathEffect = PathEffect.dashPathEffect(floatArrayOf(tiret.toPx(), tiret.toPx()))),
        )
        if (j.polaire == Polaire.NUIT) return@Canvas
        if (f != null) {
            if (f > 0.005f) drawPath(chemin(0f, f), soleil, style = Stroke(width = e, cap = StrokeCap.Round))
            val p = point(f)
            drawCircle(soleil.copy(alpha = 0.25f), halo, p)
            drawCircle(soleil, r, p)
        } else {
            // Nuit : extrémités de l'arc marquées
            drawCircle(soleil.copy(alpha = 0.6f), e * 1.2f, point(0f))
            drawCircle(soleil.copy(alpha = 0.6f), e * 1.2f, point(1f))
        }
    }
}

/**
 * Lune dessinée selon sa phase : disque sombre, partie éclairée (bord éclairé + terminateur
 * elliptique), quelques mers discrètes. Croissante : éclairée à droite dans l'hémisphère nord,
 * à gauche dans l'hémisphère sud ([sud]).
 */
@Composable
internal fun DessinLune(lune: EtatLune, sud: Boolean, style: NbaStyle, modifier: Modifier) {
    val eclairee = luneEclairee(style)
    val ombre = luneOmbre(style)
    val contour = style.texteDiscret.copy(alpha = style.texteDiscret.alpha * 0.55f)
    val trait = 1.u
    Canvas(modifier) {
        val t = trait.toPx()
        val r = min(size.width, size.height) / 2f - t
        val c = Offset(size.width / 2f, size.height / 2f)
        drawCircle(ombre, r, c)
        val s = (2 * lune.fraction - 1).toFloat()
        val cote = if (lune.croissante != sud) 1f else -1f
        val n = 40
        val partie = Path().apply {
            // Bord éclairé, du haut vers le bas
            for (i in 0..n) {
                val a = -PI / 2 + PI * i / n
                val x = c.x + cote * r * cos(a).toFloat()
                val y = c.y + r * sin(a).toFloat()
                if (i == 0) moveTo(x, y) else lineTo(x, y)
            }
            // Terminateur, du bas vers le haut
            for (i in 0..n) {
                val a = PI / 2 - PI * i / n
                lineTo(c.x - cote * s * r * cos(a).toFloat(), c.y + r * sin(a).toFloat())
            }
            close()
        }
        if (lune.fraction > 0.005) {
            drawPath(partie, eclairee)
            clipPath(partie) {
                val mer = Color(0xFF8A8270).copy(alpha = 0.22f)
                drawCircle(mer, r * 0.22f, Offset(c.x - r * 0.25f, c.y - r * 0.28f))
                drawCircle(mer, r * 0.16f, Offset(c.x + r * 0.30f, c.y - r * 0.05f))
                drawCircle(mer, r * 0.12f, Offset(c.x - r * 0.05f, c.y + r * 0.40f))
            }
        }
        drawCircle(contour, r, c, style = Stroke(t))
    }
}

/** Icône du module : soleil à l'horizon et croissant de lune (24 × 24, blanche). */
val IconeSoleilLune: ImageVector = ImageVector.Builder(
    defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f,
).apply {
    addPath(
        pathData = PathParser().parsePathString(
            "M2.5,19H21.5 M6.5,19A5.5,5.5 0 0,1 17.5,19 M12,9.5V11.5 M5.3,12.3L6.7,13.7 M18.7,12.3L17.3,13.7"
        ).toNodes(),
        stroke = SolidColor(Color.White),
        strokeLineWidth = 1.8f,
        strokeLineCap = StrokeCap.Round,
    )
    addPath(
        pathData = PathParser().parsePathString("M17,2.5A3.5,3.5 0 1,0 21.5,7A2.8,2.8 0 1,1 17,2.5Z").toNodes(),
        fill = SolidColor(Color.White),
    )
}.build()
