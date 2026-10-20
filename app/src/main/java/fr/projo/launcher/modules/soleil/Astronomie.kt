package fr.projo.launcher.modules.soleil

import java.util.Calendar
import java.util.TimeZone
import kotlin.math.acos
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.roundToLong
import kotlin.math.sin
import kotlin.math.tan

/**
 * Calculs astronomiques locaux (aucun réseau), précis à la minute près :
 *  - soleil : algorithme de la NOAA (déclinaison et équation du temps), lever et coucher pour
 *    une hauteur de −0,833° (réfraction et rayon du disque), hauteur à un instant donné ;
 *  - lune : fraction éclairée (Meeus, Astronomical Algorithms, formule 48.4) et dates vraies
 *    des phases principales (Meeus, chapitre 49, forme abrégée de « moontool »).
 * Toutes les dates sont en millisecondes UTC (comme System.currentTimeMillis()).
 */
object Astronomie {

    private const val JOUR_MS = 86_400_000L
    private const val J2000 = 2451545.0
    /** Durée moyenne d'une lunaison (mois synodique), en jours. */
    const val MOIS_SYNODIQUE = 29.530588853
    /** Hauteur du centre du soleil au lever et au coucher (réfraction et demi-diamètre). */
    private const val HAUTEUR_LEVER = -0.833

    fun jourJulien(ms: Long): Double = ms / JOUR_MS.toDouble() + 2440587.5

    fun msDeJourJulien(jj: Double): Long = ((jj - 2440587.5) * JOUR_MS).roundToLong()

    private fun rad(d: Double) = Math.toRadians(d)
    private fun deg(r: Double) = Math.toDegrees(r)

    // ------------------------------------------------------------------ soleil

    /** Déclinaison du soleil (°) et équation du temps (minutes) au jour julien [jj] (NOAA). */
    internal fun soleil(jj: Double): Pair<Double, Double> {
        val t = (jj - J2000) / 36525.0
        val l0 = ((280.46646 + t * (36000.76983 + t * 0.0003032)) % 360.0 + 360.0) % 360.0
        val m = 357.52911 + t * (35999.05029 - 0.0001537 * t)
        val e = 0.016708634 - t * (0.000042037 + 0.0000001267 * t)
        val c = sin(rad(m)) * (1.914602 - t * (0.004817 + 0.000014 * t)) +
            sin(rad(2 * m)) * (0.019993 - 0.000101 * t) + sin(rad(3 * m)) * 0.000289
        val omega = 125.04 - 1934.136 * t
        val lambda = l0 + c - 0.00569 - 0.00478 * sin(rad(omega))
        val eps0 = 23.0 + (26.0 + (21.448 - t * (46.815 + t * (0.00059 - t * 0.001813))) / 60.0) / 60.0
        val eps = eps0 + 0.00256 * cos(rad(omega))
        val declinaison = deg(asin(sin(rad(eps)) * sin(rad(lambda))))
        val y = tan(rad(eps / 2)).let { it * it }
        val equation = 4 * deg(
            y * sin(2 * rad(l0)) - 2 * e * sin(rad(m)) + 4 * e * y * sin(rad(m)) * cos(2 * rad(l0)) -
                0.5 * y * y * sin(4 * rad(l0)) - 1.25 * e * e * sin(2 * rad(m))
        )
        return declinaison to equation
    }

    private fun latitudeSure(lat: Double) = lat.coerceIn(-89.9, 89.9)

    /** Hauteur du soleil au-dessus de l'horizon (°, sans réfraction) à l'instant [t]. */
    fun hauteurSoleil(t: Long, lat: Double, lon: Double): Double {
        val (decl, eq) = soleil(jourJulien(t))
        val minutesUtc = ((t % JOUR_MS + JOUR_MS) % JOUR_MS) / 60_000.0
        val angleHoraire = (minutesUtc + eq + 4 * lon) / 4.0 - 180.0
        val phi = rad(latitudeSure(lat))
        val cosZenith = sin(phi) * sin(rad(decl)) + cos(phi) * cos(rad(decl)) * cos(rad(angleHoraire))
        return 90.0 - deg(acos(cosZenith.coerceIn(-1.0, 1.0)))
    }

    /** true si le soleil est levé à l'instant [t] (haut bord du disque au-dessus de l'horizon). */
    fun faitJour(t: Long, lat: Double, lon: Double): Boolean = hauteurSoleil(t, lat, lon) > HAUTEUR_LEVER

    /**
     * Lever, coucher et midi solaire du jour civil qui contient [t] dans [fuseau] (le fuseau
     * de l'appareil : les heures sont affichées à l'heure locale de l'appareil).
     */
    fun journee(t: Long, lat: Double, lon: Double, fuseau: TimeZone = TimeZone.getDefault()): JourneeSoleil {
        val local = Calendar.getInstance(fuseau).apply { timeInMillis = t }
        val minuitUtc = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
            clear()
            set(local.get(Calendar.YEAR), local.get(Calendar.MONTH), local.get(Calendar.DAY_OF_MONTH))
        }.timeInMillis
        val phi = latitudeSure(lat)
        // Midi solaire : 720 − 4 × longitude − équation du temps (minutes après 0 h UTC)
        fun midi(instant: Long): Long {
            val eq = soleil(jourJulien(instant)).second
            return minuitUtc + ((720.0 - 4 * lon - eq) * 60_000.0).roundToLong()
        }
        val midiSolaire = midi(midi(minuitUtc + JOUR_MS / 2))

        /** Lever (sens = −1) ou coucher (+1) : 3 itérations ; null si le soleil ne passe pas l'horizon. */
        fun evenement(sens: Int): Pair<Long?, Polaire?> {
            var instant = midiSolaire
            repeat(3) {
                val decl = soleil(jourJulien(instant)).first
                val cosH = (cos(rad(90.0 - HAUTEUR_LEVER)) - sin(rad(phi)) * sin(rad(decl))) /
                    (cos(rad(phi)) * cos(rad(decl)))
                if (cosH > 1.0) return null to Polaire.NUIT
                if (cosH < -1.0) return null to Polaire.JOUR
                val h = deg(acos(cosH))
                instant = midi(instant) + (sens * 4.0 * h * 60_000.0).roundToLong()
            }
            return instant to null
        }

        val (lever, p1) = evenement(-1)
        val (coucher, p2) = evenement(+1)
        val polaire = p1 ?: p2
        if (lever == null || coucher == null) {
            // Nuit ou jour polaire : la hauteur à midi tranche
            val p = polaire ?: if (hauteurSoleil(midiSolaire, lat, lon) > HAUTEUR_LEVER) Polaire.JOUR else Polaire.NUIT
            return JourneeSoleil(null, null, midiSolaire, if (p == Polaire.JOUR) JOUR_MS else 0L, p)
        }
        return JourneeSoleil(lever, coucher, midiSolaire, (coucher - lever).coerceIn(0L, JOUR_MS), null)
    }

    // ------------------------------------------------------------------ lune

    /** Fraction éclairée (0 à 1) et sens (true = croissante) à l'instant [t]. */
    fun eclairementLune(t: Long): Pair<Double, Boolean> {
        val tt = (jourJulien(t) - J2000) / 36525.0
        val d = 297.8501921 + 445267.1114034 * tt - 0.0018819 * tt * tt
        val m = 357.5291092 + 35999.0502909 * tt - 0.0001536 * tt * tt
        val mp = 134.9633964 + 477198.8675055 * tt + 0.0087414 * tt * tt
        val elongation = ((d % 360.0) + 360.0) % 360.0
        // Angle de phase (Meeus 48.4)
        val i = 180.0 - elongation - 6.289 * sin(rad(mp)) + 2.100 * sin(rad(m)) -
            1.274 * sin(rad(2 * d - mp)) - 0.658 * sin(rad(2 * d)) - 0.214 * sin(rad(2 * mp)) - 0.110 * sin(rad(d))
        val fraction = ((1.0 + cos(rad(i))) / 2.0).coerceIn(0.0, 1.0)
        return fraction to (elongation < 180.0)
    }

    /**
     * Date vraie (jour julien) de la phase principale [quart] (0 = nouvelle lune, 1 = premier
     * quartier, 2 = pleine lune, 3 = dernier quartier) de la lunaison [k] (comptée depuis 1900).
     */
    internal fun phaseVraie(k: Int, quart: Int): Double {
        val kk = k + quart / 4.0
        val t = kk / 1236.85
        val t2 = t * t
        val t3 = t2 * t
        var jj = 2415020.75933 + MOIS_SYNODIQUE * kk + 0.0001178 * t2 - 0.000000155 * t3 +
            0.00033 * sin(rad(166.56 + 132.87 * t - 0.009173 * t2))
        val m = rad(359.2242 + 29.10535608 * kk - 0.0000333 * t2 - 0.00000347 * t3)
        val mp = rad(306.0253 + 385.81691806 * kk + 0.0107306 * t2 + 0.00001236 * t3)
        val f = rad(21.2964 + 390.67050646 * kk - 0.0016528 * t2 - 0.00000239 * t3)
        if (quart == 0 || quart == 2) {
            jj += (0.1734 - 0.000393 * t) * sin(m) + 0.0021 * sin(2 * m) - 0.4068 * sin(mp) +
                0.0161 * sin(2 * mp) - 0.0004 * sin(3 * mp) + 0.0104 * sin(2 * f) - 0.0051 * sin(m + mp) -
                0.0074 * sin(m - mp) + 0.0004 * sin(2 * f + m) - 0.0004 * sin(2 * f - m) -
                0.0006 * sin(2 * f + mp) + 0.0010 * sin(2 * f - mp) + 0.0005 * sin(m + 2 * mp)
        } else {
            jj += (0.1721 - 0.0004 * t) * sin(m) + 0.0021 * sin(2 * m) - 0.6280 * sin(mp) +
                0.0089 * sin(2 * mp) - 0.0004 * sin(3 * mp) + 0.0079 * sin(2 * f) - 0.0119 * sin(m + mp) -
                0.0047 * sin(m - mp) + 0.0003 * sin(2 * f + m) - 0.0004 * sin(2 * f - m) -
                0.0006 * sin(2 * f + mp) + 0.0021 * sin(2 * f - mp) + 0.0003 * sin(m + 2 * mp) +
                0.0004 * sin(m - 2 * mp) - 0.0003 * sin(2 * m + mp)
            jj += if (quart == 1) 0.0028 - 0.0004 * cos(m) + 0.0003 * cos(mp)
            else -0.0028 + 0.0004 * cos(m) - 0.0003 * cos(mp)
        }
        return jj
    }

    /** Phases principales autour de l'instant [t] (environ ± 1,5 lunaison), dans l'ordre. */
    fun phasesAutour(t: Long): List<Pair<Long, PhaseLune>> {
        val k0 = floor((jourJulien(t) - 2415020.75933) / MOIS_SYNODIQUE).toInt() - 1
        val majeures = listOf(PhaseLune.NOUVELLE, PhaseLune.PREMIER_QUARTIER, PhaseLune.PLEINE, PhaseLune.DERNIER_QUARTIER)
        return (k0..k0 + 3).flatMap { k -> (0..3).map { q -> msDeJourJulien(phaseVraie(k, q)) to majeures[q] } }
            .sortedBy { it.first }
    }

    /** État de la lune à l'instant [t] : éclairement, nom de la phase (jour civil de [fuseau]), prochaine phase. */
    fun lune(t: Long, fuseau: TimeZone = TimeZone.getDefault()): EtatLune {
        val (fraction, croissante) = eclairementLune(t)
        val phases = phasesAutour(t)
        val prochaine = phases.firstOrNull { it.first > t }
            ?: (t + (MOIS_SYNODIQUE * JOUR_MS / 4).toLong() to PhaseLune.NOUVELLE)
        // Phase principale tombant le jour même : c'est elle qu'on nomme
        val jour = cleJour(t, fuseau)
        val duJour = phases.firstOrNull { cleJour(it.first, fuseau) == jour }?.second
        val phase = duJour ?: when {
            croissante && fraction < 0.5 -> PhaseLune.PREMIER_CROISSANT
            croissante -> PhaseLune.GIBBEUSE_CROISSANTE
            fraction > 0.5 -> PhaseLune.GIBBEUSE_DECROISSANTE
            else -> PhaseLune.DERNIER_CROISSANT
        }
        return EtatLune(fraction, croissante, phase, prochaine.second, prochaine.first)
    }

    private fun cleJour(t: Long, fuseau: TimeZone): Int {
        val c = Calendar.getInstance(fuseau).apply { timeInMillis = t }
        return c.get(Calendar.YEAR) * 1000 + c.get(Calendar.DAY_OF_YEAR)
    }

    /** Écart en jours civils (dans [fuseau]) entre [t] et [reference] : 0 aujourd'hui, 1 demain… */
    fun ecartJours(t: Long, reference: Long, fuseau: TimeZone = TimeZone.getDefault()): Int {
        fun jourAbsolu(x: Long): Long {
            val c = Calendar.getInstance(fuseau).apply { timeInMillis = x }
            // floorDiv de Kotlin (Math.floorDiv n'existe qu'à partir d'Android 7)
            return (x + c.get(Calendar.ZONE_OFFSET) + c.get(Calendar.DST_OFFSET)).floorDiv(JOUR_MS)
        }
        return (jourAbsolu(t) - jourAbsolu(reference)).toInt()
    }
}

enum class Polaire { NUIT, JOUR }

/**
 * Soleil d'un jour civil. [lever] / [coucher] : null pendant la nuit ou le jour polaire
 * ([polaire]) ; [dureeJour] en ms (0 en nuit polaire, 24 h en jour polaire).
 */
data class JourneeSoleil(
    val lever: Long?,
    val coucher: Long?,
    val midi: Long,
    val dureeJour: Long,
    val polaire: Polaire?,
)

/** Phases de la lune ; les 4 principales (nouvelle, quartiers, pleine) ont une date exacte. */
enum class PhaseLune(val libelle: String, val court: String) {
    NOUVELLE("Nouvelle lune", "Nouvelle lune"),
    PREMIER_CROISSANT("Premier croissant", "1er croissant"),
    PREMIER_QUARTIER("Premier quartier", "1er quartier"),
    GIBBEUSE_CROISSANTE("Gibbeuse croissante", "Gibbeuse croiss."),
    PLEINE("Pleine lune", "Pleine lune"),
    GIBBEUSE_DECROISSANTE("Gibbeuse décroissante", "Gibbeuse décr."),
    DERNIER_QUARTIER("Dernier quartier", "Dernier quartier"),
    DERNIER_CROISSANT("Dernier croissant", "Dernier croiss."),
}

/**
 * Lune à un instant : [fraction] éclairée (0 à 1), [croissante], [phase] du jour, et prochaine
 * phase principale ([prochaine], à la date [prochaineDate], ms UTC).
 */
data class EtatLune(
    val fraction: Double,
    val croissante: Boolean,
    val phase: PhaseLune,
    val prochaine: PhaseLune,
    val prochaineDate: Long,
)
