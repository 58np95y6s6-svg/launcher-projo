package fr.projo.launcher.modules.bourse

import android.util.Log
import fr.projo.launcher.modules.DonneesModules
import fr.projo.launcher.modules.Reseau
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.concurrent.ConcurrentHashMap

/** Origine d'un cours. */
enum class SourceCours { CRYPTO, YAHOO }

/**
 * Un cours proposé dans les réglages. [id] (enregistré) : « c:bitcoin », « y:^FCHI ».
 * [indice] : valeur en points (pas de devise affichée).
 */
class Instrument(
    val source: SourceCours,
    /** Identifiant CoinGecko (bitcoin) ou symbole Yahoo (^FCHI, MC.PA). */
    val code: String,
    /** Nom court affiché (BTC, CAC 40). */
    val court: String,
    /** Nom complet (réglages, sous-titre). */
    val nom: String,
    val categorie: String,
    val indice: Boolean = false,
) {
    val id: String get() = (if (source == SourceCours.CRYPTO) "c:" else "y:") + code
}

private const val CRYPTOS = "CRYPTOMONNAIES"
private const val INDICES = "INDICES"
private const val ACTIONS = "ACTIONS FRANÇAISES"

/** Catalogue proposé dans les réglages (l'ordre est celui du dialogue). */
val INSTRUMENTS: List<Instrument> = listOf(
    Instrument(SourceCours.CRYPTO, "bitcoin", "BTC", "Bitcoin", CRYPTOS),
    Instrument(SourceCours.CRYPTO, "ethereum", "ETH", "Ethereum", CRYPTOS),
    Instrument(SourceCours.CRYPTO, "solana", "SOL", "Solana", CRYPTOS),
    Instrument(SourceCours.CRYPTO, "ripple", "XRP", "XRP", CRYPTOS),
    Instrument(SourceCours.CRYPTO, "binancecoin", "BNB", "BNB", CRYPTOS),
    Instrument(SourceCours.CRYPTO, "cardano", "ADA", "Cardano", CRYPTOS),
    Instrument(SourceCours.CRYPTO, "dogecoin", "DOGE", "Dogecoin", CRYPTOS),
    Instrument(SourceCours.YAHOO, "^FCHI", "CAC 40", "CAC 40 (Paris)", INDICES, indice = true),
    Instrument(SourceCours.YAHOO, "^GSPC", "S&P 500", "S&P 500 (New York)", INDICES, indice = true),
    Instrument(SourceCours.YAHOO, "^IXIC", "Nasdaq", "Nasdaq Composite", INDICES, indice = true),
    Instrument(SourceCours.YAHOO, "^DJI", "Dow Jones", "Dow Jones Industrial", INDICES, indice = true),
    Instrument(SourceCours.YAHOO, "^STOXX50E", "Stoxx 50", "Euro Stoxx 50", INDICES, indice = true),
    Instrument(SourceCours.YAHOO, "MC.PA", "LVMH", "LVMH", ACTIONS),
    Instrument(SourceCours.YAHOO, "TTE.PA", "Total", "TotalEnergies", ACTIONS),
    Instrument(SourceCours.YAHOO, "AIR.PA", "Airbus", "Airbus", ACTIONS),
    Instrument(SourceCours.YAHOO, "OR.PA", "L'Oréal", "L'Oréal", ACTIONS),
    Instrument(SourceCours.YAHOO, "RMS.PA", "Hermès", "Hermès", ACTIONS),
    Instrument(SourceCours.YAHOO, "SAN.PA", "Sanofi", "Sanofi", ACTIONS),
    Instrument(SourceCours.YAHOO, "BNP.PA", "BNP", "BNP Paribas", ACTIONS),
    Instrument(SourceCours.YAHOO, "SU.PA", "Schneider", "Schneider Electric", ACTIONS),
)

val INSTRUMENTS_PAR_DEFAUT = listOf("c:bitcoin", "c:ethereum", "y:^FCHI", "y:^GSPC")

/**
 * Cours d'un instrument : prix, variation en % (24 h pour les cryptos, depuis la clôture de
 * la veille pour la bourse), courbe récente (du plus ancien au plus récent) et devise (code ISO).
 */
data class Cours(val prix: Double, val variation: Double?, val courbe: List<Double>, val devise: String?)

/** Cours des cryptos choisies (une seule requête de prix pour toutes) : id CoinGecko → cours. */
data class CoursCryptos(val cours: Map<String, Cours>)

/**
 * Accès réseau du module Bourse, sans clé :
 *  - CoinGecko (api.coingecko.com) : simple/price pour les prix et la variation sur 24 h,
 *    market_chart (1 jour) pour les courbes, gardées 30 min (limites de débit de l'API publique) ;
 *  - Yahoo Finance (query1/query2.finance.yahoo.com, v8/finance/chart) pour les indices et
 *    les actions : prix, clôture de la veille et points de la séance.
 */
object BourseReseau {

    private const val TAG = DonneesModules.TAG
    private const val COURBE_MAX = 48
    private const val VALIDITE_COURBE = 30 * 60_000L

    /** Courbes CoinGecko déjà chargées : « id:devise » → (date, points). */
    private val courbes = ConcurrentHashMap<String, Pair<Long, List<Double>>>()

    fun cleCryptos(ids: List<String>, devise: String) = "bourse:crypto:$devise:" + ids.joinToString(",")
    fun cleYahoo(symbole: String) = "bourse:yahoo:$symbole"

    /** Lecture HTTPS (navigateur générique : Yahoo refuse certains agents). */
    private fun lire(url: String): String {
        val c = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 7000
            readTimeout = 7000
            setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 11; TV) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120 Safari/537.36")
            setRequestProperty("Accept", "application/json")
        }
        try {
            val code = c.responseCode
            if (code != 200) throw Reseau.ErreurHttp(code)
            return c.inputStream.bufferedReader().use { it.readText() }
        } finally {
            c.disconnect()
        }
    }

    // ------------------------------------------------------------------ CoinGecko

    fun chargerCryptos(ids: List<String>, devise: String): CoursCryptos {
        if (ids.isEmpty()) return CoursCryptos(emptyMap())
        val dv = devise.lowercase()
        val json = lire(
            "https://api.coingecko.com/api/v3/simple/price?ids=" + URLEncoder.encode(ids.joinToString(","), "UTF-8") +
                "&vs_currencies=$dv&include_24hr_change=true",
        )
        val prix = lirePrixCryptos(JSONObject(json), ids, dv)
        if (prix.isEmpty()) error("aucun prix CoinGecko")
        val resultat = prix.mapValues { (id, c) ->
            val points = courbeCrypto(id, dv)
            c.copy(courbe = if (points.size >= 2) points + c.prix else points)
        }
        return CoursCryptos(resultat)
    }

    /** {"bitcoin":{"eur":58000.1,"eur_24h_change":1.23}, …} */
    fun lirePrixCryptos(j: JSONObject, ids: List<String>, devise: String): Map<String, Cours> {
        val res = LinkedHashMap<String, Cours>()
        for (id in ids) {
            val o = j.optJSONObject(id) ?: continue
            val p = o.optDouble(devise)
            if (p.isNaN() || p <= 0.0) continue
            val v = o.optDouble(devise + "_24h_change").takeIf { !it.isNaN() }
            res[id] = Cours(p, v, emptyList(), devise.uppercase())
        }
        return res
    }

    /** Courbe des dernières 24 h (gardée 30 min ; en cas d'échec, l'ancienne ou rien). */
    private fun courbeCrypto(id: String, devise: String): List<Double> {
        val cle = "$id:$devise"
        val connue = courbes[cle]
        if (connue != null && System.currentTimeMillis() - connue.first < VALIDITE_COURBE) return connue.second
        return try {
            val j = JSONObject(lire("https://api.coingecko.com/api/v3/coins/${URLEncoder.encode(id, "UTF-8")}/market_chart?vs_currency=$devise&days=1"))
            val points = lirePoints(j.optJSONArray("prices"))
            courbes[cle] = System.currentTimeMillis() to points
            points
        } catch (e: Exception) {
            Log.w(TAG, "Bourse : courbe de $id indisponible", e)
            connue?.second ?: emptyList()
        }
    }

    /** [[ms, prix], …] → prix, réduits à [COURBE_MAX] points. */
    fun lirePoints(a: JSONArray?): List<Double> {
        if (a == null) return emptyList()
        val tous = ArrayList<Double>(a.length())
        for (i in 0 until a.length()) {
            val v = a.optJSONArray(i)?.optDouble(1) ?: continue
            if (!v.isNaN()) tous += v
        }
        return reduire(tous)
    }

    private fun reduire(points: List<Double>): List<Double> {
        if (points.size <= COURBE_MAX) return points
        val pas = points.size.toDouble() / COURBE_MAX
        return List(COURBE_MAX) { i -> points[(i * pas).toInt().coerceAtMost(points.size - 1)] } + points.last()
    }

    // ------------------------------------------------------------------ Yahoo Finance

    fun chargerYahoo(symbole: String): Cours {
        val s = URLEncoder.encode(symbole, "UTF-8")
        var derniere: Exception? = null
        for (hote in listOf("query1", "query2")) {
            try {
                return lireYahoo(JSONObject(lire("https://$hote.finance.yahoo.com/v8/finance/chart/$s?range=1d&interval=15m")))
            } catch (e: Exception) {
                Log.w(TAG, "Bourse : $hote ne répond pas pour $symbole", e)
                derniere = e
            }
        }
        throw derniere ?: IllegalStateException("Yahoo indisponible")
    }

    /**
     * {"chart":{"result":[{"meta":{"currency":"EUR","regularMarketPrice":…,
     * "chartPreviousClose":…},"indicators":{"quote":[{"close":[…]}]}}]}}
     */
    fun lireYahoo(j: JSONObject): Cours {
        val r = j.getJSONObject("chart").getJSONArray("result").getJSONObject(0)
        val meta = r.getJSONObject("meta")
        val fermetures = r.optJSONObject("indicators")?.optJSONArray("quote")?.optJSONObject(0)?.optJSONArray("close")
        val points = ArrayList<Double>()
        if (fermetures != null) {
            for (i in 0 until fermetures.length()) {
                if (fermetures.isNull(i)) continue
                val v = fermetures.optDouble(i)
                if (!v.isNaN() && v > 0.0) points += v
            }
        }
        val prix = meta.optDouble("regularMarketPrice").takeIf { !it.isNaN() && it > 0.0 }
            ?: points.lastOrNull() ?: error("prix absent")
        val veille = listOf("chartPreviousClose", "previousClose", "regularMarketPreviousClose")
            .map { meta.optDouble(it) }.firstOrNull { !it.isNaN() && it > 0.0 }
        val variation = veille?.let { (prix - it) / it * 100.0 }
        val courbe = reduire(points).let { if (it.size >= 2) it + prix else it }
        return Cours(prix, variation, courbe, meta.optString("currency").ifBlank { null })
    }
}
