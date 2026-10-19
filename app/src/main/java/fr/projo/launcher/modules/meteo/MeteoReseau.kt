package fr.projo.launcher.modules.meteo

import android.util.Log
import fr.projo.launcher.modules.DonneesModules
import fr.projo.launcher.modules.Reseau
import org.json.JSONObject
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

/** Un lieu : nom affiché et coordonnées. Enregistré sous la forme « nom;lat;lon ». */
data class Lieu(val nom: String, val lat: Double, val lon: Double) {
    fun encoder(): String = "${nom.replace(';', ',')};$lat;$lon"

    companion object {
        fun decoder(s: String?): Lieu? {
            val p = s?.split(';') ?: return null
            if (p.size != 3) return null
            val lat = p[1].toDoubleOrNull() ?: return null
            val lon = p[2].toDoubleOrNull() ?: return null
            return Lieu(p[0], lat, lon)
        }
    }
}

/** Météo du moment et des 3 prochains jours. Codes du temps : WMO (voir [CielMeteo.de]). */
data class MeteoDonnees(
    val lieu: String,
    val temperature: Double,
    val ressenti: Double,
    val code: Int,
    val jour: Boolean,
    /** Pluie attendue dans l'heure : null = pas de pluie ; sinon heure de début (ms), ≤ maintenant = en cours. */
    val pluieDebut: Long?,
    val jours: List<MeteoJour>,
)

data class MeteoJour(val date: Long, val code: Int, val min: Double, val max: Double, val probaPluie: Int?)

/** Grandes villes françaises proposées dans les réglages (pas de saisie à la télécommande). */
val VILLES: List<Lieu> = listOf(
    Lieu("Paris", 48.8566, 2.3522),
    Lieu("Marseille", 43.2965, 5.3698),
    Lieu("Lyon", 45.7640, 4.8357),
    Lieu("Toulouse", 43.6047, 1.4442),
    Lieu("Nice", 43.7102, 7.2620),
    Lieu("Nantes", 47.2184, -1.5536),
    Lieu("Montpellier", 43.6108, 3.8767),
    Lieu("Strasbourg", 48.5734, 7.7521),
    Lieu("Bordeaux", 44.8378, -0.5792),
    Lieu("Lille", 50.6292, 3.0573),
    Lieu("Rennes", 48.1173, -1.6778),
    Lieu("Reims", 49.2583, 4.0317),
    Lieu("Toulon", 43.1242, 5.9280),
    Lieu("Grenoble", 45.1885, 5.7245),
    Lieu("Dijon", 47.3220, 5.0415),
    Lieu("Angers", 47.4784, -0.5632),
    Lieu("Nîmes", 43.8367, 4.3601),
    Lieu("Clermont-Ferrand", 45.7772, 3.0870),
    Lieu("Le Havre", 49.4944, 0.1079),
    Lieu("Tours", 47.3941, 0.6848),
    Lieu("Limoges", 45.8336, 1.2611),
    Lieu("Amiens", 49.8941, 2.2958),
    Lieu("Perpignan", 42.6887, 2.8948),
    Lieu("Metz", 49.1193, 6.1757),
    Lieu("Besançon", 47.2378, 6.0241),
    Lieu("Orléans", 47.9030, 1.9093),
    Lieu("Rouen", 49.4432, 1.0999),
    Lieu("Caen", 49.1829, -0.3707),
    Lieu("Nancy", 48.6921, 6.1844),
    Lieu("Brest", 48.3904, -4.4861),
    Lieu("Avignon", 43.9493, 4.8055),
    Lieu("La Rochelle", 46.1603, -1.1511),
    Lieu("Pau", 43.2951, -0.3708),
    Lieu("Bayonne", 43.4929, -1.4748),
    Lieu("Ajaccio", 41.9192, 8.7386),
)

/** Accès réseau du module Météo (Open-Meteo et géolocalisation par IP, sans clé). */
object MeteoReseau {

    private const val TAG = DonneesModules.TAG

    /**
     * Position approximative d'après l'adresse IP : plusieurs services sans clé, essayés dans
     * l'ordre (le premier qui répond l'emporte). Lève une exception si aucun ne répond.
     */
    fun localiserParIp(): Lieu {
        val services: List<Pair<String, (JSONObject) -> Lieu?>> = listOf(
            "https://ipapi.co/json/" to { j -> lieuIp(j, "city", "latitude", "longitude") },
            "https://ipwho.is/" to { j -> if (j.optBoolean("success", true)) lieuIp(j, "city", "latitude", "longitude") else null },
            "https://freeipapi.com/api/json" to { j -> lieuIp(j, "cityName", "latitude", "longitude") },
            "https://get.geojs.io/v1/ip/geo.json" to { j -> lieuIp(j, "city", "latitude", "longitude") },
        )
        for ((url, lire) in services) {
            try {
                val lieu = lire(JSONObject(Reseau.lireTexte(url, 5000)))
                if (lieu != null) return lieu
            } catch (e: Exception) {
                Log.w(TAG, "Géolocalisation IP : $url ne répond pas", e)
            }
        }
        error("position introuvable")
    }

    private fun lieuIp(j: JSONObject, cleVille: String, cleLat: String, cleLon: String): Lieu? {
        // Certains services donnent les coordonnées en texte
        val lat = j.opt(cleLat)?.toString()?.toDoubleOrNull() ?: return null
        val lon = j.opt(cleLon)?.toString()?.toDoubleOrNull() ?: return null
        if (lat == 0.0 && lon == 0.0) return null
        return Lieu(j.optString(cleVille).ifBlank { "Ma position" }, lat, lon)
    }

    /** Recherche de villes (géocodage Open-Meteo, en français). */
    fun rechercher(texte: String): List<Pair<Lieu, String>> {
        val q = URLEncoder.encode(texte.trim(), "UTF-8")
        val j = JSONObject(Reseau.lireTexte("https://geocoding-api.open-meteo.com/v1/search?name=$q&count=10&language=fr&format=json", 6000))
        val res = j.optJSONArray("results") ?: return emptyList()
        return (0 until res.length()).mapNotNull { i ->
            val r = res.optJSONObject(i) ?: return@mapNotNull null
            val nom = r.optString("name").ifBlank { return@mapNotNull null }
            val detail = listOf(r.optString("admin1"), r.optString("country"))
                .filter { it.isNotBlank() }.joinToString(", ")
            Lieu(nom, r.optDouble("latitude"), r.optDouble("longitude")) to detail
        }.filter { !it.first.lat.isNaN() && !it.first.lon.isNaN() }
    }

    /** Prévisions Open-Meteo : maintenant, pluie par quart d'heure, 3 prochains jours. */
    fun charger(lieu: Lieu): MeteoDonnees {
        val base = "https://api.open-meteo.com/v1/forecast?latitude=${lieu.lat}&longitude=${lieu.lon}" +
            "&current=temperature_2m,apparent_temperature,weather_code,is_day,precipitation" +
            "&daily=weather_code,temperature_2m_max,temperature_2m_min,precipitation_probability_max" +
            "&timezone=auto&forecast_days=4"
        val json = try {
            Reseau.lireTexte("$base&minutely_15=precipitation&forecast_minutely_15=8")
        } catch (e: Reseau.ErreurHttp) {
            // Paramètre refusé : on se passe de la pluie au quart d'heure
            if (e.code !in 400..499) throw e
            Reseau.lireTexte(base)
        }
        return lire(JSONObject(json), lieu.nom)
    }

    fun lire(j: JSONObject, nom: String): MeteoDonnees {
        val decalage = j.optLong("utc_offset_seconds", 0L) * 1000L
        val c = j.getJSONObject("current")
        val maintenant = System.currentTimeMillis()

        // Pluie dans l'heure : premier quart d'heure pluvieux entre maintenant et +1 h
        var pluie: Long? = null
        j.optJSONObject("minutely_15")?.let { m ->
            val temps = m.optJSONArray("time")
            val mm = m.optJSONArray("precipitation")
            if (temps != null && mm != null) {
                for (i in 0 until minOf(temps.length(), mm.length())) {
                    val t = heureLocale(temps.optString(i), decalage) ?: continue
                    if (t < maintenant - 15 * 60_000L || t > maintenant + 60 * 60_000L) continue
                    if (mm.optDouble(i, 0.0) >= 0.1) { pluie = t; break }
                }
            }
        }
        if (pluie == null && c.optDouble("precipitation", 0.0) >= 0.1) pluie = maintenant

        val jours = ArrayList<MeteoJour>()
        j.optJSONObject("daily")?.let { d ->
            val dates = d.optJSONArray("time")
            val codes = d.optJSONArray("weather_code")
            val max = d.optJSONArray("temperature_2m_max")
            val min = d.optJSONArray("temperature_2m_min")
            val proba = d.optJSONArray("precipitation_probability_max")
            if (dates != null && codes != null && max != null && min != null) {
                // Indice 0 = aujourd'hui : on garde les 3 jours suivants
                for (i in 1 until minOf(4, dates.length())) {
                    val t = heureLocale(dates.optString(i) + "T12:00", decalage) ?: continue
                    jours += MeteoJour(
                        t, codes.optInt(i), min.optDouble(i), max.optDouble(i),
                        proba?.takeIf { !it.isNull(i) }?.optInt(i),
                    )
                }
            }
        }
        return MeteoDonnees(
            lieu = nom,
            temperature = c.getDouble("temperature_2m"),
            ressenti = c.optDouble("apparent_temperature", c.getDouble("temperature_2m")),
            code = c.optInt("weather_code"),
            jour = c.optInt("is_day", 1) == 1,
            pluieDebut = pluie,
            jours = jours,
        )
    }

    /** « 2026-10-19T18:15 » (heure locale du lieu) → millisecondes. */
    private fun heureLocale(s: String, decalage: Long): Long? = try {
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }
            .parse(s)?.time?.minus(decalage)
    } catch (e: Exception) {
        null
    }
}
