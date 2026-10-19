package fr.projo.launcher.system

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Settings
import android.view.accessibility.AccessibilityManager

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
        val notre = ComponentName(context, RedirectionAccueil::class.java)
        val actifs = Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES).orEmpty()
        // Les réglages peuvent stocker le nom complet ou abrégé (« paquet/.Classe »)
        return actifs.split(':').any { ComponentName.unflattenFromString(it.trim()) == notre }
    }

    /**
     * Service activé mais relié avec son ancienne configuration, sans la touche Maison.
     * Android 9 garde la configuration d'avant une mise à jour d'iBiProjo jusqu'au
     * redémarrage, ou jusqu'à ce que le service soit désactivé puis réactivé.
     */
    fun majAFinir(context: Context): Boolean = try {
        val notre = ComponentName(context, RedirectionAccueil::class.java)
        val am = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
        val info = am.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
            .firstOrNull {
                val si = it.resolveInfo?.serviceInfo
                si != null && ComponentName(si.packageName, si.name) == notre
            }
        // Pas encore relié : on ne peut rien dire
        info != null && (
            info.capabilities and AccessibilityServiceInfo.CAPABILITY_CAN_REQUEST_FILTER_KEY_EVENTS == 0 ||
                info.flags and AccessibilityServiceInfo.FLAG_REQUEST_FILTER_KEY_EVENTS == 0
            )
    } catch (e: Exception) {
        false
    }

    /** Ouvre les options pour les développeurs (pour activer « Débogage ADB »). */
    fun ouvrirOptionsDeveloppeur(context: Context): Boolean = try {
        val i = Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (i.resolveActivity(context.packageManager) == null) false else { context.startActivity(i); true }
    } catch (e: Exception) {
        false
    }

    /**
     * Les commandes à taper dans une app ADB (iPhone, ordinateur) pour activer le service.
     * La première le désactive d'abord, pour qu'Android relise sa configuration (utile
     * après une mise à jour d'iBiProjo).
     */
    fun commandesShell(context: Context): List<String> {
        val c = ComponentName(context, RedirectionAccueil::class.java).flattenToString()
        return listOf(
            "settings delete secure enabled_accessibility_services",
            "settings put secure enabled_accessibility_services $c",
            "settings put secure accessibility_enabled 1",
        )
    }

    /** Adresse IP du Fire TV sur le réseau local (pour s'y connecter depuis un téléphone). */
    fun adresseIp(): String? = try {
        java.net.NetworkInterface.getNetworkInterfaces().toList()
            .filter { it.isUp && !it.isLoopback }
            .flatMap { it.inetAddresses.toList() }
            .firstOrNull { it is java.net.Inet4Address && !it.isLoopbackAddress }
            ?.hostAddress
    } catch (e: Exception) {
        null
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
