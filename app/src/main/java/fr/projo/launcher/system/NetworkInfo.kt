package fr.projo.launcher.system

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager

/**
 * Libellé de la pastille Wi-Fi : nom du réseau si Android le donne, sinon « Wi-Fi »,
 * ou « Hors ligne ». (Depuis Android 8.1, le nom n'est fourni qu'avec la permission
 * de localisation : sans elle on affiche simplement « Wi-Fi ».)
 */
object NetworkInfo {
    /** Pas de réseau. */
    const val AUCUN = -1
    /** Autre réseau (mobile, VPN…). */
    const val AUTRE = 99

    /**
     * Type de la connexion active : NetworkCapabilities.TRANSPORT_WIFI, TRANSPORT_ETHERNET,
     * AUTRE ou AUCUN. Compatible avec les vieux Fire TV (Android 5.1).
     */
    @Suppress("DEPRECATION")
    fun transportActif(context: Context): Int {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        if (android.os.Build.VERSION.SDK_INT >= 23) {
            val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return AUCUN
            return when {
                caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> NetworkCapabilities.TRANSPORT_ETHERNET
                caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> NetworkCapabilities.TRANSPORT_WIFI
                else -> AUTRE
            }
        }
        val info = cm.activeNetworkInfo ?: return AUCUN
        if (!info.isConnected) return AUCUN
        return when (info.type) {
            ConnectivityManager.TYPE_ETHERNET -> NetworkCapabilities.TRANSPORT_ETHERNET
            ConnectivityManager.TYPE_WIFI -> NetworkCapabilities.TRANSPORT_WIFI
            else -> AUTRE
        }
    }

    fun wifiLabel(context: Context): String {
        return try {
            when (transportActif(context)) {
                AUCUN -> "Hors ligne"
                NetworkCapabilities.TRANSPORT_ETHERNET -> "Ethernet"
                NetworkCapabilities.TRANSPORT_WIFI -> nomWifi(context) ?: "Wi-Fi"
                else -> "Wi-Fi"
            }
        } catch (e: Exception) {
            "Wi-Fi"
        }
    }

    @Suppress("DEPRECATION")
    private fun nomWifi(context: Context): String? {
        val wm = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager ?: return null
        val ssid = wm.connectionInfo?.ssid?.trim('"') ?: return null
        return ssid.takeIf { it.isNotBlank() && it != "<unknown ssid>" && it != "0x" }
    }
}
