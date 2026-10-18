package fr.projo.launcher.system

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.media.tv.TvContract
import android.media.tv.TvInputInfo
import android.media.tv.TvInputManager
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import fr.projo.launcher.data.AppRepository
import fr.projo.launcher.data.SourceCandidate

/** Ouvre les panneaux système (Wi-Fi, Bluetooth, Réglages, Sources) avec des replis. */
class SystemActions(private val context: Context, private val repo: AppRepository) {

    private val pm = context.packageManager

    fun openWifi() = ouvrirPremier(
        Intent(Settings.ACTION_WIFI_SETTINGS),
        Intent(Settings.ACTION_WIRELESS_SETTINGS),
        Intent(Settings.ACTION_SETTINGS),
    )

    fun openBluetooth() = ouvrirPremier(
        Intent(Settings.ACTION_BLUETOOTH_SETTINGS),
        Intent(Settings.ACTION_SETTINGS),
    )

    fun openSettings() = ouvrirPremier(Intent(Settings.ACTION_SETTINGS))

    fun openHomeSettings() = ouvrirPremier(
        Intent(Settings.ACTION_HOME_SETTINGS),
        Intent(Settings.ACTION_SETTINGS),
    )

    /** Page « Infos de l'app » du système. */
    fun openAppInfo(packageName: String) = ouvrirPremier(
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, android.net.Uri.parse("package:$packageName")),
        Intent(Settings.ACTION_MANAGE_APPLICATIONS_SETTINGS),
    )

    /**
     * « Sources » (HDMI) : aucun Intent standard n'existe. Ordre des essais :
     *  1. l'action choisie par l'utilisateur (appui long sur « Sources ») :
     *     « app:paquet » pour une app, « paquet/classe » pour un écran précis ;
     *  2. l'entrée HDMI via le framework TV d'Android (si l'appareil l'a) ;
     *  3. une app ou un écran dont le nom évoque HDMI / Source ;
     *  4. les réglages, avec un message.
     */
    fun openSources(choisi: String?, candidats: List<SourceCandidate>) {
        if (choisi != null) {
            if (choisi.startsWith("app:")) {
                if (repo.launch(choisi.removePrefix("app:"))) return
            } else {
                val cn = ComponentName.unflattenFromString(choisi)
                if (cn != null && repo.start(Intent(Intent.ACTION_MAIN).setComponent(cn))) return
            }
        }
        if (ouvrirEntreeHdmiTv()) return
        for (c in candidats) {
            if (repo.start(Intent(Intent.ACTION_MAIN).setComponent(c.component))) return
        }
        Toast.makeText(
            context,
            "Entrée HDMI introuvable : maintiens OK sur « Sources » pour choisir l'app à ouvrir",
            Toast.LENGTH_LONG,
        ).show()
        openSettings()
    }

    private fun ouvrirEntreeHdmiTv(): Boolean {
        val tim = context.getSystemService(Context.TV_INPUT_SERVICE) as? TvInputManager ?: return false
        val hdmi = try {
            tim.tvInputList.firstOrNull { it.type == TvInputInfo.TYPE_HDMI }
        } catch (e: Exception) { null } ?: return false
        Log.i(AppRepository.TAG, "Entrée HDMI TV trouvée : ${hdmi.id}")
        val intent = Intent(Intent.ACTION_VIEW, TvContract.buildChannelUriForPassthroughInput(hdmi.id))
        return intent.resolveActivity(pm) != null && repo.start(intent)
    }

    private fun ouvrirPremier(vararg intents: Intent): Boolean {
        for (intent in intents) {
            if (intent.resolveActivity(pm) != null && repo.start(intent)) return true
            Log.i(AppRepository.TAG, "Panneau indisponible : ${intent.action}")
        }
        Toast.makeText(context, "Réglage indisponible sur cet appareil", Toast.LENGTH_SHORT).show()
        return false
    }
}
