package fr.projo.launcher.modules.horloges

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
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Text
import fr.projo.launcher.modules.CarteModule
import fr.projo.launcher.modules.DonneesModules
import fr.projo.launcher.modules.Module
import fr.projo.launcher.modules.ModuleContexte
import fr.projo.launcher.modules.ReglagesModule
import fr.projo.launcher.modules.TailleModule
import fr.projo.launcher.modules.filet
import fr.projo.launcher.modules.ligne
import fr.projo.launcher.modules.soleil.Astronomie
import fr.projo.launcher.modules.u
import fr.projo.launcher.ui.LigneListe
import fr.projo.launcher.ui.ListeDialog
import fr.projo.launcher.ui.NbaStyle
import fr.projo.launcher.ui.u
import fr.projo.launcher.ui.us
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/** Une ville proposée : nom affiché, fuseau horaire (identifiant tz) et coordonnées (jour/nuit). */
data class VilleHoraire(val id: String, val nom: String, val fuseau: String, val lat: Double, val lon: Double) {
    val zone: TimeZone get() = TimeZone.getTimeZone(fuseau)
}

/** Grandes villes du monde (identifiants tz connus d'Android depuis longtemps). */
val VILLES_HORAIRES: List<VilleHoraire> = listOf(
    VilleHoraire("honolulu", "Honolulu", "Pacific/Honolulu", 21.31, -157.86),
    VilleHoraire("los-angeles", "Los Angeles", "America/Los_Angeles", 34.05, -118.24),
    VilleHoraire("mexico", "Mexico", "America/Mexico_City", 19.43, -99.13),
    VilleHoraire("new-york", "New York", "America/New_York", 40.71, -74.01),
    VilleHoraire("montreal", "Montréal", "America/Toronto", 45.50, -73.57),
    VilleHoraire("guadeloupe", "Guadeloupe", "America/Guadeloupe", 16.24, -61.53),
    VilleHoraire("buenos-aires", "Buenos Aires", "America/Argentina/Buenos_Aires", -34.60, -58.38),
    VilleHoraire("rio", "Rio", "America/Sao_Paulo", -22.91, -43.17),
    VilleHoraire("reykjavik", "Reykjavik", "Atlantic/Reykjavik", 64.15, -21.94),
    VilleHoraire("londres", "Londres", "Europe/London", 51.51, -0.13),
    VilleHoraire("paris", "Paris", "Europe/Paris", 48.86, 2.35),
    VilleHoraire("le-caire", "Le Caire", "Africa/Cairo", 30.04, 31.24),
    VilleHoraire("johannesburg", "Johannesburg", "Africa/Johannesburg", -26.20, 28.05),
    VilleHoraire("moscou", "Moscou", "Europe/Moscow", 55.76, 37.62),
    VilleHoraire("dubai", "Dubaï", "Asia/Dubai", 25.20, 55.27),
    VilleHoraire("la-reunion", "La Réunion", "Indian/Reunion", -21.12, 55.53),
    VilleHoraire("bombay", "Bombay", "Asia/Kolkata", 19.08, 72.88),
    VilleHoraire("bangkok", "Bangkok", "Asia/Bangkok", 13.76, 100.50),
    VilleHoraire("singapour", "Singapour", "Asia/Singapore", 1.35, 103.82),
    VilleHoraire("pekin", "Pékin", "Asia/Shanghai", 39.90, 116.41),
    VilleHoraire("tokyo", "Tokyo", "Asia/Tokyo", 35.68, 139.69),
    VilleHoraire("sydney", "Sydney", "Australia/Sydney", -33.87, 151.21),
    VilleHoraire("noumea", "Nouméa", "Pacific/Noumea", -22.28, 166.46),
    VilleHoraire("auckland", "Auckland", "Pacific/Auckland", -36.85, 174.76),
)

/**
 * Module « Horloges du monde » : 2 à 4 villes choisies dans ses réglages, avec l'heure,
 * l'écart avec l'heure de l'appareil et un indicateur jour/nuit (soleil levé ou non, calculé
 * localement). java.util.TimeZone (compatible Android 5.1) ; mise à jour à la minute.
 */
object ModuleHorloges : Module(
    id = "horloges",
    nom = "Horloges du monde",
    description = "L'heure à New York, Tokyo… avec le jour et la nuit",
    icone = IconeHorloges,
    tailles = setOf(TailleModule.PETIT, TailleModule.MOYEN),
) {
    private const val CLE_VILLES = "villes"
    const val MIN_VILLES = 2
    const val MAX_VILLES = 4
    val PAR_DEFAUT = listOf("new-york", "londres", "tokyo")

    /** Villes choisies (2 à 4, dans l'ordre du choix) ; défaut si le réglage est invalide. */
    fun villes(r: ReglagesModule): List<VilleHoraire> {
        val ids = r.liste(CLE_VILLES, PAR_DEFAUT).distinct()
        val v = ids.mapNotNull { id -> VILLES_HORAIRES.firstOrNull { it.id == id } }.take(MAX_VILLES)
        return if (v.size >= MIN_VILLES) v else PAR_DEFAUT.mapNotNull { id -> VILLES_HORAIRES.firstOrNull { it.id == id } }
    }

    @Composable
    override fun Contenu(ctx: ModuleContexte, modifier: Modifier) {
        val maintenant = rememberMinute()
        val villes = villes(ctx.reglages)
        val heures = remember(villes, maintenant) { villes.map { HeureVille.de(it, maintenant) } }
        CarteModule(ctx, modifier.height(ctx.hauteur.u)) {
            when (ctx.taille) {
                TailleModule.PETIT -> HorlogesPetit(heures, ctx.style, ctx.hauteur)
                else -> HorlogesMoyen(heures, ctx.style)
            }
        }
    }

    override val aDesReglages: Boolean get() = true

    override fun resumeReglages(reglages: ReglagesModule): String = villes(reglages).joinToString(", ") { it.nom }

    @Composable
    override fun Reglages(reglages: ReglagesModule, donnees: DonneesModules, fermer: () -> Unit) {
        val choisies = villes(reglages).map { it.id }
        val plein = choisies.size >= MAX_VILLES
        val lignes = buildList {
            for (v in VILLES_HORAIRES) {
                val coche = v.id in choisies
                add(
                    LigneListe(v.nom, ecartTexte(v.zone, System.currentTimeMillis()), coche = coche) {
                        val nouvelles = if (coche) choisies - v.id else choisies + v.id
                        // Entre 2 et 4 villes : au-delà, il faut d'abord en décocher une
                        if (nouvelles.size in MIN_VILLES..MAX_VILLES) reglages.mettreListe(CLE_VILLES, nouvelles)
                    },
                )
            }
            add(LigneListe("Terminé", "Revenir aux modules") { fermer() })
        }
        ListeDialog(
            titre = "Horloges du monde",
            sousTitre = when {
                plein -> "4 villes au plus : décoches-en une avant d'en ajouter"
                choisies.size <= MIN_VILLES -> "Coche 2 à 4 villes (2 au moins)"
                else -> "Coche 2 à 4 villes"
            },
            lignes = lignes,
            onDismiss = fermer,
        )
    }
}

// ------------------------------------------------------------------ calculs

/** Heure d'une ville à un instant : texte, écart avec l'appareil, jour/nuit. */
internal class HeureVille(val ville: VilleHoraire, val heure: String, val ecart: String, val jour: Boolean, val decalageJour: Int) {
    companion object {
        fun de(v: VilleHoraire, t: Long): HeureVille {
            val zone = v.zone
            val heure = SimpleDateFormat("HH:mm", Locale.FRENCH).apply { timeZone = zone }.format(Date(t))
            val jour = try { Astronomie.faitJour(t, v.lat, v.lon) } catch (e: Exception) { heureLocale(zone, t) in 7..19 }
            return HeureVille(v, heure, ecartTexte(zone, t), jour, decalageJour(zone, t))
        }

        private fun heureLocale(zone: TimeZone, t: Long): Int =
            Calendar.getInstance(zone).apply { timeInMillis = t }.get(Calendar.HOUR_OF_DAY)
    }
}

/** « +6 h », « −5 h 30 », « Même heure » : écart entre [zone] et l'heure de l'appareil. */
internal fun ecartTexte(zone: TimeZone, t: Long): String {
    val minutes = (zone.getOffset(t) - TimeZone.getDefault().getOffset(t)) / 60_000
    if (minutes == 0) return "Même heure"
    val signe = if (minutes > 0) "+" else "−"
    val h = abs(minutes) / 60
    val m = abs(minutes) % 60
    return signe + (if (m == 0) "$h h" else "$h h " + m.toString().padStart(2, '0'))
}

/** −1 : la ville est encore la veille ; +1 : déjà le lendemain ; 0 : même jour. */
private fun decalageJour(zone: TimeZone, t: Long): Int {
    fun cle(z: TimeZone): Int = Calendar.getInstance(z).apply { timeInMillis = t }.let { it.get(Calendar.YEAR) * 1000 + it.get(Calendar.DAY_OF_YEAR) }
    val ici = cle(TimeZone.getDefault())
    val la = cle(zone)
    return when {
        la == ici -> 0
        // Changement d'année : 2026001 contre 2025365
        la > ici -> 1
        else -> -1
    }
}

private fun texteJour(d: Int): String? = when (d) {
    1 -> "demain"
    -1 -> "hier"
    else -> null
}

// ------------------------------------------------------------------ rendus

private fun NbaStyle.clair(): Boolean = texte.luminance() < 0.5f

/** Petit soleil (jour) ou croissant de lune (nuit). */
@Composable
private fun IndicateurJour(jour: Boolean, style: NbaStyle, modifier: Modifier) {
    val soleil = if (style.clair()) Color(0xFFEE9A12) else Color(0xFFFFC94D)
    val lune = if (style.clair()) Color(0xFF6E6290) else Color(0xFFC9C3E6)
    val trait = 1.5f.u
    Canvas(modifier) {
        val r = min(size.width, size.height) / 2f
        val c = Offset(size.width / 2f, size.height / 2f)
        if (jour) {
            drawCircle(soleil, r * 0.42f, c)
            val e = trait.toPx()
            for (i in 0 until 8) {
                val a = PI / 4 * i
                val dx = cos(a).toFloat()
                val dy = sin(a).toFloat()
                drawLine(soleil, Offset(c.x + dx * r * 0.62f, c.y + dy * r * 0.62f), Offset(c.x + dx * r * 0.92f, c.y + dy * r * 0.92f), strokeWidth = e, cap = StrokeCap.Round)
            }
        } else {
            // Croissant : disque moins un disque décalé
            val r1 = r * 0.8f
            val disque = Path().apply { addOval(Rect(c, r1)) }
            val cache = Path().apply { addOval(Rect(Offset(c.x + r1 * 0.55f, c.y - r1 * 0.3f), r1 * 0.85f)) }
            drawPath(Path().apply { op(disque, cache, PathOperation.Difference) }, lune)
        }
    }
}

@Composable
private fun TitreHorloges(style: NbaStyle) {
    Text(
        "HORLOGES DU MONDE", color = style.texteDiscret, fontSize = 9.us, letterSpacing = 2.us,
        fontWeight = FontWeight.Bold, style = ligne(12.us), maxLines = 1, overflow = TextOverflow.Ellipsis,
    )
}

/** MOYEN : une colonne par ville (nom, heure en grand, jour/nuit et écart). */
@Composable
private fun HorlogesMoyen(heures: List<HeureVille>, style: NbaStyle) {
    val n = heures.size
    val (taille, interligne) = when {
        n <= 2 -> 30.us to 34.us
        n == 3 -> 26.us to 30.us
        else -> 22.us to 26.us
    }
    Column(Modifier.fillMaxSize().padding(horizontal = 8.u, vertical = 6.u)) {
        TitreHorloges(style)
        Row(Modifier.fillMaxWidth().weight(1f), verticalAlignment = Alignment.CenterVertically) {
            heures.forEachIndexed { i, h ->
                if (i > 0) Spacer(Modifier.fillMaxHeight().padding(vertical = 10.u).width(1.u).background(style.filet))
                ColonneVille(h, style, taille, interligne, serre = n >= 4, modifier = Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun ColonneVille(h: HeureVille, style: NbaStyle, taille: TextUnit, interligne: TextUnit, serre: Boolean, modifier: Modifier) {
    // 4 colonnes : textes un peu plus petits (« Johannesburg », « Même heure » tiennent)
    val nom = if (serre) 10.us * 0.95f else 11.us
    val detail = if (serre) 9.us else 10.us
    Column(modifier.padding(horizontal = if (serre) 1.u else 3.u), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(h.ville.nom, color = style.texte, fontSize = nom, fontWeight = FontWeight.SemiBold, style = ligne(14.us), maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
        Text(h.heure, color = style.texte, fontSize = taille, fontWeight = FontWeight.Light, style = ligne(interligne), maxLines = 1, softWrap = false)
        Row(verticalAlignment = Alignment.CenterVertically) {
            IndicateurJour(h.jour, style, Modifier.size(11.u))
            Spacer(Modifier.width(3.u))
            Text(h.ecart, color = style.texteDiscret, fontSize = detail, style = ligne(13.us), maxLines = 1, softWrap = false)
        }
        Text(texteJour(h.decalageJour) ?: "", color = style.texteDiscret, fontSize = 9.us, style = ligne(12.us), maxLines = 1)
    }
}

/** PETIT : une ligne par ville (jour/nuit, nom et écart, heure). */
@Composable
private fun HorlogesPetit(heures: List<HeureVille>, style: NbaStyle, hauteur: Float) {
    val ligneH = ((hauteur - 12 - 18) / heures.size.coerceAtLeast(1)).coerceIn(24f, 34f)
    Column(Modifier.fillMaxSize().padding(horizontal = 8.u, vertical = 6.u), verticalArrangement = Arrangement.Center) {
        TitreHorloges(style)
        Spacer(Modifier.height(3.u))
        heures.forEach { h ->
            Row(Modifier.fillMaxWidth().height(ligneH.u), verticalAlignment = Alignment.CenterVertically) {
                IndicateurJour(h.jour, style, Modifier.size(11.u))
                Spacer(Modifier.width(5.u))
                Column(Modifier.weight(1f)) {
                    Text(h.ville.nom, color = style.texte, fontSize = 10.us, fontWeight = FontWeight.SemiBold, style = ligne(12.us), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    val jour = texteJour(h.decalageJour)
                    Text(h.ecart + (jour?.let { ", $it" } ?: ""), color = style.texteDiscret, fontSize = 8.us, style = ligne(11.us), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Spacer(Modifier.width(4.u))
                Text(h.heure, color = style.texte, fontSize = 15.us, style = ligne(18.us), maxLines = 1, softWrap = false)
            }
        }
    }
}

/** Icône du module : globe et aiguilles (24 × 24, blanche). */
val IconeHorloges: ImageVector = ImageVector.Builder(
    defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f,
).apply {
    addPath(
        pathData = PathParser().parsePathString(
            "M12,3A9,9 0 1,1 11.99,3Z M3.5,9H8 M3.5,15H7 M12,7.5V12L15,14"
        ).toNodes(),
        stroke = SolidColor(Color.White),
        strokeLineWidth = 1.8f,
        strokeLineCap = StrokeCap.Round,
    )
}.build()
