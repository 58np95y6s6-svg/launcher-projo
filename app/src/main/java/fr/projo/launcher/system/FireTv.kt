package fr.projo.launcher.system

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Settings

/**
 * Fire TV (Amazon) : on ne peut pas y remplacer l'écran d'accueil. iBiProjo s'y ouvre
 * donc automatiquement grâce au service d'accessibilité RedirectionAccueil, et masque
 * ce qui n'a pas de sens sur ces boîtiers (entrée HDMI, volume).
 */
object FireTv {

    /** true sur un Fire TV (Stick, Cube, téléviseurs Fire TV…). */
    fun estFireTv(context: Context): Boolean =
        context.packageManager.hasSystemFeature("amazon.hardware.fire_tv") ||
            android.os.Build.MANUFACTURER.equals("Amazon", ignoreCase = true)

    /** Écrans d'accueil d'Amazon connus (selon les versions de Fire OS). */
    val ACCUEILS_AMAZON = setOf(
        "com.amazon.tv.launcher",
        "com.amazon.firebat",
        "com.amazon.tv.leanbacklauncher",
    )

    /** Service d'ouverture automatique activé dans les réglages d'accessibilité ? */
    fun redirectionActive(context: Context): Boolean {
        val attendu = ComponentName(context, RedirectionAccueil::class.java).flattenToString()
        val actifs = Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES).orEmpty()
        return actifs.split(':').any { it.equals(attendu, ignoreCase = true) }
    }

    /** Commande ADB à taper si le Fire TV ne montre pas le service dans ses réglages. */
    fun commandeAdb(context: Context): String {
        val c = ComponentName(context, RedirectionAccueil::class.java).flattenToString()
        return "adb shell settings put secure enabled_accessibility_services $c && " +
            "adb shell settings put secure accessibility_enabled 1"
    }

    /** Ouvre les réglages d'accessibilité (renvoie false si le Fire TV ne les propose pas). */
    fun ouvrirAccessibilite(context: Context): Boolean = try {
        val i = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (i.resolveActivity(context.packageManager) == null) false else { context.startActivity(i); true }
    } catch (e: Exception) {
        false
    }

    /** Paquet de l'écran d'accueil par défaut (autre que iBiProjo), s'il y en a un. */
    fun accueilParDefaut(context: Context): String? = try {
        val ri = context.packageManager.resolveActivity(
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME), PackageManager.MATCH_DEFAULT_ONLY,
        )
        ri?.activityInfo?.packageName?.takeIf { it != context.packageName && it != "android" }
    } catch (e: Exception) {
        null
    }
}
