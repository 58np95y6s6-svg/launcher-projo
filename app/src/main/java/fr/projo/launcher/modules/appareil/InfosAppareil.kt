package fr.projo.launcher.modules.appareil

import android.app.ActivityManager
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.util.Log
import fr.projo.launcher.modules.DonneesModules
import fr.projo.launcher.system.NetworkInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.Inet4Address
import java.net.NetworkInterface
import java.net.URL
import kotlin.coroutines.coroutineContext

/** Instantané de l'état de l'appareil (module « État de l'appareil »). */
data class InfosAppareil(
    /** Nom du Wi-Fi, « Wi-Fi » (nom masqué par Android), « Ethernet » ou « Hors ligne ». */
    val reseau: String,
    val wifi: Boolean,
    val enLigne: Boolean,
    /** Barres de signal Wi-Fi, 0 à 4 (-1 : inconnu ou pas en Wi-Fi). */
    val signal: Int,
    /** Puissance du signal Wi-Fi en dBm (null : inconnue). */
    val rssi: Int?,
    val ip: String?,
    val stockageLibre: Long,
    val stockageTotal: Long,
    val memoireLibre: Long,
    val memoireTotale: Long,
    /** Température du processeur en °C (null : illisible sans root). */
    val temperature: Double?,
    /** Version d'iBiProjo (versionName). */
    val version: String,
    /** « Android 11 » (affiché à la place de la température si elle est illisible). */
    val android: String,
)

/** Lecture de l'état de l'appareil : rapide, à appeler hors du thread principal, jamais d'exception. */
object LecteurAppareil {

    private const val TAG = DonneesModules.TAG

    fun lire(context: Context): InfosAppareil {
        val app = context.applicationContext
        val (reseau, wifi, enLigne) = lireReseau(app)
        val (signal, rssi) = if (wifi) lireSignal(app) else (-1 to null)
        val stat = try { StatFs(Environment.getDataDirectory().path) } catch (e: Exception) { null }
        val memoire = try {
            val am = app.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            ActivityManager.MemoryInfo().also { am.getMemoryInfo(it) }
        } catch (e: Exception) {
            null
        }
        return InfosAppareil(
            reseau = reseau,
            wifi = wifi,
            enLigne = enLigne,
            signal = signal,
            rssi = rssi,
            ip = lireIp(),
            stockageLibre = runCatching { stat?.availableBytes }.getOrNull() ?: 0L,
            stockageTotal = runCatching { stat?.totalBytes }.getOrNull() ?: 0L,
            memoireLibre = memoire?.availMem ?: 0L,
            memoireTotale = memoire?.totalMem ?: 0L,
            temperature = lireTemperature(),
            version = lireVersion(app),
            android = "Android " + Build.VERSION.RELEASE,
        )
    }

    /** (libellé, Wi-Fi ?, en ligne ?) */
    @Suppress("DEPRECATION")
    private fun lireReseau(context: Context): Triple<String, Boolean, Boolean> = try {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        if (Build.VERSION.SDK_INT >= 23) {
            val caps = cm.getNetworkCapabilities(cm.activeNetwork)
            when {
                caps == null -> Triple("Hors ligne", false, false)
                caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> Triple("Ethernet", false, true)
                caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> Triple(nomWifi(context), true, true)
                else -> Triple("Connecté", false, true)
            }
        } else {
            val info = cm.activeNetworkInfo
            when {
                info == null || !info.isConnected -> Triple("Hors ligne", false, false)
                info.type == ConnectivityManager.TYPE_ETHERNET -> Triple("Ethernet", false, true)
                info.type == ConnectivityManager.TYPE_WIFI -> Triple(nomWifi(context), true, true)
                else -> Triple("Connecté", false, true)
            }
        }
    } catch (e: Exception) {
        Triple("Réseau inconnu", false, false)
    }

    private fun nomWifi(context: Context): String =
        runCatching { NetworkInfo.wifiLabel(context) }.getOrNull()?.takeIf { it != "Hors ligne" } ?: "Wi-Fi"

    @Suppress("DEPRECATION")
    private fun lireSignal(context: Context): Pair<Int, Int?> = try {
        val wm = context.getSystemService(Context.WIFI_SERVICE) as WifiManager
        val rssi = wm.connectionInfo?.rssi
        if (rssi == null || rssi <= -127 || rssi >= 0) -1 to null
        else WifiManager.calculateSignalLevel(rssi, 5).coerceIn(0, 4) to rssi
    } catch (e: Exception) {
        -1 to null
    }

    /** Adresse IPv4 locale (Wi-Fi ou Ethernet d'abord). */
    private fun lireIp(): String? = try {
        val interfaces = NetworkInterface.getNetworkInterfaces()?.toList().orEmpty()
            .filter { runCatching { it.isUp && !it.isLoopback }.getOrDefault(false) }
            .sortedBy { i ->
                val n = i.name.orEmpty()
                when {
                    n.startsWith("wlan") -> 0
                    n.startsWith("eth") -> 1
                    else -> 2
                }
            }
        interfaces.firstNotNullOfOrNull { i ->
            i.inetAddresses?.toList()?.firstOrNull { it is Inet4Address && !it.isLoopbackAddress }?.hostAddress
        }
    } catch (e: Exception) {
        null
    }

    /**
     * Température du processeur d'après /sys/class/thermal (lisible sans root sur la plupart des
     * boîtiers ; sinon null, sans message). Zones « cpu », « soc »… d'abord, sinon la plus chaude.
     */
    fun lireTemperature(base: File = File("/sys/class/thermal")): Double? = try {
        val zones = base.listFiles()?.filter { it.name.startsWith("thermal_zone") }?.take(24).orEmpty()
        val mesures = zones.mapNotNull { z ->
            val brut = runCatching { File(z, "temp").readText().trim().toDouble() }.getOrNull() ?: return@mapNotNull null
            val type = runCatching { File(z, "type").readText().trim().lowercase() }.getOrDefault("")
            val c = when {
                brut > 1000 -> brut / 1000.0
                brut > 200 -> brut / 10.0
                else -> brut
            }
            if (c in 5.0..125.0) type to c else null
        }
        val processeur = mesures.filter { (t, _) -> listOf("cpu", "soc", "tsens", "ap", "core").any { t.contains(it) } }
        (processeur.ifEmpty { mesures }).maxOfOrNull { it.second }
    } catch (e: Throwable) {
        null
    }

    private fun lireVersion(context: Context): String = try {
        @Suppress("DEPRECATION")
        context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "?"
    } catch (e: Exception) {
        "?"
    }
}

/** État du test de débit. */
sealed class EtatDebit {
    data object Inactif : EtatDebit()
    /** [progression] de 0 à 1. */
    data class EnCours(val progression: Float) : EtatDebit()
    /** Débit descendant en mégabits par seconde. */
    data class Resultat(val mbps: Double) : EtatDebit()
    data object Erreur : EtatDebit()
}

/**
 * Test de débit léger, lancé seulement à la demande (OK sur le module) : téléchargement de
 * 5 Mo au plus (Cloudflare, sinon OVH), 15 s au plus. Une mesure à la fois.
 */
object TestDebit {

    private const val OCTETS = 5_000_000
    private const val DUREE_MAX = 15_000L

    private val sources = listOf(
        "https://speed.cloudflare.com/__down?bytes=$OCTETS",
        "https://proof.ovh.net/files/10Mb.dat",
    )

    private val etat = MutableStateFlow<EtatDebit>(EtatDebit.Inactif)
    val flux: StateFlow<EtatDebit> get() = etat

    val enCours: Boolean get() = etat.value is EtatDebit.EnCours

    /** Mesure (à lancer dans une coroutine ; annulée si le module disparaît). */
    suspend fun mesurer() {
        if (enCours) return
        etat.value = EtatDebit.EnCours(0f)
        try {
            val mbps = withContext(Dispatchers.IO) {
                var resultat: Double? = null
                for (url in sources) {
                    resultat = runCatching { telecharger(url) }.onFailure { Log.w(DonneesModules.TAG, "Débit : $url", it) }.getOrNull()
                    if (resultat != null || !coroutineContext.isActive) break
                }
                resultat
            }
            etat.value = if (mbps != null) EtatDebit.Resultat(mbps) else EtatDebit.Erreur
        } catch (e: kotlinx.coroutines.CancellationException) {
            etat.value = EtatDebit.Inactif
            throw e
        } catch (e: Exception) {
            etat.value = EtatDebit.Erreur
        }
    }

    private suspend fun telecharger(url: String): Double {
        val c = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 6000
            readTimeout = 6000
            setRequestProperty("User-Agent", "Projo-Launcher")
        }
        try {
            if (c.responseCode != 200) error("HTTP ${c.responseCode}")
            val tampon = ByteArray(32 * 1024)
            var total = 0L
            // Chronomètre lancé à la réception des en-têtes : la latence ne compte pas
            val debut = System.nanoTime()
            c.inputStream.use { flux ->
                while (total < OCTETS && coroutineContext.isActive) {
                    val n = flux.read(tampon)
                    if (n < 0) break
                    total += n
                    etat.value = EtatDebit.EnCours((total.toFloat() / OCTETS).coerceIn(0f, 1f))
                    if ((System.nanoTime() - debut) / 1_000_000L > DUREE_MAX) break
                }
            }
            val secondes = (System.nanoTime() - debut) / 1e9
            if (total < 200_000L || secondes <= 0.0) error("trop peu de données")
            return total * 8 / secondes / 1_000_000.0
        } finally {
            c.disconnect()
        }
    }

    /** Tests : remet l'état à zéro. */
    internal fun reinitialiser() {
        etat.value = EtatDebit.Inactif
    }
}
