package fr.projo.launcher.modules.air

import androidx.compose.ui.graphics.Color
import fr.projo.launcher.modules.Reseau
import fr.projo.launcher.modules.meteo.Lieu
import org.json.JSONArray
import org.json.JSONObject

/** Niveaux de l'indice européen de qualité de l'air (European AQI, libellés d'Atmo France). */
enum class NiveauAir(val libelle: String, val couleur: Color, val max: Int) {
    BON("Bon", Color(0xFF50F0E6), 20),
    MOYEN("Moyen", Color(0xFF50CCAA), 40),
    DEGRADE("Dégradé", Color(0xFFF0E641), 60),
    MAUVAIS("Mauvais", Color(0xFFFF5050), 80),
    TRES_MAUVAIS("Très mauvais", Color(0xFF960032), 100),
    EXTREME("Extrêmement mauvais", Color(0xFF872181), Int.MAX_VALUE);

    companion object {
        fun de(indice: Int): NiveauAir = entries.firstOrNull { indice <= it.max } ?: EXTREME
    }
}

/** Polluant de l'indice européen : [concentration] en µg/m³, [indice] = sous-indice européen. */
data class Polluant(val nom: String, val concentration: Double?, val indice: Int?)

/** Pollens suivis par Open-Meteo (Europe seulement, en saison), avec des seuils en grains/m³. */
enum class TypePollen(val cle: String, val nom: String, private val seuils: IntArray) {
    GRAMINEES("grass_pollen", "Graminées", intArrayOf(1, 20, 50, 150)),
    BOULEAU("birch_pollen", "Bouleau", intArrayOf(1, 30, 100, 500)),
    AMBROISIE("ragweed_pollen", "Ambroisie", intArrayOf(1, 5, 20, 50)),
    OLIVIER("olive_pollen", "Olivier", intArrayOf(1, 20, 100, 400)),
    AULNE("alder_pollen", "Aulne", intArrayOf(1, 30, 100, 400)),
    ARMOISE("mugwort_pollen", "Armoise", intArrayOf(1, 10, 30, 100));

    /** 0 (aucun) à 4 (très élevé), d'après les seuils (approximatifs) de ce pollen. */
    fun niveau(grains: Double): Int = seuils.count { grains >= it }
}

/** Libellés des niveaux de pollen (0 à 4). */
val NIVEAUX_POLLEN = listOf("Nul", "Faible", "Moyen", "Élevé", "Très élevé")

/** Pollen du jour : maximum de la journée en grains/m³. */
data class Pollen(val type: TypePollen, val grains: Double) {
    val niveau: Int get() = type.niveau(grains)
}

/**
 * Qualité de l'air à un lieu.
 *  - [indice] : European AQI (null si indisponible) ;
 *  - [polluants] : triés du plus pénalisant au moins pénalisant ;
 *  - [pollens] : pollens présents aujourd'hui, du plus fort au plus faible ; null si aucune
 *    donnée (hors d'Europe, ou hors saison) ; vide si tous sont nuls ;
 *  - [europe] : le lieu est couvert par les prévisions de pollens (Europe).
 */
data class AirDonnees(
    val lieu: String,
    val indice: Int?,
    val polluants: List<Polluant>,
    val pollens: List<Pollen>?,
    val europe: Boolean,
) {
    val niveau: NiveauAir? get() = indice?.let { NiveauAir.de(it) }
}

/** Accès à l'API Open-Meteo de qualité de l'air (sans clé). */
object AirReseau {

    /** Polluants : clé de la concentration, clé du sous-indice, nom affiché. */
    private val POLLUANTS = listOf(
        Triple("pm2_5", "european_aqi_pm2_5", "PM2,5"),
        Triple("pm10", "european_aqi_pm10", "PM10"),
        Triple("nitrogen_dioxide", "european_aqi_nitrogen_dioxide", "NO₂"),
        Triple("ozone", "european_aqi_ozone", "O₃"),
        Triple("sulphur_dioxide", "european_aqi_sulphur_dioxide", "SO₂"),
    )

    fun cle(lieu: Lieu): String = "air:" + lieu.encoder()

    /** Couverture approximative des prévisions de pollens (modèle européen CAMS). */
    fun enEurope(lieu: Lieu): Boolean = lieu.lat in 30.0..72.0 && lieu.lon in -25.0..45.0

    fun charger(lieu: Lieu): AirDonnees {
        val courant = (listOf("european_aqi") + POLLUANTS.flatMap { listOf(it.first, it.second) }).joinToString(",")
        val pollens = TypePollen.entries.joinToString(",") { it.cle }
        val url = "https://air-quality-api.open-meteo.com/v1/air-quality?latitude=${lieu.lat}&longitude=${lieu.lon}" +
            "&current=$courant&hourly=$pollens&forecast_days=1&timezone=auto"
        return lire(JSONObject(Reseau.lireTexte(url)), lieu)
    }

    /** Lecture défensive de la réponse (valeurs nulles ou absentes tolérées). */
    fun lire(j: JSONObject, lieu: Lieu): AirDonnees {
        val c = j.optJSONObject("current")
        fun nombre(o: JSONObject?, cle: String): Double? =
            if (o == null || !o.has(cle) || o.isNull(cle)) null else o.optDouble(cle).takeIf { !it.isNaN() && it >= 0.0 }

        val polluants = POLLUANTS.map { (conc, indice, nom) ->
            Polluant(nom, nombre(c, conc), nombre(c, indice)?.let { Math.round(it).toInt() })
        }.filter { it.concentration != null || it.indice != null }
            .sortedByDescending { it.indice ?: -1 }
        val indice = nombre(c, "european_aqi")?.let { Math.round(it).toInt() }
            ?: polluants.mapNotNull { it.indice }.maxOrNull()

        // Pollens : maximum de la journée, pour chaque type qui a au moins une valeur
        val h = j.optJSONObject("hourly")
        var mesure = false
        val presents = TypePollen.entries.mapNotNull { type ->
            val valeurs = h?.optJSONArray(type.cle) ?: return@mapNotNull null
            val max = maximum(valeurs) ?: return@mapNotNull null
            mesure = true
            Pollen(type, max).takeIf { it.niveau > 0 }
        }.sortedWith(compareByDescending<Pollen> { it.niveau }.thenByDescending { it.grains })

        return AirDonnees(
            lieu = lieu.nom,
            indice = indice,
            polluants = polluants,
            pollens = if (mesure) presents else null,
            europe = enEurope(lieu),
        )
    }

    private fun maximum(a: JSONArray): Double? {
        var max: Double? = null
        for (i in 0 until a.length()) {
            if (a.isNull(i)) continue
            val v = a.optDouble(i)
            if (v.isNaN() || v < 0.0) continue
            if (max == null || v > max) max = v
        }
        return max
    }
}
