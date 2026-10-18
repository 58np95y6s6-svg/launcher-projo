package fr.projo.launcher.data

import android.content.ComponentName
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
) {
    /** Tuile propre à Projo (ex. « Source HDMI »), pas une vraie app. */
    val isSpecial: Boolean get() = packageName.startsWith("projo:")

    companion object {
        const val HDMI_ID = "projo:hdmi"
        val HDMI = AppInfo(HDMI_ID, "Source HDMI", isTvApp = true)
    }
}

/**
 * Ce que peut ouvrir la tuile « Source HDMI » : un écran d'app (component) ou une entrée
 * du système TV d'Android (tvInputId, ex. HDMI 2).
 */
data class SourceCandidate(
    val label: String,
    val component: ComponentName? = null,
    val tvInputId: String? = null,
) {
    /** Identifiant mémorisé : « tv:<entrée> » ou « paquet/classe ». */
    val id: String get() = tvInputId?.let { "tv:$it" } ?: component?.flattenToString().orEmpty()
}

/** Lit la liste des apps installées via le PackageManager. */
class AppRepository(private val context: Context) {

    private val pm: PackageManager = context.packageManager

    /**
     * Toutes les apps lançables, triées par nom, sans le launcher lui-même.
     * Apps TV (LEANBACK_LAUNCHER) d'abord, complétées par les apps classiques (LAUNCHER) :
     * sur un projecteur Android « non TV », la plupart sont classiques.
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

    /**
     * Vrai Android TV / Google TV ? Sur les projecteurs « Android classique » (ex. COOLiD),
     * l'écran TV de certaines apps ne démarre pas : on préfère alors la version classique,
     * comme le fait l'accueil du fabricant.
     */
    private val estAndroidTv: Boolean = pm.hasSystemFeature(PackageManager.FEATURE_LEANBACK)

    /** Ouvre une app (version TV d'abord sur un vrai Android TV, classique sinon). */
    fun launch(packageName: String): Boolean {
        val tv = pm.getLeanbackLaunchIntentForPackage(packageName)
        val classique = pm.getLaunchIntentForPackage(packageName)
        val essais = if (estAndroidTv) listOfNotNull(tv, classique) else listOfNotNull(classique, tv)
        return essais.any { start(it) }
    }

    fun start(intent: Intent): Boolean = try {
        context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        true
    } catch (e: Exception) {
        Log.w(TAG, "Impossible d'ouvrir $intent", e)
        false
    }

    /** Paquet de l'écran d'accueil utilisé par le bouton Maison (null si aucun choix). */
    fun currentHomePackage(): String? {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        val ri = pm.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY) ?: return null
        val pkg = ri.activityInfo?.packageName
        // « android » = boîte de choix système : aucun accueil par défaut n'est défini
        return if (pkg == "android") null else pkg
    }

    /** Tous les écrans d'accueil disponibles sur l'appareil. */
    fun homeCandidates(): List<String> {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        return pm.queryIntentActivities(intent, 0).mapNotNull { it.activityInfo?.packageName }.distinct()
    }

    /**
     * Cherche ce qui pourrait ouvrir l'entrée HDMI : apps dont le nom évoque une source,
     * et écrans exportés des autres launchers (ex. accueil du fabricant) dont le nom de classe
     * contient « hdmi », « source » ou « input ». Tout est affiché dans les logs.
     */
    /**
     * Paquet d'AwSource, l'app d'entrées (HDMI…) des projecteurs Allwinner, ou null.
     * Repéré par son nom de paquet ou son nom affiché.
     */
    fun awSourcePackage(): String? {
        val est = { t: String -> t.replace(" ", "").contains("awsource", ignoreCase = true) }
        return try {
            pm.getInstalledApplications(0).firstOrNull { info ->
                est(info.packageName) || est(pm.getApplicationLabel(info).toString())
            }?.packageName
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Choix proposés pour « Sources » : uniquement AwSource (son écran principal, puis ses
     * autres écrans). Sans AwSource : les apps dont le nom contient « HDMI ».
     */
    fun sourceCandidates(apps: List<AppInfo>): List<SourceCandidate> {
        val res = mutableListOf<SourceCandidate>()
        val aw = awSourcePackage()
        if (aw != null) {
            val principal = (pm.getLaunchIntentForPackage(aw) ?: pm.getLeanbackLaunchIntentForPackage(aw))?.component
            principal?.let { res += SourceCandidate("AwSource", component = it) }
            val activites = try {
                @Suppress("DEPRECATION")
                pm.getPackageInfo(aw, PackageManager.GET_ACTIVITIES).activities.orEmpty()
            } catch (e: Exception) {
                emptyArray()
            }
            for (act in activites) {
                if (!act.exported || !act.enabled) continue
                val cn = ComponentName(act.packageName, act.name)
                if (res.none { it.component == cn }) {
                    res += SourceCandidate("AwSource · ${act.name.substringAfterLast('.')}", component = cn)
                }
            }
        } else {
            for (app in apps) {
                if (app.isSpecial || !app.label.contains("hdmi", ignoreCase = true)) continue
                val intent = pm.getLaunchIntentForPackage(app.packageName)
                    ?: pm.getLeanbackLaunchIntentForPackage(app.packageName)
                intent?.component?.let { res += SourceCandidate(app.label, component = it) }
            }
        }
        Log.i(TAG, "Candidats « Sources » : ${res.size}")
        res.forEach { Log.i(TAG, "  ${it.label} → ${it.id}") }
        return res
    }

    companion object {
        const val TAG = "Projo"
    }
}
