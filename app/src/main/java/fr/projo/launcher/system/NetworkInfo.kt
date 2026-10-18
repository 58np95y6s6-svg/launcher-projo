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
    fun wifiLabel(context: Context): String {
        return try {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val caps = cm.getNetworkCapabilities(cm.activeNetwork)
            when {
                caps == null -> "Hors ligne"
                caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "Ethernet"
                caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> nomWifi(context) ?: "Wi-Fi"
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
