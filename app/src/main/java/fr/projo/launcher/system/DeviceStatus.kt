package fr.projo.launcher.system

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context
import android.media.AudioManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import android.os.Environment
import android.os.StatFs

/** Instantané de l'état de l'appareil, pour le panneau « Réglages ». */
data class DeviceSnapshot(
    val volume: Int,
    val volumeMax: Int,
    val muet: Boolean,
    /** Nom du réseau, « Wi-Fi », « Ethernet » ou « Hors ligne ». */
    val reseau: String,
    val enLigne: Boolean,
    /** Barres de signal Wi-Fi, de 0 à 4 (-1 si inconnu ou pas en Wi-Fi). */
    val signal: Int,
    /** null si l'appareil n'a pas de Bluetooth. */
    val bluetoothActif: Boolean?,
    val appareilsBluetooth: List<String>,
    val stockageLibre: Long,
    val stockageTotal: Long,
)

/** Lecture de l'état (volume, réseau, Bluetooth, stockage) et réglage du volume. */
class DeviceStatus(private val context: Context) {

    private val audio = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    fun snapshot(): DeviceSnapshot {
        val (reseau, enLigne, signal) = lireReseau()
        val (bt, appareils) = lireBluetooth()
        val stat = try { StatFs(Environment.getDataDirectory().path) } catch (e: Exception) { null }
        return DeviceSnapshot(
            volume = audio.getStreamVolume(AudioManager.STREAM_MUSIC),
            volumeMax = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1),
            muet = estMuet(),
            reseau = reseau,
            enLigne = enLigne,
            signal = signal,
            bluetoothActif = bt,
            appareilsBluetooth = appareils,
            stockageLibre = stat?.availableBytes ?: 0L,
            stockageTotal = stat?.totalBytes ?: 0L,
        )
    }

    /** Règle le volume (0..max) sans afficher la barre système par-dessus Projo. */
    fun setVolume(v: Int) {
        val max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        audio.setStreamVolume(AudioManager.STREAM_MUSIC, v.coerceIn(0, max), 0)
    }

    /** Son coupé ? (Android 5 ne le dit pas : on se souvient de notre propre réglage.) */
    private var muetAncien = false

    private fun estMuet(): Boolean =
        if (android.os.Build.VERSION.SDK_INT >= 23) audio.isStreamMute(AudioManager.STREAM_MUSIC) else muetAncien

    @Suppress("DEPRECATION")
    fun toggleMute() {
        val muet = estMuet()
        if (android.os.Build.VERSION.SDK_INT >= 23) {
            audio.adjustStreamVolume(
                AudioManager.STREAM_MUSIC,
                if (muet) AudioManager.ADJUST_UNMUTE else AudioManager.ADJUST_MUTE, 0,
            )
        } else {
            audio.setStreamMute(AudioManager.STREAM_MUSIC, !muet)
            muetAncien = !muet
        }
    }

    @Suppress("DEPRECATION")
    private fun lireReseau(): Triple<String, Boolean, Int> = try {
        when (NetworkInfo.transportActif(context)) {
            NetworkInfo.AUCUN -> Triple("Hors ligne", false, -1)
            NetworkCapabilities.TRANSPORT_ETHERNET -> Triple("Ethernet", true, -1)
            NetworkCapabilities.TRANSPORT_WIFI -> {
                val wm = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
                val info = wm.connectionInfo
                val barres = info?.rssi?.let { WifiManager.calculateSignalLevel(it, 5) } ?: -1
                Triple(NetworkInfo.wifiLabel(context), true, barres)
            }
            else -> Triple("Connecté", true, -1)
        }
    } catch (e: Exception) {
        Triple("Wi-Fi", false, -1)
    }

    /** Bluetooth : actif ? et noms des appareils associés (casques, enceintes…). */
    @SuppressLint("MissingPermission")
    private fun lireBluetooth(): Pair<Boolean?, List<String>> = try {
        val bm = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        val adapter: BluetoothAdapter? = bm?.adapter
        if (adapter == null) {
            null to emptyList()
        } else {
            val actif = adapter.isEnabled
            val noms = if (actif) adapter.bondedDevices.orEmpty().mapNotNull { it.name }.sorted() else emptyList()
            actif to noms
        }
    } catch (e: SecurityException) {
        // Android 12+ sans l'autorisation « appareils à proximité » : on n'affiche que l'essentiel
        null to emptyList()
    } catch (e: Exception) {
        null to emptyList()
    }
}
