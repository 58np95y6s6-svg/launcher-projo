package fr.projo.launcher.data

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.util.Log

/** Une app installée qu'on peut lancer. */
data class AppInfo(
    val packageName: String,
    val label: String,
    /** true si l'app déclare une version TV (catégorie LEANBACK_LAUNCHER). */
    val isTvApp: Boolean,
)

/** Lit la liste des apps installées via le PackageManager. */
class AppRepository(private val context: Context) {

    private val pm: PackageManager = context.packageManager

    /**
     * Toutes les apps lançables, triées par nom, sans le launcher lui-même.
     * On prend les apps TV (LEANBACK_LAUNCHER) puis on complète avec les apps classiques
     * (LAUNCHER) : sur un projecteur Android « non TV », la plupart sont classiques.
     * À appeler hors du thread principal.
     */
    fun loadApps(): List<AppInfo> {
        val tv = query(Intent.CATEGORY_LEANBACK_LAUNCHER)
        val classiques = query(Intent.CATEGORY_LAUNCHER)

        val parPaquet = LinkedHashMap<String, AppInfo>()
        for (ri in tv) ajouter(parPaquet, ri, isTv = true)
        for (ri in classiques) ajouter(parPaquet, ri, isTv = false)

        val apps = parPaquet.values.sortedBy { it.label.lowercase() }
        Log.i(TAG, "${apps.size} apps trouvées (${tv.size} TV, ${classiques.size} classiques)")
        apps.forEach { Log.i(TAG, "  ${it.label} → ${it.packageName}${if (it.isTvApp) " [TV]" else ""}") }
        return apps
    }

    private fun query(categorie: String): List<ResolveInfo> {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(categorie)
        return try {
            pm.queryIntentActivities(intent, 0)
        } catch (e: Exception) {
            Log.w(TAG, "Lecture des apps impossible ($categorie)", e)
            emptyList()
        }
    }

    private fun ajouter(map: MutableMap<String, AppInfo>, ri: ResolveInfo, isTv: Boolean) {
        val pkg = ri.activityInfo?.packageName ?: return
        if (pkg == context.packageName || map.containsKey(pkg)) return
        val label = ri.loadLabel(pm)?.toString()?.trim().orEmpty().ifEmpty { pkg }
        map[pkg] = AppInfo(pkg, label, isTv)
    }

    /** Ouvre une app : version TV si elle existe, sinon version classique. */
    fun launch(app: AppInfo): Boolean {
        val intent = pm.getLeanbackLaunchIntentForPackage(app.packageName)
            ?: pm.getLaunchIntentForPackage(app.packageName)
            ?: return false
        return try {
            context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            true
        } catch (e: Exception) {
            Log.w(TAG, "Impossible d'ouvrir ${app.packageName}", e)
            false
        }
    }

    /** Paquet de l'écran d'accueil actuellement utilisé par le bouton Maison (null si aucun choix). */
    fun currentHomePackage(): String? {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        val ri = pm.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY) ?: return null
        val pkg = ri.activityInfo?.packageName
        // « android » = boîte de choix système : aucun accueil par défaut n'est défini
        return if (pkg == "android") null else pkg
    }

    /** Liste de tous les écrans d'accueil disponibles sur l'appareil. */
    fun homeCandidates(): List<String> {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        return pm.queryIntentActivities(intent, 0).mapNotNull { it.activityInfo?.packageName }.distinct()
    }

    companion object {
        const val TAG = "Projo"
    }
}
